package com.gravasom.feature.transcricao

import android.net.Uri
import com.gravasom.core.data.db.AnnotationDao
import com.gravasom.core.data.db.RecordingAnnotationEntity
import com.gravasom.core.data.modelmanager.ModelManager
import com.gravasom.core.data.modelmanager.ModelMetadata
import com.gravasom.core.data.modelmanager.ModelNaoDisponivelException
import com.gravasom.core.data.modelmanager.ModeloInfo
import com.gravasom.core.domain.audio.RecordingFormat
import com.gravasom.core.domain.model.Recording
import com.gravasom.core.domain.repository.RecordingRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Falsificações compartilhadas pelos testes da tela/ViewModel de transcrição (host, sem JNI).
 * O use case falso simula os caminhos felizes e de falha; o gerente falso simula o ModelManager
 * com download instantâneo, falho, com OOM ou infinito (cancelamento).
 */
internal class ModeloFalso : ModelManager {
    var modelo: ModelMetadata? = null
    var excecaoNoDownload: Throwable? = null
    var falhaNoDownload: Throwable? = null
    var progressosReportados = mutableListOf<Float>()
    var consentimento = false
    var downloadsIniciados = 0

    override fun hasModelo(): Boolean = modelo != null
    override fun modeloAtual(): ModelMetadata? = modelo
    override fun obterModelo(): String = modelo?.path ?: throw ModelNaoDisponivelException()
    override fun consentiu(): Boolean = consentimento
    override fun registrarConsentimento() { consentimento = true }

    override suspend fun baixarModelo(modelo: ModeloInfo, onProgresso: (Float) -> Unit): Result<ModelMetadata> {
        downloadsIniciados++
        if (excecaoNoDownload != null) throw excecaoNoDownload!!
        if (falhaNoDownload != null) {
            progressosReportados.add(0.4f)
            onProgresso(0.4f)
            return Result.failure(falhaNoDownload!!)
        }
        progressosReportados.add(0.25f)
        progressosReportados.add(0.75f)
        onProgresso(0.25f)
        onProgresso(0.75f)
        val meta = ModelMetadata(
            nome = modelo.nome, tamanhoMb = modelo.tamanhoMb, sha256 = modelo.sha256,
            origem = modelo.url, path = "/data/files/models/${modelo.nome}", downloadAtMillis = 1L,
        )
        this.modelo = meta
        return Result.success(meta)
    }

    override suspend fun importarModelo(uri: Uri): Result<ModelMetadata> =
        Result.success(modelo ?: ModelMetadata("importado.bin", 1f, "h", "importado", "/m/i.bin", null))

    override fun removerModelo() { modelo = null }
}

/** Download infinito (nunca termina) — para provar o cancelamento do download. */
internal class DownloadInfinitoFalso : ModelManager by ModeloFalso() {
    override suspend fun baixarModelo(modelo: ModeloInfo, onProgresso: (Float) -> Unit): Result<ModelMetadata> {
        onProgresso(0.1f)
        while (true) {
            currentCoroutineContext().ensureActive()
            delay(50)
        }
    }
}

internal class RepositorioFalso(private val recording: Recording?) : RecordingRepository {
    override fun observeAll(): Flow<List<Recording>> = flowOf(emptyList())
    override fun search(query: String): Flow<List<Recording>> = flowOf(emptyList())
    override fun observeFavorites(): Flow<List<Recording>> = flowOf(emptyList())
    override fun observeTrash(): Flow<List<Recording>> = flowOf(emptyList())
    override suspend fun getById(id: Long): Recording? = recording
    override suspend fun bySha256(sha256: String): Recording? = null
    override suspend fun insert(recording: Recording): Long = 1L
    override suspend fun rename(id: Long, newTitle: String) = Unit
    override suspend fun setFavorite(id: Long, favorite: Boolean) = Unit
    override suspend fun setTags(id: Long, tags: List<String>) = Unit
    override suspend fun moveToTrash(id: Long, nowMillis: Long) = Unit
    override suspend fun restore(id: Long) = Unit
    override suspend fun setPublishedUri(id: Long, uri: String?) = Unit
    override suspend fun deleteForever(id: Long) = Unit
    override suspend fun purgeTrash(olderThanMillis: Long, nowMillis: Long) = Unit
}

