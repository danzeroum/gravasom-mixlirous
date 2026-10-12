package com.gravasom.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gravasom.core.data.repository.RoomMarkerRepository
import com.gravasom.core.data.repository.RoomRecordingRepository
import com.gravasom.core.domain.preset.FormatPreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migracao v4 -> v5 (Onda 2) escrita a mao: o banco de partida e criado com o DDL EXATO da v4 (as 5 tabelas), populado como um usuario
 * real (gravacoes com tags, marcadores, analise, projeto de edicao) e so entao aberto pela Room v5.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigracaoV5Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nome = "migracao-v5-teste.db"
    private var db: GravasomDatabase? = null

    @After
    fun fechar() {
        db?.close()
        context.deleteDatabase(nome)
    }

    private fun criarV4(populate: (SQLiteDatabase) -> Unit) {
        context.deleteDatabase(nome)
        val helper = object : SQLiteOpenHelper(context, nome, null, 4) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recordings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`path` TEXT NOT NULL, `format` TEXT NOT NULL, `durationMillis` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `folder` TEXT, `tagsCsv` TEXT NOT NULL, " +
                        "`deletedAtMillis` INTEGER, `publishedUri` TEXT)",
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

    private fun abrirV5(): GravasomDatabase = Room.databaseBuilder(context, GravasomDatabase::class.java, nome)
        .addMigrations(
            GravasomDatabase.MIGRATION_1_2, GravasomDatabase.MIGRATION_2_3, GravasomDatabase.MIGRATION_3_4,
            GravasomDatabase.MIGRATION_4_5, GravasomDatabase.MIGRATION_5_6, GravasomDatabase.MIGRATION_6_7,
            GravasomDatabase.MIGRATION_7_8,
        )
        .allowMainThreadQueries()
        .build().also { db = it }

    @Test
    fun `gravacoes tags marcadores analise e projeto sobrevivem a migracao v4 para v5`() = runBlocking {
        criarV4 { sql ->
            sql.execSQL(
                "INSERT INTO recordings VALUES (1, 'Aula de fisica', '/x/a.m4a', 'M4A', 3600000, 57600000, 1000, 1, " +
                    "'estudo', 'aula,fisica', NULL, 'content://media/1')",
            )
            sql.execSQL("INSERT INTO recordings VALUES (2, 'Ideia', '/x/b.wav', 'WAV', 12000, 1056000, 2000, 0, NULL, '', NULL, NULL)")
            sql.execSQL("INSERT INTO markers VALUES (1, 1, 600000, 'Pergunta', 'voltar aqui', 'PURPLE')")
            sql.execSQL("INSERT INTO edit_projects VALUES (1, 1, 1, 'v1', 5)")
            sql.execSQL("INSERT INTO recording_analysis VALUES (1, 1, x'0102', 10, 2, x'00')")
        }
        val banco = abrirV5()
        val repo = RoomRecordingRepository(banco.recordingDao())
        assertEquals(listOf("Ideia", "Aula de fisica"), repo.observeAll().first().map { it.title })
        val aula = repo.getById(1)!!
        assertEquals(listOf("aula", "fisica"), aula.tags)
        assertEquals("estudo", aula.folder)
        assertTrue(aula.isFavorite)
        assertEquals(3_600_000L, aula.durationMillis)
        assertNull("gravacao antiga nao tem preset", aula.preset)
        assertEquals("voltar aqui", RoomMarkerRepository(banco.markerDao()).list(1).single().note)
        assertNotNull(banco.editProjectDao().getByRecording(1))
        assertNotNull(banco.analysisDao().get(1))
    }

    @Test
    fun `o preset novo e gravado e lido`() = runBlocking {
        criarV4 { sql -> sql.execSQL("INSERT INTO recordings VALUES (1, 'a', '/x/a.m4a', 'M4A', 60000, 1, 1, 0, NULL, '', NULL, NULL)") }
        val banco = abrirV5()
        val repo = RoomRecordingRepository(banco.recordingDao())
        val id = repo.insert(repo.getById(1)!!.copy(id = 0, title = "Musica", preset = FormatPreset.MUSIC))
        assertEquals(FormatPreset.MUSIC, repo.getById(id)!!.preset)
        assertNull(repo.getById(1)!!.preset)
    }

    @Test
    fun `preset desconhecido no banco vira sem preset em vez de quebrar`() = runBlocking {
        criarV4 { sql -> sql.execSQL("INSERT INTO recordings VALUES (1, 'a', '/x/a.m4a', 'M4A', 60000, 1, 1, 0, NULL, '', NULL, NULL)") }
        val banco = abrirV5()
        banco.openHelper.writableDatabase.execSQL("UPDATE recordings SET preset = 'de-uma-versao-futura' WHERE id = 1")
        assertNull(RoomRecordingRepository(banco.recordingDao()).getById(1)!!.preset)
    }
}
