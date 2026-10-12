package com.gravasom.feature.criar.loop

import androidx.lifecycle.SavedStateHandle
import androidx.work.workDataOf
import com.gravasom.feature.criar.ChavesNav
import com.gravasom.feature.criar.CriarSonsUseCases
import com.gravasom.feature.criar.Receitas
import com.gravasom.feature.criar.TabCriacaoViewModel
import com.gravasom.core.service.mix.CriacaoWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Aba Loops (F6): N compassos × repetições com emenda sem clique.
 * Repetições ∈ [2, 50]; fade ≤ 2000 ms por emenda ([CriarSonsUseCases.validarLoop]).
 */
@HiltViewModel
class LoopViewModel @Inject constructor(
    savedState: SavedStateHandle,
    workManager: androidx.work.WorkManager,
) : TabCriacaoViewModel(savedState, workManager) {

    val compassos = MutableStateFlow(LoopConfig().compassos)
    val repeticoes = MutableStateFlow(LoopConfig().repeticoes)
    val fadeMs = MutableStateFlow(LoopConfig().fadeMs)

    fun iniciarCriacao() {
        val erro = CriarSonsUseCases.validarLoop(repeticoes.value, fadeMs.value)
        if (erro != null) {
            erroValidacao.value = erro
            return
        }
        if (recordingId <= 0) {
            erroValidacao.value = "gravação de origem inválida"
            return
        }
        erroValidacao.value = null
        val receita = Receitas.montar(duracaoAlvoS = 30, crossfadeMs = fadeMs.value)
        val data = baseBuilder(receita)
            .put(CriacaoWorker.KEY_MODO, CriacaoWorker.MODO_LOOP)
            .put(CriacaoWorker.KEY_COMPASSOS, compassos.value)
            .put(CriacaoWorker.KEY_REPETICOES, repeticoes.value)
            .put(
                CriacaoWorker.KEY_FINGERPRINT_ANTERIOR,
                fingerprintAnterior.value ?: floatArrayOf(),
            )
            .build()
        enfileirar(data, "criacao_loop_${System.currentTimeMillis()}")
    }

    /** Re-enfileira com a MESMA receita normalizada + fingerprint (mesmo som < 0,05). */
    fun refazerIgual() {
        val receita = receitaNormalizada.value ?: return
        if (fingerprintAnterior.value == null) return
        val data = baseBuilder(receita)
            .put(CriacaoWorker.KEY_MODO, CriacaoWorker.MODO_LOOP)
            .put(CriacaoWorker.KEY_COMPASSOS, compassos.value)
            .put(CriacaoWorker.KEY_REPETICOES, repeticoes.value)
            .put(CriacaoWorker.KEY_FINGERPRINT_ANTERIOR, fingerprintAnterior.value!!)
            .build()
        enfileirar(data, "criacao_loop_refazer_${System.currentTimeMillis()}")
    }
}
