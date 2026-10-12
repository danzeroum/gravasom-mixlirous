package com.gravasom.feature.transcricao

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resample16k (F7): 48 kHz → 16 kHz mono, com regra de 16 kHz (passa 1:1), estéreo quebrado
 * em pedaços ímpares e zero-crossing como prova de frequência.
 */
class Resample16kTest {

    /** Gera seno intercalado estéreo de [taxa] Hz com [frequencia] Hz. */
    private fun seno(frequencia: Double, taxa: Int, segundos: Double, canais: Int): ShortArray {
        val n = (taxa * segundos).toInt()
        return ShortArray(n * canais) { i ->
            val quadro = i / canais
            (sin(2 * PI * frequencia * quadro / taxa) * 16000).toInt().toShort()
        }
    }

    /** Conta zero-crossings positivos por segundo → frequência dominante aproximada. */
    private fun frequenciaDominante(amostras: ShortArray, taxa: Int): Double {
        var cruzamentos = 0
        for (i in 1 until amostras.size) {
            if (amostras[i - 1] < 0 && amostras[i] >= 0) cruzamentos++
        }
        return cruzamentos.toDouble() * taxa / amostras.size
    }

    @Test
    fun reamostra48kPara16kMantendoFrequenciaEContagem() {
        val origem = seno(440.0, 48_000, 1.0, canais = 1)
        val resampler = Resample16k(48_000, canais = 1)
        val alvo = ArrayList<Short>()
        resampler.empurrar(origem, origem.size) { alvo.add(it) }
        // 48k -> 16k: 1 segundo gera ~16.000 amostras (±2 pelo arredondamento do step).
        assertEquals(16_000.0, alvo.size.toDouble(), 2.0)
        // Frequência preservada (±3%).
        val freq = frequenciaDominante(alvo.toShortArray(), 16_000)
        assertEquals(440.0, freq, 440.0 * 0.03)
    }

    @Test
    fun taxa44100Gera16kComFrequenciaPreservada() {
        val origem = seno(220.0, 44_100, 1.0, canais = 1)
        val resampler = Resample16k(44_100, canais = 1)
        val alvo = ArrayList<Short>()
        resampler.empurrar(origem, origem.size) { alvo.add(it) }
        assertEquals(16_000.0, alvo.size.toDouble(), 2.0)
        val freq = frequenciaDominante(alvo.toShortArray(), 16_000)
        assertEquals(220.0, freq, 220.0 * 0.03)
    }

    @Test
    fun downmixEstereoFazMediaDosCanais() {
        // Canal L constante +16000, canal R constante -16000 → mono tem que ser ~0.
        val origem = ShortArray(96_000) { i -> if (i % 2 == 0) 16000 else -16000 }
        val resampler = Resample16k(48_000, canais = 2)
        val alvo = ArrayList<Short>()
        resampler.empurrar(origem, origem.size) { alvo.add(it) }
        assertTrue(alvo.size > 15_000)
        val maximo = alvo.maxOf { abs(it.toInt()) }
        assertTrue("mono deveria ser ~0, max=$maximo", maximo < 100)
    }

    @Test
    fun pedacosImparesNaoPerdemQuadros() {
        // Estéreo quebrado em pedaços com número ÍMPAR de amostras (quebra no meio do quadro).
        val origem = seno(330.0, 48_000, 1.0, canais = 2)
        val resampler = Resample16k(48_000, canais = 2)
        val alvo = ArrayList<Short>()
        var pos = 0
        while (pos < origem.size) {
            val fim = minOf(pos + 1_023, origem.size) // ímpar de propósito
            resampler.empurrar(origem.copyOfRange(pos, fim), fim - pos) { alvo.add(it) }
            pos = fim
        }
        assertEquals(16_000.0, alvo.size.toDouble(), 2.0)
        val freq = frequenciaDominante(alvo.toShortArray(), 16_000)
        assertEquals(330.0, freq, 330.0 * 0.03)
    }

    @Test
    fun fonte16kPassaUmPorUm() {
        val origem = seno(880.0, 16_000, 1.0, canais = 1)
        val resampler = Resample16k(16_000, canais = 1)
        val alvo = ArrayList<Short>()
        resampler.empurrar(origem, origem.size) { alvo.add(it) }
        assertEquals(origem.size - 1, alvo.size) // primeira amostra vira "anterior"
    }
}
