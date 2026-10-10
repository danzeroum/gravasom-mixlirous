//! Análise de áudio exposta ao Kotlin: BPM, tonalidade, energia por trecho e
//! loudness. Tudo calculado pelo `audio_core` da Mixlirous sobre PCM já
//! decodificado no Gravasom (PcmDecoder) — o Rust nunca lê arquivos.

use audio_core::dsp::analysis::{beat_tracking::DefaultAnalyzer, key_detection};
use audio_core::ports::AudioAnalyzer;
use ndarray::Array1;

/// Energia RMS de um trecho do sinal (janela fixa da análise).
#[derive(Debug, Clone, uniffi::Record)]
pub struct EnergiaTrecho {
    /// Início do trecho, em segundos.
    pub inicio_s: f32,
    /// Fim do trecho, em segundos.
    pub fim_s: f32,
    /// RMS médio do trecho, em [0, 1].
    pub rms: f32,
}

/// Resultado da análise de uma gravação. Alimenta etiquetas automáticas
/// ("120 BPM · Lá menor"), cor de energia na waveform e os filtros da
/// biblioteca no app.
#[derive(Debug, Clone, uniffi::Record)]
pub struct AnaliseAudio {
    /// BPM estimado; `None` quando o material é curto demais ou atípico.
    pub bpm: Option<f32>,
    pub bpm_confianca: Option<f32>,
    /// Tônica: 0 = Dó, 1 = Dó♯, …, 11 = Si.
    pub tonica: Option<u8>,
    /// "maior" ou "menor".
    pub modo: Option<String>,
    pub tom_confianca: Option<f32>,
    /// Loudness integrado (ITU-R BS.1770-4), em LUFS.
    pub loudness_lufs: Option<f32>,
    /// RMS médio do material inteiro.
    pub energia_media: f32,
    /// Desvio-padrão do RMS quadro a quadro (dinâmica).
    pub energia_desvio: f32,
    /// Pico absoluto, em dBFS (−99 quando silêncio).
    pub pico_db: f32,
    /// Energia por trecho fixo — base da cor de energia na waveform.
    pub energia_por_trecho: Vec<EnergiaTrecho>,
}

/// Rótulo curto pronto para a etiqueta da biblioteca: "120 BPM · Lá menor".
pub fn rotulo_tom(tonica: u8, modo: &str) -> String {
    let nomes = [
        "Dó", "Dó♯", "Ré", "Ré♯", "Mi", "Fá", "Fá♯", "Sol", "Sol♯", "Lá", "Lá♯", "Si",
    ];
    let nome = nomes[(tonica % 12) as usize];
    match modo {
        "menor" => format!("{nome} menor"),
        _ => format!("{nome} maior"),
    }
}

const TRECHO_SEGUNDOS: f32 = 5.0;

/// Executa a análise completa sobre PCM mono a `sample_rate`.
pub fn analisar(pcm: &[f32], sample_rate: u32) -> AnaliseAudio {
    let array = Array1::from(pcm.to_vec());
    let analyzer = DefaultAnalyzer;

    // Batidas (B.1 + B.2 de docs/04): onset strength + BPM + grade.
    let params = audio_core::domain::BeatDetectionParams {
        sample_rate,
        ..Default::default()
    };
    let beats = analyzer.detect_beats(&array, &params);
    let bpm = estimar_bpm_do_grid(&beats, sample_rate);
    let bpm_confianca = beats.first().map(|b| b.onset_strength.clamp(0.0, 1.0));

    // Tonalidade (B.5): chroma agregado → perfil de chave.
    let chroma = analyzer.extract_chroma(&array, sample_rate);
    let tonal = key_detection::detect_key(&chroma);
    let modo = match tonal.mode {
        key_detection::KeyMode::Major => "maior",
        key_detection::KeyMode::Minor => "menor",
    };

    // Energia: perfil RMS (B.4) + janelas fixas de TRECHO_SEGUNDOS.
    let profile = analyzer.analyze_energy_profile(&array, 2048, 512);
    let energia_por_trecho = energia_em_trechos(pcm, sample_rate, TRECHO_SEGUNDOS);

    // Loudness integrado (BS.1770-4).
    let lufs = audio_core::dsp::mastering::measure_lufs(&array, sample_rate);
    let loudness_lufs = if lufs.is_finite() { Some(lufs) } else { None };

    AnaliseAudio {
        bpm,
        bpm_confianca,
        tonica: Some(tonal.root),
        modo: Some(modo.to_string()),
        tom_confianca: Some(tonal.confidence),
        loudness_lufs,
        energia_media: profile.rms_mean,
        energia_desvio: profile.rms_std,
        pico_db: profile.peak_db,
        energia_por_trecho,
    }
}

