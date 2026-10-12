package com.gravasom.core.service.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * F6 no worker: ganhos do balance da mescla, tipos de criação e o WAV do loop
 * repetido (Gate 6: "loop 4 compassos × 20 sem clique na emenda" — aqui a
 * prova é estrutural: dados = ciclo × repetições, header consistente).
 */
class CriacaoWorkerF6Test {

    @get:Rule
    val temp = TemporaryFolder()

    // ——— CriacaoTipo (contrato do roteiro §3.2) ———

    @Test
    fun criacaoTipoMapeiaOsModos() {
        assertEquals(CriacaoTipo.LOOP, CriacaoTipo.de(CriacaoWorker.MODO_LOOP))
        assertEquals(CriacaoTipo.TEXTURA, CriacaoTipo.de(CriacaoWorker.MODO_TEXTURA))
        assertEquals(CriacaoTipo.MESCLA, CriacaoTipo.de(CriacaoWorker.MODO_MESCLA))
        assertEquals(CriacaoTipo.MELHORES, CriacaoTipo.de(CriacaoWorker.MODO_MELHORES))
        assertNull(CriacaoTipo.de(CriacaoWorker.MODO_REMIX)) // remix é F3, fora do F6
    }

    // ——— Balance da mescla ———

    @Test
    fun balanceNeutroNaoMudaNada() {
        val (ga, gb) = CriacaoWorker.ganhosDeBalanceEstatico(0.5f)
        assertEquals(1f, ga, 1e-6f)
        assertEquals(1f, gb, 1e-6f)
    }

    @Test
    fun balanceExtremosTemChaoDeSeguranca() {
        val (ga, gb) = CriacaoWorker.ganhosDeBalanceEstatico(0f)
        assertEquals(1f, ga, 1e-6f)
        assertEquals(CriacaoWorker.CHAO_BALANCE, gb, 1e-6f)
        val (ga1, gb1) = CriacaoWorker.ganhosDeBalanceEstatico(1f)
        assertEquals(CriacaoWorker.CHAO_BALANCE, ga1, 1e-6f)
        assertEquals(1f, gb1, 1e-6f)
    }

    @Test
    fun balanceForaDaFaixaEhCoagido() {
        val (ga, gb) = CriacaoWorker.ganhosDeBalanceEstatico(7f) // fora de [0,1] → vira 1
        assertEquals(CriacaoWorker.CHAO_BALANCE, ga, 1e-6f)
        assertEquals(1f, gb, 1e-6f)
    }

    // ——— Loop repetido (escreverWav) ———

    @Test
    fun loopRepetidoMultiplicaOsBytesDeDados() {
        val destino = temp.newFile("loop.wav")
        val pcm = listOf(0f, 0.5f, -0.5f, 0.25f) // 4 amostras → 8 bytes de dados por ciclo
        CriacaoWorker.escreverWavEstatico(destino, pcm, sampleRate = 8000, repeticoes = 20)

        val bytes = destino.readBytes()
        val tamanhoDados = bytes.size - 44 // header WAV canônico = 44 bytes
        assertEquals(8 * 20, tamanhoDados)
        // Header declara o tamanho total (ciclos × dados).
        val dataHeaderSize = ((bytes[40].toInt() and 0xFF) or
            ((bytes[41].toInt() and 0xFF) shl 8) or
            ((bytes[42].toInt() and 0xFF) shl 16) or
            ((bytes[43].toInt() and 0xFF) shl 24))
        assertEquals(8 * 20, dataHeaderSize)
    }

    @Test
    fun repeticoesAlemDoTetoSaoCoagidas() {
        val destino = temp.newFile("loop50.wav")
        val pcm = List(4) { 0f }
        CriacaoWorker.escreverWavEstatico(destino, pcm, sampleRate = 8000, repeticoes = 1000)
        val tamanhoDados = destino.length() - 44
        assertEquals(8L * CriacaoWorker.REPETICOES_MAX, tamanhoDados)
    }

    @Test
    fun escreverWavSemRepeticaoMantemOFormatoAnterior() {
        val destino = temp.newFile("loop1.wav")
        val pcm = listOf(0f, 0.5f, -0.5f, 0.25f)
        CriacaoWorker.escreverWavEstatico(destino, pcm, sampleRate = 44100) // repetições default = 1
        assertEquals(44L + 8L, destino.length())
    }
}
