package com.gravasom.feature.criar

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gravasom.core.data.db.MixAnalysisStore
import com.gravasom.core.domain.marker.MarkerRepository
import com.gravasom.core.domain.repository.RecordingRepository
import com.gravasom.core.mixengine.MotorMixlirous
import com.gravasom.core.service.mix.AnaliseMixWorker
import com.gravasom.core.service.mix.CriacaoWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Tela de receita (F3) — "Criar versão" de uma gravação.
 *
 * Correção do bug registrado no roteiro: TODOS os parâmetros do formulário
 * (duração, compassos, fator, crossfade, marcadores em ms, receita JSON) são
 * serializados no `workDataOf` do worker. Antes, parte deles se perdia e o
 * render ignorava o que o usuário ajustou; a verificação é o Gate 4 (mudar a
 * duração alvo muda o resultado).
 *
 * A ValidationLayer do motor é autoridade final (mesma receita, duas barreiras):
 * aqui o crossfade > 3000 ms é recusado com mensagem clara ANTES de enfileirar,
 * e o worker revalida (`validarReceita`) antes de renderizar.
 */
@HiltViewModel
class CriarSonsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repo: RecordingRepository,
    private val markers: MarkerRepository,
    private val mixStore: MixAnalysisStore,
    private val workManager: WorkManager,
) : ViewModel() {

    val recordingId: Long = savedState.get<Long>(ChavesNav.RECORDING_ID) ?: -1L

    /** Modo de criação: remix (F3) | loop | textura | melhores (F6). */
    val modo = MutableStateFlow(CriacaoWorker.MODO_REMIX)

    // ——— Campos da receita (tela §4 do roteiro) ———
    val duracaoSegundos = MutableStateFlow(DURACAO_PADRAO_S)
    val compassos = MutableStateFlow(COMPASSOS_PADRAO)
    val fator = MutableStateFlow(FATOR_PADRAO)
    val crossfadeMs = MutableStateFlow(CROSSFADE_PADRAO_MS)
    val blocosPorBatida = MutableStateFlow(BLOCOS_PADRAO)
    val destino = MutableStateFlow(DESTINO_PADRAO)

    /** Erro de validação visível (null = válido). Mensagem clara, sem jargão. */
    val erroValidacao = MutableStateFlow<String?>(null)

    /** true enquanto o worker de criação roda. */
    val executando = MutableStateFlow(false)

    /** Progresso do render (0..1) quando observável. */
    val progresso = MutableStateFlow(0f)

    /** Mensagem final de sucesso ("vira gravação nova"). */
    val resultado = MutableStateFlow<String?>(null)

    /** Etiqueta "120 BPM · Lá menor" da ORIGEM (análise F2, quando existe). */
    val etiquetaOrigem = MutableStateFlow<String?>(null)

    /** Receita normalizada do último render (para "Refazer igual"). */
    val receitaNormalizada = MutableStateFlow<String?>(null)

    /** Fingerprint do último render (vetor plano para o Data do WorkManager). */
    val fingerprintAnterior = MutableStateFlow<FloatArray?>(null)

    /** Distância do último "Refazer igual" (< 0,05 = mesmo som). */
    val distancia = MutableStateFlow<Double?>(null)

    /** Avisos não bloqueantes devolvidos pelo motor. */
    val avisos = MutableStateFlow<List<String>>(emptyList())

    private val _erros = MutableStateFlow<String?>(null)
    val erros: StateFlow<String?> = _erros

    init {
        observarEtiqueta()
    }

    /** Etiqueta da origem; dispara a análise F2 se ainda não existe (Gate 3). */
    private fun observarEtiqueta() {
        if (recordingId <= 0) return
        viewModelScope.launch {
            if (!mixStore.has(recordingId)) enfileirarAnalise()
            mixStore.observe(recordingId).collect { analise ->
                val rotulo = analise?.etiqueta
                etiquetaOrigem.value = rotulo ?: "análise em andamento…"
            }
        }
    }

    private fun enfileirarAnalise() {
        val request = OneTimeWorkRequestBuilder<AnaliseMixWorker>()
            .setInputData(workDataOf(AnaliseMixWorker.KEY_RECORDING_ID to recordingId))
            .addTag(AnaliseMixWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(
            "analise_mix_$recordingId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Validação espelhada da ValidationLayer (o motor revalida no worker). */
    fun validar(): String? = when {
        duracaoSegundos.value <= 0 -> "A duração alvo precisa ser maior que zero."
        crossfadeMs.value > CROSSFADE_LIMITE_MS ->
            "Crossfade de ${crossfadeMs.value} ms é recusado: o limite é $CROSSFADE_LIMITE_MS ms."
        modo.value == CriacaoWorker.MODO_TEXTURA && (fator.value < FATOR_MIN || fator.value > FATOR_MAX) ->
            "O fator da textura precisa estar entre ${FATOR_MIN}× e ${FATOR_MAX}×."
        modo.value == CriacaoWorker.MODO_LOOP && (compassos.value < COMPASSOS_MIN || compassos.value > COMPASSOS_MAX) ->
            "O loop precisa de $COMPASSOS_MIN a $COMPASSOS_MAX compassos."
        else -> null
    }

    /**
     * Monta a receita JSON: base do motor por destino (destinos válidos:
     * "reels" | "shorts" | "streaming" | "voz") + campos do formulário por cima.
     * Sem motor (ABI ausente), cai na receita manual com os MESMOS limites — o
     * worker revalida de qualquer forma.
     */
    fun montarReceita(): String {
        val base = runCatching {
            MotorMixlirous.obter().receitaParaDestino(destino.value, duracaoSegundos.value.toULong())
        }.getOrElse { receitaManual() }
        return runCatching {
            val json = JSONObject(base)
            json.put(
                "target_duration",
                JSONObject().put("secs", duracaoSegundos.value.toLong()).put("nanos", 0),
            )
            json.getJSONObject("crossfade").put("max_duration_ms", crossfadeMs.value.toInt())
            json.getJSONObject("selection").put("block_size_beats", blocosPorBatida.value)
            json.toString()
        }.getOrElse { receitaManual() }
    }

    /** Receita manual (fallback sem motor), forma verificada no host. */
    private fun receitaManual(): String = JSONObject().apply {
        put(
            "target_duration",
            JSONObject().put("secs", duracaoSegundos.value.toLong()).put("nanos", 0),
        )
        put(
            "crossfade",
            JSONObject()
                .put("enabled", crossfadeMs.value > 0)
                .put("max_duration_ms", crossfadeMs.value.toInt())
                .put("curve", "constant_power"),
        )
        put(
            "mastering",
            JSONObject()
                .put("lufs_target", if (destino.value == "voz") -16.0 else -14.0)
                .put("peak_db", -1.0)
                .put("enable_limiting", true)
                .put("compression_ratio", 2.0),
        )
        put(
            "selection",
            JSONObject()
                .put("min_strong_beat_percentile", 0.8)
                .put("block_size_beats", blocosPorBatida.value)
                .put("preserve_intro_ms", 3000)
                .put("preserve_outro_ms", 3000),
        )
        put(
            "format",
            JSONObject().put("sample_rate", 44100).put("channels", 2).put("bit_depth", 24).put("codec", "WAV"),
        )
        put(
            "tuning",
            JSONObject()
                .put("enabled", false)
                .put("mode", "disabled")
                .put("max_global_cents", 50.0)
                .put("min_confidence", 0.7)
                .put("force_tonic_hz", JSONObject.NULL)
                .put("force_mode", JSONObject.NULL),
        )
    }.toString()

    /**
     * Enfileira o [CriacaoWorker] com TODOS os parâmetros do formulário — a
     * correção do bug: nada fica só na UI.
     */
    fun iniciarCriacao() {
        val erro = validar()
        if (erro != null) {
            erroValidacao.value = erro
            return
        }
        erroValidacao.value = null
        if (recordingId <= 0) {
            _erros.value = "gravação de origem inválida"
            return
        }
        val receita = montarReceita()
        executando.value = true
        progresso.value = 0f
        resultado.value = null
        distancia.value = null
        avisos.value = emptyList()

        viewModelScope.launch {
            // Marcadores do usuário (obrigatórios em "melhores momentos"), JÁ em ms.
            val marcadoresMs: LongArray =
                if (modo.value == CriacaoWorker.MODO_MELHORES) carregarMarcadoresMs() else longArrayOf()

            val data = workDataOf(
                CriacaoWorker.KEY_RECORDING_ID to recordingId,
                CriacaoWorker.KEY_MODO to modo.value,
                // bug corrigido: a receita inteira (duração/compassos/fator/crossfade
                // já normalizados pela ValidationLayer) vai no workData…
                CriacaoWorker.KEY_RECEITA_JSON to receita,
                // …e os parâmetros crus também (fins de diagnóstico e de tela).
                CriacaoWorker.KEY_COMPASSOS to compassos.value,
                CriacaoWorker.KEY_FATOR to fator.value,
                CriacaoWorker.KEY_MARCADORES_MS to marcadoresMs,
                CriacaoWorker.KEY_FINGERPRINT_ANTERIOR to (fingerprintAnterior.value ?: floatArrayOf()),
            )
            val request = OneTimeWorkRequestBuilder<CriacaoWorker>()
                .setInputData(data)
                .addTag(CriacaoWorker.TAG)
                .build()
            workManager.enqueueUniqueWork(
                "criacao_${System.currentTimeMillis()}",
                ExistingWorkPolicy.KEEP,
                request,
            )
            observar(request.id)
        }
    }

    /** "Refazer igual": MESMA receita normalizada + fingerprint anterior → < 0,05. */
    fun refazerIgual() {
        val receita = receitaNormalizada.value
        if (receita == null || fingerprintAnterior.value == null) {
            _erros.value = "ainda não há render para refazer"
            return
        }
        executando.value = true
        progresso.value = 0f
        distancia.value = null
        viewModelScope.launch {
            val data = workDataOf(
                CriacaoWorker.KEY_RECORDING_ID to recordingId,
                CriacaoWorker.KEY_MODO to modo.value,
                CriacaoWorker.KEY_RECEITA_JSON to receita,
                CriacaoWorker.KEY_MARCADORES_MS to
                    (if (modo.value == CriacaoWorker.MODO_MELHORES) carregarMarcadoresMs() else longArrayOf()),
                CriacaoWorker.KEY_FINGERPRINT_ANTERIOR to fingerprintAnterior.value!!,
            )
            val request = OneTimeWorkRequestBuilder<CriacaoWorker>()
                .setInputData(data)
                .addTag(CriacaoWorker.TAG)
                .build()
            workManager.enqueueUniqueWork(
                "criacao_refazer_${System.currentTimeMillis()}",
                ExistingWorkPolicy.KEEP,
                request,
            )
            observar(request.id)
        }
    }

    private suspend fun carregarMarcadoresMs(): LongArray {
        val daGravacao = runCatching { markers.list(recordingId) }.getOrDefault(emptyList())
        return daGravacao.flatMap { listOf(it.positionMillis, it.positionMillis + DURACAO_MARCADOR_PADRAO_MS) }
            .toLongArray()
    }

    /** Observa o trabalho enfileirado: progresso, erros e saída. */
    private fun observar(idTrabalho: java.util.UUID) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(idTrabalho).collect { info ->
                when (info?.state) {
                    WorkInfo.State.RUNNING -> {
                        val p = info.progress.getFloat("progresso", -1f)
                        if (p >= 0f) progresso.value = p
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        executando.value = false
                        progresso.value = 1f
                        val novaId = info.outputData.getLong(CriacaoWorker.KEY_NOVA_GRAVACAO_ID, -1L)
                        resultado.value =
                            if (novaId > 0) "Render concluído — virou gravação nova na biblioteca." else "Render concluído."
                        val fp = info.outputData.getFloatArray(CriacaoWorker.KEY_FINGERPRINT)
                        if (fp != null) fingerprintAnterior.value = fp
                        val d = info.outputData.getDouble(CriacaoWorker.KEY_DISTANCIA, Double.NaN)
                        distancia.value = if (d.isNaN()) null else d
                        val av = info.outputData.getString(CriacaoWorker.KEY_AVISOS)
                        avisos.value = av?.takeIf { it.isNotBlank() }?.split(" | ") ?: emptyList()
                        receitaNormalizada.value = info.outputData.getString(CriacaoWorker.KEY_RECEITA_NORMALIZADA)
                            ?: receitaNormalizada.value
                    }
                    WorkInfo.State.FAILED -> {
                        executando.value = false
                        val recusado = info.outputData.getBoolean(CriacaoWorker.KEY_RECUSADO, false)
                        val msg = info.outputData.getString(CriacaoWorker.KEY_ERROR) ?: "render falhou"
                        if (recusado) erroValidacao.value = msg else _erros.value = msg
                    }
                    WorkInfo.State.CANCELLED -> {
                        executando.value = false
                        _erros.value = "render cancelado — nada foi gravado"
                    }
                    else -> Unit
                }
            }
        }
    }

    fun consumirErro() {
        _erros.value = null
    }

    companion object {
        const val DURACAO_PADRAO_S = 30
        const val COMPASSOS_PADRAO = 4
        const val FATOR_PADRAO = 4f
        const val CROSSFADE_PADRAO_MS = 1000L
        const val BLOCOS_PADRAO = 4
        const val DESTINO_PADRAO = "reels"

        /** Limites da ValidationLayer do motor (fonte da verdade: os newtypes Rust). */
        const val CROSSFADE_LIMITE_MS = 3000L
        const val FATOR_MIN = 2f
        const val FATOR_MAX = 8f
        const val COMPASSOS_MIN = 1
        const val COMPASSOS_MAX = 64

        /** Trecho obrigatório padrão ao redor de cada marcador do usuário. */
        const val DURACAO_MARCADOR_PADRAO_MS = 4000L
    }
}

/** Chaves de navegação compartilhadas com o NavHost. */
object ChavesNav {
    const val RECORDING_ID = "recordingId"
}
