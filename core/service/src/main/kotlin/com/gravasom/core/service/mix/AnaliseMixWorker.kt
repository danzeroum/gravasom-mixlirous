package com.gravasom.core.service.mix

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gravasom.core.data.db.MixAnalysisStore
import com.gravasom.core.domain.repository.RecordingRepository
import com.gravasom.core.mixengine.MotorMixlirous
import com.gravasom.core.service.analysis.PcmDecoder
import com.gravasom.core.service.library.RecordingFiles
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Analise musical (F2) em background: decodifica a gravacao, manda o PCM mono
 * ao motor Rust (`MixEngine.analisar`) e salva BPM/tom/energia no Room (v6).
 *
 * Contratos do roteiro (docs/teste-aparelho-real-mixlirous.md §3):
 * - Progresso publicado por [setProgress] (fração 0..1);
 * - Cancelar no meio NÃO deixa etiqueta parcial: a escrita no Room é única,
 *   no fim; o cancelamento derruba a coroutine antes do upsert;
 * - UniFFI lança exceção (não Result): toda chamada ao motor passa por
 *   [runCatching] e o erro real volta em KEY_ERROR + stacktrace, sem crash.
 */
class AnaliseMixWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AnaliseMixWorkerEntryPoint {
        fun repository(): RecordingRepository
        fun mixAnalysisStore(): MixAnalysisStore
        fun files(): RecordingFiles
    }

    private fun deps(): AnaliseMixWorkerEntryPoint =
        EntryPointAccessors.fromApplication(applicationContext, AnaliseMixWorkerEntryPoint::class.java)

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val recordingId = inputData.getLong(KEY_RECORDING_ID, -1L)
        if (recordingId <= 0) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "recordingId ausente"))
        }
        if (!MotorMixlirous.disponivel) {
            return@withContext Result.failure(
                workDataOf(KEY_ERROR to "libmixlirous_mobile.so ausente nesta ABI"),
            )
        }
        val entry = deps()
        val recording = entry.repository().getById(recordingId)
        if (recording == null) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "gravação $recordingId não existe"))
        }
        val arquivo = runCatching { File(entry.files().playablePath(recording)) }.getOrNull()
        if (arquivo == null || !arquivo.isFile) {
            return@withContext Result.failure(
                workDataOf(KEY_ERROR to "arquivo da gravação $recordingId ilegível (cofre fechado?)"),
            )
        }

        val inicio = System.currentTimeMillis()
        try {
            setProgress(workDataOf(KEY_PROGRESSO to 0.05f))
            val acumulado = DecodificadorMono()
            val quadrosDecodificados = java.util.concurrent.atomic.AtomicLong(0L)
            val totalQuadrosEstimado = java.util.concurrent.atomic.AtomicLong(-1L)
            // O progresso é publicado por um ticker: o PcmSink não é suspend.
            val publicadorProgresso = launch {
                while (isActive) {
                    val total = totalQuadrosEstimado.get()
                    if (total > 0) {
                        val fracao = (quadrosDecodificados.get().toDouble() / total).coerceIn(0.0, 0.9)
                        runCatching { setProgress(workDataOf(KEY_PROGRESSO to fracao.toFloat())) }
                    }
                    delay(PASSO_PROGRESSO_MS)
                }
            }
            val decodificou = PcmDecoder.decode(
                arquivo,
                { rate, canais ->
                    acumulado.sampleRate = rate
                    acumulado.definirCanais(canais)
                    // Estimativa para o progresso: duração conhecida × taxa real.
                    totalQuadrosEstimado.set(recording.durationMillis * rate / 1000L)
                },
                { pcm, length ->
                    acumulado.adicionar(pcm, length)
                    quadrosDecodificados.addAndGet((length / maxOf(1, acumulado.canais)).toLong())
                },
                { isStopped },
            )
            publicadorProgresso.cancel()
            if (isStopped) return@withContext Result.failure() // cancelado: nada é salvo
            if (!decodificou || acumulado.pcm.isEmpty() || acumulado.sampleRate == 0) {
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "decodificação não produziu PCM (arquivo corrompido?)"),
                )
            }
            setProgress(workDataOf(KEY_PROGRESSO to 0.9f))

            val analise = runCatching {
                MotorMixlirous.obter().analisar(acumulado.pcm.toList(), acumulado.sampleRate.toUInt())
            }.getOrElse { e ->
                Timber.w(e, "analisar falhou para $recordingId")
                return@withContext Result.failure(
                    workDataOf(
                        KEY_ERROR to (e.message ?: "falha do motor"),
                        KEY_STACKTRACE to e.stackTraceToString(),
                    ),
                )
            }
            coroutineContext.ensureActive() // cancelou entre o motor e o upsert? nada é salvo

            val trechos = analise.energiaPorTrecho.map { Triple(it.inicioS, it.fimS, it.rms) }
            entry.mixAnalysisStore().save(
                recordingId,
                MixAnalysisStore.AnaliseMix(
                    bpm = analise.bpm,
                    bpmConfianca = analise.bpmConfianca,
                    tonica = analise.tonica?.toInt(),
                    modo = analise.modo,
                    loudnessLufs = analise.loudnessLufs,
                    energiaMedia = analise.energiaMedia,
                    energiaDesvio = analise.energiaDesvio,
                    picoDb = analise.picoDb,
                    energiaTrechos = MixAnalysisStore.serializarTrechos(trechos),
                ),
            )
            val rotulo = etiqueta(analise)
            Timber.i("F2 análise %d em %d ms → %s", recordingId, System.currentTimeMillis() - inicio, rotulo)
            Result.success(
                workDataOf(
                    KEY_ROTULO to rotulo,
                    KEY_BPM to (analise.bpm ?: -1f),
                    KEY_TOM to (entry.mixAnalysisStore().get(recordingId)?.rotuloTom ?: ""),
                ),
            )
        } catch (e: CancellationException) {
            // Cancelamento do usuário: nenhum rótulo parcial foi escrito.
            throw e
        } catch (e: Exception) {
            Timber.w(e, "analise inesperada $recordingId")
            Result.failure(
                workDataOf(
                    KEY_ERROR to (e.message ?: "erro inesperado"),
                    KEY_STACKTRACE to e.stackTraceToString(),
                ),
            )
        }
    }

    /** "120 BPM · Lá menor" — partes omitidas quando o motor não confia nelas. */
    internal fun etiqueta(analise: com.gravasom.core.mixengine.motor.AnaliseAudio): String =
        listOfNotNull(
            analise.bpm?.takeIf { (analise.bpmConfianca ?: 0f) >= BPM_CONFIANCA_MINIMA }?.let { "${it.toInt()} BPM" },
            analise.tonica?.let { t ->
                val nomes = NOMES_NOTAS
                val nome = nomes[t.toInt() % 12]
                if (analise.modo == "menor") "$nome menor" else "$nome maior"
            },
        ).joinToString(" · ").ifEmpty { "análise sem BPM/tom confiáveis" }

    /** Acumulador de PCM mono f32 (o motor não lê arquivos; só recebe PCM). */
    internal class DecodificadorMono {
        val pcm = ArrayList<Float>(PCM_CAPACIDADE_INICIAL)
        var sampleRate: Int = 0
        var canais = 1
            private set
        var quadros = 0L
            private set

        fun definirCanais(novos: Int) {
            if (novos >= 1) canais = novos
        }

        fun adicionar(intercalado: ShortArray, length: Int) {
            val quadrosTrecho = length / canais
            for (quadro in 0 until quadrosTrecho) {
                var soma = 0
                for (c in 0 until canais) soma += intercalado[quadro * canais + c].toInt()
                pcm.add(soma.toFloat() / canais / Short.MAX_VALUE)
            }
            quadros += quadrosTrecho
        }

        companion object {
            /** ~1 min de áudio 44,1 kHz mono; ArrayList cresce a partir daqui. */
            const val PCM_CAPACIDADE_INICIAL = 2_646_000
        }
    }

    companion object {
        val NOMES_NOTAS = arrayOf(
            "Dó", "Dó♯", "Ré", "Ré♯", "Mi", "Fá", "Fá♯", "Sol", "Sol♯", "Lá", "Lá♯", "Si",
        )

        /** Publica progresso a cada 1 s de decodificação. */
        const val PASSO_PROGRESSO_MS = 1_000L
        const val KEY_RECORDING_ID = "recording_id"
        const val KEY_PROGRESSO = "progresso"
        const val KEY_ERROR = "erro"
        const val KEY_STACKTRACE = "stacktrace"
        const val KEY_ROTULO = "rotulo"
        const val KEY_BPM = "bpm"
        const val KEY_TOM = "tom"

        /** Abaixo disso o BPM aparece na análise mas NÃO na etiqueta (honestidade do rótulo). */
        const val BPM_CONFIANCA_MINIMA = 0.5f

        /** Nome único para enfileirar/observar todos os trabalhos de análise. */
        const val TAG = "analise_mix"
    }
}
