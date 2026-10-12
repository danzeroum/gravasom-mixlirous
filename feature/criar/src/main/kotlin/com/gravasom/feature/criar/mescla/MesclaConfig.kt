package com.gravasom.feature.criar.mescla

/** Parâmetros da mescla (F6): trilha A (a origem) + trilha B + balance 0–1. */
data class MesclaConfig(
    val trilhaBId: Long = 0L,
    val balance: Float = 0.5f,
    /** false = o motor recusa tons incompatíveis (HITL); true = usuário confirmou seguir. */
    val seguirComTonsIncompativeis: Boolean = false,
)
