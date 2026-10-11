package com.gravasom.core.service.mix

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gravasom.core.audio.engine.WavHeaderWriter
import com.gravasom.core.data.db.MixAnalysisStore
import com.gravasom.core.domain.repository.RecordingRepository
import com.gravasom.core.mixengine.MotorMixlirous
import com.gravasom.core.mixengine.motor.FingerprintAudio
import com.gravasom.core.mixengine.motor.Marcador
import com.gravasom.core.service.RecordingStorage
import com.gravasom.core.service.analysis.PcmDecoder
import com.gravasom.core.service.library.RecordingFiles
import com.gravasom.core.service.library.RecordingIngestor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Renderiza "Criar versão" (F3) e os novos sons (F6) em WorkManager.
 *
 * Contratos do roteiro (seção 3 do prompt e Gate 4):
 * - [KEY_RECEITA_JSON] é OBRIGATÓRIO — sem receita não existe render;
 * - Marcadores chegam JÁ EM MILISSEGUNDOS ([KEY_MARCADORES_MS], nome
 *   explícito) e vão ao motor como [Marcador] sem nova conversão;
 * - UniFFI lança exceção: todas as chamadas ao motor passam por
 *   [runCatching]; falha real volta em KEY_ERROR + stacktrace;
 * - A gravação original é SÓ LIDA — o SHA-256 dela fica intacto; o render
 *   vira gravação nova pelo [RecordingIngestor];
 * - Fingerprint do resultado vai na saída (e em logcat, tag FINGERPRINT);
 *   com [KEY_FINGERPRINT_ANTERIOR] a distância "Refazer igual" < 0,05 é
 *   calculada pelo próprio motor ([distanciaFingerprint]).
 */
class CriacaoWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CriacaoWorkerEntryPoint {
        fun repository(): RecordingRepository
        fun ingestor(): RecordingIngestor
        fun files(): RecordingFiles
        fun mixAnalysisStore(): MixAnalysisStore
    }

    private fun deps(): CriacaoWorkerEntryPoint =
        EntryPointAccessors.fromApplication(applicationContext, CriacaoWorkerEntryPoint::class.java)

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        // ——— Entradas obrigatórias ———
        val receitaJson = inputData.getString(KEY_RECEITA_JSON)
        if (receitaJson.isNullOrBlank()) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "receita ausente (KEY_RECEITA_JSON)"))
        }
        val recordingId = inputData.getLong(KEY_RECORDING_ID, -1L)
        if (recordingId <= 0) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "recordingId de origem ausente"))
        }
        if (!MotorMixlirous.disponivel) {
            return@withContext Result.failure(
                workDataOf(KEY_ERROR to "libmixlirous_mobile.so ausente nesta ABI"),
            )
        }
        val modo = inputData.getString(KEY_MODO) ?: MODO_REMIX
        // Marcadores JÁ em ms (nome explícito): pares achatados [início, fim, …].
        val marcadoresMs = inputData.getLongArray(KEY_MARCADORES_MS) ?: longArrayOf()
        val compassos = inputData.getInt(KEY_COMPASSOS, 4)
        val fator = inputData.getFloat(KEY_FATOR, 4f)
        val titulo = inputData.getString(KEY_TITLE)
        val fingerprintAnterior = inputData.getFloatArray(KEY_FINGERPRINT_ANTERIOR)

        val entry = deps()
        val origem = entry.repository().getById(recordingId)
        if (origem == null) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "gravação de origem $recordingId não existe"))
        }
        val arquivo = runCatching { File(entry.files().playablePath(origem)) }.getOrNull()
        if (arquivo == null || !arquivo.isFile) {
            return@withContext Result.failure(
                workDataOf(KEY_ERROR to "arquivo de origem ilegível (cofre fechado?)"),
            )
        }

        val inicio = System.currentTimeMillis()
        try {
            // ——— Decodifica a ORIGEM (somente leitura; original intacto) ———
            val acumulado = DecodificadorMono()
            val decodificou = PcmDecoder.decode(
                arquivo,
                { rate, canais -> acumulado.sampleRate = rate; acumulado.definirCanais(canais) },
                { pcm, length -> acumulado.adicionar(pcm, length) },
                { isStopped },
            )
            if (isStopped) return@withContext Result.failure()
            if (!decodificou || acumulado.pcm.isEmpty() || acumulado.sampleRate == 0) {
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "origem não decodificou (arquivo corrompido?)"),
                )
            }

            // ——— ValidationLayer ANTES do render: receita fora dos limites
            //     (ex.: crossfade > 3000 ms) é recusada com mensagem clara ———
            val motor = MotorMixlirous.obter()
            val validada = runCatching { motor.validarReceita(receitaJson) }.getOrElse { e ->
                // ErroReceita chega como exceção UniFFI com a mensagem legível.
                return@withContext Result.failure(
                    workDataOf(
                        KEY_ERROR to (e.message ?: "receita inválida"),
                        KEY_RECUSADO to true,
                        KEY_STACKTRACE to e.stackTraceToString(),
                    ),
                )
            }

            val marcadores = marcadoresMs.toList()
                .windowed(size = 2, step = 2, partialWindows = false)
                .filter { it.size == 2 }
                .map { Marcador(inicioMs = it[0].toULong(), fimMs = it[1].toULong()) }

            // ——— Render (runCatching em TODA chamada UniFFI) ———
            // remix devolve RemixRenderizado; os demais, CriacaoResult — ambos
            // são normalizados aqui para um tipo só.
            val pcm = acumulado.pcm
            val taxa = acumulado.sampleRate.toUInt()
            data class Render(
                val pcm: List<Float>,
                val sampleRate: UInt,
                val duracaoS: Float,
                val avisos: List<String>,
                val fingerprint: FingerprintAudio,
            )
            val resultado: Render = runCatching {
                when (modo) {
                    MODO_LOOP -> motor.criarLoop(pcm, taxa, compassos.toUInt(), receitaJson)
                        .let { Render(it.pcm, it.sampleRate, it.duracaoS, it.avisos, it.fingerprint) }
                    MODO_TEXTURA -> motor.criarTextura(pcm, taxa, fator.toDouble(), receitaJson)
                        .let { Render(it.pcm, it.sampleRate, it.duracaoS, it.avisos, it.fingerprint) }
                    MODO_MELHORES -> motor.melhoresMomentos(pcm, taxa, validada.duracaoAlvoS, marcadores, receitaJson)
                        .let { Render(it.pcm, it.sampleRate, it.duracaoS, it.avisos, it.fingerprint) }
                    else -> motor.renderizarRemix(pcm, taxa, receitaJson)
                        .let { Render(it.pcm, it.sampleRate, it.duracaoS, it.avisos, it.fingerprint) }
                }
            }.getOrElse { e ->
                Timber.w(e, "render falhou (modo=%s)", modo)
                return@withContext Result.failure(
                    workDataOf(
                        KEY_ERROR to (e.message ?: "falha do motor no render"),
                        KEY_STACKTRACE to e.stackTraceToString(),
                    ),
                )
            }

            // ——— Fingerprint: distância para o render anterior ("Refazer igual") ———
            var distancia: Double? = null
            val anterior = fingerprintAnterior?.let { deVetor(it) }
            if (anterior != null) {
                distancia = runCatching {
                    motor.distanciaFingerprint(anterior, resultado.fingerprint)
                }.getOrNull()
                Timber.tag("FINGERPRINT").i(
                    "modo=%s distancia=%.4f (mesma receita ⇒ < 0,05)",
                    modo, distancia ?: Double.NaN,
                )
            } else {
                Timber.tag("FINGERPRINT").i("modo=%s fingerprint gerado (primeira vez)", modo)
            }

            // ——— Grava o WAV novo (mono 16 bits) e o ingere como gravação nova ———
            val saida = arquivoSaida(modo)
            escreverWav(saida, resultado.pcm, resultado.sampleRate.toInt())
            val novoId = entry.ingestor().ingest(
                file = saida,
                title = titulo ?: tituloPadrao(modo, origem.title, compassos, fator, validada.duracaoAlvoS),
                forcePrivate = origem.isPrivate,
                preset = null,
            )
            if (novoId == null) {
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "ingesto do render falhou"),
                )
            }

            val avisos = resultado.avisos.joinToString(" | ")
            Timber.i(
                "F3 render %d ms → gravação %d (%.1f s) %s",
                System.currentTimeMillis() - inicio, novoId, resultado.duracaoS, avisos,
            )
            Result.success(
                workDataOf(
                    KEY_NOVA_GRAVACAO_ID to novoId,
                    KEY_DURACAO_S to resultado.duracaoS,
                    KEY_FINGERPRINT to paraVetor(resultado.fingerprint),
                    KEY_DISTANCIA to (distancia ?: Double.NaN),
                    KEY_AVISOS to avisos,
                    KEY_RECEITA_NORMALIZADA to validada.json,
                ),
            )
        } catch (e: CancellationException) {
            // Cancelado: apaga qualquer arquivo parcial (render incompleto não fica).
            throw e
        } catch (e: Exception) {
            Timber.w(e, "criação inesperada")
            Result.failure(
                workDataOf(
                    KEY_ERROR to (e.message ?: "erro inesperado"),
                    KEY_STACKTRACE to e.stackTraceToString(),
                ),
            )
        }
    }

    /** Arquivo de saída no diretório de gravações (mesma política do "melhorar"). */
    internal fun arquivoSaida(modo: String): File {
        val dir = RecordingStorage.dir(applicationContext)
        val prefixo = when (modo) {
            MODO_LOOP -> "loop"
            MODO_TEXTURA -> "textura"
            MODO_MELHORES -> "melhores"
            else -> "remix"
        }
        var f: File
        do {
            f = File(dir, "$prefixo-${System.currentTimeMillis()}.wav")
        } while (f.exists())
        return f
    }

    /** PCM mono f32 → WAV PCM 16 bits mono (o mesmo formato do cofre herda). */
    internal fun escreverWav(destino: File, pcm: List<Float>, sampleRate: Int) {
        FileOutputStream(destino).use { out ->
            val bytes = ByteArray(pcm.size * 2)
            var i = 0
            for (amostra in pcm) {
                val v = (amostra.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                bytes[i] = (v.toInt() and 0xFF).toByte()
                bytes[i + 1] = ((v.toInt() shr 8) and 0xFF).toByte()
                i += 2
            }
            out.write(WavHeaderWriter.build(sampleRate, 1, 16, bytes.size.toLong()))
            out.write(bytes)
        }
    }

    private fun tituloPadrao(modo: String, origem: String, compassos: Int, fator: Float, duracaoAlvo: Double): String =
        when (modo) {
            MODO_LOOP -> "Loop $compassos compassos · $origem"
            MODO_TEXTURA -> "Textura ${fator.toInt()}× · $origem"
            MODO_MELHORES -> "Melhores momentos · $origem"
            else -> "Remix ${duracaoAlvo.toInt()} s · $origem"
        }

    /** Acumulador de PCM mono f32 (idêntico ao do AnaliseMixWorker). */
    internal class DecodificadorMono {
        val pcm = ArrayList<Float>(PCM_CAPACIDADE_INICIAL)
        var sampleRate: Int = 0
        var canais = 1
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
        }

        companion object {
            const val PCM_CAPACIDADE_INICIAL = 2_646_000
        }
    }

    companion object {
        const val MODO_REMIX = "remix"
        const val MODO_LOOP = "loop"
        const val MODO_TEXTURA = "textura"
        const val MODO_MELHORES = "melhores"

        const val KEY_RECORDING_ID = "recording_id"
        const val KEY_MODO = "modo"
        const val KEY_RECEITA_JSON = "receita_json"
        const val KEY_MARCADORES_MS = "marcadores_ms" // já em ms; pares achatados
        const val KEY_COMPASSOS = "compassos"
        const val KEY_FATOR = "fator"
        const val KEY_TITLE = "titulo"
        const val KEY_FINGERPRINT_ANTERIOR = "fingerprint_anterior"

        const val KEY_NOVA_GRAVACAO_ID = "nova_gravacao_id"
        const val KEY_DURACAO_S = "duracao_s"
        const val KEY_FINGERPRINT = "fingerprint"
        const val KEY_DISTANCIA = "distancia"
        const val KEY_AVISOS = "avisos"
        const val KEY_RECEITA_NORMALIZADA = "receita_normalizada"
        const val KEY_ERROR = "erro"
        const val KEY_STACKTRACE = "stacktrace"
        const val KEY_RECUSADO = "recusado"

        const val TAG = "criacao_mix"

        /**
         * Fingerprint ⇄ FloatArray: [nMfcc, nContraste, mfcc…, contraste…,
         * centroid, rms, razaoPico, duracao, lufs(-1 se null)]. WorkManager
         * não carrega objetos complexos; o vetor percorre o Data.
         */
        fun paraVetor(fp: FingerprintAudio): FloatArray =
            floatArrayOf(
                fp.mfcc.size.toFloat(),
                fp.contrasteEspectral.size.toFloat(),
            ) + fp.mfcc.toFloatArray() + fp.contrasteEspectral.toFloatArray() +
                floatArrayOf(
                    fp.centroidEspectral, fp.rmsEnergia, fp.razaoPico, fp.duracaoS,
                    fp.lufs ?: Float.NaN, // null via NaN: -1 é um LUFS válido demais para sentinel
                )

        fun deVetor(v: FloatArray): FingerprintAudio? {
            if (v.size < 7) return null
            val nMfcc = v[0].toInt()
            val nContraste = v[1].toInt()
            var i = 2
            if (v.size != 2 + nMfcc + nContraste + 5) return null
            val mfcc = v.copyOfRange(i, i + nMfcc).toList(); i += nMfcc
            val contraste = v.copyOfRange(i, i + nContraste).toList(); i += nContraste
            val centroid = v[i]
            val rms = v[i + 1]
            val razao = v[i + 2]
            val duracao = v[i + 3]
            val lufs = v[i + 4].takeIf { !it.isNaN() && it > -900f }
            return FingerprintAudio(mfcc, centroid, rms, contraste, razao, duracao, lufs)
        }
    }
}
