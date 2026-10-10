//! Harness do corpus de avaliação da SLM (F4, docs/ia/avaliacao/).
//!
//! Verifica AQUI (host) o que é verificável sem aparelho:
//! 1. Contagem: 60 casos de remix (36 dev + 24 teste) + 20 de texto
//!    (12 dev + 8 teste) — piso do plano.
//! 2. Toda receita esperada dos casos de remix **valida** pela mesma
//!    ValidationLayer que a SLM enfrenta (`validar_receita`), usando a
//!    tradução canônica chaves → PipelineConfig (a mesma que o prompt
//!    ensina ao modelo).
//! 3. Casos armadilha de texto estão declarados e cobrem o aceite
//!    (resumo sem data quando a transcrição não tem data; injeção na
//!    transcrição não é obedecida).

use std::path::PathBuf;

fn corpus(caminho: &str) -> serde_json::Value {
    let p = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../docs/ia/avaliacao")
        .join(caminho);
    let texto =
        std::fs::read_to_string(&p).unwrap_or_else(|e| panic!("corpus ausente em {:?}: {e}", p));
    serde_json::from_str(&texto).expect("corpus é JSON válido")
}

/// Tradução canônica chaves → PipelineConfig (mesmas regras do README do
/// corpus e do prompt do agente). Retorna o JSON da receita esperada.
fn receita_de_chaves(chaves: &serde_json::Value) -> String {
    let mut config: serde_json::Value =
        serde_json::from_str(&mixlirous_mobile::receita::receita_padrao()).unwrap();

    if let Some(duracao) = chaves.get("duracao_s").and_then(|v| v.as_u64()) {
        config["target_duration"] = serde_json::json!({ "secs": duracao, "nanos": 0 });
    }
    let destino = chaves
        .get("destino")
        .and_then(|v| v.as_str())
        .unwrap_or("streaming");
    let lufs = match destino {
        "reels" | "shorts" => -14.0,
        "voz" => -16.0,
        _ => -14.0,
    };
    config["mastering"]["lufs_target"] = serde_json::json!(lufs);
    if let Some(ratio) = chaves.get("compressao_ratio").and_then(|v| v.as_f64()) {
        config["mastering"]["compression_ratio"] = serde_json::json!(ratio);
    }
    if let Some(ms) = chaves.get("crossfade_ms").and_then(|v| v.as_u64()) {
        config["crossfade"]["max_duration_ms"] = serde_json::json!(ms);
        config["crossfade"]["enabled"] = serde_json::json!(ms > 0);
    }
    if let Some(blocos) = chaves.get("blocos_por_batida").and_then(|v| v.as_u64()) {
        config["selection"]["block_size_beats"] = serde_json::json!(blocos);
    }
    config.to_string()
}

#[test]
fn corpus_tem_os_80_casos_do_plano() {
    let remix_dev = corpus("remix-dev.json")["casos"].as_array().unwrap().len();
    let remix_teste = corpus("remix-teste.json")["casos"]
        .as_array()
        .unwrap()
        .len();
    let texto_dev = corpus("texto-dev.json")["casos"].as_array().unwrap().len();
    let texto_teste = corpus("texto-teste.json")["casos"]
        .as_array()
        .unwrap()
        .len();

    // Piso do plano: ≥ 60 remix com receita esperada e ≥ 20 de texto, com
    // dev e teste separados e NENHUM conjunto vazio.
    assert!(
        remix_dev >= 36 && remix_teste >= 24,
        "remix: {remix_dev}+{remix_teste}"
    );
    assert!(
        texto_dev >= 12 && texto_teste >= 8,
        "texto: {texto_dev}+{texto_teste}"
    );
    assert_eq!(remix_dev + remix_teste, 60);
    assert_eq!(texto_dev + texto_teste, 20);
}

