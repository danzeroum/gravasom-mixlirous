package com.gravasom.core.domain.model

import com.gravasom.core.domain.audio.RecordingFormat
import com.gravasom.core.domain.preset.FormatPreset

/** Metadados de uma gravacao, sem dependencia de Android. */
data class Recording(
    val id: Long = 0L,
    val title: String,
    val path: String,
    val format: RecordingFormat,
    val durationMillis: Long,
    val sizeBytes: Long,
    val createdAtMillis: Long,
    val isFavorite: Boolean = false,
    val folder: String? = null,
    val tags: List<String> = emptyList(),
    val deletedAtMillis: Long? = null,
    /** Uri do MediaStore da copia publicada em Music/Recordings (null se privada ou ainda nao publicada). */
    val publishedUri: String? = null,
    /** Preset de formato escolhido quando a gravacao foi feita (define os efeitos ligados por padrao); null = antiga/importada. */
    val preset: FormatPreset? = null,
    /** SHA-256 do arquivo; null nas gravacoes anteriores a v7. Importacao usa para nao duplicar arquivo igual. */
    val sha256: String? = null,
    /** De onde veio o audio (null nas anteriores a v7 = gravada no app). */
    val origin: RecordingOrigin? = null,
    /** Artista/metadados extraidos na importacao (null quando gravada no app ou sem metadado). */
    val artist: String? = null,
) {
    val isDeleted: Boolean get() = deletedAtMillis != null
    val isPrivate: Boolean get() = path.contains("/vault/")
    val isImported: Boolean get() = origin == RecordingOrigin.IMPORTED
}
