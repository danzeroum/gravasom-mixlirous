package com.gravasom.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migração v7 -> v8 (F7/Transcrição): tabela NOVA `recording_annotations` (whisper.cpp local,
 * ADR-013/017). NÃO toca em `recordings`; CASCADE ao excluir a gravação; insert/consulta funcionam.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigracaoV8Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nome = "migracao-v8-teste.db"
    private var db: GravasomDatabase? = null

    @After
    fun fechar() {
        db?.close()
        context.deleteDatabase(nome)
    }

    /** Banco v7 com o DDL EXATO do schema anterior (sem `recording_annotations`). */
    private fun criarV7(populate: (SQLiteDatabase) -> Unit) {
        context.deleteDatabase(nome)
        val helper = object : SQLiteOpenHelper(context, nome, null, 7) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recordings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`path` TEXT NOT NULL, `format` TEXT NOT NULL, `durationMillis` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `folder` TEXT, `tagsCsv` TEXT NOT NULL, " +
                        "`deletedAtMillis` INTEGER, `publishedUri` TEXT, `preset` TEXT, `sha256` TEXT, `origin` TEXT, `artist` TEXT)",
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
                    "CREATE TABLE IF NOT EXISTS `mix_analysis` (`recordingId` INTEGER NOT NULL, `bpm` REAL, `bpmConfianca` REAL, " +
                        "`tonica` INTEGER, `modo` TEXT, `loudnessLufs` REAL, `energiaMedia` REAL NOT NULL, `energiaDesvio` REAL NOT NULL, " +
                        "`picoDb` REAL NOT NULL, `energiaTrechos` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`recordingId`), " +
                        "FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mix_analysis_recordingId` ON `mix_analysis` (`recordingId`)")
                populate(db)
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.close()
    }

    private fun abrirV8(): GravasomDatabase {
        db = Room.databaseBuilder(context, GravasomDatabase::class.java, nome)
            .addMigrations(*todasMigracoes())
            .build()
        return db!!
    }

    private fun todasMigracoes() = arrayOf(
        GravasomDatabase.MIGRATION_1_2, GravasomDatabase.MIGRATION_2_3, GravasomDatabase.MIGRATION_3_4,
        GravasomDatabase.MIGRATION_4_5, GravasomDatabase.MIGRATION_5_6, GravasomDatabase.MIGRATION_6_7,
        GravasomDatabase.MIGRATION_7_8,
    )

    @Test
    fun migraPreservaGravacoesECriaTabelaDeAnotacoes() = runBlocking {
        criarV7 { sql ->
            sql.execSQL(
                "INSERT INTO recordings (id, title, path, format, durationMillis, sizeBytes, createdAtMillis, isFavorite, tagsCsv) " +
                    "VALUES (1, 'Aula de violão', '/x/a.m4a', 'M4A', 60000, 1000, 10, 0, '')",
            )
        }
        val banco = abrirV8()
        // Gravação antiga intacta.
        val gravacao = banco.recordingDao().getById(1)
        assertTrue(gravacao != null && gravacao.title == "Aula de violão")
        // Tabela nova aceita anotação e devolve.
        val dao = banco.annotationDao()
        val id = dao.insert(
            RecordingAnnotationEntity(
                recordingId = 1L,
                createdAtMillis = 100L,
                titulo = "Transcrição automática",
                texto = "[00:00] primeiro trecho",
                raw = "primeiro trecho",
                duracaoInferenciaMs = 4200L,
                modelo = "ggml-base-q5_1.bin",
                idioma = "pt",
                tokens = 3,
            ),
        )
        assertTrue(id > 0)
        val anotacoes = dao.forRecording(1L)
        assertEquals(1, anotacoes.size)
        assertEquals("[00:00] primeiro trecho", anotacoes.first().texto)
        assertEquals("ggml-base-q5_1.bin", anotacoes.first().modelo)
    }

    @Test
    fun excluirGravacaoCascadeAnotacoes() = runBlocking {
        criarV7 { sql ->
            sql.execSQL(
                "INSERT INTO recordings (id, title, path, format, durationMillis, sizeBytes, createdAtMillis, isFavorite, tagsCsv) " +
                    "VALUES (2, 'Reunião', '/x/b.m4a', 'M4A', 30000, 500, 20, 0, '')",
            )
        }
        val banco = abrirV8()
        val dao = banco.annotationDao()
        dao.insert(
            RecordingAnnotationEntity(
                recordingId = 2L, createdAtMillis = 1L, titulo = "t", texto = "x", raw = "x",
                duracaoInferenciaMs = 1L, modelo = "m", idioma = "pt", tokens = 1,
            ),
        )
        assertEquals(1, dao.countForRecording(2L))
        banco.recordingDao().deleteForever(2L)
        assertEquals(0, dao.countForRecording(2L))
    }
}