#[test]
fn toda_receita_esperada_do_corpus_passa_na_validacao() {
    for arquivo in ["remix-dev.json", "remix-teste.json"] {
        let doc = corpus(arquivo);
        let casos = doc["casos"].as_array().unwrap();
        for caso in casos {
            let chaves = &caso["chaves"];
            if chaves.get("criacao").is_some() {
                // Casos de criação (loop/textura/mescla) não viram PipelineConfig:
                // a intenção roteia para as funções de F6; só conferimos limites
                // dos parâmetros declarados.
                let criacao = chaves["criacao"].as_str().unwrap_or("");
                match criacao {
                    "loop" => {
                        let c = chaves["compassos"].as_u64().expect("loop tem compassos");
                        assert!((1..=64).contains(&c), "{}: compassos {c}", caso["id"]);
                    }
                    "textura" => {
                        let f = chaves["fator"].as_f64().expect("textura tem fator");
                        assert!((2.0..=8.0).contains(&f), "{}: fator {f}", caso["id"]);
                    }
                    "mescla" => {}
                    outro => panic!("{}: criação desconhecida: {outro}", caso["id"]),
                }
                continue;
            }
            let receita = receita_de_chaves(chaves);
            let v = mixlirous_mobile::receita::validar_receita(&receita)
                .unwrap_or_else(|e| panic!("{}: receita esperada inválida: {e}", caso["id"]));

            // As chaves declaradas têm que sobreviver à validação.
            if let Some(d) = chaves.get("duracao_s").and_then(|v| v.as_u64()) {
                assert_eq!(v.duracao_alvo_s, d as f64, "{}", caso["id"]);
            }
            if let Some(l) = chaves.get("destino").and_then(|v| v.as_str()) {
                let esperado = match l {
                    "voz" => -16.0,
                    _ => -14.0,
                };
                assert_eq!(v.lufs_alvo, esperado, "{} (destino {l})", caso["id"]);
            }
            if let Some(ms) = chaves.get("crossfade_ms").and_then(|v| v.as_u64()) {
                assert_eq!(v.crossfade_ms, ms as u32, "{}", caso["id"]);
            }
            if let Some(r) = chaves.get("compressao_ratio").and_then(|v| v.as_f64()) {
                assert_eq!(v.compressao_ratio, r, "{}", caso["id"]);
            }
            if let Some(b) = chaves.get("blocos_por_batida").and_then(|v| v.as_u64()) {
                assert_eq!(v.blocos_por_batida, b as u32, "{}", caso["id"]);
            }
        }
    }
}

#[test]
fn casos_armadilha_de_texto_cobrem_o_aceite() {
    for arquivo in ["texto-dev.json", "texto-teste.json"] {
        let doc = corpus(arquivo);
        let casos = doc["casos"].as_array().unwrap();
        for caso in casos {
            let esperado = &caso["esperado"];
            let sem_data = esperado["sem_data_se_ausente"].as_bool().unwrap_or(false);
            let injecao = esperado["nao_obedece_transcricao"]
                .as_bool()
                .unwrap_or(false);
            let transcricao = caso["transcricao"].as_str().unwrap_or("");

            // Armadilha "sem data": a transcrição não pode conter dígitos de
            // data nem referências de calendário (meses e dias da semana por
            // extenso). Casamento por TOKEN, não substring — "maior" não é "maio".
            if sem_data {
                let t = transcricao.to_lowercase();
                let tokens: Vec<&str> = t
                    .split(|c: char| !c.is_alphanumeric())
                    .filter(|s| !s.is_empty())
                    .collect();
                // Dias úteis nuos ("sexta", "terça") são INTERVALOS musicais
                // neste app (sexta maior, terça menor) — só as formas
                // inequívocas contam como calendário.
                let calendario = [
                    "janeiro",
                    "fevereiro",
                    "março",
                    "abril",
                    "maio",
                    "junho",
                    "julho",
                    "agosto",
                    "setembro",
                    "outubro",
                    "novembro",
                    "dezembro",
                    "segunda-feira",
                    "terça-feira",
                    "quarta-feira",
                    "quinta-feira",
                    "sexta-feira",
                    "sábado",
                    "domingo",
                    "hoje",
                    "ontem",
                    "amanhã",
                ];
                let tem_data = tokens.iter().any(|tk| calendario.contains(tk))
                    || t.chars().any(|c| c.is_ascii_digit());
                assert!(
                    !tem_data,
                    "{}: declarado sem data mas a transcrição tem",
                    caso["id"]
                );
            }
            // Caso de injeção: marcado explicitamente.
            let _ = injecao; // conferido na leitura do esquema; a métrica é do spike no aparelho
        }
    }
    // Os dois casos de injeção existem (um por conjunto).
    for arquivo in ["texto-dev.json", "texto-teste.json"] {
        let doc = corpus(arquivo);
        let casos = doc["casos"].as_array().unwrap();
        assert!(
            casos
                .iter()
                .any(|c| c["esperado"]["nao_obedece_transcricao"] == serde_json::json!(true)),
            "{arquivo}: falta caso de injeção de prompt"
        );
    }
}
