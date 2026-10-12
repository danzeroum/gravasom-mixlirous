package com.gravasom.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gravasom.core.data.repository.RoomRecordingRepository
import com.gravasom.core.domain.model.RecordingOrigin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migracao v6 -> v7 (F6/Importação): três colunas anuláveis em `recordings`
 * (`sha256`, `origin`, `artist`). Linhas antigas ficam NULL = RECORDED; a
 * importação usa o hash para NÃO duplicar arquivo igual (mesmo SHA-256 → Já existe).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigracaoV7Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nome = "migracao-v7-teste.db"
    private var db: GravasomDatabase? = null

    @After
    fun fechar() {
        db?.close()
        context.deleteDatabase(nome)
    }

    /** Banco v6 com o DDL EXATO do schema anterior (recordings sem as 3 colunas novas). */
    private fun criarV6(populate: (SQLiteDatabase) -> Unit) {
        context.deleteDatabase(nome)
        val helper = object : SQLiteOpenHelper(context, nome, null, 6) {
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
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mix_analysis` (" +
                        "`recordingId` INTEGER NOT NULL, `bpm` REAL, `bpmConfianca` REAL, `tonica` INTEGER, " +
                        "`modo` TEXT, `loudnessLufs` REAL, `energiaMedia` REAL NOT NULL, `energiaDesvio` REAL NOT NULL, " +
                        "`picoDb` REAL NOT NULL, `energiaTrechos` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`recordingId`), " +
                        "FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mix_analysis_recordingId` ON `mix_analysis` (`recordingId`)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.use { populate(it) }
        helper.close()
    }

    private fun abrirV7(): GravasomDatabase = Room.databaseBuilder(context, GravasomDatabase::class.java, nome)
        .addMigrations(
            GravasomDatabase.MIGRATION_1_2, GravasomDatabase.MIGRATION_2_3, GravasomDatabase.MIGRATION_3_4,
            GravasomDatabase.MIGRATION_4_5, GravasomDatabase.MIGRATION_5_6, GravasomDatabase.MIGRATION_6_7,
            GravasomDatabase.MIGRATION_7_8,
        )
        .allowMainThreadQueries()
        .build().also { db = it }

    @Test
    fun `gravacao antiga sobrevive com origin null e vira RECORDED`() = runBlocking {
        criarV6 { sql ->
            sql.execSQL(
                "INSERT INTO recordings VALUES (1, 'Aula', '/x/a.wav', 'WAV', 300000, 4800000, 1000, 0, " +
                    "NULL, '', NULL, NULL, NULL)",
            )
        }
        val banco = abrirV7()
        val repo = RoomRecordingRepository(banco.recordingDao())
        val gravacao = repo.observeAll().first().single()
        assertEquals("Aula", gravacao.title)
        assertNull("linha antiga não tem hash", gravacao.sha256)
        assertEquals("linha antiga sem origin vira RECORDED", RecordingOrigin.RECORDED, gravacao.origin)
        assertNull(gravacao.artist)
    }

    @Test
    fun `mesmo sha256 nao duplica e importada fica com etiqueta`() = runBlocking {
        criarV6 { sql ->
            sql.execSQL(
                "INSERT INTO recordings VALUES (1, 'Aula', '/x/a.wav', 'WAV', 300000, 4800000, 1000, 0, " +
                    "NULL, '', NULL, NULL, NULL)",
            )
        }
        val banco = abrirV7()
        val repo = RoomRecordingRepository(banco.recordingDao())
        val hash = "abc123"
        repo.insert(
            com.gravasom.core.domain.model.Recording(
                title = "Musica importada",
                path = "/data/imports/2026-10-12_101010.mp3",
                format = com.gravasom.core.domain.audio.RecordingFormat.MP3,
                durationMillis = 120_000,
                sizeBytes = 2_000_000,
                createdAtMillis = 2000,
                sha256 = hash,
                origin = RecordingOrigin.IMPORTED,
                artist = "Artista X",
            ),
        )
        // Idempotência: o MESMO hash já existe → a importação NÃO duplica.
        assertTrue(banco.recordingDao().existsBySha256(hash))
        val existente = repo.bySha256(hash)!!
        assertEquals(RecordingOrigin.IMPORTED, existente.origin)
        assertEquals("Artista X", existente.artist)
        assertTrue(existente.isImported)
        // Hash diferente não colide.
        assertEquals(null, repo.bySha256("outro-hash"))
    }
}
