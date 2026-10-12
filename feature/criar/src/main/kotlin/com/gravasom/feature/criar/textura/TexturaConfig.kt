package com.gravasom.feature.criar.textura

/** Parâmetros da textura (F6): time-stretch 2×–8× com fechamento em loop. */
data class TexturaConfig(
    val fator: Float = 4f,
    val envelopeMs: Long = 2000L,
)
