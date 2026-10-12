package com.gravasom.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gravasom.core.data.metrics.UsageMetrics
import com.gravasom.core.data.preferences.PreferencesRepository
import com.gravasom.core.domain.audio.RecordingFormat
import com.gravasom.core.domain.metrics.LibraryStats
import com.gravasom.core.domain.metrics.UsageEventType
import com.gravasom.core.domain.preset.FormatPreset
import com.gravasom.core.domain.repository.RecordingRepository
import com.gravasom.core.service.backup.BackupResult
import com.gravasom.core.service.backup.BackupService
import com.gravasom.core.service.backup.RestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class SettingsUiState(
    val format: RecordingFormat = RecordingFormat.M4A,
    val bitrate: Int = 128,
    val privateMode: Boolean = false,
    val numbersOptIn: Boolean = false,
    /** Preset de formato escolhido; null = personalizado. */
    val preset: FormatPreset? = null,
    /** Paleta de cores (F8, "Aparência"): id bruto da preferência. */
    val paletteId: String = PreferencesRepository.PALETA_PADRAO_ID,
)

/** Contadores de uso (so existem com opt-in) mais as estatisticas da biblioteca (sempre disponiveis, locais). */
data class NumbersUiState(
    val library: LibraryStats = LibraryStats(0, 0L, null),
    val optIn: Boolean = false,
    val started: Int = 0,
    val shared: Int = 0,
    val searches: Int = 0,
    val imported: Int = 0,
)

/** O que a tela de Ajustes mostra sobre o backup em andamento. */
enum class BackupFase { PARADO, CRIANDO, RESTAURANDO }

/** Aviso curto do backup para a UI (resultado ou falha), ja em codigo de string. */
sealed interface BackupEvento {
    data class Criado(val gravacoes: Int) : BackupEvento
    data class Restauradas(val gravacoes: Int) : BackupEvento
    data object SenhaErrada : BackupEvento
    data class Falhou(val detalhe: String?) : BackupEvento
}

/**
 * Ajustes e "Seus numeros". Etapas: Descobrir (confianca) e Gravar (formato). Hipoteses H5 e "trocar para MP3 sem ajuda"
 * em docs/ux/jornada.md. A Onda 2 acrescentou o backup cifrado: criar pacote e restaurar, com senha do usuario.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: PreferencesRepository,
    private val repo: RecordingRepository,
    private val metrics: UsageMetrics,
    private val backup: BackupService,
) : ViewModel() {

    val settings: StateFlow<SettingsUiState> = combine(
        prefs.format, prefs.bitrate, prefs.privateVault, prefs.telemetryOptIn, prefs.preset,
    ) { f, b, p, n, preset -> SettingsUiState(f, b, p, n, preset) }
        .combine(prefs.palette) { s, paleta -> s.copy(paletteId = paleta) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    private val counters = combine(
        metrics.count(UsageEventType.GRAVACAO_INICIADA), metrics.count(UsageEventType.COMPARTILHADO),
        metrics.count(UsageEventType.BUSCA_USADA), metrics.count(UsageEventType.IMPORTADO),
    ) { a, b, c, d -> listOf(a, b, c, d) }

    val numbers: StateFlow<NumbersUiState> = combine(
        repo.observeAll().map { LibraryStats.of(it) }, prefs.telemetryOptIn, counters,
    ) { lib, opt, c -> NumbersUiState(lib, opt, c[0], c[1], c[2], c[3]) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), NumbersUiState())

    fun setFormat(format: RecordingFormat) = viewModelScope.launch {
        if (settings.value.format != format) metrics.record(UsageEventType.FORMATO_ALTERADO, label = format)
        prefs.setFormat(format)
    }

    fun setBitrate(kbps: Int) = viewModelScope.launch { prefs.setBitrate(kbps) }
    fun setPreset(preset: FormatPreset) = viewModelScope.launch { prefs.setPreset(preset) }
    fun setPrivate(enabled: Boolean) = viewModelScope.launch { prefs.setPrivateVault(enabled) }

    /** F8: troca a paleta em Ajustes › Aparência; o tema reage na hora. */
    fun setPalette(id: String) = viewModelScope.launch { prefs.setPalette(id) }
    fun setNumbersOptIn(enabled: Boolean) = viewModelScope.launch { prefs.setTelemetryOptIn(enabled) }

    private val _backupFase = MutableStateFlow(BackupFase.PARADO)
    val backupFase: StateFlow<BackupFase> = _backupFase.asStateFlow()

    private val _backupEventos = MutableSharedFlow<BackupEvento>(extraBufferCapacity = 1)
    val backupEventos: SharedFlow<BackupEvento> = _backupEventos.asSharedFlow()

    /** Cria o pacote cifrado no destino escolhido (SAF) com a senha dada. */
    fun criarBackup(destino: Uri, senha: CharArray) = viewModelScope.launch {
        _backupFase.value = BackupFase.CRIANDO
        val resultado = runCatching {
            appContext.contentResolver.openOutputStream(destino)?.use { saida -> backup.create(saida, senha) }
                ?: BackupResult.Failed("destino inacessivel")
        }.getOrElse { BackupResult.Failed(it.message ?: "erro inesperado") }
        _backupFase.value = BackupFase.PARADO
        _backupEventos.emit(
            when (resultado) {
                is BackupResult.Done -> BackupEvento.Criado(resultado.recordings)
                is BackupResult.Failed -> BackupEvento.Falhou(resultado.reason)
            },
        )
    }

    /** Restaura um pacote cifrado (SAF) com a senha dada; nada e apagado. */
    fun restaurarBackup(origem: Uri, senha: CharArray) = viewModelScope.launch {
        _backupFase.value = BackupFase.RESTAURANDO
        val resultado = runCatching {
            appContext.contentResolver.openInputStream(origem)?.use { entrada -> backup.restore(entrada, senha) }
                ?: RestoreResult.Failed("pacote inacessivel")
        }.getOrElse { RestoreResult.Failed(it.message ?: "erro inesperado") }
        _backupFase.value = BackupFase.PARADO
        _backupEventos.emit(
            when (resultado) {
                is RestoreResult.Done -> BackupEvento.Restauradas(resultado.restored)
                RestoreResult.WrongPassword -> BackupEvento.SenhaErrada
                is RestoreResult.Failed -> BackupEvento.Falhou(resultado.reason)
            },
        )
    }

    fun numbersOpened() = viewModelScope.launch { metrics.record(UsageEventType.NUMEROS_ABERTO) }
    fun clearNumbers() = viewModelScope.launch { metrics.clear() }
    suspend fun exportCsv(): String = metrics.exportCsv()

    suspend fun medianTimeToRecord(): Long? = metrics.median(UsageEventType.TEMPO_ATE_GRAVAR)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
