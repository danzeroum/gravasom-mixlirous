package com.gravasom.feature.transcricao

import com.gravasom.core.service.analysis.PcmDecoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converte qualquer arquivo de áudio do app (M4A, MP3, WAV — [PcmDecoder]) para PCM 16 kHz mono
 * 16 bits num ByteBuffer DIRETO, pronto para o JNI do whisper ([com.gravasom.core.whisper.WhisperJni]).
 *
 * Por que buffer direto de shorts e não FloatArray: 10 min de áudio são 9,6 M amostras —
 * num FloatArray de heap Java são 384 MB (OOM na maioria dos aparelhos); em shorts nativos
 * são 19 MB fora do heap. A conversão s16→f32 acontece no lado nativo (C++).
 */
object PcmParaWhisper {

    /** Teto de segurança: 2 h de áudio em 16 kHz mono (~229 MB nativos). Acima disso, trunca. */
    const val MAXIMO_SHORTS = 2L * 3600L * 16000L

    data class PcmPronto(val buffer: ByteBuffer, val nShorts: Int)

    /** null = arquivo não decodificável. [cancelado] interrompe o decode no meio (botão Cancelar). */
    fun converter(arquivo: File, cancelado: () -> Boolean = { false }): PcmPronto? {
        var resampler: Resample16k? = null
        val saida = ByteArrayOutputStream(1 shl 20)

        val decodou = PcmDecoder.decode(
            arquivo,
            format = { taxa, canais -> if (resampler == null) resampler = Resample16k(taxa, canais) },
            sink = { pcm, n ->
                resampler?.empurrar(pcm, n) { s ->
                    // Little-endian, igual à ordem do buffer direto.
                    saida.write(s.toInt() and 0xFF)
                    saida.write((s.toInt() shr 8) and 0xFF)
                }
            },
            cancelled = cancelado,
        )
        if (!decodou || resampler == null) return null
        val bytes = saida.toByteArray()
        if (bytes.isEmpty()) return null

        val nShorts = (bytes.size / 2).coerceAtMost(MAXIMO_SHORTS.toInt())
        val shorts = ShortArray(nShorts) { i ->
            (((bytes[i * 2 + 1].toInt() and 0xFF) shl 8) or (bytes[i * 2].toInt() and 0xFF)).toShort()
        }
        val buffer = ByteBuffer.allocateDirect(nShorts * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asShortBuffer().put(shorts)
        buffer.rewind()
        return PcmPronto(buffer, nShorts)
    }
}
