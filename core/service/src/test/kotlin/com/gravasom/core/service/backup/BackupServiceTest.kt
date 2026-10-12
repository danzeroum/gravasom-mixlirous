package com.gravasom.core.service.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gravasom.core.audio.engine.edit.EditProject
import com.gravasom.core.audio.engine.edit.EditStep
import com.gravasom.core.audio.engine.edit.StepType
import com.gravasom.core.data.db.EditProjectDao
import com.gravasom.core.data.db.EditProjectEntity
import com.gravasom.core.data.db.EditProjectStore
import com.gravasom.core.data.vault.VaultCrypto
import com.gravasom.core.domain.audio.RecordingFormat
import com.gravasom.core.domain.marker.Marker
import com.gravasom.core.domain.marker.MarkerColor
import com.gravasom.core.domain.marker.MarkerRepository
import com.gravasom.core.domain.model.Recording
import com.gravasom.core.domain.preset.FormatPreset
import com.gravasom.core.domain.repository.RecordingRepository
import com.gravasom.core.service.library.MediaStorePublisher
import com.gravasom.core.service.library.RecordingFiles
import com.gravasom.core.service.library.VaultStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/**
 * Backup cifrado de ponta a ponta (Tink em fluxo + envelope por senha): ida e volta com o MESMO SHA-256 por arquivo, marcadores,
 * etiquetas e projeto; senha errada restaura e apaga NADA (criterio de aceite da Onda 2); pacote corrompido nao passa da fase 1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupServiceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var repo: FakeRepo
    private lateinit var markers: FakeMarkers
    private lateinit var projects: EditProjectStore
    private lateinit var saver: FakeSaver
    private lateinit var service: BackupService
    private lateinit var vault: VaultStore

    private class FakeRepo : RecordingRepository {
        val recordings = mutableListOf<Recording>()
        val favorited = mutableListOf<Pair<Long, Boolean>>()
        val tagged = mutableListOf<Pair<Long, List<String>>>()
        var foreverDeleted = 0
        override fun observeAll(): Flow<List<Recording>> = MutableStateFlow(recordings.toList())
        override fun search(query: String): Flow<List<Recording>> = MutableStateFlow(emptyList())
        override fun observeFavorites(): Flow<List<Recording>> = MutableStateFlow(emptyList())
        override fun observeTrash(): Flow<List<Recording>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: Long): Recording? = recordings.firstOrNull { it.id == id }
        override suspend fun bySha256(sha256: String): Recording? = null
        override suspend fun insert(recording: Recording): Long = recording.id
        override suspend fun rename(id: Long, newTitle: String) = Unit
        override suspend fun setFavorite(id: Long, favorite: Boolean) { favorited += id to favorite }
        override suspend fun setTags(id: Long, tags: List<String>) { tagged += id to tags }
        override suspend fun moveToTrash(id: Long, nowMillis: Long) = Unit
        override suspend fun restore(id: Long) = Unit
        override suspend fun setPublishedUri(id: Long, uri: String?) = Unit
        override suspend fun deleteForever(id: Long) { foreverDeleted++ }
        override suspend fun purgeTrash(olderThanMillis: Long, nowMillis: Long) = Unit
    }

    private class FakeMarkers : MarkerRepository {
        val byRecording = mutableMapOf<Long, MutableList<Marker>>()
        private var nextId = 1L
        override fun observe(recordingId: Long): Flow<List<Marker>> = MutableStateFlow(byRecording[recordingId].orEmpty())
        override suspend fun list(recordingId: Long): List<Marker> = byRecording[recordingId].orEmpty()
        override suspend fun insert(marker: Marker): Long {
            val id = nextId++
            byRecording.getOrPut(marker.recordingId) { mutableListOf() } += marker.copy(id = id)
            return id
        }

        override suspend fun update(marker: Marker) = Unit
        override suspend fun delete(id: Long) = Unit
    }

    private class FakeDao : EditProjectDao {
        val rows = mutableMapOf<Long, EditProjectEntity>()
        override suspend fun upsert(project: EditProjectEntity): Long {
            rows.remove(project.recordingId)
            rows[project.recordingId] = project
            return project.recordingId
        }

        override suspend fun getByRecording(recordingId: Long): EditProjectEntity? = rows[recordingId]
        override fun observeByRecording(recordingId: Long): Flow<EditProjectEntity?> = MutableStateFlow(rows[recordingId])
        override suspend fun deleteByRecording(recordingId: Long) { rows.remove(recordingId) }
    }

    private class FakeSaver : BackupSaver {
        val saved = mutableListOf<Triple<File, String, Boolean>>()
        var nextId = 100L
        override suspend fun save(file: File, title: String, forcePrivate: Boolean, preset: FormatPreset?): Long? {
            saved += Triple(file, title, forcePrivate)
            return nextId++
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repo = FakeRepo()
        markers = FakeMarkers()
        projects = EditProjectStore(FakeDao())
        saver = FakeSaver()
        vault = VaultStore(context, lazy { VaultCrypto(VaultCrypto.newStreamingAead()) })
        val files = RecordingFiles(context, repo, MediaStorePublisher(context), vault)
        service = BackupService(context, repo, files, markers, projects, saver)
    }

    /** Bytes de "audio" quaisquer: o backup nao decodifica, so copia e confere o hash. */
    private fun arquivo(name: String, bytes: Int): File {
        val f = tmp.newFile(name)
        f.writeBytes(ByteArray(bytes) { (it * 31 + 7).toByte() })
        return f
    }

    private fun sha256(f: File): String =
        MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    private suspend fun gravacao(
        id: Long,
        title: String,
        file: File,
        favorite: Boolean = false,
        tags: List<String> = emptyList(),
        noCofre: Boolean = false,
    ): Recording {
        val stored: File = if (noCofre) vault.protect(file)!! else file
        val r = Recording(
            id = id, title = title, path = stored.path, format = RecordingFormat.WAV,
            durationMillis = 60_000, sizeBytes = stored.length(), createdAtMillis = 1,
            isFavorite = favorite, tags = tags, preset = FormatPreset.CLASS,
        )
        repo.recordings += r
        return r
    }

    @Test
    fun `ida e volta devolve os mesmos bytes, marcadores, etiquetas e projeto`() = runBlocking {
        val a = gravacao(
            7, "Aula de física", arquivo("a.wav", 90_000), favorite = true, tags = listOf("aula", "física"),
        )
        markers.insert(
            Marker(recordingId = 7, positionMillis = 600_000, label = "Pergunta", note = "voltar aqui", color = MarkerColor.PURPLE),
        )
        markers.insert(Marker(recordingId = 7, positionMillis = 900_000, label = "", note = ""))
        projects.save(7, EditProject(listOf(EditStep(StepType.NOISE_REDUCTION, listOf(0.4f)))))
        val claroB = arquivo("b.wav", 30_000)
        val hashBClaro = sha256(claroB)
        gravacao(8, "Ideia da banda", claroB, noCofre = true)
        val hashA = sha256(File(a.path))
        val pacote = tmp.newFile("backup.gravasom")
        val create = pacote.outputStream().use { service.create(it, "senha certa".toCharArray()) }
        assertEquals("2 gravacoes no pacote", 2, (create as BackupResult.Done).recordings)
        assertTrue(create.bytes > 0L)
        // nada mudou nem apagou na biblioteca original
        assertEquals(2, repo.recordings.size)
        assertEquals(0, repo.foreverDeleted)
        assertEquals(hashA, sha256(File(a.path)))

        val restore = pacote.inputStream().use { service.restore(it, "senha certa".toCharArray()) }
        val done = restore as RestoreResult.Done
        assertEquals(2, done.restored)
        assertEquals(2, saver.saved.size)
        // o mesmo SHA-256 por arquivo (criterio de aceite)
        assertEquals(hashA, sha256(saver.saved[0].first))
        assertEquals(hashBClaro, sha256(saver.saved[1].first))
        // metadados voltaram para os ids novos
        assertEquals(100L to true, repo.favorited.single())
        assertTrue(repo.tagged.single().second == listOf("aula", "física"))
        assertEquals(2, markers.byRecording[100L]?.size)
        assertTrue(markers.byRecording[100L]!!.any { it.label == "Pergunta" && it.color == MarkerColor.PURPLE })
        assertEquals(0.4f, projects.load(100L)!!.steps.single().params.single(), 0.0f)
        // derivada do cofre continua indo para o cofre
        assertTrue(saver.saved[1].third)
    }

    @Test
    fun `senha errada nao restaura e nao apaga nada`() = runBlocking {
        gravacao(7, "Aula", arquivo("a.wav", 5_000))
        val pacote = tmp.newFile("backup.gravasom")
        pacote.outputStream().use { service.create(it, "certa".toCharArray()) }
        val antes = repo.recordings.toList()
        val restore = pacote.inputStream().use { service.restore(it, "errada".toCharArray()) }
        assertEquals(RestoreResult.WrongPassword, restore)
        assertTrue("nada restaurado", saver.saved.isEmpty())
        assertTrue("nada apagado", repo.foreverDeleted == 0)
        assertEquals(antes, repo.recordings)
    }

    @Test
    fun `pacote corrompido falha na conferencia e nao grava nada`() = runBlocking {
        gravacao(7, "Aula", arquivo("a.wav", 50_000))
        val pacote = tmp.newFile("backup.gravasom")
        pacote.outputStream().use { service.create(it, "certa".toCharArray()) }
        val bytes = pacote.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x55).toByte()
        pacote.writeBytes(bytes)
        val restore = pacote.inputStream().use { service.restore(it, "certa".toCharArray()) }
        assertTrue(restore.toString(), restore is RestoreResult.Failed)
        assertTrue(saver.saved.isEmpty())
    }

    @Test
    fun `arquivo que nao e pacote recusa na hora`() = runBlocking {
        val lixo = tmp.newFile("lixo.gravasom").apply { writeText("isto nao e um backup") }
        val restore = lixo.inputStream().use { service.restore(it, "x".toCharArray()) }
        assertTrue(restore.toString(), restore is RestoreResult.Failed)
        assertTrue(saver.saved.isEmpty())
    }

    @Test
    fun `biblioteca vazia gera pacote valido que restaura zero`() = runBlocking {
        val pacote = tmp.newFile("vazio.gravasom")
        pacote.outputStream().use { service.create(it, "senha".toCharArray()) }
        val restore = pacote.inputStream().use { service.restore(it, "senha".toCharArray()) }
        assertEquals(0, (restore as RestoreResult.Done).restored)
        assertTrue(saver.saved.isEmpty())
    }
}
