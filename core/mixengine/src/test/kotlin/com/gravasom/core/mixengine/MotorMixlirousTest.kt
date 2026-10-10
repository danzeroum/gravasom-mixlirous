package com.gravasom.core.mixengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Teste JVM puro da conversão PCM intercalado 16 bits → mono f32. */
class MotorMixlirousTest {

    @Test
    fun `estereo intercalado vira media dos canais`() {
        // L=0,5 e R=-0,5 → mono 0,0. Em 16 bits: 16384 e -16384.
        val intercalado = shortArrayOf(16384, -16384, 16384, -16384)
        val mono = MotorMixlirous.paraMonoF32(intercalado, canais = 2)
        assertEquals(2, mono.size)
        assertEquals(0.0f, mono[0], 1e-6f)
        assertEquals(0.0f, mono[1], 1e-6f)
    }

    @Test
    fun `amplitude maxima mapeia para 1`() {
        val intercalado = shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE)
        val mono = MotorMixlirous.paraMonoF32(intercalado, canais = 1)
        assertEquals(1.0f, mono[0], 1e-4f)
    }

    @Test
    fun `canais invalidos sao recusados`() {
        val excecao = runCatching { MotorMixlirous.paraMonoF32(shortArrayOf(1, 2), canais = 0) }
            .exceptionOrNull()
        assertTrue(excecao is IllegalArgumentException)
    }

    @Test
    fun `disponivel nao explode fora do android`() {
        // Em JVM de host não há libmixlirous_mobile.so: `disponivel` deve ser
        // false (runCatching no load), nunca lançar.
        val carregou = MotorMixlirous.disponivel
        assertFalse(carregou)
    }
}
