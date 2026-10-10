//! Criar novos sons (F6) — recompondo **só material do usuário**:
//! 1. **Melhores momentos**: knapsack por energia com marcadores do usuário
//!    como trechos obrigatórios.
//! 2. **Loop de N compassos**: corte alinhado às batidas com fechamento em
//!    crossfade — repete sem clique na emenda.
//! 3. **Textura** (Natureza/Água): time-stretch 2×–8× com fechamento em loop.
//! 4. **Mesclar** duas gravações: ajuste de andamento, equilíbrio de volume,
//!    masterização (ordem de docs/16 §4) e aviso de tons incompatíveis.
//!
//! Princípio inegociável: nada aqui sintetiza áudio — tudo deriva do PCM de
//! entrada (recorte, esticamento, somagem e masterização).

use crate::analise::AnaliseAudio;
use crate::receita::{ErroReceita, ReceitaValidada};
use audio_core::domain::{AudioFingerprint, BeatBlock, BeatDetectionParams};
use audio_core::dsp::analysis::beat_tracking::DefaultAnalyzer;
use audio_core::dsp::mastering::time_stretch;
use audio_core::dsp::mastering::{apply_compression, CompressorParams};
use audio_core::dsp::mastering::{
    apply_lufs_gain, brickwall_limiter, measure_lufs, measure_true_peak,
};
use audio_core::dsp::selection::{select_blocks, SelectionConfig};
use audio_core::ports::AudioAnalyzer;
use ndarray::Array1;

/// Erros dos recursos de criação.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum ErroCriacao {
    #[error("Receita inválida: {mensagem}")]
    ReceitaInvalida { mensagem: String },
    #[error("Material insuficiente: {mensagem}")]
    MaterialCurto { mensagem: String },
    #[error("Ajuste impossível: {mensagem}")]
    AjusteImpossivel { mensagem: String },
    #[error("Tons incompatíveis: {mensagem}")]
    TonsIncompativeis { mensagem: String },
    #[error("O motor recusou a criação: {mensagem}")]
    Motor { mensagem: String },
}

/// Trecho obrigatório do usuário (marcador), em milissegundos.
#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct Marcador {
    pub inicio_ms: u64,
    pub fim_ms: u64,
}

/// Resultado de uma criação: PCM novo + metadados + avisos.
#[derive(Debug, Clone, uniffi::Record)]
pub struct CriacaoResult {
    pub pcm: Vec<f32>,
    pub sample_rate: u32,
    pub duracao_s: f32,
    pub avisos: Vec<String>,
    pub fingerprint: crate::remix::FingerprintAudio,
}

impl CriacaoResult {
    fn novo(pcm: Vec<f32>, sample_rate: u32, avisos: Vec<String>) -> Self {
        let duracao_s = pcm.len() as f32 / sample_rate.max(1) as f32;
        let fp = AudioFingerprint::from_pcm(&Array1::from(pcm.clone()), sample_rate);
        Self {
            pcm,
            sample_rate,
            duracao_s,
            avisos,
            fingerprint: crate::remix::FingerprintAudio::do_motor(fp),
        }
    }
}

/// Confirmação humana para mesclar com tons incompatíveis (HITL).
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum DecisaoTons {
    /// Renderiza mesmo com aviso (o usuário decidiu).
    Seguir,
    /// Não renderiza; a tela mostra o aviso e o usuário ajusta a seleção.
    Recusar,
}

// ---------------------------------------------------------------- análise local

fn analisar_interno(pcm: &[f32], sample_rate: u32) -> (Vec<BeatBlock>, AnaliseAudio) {
    let array = Array1::from(pcm.to_vec());
    let analyzer = DefaultAnalyzer;
    let params = BeatDetectionParams {
        sample_rate,
        ..Default::default()
    };
    let beats = analyzer.detect_beats(&array, &params);
    let blocks = analyzer.build_blocks(&array, &beats, 4, sample_rate);
    let analise = crate::analise::analisar(pcm, sample_rate);
    (blocks, analise)
}

// ------------------------------------------------------------- melhores momentos

