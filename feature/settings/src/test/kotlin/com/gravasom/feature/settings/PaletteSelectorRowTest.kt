package com.gravasom.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.gravasom.core.ui.Paleta
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Aparência (F8) na tela de Ajustes: o seletor de paleta precisa abrir SEM CRASH com "Cores do sistema"
 * escolhida. Crash de campo de 2026-10-12: a prévia (BolinhaDaPaleta) chamava esquemaDeCores sem
 * Context; com a paleta SISTEMA num Android 12+ o requireNotNull do esquema dinâmico fechava o app
 * sempre que Ajustes era aberto. Regressão: compor a linha inteira com selecionada = "sistema".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR")
class PaletteSelectorRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val escolhidas = mutableListOf<String>()

    private fun show(selecionada: String) {
        escolhidas.clear()
        compose.setContent {
            MaterialTheme {
                Column { PaletteSelectorRow(selecionada, escolhidas::add) }
            }
        }
    }

    @Test
    fun `linha abre com cores do sistema selecionada sem crash`() {
        show(Paleta.SISTEMA.id) // antes da correção: IllegalArgumentException "Cores do sistema precisam de um Context"
        compose.onNodeWithTag("paleta_${Paleta.SISTEMA.id}").assertIsSelected()
        compose.onNodeWithTag("seletor_paleta").assertExists()
    }

    @Test
    fun `linha abre com cada paleta sem crash e tocar um chip o escolhe`() {
        // Um setContent so: a linha compõe os chips das TRÊS paletas (Android 12+ inclui SISTEMA),
        // então a prévia (BolinhaDaPaleta) roda para todas elas — incluindo a que crashava.
        show(Paleta.MIXLIROUS.id)
        compose.onNodeWithTag("paleta_${Paleta.MIXLIROUS.id}").assertIsSelected()
        compose.onNodeWithTag("paleta_${Paleta.SISTEMA.id}").assertExists()
        compose.onNodeWithTag("paleta_${Paleta.GRAVASOM.id}").performClick()
        assertEquals(listOf(Paleta.GRAVASOM.id), escolhidas)
    }
}
