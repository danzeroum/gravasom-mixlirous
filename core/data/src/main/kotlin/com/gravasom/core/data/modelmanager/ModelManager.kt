package com.gravasom.core.data.modelmanager

import android.net.Uri

/** Metadados do modelo atual (ADR-017: um modelo por vez, origem e hash sempre visíveis). */
data class ModelMetadata(
    val nome: String,
    val tamanhoMb: Float,
    val sha256: String,
    val origem: String, // URL fixa do catálogo ou "importado"
    val path: String,
    val downloadAtMillis: Long?, // null = importado via SAF
)

/** Exceções do fluxo de modelo (mensagens viram texto do Erro na tela de transcrição). */
class ModelNaoDisponivelException(mensagem: String = "Nenhum modelo disponível — baixe ou importe um modelo") :
    IllegalStateException(mensagem)

class ModelVerificacaoException(mensagem: String) : IllegalStateException(mensagem)

/**
 * Guardião do modelo de transcrição (F7, ADR-017): ÚNICA porta de rede do app, com consentimento
 * explícito (tamanho + origem + SHA-256 antes do download), verificação de hash e importação via
 * SAF para uso offline. Sem aceite, o app segue 100% offline.
 */
interface ModelManager {

    /** true se há modelo baixado/importado e conferido em filesDir/models/. */
    fun hasModelo(): Boolean

    /** Metadados do modelo atual, ou null. */
    fun modeloAtual(): ModelMetadata?

    /** Path absoluto do modelo pronto para o JNI; lança [ModelNaoDisponivelException] se não houver. */
    fun obterModelo(): String

    /** O dono já consentiu com o download (única conexão de rede do app)? */
    fun consentiu(): Boolean

    /** Registra o aceite do consentimento (persistente). */
    fun registrarConsentimento()

    /** Baixa o modelo da URL FIXA do catálogo e verifica o SHA-256 esperado. Reporta progresso 0..1. */
    suspend fun baixarModelo(modelo: ModeloInfo, onProgresso: (Float) -> Unit = {}): Result<ModelMetadata>

    /** Importa um .bin/.gguf via SAF (sem rede): calcula o SHA-256 e exibe para conferência manual. */
    suspend fun importarModelo(uri: Uri): Result<ModelMetadata>

    /** Modelos do catálogo que já vêm EMBUTIDOS no APK (assets/modelos/): não precisam de download. */
    fun modelosEmbutidos(): List<ModeloInfo>

    /**
     * Instala um modelo EMBUTIDO no APK: cópia local assets/modelos → filesDir/models com
     * verificação de SHA-256 (mesma disciplina do download), SEM nenhuma rede. Reporta
     * progresso 0..1 por bytes copiados.
     */
    suspend fun instalarEmbutido(modelo: ModeloInfo, onProgresso: (Float) -> Unit = {}): Result<ModelMetadata>

    /** Remove o modelo e os metadados (protocolo de medição ADR-016: forçar novo download). */
    fun removerModelo()
}
