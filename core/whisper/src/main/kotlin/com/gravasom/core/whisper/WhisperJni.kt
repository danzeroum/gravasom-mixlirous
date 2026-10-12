package com.gravasom.core.whisper

import java.nio.ByteBuffer

/**
 * Ligação JNI com libgravasom_whisper.so (whisper.cpp 1.8.5, hash-pinned). Mesmo contrato do spike
 * ADR-013 ("t0_ms\tt1_ms\ttexto" por segmento), com entrada extra por ByteBuffer direto de shorts
 * 16 bits — áudio longo não cabe num FloatArray de heap Java (10 min = 384 MB).
 */
object WhisperJni {
    init {
        System.loadLibrary("gravasom_whisper")
    }

    /** Abre o modelo ggml/gguf do caminho. 0 = falha. */
    external fun nativeInit(path: String): Long

    external fun nativeFree(handle: Long)

    /** PCM float mono 16 kHz — clipes curtos e testes. Devolve "t0_ms\tt1_ms\ttexto\n"... ou null. */
    external fun nativeTranscribeFloat(handle: Long, pcm: FloatArray, threads: Int, language: String): String?

    /**
     * PCM short 16 bits LE mono 16 kHz num ByteBuffer DIRETO (produção). [nShorts] limita a leitura
     * (0 = buffer inteiro). Devolve "t0_ms\tt1_ms\ttexto\n"... ou null.
     */
    external fun nativeTranscribeS16(handle: Long, buffer: ByteBuffer, nShorts: Int, threads: Int, language: String): String?

    /** Total de tokens da última transcrição (prova ADR-016). */
    external fun nativeContarTokens(handle: Long): Int

    external fun nativeSystemInfo(): String
}
