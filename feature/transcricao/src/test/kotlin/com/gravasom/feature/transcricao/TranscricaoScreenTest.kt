package com.gravasom.feature.transcricao

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.gravasom.core.data.modelmanager.ModelMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A TELA de transcrição inteira compõe sem crash em TODOS os estados da máquina (o crash de
 * Ajustes de 2026-10-12 — `esquemaDeCores` sem Context — provou que composição quebra em campo
 * mesmo com a lógica toda verde; aqui cada estado da TranscricaoScreen é montado de verdade).
 * Os toques provam as transições visíveis: aceitar → Pronto → Transcrever → Sucesso → Salvar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1800dp")  // tela alta: o consentimento inteiro cabe sem rolagem
class TranscricaoScreenTest {

    @get:Rule val regra = createComposeRule()

    private val gerente = ModeloFalso()
    private val useCase = UseCaseFalso(RepositorioFalso(gravacaoDeTeste()), AnotacaoFalsa())
    private lateinit var viewModel: TranscricaoViewModel

    @Before
    fun preparar() {
        // Unconfined: os corrotinas do viewModelScope rodam na hora do clique — determinístico.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun soltar() {
        Dispatchers.resetMain()
    }

    private fun compor(recordingId: Long = 42L) {
        viewModel = TranscricaoViewModel(gerente, useCase)
        regra.setContent {
            TranscricaoScreen(recordingId = recordingId, onDone = { }, viewModel = viewModel)
        }
        regra.waitForIdle()
    }

    @Test
    fun `consentimento compõe com catálogo tamanho origem e hash — sem crash`() {
        compor()
        regra.onNodeWithTag("transcricao_aceitar").assertExists()
        regra.onNodeWithTag("transcricao_importar").assertExists()
        regra.onNodeWithTag("transcricao_recusar").assertExists()
        regra.onNodeWithTag("modelo_ggml-base-q5_1.bin").assertExists()
        regra.onNodeWithTag("modelo_ggml-tiny-q5_1.bin").assertExists()
    }

    @Test
    fun `aceitar baixa o modelo e a tela chega em Pronto com o botão Transcrever`() {
        compor()
        regra.onNodeWithTag("transcricao_aceitar").performClick()
        regra.waitForIdle()
        assertTrue(viewModel.estado.value is TranscricaoEstado.Pronto)
        regra.onNodeWithTag("transcricao_iniciar").assertExists()
        regra.onNodeWithTag("transcricao_switch_timestamps").assertExists()
    }

    @Test
    fun `fluxo completo Pronto → Sucesso mostra o resumo editável e salva sem crash`() {
        compor()
        regra.onNodeWithTag("transcricao_aceitar").performClick()
        regra.waitForIdle()
        regra.onNodeWithTag("transcricao_iniciar").performClick()
        regra.waitForIdle()
        assertTrue(viewModel.estado.value is TranscricaoEstado.Sucesso)
        regra.onNodeWithTag("transcricao_resumo_editavel").assertExists()
        regra.onNodeWithTag("transcricao_texto_bruto").assertExists()
        regra.onNodeWithTag("transcricao_salvar").performClick()
        regra.waitForIdle()
        assertEquals("texto do resumo vai para a anotação", "[00:00] teste de transcrição", useCase.textoSalvo)
        regra.onNodeWithTag("transcricao_salvar").assertExists()
    }

    @Test
    fun `estado de Erro compõe com mensagem e botões de saída — sem crash`() {
        gerente.falhaNoDownload = RuntimeException("falha de rede simulada")
        compor()
        regra.onNodeWithTag("transcricao_aceitar").performClick()
        regra.waitForIdle()
        assertTrue(viewModel.estado.value is TranscricaoEstado.Erro)
        regra.onNodeWithTag("transcricao_tentar_novamente").assertExists()
    }

    @Test
    fun `modelo já presente abre direto em Pronto sem passar pelo consentimento`() {
        gerente.modelo = ModelMetadata("ggml-base-q5_1.bin", 60f, "abc", "origem", "/m/whisper.bin", 1L)
        compor()
        regra.waitForIdle()
        assertTrue(viewModel.estado.value is TranscricaoEstado.Pronto)
        regra.onNodeWithTag("transcricao_iniciar").assertExists()
    }
}
