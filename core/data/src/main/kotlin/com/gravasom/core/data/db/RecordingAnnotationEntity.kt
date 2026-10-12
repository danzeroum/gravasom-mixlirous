package com.gravasom.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Anotação de gravação (F7/Transcrição): resultado do whisper.cpp local vinculado à gravação.
 * `texto` carrega o RESUMO estruturado ("[mm:ss] trecho" por linha) e `raw` a transcrição bruta.
 * A anotação é só leitura de áudio — o arquivo original nunca muda (ADR-008, cadeia não destrutiva).
 */
@Entity(
    tableName = "recording_annotations",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recordingId")],
)
data class RecordingAnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val recordingId: Long,
    @ColumnInfo(name = "createdAtMillis") val createdAtMillis: Long,
    val titulo: String,
    val texto: String,
    val raw: String,
    /** Duração da INFERÊNCIA no aparelho (prova ADR-016), não do áudio. */
    @ColumnInfo(name = "duracaoInferenciaMs") val duracaoInferenciaMs: Long,
    /** Nome do arquivo do modelo usado (ex.: ggml-base-q5_1.bin) — rastreabilidade da medição. */
    val modelo: String,
    val idioma: String,
    val tokens: Int,
)
