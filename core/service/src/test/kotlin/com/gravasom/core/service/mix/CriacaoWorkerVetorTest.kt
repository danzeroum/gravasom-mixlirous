package com.gravasom.core.service.mix

import com.gravasom.core.mixengine.motor.FingerprintAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip do fingerprint pelo Data do WorkManager: o render original e o
 * "Refazer igual" precisam do MESMO vetor para o motor calcular a distância.
 */
class CriacaoWorkerVetorTest {

    private val fp = FingerprintAudio(
        mfcc = listOf(0.1f, -0.2f, 0.3f, 0.4f, -0.5f),
        centroidEspectral = 1200.5f,
        rmsEnergia = 0.25f,
        contrasteEspectral = listOf(1f, 2f, 3f),
        razaoPico = 0.9f,
        duracaoS = 30.5f,
        lufs = -14.2f,
    )

    @Test
    fun roundTripPreservaCampos() {
        val vetor = CriacaoWorker.paraVetor(fp)
        val reconstruido = CriacaoWorker.deVetor(vetor)!!
        assertEquals(fp.mfcc, reconstruido.mfcc)
        assertEquals(fp.contrasteEspectral, reconstruido.contrasteEspectral)
        assertEquals(fp.centroidEspectral, reconstruido.centroidEspectral, 1e-6f)
        assertEquals(fp.rmsEnergia, reconstruido.rmsEnergia, 1e-6f)
        assertEquals(fp.razaoPico, reconstruido.razaoPico, 1e-6f)
        assertEquals(fp.duracaoS, reconstruido.duracaoS, 1e-6f)
        assertEquals(fp.lufs!!, reconstruido.lufs!!, 1e-6f)
    }

    @Test
    fun lufsNuloSobrevive() {
        val semLufs = fp.copy(lufs = null)
        val reconstruido = CriacaoWorker.deVetor(CriacaoWorker.paraVetor(semLufs))!!
        assertNull(reconstruido.lufs)
    }

    @Test
    fun vetorTruncadoViraNullSemCrash() {
        assertNull(CriacaoWorker.deVetor(floatArrayOf(1f, 2f)))
        assertNull(CriacaoWorker.deVetor(floatArrayOf()))
        assertNull(CriacaoWorker.deVetor(floatArrayOf(5f, 999f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)))
    }

    @Test
    fun vetorTemTamanhoEsperado() {
        val vetor = CriacaoWorker.paraVetor(fp)
        assertEquals(2 + fp.mfcc.size + fp.contrasteEspectral.size + 5, vetor.size)
        assertTrue(vetor[0] == fp.mfcc.size.toFloat())
        assertTrue(vetor[1] == fp.contrasteEspectral.size.toFloat())
    }
}
