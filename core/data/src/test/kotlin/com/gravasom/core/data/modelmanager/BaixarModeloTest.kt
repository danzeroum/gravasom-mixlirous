package com.gravasom.core.data.modelmanager

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}
