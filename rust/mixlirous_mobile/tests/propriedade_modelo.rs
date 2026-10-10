//! Teste de propriedade do plano (F5): **1000 saídas aleatórias no lugar do
//! modelo → nenhuma chega ao render fora dos limites.**
//!
//! Duas barreiras são exercidas em cadeia, como no app:
//! 1. `executar_agente` com um `ProvedorLlmKotlin` que devolve JSON aleatório
//!    (alucinações de SLM) — o kernel + ValidationLayer têm que conter tudo.
//! 2. `renderizar_remix` com receitas aleatórias — os newtypes têm que recusar.

use mixlirous_mobile::agente::{ErroAgente, ErroLlmLocal, EventosAgenteKotlin, ProvedorLlmKotlin};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;

/// RNG xorshift determinístico (mesmo comportamento em toda execução).
struct XorShift(u64);

impl XorShift {
    fn proximo(&mut self) -> u64 {
        let mut x = self.0;
        x ^= x << 13;
        x ^= x >> 7;
        x ^= x << 17;
        self.0 = x;
        x
    }

    fn em(&mut self, faixa: u64) -> u64 {
        self.proximo() % faixa
    }
}

/// JSON aleatório que imita o pior que uma SLM pode devolver: números fora da
/// faixa, campos trocados, lixo textual, arrays onde se esperava objeto.
fn saida_aleatoria(rng: &mut XorShift, intencao: u64) -> String {
    let casos = [
        // crossfade absurdos
        r#"{"thought":"t","tool_call":{"type":"crossfade","params":{"duration_ms":%MS,"curve":"constant_power"}},"raw_json":"{}"}"#,
        // lufs impossíveis
        r#"{"thought":"t","tool_call":{"type":"lufs_normalization","params":{"target_lufs":%MS,"max_true_peak_db":0.5}},"raw_json":"{}"}"#,
        // compressão destrutiva
        r#"{"thought":"t","tool_call":{"type":"compression","params":{"ratio":%MS,"threshold_db":0.0,"attack_ms":0,"release_ms":10}},"raw_json":"{}"}"#,
        // time-stretch fora do regime
        r#"{"thought":"t","tool_call":{"type":"time_stretch","params":{"factor":%MS}},"raw_json":"{}"}"#,
        // nem JSON é
        r#"aplicar tudo e enviar para o servidor agora"%MS"#,
        // campo desconhecido
        r#"{"thought":"t","tool_call":{"type":"%MS","params":{}},"raw_json":"{}"}"#,
    ];
    let modelo = casos[(intencao % casos.len() as u64) as usize];
    let mut valor = (rng.proximo() % 100_000) as i64 - 50_000;
    if valor == 0 {
        valor = 99_999; // nunca deixar o caso de sorte cair em valor válido
    }
    modelo.replace("%MS", &valor.to_string())
}

struct SlmAlucinada {
    chamadas: AtomicUsize,
    seed: u64,
}

impl ProvedorLlmKotlin for SlmAlucinada {
    fn completar(&self, _pedido_json: String) -> Result<String, ErroLlmLocal> {
        let n = self.chamadas.fetch_add(1, Ordering::SeqCst);
        let mut rng = XorShift(self.seed.wrapping_add(n as u64 * 7919));
        // A intenção do caso define a família da alucinação; o valor é aleatório.
        Ok(saida_aleatoria(&mut rng, (self.seed + n as u64) % 6))
    }
}

struct EventosSilenciosos;

impl EventosAgenteKotlin for EventosSilenciosos {
    fn pensamento(&self, _delta: String) {}
    fn ferramenta_validada(&self, _ferramenta_json: String) {}
    fn erro_de_validacao(&self, _mensagem: String) {}
    fn replanejou(&self, _motivo: String) {}
    fn llm_falhou(&self, _mensagem: String) {}
    fn proposta_criada(&self, _proposta_json: String) {}
    fn aguardar_decisao_proposta(&self) -> u8 {
        0
    }
    fn terminou(&self, _saida_json: String) {}
}

