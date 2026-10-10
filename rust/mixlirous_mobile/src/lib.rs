//! `mixlirous_mobile` — ponte UniFFI entre o app Gravasom Mixlirous (Kotlin)
//! e o motor da Mixlirous (Rust, `third_party/mixlirous`).
//!
//! Responsabilidades (F1 do plano):
//! 1. **Análise** — BPM, grade de batidas, tonalidade, energia e loudness
//!    (`analise::analisar`).
//! 2. **Pipeline de remix** — PCM decodificado no Kotlin entra, PCM masterizado
//!    sai; receita JSON validada na desserialização (`remix::renderizar_remix`).
//! 3. **Validação** — receitas (`receita::validar_receita`) nunca fora dos
//!    limites; alucinação da SLM vira erro, não arquivo estragado.
//! 4. **Kernel ReAct** — o agente da Mixlirous com provedor LLM local
//!    implementado em Kotlin (`agente::executar_agente`).
//!
//! Sem rede: `audio_agent` entra com `default-features = false` (sem reqwest).
//! A única permissão de internet do app é o ModelManager, em Kotlin.

uniffi::setup_scaffolding!("mixlirous_mobile");

pub mod agente;
pub mod analise;
pub mod criar;
pub mod receita;
pub mod remix;

use std::sync::Arc;

/// Motor de remix do Gravasom Mixlirous. Objeto sem estado: cada método é
/// independente e thread-safe; o app cria um e compartilha entre telas.
#[derive(uniffi::Object)]
pub struct MixEngine;

#[uniffi::export]
impl MixEngine {
    #[uniffi::constructor]
    pub fn novo() -> Arc<Self> {
        Arc::new(MixEngine)
    }

    /// Análise completa: BPM, tonalidade, energia por trecho, loudness.
    /// Chamar em WorkManager (processamento pesado, com progresso e cancelar).
    pub fn analisar(&self, pcm: Vec<f32>, sample_rate: u32) -> analise::AnaliseAudio {
        analise::analisar(&pcm, sample_rate)
    }

    /// Valida uma receita JSON sem renderizar. Retorna a receita normalizada
    /// (para salvar e "Refazer igual") ou erro legível.
    pub fn validar_receita(
        &self,
        receita_json: String,
    ) -> Result<receita::ReceitaValidada, receita::ErroReceita> {
        receita::validar_receita(&receita_json)
    }

    /// Receita padrão (fallback sem modelo e ponto de partida da tela manual).
    pub fn receita_padrao(&self) -> String {
        receita::receita_padrao()
    }

    /// Receita padrão por destino: "reels" | "shorts" | "streaming" | "voz".
    pub fn receita_para_destino(
        &self,
        destino: String,
        duracao_s: u64,
    ) -> Result<String, receita::ErroReceita> {
        receita::receita_para_destino(&destino, duracao_s)
    }

    /// Renderiza o remix: PCM mono + receita JSON → PCM masterizado.
    /// Chamar em WorkManager (alvo: 5 min de áudio em ≤ 90 s no aparelho).
    pub fn renderizar_remix(
        &self,
        pcm: Vec<f32>,
        sample_rate: u32,
        receita_json: String,
    ) -> Result<remix::RemixRenderizado, remix::ErroRemix> {
        remix::renderizar_remix(&pcm, sample_rate, &receita_json)
    }

    /// Distância de fingerprint entre dois renders (mesmo som < 0,05).
    pub fn distancia_fingerprint(
        &self,
        a: remix::FingerprintAudio,
        b: remix::FingerprintAudio,
    ) -> Option<f64> {
        remix::distancia_fingerprint(&a, &b).map(|d| d as f64)
    }

    /// Fingerprint de um PCM bruto (golden master do original).
    pub fn fingerprint_de_pcm(&self, pcm: Vec<f32>, sample_rate: u32) -> remix::FingerprintAudio {
        remix::fingerprint_de_pcm(&pcm, sample_rate)
    }

    /// Executa o agente ReAct: intenção (texto/falada) → proposta de receita
    /// validada. O provedor LLM é a SLM local em Kotlin (callback); os eventos
    /// voltam pelo `EventosAgenteKotlin` (raciocínio visível, propostas HITL).
    pub fn executar_agente(
        &self,
        intencao: String,
        contexto_json: String,
        receita_atual_json: String,
        provedor: Arc<dyn agente::ProvedorLlmKotlin>,
        eventos: Arc<dyn agente::EventosAgenteKotlin>,
        orcamento: u8,
    ) -> Result<agente::SaidaAgente, agente::ErroAgente> {
        agente::executar_agente(
            intencao,
            contexto_json,
            receita_atual_json,
            provedor,
            eventos,
            orcamento,
        )
    }

    // ---------- Criar novos sons (F6) — recomposição do material do usuário ----------

    /// Melhores momentos: maior energia até o alvo; marcadores entram como
    /// trechos obrigatórios. Não destrutivo.
    pub fn melhores_momentos(
        &self,
        pcm: Vec<f32>,
        sample_rate: u32,
        duracao_alvo_s: f64,
        marcadores: Vec<criar::Marcador>,
        receita_json: String,
    ) -> Result<criar::CriacaoResult, criar::ErroCriacao> {
        criar::melhores_momentos(
            &pcm,
            sample_rate,
            duracao_alvo_s as f32,
            marcadores,
            &receita_json,
        )
    }

    /// Loop de N compassos com fechamento sem clique (repete sem estalo).
    pub fn criar_loop(
        &self,
        pcm: Vec<f32>,
        sample_rate: u32,
        compassos: u32,
        receita_json: String,
    ) -> Result<criar::CriacaoResult, criar::ErroCriacao> {
        criar::criar_loop(&pcm, sample_rate, compassos, &receita_json)
    }

    /// Textura 2×–8× com fechamento em loop (Natureza/Água).
    pub fn criar_textura(
        &self,
        pcm: Vec<f32>,
        sample_rate: u32,
        fator: f64,
        receita_json: String,
    ) -> Result<criar::CriacaoResult, criar::ErroCriacao> {
        criar::criar_textura(&pcm, sample_rate, fator as f32, &receita_json)
    }

    /// Avisos da mescla ANTES de renderizar (tons e andamento).
    pub fn avisos_de_mescla(
        &self,
        pcm_a: Vec<f32>,
        pcm_b: Vec<f32>,
        sample_rate: u32,
    ) -> Vec<String> {
        criar::avisos_de_mescla(&pcm_a, &pcm_b, sample_rate)
    }

    /// Mescla duas gravações (andamento, volume, masterização); com tons
    /// incompatíveis e `decisao_tons = Recusar`, nada renderiza.
    pub fn mesclar(
        &self,
        pcm_a: Vec<f32>,
        pcm_b: Vec<f32>,
        sample_rate: u32,
        lufs_alvo: f64,
        decisao_tons: criar::DecisaoTons,
        receita_json: String,
    ) -> Result<criar::CriacaoResult, criar::ErroCriacao> {
        criar::mesclar(
            &pcm_a,
            &pcm_b,
            sample_rate,
            lufs_alvo as f32,
            decisao_tons,
            &receita_json,
        )
    }
}
