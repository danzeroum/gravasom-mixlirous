package com.gravasom.feature.criar

import org.json.JSONObject

/**
 * Receitas JSON das abas F6. Base é a receita manual verificada no host (mesma
 * forma da aba Receita): o motor revalida no worker — qualquer campo fora dos
 * limites volta como ErroReceita legível (KEY_RECUSADO).
 */
object Receitas {

    /** Receita completa com os campos que as abas ajustam. */
    fun montar(
        duracaoAlvoS: Int,
        crossfadeMs: Long,
        lufsAlvo: Double = -14.0,
        destino: String = "voz",
    ): String = runCatching {
        // Base do motor por destino quando há motor (ABI presente); caia na manual o contrário.
        val base = runCatching {
            com.gravasom.core.mixengine.MotorMixlirous.obter().receitaParaDestino(destino, duracaoAlvoS.toULong())
        }.getOrElse { manual(duracaoAlvoS, crossfadeMs, lufsAlvo) }
        val json = JSONObject(base)
        json.put(
            "target_duration",
            JSONObject().put("secs", duracaoAlvoS.toLong()).put("nanos", 0),
        )
        json.getJSONObject("crossfade").put("max_duration_ms", crossfadeMs.toInt())
        json.toString()
    }.getOrElse { manual(duracaoAlvoS, crossfadeMs, lufsAlvo) }

    /** Receita manual (fallback sem motor), forma verificada no host. */
    fun manual(duracaoAlvoS: Int, crossfadeMs: Long, lufsAlvo: Double): String = JSONObject().apply {
        put(
            "target_duration",
            JSONObject().put("secs", duracaoAlvoS.toLong()).put("nanos", 0),
        )
        put(
            "crossfade",
            JSONObject()
                .put("enabled", crossfadeMs > 0)
                .put("max_duration_ms", crossfadeMs.toInt())
                .put("curve", "constant_power"),
        )
        put(
            "mastering",
            JSONObject()
                .put("lufs_target", lufsAlvo)
                .put("peak_db", -1.0)
                .put("enable_limiting", true)
                .put("compression_ratio", 2.0),
        )
        put(
            "selection",
            JSONObject()
                .put("min_strong_beat_percentile", 0.8)
                .put("block_size_beats", 4)
                .put("preserve_intro_ms", 3000)
                .put("preserve_outro_ms", 3000),
        )
        put(
            "format",
            JSONObject().put("sample_rate", 44100).put("channels", 2).put("bit_depth", 24).put("codec", "WAV"),
        )
        put(
            "tuning",
            JSONObject()
                .put("enabled", false)
                .put("mode", "disabled")
                .put("max_global_cents", 50.0)
                .put("min_confidence", 0.7)
                .put("force_tonic_hz", JSONObject.NULL)
                .put("force_mode", JSONObject.NULL),
        )
    }.toString()
}