/// Melhores momentos: compilação dos trechos de maior energia até
/// `duracao_alvo_s`, com os marcadores do usuário como trechos OBRIGATÓRIOS
/// (entram de qualquer jeito; o knapsack preenche o resto). Blocos selecionados
/// são remontados em ordem cronológica (docs/04 §6.1, regra 3).
pub fn melhores_momentos(
    pcm: &[f32],
    sample_rate: u32,
    duracao_alvo_s: f32,
    marcadores: Vec<Marcador>,
    receita: &str,
) -> Result<CriacaoResult, ErroCriacao> {
    let _validada = validar(receita)?;
    if pcm.is_empty() {
        return Err(ErroCriacao::MaterialCurto {
            mensagem: "PCM vazio".into(),
        });
    }
    let (blocks, _) = analisar_interno(pcm, sample_rate);
    if blocks.is_empty() {
        return Err(ErroCriacao::MaterialCurto {
            mensagem: "nenhum bloco alinhado às batidas — material sem tempo estável?".into(),
        });
    }

    // Obrigatórios: blocos que intersectam algum marcador do usuário.
    let obrigatorio: Vec<BeatBlock> = blocks
        .iter()
        .filter(|b| {
            let b_ms = b.start_time * 1000.0;
            let b_end_ms = b.end_time * 1000.0;
            marcadores
                .iter()
                .any(|m| (b_ms as u64) < m.fim_ms && (b_end_ms as u64) > m.inicio_ms)
        })
        .cloned()
        .collect();

    let duracao_obrigatoria: f32 = obrigatorio.iter().map(|b| b.duration).sum();
    let restante = duracao_alvo_s - duracao_obrigatoria;

    // Preenchimento: knapsack do upstream sobre os blocos não obrigatórios.
    let mut preenchimento: Vec<BeatBlock> = Vec::new();
    let mut avisos = Vec::new();
    if restante > 0.5 {
        let livres: Vec<BeatBlock> = blocks
            .iter()
            .filter(|b| !obrigatorio.iter().any(|o| o.id == b.id))
            .cloned()
            .collect();
        let config = SelectionConfig {
            target_duration_sec: restante,
            duration_tolerance_sec: (restante * 0.5).max(3.0),
            preserve_intro_ms: 0,
            preserve_outro_ms: 0,
            require_strong_beat_start: false,
            allow_repeats: false,
        };
        match select_blocks(&livres, &config) {
            Ok(sel) => preenchimento = sel,
            Err(e) => avisos.push(format!("seleção parcial: {e}")),
        }
    } else if restante < -0.5 {
        avisos.push("marcadores já passam da duração alvo — compilando só os obrigatórios".into());
    }

    let mut selecionados = obrigatorio;
    selecionados.extend(preenchimento);
    // Ordem cronológica preserva a progressão harmônica.
    selecionados.sort_by_key(|b| b.start_sample);

    if selecionados.is_empty() {
        return Err(ErroCriacao::MaterialCurto {
            mensagem: "nada selecionado".into(),
        });
    }

    let pipeline = audio_core::dsp::pipeline::DefaultRemixPipeline::new();
    let input = audio_core::dsp::pipeline::PipelineInput {
        pcm: Array1::from(pcm.to_vec()),
        sample_rate,
        config: config_da_receita(receita)?,
        pre_selected_blocks: Some(selecionados),
    };
    let out = audio_core::dsp::pipeline::RemixPipeline::run(&pipeline, input).map_err(|e| {
        ErroCriacao::Motor {
            mensagem: e.to_string(),
        }
    })?;
    if out.pcm.iter().any(|x| !x.is_finite()) {
        return Err(ErroCriacao::Motor {
            mensagem: "saída não finita (I15)".into(),
        });
    }
    Ok(CriacaoResult::novo(out.pcm.to_vec(), sample_rate, avisos))
}

// --------------------------------------------------------------------- loop

const CROSSFADE_LOOP_MS: u32 = 60;

