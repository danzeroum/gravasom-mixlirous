# Patches na Mixlirous (third_party/mixlirous)

A Mixlirous vive neste repositório como `git subtree` (`--squash`), fixada no commit
`5048a107a28524fc515bd7bd26805514a37187d9`. Toda mudança feita na copia local
`third_party/mixlirous/` é um patch que precisa ser:

1. Registrado aqui com: motivo, arquivos tocados e teste que cobre.
2. Limitado ao mínimo necessário — preferir expor algo no crate `mixlirous_mobile`
   a alterar a Mixlirous.
3. Candidato a devolver ao upstream (`danzeroum/mixlirous`) via PR quando tiver
   valor geral.

## Pendências conhecidas herdadas (sem patch)

- Canvas não serializa o grafo para `pipeline_config` — irrelevante no celular
  (o canvas web não é portado; a receita é a fonte de verdade).
- `get_track_peaks` retorna vazio (C9) — o app usa a waveform do próprio
  Gravasom (decodificação no Kotlin); não afeta o motor.
- `audio_agent` depende de `reqwest` — **resolvido por patch** (abaixo); o
  provedor LLM local entra por callback UniFFI.

## Achados da F1 que viraram registro no app

- **Seleção fica abaixo do alvo em material uniforme** (`audio_core` knapsack
  com restrição de batida forte + intro/outro preservados): medido
  23,57 s para alvo de 30 s numa trilha sintética de 60 s uniforme. O
  `docs/04 §6.1` previa erro `CannotMeetTarget` quando não fecha. O teste
  `rust/mixlirous_mobile/tests/golden_master.rs` prende o piso observado e o
  F3 (tela de receita) vai expor a duração real na UI. Candidato a issue no
  upstream; correção não vai pelo app.
- **BPM tem ambiguidade de oitava** (detector de onset devolve metade ou o
  dobro do tempo real — 60 em vez de 120). Comportamento clássico de
  detectores; a etiqueta do app reporta o valor medido. O corpus do F2 mede a
  taxa de acerto com a convenção de oitava definida lá.

## Patches aplicados

### P1 — `audio_agent`: rede atrás da feature `network` (F1)

- **Motivo:** o crate `audio_agent` declara `reqwest` incondicionalmente; no
  celular a única rede permitida é o ModelManager (Kotlin) e o provedor LLM é
  local (callback UniFFI). Nenhuma dependência de rede pode entrar no .so.
- **Arquivos:** `crates/audio_agent/Cargo.toml` (`reqwest` → `optional = true`,
  feature `network` no default), `src/lib.rs` e `src/llm/mod.rs`
  (`#[cfg(feature = "network")]` nos provedores `openai`/`ollama`).
- **Compatibilidade:** `default = ["network"]` preserva o comportamento do
  workspace original (`cargo build/test` na Mixlirous intacta); o app consome
  com `default-features = false`.
- **Verificação:** `cargo build` com e sem a feature; suite do `audio_agent`
  roda com a feature (default); o `mixlirous_mobile` roda sem (não compilando
  `reqwest`).
