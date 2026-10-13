package com.gravasom.core.data.modelmanager

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowDownloadManager

/**
 * Fim do fluxo de download do ModelManager (F7/ADR-17) — crash/erro de campo de 2026-10-12:
 * 1) o parcial era limpo/lido em `getExternalFilesDir(null)` enquanto o DownloadManager gravava em
 *    `…/files/Download` → o arquivo nunca era encontrado; 2) o `renameTo` externo→interno cruza
 *    volumes (sdcard/FUSE → /data/data) e SEMPRE falha no Android → todo download terminava em
 *    "Arquivo baixado vazio ou incompleto" e a transcrição ficava inutilizável.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BaixarModeloTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = ModelManagerImpl(context, ModelStore.de(context))

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun modeloDe(bytes: ByteArray) = ModeloInfo(
        nome = "ggml-teste.bin",
        url = "https://exemplo/modelo.bin",
        sha256 = sha256(bytes),
        tamanhoMb = bytes.size / 1e6f,
        rotulo = "Teste",
    )

    @Test
    fun `o parcial do download é o MESMO caminho do destino gravado pelo DownloadManager`() {
        val parcial = manager.arquivoParcial(context)
        val destinoDoDownloadManager =
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "models/whisper-modelo.download")
        assertEquals(destinoDoDownloadManager.absolutePath, parcial.absolutePath)
    }

    @Test
    fun `fim do download copia para o interno confere o hash e apaga o parcial`() {
        val bytes = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() } // 2 MB > mínimo de 1 MB
        val modelo = modeloDe(bytes)
        val parcial = File(context.cacheDir, "parcial-teste.bin").apply { writeBytes(bytes) }
        val destino = File(ModelStore.de(context).dirModelos, modelo.nome)

        val meta = manager.receberBaixado(parcial, destino, modelo)

        assertEquals(modelo.nome, meta.nome)
        assertEquals(modelo.sha256, meta.sha256)
        assertEquals(destino.absolutePath, meta.path)
        assertTrue(destino.exists())
        assertEquals(bytes.size.toLong(), destino.length())
        assertFalse("o parcial precisa ser apagado após a cópia", parcial.exists())
        destino.delete()
    }

    @Test
    fun `hash divergente descarta o destino e o parcial`() {
        val bytes = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val modelo = modeloDe(bytes).copy(sha256 = "00".repeat(32))
        val parcial = File(context.cacheDir, "parcial-teste.bin").apply { writeBytes(bytes) }
        val destino = File(ModelStore.de(context).dirModelos, modelo.nome)

        val erro = runCatching { manager.receberBaixado(parcial, destino, modelo) }.exceptionOrNull()

        assertTrue("esperava ModelVerificacaoException, veio $erro", erro is ModelVerificacaoException)
        assertFalse("destino com hash errado não pode ficar no disco", destino.exists())
        assertFalse(parcial.exists())
    }

    @Test
    fun `arquivo menor que 1 MB é recusado e apagado`() {
        val bytes = ByteArray(1024) { it.toByte() } // 1 KB < mínimo
        val modelo = modeloDe(bytes)
        val parcial = File(context.cacheDir, "parcial-teste.bin").apply { writeBytes(bytes) }
        val destino = File(ModelStore.de(context).dirModelos, modelo.nome)

        val erro = runCatching { manager.receberBaixado(parcial, destino, modelo) }.exceptionOrNull()

        assertTrue("esperava ModelVerificacaoException, veio $erro", erro is ModelVerificacaoException)
        assertFalse(destino.exists())
        assertFalse(parcial.exists())
    }

    // ——— Pausas do DownloadManager (aparelho 2026-10-12: desistir na 1ª pausa abortava download) ———

    /** Espera o código em teste enfileirar o download e devolve a sombra da requisição (id 0). */
    private fun esperarRequisicaoEnfileirada(): ShadowDownloadManager.ShadowRequest {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val sombra = org.robolectric.Shadows.shadowOf(downloadManager)
        var tentativas = 0
        while (sombra.requestCount == 0 && tentativas < 500) {
            Thread.sleep(2)
            tentativas++
        }
        val request = sombra.getRequest(0)
        assertTrue("o download deveria ter sido enfileirado", request != null)
        return Shadow.extract(request)
    }

    @Test
    fun `pausa transitória do DownloadManager NÃO desiste — download segue até concluir`() {
        val bytes = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val modelo = modeloDe(bytes)
        manager.intervaloDePollingMs = 1L // teste rápido; a tolerância continua em Nº de pausas
        val resultado = java.util.concurrent.atomic.AtomicReference<Result<ModelMetadata>?>(null)

        kotlinx.coroutines.test.runTest {
            val tarefa = launch(Dispatchers.IO) {
                resultado.set(manager.baixarModelo(modelo) {})
            }
            val sombra = esperarRequisicaoEnfileirada()

            // ~20 polls em pausa (transitória): tem de TOLERAR e não desistir (20 < 60).
            sombra.setStatus(DownloadManager.STATUS_PAUSED)
            Thread.sleep(20)

            // O "DownloadManager" retoma e conclui: status + arquivo gravado no destino do parcial.
            sombra.setStatus(DownloadManager.STATUS_SUCCESSFUL)
            manager.arquivoParcial(context).parentFile?.mkdirs() // no aparelho quem cria é o sistema
            manager.arquivoParcial(context).writeBytes(bytes)

            tarefa.join()
        }

        val meta = resultado.get()?.getOrNull()
        assertTrue("download concluído apesar da pausa transitória; veio ${resultado.get()}", meta != null)
        assertEquals(modelo.sha256, meta?.sha256)
        assertFalse("parcial consumido", manager.arquivoParcial(context).exists())
        File(ModelStore.de(context).dirModelos, modelo.nome).delete()
    }

    @Test
    fun `pausa persistente além da tolerância desiste com mensagem clara e limpa o parcial`() {
        val bytes = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val modelo = modeloDe(bytes)
        manager.intervaloDePollingMs = 1L
        val resultado = java.util.concurrent.atomic.AtomicReference<Result<ModelMetadata>?>(null)

        kotlinx.coroutines.test.runTest {
            val tarefa = launch(Dispatchers.IO) {
                resultado.set(manager.baixarModelo(modelo) {})
            }
            val sombra = esperarRequisicaoEnfileirada()
            sombra.setStatus(DownloadManager.STATUS_PAUSED)

            // ~200 polls em pausa (> MAXIMO_PAUSAS_SEGUIDAS = 60): desiste de verdade.
            Thread.sleep(200)
            tarefa.join()
        }

        val falha = resultado.get()?.exceptionOrNull()
        assertTrue("pausa persistente deve falhar; veio ${resultado.get()}", falha is ModelVerificacaoException)
        assertTrue(
            "mensagem deve orientar o dono: ${falha?.message}",
            (falha?.message ?: "").contains("pausado por muito tempo"),
        )
        assertEquals("download desistido sai da fila do sistema", 0,
            org.robolectric.Shadows.shadowOf(context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).requestCount)
        assertFalse(manager.arquivoParcial(context).exists())
    }
}
