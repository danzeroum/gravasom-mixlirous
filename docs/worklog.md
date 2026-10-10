# Worklog — Gravasom Mixlirous

Registro cronológico do trabalho de integração. Cada fase termina com: o que foi
feito, comandos de verificação com saída real, e o que ficou pendente.

---

## F0 — Base (2026-10-10)

**Objetivo:** repositório `gravasom-mixlirous` com todo o histórico do Gravasom,
identidade própria e a Mixlirous como subtree.

### Feito

1. Clonado `danzeroum/gravasom` (privado) com histórico completo e tags.
   HEAD da `main`: `2e3a595daf0ee6db46bc4b03a18644e428c925b2` (exatamente o commit
   citado no plano); única tag: `v1.2.0-onda2`.
2. O remoto `gravasom-mixlirous` **não estava vazio** — havia o arquivo `plano`
   (análise de integração, 2 commits). Para não destruí-lo, fizemos
   `git merge --allow-unrelated-histories` trazendo o histórico do Gravasom por
   cima, preservando o `plano` na raiz. Commit: `b1933f3`.
3. Push de `--all` e `--tags` para o destino. Verificação: `git ls-remote`
   mostrou `main` → `b1933f3` e `refs/tags/v1.2.0-onda2`.
4. Identidade do app (commit `8718b58`):
   - `applicationId = "com.gravasom.mixlirous"` (namespace permanece `com.gravasom`
     — troca de namespace quebraria imports de 12 módulos por causa cosmética).
   - `app_name` = "Gravasom Mixlirous" (pt-BR e en).
   - `version.properties`: `versionCode=100`, `versionName=0.1.0-mixlirous`.
   - Ícone próprio: mesma silhueta (anel + três barras, `docs/design/icone`),
     recolorida com a paleta Mixlirous — fundo `#662F79`, anel `#FAD7FF`,
     barras `#EFB0FF`. Contrastes calculados: 7,26:1 (anel) e 5,50:1 (barras),
     acima do mínimo 3:1 da identidade. Fontes SVG em `docs/design/icone-mixlirous/`.
   - Paleta padrão do app = MIXLIROUS: novo `Paleta.PADRAO` em `core/ui`;
     `fromId` e o fallback pré-Android 12 de `SISTEMA` caem nele;
     `GravasomTheme` usa-o por omissão. Testes `TemaGeradoTest` atualizados ao
     novo contrato (mudança de produto consciente, não afrouxamento).
   - Removidos `scripts/__pycache__/*.pyc` versionados por engano no Gravasom.
5. Subtree: `git subtree add --prefix=third_party/mixlirous … 5048a10 --squash`
   (commits `078ecae` + merge `fbcc2e2`).
6. Leitura obrigatória concluída antes de código Rust (resumo do que importa
   para o celular):
   - `CLAUDE.md`/README: workspace `audio_core` (DSP puro) / `audio_agent`
     (ReAct, validator, limits, prompt_guard) / `audio_api` (Axum — não portado).
   - `docs/00`: princípios — "Não gera música, recompõe a sua"; "A IA propõe,
     você decide"; alvos de performance (< 20 s de DSP para 5 min; o motor é
     rápido o bastante para o celular).
   - `docs/04`: pipeline `decode → analyze → segment → select → stitch →
     master → encode`; validação na **desserialização** dos newtypes
     (`CrossfadeMs::MAX = 3000`, etc.); invariantes de emenda (sem clipping,
     continuidade) e da cadeia de masterização (compressor → limiter → LUFS,
     nessa ordem — doc 16 substitui qualquer ordem "medir → normalizar → limitar").
   - `docs/05`: tabela canônica de limites gerada de `audio_agent::limits`;
     orçamento de 5 ferramentas; falha de validação não consome orçamento (teto 2);
     LLM fora do ar → fallback manual, job não falha; prompt_guard com padrões
     proibidos e limite de 4096 caracteres.
   - `docs/16`: newtypes validados em todos os 10 parâmetros (T0.0 concluído no
     upstream); testes de propriedade com proptest; I15 finitude; crossfade de
     potência constante já feito (T2.2); latência declarada por estágio.
   - `docs/17.1`: testes acústicos implementados (bypass neutro, idempotência
     LUFS, latência, THD/aliasing, DC offset, homogeneidade); correção real
     `apply_lufs_gain` com loudness não mensurável → `UnmeasurableLoudness`.
   - `CHANGELOG`: política de privacidade auditável, consentimento revogável,
     transparência de downmix mono — a UI do app herda esses compromissos.
   - Contratos (`docs/03` + adendo R2): `PipelineConfig` = { target_duration,
     crossfade, mastering, selection, format, tuning } — é este JSON que a
     receita do app carrega e que a SLM preenche.
