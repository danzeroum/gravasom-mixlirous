package com.gravasom.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gravasom.core.data.repository.RoomRecordingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migracao v5 -> v6 (F2): a tabela nova `mix_analysis` (BPM/tom/energia do
 * motor Mixlirous) nasce VAZIA sem tocar em `recordings` nem nas demais; o
 * banco de partida e criado com o DDL EXATO da v5 (as 5 tabelas + coluna
 * `preset`), populado como um usuario real e so entao aberto pela Room v6.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigracaoV6Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nome = "migracao-v6-teste.db"
    private var db: GravasomDatabase? = null

    @After
    fun fechar() {
        db?.close()
        context.deleteDatabase(nome)
    }

    private fun criarV5(populate: (SQLiteDatabase) -> Unit) {
        context.deleteDatabase(nome)
        val helper = object : SQLiteOpenHelper(context, nome, null, 5) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recordings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`path` TEXT NOT NULL, `format` TEXT NOT NULL, `durationMillis` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `folder` TEXT, `tagsCsv` TEXT NOT NULL, " +
                        "`deletedAtMillis` INTEGER, `publishedUri` TEXT, `preset` TEXT)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `edit_projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`recordingId` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, `stepsEncoded` TEXT NOT NULL, " +
                        "`updatedAtMillis` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_edit_projects_recordingId` ON `edit_projects` (`recordingId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `usage_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, `timestampMillis` INTEGER NOT NULL, `value` INTEGER, `label` TEXT)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `markers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`recordingId` INTEGER NOT NULL, `positionMillis` INTEGER NOT NULL, `label` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, `color` TEXT NOT NULL, " +
                        "FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_markers_recordingId_positionMillis` ON `markers` (`recordingId`, `positionMillis`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recording_analysis` (`recordingId` INTEGER NOT NULL, `version` INTEGER NOT NULL, " +
                        "`peaks` BLOB NOT NULL, `frameMillis` INTEGER NOT NULL, `frameCount` INTEGER NOT NULL, `speech` BLOB NOT NULL, " +
                        "PRIMARY KEY(`recordingId`), " +
                        "FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.use { populate(it) }
        helper.close()
    }

    private fun abrirV6(): GravasomDatabase = Room.databaseBuilder(context, GravasomDatabase::class.java, nome)
        .addMigrations(
            GravasomDatabase.MIGRATION_1_2, GravasomDatabase.MIGRATION_2_3, GravasomDatabase.MIGRATION_3_4,
            GravasomDatabase.MIGRATION_4_5, GravasomDatabase.MIGRATION_5_6, GravasomDatabase.MIGRATION_6_7,
        )
        .allowMainThreadQueries()
        .build().also { db = it }

    @Test
    fun `gravacoes sobrevivem e mix_analysis nasce vazia`() = runBlocking {
        criarV5 { sql ->
            sql.execSQL(
                "INSERT INTO recordings VALUES (1, 'Musica de fundo', '/x/mus.wav', 'WAV', 300000, 4800000, 1000, 0, " +
                    "NULL, '', NULL, NULL, NULL)",
            )
        }
        val banco = abrirV6()
        val repo = RoomRecordingRepository(banco.recordingDao())
        assertEquals(listOf("Musica de fundo"), repo.observeAll().first().map { it.title })
        assertEquals("banco migrado nasce sem analise musical", 0, banco.mixAnalysisDao().count())
    }

    @Test
    fun `analise musical salva lida e apagada em cascata`() = runBlocking {
        criarV5 { sql ->
            sql.execSQL(
                "INSERT INTO recordings VALUES (1, 'Riff', '/x/r.wav', 'WAV', 30000, 480000, 1000, 0, NULL, '', NULL, NULL, NULL)",
            )
        }
        val banco = abrirV6()
        val store = MixAnalysisStore(banco.mixAnalysisDao())
        store.save(
            1,
            MixAnalysisStore.AnaliseMix(
                bpm = 120.4f,
                bpmConfianca = 0.9f,
                tonica = 9,
                modo = "menor",
                loudnessLufs = -14.1f,
                energiaMedia = 0.3f,
                energiaDesvio = 0.1f,
                picoDb = -1.2f,
                energiaTrechos = "0.0,5.0,0.32;5.0,10.0,0.28",
            ),
        )
        val analise = store.get(1)
        assertNotNull(analise)
        assertEquals(120.4f, analise!!.bpm!!, 1e-4f)
        assertEquals("Lá menor", analise.rotuloTom)
        assertEquals("120 BPM · Lá menor", analise.etiqueta)
        assertEquals(2, MixAnalysisStore.trechos(analise).size)

        // Upsert substitui (não duplica).
        store.save(
            1,
            MixAnalysisStore.AnaliseMix(95f, 0.7f, 4, "maior", -13f, 0.2f, 0.05f, -2f, "0.0,5.0,0.2"),
        )
        assertEquals(1, banco.mixAnalysisDao().count())
        assertEquals("Mi maior", store.get(1)!!.rotuloTom)

        // Excluir a gravacao apaga a analise (CASCADE) — nada órfão.
        val repo = RoomRecordingRepository(banco.recordingDao())
        repo.deleteForever(1)
        assertFalse(store.has(1))
    }

    @Test
    fun `gravacao sem tonalidade e sem bpm nao tem etiqueta`() = runBlocking {
        criarV5 { sql ->
            sql.execSQL(
                "INSERT INTO recordings VALUES (1, 'Fala', '/x/f.wav', 'WAV', 10000, 160000, 1000, 0, NULL, '', NULL, NULL, NULL)",
            )
        }
        val banco = abrirV6()
        val store = MixAnalysisStore(banco.mixAnalysisDao())
        store.save(1, MixAnalysisStore.AnaliseMix(null, null, null, null, null, 0f, 0f, -3f, ""))
        val entidade = store.get(1)!!
        assertNull(entidade.rotuloTom)
        assertNull(entidade.etiqueta)
    }
}
