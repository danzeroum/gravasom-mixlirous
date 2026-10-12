package com.gravasom.feature.transcricao

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PcmParaWhisper (F7): WAV → PCM 16 kHz mono s16 num ByteBuffer direto. Caminho WAV é JVM pura
 * (WavReader + Resample16k, sem MediaCodec). Regressão do crash de campo 2026-10-12: o PCM
 * reamostrado descansava no heap Java (~3× o tamanho do áudio) — gravações longas estouravam
 * memória; agora passa por ARQUIVO DE ESTÁGIO (heap plano) e o estágio é apagado no fim.
 */
class PcmParaWhisperTest {

    private val pasta = File(System.getProperty("java.io.tmpdir")).apply { mkdirs() }

    /** WAV PCM16 canônico (44-byte header) com os shorts já intercalados por canal. */
    private fun wav(nome: String, taxa: Int, canais: Int, pcm: ShortArray): File {
        val bytesPcm = pcm.size * 2
        val buffer = ByteBuffer.allocate(44 + bytesPcm).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + bytesPcm).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(canais.toShort())
            .putInt(taxa).putInt(taxa * canais * 2).putShort((canais * 2).toShort()).putShort(16)
        buffer.put("data".toByteArray()).putInt(bytesPcm)
        pcm.forEach { buffer.putShort(it) }
        return File.createTempFile(nome, ".wav", pasta).apply { writeBytes(buffer.array()) }
    }

    private fun shortsDe(p: PcmParaWhisper.PcmPronto): ShortArray =
        ShortArray(p.nShorts).also { p.buffer.asShortBuffer().get(it) }

    @Test
    fun `mono 16k passa amostra a amostra (menos a última, presa no resampler)`() {
        val n = 1000
        val amostras = ShortArray(n) { ((it % 327) * 90).toShort() }
        val arquivo = wav("mono16k", 16_000, 1, amostras)

        val pronto = PcmParaWhisper.converter(arquivo)!!

        assertEquals(n - 1, pronto.nShorts)
        val saida = shortsDe(pronto)
        for (i in saida.indices) assertEquals("amostra $i", amostras[i], saida[i])
        arquivo.delete()
    }

    @Test
    fun `48k estereo vira 16k mono com o comprimento certo`() {
        val quadros = 480 // 480 quadros @48 kHz = 0,01 s → 160 amostras @16 kHz (Resample16k linear)
        val pcm = ShortArray(quadros * 2) { ((it % 211) * 100).toShort() } // intercalado L/R
        val arquivo = wav("estereo48k", 48_000, 2, pcm)

        val pronto = PcmParaWhisper.converter(arquivo)!!

        assertEquals(160, pronto.nShorts)
        arquivo.delete()
    }

    @Test
    fun `o estágio de disco some depois do converter`() {
        val arquivo = wav("estagio", 16_000, 1, ShortArray(200) { it.toShort() })

        PcmParaWhisper.converter(arquivo)

        val sobras = pasta.listFiles { f -> f.name.startsWith("pcm_whisper_") } ?: emptyArray()
        assertTrue("estágio não foi apagado: ${sobras.joinToString { it.name }}", sobras.isEmpty())
        arquivo.delete()
    }

    @Test
    fun `wav sem nenhum dado de áudio devolve null`() {
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(16_000).putInt(32_000).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(0)
        val arquivo = File.createTempFile("vazio", ".wav", pasta).apply { writeBytes(buffer.array()) }

        assertNull(PcmParaWhisper.converter(arquivo))
        arquivo.delete()
    }
}
