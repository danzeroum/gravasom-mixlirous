package com.gravasom.feature.criar

import com.gravasom.core.service.mix.CriacaoWorker
import kotlin.math.max

/**
 * Validação de domínio das abas F6 (obrigatória, roteiro §3.3). O motor é a
 * autoridade final (revalida no worker); aqui a mensagem chega ao usuário
 * ANTES de enfileirar — sem jargão, com o limite na frase.
 *
 * | Parâmetro              | Regra                          | Mensagem                                        |
 * | Loop — repetições      | ≥ 2 e ≤ 50                     | "Mínimo 2, máximo 50 repetições"                |
 * | Loop — fade-in/out     | ≤ 2000 ms cada                 | "Fade máximo 2 s por emenda"                    |
 * | Textura — fator        | ∈ [2, 8] (Float)               | "Fator de textura entre 2× e 8×"                |
 * | Textura — duração      | ≥ 5 s                          | "Duração mínima 5 s"                            |
 * | Mescla — balance       | ∈ [0.0, 1.0]                   | "Balance entre 0 (A) e 1 (B)"                   |
 * | Mescla — tons          | avisosDeMescla antes (HITL)    | "Tons incompatíveis: continuar mesmo assim?"    |
 * | Melhores — duração alvo| ≥ soma dos marcadores          | "Duração alvo menor que os trechos selecionados"|
 */
object CriarSonsUseCases {

    // ——— Loop ———

    fun validarLoop(repeticoes: Int, fadeMs: Long): String? = when {
        repeticoes < REPETICOES_MIN || repeticoes > REPETICOES_MAX ->
            "Mínimo $REPETICOES_MIN, máximo $REPETICOES_MAX repetições"
        fadeMs > FADE_MAX_MS -> "Fade máximo ${FADE_MAX_MS / 1000} s por emenda"
        else -> null
    }

    // ——— Textura ———

    /** [duracaoOrigemMs] = duração da gravação de origem (a textura estica 2×–8×). */
    fun validarTextura(fator: Float, duracaoOrigemMs: Long): String? = when {
        fator < FATOR_MIN || fator > FATOR_MAX -> "Fator de textura entre ${FATOR_MIN}× e ${FATOR_MAX}×"
        duracaoOrigemMs < DURACAO_MINIMA_MS -> "Duração mínima ${DURACAO_MINIMA_MS / 1000} s"
        else -> null
    }

    // ——— Mescla ———

    fun validarMescla(balance: Float): String? =
        if (balance < BALANCE_MIN || balance > BALANCE_MAX) "Balance entre $BALANCE_MIN (A) e $BALANCE_MAX (B)" else null

    /** Balance → ganhos relativos (A, B), mesma regra do worker (fonte única). */
    fun ganhosDeBalance(balance: Float): Pair<Float, Float> {
        val b = balance.coerceIn(BALANCE_MIN, BALANCE_MAX)
        val ganhoA = (1f - b).coerceAtLeast(ChaoBalance)
        val ganhoB = b.coerceAtLeast(ChaoBalance)
        val maior = max(ganhoA, ganhoB)
        return ganhoA / maior to ganhoB / maior
    }

    // ——— Melhores ———

    /** [marcadoresMs] = pares achatados [inicio, fim, …] em ms (trechos obrigatórios). */
    fun validarMelhores(duracaoAlvoS: Int, marcadoresMs: LongArray): String? {
        val somaTrechosMs = somaDosTrechos(marcadoresMs)
        return if (duracaoAlvoS * 1000L < somaTrechosMs) {
            "Duração alvo menor que os trechos selecionados (${somaTrechosMs / 1000} s de trechos)"
        } else {
            null
        }
    }

    fun somaDosTrechos(marcadoresMs: LongArray): Long =
        marcadoresMs.toList().windowed(size = 2, step = 2, partialWindows = false)
            .sumOf { max(0L, it[1] - it[0]) }

    // ——— Constantes (fonte única para UI e testes; o motor revalida) ———

    const val REPETICOES_MIN = 2
    const val REPETICOES_MAX = CriacaoWorker.REPETICOES_MAX // 50
    const val FADE_MAX_MS = 2000L
    const val FADE_PADRAO_MS = 120L // crossfade de emenda do loop (mesma ordem do motor)

    const val FATOR_MIN = 2f
    const val FATOR_MAX = 8f
    const val DURACAO_MINIMA_MS = 5_000L

    const val BALANCE_MIN = 0f
    const val BALANCE_MAX = 1f
    const val ChaoBalance = CriacaoWorker.CHAO_BALANCE

    const val DURACAO_ALVO_PADRAO_S = 30
}
