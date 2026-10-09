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
