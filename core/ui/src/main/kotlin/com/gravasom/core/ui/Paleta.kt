package com.gravasom.core.ui

import android.os.Build

/**
 * Paleta de cores escolhida em Ajustes › Aparência. No Gravasom Mixlirous o padrão é [MIXLIROUS]. [SISTEMA] usa as cores
 * dinâmicas do Android 12+; em versões anteriores cai em [MIXLIROUS] (ver [efetiva]). Gravando, pausado, medidor e
 * marcadores têm as mesmas cores nas três.
 */
enum class Paleta(val id: String) {
    GRAVASOM("gravasom"),
    MIXLIROUS("mixlirous"),
    SISTEMA("sistema"),
    ;

    /** A paleta que de fato vale neste aparelho: "Cores do sistema" não existe antes do Android 12. */
    fun efetiva(sdkInt: Int = Build.VERSION.SDK_INT): Paleta =
        if (this == SISTEMA && sdkInt < Build.VERSION_CODES.S) PADRAO else this

    companion object {
        /** Paleta padrão do app: a Mixlirous, herança visual do Gravasom Mixlirous. */
        val PADRAO: Paleta = MIXLIROUS

        /** Id guardado nas preferências; nulo ou desconhecido volta ao padrão. */
        fun fromId(id: String?): Paleta = entries.firstOrNull { it.id == id } ?: PADRAO

        /** "Cores do sistema" só aparece na lista de escolha a partir do Android 12. */
        fun disponiveis(sdkInt: Int = Build.VERSION.SDK_INT): List<Paleta> = entries.filter { it.efetiva(sdkInt) == it }
    }
}
