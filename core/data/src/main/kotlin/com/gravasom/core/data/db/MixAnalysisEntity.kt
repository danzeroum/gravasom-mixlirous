package com.gravasom.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Analise musical (F2) calculada pelo motor Rust (`MixEngine.analisar`) em
 * WorkManager, por gravacao. Alimenta a etiqueta "120 BPM · Lá menor", os
 * filtros de BPM/tom da biblioteca e o perfil de energia da waveform.
 *
 * A gravacao original NUNCA é alterada (SHA-256 intacto) — esta tabela é só
 * derivada; apagar aqui não perde áudio.
 */
@Entity(
    tableName = "mix_analysis",
    primaryKeys = ["recordingId"],
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
data class MixAnalysisEntity(
    val recordingId: Long,
    /** BPM estimado; null quando o material é curto demais ou atípico. */
    val bpm: Float?,
    /** Confiança do BPM (0–1); null junto com [bpm]. */
    val bpmConfianca: Float?,
    /** Tônica 0–11 (0 = Dó … 11 = Si); null quando não detectada. */
    val tonica: Int?,
    /** "maior" | "menor"; null quando não detectado. */
    val modo: String?,
    /** Loudness integrado (ITU-R BS.1770-4) em LUFS. */
    val loudnessLufs: Float?,
    /** Energia média (RMS) do material. */
    val energiaMedia: Float,
    /** Desvio de energia — base da waveform colorida por energia. */
    val energiaDesvio: Float,
    /** Pico em dBFS. */
    val picoDb: Float,
    /** Perfil de energia por trecho, formato "inicio_s,fim_s,rms;…". */
    val energiaTrechos: String,
    val updatedAtMillis: Long,
) {
    /** Rótulo de tom no formato do gate: "Lá menor". null sem tonalidade. */
    val rotuloTom: String?
        get() = tonica?.let { t ->
            val nomes = arrayOf(
                "Dó", "Dó♯", "Ré", "Ré♯", "Mi", "Fá", "Fá♯", "Sol", "Sol♯", "Lá", "Lá♯", "Si",
            )
            val nome = nomes[t % 12]
            if (modo == "menor") "$nome menor" else "$nome maior"
        }

    /** Etiqueta completa do gate 3: "120 BPM · Lá menor" (partes opcionais). */
    val etiqueta: String?
        get() = listOfNotNull(
            bpm?.let { "${it.toInt()} BPM" },
            rotuloTom,
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
