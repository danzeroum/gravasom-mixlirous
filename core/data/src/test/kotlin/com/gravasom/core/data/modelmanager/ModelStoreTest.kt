package com.gravasom.core.data.modelmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ModelStore (F7/ADR-017): consentimento persistente, metadados JSON, sanidade do arquivo
 * (existe E > 1 MB) e SHA-256 em streaming — a lógica pura do guardião de modelos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: ModelStore
    private var contadorPrefs = 0

    @Before
    fun preparar() {
        contadorPrefs++
        store = ModelStore(context, context.getSharedPreferences("modelmanager-test-$contadorPrefs", Context.MODE_PRIVATE))
        store.dirModelos.deleteRecursively()
    }

    @Test
    fun consentimentoPersiste() {
        assertFalse(store.consentiu())
        store.registrarConsentimento()
        assertTrue(store.consentiu())
    }

    @Test
    fun semModeloHasModeloFalse() {
        assertFalse(store.hasModelo())
        assertNull(store.modeloAtual())
        // Arquivo de 0 bytes NÃO é modelo (MINIMO_BYTES = 1 MB).
        store.salvarMetadata(ModelMetadata("m.bin", 1f, "abc", "origem", File(store.dirModelos, "vazio.bin").absolutePath, null))
        File(store.dirModelos, "vazio.bin").writeBytes(ByteArray(10))
        assertFalse(store.hasModelo())
    }

    @Test
    fun metadataRoundtripEHasModelo() {
        val arquivo = File(store.dirModelos, "ggml-tiny-q5_1.bin")
        store.dirModelos.mkdirs()
        arquivo.writeBytes(ByteArray(2 * 1024 * 1024)) // > 1 MB
        val meta = ModelMetadata(
            nome = "ggml-tiny-q5_1.bin",
            tamanhoMb = 32f,
            sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7",
            origem = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin",
            path = arquivo.absolutePath,
            downloadAtMillis = 1234L,
        )
        store.salvarMetadata(meta)
        val lida = store.lerMetadata()
        assertEquals(meta, lida)
        assertTrue(store.hasModelo())
        assertEquals(meta.path, store.modeloAtual()!!.path)
    }

    @Test
    fun metadataImportadaTemDownloadNulo() {
        val arquivo = File(store.dirModelos, "importado.bin")
        store.dirModelos.mkdirs()
        arquivo.writeBytes(ByteArray(2 * 1024 * 1024))
        store.salvarMetadata(ModelMetadata("importado.bin", 2f, "hash", "importado", arquivo.absolutePath, null))
        val lida = store.lerMetadata()
        assertNull(lida!!.downloadAtMillis)
        assertEquals("importado", lida.origem)
    }

    @Test
    fun removerModeloApagaArquivoEMetadata() {
        val arquivo = File(store.dirModelos, "ggml-base-q5_1.bin")
        store.dirModelos.mkdirs()
        arquivo.writeBytes(ByteArray(2 * 1024 * 1024))
        store.salvarMetadata(ModelMetadata("ggml-base-q5_1.bin", 60f, "hash", "origem", arquivo.absolutePath, 1L))
        assertTrue(store.hasModelo())
        store.removerModelo()
        assertFalse(arquivo.exists())
        assertFalse(store.hasModelo())
    }

    @Test
    fun catalogoTemHashesETamanhosPlausiveis() {
        assertTrue(CatalogoModelos.modelos.size >= 6)
        CatalogoModelos.modelos.forEach { modelo ->
            assertTrue(modelo.url.startsWith("https://huggingface.co/"))
            assertEquals(64, modelo.sha256.length)
            assertTrue(modelo.tamanhoMb in 20f..600f)
        }
        assertTrue(CatalogoModelos.modelos.first { it.recomendado }.nome == "ggml-base-q5_1.bin")
    }

    @Test
    fun sha256StreamingConferidoContraHashConhecido() {
        // "abc" -> ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad (vetor NIST).
        val arquivo = File(context.cacheDir, "sha-abc.txt")
        arquivo.writeText("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256Streaming.calcular(arquivo),
        )
        // copiarComHash: mesma hash do conteúdo e arquivo escrito igual.
        val destino = File(context.cacheDir, "sha-abc-copia.txt")
        arquivo.inputStream().use { entrada ->
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Sha256Streaming.copiarComHash(entrada, destino),
            )
        }
        assertEquals("abc", destino.readText())
    }
}
