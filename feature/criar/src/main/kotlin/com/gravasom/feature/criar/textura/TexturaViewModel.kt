package com.gravasom.feature.criar.textura

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.gravasom.core.domain.repository.RecordingRepository
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
 * Aba Texturas (F6): fundo contínuo 2×–8× derivado da gravação.
 * Fator ∈ [2, 8]; a ORIGEM precisa ter ≥ 5 s ([CriarSonsUseCases.validarTextura]).
 */
@HiltViewModel
class TexturaViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: RecordingRepository,
    workManager: WorkManager,
) : TabCriacaoViewModel(savedState, workManager) {

    val fator = MutableStateFlow(TexturaConfig().fator)

    /** Duração da origem (ms) para a regra "Duração mínima 5 s". */
    val duracaoOrigemMs = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            duracaoOrigemMs.value = repo.getById(recordingId)?.durationMillis ?: 0L
        }
    }

    fun iniciarCriacao() {
        val erro = CriarSonsUseCases.validarTextura(fator.value, duracaoOrigemMs.value)
        if (erro != null) {
            erroValidacao.value = erro
            return
        }
        if (recordingId <= 0) {
            erroValidacao.value = "gravação de origem inválida"
            return
        }
        erroValidacao.value = null
        val receita = Receitas.montar(duracaoAlvoS = 30, crossfadeMs = TexturaConfig().envelopeMs)
        val data = baseBuilder(receita)
            .put(CriacaoWorker.KEY_MODO, CriacaoWorker.MODO_TEXTURA)
            .put(CriacaoWorker.KEY_FATOR, fator.value)
            .put(CriacaoWorker.KEY_FINGERPRINT_ANTERIOR, fingerprintAnterior.value ?: floatArrayOf())
            .build()
        enfileirar(data, "criacao_textura_${System.currentTimeMillis()}")
    }

    fun refazerIgual() {
        val receita = receitaNormalizada.value ?: return
        if (fingerprintAnterior.value == null) return
        val data = baseBuilder(receita)
            .put(CriacaoWorker.KEY_MODO, CriacaoWorker.MODO_TEXTURA)
            .put(CriacaoWorker.KEY_FATOR, fator.value)
            .put(CriacaoWorker.KEY_FINGERPRINT_ANTERIOR, fingerprintAnterior.value!!)
            .build()
        enfileirar(data, "criacao_textura_refazer_${System.currentTimeMillis()}")
    }
}