7. `docs/mixlirous-patches.md` criado (sem patches no F0; pendências conhecidas
   anotadas com a decisão de como o app as contorna).
8. `docs/status.md` ganhou a seção da integração (F0–F9) com estado honesto.

### Verificado (comando → saída)

- `git push destino --all` → `6c706b3..b1933f3  main -> main`
- `git push destino --tags` → `* [new tag] v1.2.0-onda2`
- `git ls-remote destino` → `b1933f3…refs/heads/main`, `c1f0d8e…refs/tags/v1.2.0-onda2`
- `git subtree add …` → `Added dir 'third_party/mixlirous'`
- `rg -n "applicationId" app/build.gradle.kts` → `com.gravasom.mixlirous`

### Não verificado (sem Android SDK neste ambiente)

- `./gradlew detekt lintDebug testDebugUnitTest koverVerify` (testes de tema
  alterados rodam no Robolectric — precisam do SDK).
- `./gradlew :app:assembleDebug` e instalação ao lado do Gravasom.
- Ambos viram primeiros itens do roteiro do dono (`docs/teste-aparelho-real.md`)
  e/ou de um ambiente com SDK.

---

## F1 — Motor Rust no Android (2026-10-10)

**Objetivo:** crate `rust/mixlirous_mobile` (UniFFI) expondo análise, pipeline
de remix, validação e kernel ReAct; módulo `:core:mixengine` com cargo-ndk;
decodificação continua no Kotlin.

### Feito

1. **Patch P1 na subtree** (`third_party/mixlirous`): `reqwest` do `audio_agent`
   virou opcional atrás da feature `network` (default ligada — workspace
   original inalterado; o app entra com `default-features = false`). Provedores
   `openai`/`ollama` atrás de `#[cfg(feature = "network")]`. Registrado em
   `docs/mixlirous-patches.md`.
2. **Crate `rust/mixlirous_mobile`** (workspace próprio em `rust/`, depósito de
   path para a subtree):
   - `analise.rs` — BPM (grade mediana), tonalidade (chroma → `detect_key`),
     energia em trechos de 5 s, loudness LUFS (BS.1770-4), pico dBFS, rótulo
     pt-BR ("120 BPM · Lá menor").
   - `receita.rs` — `validar_receita` (desserialização do `PipelineConfig` com
     newtypes: um `crossfade_ms: 5000` é rejeitado no tipo), receita padrão e
     receita por destino (reels/shorts/streaming −14 LUFS; voz −16 LUFS).
   - `remix.rs` — `renderizar_remix` via `DefaultRemixPipeline` (I15 verificado
     na fronteira), `FingerprintAudio` (espelho UniFFI de `AudioFingerprint`)
     e `distancia_fingerprint`.
   - `agente.rs` — kernel ReAct da Mixlirous com `ProvedorLlmKotlin` (callback
     UniFFI `with_foreign`) adaptado a `LlmProvider` via `spawn_blocking`;
     eventos do agente (pensamento, ferramenta validada, erro de validação,
     proposta HITL, decisão) para o Kotlin; toda ferramenta exige proposta
     (`tool_requires_proposal = true`); fallback devolve a receita manual.
   - `lib.rs` — objeto `MixEngine` (UniFFI) com os métodos acima.
