package com.gravasom.feature.criar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.gravasom.core.service.mix.CriacaoWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Base das abas F6 (Loops / Texturas / Mescla / Melhores): contratos comuns do
 * worker de criação — executando/progresso/resultado/distância/avisos — e a
 * observação do [CriacaoWorker] enfileirado.
 *
 * A aba Receita mantém o [CriarSonsViewModel] próprio (já fechado em gate);
 * as quatro abas novas estendem esta base para NÃO duplicar a máquina de estados.
 */
abstract class TabCriacaoViewModel protected constructor(
    savedState: SavedStateHandle,
    protected val workManager: WorkManager,
) : ViewModel() {

    val recordingId: Long = savedState.get<Long>(ChavesNav.RECORDING_ID) ?: -1L

    val executando = MutableStateFlow(false)
    val progresso = MutableStateFlow(0f)

    /** Mensagem de sucesso (o render vira gravação nova na biblioteca). */
    val resultado = MutableStateFlow<String?>(null)

    /** Distância do último "Refazer igual" (< 0,05 = mesmo som). */
    val distancia = MutableStateFlow<Double?>(null)

    /** Avisos não bloqueantes devolvidos pelo motor. */
    val avisos = MutableStateFlow<List<String>>(emptyList())

    /** Fingerprint do último render (para "Refazer igual" da aba) — público: as abas leem. */
    val fingerprintAnterior = MutableStateFlow<FloatArray?>(null)

    /** Receita normalizada do último render. */
    val receitaNormalizada = MutableStateFlow<String?>(null)

    /** Erro de validação local (mensagem clara, sem jargão); null = válido. */
    val erroValidacao = MutableStateFlow<String?>(null)

    private val _erros = MutableStateFlow<String?>(null)
    val erros: StateFlow<String?> = _erros

    /**
     * HITL da mescla: o motor recusou (tons incompatíveis + decisão RECUSAR);
     * a aba mostra os avisos e pergunta "continuar mesmo assim?".
     */
    data class RecusaDeTons(val mensagem: String, val avisos: List<String>)

    val recusaDeTons = MutableStateFlow<RecusaDeTons?>(null)

    /** Enfileira o render e observa: progresso, sucesso, recusa e falha. */
    protected fun enfileirar(data: Data, nomeUnico: String) {
        executando.value = true
        progresso.value = 0f
        resultado.value = null
        distancia.value = null
        recusaDeTons.value = null
        val request = OneTimeWorkRequestBuilder<CriacaoWorker>()
            .setInputData(data)
            .addTag(CriacaoWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(nomeUnico, ExistingWorkPolicy.KEEP, request)
        observar(request.id)
    }

    /** Builder comum: chave do modo + receita + origem — cada aba acrescenta o seu. */
    protected fun baseBuilder(receitaJson: String): Data.Builder = Data.Builder()
        .putLong(CriacaoWorker.KEY_RECORDING_ID, recordingId)
        .putString(CriacaoWorker.KEY_RECEITA_JSON, receitaJson)

    private fun observar(idTrabalho: java.util.UUID) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(idTrabalho).collect { info ->
                when (info?.state) {
                    WorkInfo.State.RUNNING -> {
                        val p = info.progress.getFloat("progresso", -1f)
                        if (p >= 0f) progresso.value = p
                    }
                    WorkInfo.State.SUCCEEDED -> registrado(info)
                    WorkInfo.State.FAILED -> falhou(info)
                    WorkInfo.State.CANCELLED -> {
                        executando.value = false
                        _erros.value = "render cancelado — nada foi gravado"
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun registrado(info: WorkInfo) {
        executando.value = false
        progresso.value = 1f
        val novaId = info.outputData.getLong(CriacaoWorker.KEY_NOVA_GRAVACAO_ID, -1L)
        resultado.value =
            if (novaId > 0) "Render concluído — virou gravação nova na biblioteca." else "Render concluído."
        info.outputData.getFloatArray(CriacaoWorker.KEY_FINGERPRINT)?.let { fingerprintAnterior.value = it }
        val d = info.outputData.getDouble(CriacaoWorker.KEY_DISTANCIA, Double.NaN)
        distancia.value = if (d.isNaN()) null else d
        val av = info.outputData.getString(CriacaoWorker.KEY_AVISOS)
        avisos.value = av?.takeIf { it.isNotBlank() }?.split(" | ") ?: emptyList()
        receitaNormalizada.value =
            info.outputData.getString(CriacaoWorker.KEY_RECEITA_NORMALIZADA) ?: receitaNormalizada.value
    }

    private fun falhou(info: WorkInfo) {
        executando.value = false
        val msg = info.outputData.getString(CriacaoWorker.KEY_ERROR) ?: "render falhou"
        val recusado = info.outputData.getBoolean(CriacaoWorker.KEY_RECUSADO, false)
        val avisosDaFalha = info.outputData.getString(CriacaoWorker.KEY_AVISOS)
            ?.takeIf { it.isNotBlank() }?.split(" | ") ?: emptyList()
        if (recusado) {
            // HITL da mescla: tons incompatíveis com decisão RECUSAR — a aba pergunta.
            recusaDeTons.value = RecusaDeTons(msg, avisosDaFalha)
        } else {
            _erros.value = msg
        }
    }

    fun consumirErro() {
        _erros.value = null
    }

    fun consumirRecusa() {
        recusaDeTons.value = null
    }
}