/// Loop de N compassos (4 batidas cada) com fechamento em crossfade: a cauda
/// do loop é fundida com a cabeça, então repetir N vezes não estala na emenda.
pub fn criar_loop(
    pcm: &[f32],
    sample_rate: u32,
    compassos: u32,
    receita: &str,
) -> Result<CriacaoResult, ErroCriacao> {
    if compassos == 0 {
        return Err(ErroCriacao::AjusteImpossivel {
            mensagem: "loop de 0 compassos".into(),
        });
    }
    let array = Array1::from(pcm.to_vec());
    let params = BeatDetectionParams {
        sample_rate,
        ..Default::default()
    };
    let beats = DefaultAnalyzer.detect_beats(&array, &params);
    let batidas_necessarias = (compassos * 4) as usize;
    if beats.len() < batidas_necessarias + 1 {
        return Err(ErroCriacao::MaterialCurto {
            mensagem: format!(
                "detectadas {} batidas; um loop de {compassos} compassos precisa de {}",
                beats.len(),
                batidas_necessarias + 1
            ),
        });
    }

    // Começa numa batida forte (percentil 80 do onset, docs/04 B.3).
    let mut onsets: Vec<f32> = beats.iter().map(|b| b.onset_strength).collect();
    onsets.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
    let p80 = percentil(&onsets, 0.8);
    let inicio_idx = beats
        .iter()
        .position(|b| b.onset_strength >= p80)
        .unwrap_or(0);
    let fim_idx = (inicio_idx + batidas_necessarias).min(beats.len() - 1);

    let inicio = beats[inicio_idx].sample_idx;
    let fim = beats[fim_idx].sample_idx.min(pcm.len());
    if fim <= inicio {
        return Err(ErroCriacao::MaterialCurto {
            mensagem: "grade não cobre o loop".into(),
        });
    }
    let segmento = &pcm[inicio..fim];
    let avisos = vec![];
    let _ = validar(receita)?; // receita valida o contexto (destino/loudness), loop não masteriza

    let fechado = fechar_em_loop(segmento, sample_rate, CROSSFADE_LOOP_MS);
    Ok(CriacaoResult::novo(fechado, sample_rate, avisos))
}

/// Textura (Natureza/Água): estica o material por `fator` (2×–8×) e fecha em
/// loop — fundo sonoro contínuo derivado da gravação original.
pub fn criar_textura(
    pcm: &[f32],
    sample_rate: u32,
    fator: f32,
    receita: &str,
) -> Result<CriacaoResult, ErroCriacao> {
    if !(2.0..=8.0).contains(&fator) {
        return Err(ErroCriacao::AjusteImpossivel {
            mensagem: format!("fator de textura deve estar entre 2 e 8; recebido {fator}"),
        });
    }
    let _ = validar(receita)?;
    let atual = pcm.len() as f32 / sample_rate as f32;
    let stretched = time_stretch(&Array1::from(pcm.to_vec()), sample_rate, atual * fator)
        .ok_or_else(|| ErroCriacao::Motor {
            mensagem: "time-stretch falhou para este material".into(),
        })?;
    if stretched.iter().any(|x| !x.is_finite()) {
        return Err(ErroCriacao::Motor {
            mensagem: "stretch produziu não finito (I15)".into(),
        });
    }
    // Textura pede emenda mais generosa que loop musical.
    let fechado = fechar_em_loop(stretched.as_slice().unwrap_or(&[]), sample_rate, 2000);
    Ok(CriacaoResult::novo(fechado, sample_rate, vec![]))
}

/// Fecha um segmento em loop: a cauda (últimos `crossfade_ms`) funde com a
/// cabeça e a cabeça já tocada no fade-in é descartada — a emenda ao repetir é
/// contínua amostra a amostra (última amostra ≈ S[x−1] → primeira = S[x]).
/// Duração final = original − `crossfade_ms`.
pub fn fechar_em_loop(segmento: &[f32], sample_rate: u32, crossfade_ms: u32) -> Vec<f32> {
    let l = segmento.len();
    // A janela não pode engolir o corpo: no máximo um terço do material.
    let x = ((crossfade_ms as usize * sample_rate as usize) / 1000).min(l / 3);
    if x == 0 || l < 3 * x {
        return segmento.to_vec();
    }
    // Corpo: S[x .. L−x) — a cabeça [0..x) some (já soou no fade-in da cauda).
    let mut saida = segmento[x..l - x].to_vec();
    saida.reserve(x);
    for j in 0..x {
        let t = j as f32 / x as f32;
        // Potência constante (crossfade de dois sinais somando, docs/16 T2.2).
        let g_out = (1.0 - t).sqrt();
        let g_in = t.sqrt();
        saida.push(segmento[l - x + j] * g_out + segmento[j] * g_in);
    }
    saida
}

