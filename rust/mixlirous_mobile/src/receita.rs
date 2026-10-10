//! Receita de remix (`PipelineConfig` da Mixlirous) em JSON + validação.
//!
//! A IA nunca toca em PCM: ela preenche (ou o usuário monta) um JSON de receita.
//! A validação acontece na **desserialização** — os newtypes do `audio_core`
//! (`CrossfadeMs`, `CompressionRatio`, `LufsTarget`, …) tornam um valor fora da
//! faixa irrepresentável (`docs/16-CORRECOES-DSP` T0.0, I14). Um JSON
//! alucinado com `crossfade_ms: 5000` falha aqui, nunca no render.

use audio_core::domain::PipelineConfig;

/// Erros de receita visíveis ao Kotlin.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ErroReceita {
    #[error("Receita inválida: {mensagem}")]
    Invalida { mensagem: String },
}

/// Uma receita validada — só existe se o JSON passou pelos newtypes.
#[derive(Debug, Clone, uniffi::Record)]
pub struct ReceitaValidada {
    /// Duração alvo em segundos.
    pub duracao_alvo_s: f64,
    /// Crossfade máximo entre blocos, em ms (0–3000, garantido).
    pub crossfade_ms: u32,
    /// Alvo de loudness em LUFS (−30..−6, garantido).
    pub lufs_alvo: f64,
    /// Teto de true peak em dB.
    pub pico_db: f64,
    /// Razão de compressão (1–10, garantido).
    pub compressao_ratio: f64,
    /// Blocos por batida (1–64, garantido).
    pub blocos_por_batida: u32,
    /// Receita normalizada, para salvar junto do render ("Refazer igual").
    pub json: String,
}

/// Valida um JSON de receita. Só existe caminho válido pela desserialização.
pub fn validar_receita(json: &str) -> Result<ReceitaValidada, ErroReceita> {
    let config: PipelineConfig = serde_json::from_str(json).map_err(|e| ErroReceita::Invalida {
        mensagem: e.to_string(),
    })?;

    // Invariante de domínio (docs/04 §1, TargetDuration): duração alvo tem que
    // ser positiva. `std::time::Duration` não é newtype no upstream — a barreira
    // fica aqui, na ValidationLayer do app, antes de qualquer render.
    if config.target_duration.is_zero() {
        return Err(ErroReceita::Invalida {
            mensagem: "target_duration deve ser maior que zero".to_string(),
        });
    }
    // Teto de true peak: tabela canônica (docs/05 §3) — min −6, max 0 dB.
    // `MasteringConfig.peak_db` é f32 cru no upstream (exceção conhecida de
    // T0.0, docs/16); a barreira fica aqui até virar newtype lá.
    if !(-6.0..=0.0).contains(&config.mastering.peak_db) {
        return Err(ErroReceita::Invalida {
            mensagem: format!(
                "peak_db deve estar entre -6 e 0; recebido {}",
                config.mastering.peak_db
            ),
        });
    }

    // Re-serializa a receita normalizada: é este JSON que o app salva junto do
    // render para "Refazer igual" (determinístico: mesma receita → mesmo som).
    let normalizado = serde_json::to_string(&config).map_err(|e| ErroReceita::Invalida {
        mensagem: e.to_string(),
    })?;

    Ok(ReceitaValidada {
        duracao_alvo_s: config.target_duration.as_secs_f64(),
        crossfade_ms: config.crossfade.max_duration_ms.get(),
        lufs_alvo: config.mastering.lufs_target.get() as f64,
        pico_db: config.mastering.peak_db as f64,
        compressao_ratio: config.mastering.compression_ratio.get() as f64,
        blocos_por_batida: config.selection.block_size_beats.get() as u32,
        json: normalizado,
    })
}

/// Receita padrão do destino (fallback quando não há modelo ou o usuário não
/// ajusta nada): 30 s, crossfade 1000 ms de potência constante, −14 LUFS
/// (streaming/Reels/Shorts), blocos de 8 batidas.
pub fn receita_padrao() -> String {
    let config = PipelineConfig::default();
    serde_json::to_string(&config).unwrap_or_else(|_| "{}".to_string())
}

/// Receita padrão com duração e LUFS ajustados ao destino do app.
/// Destinos: "reels" | "shorts" | "streaming" → −14 LUFS; "voz" → −16 LUFS.
pub fn receita_para_destino(destino: &str, duracao_s: u64) -> Result<String, ErroReceita> {
    let lufs: f32 = match destino {
        "reels" | "shorts" | "streaming" => -14.0,
        "voz" => -16.0,
        outro => {
            return Err(ErroReceita::Invalida {
                mensagem: format!("destino desconhecido: {outro}"),
            })
        }
    };
    let config = PipelineConfig {
        target_duration: std::time::Duration::from_secs(duracao_s.max(1)),
        mastering: audio_core::domain::MasteringConfig {
            lufs_target: audio_core::LufsTarget::try_from(lufs).map_err(|e| {
                ErroReceita::Invalida {
                    mensagem: e.to_string(),
                }
            })?,
            ..PipelineConfig::default().mastering
        },
        ..PipelineConfig::default()
    };
    serde_json::to_string(&config).map_err(|e| ErroReceita::Invalida {
        mensagem: e.to_string(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn crossfade_de_5000_ms_e_rejeitado_pela_desserializacao() {
        // Teste do plano: receita com crossfade de 5000 ms → ValidationLayer
        // rejeita. Aqui a rejeição é mais cedo ainda: no newtype.
        let mut maldita: serde_json::Value =
            serde_json::from_str(&receita_padrao()).expect("padrão é JSON válido");
        maldita["crossfade"]["max_duration_ms"] = serde_json::json!(5000);
        let maldita = maldita.to_string();
        let err = validar_receita(&maldita).unwrap_err();
        assert!(
            err.to_string().contains("crossfade_ms"),
            "mensagem não menciona o campo: {err}"
        );
    }

    #[test]
    fn receita_padrao_e_valida_e_circular() {
        let json = receita_padrao();
        let v = validar_receita(&json).expect("padrão deve validar");
        assert!(v.duracao_alvo_s > 0.0);
        assert!(v.crossfade_ms <= 3000);
        // Revalidar o JSON normalizado tem que dar o mesmo resultado.
        let v2 = validar_receita(&v.json).expect("normalizado deve validar");
        assert_eq!(v.json, v2.json);
    }

    #[test]
    fn destinos_mapeiam_lufs_corretos() {
        let reels = validar_receita(&receita_para_destino("reels", 30).unwrap()).unwrap();
        assert_eq!(reels.lufs_alvo, -14.0);
        assert_eq!(reels.duracao_alvo_s, 30.0);
        let voz = validar_receita(&receita_para_destino("voz", 60).unwrap()).unwrap();
        assert_eq!(voz.lufs_alvo, -16.0);
        assert!(receita_para_destino("facebook", 30).is_err());
    }

    #[test]
    fn json_aleatorio_nao_vira_receita() {
        // Teste de propriedade do plano (1000 saídas aleatórias) — semente aqui.
        let lixo = ["{}", "null", "[]", "{\"target_duration\": 3}", "não é json"];
        for caso in lixo {
            assert!(validar_receita(caso).is_err(), "{caso} não pode validar");
        }
    }
}
