package com.gravasom.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.gravasom.core.ui.theme.GravasomDarkColors
import com.gravasom.core.ui.theme.GravasomExtraLight
import com.gravasom.core.ui.theme.GravasomLightColors
import com.gravasom.core.ui.theme.MixlirousDarkColors
import com.gravasom.core.ui.theme.MixlirousLightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TemaGeradoTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `mixlirous escuro tem primario, fundo e cofre do documento`() {
        val esquema = esquemaDeCores(Paleta.MIXLIROUS, escuro = true)
        val extras = coresExtras(Paleta.MIXLIROUS, escuro = true)
        assertEquals(Color(0xFFEFB0FF), esquema.primary)
        assertEquals(Color(0xFF18120C), esquema.background)
        assertEquals(Color(0xFF87D4CE), extras.cofre.base)
    }

    @Test
    fun `gravasom claro e escuro usam o petroleo`() {
        assertEquals(Color(0xFF006877), esquemaDeCores(Paleta.GRAVASOM, escuro = false).primary)
        assertEquals(Color(0xFF76D4E8), esquemaDeCores(Paleta.GRAVASOM, escuro = true).primary)
    }

    @Test
    fun `gravando pausado medidor e marcadores sao iguais nas tres paletas`() {
        for (escuro in listOf(false, true)) {
            val base = coresExtras(Paleta.GRAVASOM, escuro)
            for (paleta in Paleta.entries) {
                val e = coresExtras(paleta, escuro)
                assertEquals("gravando $paleta", base.gravando, e.gravando)
                assertEquals("pausado $paleta", base.pausado, e.pausado)
                assertEquals("vu $paleta", base.vu, e.vu)
                assertEquals("marcadores $paleta", base.marker, e.marker)
            }
        }
    }

    @Test
    fun `so pronto cofre e onda mudam na mixlirous`() {
        val g = coresExtras(Paleta.GRAVASOM, escuro = false)
        val m = coresExtras(Paleta.MIXLIROUS, escuro = false)
        assertFalse(g.pronto == m.pronto)
        assertFalse(g.cofre == m.cofre)
        assertFalse(g.onda == m.onda)
    }

    @Test
    fun `o vermelho so aparece em gravando e no erro`() {
        val vermelho = GravasomExtraLight.gravando.container
        assertEquals(vermelho, GravasomLightColors.error)
        assertFalse(GravasomLightColors.primary == vermelho)
        assertFalse(GravasomExtraLight.pronto.container == vermelho)
    }

    @Test
    fun `android 11 com cores do sistema cai na paleta padrao sem erro`() {
        // Sem Context: no Android 11 o Context nem é consultado, então nada pode lançar.
        assertEquals(MixlirousLightColors, esquemaDeCores(Paleta.SISTEMA, escuro = false, context = null, sdkInt = 30))
        assertEquals(MixlirousDarkColors, esquemaDeCores(Paleta.SISTEMA, escuro = true, context = null, sdkInt = 30))
        assertEquals(Paleta.PADRAO, Paleta.SISTEMA.efetiva(30))
        assertFalse(Paleta.SISTEMA in Paleta.disponiveis(30))
        assertTrue(Paleta.SISTEMA in Paleta.disponiveis(31))
    }

    @Test
    fun `tema inteiro no android 11 com cores do sistema usa a gravasom`() {
        var primario = Color.Unspecified
        var gravando = Color.Unspecified
        compose.setContent {
            GravasomThemeImpl(darkTheme = false, paleta = Paleta.SISTEMA, sdkInt = 30) {
                primario = MaterialTheme.colorScheme.primary
                gravando = GravasomTheme.extra.gravando.container
            }
        }
        compose.waitForIdle()
        assertEquals(GravasomLightColors.primary, primario)
        assertEquals(GravasomExtraLight.gravando.container, gravando)
    }

    @Test
    fun `no android 14 cores do sistema trocam o esquema e mantem gravando`() {
        val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dinamico = esquemaDeCores(Paleta.SISTEMA, escuro = false, context = contexto, sdkInt = 34)
        assertFalse(dinamico == GravasomLightColors)
        assertEquals(GravasomExtraLight.gravando, coresExtras(Paleta.SISTEMA, escuro = false, sdkInt = 34).gravando)
    }

    @Test
    fun `paleta padrao e mixlirous e ids desconhecidos voltam a ela`() {
        assertEquals(Paleta.MIXLIROUS, Paleta.PADRAO)
        assertEquals(Paleta.MIXLIROUS, Paleta.fromId(null))
        assertEquals(Paleta.MIXLIROUS, Paleta.fromId("outra"))
        assertEquals(Paleta.GRAVASOM, Paleta.fromId("gravasom"))
    }

    @Test
    fun `marcador pelo nome do dominio, RED vira rosa`() {
        val marcadores = GravasomExtraLight.marker
        assertEquals(marcadores.rosa, marcadores.forDomainName("RED"))
        assertEquals(marcadores.grafite, marcadores.forDomainName("GRAPHITE"))
        assertEquals(marcadores.ambar, marcadores.forDomainName("QUALQUER"))
        assertEquals(6, marcadores.ordem.size)
    }
}
