package com.gravasom.core.domain.model

/**
 * De onde veio o áudio da gravação (v7 do banco, F6/Importação).
 * [IMPORTED] é o valor gravado na coluna `origin` e a etiqueta visível na
 * Library ("origin=IMPORTED"); linhas antigas (coluna nula) são [RECORDED].
 */
enum class RecordingOrigin(val id: String) {
    RECORDED("RECORDED"),
    IMPORTED("IMPORTED"),
    ;

    companion object {
        /** Nulo ou desconhecido = gravação feita no próprio app (linhas anteriores à v7). */
        fun fromId(id: String?): RecordingOrigin = entries.firstOrNull { it.id == id } ?: RECORDED
    }
}
