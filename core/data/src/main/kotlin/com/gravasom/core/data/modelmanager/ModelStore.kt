package com.gravasom.core.data.modelmanager

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import org.json.JSONException
import org.json.JSONObject

/**
 * Estado PERSISTENTE do ModelManager (F7, ADR-017), isolado de rede/DownloadManager para ser
 * testável no host: aceite do consentimento, metadados JSON em `files/models/.metadata.json`
 * e verificação de sanidade do arquivo (existe E > 1 MB).
 *
 * O diretório `filesDir/models` já é privado do app (nenhum outro app lê — cache de modelo
 * com permissão do sistema, sem world-readable).
 */
class ModelStore(private val contexto: Context, private val prefs: SharedPreferences) {

    val dirModelos: File = File(contexto.filesDir, "models")
    private val arquivoMetadata: File get() = File(dirModelos, METADATA_JSON)

    fun consentiu(): Boolean = prefs.getBoolean(CHAVE_CONSENTIMENTO, false)

    fun registrarConsentimento() {
        prefs.edit().putBoolean(CHAVE_CONSENTIMENTO, true).apply()
    }

    fun hasModelo(): Boolean {
        val meta = lerMetadata() ?: return false
        val arquivo = File(meta.path)
        return arquivo.exists() && arquivo.length() > MINIMO_BYTES
    }

    fun modeloAtual(): ModelMetadata? = if (hasModelo()) lerMetadata() else null

    fun salvarMetadata(meta: ModelMetadata) {
        dirModelos.mkdirs()
        val json = JSONObject()
            .put("nome", meta.nome)
            .put("tamanhoMb", meta.tamanhoMb.toDouble())
            .put("sha256", meta.sha256)
            .put("origem", meta.origem)
            .put("path", meta.path)
            .put("downloadAtMillis", meta.downloadAtMillis ?: JSONObject.NULL)
        arquivoMetadata.writeText(json.toString())
    }

    fun lerMetadata(): ModelMetadata? {
        if (!arquivoMetadata.exists()) return null
        return try {
            val json = JSONObject(arquivoMetadata.readText())
            ModelMetadata(
                nome = json.getString("nome"),
                tamanhoMb = json.getDouble("tamanhoMb").toFloat(),
                sha256 = json.getString("sha256"),
                origem = json.getString("origem"),
                path = json.getString("path"),
                downloadAtMillis = if (json.isNull("downloadAtMillis")) null else json.getLong("downloadAtMillis"),
            )
        } catch (_: JSONException) {
            null
        } catch (_: java.io.IOException) {
            null
        }
    }

    fun removerModelo() {
        val meta = lerMetadata()
        if (meta != null) File(meta.path).delete()
        arquivoMetadata.delete()
    }

    companion object {
        const val METADATA_JSON = ".metadata.json"
        const val CHAVE_CONSENTIMENTO = "consentiu_download_modelo"
        const val MINIMO_BYTES = 1024L * 1024L // >1 MB: arquivo de 0 bytes não é modelo

        fun de(contexto: Context): ModelStore =
            ModelStore(contexto, contexto.getSharedPreferences("modelmanager", Context.MODE_PRIVATE))
    }
}

/** SHA-256 em streaming (blocos de 8 KB) — nunca carrega o modelo inteiro na memória. */
object Sha256Streaming {

    fun calcular(arquivo: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(arquivo).use { entrada ->
            val bloco = ByteArray(8192)
            while (true) {
                val lidos = entrada.read(bloco)
                if (lidos <= 0) break
                digest.update(bloco, 0, lidos)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Recebe um fluxo já aberto (SAF), calcula o hash e escreve em [destino] na MESMA passada. */
    fun copiarComHash(entrada: java.io.InputStream, destino: File): String =
        copiarComHash(entrada, destino, onBloco = null)

    /**
     * Mesma cópia com hash, com callback opcional de progresso por BYTES copiados (acumulado desde
     * o início). Usado pela instalação de modelos embutidos no APK (sem rede) — o total conhecido
     * vem do catálogo; aqui só reportamos o acumulado bruto.
     */
    fun copiarComHash(entrada: java.io.InputStream, destino: File, onBloco: ((Long) -> Unit)?): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bloco = ByteArray(BUFFER_COPIA)
        var acumulado = 0L
        destino.parentFile?.mkdirs()
        destino.outputStream().use { saida ->
            while (true) {
                val lidos = entrada.read(bloco)
                if (lidos <= 0) break
                digest.update(bloco, 0, lidos)
                saida.write(bloco, 0, lidos)
                acumulado += lidos
                onBloco?.invoke(acumulado)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Bloco de cópia em disco: 256 KB (modelos de dezenas/centenas de MB — 8 KB era lento demais). */
    internal const val BUFFER_COPIA = 256 * 1024
}