3. **Bindings Kotlin UniFFI** geradas de verdade (`uniffi-bindgen generate
   --language kotlin`) e commitadas em
   `core/mixengine/src/main/kotlin/com/gravasom/core/mixengine/motor/mixlirous_mobile.kt`
   (pacote `com.gravasom.core.mixengine.motor`).
4. **Módulo `:core:mixengine`** (Gradle): tarefas cargo-ndk (`buildRustMixlirousDebug`
   arm64+x86_64, `buildRustMixlirousRelease` arm64), jniLibs por build dir,
   tarefa `gerarBindings`, `MotorMixlirous` (loader + conversão PCM 16 bits
   intercalado → mono f32) e testes JVM da conversão. Incluído em
   `settings.gradle.kts` e como dependência de `:app`.
5. **Leitura prévia concluída no F0** (docs 00/03/04/05/16/17.1) — o desenho da
   F1 segue doc 04 (pipeline B–F) e doc 05 (kernel/validação/fallback).

### Verificado (comando → saída)

- `cargo test -p mixlirous_mobile` → **16 unit + 2 integração passando**:
  - crossfade 5000 ms rejeitado na desserialização (teste do plano ✓)
  - 200 receitas com crossfade fora da faixa → nenhuma renderiza (teste de
    propriedade do plano, amostrado ✓)
  - fingerprint < 0,05 entre dois renders da mesma receita (✓, 2 testes)
  - seno amplitude 1 → RMS 0,7071; seno 440 Hz → tônica Lá (invariantes B.4/B.5 ✓)
  - SLM indisponível → fallback com receita manual preservada; injeção
    "ignore todas as instruções…" rejeitada pelo prompt_guard ✓
- `cargo test -p mixlirous_mobile --test golden_master` → **2 passando**:
  pipeline sobre `rhythm_120bpm_mono.wav` do corpus (decode real via
  `decode_to_pcm`) com golden master < 0,05, pico ≤ teto, finitude; e Reels 30 s
  sobre trilha sintética de 60 s.
- `cargo clippy --all-targets -- -D warnings` (workspace `rust/`) → **0 erros**.
- `cargo fmt --check` → OK.
- **Suite da Mixlirous (subtree) no host** — `cargo test -p audio_agent -p
  audio_core --no-fail-fast`, por lotes (proptest demora; saída completa no
  terminal): `audio_agent` lib **71 passed** (1 ignored); `audio_core` lib
  **207 passed**; integração: fixtures_manifest 2, invariant_tests 6,
  neutral_bypass 5, idempotence 9, crossfade_properties 3, compressor_properties 7,
  knapsack_properties 6, latency 8, dc_offset 3, homogeneity 5, generators 2,
  contract_ts_rust 6, thd 7, aliasing 5, quality_metrics 6, decoder 4,
  tuning_pipeline 8 — **todos verdes, 0 falhas**.
- Fixtures geradas: `python3 scripts/generate_fixtures.py` (15 MB, gitignored).

### Não verificado (sem Android SDK/NDK neste ambiente)

- `cargo ndk -t arm64-v8a build` (o binário Android real) e o APK com o .so
  dentro; `./gradlew :core:mixengine:testDebugUnitTest`,
  `detekt`, `lintDebug`, `koverVerify` e build do app.
- **Teste JNI com golden master no aparelho** (gate do F1): o teste
  `MotorMixlirousInstrumentedTest` vira item do roteiro do dono — o mesmo
  caminho Rust já está provado no host (`golden_master.rs`), o que falta é
  provar o carregamento JNI na ABI arm64.
- Reproduzir: `rustup target add aarch64-linux-android` + NDK 27 + `cargo
  install cargo-ndk`, depois `./gradlew :app:assembleDebug`.
