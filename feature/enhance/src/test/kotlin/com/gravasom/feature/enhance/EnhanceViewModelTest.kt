package com.gravasom.feature.enhance

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.gravasom.core.audio.engine.WavHeaderWriter
import com.gravasom.core.audio.engine.edit.AudioBlockProcessor
import com.gravasom.core.audio.engine.edit.EditProject
import com.gravasom.core.domain.audio.RecordingFormat
import com.gravasom.core.domain.model.Recording
import com.gravasom.core.domain.repository.RecordingRepository
import com.gravasom.core.service.enhance.EnhanceHost
import com.gravasom.core.service.enhance.EnhanceService
import com.gravasom.core.service.enhance.EnhanceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * O ViewModel do "Melhorar som" sobre o servico REAL (host falso, redutor que so atenua): preparar, previa A/B e, sobretudo,
 * cancelar no meio — nenhum arquivo novo e o original intacto (criterio de aceite da Onda 2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR")
class EnhanceViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val rate = 16_000

    private lateinit var origem: File
    private lateinit var host: Host
    private lateinit var repo: FakeRepo
    private lateinit var player: FakePreviewPlayer
    private lateinit var vm: EnhanceViewModel

    /** Redutor falso: atenua proporcionalmente a intensidade (basta mudar o som de forma deterministica). */
    private class Atenua(private val intensity: Float) : AudioBlockProcessor {
        override val latencySamples = 0
        override fun process(block: FloatArray, length: Int) {
            for (i in 0 until length) block[i] *= (1f - 0.5f * intensity)
        }
    }

    private class Host(private val file: File, root: File) : EnhanceHost {
        val saved = mutableListOf<Triple<File, String, Recording>>()
        val projects = mutableMapOf<Long, EditProject>()
        override val workDir = File(root, "work").apply { mkdirs() }
        override val outputDir = File(root, "saida").apply { mkdirs() }
        override suspend fun playableFile(recording: Recording) = file
        override suspend fun saveDerived(file: File, title: String, source: Recording): Long? {
            saved += Triple(file, title, source)
            return 99L
        }

        override suspend fun loadProject(recordingId: Long) = projects[recordingId]
        override suspend fun saveProject(recordingId: Long, project: EditProject) { projects[recordingId] = project }
    }

    private class FakeRepo(private var recording: Recording?) : RecordingRepository {
        override fun observeAll(): Flow<List<Recording>> = MutableStateFlow(listOfNotNull(recording))
        override fun search(query: String): Flow<List<Recording>> = MutableStateFlow(emptyList())
        override fun observeFavorites(): Flow<List<Recording>> = MutableStateFlow(emptyList())
        override fun observeTrash(): Flow<List<Recording>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: Long): Recording? = recording?.takeIf { it.id == id }
        override suspend fun bySha256(sha256: String): Recording? = null
        override suspend fun insert(recording: Recording): Long = recording.id
        override suspend fun rename(id: Long, newTitle: String) = Unit
        override suspend fun setFavorite(id: Long, favorite: Boolean) = Unit
        override suspend fun setTags(id: Long, tags: List<String>) = Unit
        override suspend fun moveToTrash(id: Long, nowMillis: Long) = Unit
        override suspend fun restore(id: Long) = Unit
        override suspend fun setPublishedUri(id: Long, uri: String?) = Unit
        override suspend fun deleteForever(id: Long) = Unit
        override suspend fun purgeTrash(olderThanMillis: Long, nowMillis: Long) = Unit
    }

    /** Jogador de previa falso: "toca" na hora e guarda o que tocou. */
    private class FakePreviewPlayer : PreviewPlayer {
        val tocados = mutableListOf<File>()
        var fim: (() -> Unit)? = null
        override suspend fun play(file: File, onEnd: () -> Unit): Boolean {
            tocados += file
            fim = onEnd
            return true
        }

        override fun stop() = Unit
    }

    @Before
    fun setUp() {
        origem = wav(12)
        host = Host(origem, tmp.root)
        repo = FakeRepo(
            Recording(
                id = 7, title = "Aula de fisica", path = origem.path, format = RecordingFormat.WAV,
                durationMillis = 12_000, sizeBytes = origem.length(), createdAtMillis = 1,
            ),
        )
        player = FakePreviewPlayer()
        vm = EnhanceViewModel(
            SavedStateHandle(mapOf("recordingId" to 7L)),
            repo,
            EnhanceService(host, { i, _ -> Atenua(i) }, portuguese = { true }),
            player,
        )
        aguardarPronto()
    }

    @Test
    fun `cancelar no meio nao salva nenhum arquivo e o original fica intacto`() {
        val antes = origem.readBytes()
        vm.setNormalize(true)
        vm.setTargetLufs(-16f)
        vm.export()
        aguardar({ vm.state.value.status == EnhanceStatus.EXPORTING })
        vm.cancelExport()
        aguardar({ vm.state.value.status == EnhanceStatus.READY }, "voltar a READY apos cancelar")
        assertTrue(host.saved.isEmpty())
        assertTrue("nenhum WAV novo", host.outputDir.listFiles().orEmpty().none { it.extension == "wav" })
        assertArrayEquals("o original nao muda", antes, origem.readBytes())
        assertNull(vm.state.value.savedTitle)
    }

    @Test
    fun `sem nada ligado exportar so avisa e nao chama o servico`() {
        vm.export()
        aguardar({ vm.state.value.notice != null })
        assertEquals(EnhanceStatus.READY, vm.state.value.status)
        assertEquals(R.string.enhance_notice_identity, vm.state.value.notice)
        assertTrue(host.saved.isEmpty())
        assertTrue(host.outputDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `exportar ate o fim salva a copia e guarda o projeto`() {
        vm.setNormalize(true)
        vm.export()
        aguardar({ vm.state.value.status == EnhanceStatus.SAVED }, "concluir a exportacao")
        assertEquals("Aula de fisica (melhorada)", vm.state.value.savedTitle)
        assertEquals(1, host.saved.size)
        assertTrue(host.projects.containsKey(7L))
    }

    @Test
    fun `a previa toca o lado pedido e mexer nos ajustes invalida os trechos gerados`() {
        vm.setNormalize(true)
        vm.preview(EnhanceSide.A)
        aguardar({ vm.state.value.previewing == EnhanceSide.A }, "tocar A")
        assertEquals(1, player.tocados.size)
        vm.setDenoise(true)
        aguardar({ vm.state.value.previewing == null })
        vm.preview(EnhanceSide.B)
        // B regera os trechos (ajustes mudaram) e so entao toca: segunda chamada ao jogador.
        aguardar({ vm.state.value.previewing == EnhanceSide.B }, "tocar B depois de regerar")
        assertEquals(2, player.tocados.size)
    }

    /** Fala sintetica baixa (silabas a 3 Hz + pausas), mono, [seconds] s — a mesma do EnhanceServiceTest. */
    private fun wav(seconds: Int, amp: Double = 0.02): File {
        val n = rate * seconds
        val f = tmp.newFile("origem_${System.nanoTime()}.wav")
        RandomAccessFile(f, "rw").use { raf ->
            raf.write(WavHeaderWriter.build(rate, 1, 16, n * 2L))
            val pcm = ByteArray(n * 2)
            for (i in 0 until n) {
                val t = i.toDouble() / rate
                val syllable = abs(sin(2 * PI * 3.0 * t))
                val v = (amp * syllable * (sin(2 * PI * 180 * t) + 0.5 * sin(2 * PI * 900 * t)) * 32767).toInt().coerceIn(-32768, 32767)
                pcm[2 * i] = (v and 0xFF).toByte()
                pcm[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
            }
            raf.write(pcm)
        }
        return f
    }

    private fun aguardarPronto() = aguardar({ vm.state.value.status == EnhanceStatus.READY }, "preparar a gravacao")

    /**
     * O viewModelScope corre no Main do Robolectric e o servico em threads de verdade (IO): drena o looper enquanto
     * espera a condicao, com prazo.
     */
    private fun aguardar(condicao: () -> Boolean, oque: String = "") {
        val prazo = System.currentTimeMillis() + LIMITE_MS
        while (!condicao()) {
            check(System.currentTimeMillis() < prazo) { "timeout esperando $oque (estado=${vm.state.value})" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private companion object {
        const val LIMITE_MS = 20_000L
    }
}
