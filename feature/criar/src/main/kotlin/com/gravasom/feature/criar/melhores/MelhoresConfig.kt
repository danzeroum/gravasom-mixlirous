package com.gravasom.feature.criar.melhores

/** Parâmetros dos melhores momentos (F6): marcadores obrigatórios + duração alvo. */
data class MelhoresConfig(
    val duracaoAlvoS: Int = 30,
    /** Pares achatados [inicio, fim, …] em ms — trechos obrigatórios do usuário. */
    val marcadoresMs: LongArray = longArrayOf(),
)