internal class AnotacaoFalsa : AnnotationDao {
    val inseridas = mutableListOf<RecordingAnnotationEntity>()
    override suspend fun insert(annotation: RecordingAnnotationEntity): Long {
        inseridas.add(annotation); return 99L
    }
    override suspend fun forRecording(recordingId: Long): List<RecordingAnnotationEntity> = inseridas
    override suspend fun countForRecording(recordingId: Long): Int = inseridas.size
    override suspend fun deleteById(id: Long) = Unit
}

/** UseCase de teste: nada de JNI na JVM — simula os caminhos felizes e de falha. */
internal class UseCaseFalso(
    repo: RecordingRepository,
    anotacaoDao: AnnotationDao,
) : TranscricaoUseCase(repo, anotacaoDao) {
    enum class Modo { FELIZ, OOM, LINKAGE, ERRO, GRAVACAO_AUSENTE, CONGELADO }
    var modo = Modo.FELIZ
    val fases = mutableListOf<FaseTranscricao>()
    var resultado: TranscricaoResultado? = null
    var textoSalvo: String? = null
    val anotacao = AnotacaoFalsa()

    private fun resultadoFalso() = TranscricaoResultado(
        segmentos = listOf(Segmento(0, 2000, "teste de transcrição")),
        resumo = "[00:00] teste de transcrição",
        raw = "teste de transcrição",
        tokens = 7,
        duracaoInferenciaMs = 1_234L,
        modeloNome = "ggml-base-q5_1.bin",
        idioma = "pt",
        comTimestamps = true,
    )

    override suspend fun transcreverAberta(
        recordingId: Long, modeloPath: String, modeloNome: String, idioma: String,
        comTimestamps: Boolean, cancelado: () -> Boolean, onFase: (FaseTranscricao) -> Unit,
    ): TranscricaoResultado? {
        val fase: (FaseTranscricao) -> Unit = { fases.add(it); onFase(it) }
        when (modo) {
            Modo.GRAVACAO_AUSENTE -> return super.transcreverAberta(
                recordingId, modeloPath, modeloNome, idioma, comTimestamps, cancelado, onFase,
            )
            Modo.OOM -> { fase(FaseTranscricao.PREPARANDO); throw OutOfMemoryError("heap cheio") }
            Modo.LINKAGE -> { fase(FaseTranscricao.PREPARANDO); throw UnsatisfiedLinkError("lib não carregou") }
            Modo.ERRO -> { fase(FaseTranscricao.PREPARANDO); throw IllegalStateException("decode falhou") }
            Modo.CONGELADO -> {
                fase(FaseTranscricao.PREPARANDO)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    delay(50)
                }
            }
            Modo.FELIZ -> {
                fase(FaseTranscricao.PREPARANDO)
                fase(FaseTranscricao.TRANSCREVENDO)
                resultado = resultadoFalso()
                return resultado
            }
        }
    }

    override suspend fun salvarAnotacao(
        recordingId: Long, resultado: TranscricaoResultado, titulo: String, texto: String,
    ): Long {
        textoSalvo = texto
        return anotacao.insert(
            RecordingAnnotationEntity(
                recordingId = recordingId, createdAtMillis = 1L, titulo = titulo, texto = texto,
                raw = resultado.raw, duracaoInferenciaMs = resultado.duracaoInferenciaMs,
                modelo = resultado.modeloNome, idioma = resultado.idioma, tokens = resultado.tokens,
            ),
        )
    }
}

internal fun gravacaoDeTeste() = Recording(
    id = 42L, title = "Reunião", path = "/data/gravacoes/reuniao.m4a", format = RecordingFormat.M4A,
    durationMillis = 60_000L, sizeBytes = 1_000_000L, createdAtMillis = 1L,
)
