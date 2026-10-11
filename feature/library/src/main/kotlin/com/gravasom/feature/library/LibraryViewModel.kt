package com.gravasom.feature.library

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gravasom.core.data.db.MixAnalysisEntity
import com.gravasom.core.data.db.MixAnalysisStore
import com.gravasom.core.data.metrics.UsageMetrics
import com.gravasom.core.domain.metrics.ShareChannel
import com.gravasom.core.domain.metrics.UsageEventType
import com.gravasom.core.domain.model.Recording
import com.gravasom.core.domain.repository.RecordingRepository
import com.gravasom.core.domain.tags.TagFilter
import com.gravasom.core.domain.usecase.DeleteRecording
import com.gravasom.core.domain.usecase.ListRecordings
import com.gravasom.core.domain.usecase.RenameRecording
import com.gravasom.core.domain.usecase.RestoreRecording
import com.gravasom.core.domain.usecase.SearchRecordings
import com.gravasom.core.service.clip.JoinOutcome
import com.gravasom.core.service.clip.JoinService
import com.gravasom.core.service.library.RecordingFiles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tela Gravacoes. Etapas da jornada: Encontrar, Ouvir e Compartilhar. Hipotese H2 (docs/ux/jornada.md): achar a ultima
 * gravacao em <= 3 toques e ouvir em 1 toque.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: RecordingRepository,
    private val files: RecordingFiles,
    private val metrics: UsageMetrics,
    private val joins: JoinService,
    private val mixStore: MixAnalysisStore,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())

    /** Etiquetas escolhidas no filtro (a gravacao precisa ter TODAS). */
    val selectedTags: StateFlow<Set<String>> = _selectedTags

    /** Etiquetas que existem na biblioteca, as mais usadas primeiro. */
    val availableTags: StateFlow<List<String>> = ListRecordings(repo)()
        .map { TagFilter.available(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    // ——— Analise musical (F2): etiqueta "120 BPM · Lá menor" + filtros de BPM/tom ———

    /** Analises por id de gravacao (null/ausente = ainda não analisada). */
    val mixAnalises: StateFlow<Map<Long, MixAnalysisEntity>> = mixStore.observeAll()
        .map { lista -> lista.associateBy { it.recordingId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    private val _filtroBpm = MutableStateFlow<BpmFaixa?>(null)
    val filtroBpm: StateFlow<BpmFaixa?> = _filtroBpm

    private val _filtroTom = MutableStateFlow<String?>(null)
    val filtroTom: StateFlow<String?> = _filtroTom

    /** Tons detectados na biblioteca (para o filtro). */
    val tomsDisponiveis: StateFlow<List<String>> = mixAnalises
        .map { m -> m.values.mapNotNull { it.rotuloTom }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val recordings: StateFlow<List<Recording>> = combine(
        _query.flatMapLatest { q -> if (q.isBlank()) ListRecordings(repo)() else SearchRecordings(repo)(q.trim()) },
        _selectedTags,
        _filtroBpm,
        _filtroTom,
        mixAnalises,
    ) { list, tags, bpm, tom, analises ->
        TagFilter.apply(list, tags).filter { r ->
            val analise = analises[r.id]
            (bpm?.contem(analise?.bpm) ?: true) && (tom?.let { it == analise?.rotuloTom } ?: true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun setFiltroBpm(faixa: BpmFaixa?) {
        _filtroBpm.value = faixa
    }

    fun setFiltroTom(tom: String?) {
        _filtroTom.value = tom
    }

    fun clearMixFiltros() {
        _filtroBpm.value = null
        _filtroTom.value = null
    }

    fun toggleTag(tag: String) {
        _selectedTags.value = _selectedTags.value.let { if (tag in it) it - tag else it + tag }
    }

    fun clearTags() {
        _selectedTags.value = emptySet()
    }

    fun setQuery(text: String) {
        if (_query.value.isBlank() && text.isNotBlank()) viewModelScope.launch { metrics.record(UsageEventType.BUSCA_USADA) }
        _query.value = text
    }

    fun rename(recording: Recording, title: String) {
        viewModelScope.launch { runCatching { RenameRecording(repo)(recording.id, title) } }
    }

    fun toggleFavorite(recording: Recording) {
        viewModelScope.launch { repo.setFavorite(recording.id, !recording.isFavorite) }
    }

    /** Vai para a lixeira (nunca apaga direto); a tela oferece DESFAZER. */
    fun delete(recording: Recording) {
        viewModelScope.launch { DeleteRecording(repo)(recording.id) }
    }

    fun undoDelete(recording: Recording) {
        viewModelScope.launch { RestoreRecording(repo)(recording.id) }
    }

    fun played() {
        viewModelScope.launch { metrics.record(UsageEventType.REPRODUCAO_INICIADA) }
    }

    /** No cofre, a copia em claro para o destinatario e gerada num cache privado (fora do thread principal). */
    suspend fun shareIntent(recording: Recording): Intent {
        viewModelScope.launch { metrics.record(UsageEventType.COMPARTILHADO, label = ShareChannel.FOLHA_DO_SISTEMA) }
        return files.shareIntent(recording)
    }

    /** Caminho que o player consegue abrir (decifra para o cache privado se a gravacao estiver no cofre). */
    suspend fun playablePath(recording: Recording): String = files.playablePath(recording)

    fun saveTo(recording: Recording, destination: Uri): Boolean {
        val ok = files.copyTo(recording, destination)
        if (ok) viewModelScope.launch { metrics.record(UsageEventType.COMPARTILHADO, label = ShareChannel.SALVAR_EM) }
        return ok
    }

    /** Onda 2, "juntar": concatena na ordem dada numa gravacao nova; o resultado volta pelo callback para o snackbar. */
    fun join(recordings: List<Recording>, onResult: (JoinOutcome) -> Unit) {
        viewModelScope.launch { onResult(joins.join(recordings)) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Faixas do filtro de BPM (Gate 3). Sem análise, a gravação não entra na faixa escolhida. */
enum class BpmFaixa {
    LENTA,
    MEDIA,
    RAPIDA,
    ;

    fun contem(bpm: Float?): Boolean = when (this) {
        LENTA -> bpm != null && bpm <= 90f
        MEDIA -> bpm != null && bpm > 90f && bpm <= 130f
        RAPIDA -> bpm != null && bpm > 130f
    }
}
