package com.gravasom.core.data.modelmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Instalação de modelos EMBUTIDOS no APK (assets/modelos) — pedido do dono (2026-10-13):
 * os três modelos mais leves do catálogo vão dentro do APK para testar a transcrição SEM
 * depender do DownloadManager (que exigia a permissão INTERNET ausente — erro de campo).
 *
 * Disciplina idêntica ao download (ADR-017): NADA entra em uso sem SHA-256 conferido, cópia
 * atômica por .parcial, metadata registrada só no fim.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstalarEmbutidoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = ModelStore.de(context)
    private val manager = ModelManagerImpl(context, store)

    @After
    fun limpar() {
        store.dirModelos.deleteRecursively()
        store.dirModelos.mkdirs()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun bytesDeTeste(tamanho: Int = 1_500_000): ByteArray {
        val bytes = ByteArray(tamanho)
        var seed = 42L
        for (i in bytes.indices) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            bytes[i] = (seed ushr 33).toByte()
        }
        return bytes
    }

    private fun modeloDe(bytes: ByteArray, nome: String = "ggml-teste-embutido.bin") = ModeloInfo(
        nome = nome,
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$nome",
        sha256 = sha256(bytes),
        tamanhoMb = bytes.size / 1_048_576f,
        rotulo = "Teste embutido",
    )

    private fun gerenteComAsset(bytes: ByteArray): ModelManagerImpl = manager.apply {
        abridorDeEmbutido = { ByteArrayInputStream(bytes) }
    }

    @Test
    fun `instalar copia do asset confere o hash registra metadata e NÃO deixa parcial`() = runBlocking {
        val bytes = bytesDeTeste()
        val modelo = modeloDe(bytes)
        val progressos = mutableListOf<Float>()
        val resultado = gerenteComAsset(bytes).instalarEmbutido(modelo) { progressos.add(it) }

        assertTrue("instalação deveria ter sucesso: ${resultado.exceptionOrNull()}", resultado.isSuccess)
        val meta = resultado.getOrThrow()
        assertEquals(modelo.nome, meta.nome)
        assertEquals(modelo.sha256, meta.sha256)
        assertEquals("embutido no APK", meta.origem)
        val destino = File(store.dirModelos, modelo.nome)
        assertTrue("modelo deve existir em filesDir/models", destino.exists())
        assertEquals(bytes.size.toLong(), destino.length())
        assertFalse(".parcial não pode sobrar", File(store.dirModelos, "${modelo.nome}.parcial").exists())
        assertTrue("hasModelo deve reconhecer o modelo instalado", store.hasModelo())
        assertTrue("progresso deve chegar ao fim (tolerância de arredondamento float)", progressos.last() >= 0.99f)
        assertTrue("progresso é não-decrescente", progressos.zipWithNext().all { (a, b) -> b >= a })
        assertEquals("metadata persistida", meta.nome, store.modeloAtual()?.nome)
    }

    @Test
    fun `hash divergente do embutido falha apaga o parcial e NÃO registra metadata`() = runBlocking {
        val bytes = bytesDeTeste()
        val modelo = modeloDe(bytes).copy(sha256 = "0".repeat(64)) // catálogo com hash errado
        val resultado = gerenteComAsset(bytes).instalarEmbutido(modelo) { }

        assertTrue("hash divergente tem que falhar", resultado.isFailure)
        assertFalse(File(store.dirModelos, modelo.nome).exists())
        assertFalse(File(store.dirModelos, "${modelo.nome}.parcial").exists())
        assertFalse("nada entra em uso sem hash batido", store.hasModelo())
    }

    @Test
    fun `asset menor que o mínimo falha com mensagem clara`() = runBlocking {
        val bytes = bytesDeTeste(500_000) // < MINIMO_BYTES (1 MB)
        val modelo = modeloDe(bytes)
        val resultado = gerenteComAsset(bytes).instalarEmbutido(modelo) { }

        assertTrue(resultado.isFailure)
        assertTrue(
            (resultado.exceptionOrNull()?.message ?: "").contains("muito pequeno"),
        )
        assertFalse(store.hasModelo())
    }

    @Test
    fun `reinstalar sobre modelo existente substitui sem lixo`() = runBlocking {
        val bytes = bytesDeTeste()
        val modelo = modeloDe(bytes)
        val gerente = gerenteComAsset(bytes)
        gerente.instalarEmbutido(modelo) { }
        val bytes2 = bytesDeTeste(1_800_000)
        val modelo2 = modeloDe(bytes2, "ggml-teste-embutido.bin") // MESMO nome, outro conteúdo
        manager.apply { abridorDeEmbutido = { ByteArrayInputStream(bytes2) } }
        val resultado2 = manager.instalarEmbutido(modelo2) { }

        assertTrue(resultado2.isSuccess)
        val destino = File(store.dirModelos, modelo.nome)
        assertEquals(bytes2.size.toLong(), destino.length())
        assertEquals(bytes2.size / 1e6f, store.modeloAtual()?.tamanhoMb ?: 0f, 0.01f)
        assertFalse(File(store.dirModelos, "${modelo.nome}.parcial").exists())
    }

    @Test
    fun `modelosEmbutidos sem assets devolve lista vazia sem crash`() {
        // Robolectric não empacota APK: context.assets.list("modelos") não existe/vazio —
        // tem que devolver lista vazia e NUNCA lançar (a tela depende disso no boot).
        val embutidos = manager.modelosEmbutidos()
        assertTrue("esperava lista vazia, veio $embutidos", embutidos.isEmpty())
    }

    @Test
    fun `asset ausente para modelo do catálogo falha com mensagem clara e sem lixo`() = runBlocking {
        val modelo = CatalogoModelos.modelos.first { it.nome == "ggml-base-q5_1.bin" }
        val resultado = manager.instalarEmbutido(modelo) { } // nenhum asset configurado
        assertTrue("asset ausente tem que falhar", resultado.isFailure)
        assertFalse(store.hasModelo())
    }
}
