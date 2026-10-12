package com.gravasom.feature.transcricao

import com.gravasom.core.whisper.WhisperJni
import java.nio.ByteBuffer

/** Segmento transcrito, em ms do áudio (mesmo contrato do spike ADR-013). */
data class Segmento(val inicioMs: Long, val fimMs: Long, val texto: String)

/**
 * Sessão com o modelo whisper carregado: um modelo por vez na memória (regra do plano / ADR-017).
 * Fechar devolve a RAM do modelo; transcrever NÃO é interrompível no meio (limitação do whisper.cpp
 * síncrono) — o cancelamento cooperativo acontece nas fases de decode/preparo.
 */
class TranscricaoWhisper(caminhoModelo: String, private val threads: Int = defaultThreads()) : AutoCloseable {

    private val handle: Long = WhisperJni.nativeInit(caminhoModelo)

    init {
        check(handle != 0L) { "whisper não abriu o modelo: $caminhoModelo" }
    }

    fun transcrever(buffer: ByteBuffer, nShorts: Int, idioma: String = "pt"): List<Segmento> {
        val saida = requireNotNull(WhisperJni.nativeTranscribeS16(handle, buffer, nShorts, threads, idioma)) {
            "whisper_full falhou"
        }
        return parsear(saida)
    }

    fun contarTokens(): Int = WhisperJni.nativeContarTokens(handle)

    override fun close() {
        WhisperJni.nativeFree(handle)
    }

    companion object {
        /** whisper.cpp de exemplo roda com 4 threads: hardware_concurrency (8 em big.LITTLE) só
         *  aumenta o pico de RAM/CPU sem ganhar tempo útil — e é justamente o pico que mata app
         *  em segundo plano no aparelho. */
        internal fun defaultThreads(): Int = minOf(4, maxOf(1, Runtime.getRuntime().availableProcessors()))

        /** Parseia "t0_ms\tt1_ms\ttexto\n"... (contrato JNI). Linhas em branco são ignoradas. */
        fun parsear(saida: String): List<Segmento> = saida.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { linha ->
                val partes = linha.split('\t', limit = 3)
                if (partes.size < 3) return@mapNotNull null
                runCatching {
                    Segmento(partes[0].toLong(), partes[1].toLong(), partes[2].trim())
                }.getOrNull()
            }
            .toList()

        fun systemInfo(): String = WhisperJni.nativeSystemInfo()
    }
}

/** Formata o resultado: resumo estruturado (com/sem [mm:ss]) e transcrição bruta. */
object ResumoFormatter {

    fun resumo(segmentos: List<Segmento>, comTimestamps: Boolean): String = when {
        segmentos.isEmpty() -> ""
        comTimestamps -> segmentos.joinToString("\n") { "[${mmss(it.inicioMs)}] ${it.texto.trim()}" }
        else -> segmentos.joinToString("\n") { it.texto.trim() }
    }

    fun bruto(segmentos: List<Segmento>): String = segmentos.joinToString(" ") { it.texto.trim() }

    fun mmss(ms: Long): String {
        val total = ms / 1000
        return "%02d:%02d".format(total / 60, total % 60)
    }
}