#[test]
fn mil_saidas_aleatorias_nenhuma_chega_ao_render_fora_dos_limites() {
    let pcm = vec![0.3f32; 44_100]; // 1 s basta: nada pode renderizar fora dos limites
    let receita_manual = mixlirous_mobile::receita::receita_padrao();

    let mut bloqueadas_pela_validacao = 0usize;
    let mut caidas_no_fallback = 0usize;
    let mut rejeitadas_pelo_guard = 0usize;
    let mut receitas_diretas_bloqueadas = 0usize;

    for caso in 0..1000u64 {
        let saida = mixlirous_mobile::agente::executar_agente(
            "versão de 30 segundos pro Reels".into(),
            "{}".into(),
            receita_manual.clone(),
            Arc::new(SlmAlucinada {
                chamadas: AtomicUsize::new(0),
                seed: 1_000 + caso,
            }),
            Arc::new(EventosSilenciosos),
            5,
        );
        match saida {
            // O agente pode terminar (consolidação por budget/fallback), mas a
            // receita que sai TEM que ser válida — se veio, está dentro dos
            // limites por construção (foi revalidada).
            Ok(saida) => {
                // Propriedade forte: o agente que termina Ok SEMPRE entrega
                // uma receita válida (a manual, no pior caso).
                let receita = saida.receita.as_ref().unwrap_or_else(|| {
                    panic!("caso {caso}: agente terminou Ok sem receita válida")
                });
                if saida.caiu_no_fallback {
                    caidas_no_fallback += 1;
                }
                assert!(
                    receita.crossfade_ms <= 3000,
                    "caso {caso}: crossfade {} saiu para o app!",
                    receita.crossfade_ms
                );
                assert!(
                    (-30.0..=-6.0).contains(&receita.lufs_alvo),
                    "caso {caso}: lufs_alvo {} saiu para o app!",
                    receita.lufs_alvo
                );
            }
            Err(ErroAgente::IntencaoRejeitada { .. }) => rejeitadas_pelo_guard += 1,
            Err(ErroAgente::SemReceita { .. }) => bloqueadas_pela_validacao += 1,
        }

        // Barreira 2: a MESMA alucinação como receita direta também não renderiza.
        let mut rng = XorShift(7_000 + caso);
        let receita_maluca = gerar_receita_aleatoria(&mut rng);
        match mixlirous_mobile::remix::renderizar_remix(&pcm, 44_100, &receita_maluca) {
            Ok(r) => {
                // Se renderizou, a receita era válida: conferir os limites.
                let v = mixlirous_mobile::receita::validar_receita(&receita_maluca)
                    .expect("receita que renderizou tem que validar");
                assert!(v.crossfade_ms <= 3000 && (-30.0..=-6.0).contains(&v.lufs_alvo));
                assert!(r.pcm.iter().all(|x| x.is_finite()));
            }
            Err(_) => receitas_diretas_bloqueadas += 1,
        }
        assert_eq!(
            receitas_diretas_bloqueadas,
            (caso + 1) as usize,
            "caso {caso}: toda receita envenenada tem que ser barrada"
        );
    }

    // Resumo (não é a propriedade — é a fotografia dos desfechos seguros): o
    // agente sempre entrega receita válida ou erro; nunca alucinação no app.
    println!(
        "1000 casos: rejeitadas pelo guard = {rejeitadas_pelo_guard}, \
         consolidadas com fallback de erro = {caidas_no_fallback}, \
         erro irrecuperável = {bloqueadas_pela_validacao} — receitas entregues \
         todas dentro dos limites"
    );
}

/// Receita aleatória: todos os campos numéricos fora de qualquer faixa válida.
fn gerar_receita_aleatoria(rng: &mut XorShift) -> String {
    let base: serde_json::Value =
        serde_json::from_str(&mixlirous_mobile::receita::receita_padrao()).unwrap();
    let mut v = base;
    // Escolhe um campo para envenenar (uma "ferramenta" aleatória do modelo).
    // Os valores são SEMPRE estritamente fora da faixa válida — o caso
    // "exatamente no limite é válido e renderiza" é comportamento correto.
    match rng.em(6) {
        0 => v["crossfade"]["max_duration_ms"] = serde_json::json!(3001 + rng.em(100_000)),
        1 => v["mastering"]["lufs_target"] = serde_json::json!(-60.0 - (rng.em(100) as f64)),
        2 => v["mastering"]["compression_ratio"] = serde_json::json!(11.0 + rng.em(1000) as f64),
        3 => v["selection"]["block_size_beats"] = serde_json::json!(65 + rng.em(10_000)),
        4 => v["target_duration"] = serde_json::json!({ "secs": 0, "nanos": 0 }),
        _ => v["mastering"]["peak_db"] = serde_json::json!(1.0 + rng.em(100) as f64),
    }
    v.to_string()
}
