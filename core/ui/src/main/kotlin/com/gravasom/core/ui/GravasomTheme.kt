package com.gravasom.core.ui

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.gravasom.core.ui.theme.GravasomDarkColors
import com.gravasom.core.ui.theme.GravasomExtraColors
import com.gravasom.core.ui.theme.GravasomExtraDark
import com.gravasom.core.ui.theme.GravasomExtraLight
import com.gravasom.core.ui.theme.GravasomLightColors
import com.gravasom.core.ui.theme.GravasomShapeScale
import com.gravasom.core.ui.theme.GravasomTypography
import com.gravasom.core.ui.theme.LocalGravasomExtraColors
import com.gravasom.core.ui.theme.MixlirousDarkColors
import com.gravasom.core.ui.theme.MixlirousExtraDark
import com.gravasom.core.ui.theme.MixlirousExtraLight
import com.gravasom.core.ui.theme.MixlirousLightColors

/**
 * Esquema de cores do Material para a [paleta] (já resolvida por [Paleta.efetiva]). Só "Cores do sistema" precisa de [context].
 */
fun esquemaDeCores(paleta: Paleta, escuro: Boolean, context: Context? = null, sdkInt: Int = Build.VERSION.SDK_INT): ColorScheme =
    when (paleta.efetiva(sdkInt)) {
        Paleta.MIXLIROUS -> if (escuro) MixlirousDarkColors else MixlirousLightColors
        Paleta.SISTEMA -> {
            requireNotNull(context) { "Cores do sistema precisam de um Context" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                esquemaDinamico(escuro, context)
            } else {
                if (escuro) GravasomDarkColors else GravasomLightColors
            }
        }
        Paleta.GRAVASOM -> if (escuro) GravasomDarkColors else GravasomLightColors
    }

@RequiresApi(Build.VERSION_CODES.S)
private fun esquemaDinamico(escuro: Boolean, context: Context): ColorScheme =
    (if (escuro) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).comBordaLegivel()

/**
 * O esquema dinâmico do sistema deixa a borda (`outline`) com menos de 3:1 sobre as superfícies mais altas no escuro (2,6:1 medido).
 * Escurece ou clareia só a borda, em direção ao texto, até chegar a 3:1 sobre a pior superfície; o resto vem do sistema.
 */
internal fun ColorScheme.comBordaLegivel(): ColorScheme {
    val superficies = listOf(
        surface, surfaceDim, surfaceBright, surfaceContainerLowest, surfaceContainerLow,
        surfaceContainer, surfaceContainerHigh, surfaceContainerHighest,
    )
    fun pior(borda: Color) = superficies.minOf { ContrasteWcag.razao(borda, it) }
    var borda = outline
    var passo = 0
    while (pior(borda) < BORDA_MINIMA && passo < PASSOS_BORDA) {
        passo++
        borda = lerp(outline, onSurface, passo.toFloat() / PASSOS_BORDA)
    }
    return if (passo == 0) this else copy(outline = borda)
}

private const val BORDA_MINIMA = 3.0
private const val PASSOS_BORDA = 20

/**
 * Cores de estado, medidor, onda e marcadores. Iguais nas três paletas, exceto pronto, cofre e onda da Mixlirous: o vermelho de
 * "gravando" nunca vem do papel de parede.
 */
fun coresExtras(paleta: Paleta, escuro: Boolean, sdkInt: Int = Build.VERSION.SDK_INT): GravasomExtraColors =
    when (paleta.efetiva(sdkInt)) {
        Paleta.MIXLIROUS -> if (escuro) MixlirousExtraDark else MixlirousExtraLight
        else -> if (escuro) GravasomExtraDark else GravasomExtraLight
    }

/** Atalho para as cores extras do tema atual: `GravasomTheme.extra.gravando.container`. */
object GravasomTheme {
    val extra: GravasomExtraColors
        @Composable get() = LocalGravasomExtraColors.current
}

/**
 * Tema do Gravasom Mixlirous: Material 3 com as cores, a tipografia (Atkinson Hyperlegible Next) e as formas de
 * docs/design/tokens.json. [paleta] padrão é a Mixlirous (identidade do app); o seletor em Ajustes fica para a Parte B.
 */
@Composable
fun GravasomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    paleta: Paleta = Paleta.PADRAO,
    content: @Composable () -> Unit,
) = GravasomThemeImpl(darkTheme, paleta, Build.VERSION.SDK_INT, content)

/** Mesmo tema com a versão do Android explícita (os testes simulam o Android 11 sem baixar outra imagem do Robolectric). */
@Composable
internal fun GravasomThemeImpl(darkTheme: Boolean, paleta: Paleta, sdkInt: Int, content: @Composable () -> Unit) {
    val context = LocalContext.current
    CompositionLocalProvider(LocalGravasomExtraColors provides coresExtras(paleta, darkTheme, sdkInt)) {
        MaterialTheme(
            colorScheme = esquemaDeCores(paleta, darkTheme, context, sdkInt),
            typography = GravasomTypography,
            shapes = GravasomShapeScale,
            content = content,
        )
    }
}