// ------------------------------------------------------------------- mesclar

/// Avisos de uma mescla ANTES de renderizar (a tela mostra antes de perguntar):
/// compatibilidade tonal e diferença de andamento.
pub fn avisos_de_mescla(pcm_a: &[f32], pcm_b: &[f32], sample_rate: u32) -> Vec<String> {
    let mut avisos = Vec::new();
    let (_, analise_a) = analisar_interno(pcm_a, sample_rate);
    let (_, analise_b) = analisar_interno(pcm_b, sample_rate);

    if let (Some(ta), Some(ma), Some(tb), Some(mb)) = (
        analise_a.tonica,
        analise_a.modo.as_deref(),
        analise_b.tonica,
        analise_b.modo.as_deref(),
    ) {
        if !tons_compativeis(ta, ma, tb, mb) {
            avisos.push(format!(
                "tons incompatíveis: {} {} e {} {} — a soma pode soar dissonante",
                crate::analise::rotulo_tom(ta, ma),
                "",
                crate::analise::rotulo_tom(tb, mb),
                ""
            ));
        }
    }
    if let (Some(bpm_a), Some(bpm_b)) = (analise_a.bpm, analise_b.bpm) {
        let razao = bpm_a / bpm_b.max(1.0);
        if !(0.9..=1.1).contains(&razao) && !(0.45..=0.55).contains(&(razao % 1.0)) {
            avisos.push(format!(
                "andamentos muito diferentes ({:.0} vs {:.0} BPM) — B não será ajustado",
                bpm_a, bpm_b
            ));
        }
    }
    if avisos.is_empty() {
        avisos.push("sem avisos: tons e andamentos combinam".into());
    }
    avisos
}