/// BPM mediano implícito na grade de batidas detectadas (mais estável que o
/// estimador global quando a grade já é confiável).
fn estimar_bpm_do_grid(
    beats: &[audio_core::domain::BeatCandidate],
    sample_rate: u32,
) -> Option<f32> {
    if beats.len() < 4 {
        return None;
    }
    let mut intervalos: Vec<f32> = beats
        .windows(2)
        .map(|par| (par[1].sample_idx as f32 - par[0].sample_idx as f32) / sample_rate as f32)
        .filter(|d| *d > 0.0)
        .collect();
    if intervalos.is_empty() {
        return None;
    }
    intervalos.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
    let mediana = intervalos[intervalos.len() / 2];
    let bpm = 60.0 / mediana;
    // Grade impossível (BPM fora de 30–300) é sinal de detecção falha.
    (30.0..=300.0).contains(&bpm).then_some(bpm)
}

/// Divide o PCM em janelas fixas e devolve o RMS médio de cada uma.
fn energia_em_trechos(pcm: &[f32], sample_rate: u32, trecho_s: f32) -> Vec<EnergiaTrecho> {
    let janela = ((sample_rate as f32 * trecho_s) as usize).max(1);
    let total = pcm.len();
    pcm.chunks(janela)
        .enumerate()
        .map(|(i, janela_pcm)| {
            let soma: f64 = janela_pcm.iter().map(|x| (*x as f64).powi(2)).sum();
            let rms = (soma / janela_pcm.len().max(1) as f64).sqrt() as f32;
            let inicio = i * janela;
            // O fim do último trecho é o fim real do buffer, não a janela cheia.
            let fim = (inicio + janela_pcm.len()).min(total);
            EnergiaTrecho {
                inicio_s: inicio as f32 / sample_rate as f32,
                fim_s: fim as f32 / sample_rate as f32,
                rms,
            }
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn seno(frequencia: f32, segundos: f32, sample_rate: u32, amplitude: f32) -> Vec<f32> {
        let n = (segundos * sample_rate as f32) as usize;
        (0..n)
            .map(|i| {
                amplitude
                    * (2.0 * std::f32::consts::PI * frequencia * i as f32 / sample_rate as f32)
                        .sin()
            })
            .collect()
    }

    #[test]
    fn seno_amplitude_1_tem_rms_07071() {
        // Invariante B.4 de docs/04: rms(seno amplitude 1) ≈ 0,7071.
        let pcm = seno(440.0, 5.0, 44_100, 1.0);
        let a = analisar(&pcm, 44_100);
        assert!(
            (a.energia_media - std::f32::consts::FRAC_1_SQRT_2).abs() < 0.01,
            "rms do seno foi {}",
            a.energia_media
        );
    }

    #[test]
    fn tonalidade_seno_puro_em_la_concentra_na_classe_9() {
        // Invariante B.5: seno 440 Hz concentra a energia na classe Lá (9).
        let pcm = seno(440.0, 10.0, 44_100, 0.9);
        let a = analisar(&pcm, 44_100);
        assert_eq!(a.tonica, Some(9), "tônica esperada Lá, veio {:?}", a.tonica);
    }

    #[test]
    fn silencio_nao_gera_bpm_e_loudness_nao_finito_fica_none() {
        let pcm = vec![0.0f32; 44_100 * 3];
        let a = analisar(&pcm, 44_100);
        assert!(a.bpm.is_none());
        assert_eq!(a.energia_media, 0.0);
        assert!(
            a.loudness_lufs.is_none(),
            "LUFS de silêncio não deve expor −inf"
        );
    }

    #[test]
    fn trechos_cobrem_a_duracao_toda() {
        let pcm = seno(220.0, 12.0, 44_100, 0.5);
        let a = analisar(&pcm, 44_100);
        assert_eq!(
            a.energia_por_trecho.len(),
            3,
            "12 s em trechos de 5 s → 3 janelas"
        );
        assert!((a.energia_por_trecho[2].fim_s - 12.0).abs() < 0.01);
    }

    #[test]
    fn rotulo_usa_nomes_em_portugues() {
        assert_eq!(rotulo_tom(9, "menor"), "Lá menor");
        assert_eq!(rotulo_tom(0, "maior"), "Dó maior");
        assert_eq!(rotulo_tom(6, "maior"), "Fá♯ maior");
    }
}
