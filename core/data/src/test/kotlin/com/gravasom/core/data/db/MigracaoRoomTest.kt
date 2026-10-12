package com.gravasom.core.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gravasom.core.data.repository.RoomMarkerRepository
import com.gravasom.core.data.repository.RoomRecordingRepository
import com.gravasom.core.domain.marker.Marker
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
 * Migracao v3 -> v4 (Onda 1; a Room atual e a v5, com a 4->5 encadeada) escrita a mao. O banco de partida e criado com
 * o DDL EXATO da v3 (copiado das migracoes 1->2 e 2->3 e da entidade `recordings` da v3), populado como um usuario
 * real e so entao aberto pela Room v4: se a
 * migracao perder dados ou divergir do esquema que a Room espera, a abertura falha.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigracaoRoomTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nome = "migracao-teste.db"
    private var db: GravasomDatabase? = null

    @After
    fun fechar() {
        db?.close()
        context.deleteDatabase(nome)
    }

    /** Cria o banco como o app v3 o deixaria. */
    private fun criarV3(populate: (SQLiteDatabase) -> Unit) {
        context.deleteDatabase(nome)
        val helper = object : SQLiteOpenHelper(context, nome, null, 3) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recordings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`path` TEXT NOT NULL, `format` TEXT NOT NULL, `durationMillis` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `folder` TEXT, `tagsCsv` TEXT NOT NULL, " +
                        "`deletedAtMillis` INTEGER, `publishedUri` TEXT)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `edit_projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`recordingId` INTEGER NOT NULL, " +
                        "`schemaVersion` INTEGER NOT NULL, `stepsEncoded` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_edit_projects_recordingId` ON `edit_projects` (`recordingId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `usage_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, `timestampMillis` INTEGER NOT NULL, `value` INTEGER, `label` TEXT)",
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.use { populate(it) }
        helper.close()
    }

    private fun abrirV4(): GravasomDatabase = Room.databaseBuilder(context, GravasomDatabase::class.java, nome)
        .addMigrations(
            GravasomDatabase.MIGRATION_1_2, GravasomDatabase.MIGRATION_2_3, GravasomDatabase.MIGRATION_3_4,
            GravasomDatabase.MIGRATION_4_5, GravasomDatabase.MIGRATION_5_6, GravasomDatabase.MIGRATION_6_7,
            GravasomDatabase.MIGRATION_7_8,
        )
        .allowMainThreadQueries()
        .build().also { db = it }

    @Test
    fun `gravacoes e tags existentes sobrevivem a migracao v3 para v4`() = runBlocking {
        criarV3 { sql ->
            sql.execSQL(
                "INSERT INTO recordings VALUES (1, 'Aula de fisica', '/x/a.m4a', 'M4A', 3600000, 57600000, 1000, 1, " +
                    "'estudo', 'aula,fisica', NULL, 'content://media/1')",
            )
            sql.execSQL("INSERT INTO recordings VALUES (2, 'Ideia', '/x/b.wav', 'WAV', 12000, 1056000, 2000, 0, NULL, '', NULL, NULL)")
            sql.execSQL(
                "INSERT INTO recordings VALUES (3, 'Na lixeira', '/x/c.mp3', 'MP3', 5000, 80000, 3000, 0, NULL, 'velha', 99999, NULL)",
            )
            sql.execSQL("INSERT INTO edit_projects VALUES (1, 1, 1, 'passo', 5)")
            sql.execSQL("INSERT INTO usage_events VALUES (1, 'GRAVACAO_CONCLUIDA', 7, 3600000, 'M4A')")
        }

        val banco = abrirV4()
        val repo = RoomRecordingRepository(banco.recordingDao())

        val todas = repo.observeAll().first()
        assertEquals(listOf("Ideia", "Aula de fisica"), todas.map { it.title }) // ordem por data, mais nova primeiro
        val aula = repo.getById(1)!!
        assertEquals(listOf("aula", "fisica"), aula.tags)
        assertEquals("estudo", aula.folder)
        assertTrue(aula.isFavorite)
        assertEquals(3_600_000L, aula.durationMillis)
        assertEquals("content://media/1", aula.publishedUri)
        assertEquals(listOf("velha"), repo.getById(3)!!.tags)
        assertNotNull(repo.getById(3)!!.deletedAtMillis)
        assertNotNull(banco.editProjectDao().getByRecording(1))
        assertEquals(listOf("GRAVACAO_CONCLUIDA"), banco.usageEventDao().all().map { it.type })
    }

    @Test
    fun `tabelas novas nascem vazias e funcionam depois da migracao`() = runBlocking {
        criarV3 { sql ->
            sql.execSQL("INSERT INTO recordings VALUES (1, 'a', '/x/a.m4a', 'M4A', 60000, 1, 1, 0, NULL, 'aula', NULL, NULL)")
        }
        val banco = abrirV4()
        val markers = RoomMarkerRepository(banco.markerDao())
        assertTrue(markers.list(1).isEmpty())
        val id = markers.insert(Marker(recordingId = 1, positionMillis = 5_000, label = "Pergunta", note = "voltar aqui"))
        assertEquals("voltar aqui", markers.list(1).single().note)
        assertEquals(id, markers.list(1).single().id)
        assertNull(AnalysisStore(banco.analysisDao()).load(1))
    }

    @Test
    fun `excluir a gravacao apaga marcadores e analise em cascata`() = runBlocking {
        criarV3 { sql -> sql.execSQL("INSERT INTO recordings VALUES (1, 'a', '/x/a.m4a', 'M4A', 60000, 1, 1, 0, NULL, '', NULL, NULL)") }
        val banco = abrirV4()
        val markers = RoomMarkerRepository(banco.markerDao())
        markers.insert(Marker(recordingId = 1, positionMillis = 1, label = "", note = ""))
        banco.analysisDao().upsert(RecordingAnalysisEntity(1, AnalysisStore.VERSION, ByteArray(10), 20, 0, ByteArray(0)))
        banco.recordingDao().deleteForever(1)
        assertTrue(markers.list(1).isEmpty())
        assertNull(banco.analysisDao().get(1))
    }
}
