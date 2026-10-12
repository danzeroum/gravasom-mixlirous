package com.gravasom.feature.criar.loop

/**
 * Parâmetros do loop (F6), serializáveis para o workDataOf do [com.gravasom.core.service.mix.CriacaoWorker].
 * A emenda é fechada pelo motor (sem clique); as [repeticoes] escrevem o ciclo N vezes no WAV.
 */
data class LoopConfig(
    val compassos: Int = 4,
    val repeticoes: Int = 10,
    val fadeMs: Long = 120L,
)
