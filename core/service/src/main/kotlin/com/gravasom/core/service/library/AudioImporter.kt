package com.gravasom.core.service.library

import android.content.Context
import android.net.Uri
import com.gravasom.core.data.metrics.UsageMetrics
import com.gravasom.core.data.preferences.PreferencesRepository
import com.gravasom.core.domain.audio.RecordingFormat
import com.gravasom.core.domain.repository.RecordingRepository

sealed interface ImportResult {
    data class Imported(val id: Long) : ImportResult
    /** Mesmo arquivo (mesmo SHA-256) já importado: NÃO duplica — a tela mostra "Já existe". */
    data class Duplicata(val idExistente: Long) : ImportResult
    data object Unsupported : ImportResult
    data object Invalid : ImportResult
}

/**
 * Recebe áudio de outros apps (ACTION_SEND com tipo de áudio) e da porta SAF
 * (botão "Importar" da Library, F6). Toda a lógica vive no [ImportUseCase] —
 * aqui fica só a adaptação do [ImportOutcome] ao contrato antigo ([ImportResult]),
 * mantido para o ACTION_SEND da [com.gravasom.MainActivity].
 */
class AudioImporter(
    private val useCase: ImportUseCase,
) {

    suspend fun import(uri: Uri): ImportResult = importar(uri, persistable = false)

    /** Porta SAF (picker): a concessão do documento é persistível. */
    suspend fun importar(uri: Uri, persistable: Boolean): ImportResult =
        when (val outcome = useCase.importFromUri(uri, persistable)) {
            is ImportOutcome.Imported -> ImportResult.Imported(outcome.id)
            is ImportOutcome.Duplicata -> ImportResult.Duplicata(outcome.idExistente)
            ImportOutcome.Unsupported -> ImportResult.Unsupported
            ImportOutcome.Invalid -> ImportResult.Invalid
        }

    companion object {
        /** Mime/extensão → formato. Compartilhado entre a porta SEND e a SAF. */
        fun formatFor(mime: String?, name: String?): RecordingFormat? = when {
            mime in setOf("audio/mp4", "audio/x-m4a", "audio/m4a") -> RecordingFormat.M4A
            mime in setOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave") -> RecordingFormat.WAV
            mime in setOf("audio/mpeg", "audio/mp3") -> RecordingFormat.MP3
            else -> RecordingFormat.entries.firstOrNull { name?.lowercase()?.endsWith("." + it.extension) == true }
        }
    }
}
