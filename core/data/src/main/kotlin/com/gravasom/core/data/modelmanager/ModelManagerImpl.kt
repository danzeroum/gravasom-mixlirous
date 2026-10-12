package com.gravasom.core.data.modelmanager

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Implementação do ADR-017: DownloadManager (sistema) como ÚNICA porta de rede do app, verificação
 * de SHA-256 antes de qualquer uso, e importação SAF sem rede. Nenhuma outra classe do app toca rede.
 */
@Singleton
class ModelManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ModelStore,
) : ModelManager {

    private val downloadManager: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    override fun hasModelo(): Boolean = store.hasModelo()

    override fun modeloAtual(): ModelMetadata? = store.modeloAtual()

    override fun obterModelo(): String =
        store.modeloAtual()?.path ?: throw ModelNaoDisponivelException()

    override fun consentiu(): Boolean = store.consentiu()

    override fun registrarConsentimento() = store.registrarConsentimento()

    override suspend fun baixarModelo(modelo: ModeloInfo, onProgresso: (Float) -> Unit): Result<ModelMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                // O DownloadManager recusa destino que já existe: limpar parciais de tentativas anteriores.
                val parcial = File(context.getExternalFilesDir(null), "models/${NOME_PARCIAL}")
                if (parcial.exists()) parcial.delete()

                val request = DownloadManager.Request(Uri.parse(modelo.url))
                    .setTitle("Modelo Whisper — ${modelo.nome}")
                    .setDescription("Única conexão de rede do app (ADR-017)")
                    .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                    .setAllowedOverRoaming(false)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "models/$NOME_PARCIAL")
                val id = downloadManager.enqueue(request)

                val status = aguardarDownload(id, onProgresso)
                if (status.isFailure()) {
                    throw ModelVerificacaoException("Download falhou (${status.descricao()})")
                }

                // Move para o storage privado interno e verifica o hash ANTES de registrar metadados.
                store.dirModelos.mkdirs()
                val destino = File(store.dirModelos, modelo.nome)
                parcial.renameTo(destino)
                if (!destino.exists() || destino.length() < ModelStore.MINIMO_BYTES) {
                    destino.delete()
                    throw ModelVerificacaoException("Arquivo baixado vazio ou incompleto")
                }
                val hashReal = Sha256Streaming.calcular(destino)
                if (!hashReal.equals(modelo.sha256, ignoreCase = true)) {
                    destino.delete()
                    throw ModelVerificacaoException("SHA-256 não confere — arquivo descartado")
                }
                val meta = ModelMetadata(
                    nome = modelo.nome,
                    tamanhoMb = destino.length() / 1e6f,
                    sha256 = hashReal,
                    origem = modelo.url,
                    path = destino.absolutePath,
                    downloadAtMillis = System.currentTimeMillis(),
                )
                store.salvarMetadata(meta)
                meta
            }
        }

    override suspend fun importarModelo(uri: Uri): Result<ModelMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val entrada = context.contentResolver.openInputStream(uri)
                ?: throw ModelVerificacaoException("Não foi possível abrir o arquivo selecionado")
            val nomeOriginal = queryNome(uri) ?: "modelo-importado.bin"
            val destino = File(store.dirModelos, "importado_$nomeOriginal")
            val hash = entrada.use { Sha256Streaming.copiarComHash(it, destino) }
            if (destino.length() < ModelStore.MINIMO_BYTES) {
                destino.delete()
                throw ModelVerificacaoException("Arquivo muito pequeno para ser um modelo")
            }
            val meta = ModelMetadata(
                nome = nomeOriginal,
                tamanhoMb = destino.length() / 1e6f,
                sha256 = hash,
                origem = "importado",
                path = destino.absolutePath,
                downloadAtMillis = null,
            )
            store.salvarMetadata(meta)
            meta
        }
    }

    override fun removerModelo() = store.removerModelo()

    /** Polling 1×/s do DownloadManager com progresso; cancelável — remove o download do sistema. */
    private suspend fun aguardarDownload(id: Long, onProgresso: (Float) -> Unit): DownloadEstado {
        while (true) {
            coroutineContext.ensureActive() // cancelar durante o download interrompe e limpa abaixo
            val query = downloadManager.query(DownloadManager.Query().setFilterById(id))
            var estado: DownloadEstado? = null
            query?.use { cursor ->
                if (cursor.moveToFirst()) {
                    estado = lerEstado(cursor, id)
                }
            } ?: run { estado = DownloadEstado.FALHOU("DownloadManager indisponível") }
            when (val e = estado) {
                is DownloadEstado.SUCESSO -> return e
                is DownloadEstado.FALHOU -> {
                    downloadManager.remove(id)
                    File(context.getExternalFilesDir(null), "models/$NOME_PARCIAL").delete()
                    return e
                }
                is DownloadEstado.RODANDO -> onProgresso(e.progresso)
                null -> {}
            }
            delay(1000)
        }
    }

    private fun lerEstado(cursor: Cursor, id: Long): DownloadEstado {
        val colunaStatus = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        if (colunaStatus < 0) return DownloadEstado.FALHOU("Status indisponível")
        return when (cursor.getInt(colunaStatus)) {
            DownloadManager.STATUS_SUCCESSFUL -> DownloadEstado.SUCESSO
            DownloadManager.STATUS_FAILED -> DownloadEstado.FALHOU("STATUS_FAILED")
            DownloadManager.STATUS_PAUSED ->
                DownloadEstado.FALHOU("download pausado — verifique Wi-Fi/espaço no aparelho")
            else -> {
                val colunaBytes = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val colunaTotal = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val lidos = if (colunaBytes >= 0) cursor.getLong(colunaBytes) else 0L
                val total = if (colunaTotal >= 0) cursor.getLong(colunaTotal) else -1L
                val progresso = if (total > 0) (lidos.toFloat() / total).coerceIn(0f, 1f) else 0f
                DownloadEstado.RODANDO(progresso, id)
            }
        }
    }

    private fun queryNome(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val coluna = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (coluna >= 0 && cursor.moveToFirst()) cursor.getString(coluna) else null
        }
    }.getOrNull()

    private sealed interface DownloadEstado {
        data object SUCESSO : DownloadEstado
        data class RODANDO(val progresso: Float, val id: Long) : DownloadEstado
        data class FALHOU(val motivo: String) : DownloadEstado
    }

    private fun DownloadEstado.isFailure(): Boolean = this is DownloadEstado.FALHOU
    private fun DownloadEstado.descricao(): String = when (this) {
        is DownloadEstado.FALHOU -> motivo
        else -> "estado inesperado"
    }

    private companion object {
        const val NOME_PARCIAL = "whisper-modelo.download"
    }
}
