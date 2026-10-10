//! Pipeline de remix exposto ao Kotlin: recebe PCM mono decodificado +
//! receita JSON validada, executa `DefaultRemixPipeline` da Mixlirous
//! (analisar → segmentar → selecionar → emendar → masterizar) e devolve PCM.
//! Não destrutivo por construção: o original fica no Kotlin, o Rust só devolve
//! um buffer novo.

use crate::receita::{validar_receita, ErroReceita, ReceitaValidada};
use audio_core::domain::AudioFingerprint;
use audio_core::dsp::pipeline::{DefaultRemixPipeline, PipelineInput, RemixPipeline};
use ndarray::Array1;

/// Erros do render de remix visíveis ao Kotlin.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ErroRemix {
    #[error("Receita inválida: {mensagem}")]
    ReceitaInvalida { mensagem: String },
    #[error("O motor recusou o render: {mensagem}")]
    Motor { mensagem: String },
}

/// Fingerprint acústico (espelho de `audio_core::domain::AudioFingerprint`).
/// Serve para comparar renders entre si ("mesmo som" = distância < 0,05) e
/// como golden master da receita.
#[derive(Debug, Clone, uniffi::Record)]
pub struct FingerprintAudio {
    pub mfcc: Vec<f32>,
    pub centroid_espectral: f32,
    pub rms_energia: f32,
    pub contraste_espectral: Vec<f32>,
    pub razao_pico: f32,
    pub duracao_s: f32,
    pub lufs: Option<f32>,
}

impl FingerprintAudio {
    fn do_motor(fp: AudioFingerprint) -> Self {
        Self {
            mfcc: fp.mfcc,
            centroid_espectral: fp.spectral_centroid,
            rms_energia: fp.rms_energy,
            contraste_espectral: fp.spectral_contrast,
            razao_pico: fp.peak_ratio,
            duracao_s: fp.duration,
            lufs: fp.lufs,
        }
    }

    fn para_motor(&self) -> Option<AudioFingerprint> {
        Some(AudioFingerprint {
            mfcc: self.mfcc.clone(),
            spectral_centroid: self.centroid_espectral,
            rms_energy: self.rms_energia,
            spectral_contrast: self.contraste_espectral.clone(),
            peak_ratio: self.razao_pico,
            duration: self.duracao_s,
            lufs: self.lufs,
        })
    }
}

/// Resultado do render: PCM novo + fingerprint acústico.
#[derive(Debug, Clone, uniffi::Record)]
pub struct RemixRenderizado {
    /// PCM mono processado, em [-1, 1].
    pub pcm: Vec<f32>,
    /// Taxa de amostragem (igual à entrada).
    pub sample_rate: u32,
    /// Duração do resultado, em segundos.
    pub duracao_s: f32,
    /// BPM estimado durante o pipeline.
    pub bpm: Option<f32>,
    /// Avisos não bloqueantes (ex.: "encaixe brusco", downmix).
    pub avisos: Vec<String>,
    /// Fingerprint acústico do resultado, para comparar renders.
    pub fingerprint: FingerprintAudio,
}

/// Renderiza o remix de um PCM com uma receita JSON. Toda receita passa pela
/// validação antes de chegar ao DSP — não existe caminho de render com receita
/// fora dos limites.
pub fn renderizar_remix(
    pcm: &[f32],
    sample_rate: u32,
    receita_json: &str,
) -> Result<RemixRenderizado, ErroRemix> {
    let receita = validar_receita(receita_json)
        .map_err(|ErroReceita::Invalida { mensagem }| ErroRemix::ReceitaInvalida { mensagem })?;

    let pipeline = DefaultRemixPipeline::new();
    let input = PipelineInput {
        pcm: Array1::from(pcm.to_vec()),
        sample_rate,
        config: config_da_receita(receita_json, &receita)?,
        pre_selected_blocks: None,
    };
    let resultado = pipeline.run(input).map_err(|e| ErroRemix::Motor {
        mensagem: e.to_string(),
    })?;

    // Finitude (I15): nenhum NaN/Inf sai do motor — defesa extra no limite do
    // crate, onde o Kotlin consome o buffer.
    if resultado.pcm.iter().any(|x| !x.is_finite()) {
        return Err(ErroRemix::Motor {
            mensagem: "o render produziu amostras não finitas (I15)".to_string(),
        });
    }

    let fingerprint = AudioFingerprint::from_pcm(&resultado.pcm, sample_rate);
    Ok(RemixRenderizado {
        pcm: resultado.pcm.to_vec(),
        sample_rate: resultado.sample_rate,
        duracao_s: resultado.duration_sec(),
        bpm: resultado.bpm_estimate,
        avisos: resultado.warnings,
        fingerprint: FingerprintAudio::do_motor(fingerprint),
    })
}

/// Distância de fingerprint entre dois renders (0 = idênticos; < 0,05 no
/// aceite = "mesmo som"). `None` quando os vetores não são comparáveis.
pub fn distancia_fingerprint(a: &FingerprintAudio, b: &FingerprintAudio) -> Option<f32> {
    let fa = a.para_motor()?;
    let fb = b.para_motor()?;
    Some(fa.distance(&fb))
}

