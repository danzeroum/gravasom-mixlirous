package com.gravasom.core.data.modelmanager

/**
 * Catálogo FIXO de modelos whisper (ADR-017: origem fixa + SHA-256 codificado no app — trocar de
 * modelo é atualização de aplicativo). Hashes conferidos na origem oficial (Hugging Face,
 * API ggerganov/whisper.cpp, 2026-10-12): tiny/base idênticos a `corpus/modelos.sha256` (ADR-013);
 * small obtidos da mesma origem.
 *
 * Recomendação do ADR-013: base q5_1 (WER pt-BR ~10-12% com 60% do tamanho do f16).
 */
data class ModeloInfo(
    val nome: String,
    val url: String,
    val sha256: String,
    val tamanhoMb: Float,
    val rotulo: String,
    val recomendado: Boolean = false,
)

object CatalogoModelos {

    val modelos: List<ModeloInfo> = listOf(
        ModeloInfo(
            nome = "ggml-tiny-q5_1.bin",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin",
            sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7",
            tamanhoMb = 32f,
            rotulo = "Tiny (rápido, ~32 MB)",
        ),
        ModeloInfo(
            nome = "ggml-tiny.bin",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            sha256 = "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
            tamanhoMb = 78f,
            rotulo = "Tiny f16 (~78 MB)",
        ),
        ModeloInfo(
            nome = "ggml-base-q5_1.bin",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
            sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
            tamanhoMb = 60f,
            rotulo = "Base (recomendado, ~60 MB)",
            recomendado = true,
        ),
        ModeloInfo(
            nome = "ggml-base.bin",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
            sha256 = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",
            tamanhoMb = 148f,
            rotulo = "Base f16 (~148 MB)",
        ),
        ModeloInfo(
            nome = "ggml-small-q5_1.bin",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
            tamanhoMb = 190f,
            rotulo = "Small (melhor WER, lento, ~190 MB)",
        ),
        ModeloInfo(
            nome = "ggml-small.bin",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
            sha256 = "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b",
            tamanhoMb = 488f,
            rotulo = "Small f16 (~488 MB)",
        ),
    )

    fun porNome(nome: String): ModeloInfo? = modelos.firstOrNull { it.nome == nome }
}