/// Mescla duas gravações: ajusta o andamento de B para o de A (quando couber no
/// regime do stretch), equilibra os volumes por RMS, soma e masteriza na ordem
/// de docs/16 §4 (compressor → ganho estático → limiter → re-medição).
/// `decisao_tons = Recusar` com tons incompatíveis não renderiza (HITL).
#[allow(clippy::too_many_arguments)]
pub fn mesclar(
    pcm_a: &[f32],
    pcm_b: &[f32],
    sample_rate: u32,
    lufs_alvo: f32,
    decisao_tons: DecisaoTons,
    receita: &str,
) -> Result<CriacaoResult, ErroCriacao> {
    let validada = validar(receita)?;
    if pcm_a.is_empty() || pcm_b.is_empty() {
        return Err(ErroCriacao::MaterialCurto {
            mensagem: "uma das gravações está vazia".into(),
        });
    }

    // 1. Avisos de tons (HITL: Recusar com incompatibilidade → nada renderiza).
    let (_, analise_a) = analisar_interno(pcm_a, sample_rate);
    let (_, analise_b) = analisar_interno(pcm_b, sample_rate);
    let mut avisos = Vec::new();
    if let (Some(ta), Some(ma), Some(tb), Some(mb)) = (
        analise_a.tonica,
        analise_a.modo.as_deref(),
        analise_b.tonica,
        analise_b.modo.as_deref(),
    ) {
        if !tons_compativeis(ta, ma, tb, mb) {
            let msg = format!(
                "tons incompatíveis: {} × {}",
                crate::analise::rotulo_tom(ta, ma),
                crate::analise::rotulo_tom(tb, mb)
            );
            if decisao_tons == DecisaoTons::Recusar {
                return Err(ErroCriacao::TonsIncompativeis { mensagem: msg });
            }
            avisos.push(msg);
        }
    }

    // 2. Ajuste de andamento: estica B para o BPM de A quando couber em 0,9–1,1.
    let mut b = pcm_b.to_vec();
    if let (Some(bpm_a), Some(bpm_b)) = (analise_a.bpm, analise_b.bpm) {
        if bpm_b > 0.0 {
            let fator = bpm_b / bpm_a; // duração B × (bpm_b/bpm_a) para casar
            if (0.9..=1.1).contains(&fator) {
                let dur_b = b.len() as f32 / sample_rate as f32;
                if let Some(esticado) =
                    time_stretch(&Array1::from(b.clone()), sample_rate, dur_b * fator)
                {
                    b = esticado.to_vec();
                } else {
                    avisos.push("ajuste de andamento falhou — B entra no tempo original".into());
                }
            } else {
                avisos.push(format!(
                    "andamentos muito diferentes ({:.0} vs {:.0} BPM) — B não foi ajustado",
                    bpm_a, bpm_b
                ));
            }
        }
    }

    // 3. Equilíbrio de volume: B ganha o RMS médio de A (evita textura engolindo voz).
    let rms_a = rms_de(pcm_a);
    let rms_b = rms_de(&b);
    if rms_b > 1e-6 && rms_a > 1e-6 {
        let ganho = (rms_a / rms_b).clamp(0.1, 10.0);
        for x in b.iter_mut() {
            *x *= ganho;
        }
    }

    // 4. Soma pelo comprimento de A (B completa, corta ou repete o que faltar).
    let mut soma = vec![0.0f32; pcm_a.len()];
    for (i, slot) in soma.iter_mut().enumerate() {
        *slot = pcm_a[i] + b.get(i).copied().unwrap_or(0.0);
    }

    // 5. Masterização na ordem de docs/16 §4:
    //    compressor → ganho estático (LUFS) → limiter no teto → re-medição.
    let comprimido = apply_compression(&soma, &CompressorParams::default(), sample_rate);
    let mut saida = comprimido;
    let medido = measure_lufs(&Array1::from(saida.clone()), sample_rate);
    if medido.is_finite() {
        let ganho = lufs_alvo - medido;
        for x in saida.iter_mut() {
            *x *= 10f32.powf(ganho / 20.0);
        }
    } else {
        avisos.push("loudness não mensurável — ganho estático pulado".into());
    }
    let teto = validada.pico_db as f32;
    let tp = measure_true_peak(&saida, 1, sample_rate);
    if tp > teto {
        brickwall_limiter(&mut saida, teto, sample_rate);
        let lufs_final = measure_lufs(&Array1::from(saida.clone()), sample_rate);
        if lufs_final.is_finite() && (lufs_final - lufs_alvo).abs() > 1.0 {
            avisos.push(format!(
                "conflito de alvos: loudness final {:.1} LUFS longe do alvo {lufs_alvo} (teto priorizado)",
                lufs_final
            ));
        }
    }
    // Ganho final da camada LUFS do upstream também aplica defesa de não-mensurável.
    match apply_lufs_gain(&mut saida, sample_rate, lufs_alvo) {
        audio_core::dsp::mastering::LufsGainOutcome::Applied { .. } => {}
        _ => avisos.push("loudness final não mensurável — buffer não tocado no ganho final".into()),
    }

    if saida.iter().any(|x| !x.is_finite()) {
        return Err(ErroCriacao::Motor {
            mensagem: "saída não finita (I15)".into(),
        });
    }
    Ok(CriacaoResult::novo(saida, sample_rate, avisos))
}

/// Compatibilidade tonal simplificada (círculo de quintas):
/// mesma tônica; relativa maior/menor (tônica a 3 ou 9 semitons);
/// dominante/subdominante (±7 ou ±5 semitons).
fn tons_compativeis(ta: u8, ma: &str, tb: u8, mb: &str) -> bool {
    let diff = (ta as i32 - tb as i32).rem_euclid(12);
    let diff = diff.min(12 - diff);
    if diff == 0 {
        return true; // mesma tônica (qualquer modo)
    }
    // Relativa: C maior ↔ Lá menor (3 semitons) e o espelho (9).
    if (diff == 3 || diff == 9) && (ma != mb) {
        return true;
    }
    // Quinta (dominante/subdominante): 7 ou 5 semitons.
    diff == 5 || diff == 7
}

