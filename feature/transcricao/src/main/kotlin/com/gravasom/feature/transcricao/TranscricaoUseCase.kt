package com.gravasom.feature.transcricao

import android.os.SystemClock
import com.gravasom.core.data.db.AnnotationDao
import com.gravasom.core.data.db.RecordingAnnotationEntity
import com.gravasom.core.domain.repository.RecordingRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resultado completo da transcrição, pronto para a tela e para a anotação. */
data class TranscricaoResultado(
    val segmentos: List<Segmento>,
    val resumo: String,
    val raw: String,
    val tokens: Int,
    val duracaoInferenciaMs: Long,
    val modeloNome: String,
    val idioma: String,
    val comTimestamps: Boolean,
)

/** Fase corrente da transcrição — a tela usa para trocar o estado (Preparando → Transcrevendo). */
enum class FaseTranscricao { PREPARANDO, TRANSCREVENDO }

/**
 * Orquestra a transcrição local (F7): arquivo da gravação → PCM 16 kHz mono ([PcmParaWhisper]) →
 * whisper.cpp ([TranscricaoWhisper]) → resumo formatado ([ResumoFormatter]) → anotação no Room.
 * Cadeia não destrutiva (ADR-008): a gravação original é só lida — o SHA-256 fica intacto.
 *
 * `open` de propósito: os testes de host do ViewModel substituem transcrever/salvarAnotacao
 * (o JNI do whisper não carrega na JVM — a inferência real só existe no aparelho).
 */
open class TranscricaoUseCase @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val annotationDao: AnnotationDao,
) {

    /**
     * Transcreve SEM salvar — o resumo volta editável para a tela; a anotação só nasce em
     * [salvarAnotacao] quando o usuário confirma (Gate 7: “campo editável antes de salvar”).
     * [cancelado] interrompe o preparo do áudio entre blocos; a inferência em si (whisper.cpp
     * síncrono) roda até o fim — se o usuário cancelar durante ela, o resultado é descartado.
     */
    suspend fun transcrever(
        recordingId: Long,
        modeloPath: String,
        modeloNome: String,
        idioma: String = "pt",
        comTimestamps: Boolean = true,
        cancelado: () -> Boolean = { false },
        onFase: (FaseTranscricao) -> Unit = {},
    ): TranscricaoResultado? = transcreverAberta(
        recordingId, modeloPath, modeloNome, idioma, comTimestamps, cancelado, onFase,
    )

    /** Gancho de herança para os testes de host (mesmo comportamento — ver doc da classe). */
    protected open suspend fun transcreverAberta(
        recordingId: Long,
        modeloPath: String,
        modeloNome: String,
        idioma: String,
        comTimestamps: Boolean,
        cancelado: () -> Boolean,
        onFase: (FaseTranscricao) -> Unit,
    ): TranscricaoResultado? {
        val recording = recordingRepository.getById(recordingId)
            ?: throw GravacaoNaoEncontradaException(recordingId)

        // Fase 1 — preparar áudio (cancelável).
        onFase(FaseTranscricao.PREPARANDO)
        val pcm = withContext(Dispatchers.IO) { PcmParaWhisper.converter(File(recording.path), cancelado) }
            ?: throw AudioNaoDecodificavelException(recording.path)

        // Fase 2 — inferência (não interrompível; roda fora do main). Cancelamento aqui
        // descarta o resultado no fim, sem salvar nada.
        onFase(FaseTranscricao.TRANSCREVENDO)
        return withContext(Dispatchers.Default) {
            val inicio = SystemClock.elapsedRealtime()
            TranscricaoWhisper(modeloPath).use { sessao ->
                val segmentos = sessao.transcrever(pcm.buffer, pcm.nShorts, idioma)
                val tokens = sessao.contarTokens()
                val duracao = SystemClock.elapsedRealtime() - inicio
                if (cancelado()) return@use null
                TranscricaoResultado(
                    segmentos = segmentos,
                    resumo = ResumoFormatter.resumo(segmentos, comTimestamps),
                    raw = ResumoFormatter.bruto(segmentos),
                    tokens = tokens,
                    duracaoInferenciaMs = duracao,
                    modeloNome = modeloNome,
                    idioma = idioma,
                    comTimestamps = comTimestamps,
                )
            }
        }
    }

    /** Salva a anotação com o TEXTO EDITADO na tela; devolve o id (Gate 7: edição antes de salvar). */
    open suspend fun salvarAnotacao(
        recordingId: Long,
        resultado: TranscricaoResultado,
        titulo: String = TITULO_PADRAO,
        texto: String,
    ): Long {
        val entity = RecordingAnnotationEntity(
            recordingId = recordingId,
            createdAtMillis = System.currentTimeMillis(),
            titulo = titulo,
            texto = texto,
            raw = resultado.raw,
            duracaoInferenciaMs = resultado.duracaoInferenciaMs,
            modelo = resultado.modeloNome,
            idioma = resultado.idioma,
            tokens = resultado.tokens,
        )
        return annotationDao.insert(entity)
    }

    class GravacaoNaoEncontradaException(id: Long) : IllegalStateException("Gravação $id não encontrada")
    class AudioNaoDecodificavelException(caminho: String) : IllegalStateException("Áudio não decodificável: $caminho")

    companion object {
        const val TITULO_PADRAO = "Transcrição automática"
    }
}
