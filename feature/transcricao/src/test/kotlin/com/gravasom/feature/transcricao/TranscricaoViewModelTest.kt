package com.gravasom.feature.transcricao

import com.gravasom.core.data.modelmanager.CatalogoModelos
import com.gravasom.core.data.modelmanager.ModelMetadata
import com.gravasom.core.data.modelmanager.ModelVerificacaoException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Máquina de estados da tela de transcrição (F7) — o crash de campo de 2026-10-12 ("clico em
 * Transcrever, dá erro e sai do aplicativo") só podia ser fechado com a CADEIA INTEIRA provada:
 * consentimento → download (com progresso, falha, OOM e cancelamento) → pronto → transcrever
 * (sucesso, gravação ausente, OOM/UnsatisfiedLinkError, cancelamento) → salvar.
 *
 * Todo caminho que antes deixava `Error` (OOM/UnsatisfiedLinkError) escapar e matar o processo
 * agora é provado como ESTADO `Erro` na tela — o app NUNCA fecha por falha de transcrição.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranscricaoViewModelTest {

    private val despachante = StandardTestDispatcher()

    @Before
    fun preparar() {
        Dispatchers.setMain(despachante)
    }

    @After
    fun soltar() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        gerente: ModeloFalso = ModeloFalso(),
        useCase: UseCaseFalso = UseCaseFalso(RepositorioFalso(gravacaoDeTeste()), AnotacaoFalsa()),
    ) = Triple(TranscricaoViewModel(gerente, useCase), gerente, useCase)

    private fun metadadosProntos() = ModelMetadata(
        "ggml-base-q5_1.bin", 60f, "abc", "origem", "/m/whisper.bin", 1L,
    )

    // ——— Abrir a tela ———

    @Test
    fun `abrir sem modelo vai para Consentimento com o modelo recomendado do catálogo`() = runTest {
        val (vm, _, _) = viewModel()
        vm.avaliarDisponibilidade()
        val estado = vm.estado.value
        assertTrue("esperava Consentimento, veio $estado", estado is TranscricaoEstado.Consentimento)
        val consentimento = estado as TranscricaoEstado.Consentimento
        assertTrue(consentimento.modeloSelecionado.recomendado)
        assertEquals("ggml-base-q5_1.bin", consentimento.modeloSelecionado.nome)
    }

    @Test
    fun `abrir com modelo presente vai direto para Pronto`() = runTest {
        val (vm, gerente, _) = viewModel()
        gerente.modelo = metadadosProntos()
        vm.avaliarDisponibilidade()
        assertTrue(vm.estado.value is TranscricaoEstado.Pronto)
    }

    // ——— Consentimento e download ———

    @Test
    fun `consentir e baixar reporta progresso e vai para Pronto`() = runTest {
        val (vm, gerente, _) = viewModel()
        vm.avaliarDisponibilidade()
        vm.consentirEBaixar()
        advanceUntilIdle()
        assertTrue(gerente.consentimento)
        assertEquals(2, gerente.progressosReportados.size)
        val estado = vm.estado.value
        assertTrue("esperava Pronto, veio $estado", estado is TranscricaoEstado.Pronto)
        assertEquals("/data/files/models/ggml-base-q5_1.bin", (estado as TranscricaoEstado.Pronto).modelo.path)
    }

    @Test
    fun `download com falha mostra Erro com a mensagem e NÃO fecha o app`() = runTest {
        val (vm, gerente, _) = viewModel()
        gerente.falhaNoDownload = ModelVerificacaoException("SHA-256 não confere — arquivo descartado")
        vm.avaliarDisponibilidade()
        vm.consentirEBaixar()
        advanceUntilIdle()
        val estado = vm.estado.value
        assertTrue("esperava Erro, veio $estado", estado is TranscricaoEstado.Erro)
        assertEquals("SHA-256 não confere — arquivo descartado", (estado as TranscricaoEstado.Erro).mensagem)
    }

    @Test
    fun `OutOfMemoryError no download vira Erro com mensagem própria e NÃO derruba o processo`() = runTest {
        val (vm, gerente, _) = viewModel()
        gerente.excecaoNoDownload = OutOfMemoryError("Failed to allocate a 23190560 byte allocation")
        vm.avaliarDisponibilidade()
        vm.consentirEBaixar()
        advanceUntilIdle()
        val estado = vm.estado.value
        assertTrue("OOM deve virar Erro, não crash; veio $estado", estado is TranscricaoEstado.Erro)
        val erro = estado as TranscricaoEstado.Erro
        assertTrue(
            "mensagem de OOM deve ser a própria: ${erro.mensagem}",
            erro.mensagem.contains("Sem memória"),
        )
    }

    @Test
    fun `cancelar o download volta para Consentimento em vez de Erro`() = runTest {
        // VM construído à mão: o gerente aqui é o download infinito (não expõe campos do falso).
        val vm = TranscricaoViewModel(DownloadInfinitoFalso(), UseCaseFalso(RepositorioFalso(gravacaoDeTeste()), AnotacaoFalsa()))
        vm.avaliarDisponibilidade()
        vm.consentirEBaixar()
        advanceTimeBy(120)
        assertTrue(vm.estado.value is TranscricaoEstado.Baixando)
        vm.cancelar()
        advanceUntilIdle()
        assertTrue(
            "cancelamento NÃO é erro; veio ${vm.estado.value}",
            vm.estado.value is TranscricaoEstado.Consentimento,
        )
    }

    // ——— Transcrever ———

    @Test
    fun `transcrever feliz passa por Preparando e Transcrevendo e termina em Sucesso editável`() = runTest {
        val (vm, gerente, useCase) = viewModel()
        gerente.modelo = metadadosProntos()
        vm.avaliarDisponibilidade()
        vm.iniciarTranscricao(recordingId = 42L)
        advanceUntilIdle()
        val estado = vm.estado.value
        assertTrue("esperava Sucesso, veio $estado", estado is TranscricaoEstado.Sucesso)
        assertEquals(listOf(FaseTranscricao.PREPARANDO, FaseTranscricao.TRANSCREVENDO), useCase.fases)
        assertEquals("[00:00] teste de transcrição", (estado as TranscricaoEstado.Sucesso).resultado.resumo)
        assertNull("salvoId só nasce depois de salvar", estado.salvoId)
    }

    @Test
    fun `salvar grava a anotação com o TEXTO EDITADO e preenche salvoId`() = runTest {
        val (vm, gerente, useCase) = viewModel()
        gerente.modelo = metadadosProntos()
        vm.avaliarDisponibilidade()
        vm.iniciarTranscricao(recordingId = 42L)
        advanceUntilIdle()
        vm.salvar(recordingId = 42L, titulo = "Transcrição automática", textoEditado = "texto que o dono editou")
        advanceUntilIdle()
        assertEquals("texto que o dono editou", useCase.textoSalvo)
        val estado = vm.estado.value
        assertTrue(estado is TranscricaoEstado.Sucesso)
        assertEquals(99L, (estado as TranscricaoEstado.Sucesso).salvoId)
        assertEquals(1, useCase.anotacao.inseridas.size)
        assertEquals(42L, useCase.anotacao.inseridas.first().recordingId)
    }

    @Test
    fun `gravação inexistente vira Erro claro e NÃO fecha o app`() = runTest {
        val useCaseSemGravacao = UseCaseFalso(RepositorioFalso(null), AnotacaoFalsa())
        useCaseSemGravacao.modo = UseCaseFalso.Modo.GRAVACAO_AUSENTE
        val (vm, gerente, _) = viewModel(useCase = useCaseSemGravacao)
        gerente.modelo = metadadosProntos()
        vm.avaliarDisponibilidade()
        vm.iniciarTranscricao(recordingId = 999L)
        advanceUntilIdle()
        val estado = vm.estado.value
        assertTrue("esperava Erro, veio $estado", estado is TranscricaoEstado.Erro)
        assertEquals("Gravação 999 não encontrada", (estado as TranscricaoEstado.Erro).mensagem)
    }

    @Test
    fun `OutOfMemoryError durante a transcrição vira Erro com mensagem própria e NÃO derruba o processo`() = runTest {
        val (vm, gerente, useCase) = viewModel()
        useCase.modo = UseCaseFalso.Modo.OOM
        gerente.modelo = metadadosProntos()
        vm.avaliarDisponibilidade()
        vm.iniciarTranscricao(recordingId = 42L)
        advanceUntilIdle()
        val estado = vm.estado.value
        assertTrue("OOM deve virar Erro, não crash; veio $estado", estado is TranscricaoEstado.Erro)
        assertTrue((estado as TranscricaoEstado.Erro).mensagem.contains("Sem memória"))
    }

    @Test
    fun `UnsatisfiedLinkError durante a transcrição vira Erro e NÃO derruba o processo`() = runTest {
        val (vm, gerente, useCase) = viewModel()
        useCase.modo = UseCaseFalso.Modo.LINKAGE
        gerente.modelo = metadadosProntos()
        vm.avaliarDisponibilidade()
        vm.iniciarTranscricao(recordingId = 42L)
        advanceUntilIdle()
        val estado = vm.estado.value
        assertTrue("Error de linkage deve virar Erro; veio $estado", estado is TranscricaoEstado.Erro)
        assertNotNull((estado as TranscricaoEstado.Erro).mensagem)
    }

    @Test
    fun `cancelar durante o preparo volta para Pronto`() = runTest {
        val (vm, gerente, useCase) = viewModel()
        useCase.modo = UseCaseFalso.Modo.CONGELADO
        gerente.modelo = metadadosProntos()
        vm.avaliarDisponibilidade()
        vm.iniciarTranscricao(recordingId = 42L)
        advanceTimeBy(120)
        assertTrue("deveria estar Preparando; veio ${vm.estado.value}", vm.estado.value is TranscricaoEstado.Preparando)
        vm.cancelar()
        advanceUntilIdle()
        assertTrue("cancelar o preparo devolve Pronto; veio ${vm.estado.value}", vm.estado.value is TranscricaoEstado.Pronto)
    }

    @Test
    fun `tentar novamente a partir do Erro reinicia a transcrição`() = runTest {
        val (vm, gerente, useCase) = viewModel()
        useCase.modo = UseCaseFalso.Modo.ERRO
        gerente.modelo = metadadosProntos()
        vm.avaliarDisponibilidade()
        vm.iniciarTranscricao(recordingId = 42L)
        advanceUntilIdle()
        assertTrue(vm.estado.value is TranscricaoEstado.Erro)
        useCase.modo = UseCaseFalso.Modo.FELIZ
        vm.tentarNovamente(recordingId = 42L, comTimestamps = true)
        advanceUntilIdle()
        assertTrue("esperava Sucesso após tentar novamente; veio ${vm.estado.value}", vm.estado.value is TranscricaoEstado.Sucesso)
    }

    // ——— Modelos embutidos no APK (sem rede nenhuma) ———

    private fun gerenteComEmbutido(nome: String = "ggml-base-q5_1.bin") = ModeloFalso().apply {
        embutidos = listOf(CatalogoModelos.modelos.first { it.nome == nome })
    }

    @Test
    fun `aceitar consentimento com modelo embutido instala SEM rede e vai para Pronto`() = runTest {
        val gerente = gerenteComEmbutido()
        val vm = TranscricaoViewModel(gerente, UseCaseFalso(RepositorioFalso(gravacaoDeTeste()), AnotacaoFalsa()))
        vm.avaliarDisponibilidade()
        assertTrue(vm.estado.value is TranscricaoEstado.Consentimento)
        vm.aceitarConsentimento()
        advanceUntilIdle()
        assertEquals("modelo embutido NÃO pode tocar download", 0, gerente.downloadsIniciados)
        assertEquals(1, gerente.instalacoesIniciadas)
        assertTrue("consentimento registrado mesmo sem rede", gerente.consentimento)
        val estado = vm.estado.value
        assertTrue("esperava Pronto, veio $estado", estado is TranscricaoEstado.Pronto)
        assertEquals("embutido no APK", (estado as TranscricaoEstado.Pronto).modelo.origem)
    }

    @Test
    fun `aceitar consentimento com modelo FORA do APK segue pelo download`() = runTest {
        val (vm, gerente, _) = viewModel() // embutidos vazio — comportamento antigo intacto
        vm.avaliarDisponibilidade()
        vm.aceitarConsentimento()
        advanceUntilIdle()
        assertEquals(1, gerente.downloadsIniciados)
        assertEquals(0, gerente.instalacoesIniciadas)
        assertTrue(vm.estado.value is TranscricaoEstado.Pronto)
    }

    @Test
    fun `falha na instalação do embutido vira Erro e NÃO derruba o processo`() = runTest {
        val gerente = gerenteComEmbutido("ggml-tiny-q5_1.bin")
        gerente.falhaNaInstalacao = ModelVerificacaoException("SHA-256 do modelo embutido não confere — reinstale o aplicativo")
        val vm = TranscricaoViewModel(gerente, UseCaseFalso(RepositorioFalso(gravacaoDeTeste()), AnotacaoFalsa()))
        vm.avaliarDisponibilidade()
        // o recomendado (base-q5_1) NÃO é embutido aqui — selecionar o embutido à mão primeiro
        vm.selecionarModelo(CatalogoModelos.modelos.first { it.nome == "ggml-tiny-q5_1.bin" })
        vm.aceitarConsentimento()
        advanceUntilIdle()
        val estado = vm.estado.value
        assertTrue("esperava Erro, veio $estado", estado is TranscricaoEstado.Erro)
        assertEquals(
            "SHA-256 do modelo embutido não confere — reinstale o aplicativo",
            (estado as TranscricaoEstado.Erro).mensagem,
        )
    }

    @Test
    fun `SecurityException no download vira mensagem amigável em vez de Permission Denial crua`() = runTest {
        val (vm, gerente, _) = viewModel()
        gerente.falhaNoDownload = SecurityException(
            "Permission Denial: writing com.android.providers.downloads.DownloadProvider " +
                "uri content://downloads/my_downloads requires android.permission.INTERNET",
        )
        vm.avaliarDisponibilidade()
        vm.consentirEBaixar()
        advanceUntilIdle()
        val estado = vm.estado.value
        assertTrue("esperava Erro, veio $estado", estado is TranscricaoEstado.Erro)
        val mensagem = (estado as TranscricaoEstado.Erro).mensagem
        assertTrue(
            "mensagem deve ser amigável, não o Permission Denial cru: $mensagem",
            mensagem.contains("Permissão de rede negada"),
        )
    }

    @Test
    fun `cancelar durante a instalação do embutido volta para Consentimento em vez de Erro`() = runTest {
        val gerente = InstalacaoInfinitaFalso()
        gerente.base.embutidos = listOf(CatalogoModelos.modelos.first { it.nome == "ggml-base-q5_1.bin" })
        val vm = TranscricaoViewModel(gerente, UseCaseFalso(RepositorioFalso(gravacaoDeTeste()), AnotacaoFalsa()))
        vm.avaliarDisponibilidade()
        vm.aceitarConsentimento()
        advanceTimeBy(120)
        assertTrue("deveria estar Instalando; veio ${vm.estado.value}", vm.estado.value is TranscricaoEstado.Instalando)
        vm.cancelar()
        advanceUntilIdle()
        assertTrue(
            "cancelamento NÃO é erro; veio ${vm.estado.value}",
            vm.estado.value is TranscricaoEstado.Consentimento,
        )
    }

    // ——— Remover modelo ———

    @Test
    fun `remover modelo apaga metadados e volta ao Consentimento`() = runTest {
        val (vm, gerente, _) = viewModel()
        gerente.modelo = metadadosProntos()
        vm.avaliarDisponibilidade()
        assertTrue(vm.estado.value is TranscricaoEstado.Pronto)
        vm.removerModelo()
        assertNull(gerente.modelo)
        assertTrue(vm.estado.value is TranscricaoEstado.Consentimento)
    }
}