/// Reconstrói o `PipelineConfig` a partir do JSON validado.
fn config_da_receita(
    json: &str,
    _validada: &ReceitaValidada,
) -> Result<audio_core::domain::PipelineConfig, ErroRemix> {
    serde_json::from_str(json).map_err(|e| ErroRemix::ReceitaInvalida {
        mensagem: e.to_string(),
    })
}

/// Fingerprint de um PCM bruto (ex.: comparar um render com o original).
pub fn fingerprint_de_pcm(pcm: &[f32], sample_rate: u32) -> FingerprintAudio {
    FingerprintAudio::do_motor(AudioFingerprint::from_pcm(
        &Array1::from(pcm.to_vec()),
        sample_rate,
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Sintetiza uma trilha com batidas: cliques periódicos + seno grave, para
    /// o detector de batidas ter grade clara.
    fn trilha_sintetica(sample_rate: u32, segundos: f32) -> Vec<f32> {
        let n = (sample_rate as f32 * segundos) as usize;
        let mut pcm = vec![0.0f32; n];
        let periodo = sample_rate as f32 * 0.5; // 120 BPM
        for (i, slot) in pcm.iter_mut().enumerate() {
            let t = i as f32 / sample_rate as f32;
            // Clique forte a cada 0,5 s (batida).
            let fase_batida = (i as f32 % periodo) / periodo;
            let clique = if fase_batida < 0.01 { 0.9 } else { 0.0 };
            // Baixo contínuo em Lá (110 Hz), dá tom detectável.
            let baixo = 0.35 * (2.0 * std::f32::consts::PI * 110.0 * t).sin();
            // Variação de energia ao longo do tempo (ponte para knapsack).
            let envelope = 0.4 + 0.6 * (t / segundos.max(1.0));
            *slot = (clique + baixo) * envelope;
        }
        pcm
    }

    #[test]
    fn render_da_receita_padrao_produz_pcm_finito_e_no_teto() {
        let sr = 44_100u32;
        let pcm = trilha_sintetica(sr, 20.0);
        let receita = crate::receita::receita_padrao();
        let out = renderizar_remix(&pcm, sr, &receita).expect("render deve passar");
        assert!(!out.pcm.is_empty());
        assert!(out.pcm.iter().all(|x| x.is_finite()));
        // Teto do limiter: peak_db −1,0 dBFS ⇒ amostra ≤ 10^(−1/20) ≈ 0,891 (+ ε).
        let pico = out.pcm.iter().fold(0.0f32, |m, x| m.max(x.abs()));
        assert!(pico <= 0.9, "pico {pico} acima do teto do limiter");
        assert!(out.duracao_s > 0.0 && out.duracao_s <= 25.0);
    }

    #[test]
    fn mesma_receita_dois_renders_dao_fingerprint_menor_que_005() {
        // Aceite do plano: fingerprint < 0,05 entre dois renders da mesma receita.
        let sr = 44_100u32;
        let pcm = trilha_sintetica(sr, 15.0);
        let receita = crate::receita::receita_padrao();
        let a = renderizar_remix(&pcm, sr, &receita).unwrap();
        let b = renderizar_remix(&pcm, sr, &receita).unwrap();
        let d = distancia_fingerprint(&a.fingerprint, &b.fingerprint)
            .expect("fingerprints comparáveis");
        assert!(d < 0.05, "distância {d} ≥ 0,05 entre renders idênticos");
    }

    #[test]
    fn receita_fora_dos_limites_nunca_chega_ao_render() {
        // Teste de propriedade do plano, amostrado: 200 receitas com
        // crossfade aleatório fora da faixa — nenhuma pode renderizar.
        let sr = 44_100u32;
        let pcm = trilha_sintetica(sr, 2.0);
        let mut seed = 12345u64;
        let mut rng = || {
            seed = seed
                .wrapping_mul(6364136223846793005)
                .wrapping_add(1442695040888963407);
            (seed >> 33) as u32
        };
        let mut bloqueadas = 0;
        for _ in 0..200 {
            let mut v: serde_json::Value =
                serde_json::from_str(&crate::receita::receita_padrao()).unwrap();
            let crossfade = 3001 + rng() % 10_000;
            v["crossfade"]["max_duration_ms"] = serde_json::json!(crossfade);
            let r = renderizar_remix(&pcm, sr, &v.to_string());
            match r {
                Err(ErroRemix::ReceitaInvalida { .. }) => bloqueadas += 1,
                Err(ErroRemix::Motor { mensagem }) => panic!("recusa errada: {mensagem}"),
                Ok(_) => panic!("crossfade {crossfade} ms passou da validação!"),
            }
        }
        assert_eq!(bloqueadas, 200);
    }

    #[test]
    fn buffer_curto_e_rejeitado_com_erro_legivel() {
        let receita = crate::receita::receita_padrao();
        let r = renderizar_remix(&[0.0f32; 10], 44_100, &receita);
        assert!(matches!(r, Err(ErroRemix::Motor { .. })));
    }
}