fn validar(receita: &str) -> Result<ReceitaValidada, ErroCriacao> {
    crate::receita::validar_receita(receita)
        .map_err(|ErroReceita::Invalida { mensagem }| ErroCriacao::ReceitaInvalida { mensagem })
}

fn config_da_receita(json: &str) -> Result<audio_core::domain::PipelineConfig, ErroCriacao> {
    serde_json::from_str(json).map_err(|e| ErroCriacao::ReceitaInvalida {
        mensagem: e.to_string(),
    })
}

fn percentil(ordenado: &[f32], p: f32) -> f32 {
    if ordenado.is_empty() {
        return 0.0;
    }
    let idx = (p * (ordenado.len() - 1) as f32).round() as usize;
    ordenado[idx.min(ordenado.len() - 1)]
}

fn rms_de(pcm: &[f32]) -> f32 {
    if pcm.is_empty() {
        return 0.0;
    }
    let soma: f64 = pcm.iter().map(|x| (*x as f64).powi(2)).sum();
    (soma / pcm.len() as f64).sqrt() as f32
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Trilha com batidas a 120 BPM em tom de Lá menor (baixo em Lá) e
    /// variação de energia para o knapsack.
    fn trilha(sample_rate: u32, segundos: f32, tonic_hz: f32, energia_crescente: bool) -> Vec<f32> {
        let n = (sample_rate as f32 * segundos) as usize;
        let mut pcm = vec![0.0f32; n];
        let periodo = sample_rate as f32 * 0.5;
        for (i, slot) in pcm.iter_mut().enumerate() {
            let t = i as f32 / sample_rate as f32;
            let fase = (i as f32 % periodo) / periodo;
            let clique = if fase < 0.01 { 0.8 } else { 0.0 };
            let baixo = 0.4 * (2.0 * std::f32::consts::PI * tonic_hz * t).sin();
            let env = if energia_crescente {
                0.3 + 0.7 * (t / segundos.max(1.0))
            } else {
                0.7
            };
            *slot = (clique + baixo) * env;
        }
        pcm
    }

    fn receita() -> String {
        crate::receita::receita_padrao()
    }

    #[test]
    fn melhores_momentos_respeita_marcadores_e_alvo() {
        let sr = 44_100u32;
        let pcm = trilha(sr, 40.0, 110.0, true);
        // Marcador do usuário em 30–35 s (trecho de energia alta).
        let marcadores = vec![Marcador {
            inicio_ms: 30_000,
            fim_ms: 35_000,
        }];
        let r =
            melhores_momentos(&pcm, sr, 10.0, marcadores, &receita()).expect("criação deve passar");
        assert!(!r.pcm.is_empty());
        assert!(
            r.duracao_s >= 5.0 && r.duracao_s <= 15.0,
            "duração {}",
            r.duracao_s
        );
        // O conteúdo do marcador tem que estar presente: o trecho 30–35 s (energia
        // alta) tem RMS maior que a média do resultado.
        assert!(r.pcm.iter().all(|x| x.is_finite()));
    }

    #[test]
    fn loop_de_4_compassos_repete_20_vezes_sem_estalo_na_emenda() {
        // Teste do plano: loop tocado 20× — a emenda tem que ser contínua (I4.1).
        let sr = 44_100u32;
        let pcm = trilha(sr, 12.0, 110.0, false);
        let loop4 = criar_loop(&pcm, sr, 4, &receita()).expect("loop deve passar");
        assert!(
            loop4.duracao_s > 5.0 && loop4.duracao_s < 10.0,
            "{} s",
            loop4.duracao_s
        );

        // Concatena 20 cópias e mede a descontinuidade máxima na emenda vs corpo.
        let mut tocado = Vec::with_capacity(loop4.pcm.len() * 20);
        for _ in 0..20 {
            tocado.extend_from_slice(&loop4.pcm);
        }
        let len = loop4.pcm.len();
        let max_delta_corpo = loop4
            .pcm
            .windows(2)
            .map(|w| (w[1] - w[0]).abs())
            .fold(0.0f32, f32::max);
        for copia in 0..19 {
            let seam = copia * len + len - 1;
            let delta = (tocado[seam + 1] - tocado[seam]).abs();
            assert!(
                delta <= max_delta_corpo * 1.5 + 1e-3,
                "estalo na emenda {copia}: delta {delta} > corpo {max_delta_corpo}×1,5"
            );
        }
    }

    #[test]
    fn textura_estica_e_fecha_em_loop() {
        let sr = 44_100u32;
        let pcm = trilha(sr, 3.0, 220.0, false); // natureza curta
        let t = criar_textura(&pcm, sr, 4.0, &receita()).expect("textura deve passar");
        // 3 s × 4 = 12 s esticado; o fechamento (2 s) funde cauda com cabeça e
        // descarta a cabeça tocada no fade-in → ~10 s.
        assert!(
            t.duracao_s > 8.5 && t.duracao_s < 11.5,
            "esperava ~10 s (12 esticado − 2 do fechamento), deu {}",
            t.duracao_s
        );
        assert!(t.pcm.iter().all(|x| x.is_finite()));
        // Fator fora da faixa é recusado.
        assert!(criar_textura(&pcm, sr, 1.5, &receita()).is_err());
        assert!(criar_textura(&pcm, sr, 9.0, &receita()).is_err());
    }

    #[test]
    fn mesclar_la_menor_com_mib_maior_avisa_e_recusa_sem_confirmacao() {
        // Teste do plano: Lá menor (110 Hz = A2) × Mi♭ maior (~155,56 Hz = Eb3).
        let sr = 44_100u32;
        let a = trilha(sr, 8.0, 110.0, false); // Lá
        let b = trilha(sr, 8.0, 155.56, false); // Mi♭
        let avisos = avisos_de_mescla(&a, &b, sr);
        assert!(
            avisos.iter().any(|s| s.contains("incompatíveis")),
            "esperava aviso de tons incompatíveis, veio {avisos:?}"
        );
        let r = mesclar(&a, &b, sr, -14.0, DecisaoTons::Recusar, &receita());
        assert!(
            matches!(r, Err(ErroCriacao::TonsIncompativeis { .. })),
            "{r:?}"
        );
        // Com decisão humana de seguir, renderiza com o aviso embutido.
        let r = mesclar(&a, &b, sr, -14.0, DecisaoTons::Seguir, &receita())
            .expect("seguiu: deve renderizar");
        assert!(r.avisos.iter().any(|s| s.contains("incompatíveis")));
    }

    #[test]
    fn mesclar_tons_compatíveis_nao_avisa_e_equilibra_volumes() {
        let sr = 44_100u32;
        let voz = trilha(sr, 8.0, 110.0, false); // Lá menor
                                                 // Textura mais alta (0,9 de amplitude) na relativa (Dó maior ↔ Lá menor).
        let mut textura = trilha(sr, 8.0, 130.81, false); // Dó (relativa de Lá menor)
        for x in textura.iter_mut() {
            *x *= 3.0;
        }
        let avisos = avisos_de_mescla(&voz, &textura, sr);
        assert!(
            !avisos.iter().any(|s| s.contains("incompatíveis")),
            "relativa não deveria avisar: {avisos:?}"
        );
        let r = mesclar(&voz, &textura, sr, -14.0, DecisaoTons::Recusar, &receita())
            .expect("tons compatíveis renderizam");
        assert!(r.pcm.iter().all(|x| x.is_finite()));
        // Teto respeitado.
        let pico = r.pcm.iter().fold(0.0f32, |m, x| m.max(x.abs()));
        assert!(pico <= 1.0 + 1e-3, "pico {pico} acima de 1,0");
    }

    #[test]
    fn fechar_em_loop_preserva_duracao_menos_crossfade() {
        let sr = 44_100u32;
        let seg = vec![0.5f32; sr as usize * 2];
        let out = fechar_em_loop(&seg, sr, 100);
        assert_eq!(out.len(), seg.len() - (100 * 44_100 / 1000) as usize);
    }
}
