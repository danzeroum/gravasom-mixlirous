package com.gravasom.feature.criar.mescla

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.gravasom.core.domain.model.Recording
import com.gravasom.core.domain.repository.RecordingRepository
import com.gravasom.core.service.mix.CriacaoWorker
import com.gravasom.feature.criar.ChavesNav
import com.gravasom.feature.criar.CriarSonsUseCases
import com.gravasom.feature.criar.Receitas
import com.gravasom.feature.criar.TabCriacaoViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Aba Mescla (F6): trilha A (a gravação aberta) + trilha B (escolhida na lista)
 * + balance 0–1. Avisos de tons/andamento vêm do [avisosDeMescla] ANTES de
 * renderizar: com tons incompatíveis e decisão RECUSAR o motor não renderiza —
 * a aba mostra "Tons incompatíveis: continuar mesmo assim?" e re-enfileira
 * com SEGUIR quando o usuário confirma (HITL do roteiro).
 */
@HiltViewModel
class MesclaViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: RecordingRepository,
    workManager: WorkManager,
) : TabCriacaoViewModel(savedState, workManager) {

    val balance = MutableStateFlow(MesclaConfig().balance)
    val trilhaBId = MutableStateFlow(0L)

    /** Gravações candidatas a trilha B (a origem entra fora da lista). */
    val candidatas: StateFlow<List<Recording>> = repo.observeAll()
        .map { lista -> lista.filter { it.id != recordingId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Título da trilha B escolhida (para a linha da UI). */
    val trilhaBTitulo: StateFlow<String?> = candidatas
        .map { lista -> lista.firstOrNull { it.id == trilhaBId.value }?.title }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun escolherTrilhaB(recording: Recording) {
        trilhaBId.value = recording.id
    }

    fun iniciarCriacao() {
        val erro = CriarSonsUseCases.validarMescla(balance.value)
            ?: if (trilhaBId.value <= 0) "Escolha a trilha B na lista" else null
        if (erro != null) {
            erroValidacao.value = erro
            return
        }
        if (recordingId <= 0) {
            erroValidacao.value = "gravação de origem inválida"
            return
        }
        erroValidacao.value = null
        val data = baseBuilder(Receitas.montar(duracaoAlvoS = 60, crossfadeMs = 1000L))
            .put(CriacaoWorker.KEY_MODO, CriacaoWorker.MODO_MESCLA)
            .put(CriacaoWorker.KEY_RECORDING_ID_B, trilhaBId.value)
            .put(CriacaoWorker.KEY_BALANCE, balance.value)
            .put(CriacaoWorker.KEY_DECISAO_TONS, CriacaoWorker.DECISAO_RECUSAR)
            .put(CriacaoWorker.KEY_FINGERPRINT_ANTERIOR, fingerprintAnterior.value ?: floatArrayOf())
            .build()
        enfileirar(data, "criacao_mescla_${System.currentTimeMillis()}")
    }

    /** "Continuar mesmo assim?" confirmado: re-enfileira com SEGUIR. */
    fun confirmarTonsIncompativeis() {
        if (trilhaBId.value <= 0 || recordingId <= 0) return
        val data = baseBuilder(Receitas.montar(duracaoAlvoS = 60, crossfadeMs = 1000L))
            .put(CriacaoWorker.KEY_MODO, CriacaoWorker.MODO_MESCLA)
            .put(CriacaoWorker.KEY_RECORDING_ID_B, trilhaBId.value)
            .put(CriacaoWorker.KEY_BALANCE, balance.value)
            .put(CriacaoWorker.KEY_DECISAO_TONS, CriacaoWorker.DECISAO_SEGUIR)
            .put(CriacaoWorker.KEY_FINGERPRINT_ANTERIOR, fingerprintAnterior.value ?: floatArrayOf())
            .build()
        enfileirar(data, "criacao_mescla_seguir_${System.currentTimeMillis()}")
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
