package com.gravasom.feature.transcricao

import com.gravasom.core.service.analysis.PcmDecoder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converte qualquer arquivo de áudio do app (M4A, MP3, WAV — [PcmDecoder]) para PCM 16 kHz mono
 * 16 bits num ByteBuffer DIRETO, pronto para o JNI do whisper ([com.gravasom.core.whisper.WhisperJni]).
 *
 * Por que buffer direto de shorts e não FloatArray: 10 min de áudio são 9,6 M amostras —
 * num FloatArray de heap Java são 384 MB (OOM na maioria dos aparelhos); em shorts nativos
 * são 19 MB fora do heap. A conversão s16→f32 acontece no lado nativo (C++).
 *
 * Por que ARQUIVO DE ESTÁGIO e não ByteArrayOutputStream: o PCM reamostrado precisa descansar
 * em algum lugar entre o decode e o buffer direto — no heap Java ele custava ~3× o tamanho do
 * áudio (fluxo + toByteArray + ShortArray; 1 h ≈ 350 MB → OutOfMemoryError matava o processo).
 * No arquivo o custo de heap é o bloco de E/S (crash de campo 2026-10-12, transcrição).
 */
object PcmParaWhisper {

    /** Teto de segurança: 2 h de áudio em 16 kHz mono (~229 MB nativos). Acima disso, trunca. */
    const val MAXIMO_SHORTS = 2L * 3600L * 16000L

    private const val BLOCO_BYTES = 1 shl 16

    data class PcmPronto(val buffer: ByteBuffer, val nShorts: Int)

    /** null = arquivo não decodificável. [cancelado] interrompe o decode no meio (botão Cancelar). */
    fun converter(arquivo: File, cancelado: () -> Boolean = { false }): PcmPronto? {
        // Estágio ao lado do próprio áudio (armazenamento privado do app; em testes host, tmpdir).
        val estagio = File.createTempFile("pcm_whisper_", ".s16", arquivo.parentFile)
        try {
            val total = decodarParaEstagio(arquivo, estagio, cancelado) ?: return null
            if (total == 0L) return null

            // Passada 2 — estágio → buffer direto, em blocos (heap plano).
            val nShorts = total.coerceAtMost(MAXIMO_SHORTS).toInt()
            val buffer = ByteBuffer.allocateDirect(nShorts * 2).order(ByteOrder.LITTLE_ENDIAN)
            DataInputStream(BufferedInputStream(FileInputStream(estagio), BLOCO_BYTES)).use { entrada ->
                val bloco = ByteArray(BLOCO_BYTES)
                var restante = nShorts * 2
                while (restante > 0) {
                    val n = entrada.read(bloco, 0, minOf(bloco.size, restante))
                    if (n <= 0) break
                    buffer.put(bloco, 0, n)
                    restante -= n
                }
            }
            buffer.rewind()
            return PcmPronto(buffer, nShorts)
        } finally {
            estagio.delete()
        }
    }

    /** Passada 1 — decodifica, reamostra a 16 kHz mono e grava s16 LE no estágio; devolve o total de shorts. */
    private fun decodarParaEstagio(arquivo: File, estagio: File, cancelado: () -> Boolean): Long? {
        var resampler: Resample16k? = null
        var total = 0L
        BufferedOutputStream(FileOutputStream(estagio), BLOCO_BYTES).use { saida ->
            val decodou = PcmDecoder.decode(
                arquivo,
                format = { taxa, canais -> if (resampler == null) resampler = Resample16k(taxa, canais) },
                sink = { pcm, n ->
                    resampler?.empurrar(pcm, n) { s ->
                        if (total < MAXIMO_SHORTS) { // trunca no teto (2 h) sem estourar memória
                            total++
                            // Little-endian, igual à ordem do buffer direto.
                            saida.write(s.toInt() and 0xFF)
                            saida.write((s.toInt() shr 8) and 0xFF)
                        }
                    }
                },
                cancelled = cancelado,
            )
            if (!decodou) return null
        }
        return total
    }
}
