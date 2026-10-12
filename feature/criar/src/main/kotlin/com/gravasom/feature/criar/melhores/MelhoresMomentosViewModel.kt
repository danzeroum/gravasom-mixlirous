package com.gravasom.feature.criar.melhores

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.gravasom.core.domain.marker.MarkerRepository
import com.gravasom.core.service.mix.CriacaoWorker
import com.gravasom.feature.criar.ChavesNav
import com.gravasom.feature.criar.CriarSonsUseCases
import com.gravasom.feature.criar.Receitas
import com.gravasom.feature.criar.TabCriacaoViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Aba Melhores (F6): maior energia até o alvo, com os marcadores do usuário
 * como trechos OBRIGATÓRIOS. Regra do roteiro: a duração alvo precisa ser
 * ≥ soma dos trechos selecionados ([CriarSonsUseCases.validarMelhores]).
 */
@HiltViewModel
class MelhoresMomentosViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val markers: MarkerRepository,
    workManager: WorkManager,
) : TabCriacaoViewModel(savedState, workManager) {

    val duracaoAlvoS = MutableStateFlow(30)

    /** Marcadores da gravação, pares achatados [início, fim, …] em ms. */
    val marcadoresMs = MutableStateFlow<LongArray>(longArrayOf())

    /** Quantidade de marcadores (para a linha informativa da aba). */
    val quantidadeMarcadores = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            val lista = runCatching { markers.list(recordingId) }.getOrDefault(emptyList())
            // Mesma janela padrão da receita (4 s ao redor do marcador): o trecho
            // obrigatório é [marcador, marcador + 4000 ms] — fonte: CriarSonsViewModel.
            marcadoresMs.value = lista
                .flatMap { listOf(it.positionMillis, it.positionMillis + TRECHO_MARCADOR_MS) }
                .toLongArray()
            quantidadeMarcadores.value = lista.size
        }
    }

    fun iniciarCriacao() {
        val erro = CriarSonsUseCases.validarMelhores(duracaoAlvoS.value, marcadoresMs.value)
            ?: if (recordingId <= 0) "gravação de origem inválida" else null
        if (erro != null) {
            erroValidacao.value = erro
            return
        }
        erroValidacao.value = null
        val data = baseBuilder(Receitas.montar(duracaoAlvoS = duracaoAlvoS.value, crossfadeMs = 1000L))
            .put(CriacaoWorker.KEY_MODO, CriacaoWorker.MODO_MELHORES)
            .put(CriacaoWorker.KEY_MARCADORES_MS, marcadoresMs.value)
            .put(CriacaoWorker.KEY_FINGERPRINT_ANTERIOR, fingerprintAnterior.value ?: floatArrayOf())
            .build()
        enfileirar(data, "criacao_melhores_${System.currentTimeMillis()}")
    }

    fun refazerIgual() {
        val receita = receitaNormalizada.value ?: return
        if (fingerprintAnterior.value == null) return
        val data = baseBuilder(receita)
            .put(CriacaoWorker.KEY_MODO, CriacaoWorker.MODO_MELHORES)
            .put(CriacaoWorker.KEY_MARCADORES_MS, marcadoresMs.value)
            .put(CriacaoWorker.KEY_FINGERPRINT_ANTERIOR, fingerprintAnterior.value!!)
            .build()
        enfileirar(data, "criacao_melhores_refazer_${System.currentTimeMillis()}")
    }

    private companion object {
        const val TRECHO_MARCADOR_MS = 4000L
    }
}
