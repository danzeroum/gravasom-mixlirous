package com.gravasom.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val path: String,
    val format: String,
    val durationMillis: Long,
    val sizeBytes: Long,
    val createdAtMillis: Long,
    val isFavorite: Boolean = false,
    val folder: String? = null,
    val tagsCsv: String = "",
    val deletedAtMillis: Long? = null,
    val publishedUri: String? = null,
    /** Id do FormatPreset (v5); null para gravacoes anteriores ou importadas. */
    val preset: String? = null,
    /** SHA-256 do arquivo (v7); null nas anteriores — a importacao usa para recusar duplicata. */
    val sha256: String? = null,
    /** Origem do audio (v7): RECORDED (padrao das antigas/nulo) ou IMPORTED. */
    val origin: String? = null,
    /** Artista extraido dos metadados na importacao (v7); null quando gravada no app. */
    val artist: String? = null,
)
