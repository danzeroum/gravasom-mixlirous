package com.gravasom.core.mixengine

import com.gravasom.core.mixengine.motor.MixEngine

/**
 * Ponto de entrada Kotlin do motor de remix (Rust/Mixlirous) no app.
 *
 * Carrega `libmixlirous_mobile.so` e expõe o [MixEngine] UniFFI. Decodificação
 * de arquivos continua no Gravasom (`com.gravasom.core.service.analysis.PcmDecoder`);
 * aqui só entra PCM mono em FloatArray — o Rust nunca lê arquivos.
 */
object MotorMixlirous {

    @Volatile
    private var motor: MixEngine? = null

    /** true quando a biblioteca nativa está presente nesta ABI. */
    val disponivel: Boolean by lazy { carregar() }

    private fun carregar(): Boolean =
        runCatching {
            System.loadLibrary("mixlirous_mobile")
            true
        }.getOrElse { false }

    /**
     * Motor pronto para uso. Lança [IllegalStateException] se a biblioteca não
     * carregou (ABI ausente no APK) — a tela de receita manual segue
     * funcionando, o motor é opcional por contrato do app.
     */
    fun obter(): MixEngine {
        motor?.let { return it }
        synchronized(this) {
            motor?.let { return it }
            check(carregar()) {
                "libmixlirous_mobile.so ausente — a ABI deste aparelho não está no APK"
            }
            // UniFFI 0.32: construtores viram funções de companion (novo()).
            return MixEngine.novo().also { motor = it }
        }
    }

    /**
     * Converte PCM intercalado de 16 bits (saída do PcmDecoder) para mono f32
     * em [-1, 1], a faixa que o motor Rust espera.
     */
    fun paraMonoF32(intercalado: ShortArray, canais: Int): FloatArray {
        require(canais >= 1) { "canais deve ser ≥ 1" }
        val quadros = intercalado.size / canais
        val saida = FloatArray(quadros)
        var soma: Int
        for (quadro in 0 until quadros) {
            soma = 0
            for (c in 0 until canais) soma += intercalado[quadro * canais + c].toInt()
            saida[quadro] = (soma.toFloat() / canais) / Short.MAX_VALUE
        }
        return saida
    }
}
