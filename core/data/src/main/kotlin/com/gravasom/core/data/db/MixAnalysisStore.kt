package com.gravasom.core.data.db

import kotlinx.coroutines.flow.Flow

/**
 * Fachada da analise musical (F2) no banco. O worker salva ATOMICAMENTE no fim
 * (upsert único) — cancelar a analise no meio nunca deixa etiqueta parcial.
 *
 * O tipo do motor Rust fica no worker (core/service): core/data não conhece o
 * mixengine, só recebe os campos prontos.
 */
class MixAnalysisStore(private val dao: MixAnalysisDao) {

    /** Dados da analise que o worker extrai do motor antes de persistir. */
    data class AnaliseMix(
        val bpm: Float?,
        val bpmConfianca: Float?,
        val tonica: Int?,
        val modo: String?,
        val loudnessLufs: Float?,
        val energiaMedia: Float,
        val energiaDesvio: Float,
        val picoDb: Float,
        /** Trechos "inicio_s,fim_s,rms;…" — o worker serializa a lista do motor. */
        val energiaTrechos: String,
    )

    /** Converte a resposta do motor Rust na linha Room. Uma única escrita. */
    suspend fun save(recordingId: Long, analise: AnaliseMix) {
        dao.upsert(
            MixAnalysisEntity(
                recordingId = recordingId,
                bpm = analise.bpm,
                bpmConfianca = analise.bpmConfianca,
                tonica = analise.tonica,
                modo = analise.modo,
                loudnessLufs = analise.loudnessLufs,
                energiaMedia = analise.energiaMedia,
                energiaDesvio = analise.energiaDesvio,
                picoDb = analise.picoDb,
                energiaTrechos = analise.energiaTrechos,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun get(recordingId: Long): MixAnalysisEntity? = dao.get(recordingId)

    fun observe(recordingId: Long): Flow<MixAnalysisEntity?> = dao.observe(recordingId)

    fun observeAll(): Flow<List<MixAnalysisEntity>> = dao.observeAll()

    suspend fun has(recordingId: Long): Boolean = dao.get(recordingId) != null

    suspend fun delete(recordingId: Long) = dao.delete(recordingId)

    companion object {
        const val SEPARADOR_TRECHO = ";"
        const val SEPARADOR_CAMPO = ","

        /** Serializa a lista de trechos do motor no formato compacto da coluna. */
        fun serializarTrechos(trechos: List<Triple<Float, Float, Float>>): String =
            trechos.joinToString(SEPARADOR_TRECHO) { (i, f, r) -> "$i$SEPARADOR_CAMPO$f$SEPARADOR_CAMPO$r" }

        /** Reconstrói os trechos "inicio_s,fim_s,rms" para a waveform colorida. */
        fun trechos(entity: MixAnalysisEntity): List<TrechoEnergia> =
            entity.energiaTrechos.split(SEPARADOR_TRECHO).mapNotNull { parte ->
                val campos = parte.split(SEPARADOR_CAMPO)
                if (campos.size == 3) {
                    val i = campos[0].toFloatOrNull() ?: return@mapNotNull null
                    val f = campos[1].toFloatOrNull() ?: return@mapNotNull null
                    val r = campos[2].toFloatOrNull() ?: return@mapNotNull null
                    TrechoEnergia(i, f, r)
                } else {
                    null
                }
            }
    }
}

/** Trecho de energia (espelho legível de EnergiaTrecho do motor). */
data class TrechoEnergia(val inicioS: Float, val fimS: Float, val rms: Float)
