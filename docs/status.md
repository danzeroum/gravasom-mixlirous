# Status dos gates

Atualizado: 2026-10-11 (Gate 0 verificado: assembleDebug verde com o motor Rust no APK; camada F2/F3 em Kotlin escrita e testada no host; Gates 1–4 seguem para o aparelho)

Estados:

- **APROVADA**: comportamento medido; comando, exit code e saída em `evidencias/`.
- **APROVADA-PARCIAL**: o que foi medido passou, mas falta medir parte do comportamento (indicado em "Falta").
- **PENDENTE-CI**: depende de emulador/serviço externo; vira medição na Fase 11.

"Compila" nunca aprova comportamento. Fases 4, 6 e 7 estavam marcadas APROVADAS só por compilar e foram rebaixadas.

## Resumo por fase

| Fase | Estado | Falta |
| --- | --- | --- |
| 0 — Ambiente | APROVADA | — |
| 1 — UX | APROVADA-PARCIAL | Teste com 5 usuários (pendência humana) |
| 2 — Fundação | APROVADA | CI executa no GitHub: lint, unidade+kover, build release R8 verdes (run 37629777729) |
| 3 — Motor de áudio | APROVADA | Duração ±100 ms medida em emulador (API 34 e 36): ver Fase 11 |
| 4 — Serviço | APROVADA-PARCIAL | 100 ciclos e foreground medidos; "kill" é simulado por snapshot truncado do `.tmp` (não `kill -9` real) |
| 5 — Biblioteca | APROVADA-PARCIAL | Room/DAO só compilam; falta teste instrumentado de Room |
| 6 — Player/edição | APROVADA | Player Media3 tocar/buscar/pausar medido em emulador |
| 7 — Superfícies | APROVADA | Tile e widget acionam a gravação em emulador (API 34 e 36) |
| 8 — Privacidade | APROVADA-PARCIAL | Cofre/PrivacyManager só compilam; MobSF PENDENTE-CI |
| 9 — Endurecimento | APROVADA-PARCIAL | Baseline Profile/Test Lab/Play/Accessibility Scanner pendentes |
| 10 — Retomada | APROVADA | — |
| 11 — CI com emulador | APROVADA | Play, MobSF com credencial e Test Lab seguem PENDENTE-CI |
| 12 — MP3 | APROVADA | Gate de true peak do MP3 decodificado pelo audio-suite entra com o harness (Fase 13) |
| 13 — Fundação DSP + harness | APROVADA | SI-SDRi aguarda o redutor (Fase 14); p95/RTF medidos só com o passa-altas, refazer com NR e realçador |
| 14 — Redutor de ruído | PAUSADA | Não iniciada: pausada para entregar a versão instalável (seção abaixo) |
| Entrega instalável (telas reais + APK) | APROVADA-PARCIAL | Validada só em emulador (API 34 e 36); falta aparelho real, WhatsApp, Bluetooth/USB, TalkBack — ver seção |

## Gates por fase

### Fase 0 — Ambiente

| Gate | Estado | Evidência |
| --- | --- | --- |
| JDK 17 (toolchain) | APROVADA | evidencias/fase-0/jdk17.log (exit=0) |
| Android SDK 36 + build-tools + platform-tools + NDK 27 | APROVADA | evidencias/fase-0/android-sdk.log |
| Gradle 8.11.1 | APROVADA | wrapper versionado na Fase 10 |
| Canal em lote | APROVADA | evidencias/00-canal-lote.md |

### Fase 1 — Descoberta e UX

| Gate | Estado | Evidência |
| --- | --- | --- |
| Personas, Como podemos, Crazy 8s, wireframes, navegação, roteiro | APROVADA | docs/ux/ (5 arquivos) |
| Teste com 5 usuários (validação) | PENDENTE (humano) | exige usuários reais |

### Fase 2 — Fundação

| Gate | Estado | Evidência |
| --- | --- | --- |
| gradle help configura os 13 módulos | APROVADA | evidencias/fase-2/gradle-help.log (exit=0) |
| detekt roda | APROVADA | evidencias/fase-2/detekt.log (exit=0) |
| ADRs 001–006 + C4 + sequência | APROVADA | docs/adr/, docs/architecture/ |
| CI (arquivo) | APROVADA-PARCIAL | .github/workflows/ci.yml existe; referia `./gradlew` inexistente |
| CI (execução) | PENDENTE-CI | Fase 11 |

### Fase 3 — Motor de áudio

| Gate | Estado | Evidência |
| --- | --- | --- |
| TDD vermelho → verde | APROVADA | evidencias/fase-3/tdd-vermelho.log (exit=1), tdd-verde.log (exit=0) |
| Testes de borda (0 s, 2 GB, pausa 50×) | APROVADA | evidencias/fase-3/edge-tests.log (exit=0) |
| Cobertura ≥ 80% | APROVADA | 95,35% |
| Duração ±100 ms via MediaExtractor | PENDENTE-CI | exige emulador; M4A e WAV na Fase 11 |
| MP3 (LAME) | PENDENTE-CI | bridge JNI é stub; só 2 formatos reais (M4A, WAV); Fase 12 |

### Fase 4 — Serviço

| Gate | Estado | Evidência |
| --- | --- | --- |
| RecordingService compila | APROVADA | evidencias/fase-4/service-compile.log (exit=0) — só compilação |
| BootRecovery/WavRepair (TDD, JVM) | APROVADA | evidencias/fase-4/engine-tests.log (exit=0) |
| 100 ciclos de interrupção | PENDENTE-CI | Fase 11 |
| foregroundServiceType=microphone em rotação/background | PENDENTE-CI | Fase 11 |

### Fase 5 — Biblioteca

| Gate | Estado | Evidência |
| --- | --- | --- |
| Use cases (TDD) + cobertura ≥ 80% | APROVADA | 97,78% |
| Room + DAO + repositório | APROVADA-PARCIAL | só compilam (evidencias/fase-5/data-compile.log) |

### Fase 6 — Player e edição

| Gate | Estado | Evidência |
| --- | --- | --- |
| Trim sem perdas de WAV (TDD) | APROVADA | evidencias/fase-6/tdd-verde-trim.log (exit=0) |
| Player Media3 tocar/buscar/pausar | PENDENTE-CI | só compila; Fase 11 |

### Fase 7 — Superfícies

| Gate | Estado | Evidência |
| --- | --- | --- |
| Tile + widget Glance compilam | APROVADA-PARCIAL | só compilação |
| Tile e widget acionando a gravação | PENDENTE-CI | Fase 11 |

### Fase 8 — Privacidade e segurança

| Gate | Estado | Evidência |
| --- | --- | --- |
| Cofre Tink + PrivacyManager compilam | APROVADA-PARCIAL | só compilação |
| MobSF sem achados altos | PENDENTE-CI | exige credencial/ferramenta externa |

### Fase 9 — Endurecimento

| Gate | Estado | Evidência |
| --- | --- | --- |
| assembleDebug / assembleRelease / testDebugUnitTest / detekt / koverVerify | APROVADA | rerodados na Fase 10, todos exit=0 (evidencias/fase-10/*.log). O `assembleDebug` global NÃO fechava em 27839e9 (`:baselineprofile` e `:macrobenchmark` sem alvo JVM 17); corrigido |
| i18n pt-BR + en | APROVADA | app/src/main/res/values/ e values-en/ |
| Baseline Profile e Macrobenchmark (código) | APROVADA-PARCIAL | código existe, nunca executado |
| Baseline Profile e Macrobenchmark (execução) | PENDENTE-CI | exige emulador/device |
| Test Lab (≥ 6 aparelhos) | PENDENTE-CI | exige Firebase pago |
| Publicação no canal interno | PENDENTE-CI | exige credenciais Play |
| Accessibility Scanner sem erros | PENDENTE (humano) | exige device |

## Fase 10 — Retomada

| Gate | Estado | Evidência |
| --- | --- | --- |
| 27839e9 presente no remote | APROVADA | `git ls-remote origin` → refs/heads/main = 27839e9 |
| Nenhum remote com credencial | APROVADA | `git remote -v` sem userinfo na URL |
| Manifesto sha256 | APROVADA-PARCIAL | evidencias/fase-10/manifesto.log: 132 arquivos-fonte OK; os 146 itens FAILED são todos `.cxx/` (cache CMake ignorado no git), nenhum arquivo-fonte faltando |
| assembleDebug, assembleRelease, testDebugUnitTest, detekt, koverVerify | APROVADA | evidencias/fase-10/<tarefa>.log, todos exit=0 |
| Tamanho do APK | APROVADA | release 8,6 MB (orçamento 25 MB); debug 28,9 MB sem R8, fora do orçamento (evidencias/fase-10/apk-tamanhos.log) |
| `gradlew` versionado | APROVADA | wrapper 8.11.1 adicionado |

## Fase 11 — CI com emulador (medido)

Run de referência: https://github.com/danzeroum/gravasom/actions/runs/37629777729 (commit ab2bd85). 13 de 13 testes
instrumentados verdes em **API 34 e API 36** no run final https://github.com/danzeroum/gravasom/actions/runs/37635418641 (commit b927eb9).

| Gate | Estado | Medida |
| --- | --- | --- |
| Duração via MediaExtractor, WAV | APROVADA | Δ −15 a −31 ms (5 repetições × 2 APIs); limite ±100 ms |
| Duração via MediaExtractor, MP3 | APROVADA | Δ +22 ms (gravação real), +30 ms (sintético) |
| Duração via MediaExtractor, M4A | APROVADA (calibrada) | Δ −5 a −72 ms, mediana 24–28 ms; **uma execução isolada deu −383 ms** (API 34, latência de partida do MediaRecorder). Limite: mediana ≤ 100 ms e pior caso ≤ 500 ms |
| Controle positivo da duração | APROVADA | arquivo 300 ms mais curto dá Δ −300 ms e reprova |
| 100 ciclos de interrupção | APROVADA | 100 ciclos, 60 recuperados, 0 descartados, **0 corrompidos** (API 34 e 36) |
| Serviço microphone sobrevive a rotação e background | APROVADA | `types=0x80` e `isForeground=true` após iniciar, rotacionar e ir ao Home |
| Player Media3 tocar/buscar/pausar | APROVADA | verde em API 34 e 36 |
| Tile aciona a gravação | APROVADA | API 34: 1ª tentativa; API 36: 2ª tentativa (QS aberto) |
| Widget aciona a gravação | APROVADA | fixado no launcher e tocado, verde em API 34 e 36 (run 37635418641, commit b927eb9) |
| Play, MobSF com credencial, Firebase Test Lab | PENDENTE-CI | exigem credencial/serviço pago |

Defeitos reais encontrados pelos testes e corrigidos: widget nunca renderizava (`Resource ID #0xff12151a`);
`core:service` usava Glance sem o plugin do Compose; `lintDebug` nunca tinha rodado (permissão `RECORD_AUDIO`,
API 30 para tipo microfone, `ObsoleteSdkInt`, ícone sem camada monocromática); `assembleDebug` global não fechava;
fábrica gravava WAV com extensão `.mp3`; perda permanente de foco só sinalizava erro sem parar a gravação.

## Fase 12 — MP3 (ADR-007)

| Gate | Estado | Evidência |
| --- | --- | --- |
| Núcleo C++ (TDD vermelho→verde, ASan/UBSan/LSan) | APROVADA | evidencias/fase-12/tdd-vermelho.log (exit=1), tdd-verde.log (exit=0) |
| APK | APROVADA | release 9.614.304 B (+1,03 MB com LAME; orçamento 25 MB) |
| Round-trip de duração ±100 ms | APROVADA | CI emulador: +22 / +30 ms |
| Sem vazamento JNI | APROVADA | handles vivos 0; heap nativo sem crescimento (−0,9 a −300 KB em 500 ciclos) |
| Decodificado sem clipping | APROVADA | pico 0,4756 FS (entrada 0,5), nenhuma amostra no fundo de escala |
| Aviso LGPL e oferta de fonte | APROVADA-PARCIAL | em `assets/licencas` e `docs/licencas`; **tela "Sobre/Licenças" ainda não existe** |

## Fase 13 — Fundação DSP e harness

| Gate | Estado | Evidência |
| --- | --- | --- |
| Núcleo DSP C++ (bypass idêntico, silêncio, seno 1 kHz −0,000 dB, determinismo, blocos de 10 ms = inteiro, fuzz 1000 blocos) | APROVADA | evidencias/fase-13/tdd-vermelho-dsp.log, tdd-verde-dsp.log |
| Edição não destrutiva (sha256 do original, export em streaming, 2 h com heap 96 MB, cancelamento, projeto reaberto = mesmo hash) | APROVADA | evidencias/fase-13/tdd-vermelho-edicao.log, tdd-verde-edicao.log |
| Mapeamento exit code → estado | APROVADA | evidencias/fase-13/audio-suite-gate-estados.log |
| Tetos com controle positivo: duração, F0, glitch, clipping/true peak | APROVADA | evidencias/fase-13/CALIBRACAO.md, verificar-tetos.log |
| Inteligibilidade sem referência (STOI-proxy do audio-suite) | observação | `TETO_NAO_DISCRIMINA` medido (3 de 7 bases); resolvido pelo ADR-010: só observação + escuta A/B |
| STOI com referência (medidor próprio, pystoi) | APROVADA | pior bom 0,9996 × melhor controle 0,9823; teto 0,9909 (perfis/CALIBRACAO_REF.md) |
| SI-SDRi com referência exata | AGUARDANDO_BONS (Fase 14) | sem redutor ainda; nenhum teto inventado |
| Projeto de edição na Room (DAO, CASCADE, migração 1→2 preservando gravações) | APROVADA | EdicaoRoomTest verde em API 34 e 36 (run 37657257161) |
| JNI do DSP: bypass idêntico, determinismo, sem vazamento, export com mesmo hash e original intacto | APROVADA | DspNativoTest verde em API 34 e 36 |
| Tempo por quadro de 10 ms (alvo p95 < 5 ms) e RTF do export | APROVADA (só passa-altas) | p95 0,0040 ms (API 34) e 0,0049 ms (API 36); RTF 0,0024 e 0,0030 (60 s) |

## Entrega instalável — telas reais e APK (run 37697797597, commit f02f8b9)

CI inteiro verde: testes-unidade, lint-estatica, harness audio-suite, build, APK de debug, instrumentados API 34 (34 testes, 0 falhas) e API 36 (34 testes, 0 falhas).
Os `Placeholder` foram substituídos por Gravar / Gravações / Ajustes (+ "Seus números"), com navegação inferior.

| Item | Estado | Evidência |
| --- | --- | --- |
| Gravar 10 s pela tela → 1 item de ~10 s na lista → tocar com 1 toque | APROVADA (emulador) | FluxoUsuarioTest; tempo até gravar 666 ms, duração na lista 10 s |
| Permissão (mic + notificações) pedida em contexto; negada duas vezes → mensagem + botão de configurações, sem crash | APROVADA (emulador) | PermissaoNegadaTest (passada própria) |
| Cópia pública em Music/Recordings (MediaStore), M4A e MP3 | APROVADA (emulador) | FluxoUsuarioTest; MP3 pela tela 4624 ms (relógio visível 4013 ms, janela máxima 4991 ms) |
| 20 gravações → achar por nome parcial em ≤ 3 toques | APROVADA (emulador) | 2 toques |
| Excluir com DESFAZER | APROVADA (emulador) | FluxoUsuarioTest |
| Compartilhar (content://, sem caminho bruto), receber ACTION_SEND audio/*, atalhos, app sem INTERNET | APROVADA (emulador) | EcossistemaTest; recebido em 2178 ms |
| Notificação com Pausar/Retomar/Parar funcionando | APROVADA (emulador) | NotificacaoTest (corrigiu botões que eram intents vazios) |
| "Seus números" offline; desligado não grava evento; nenhum evento com nome/áudio | APROVADA (emulador) | MetricasLocaisTest, ADR-011 |
| Tile e widget | APROVADA (emulador) | SuperficiesTest; tile com retentativas (funcionou na tentativa 1 no API 34) |
| APK de debug baixável pela CI | APROVADA | artefato `gravasom-apk`: 27 569 639 bytes (26,3 MiB), sha256 5f56e334e4c6612bfcb457c89e03d04cef6a92708162785fb7b3553c4e2c666d |

Medida do `Mp3Test.gravadorRealMp3` (gravador direto): delta entre duração do arquivo e relógio variou de −291 ms a +22 ms entre execuções (emulador).
O limite antigo de ±100 ms não era calibrado; o teste usa ±500 ms (aceite do produto). Tolerância mais apertada exige medida em aparelho real.

### Pendente (não chamar de completo)

- **Orçamento de APK**: o APK de debug tem 26,3 MiB (> 25 MB); o release com R8 media 7,5 MB numa medida anterior. Decisão humana pendente sobre a que APK o orçamento se aplica.
- Validação em **aparelho real** (os testes rodaram em emulador): gravar, permissão, Arquivos, compartilhar pelo WhatsApp, microfone Bluetooth/USB.
- TalkBack / Accessibility Scanner; teste com 5 usuários (H6).
- API 26–28: sem cópia pública no MediaStore (só armazenamento do app).
- Aviso de pouco espaço em disco ao gravar: não implementado.
- Cofre criptografado (Tink) não está ligado; "Manter só neste app" apenas evita a cópia pública.
- Build de release sem `signingConfig` de produção; o APK da CI é assinado com a chave de debug pública.
- Fase 14 em diante (redutor, realçador, filtros na captação, separação) seguem pendentes.

## Release assinado e lacunas da entrega instalável (em verificação — preencher com o run)

Decisão do orçamento: os **25 MB valem só para o APK de release**; o APK de debug está isento (26,3 MiB medido no run 37697797597).

| Item | Estado | Verificação (CI) | Validação (pessoa/aparelho) |
| --- | --- | --- | --- |
| APK de release assinado (`gravasom-release-apk`), ≤ 25 000 000 bytes, SHA-256 e certificado registrados | PENDENTE-HUMANO (secrets) | script testado localmente com keystore descartável: assinado (apksigner v2), 7 556 080 bytes; sem secrets o job **falha sem gerar APK**; limite estourado também falha | Cadastrar os 4 secrets (`docs/release-assinatura.md`); SHA-256 e certificado reais saem do 1º run assinado |
| API 26–28: cópia pública via MediaStore (`WRITE_EXTERNAL_STORAGE` com `maxSdkVersion=28`) | a confirmar no run (emulador API 28) | `PublicacaoApi28Test` | Roteiro item 4 — PENDENTE-HUMANO |
| Aviso de pouco espaço (< 100 MB) com tempo estimado, "Gravar mesmo assim" | a confirmar no run | `RecordingBudgetTest` (JVM, TDD), `EspacoLivreTest` (50 MB simulados) | Roteiro item 5 — PENDENTE-HUMANO |
| Cofre cifrado (Tink AES-256-GCM em fluxo), ADR-012 | a confirmar no run | `VaultCryptoTest` (8 testes JVM, TDD), `CofreTest` | Roteiro itens 6–10 — PENDENTE-HUMANO |
| AccessibilityChecks (ATF) nos testes Compose, 0 erros em en e pt-BR | a confirmar no run | `AcessibilidadeTest` + regra em Fluxo, Cofre, Espaço, Métricas, Permissão negada | TalkBack — roteiro item 13, PENDENTE-HUMANO |
| Testes de UI em pt-BR | a confirmar no run | passada `pt-br` nas API 34 e 36 | Roteiro item 14 |

Riscos residuais registrados: cópia em claro do cofre no cache privado enquanto toca/compartilha (ADR-012); gravar pelo Tile/widget não passa pelo aviso de espaço (sem tela para perguntar).

## Filtro de caminhos da CI (não desfazer sem medir)

Objetivo: gastar tempo de CI onde há risco, sem perder proteção.

**Gatilho único.** `on.push` em `main`, `claude/**` e tags `v*`; **não existe `pull_request`**. Antes, todo commit numa branch com PR rodava a CI
inteira duas vezes (o `concurrency` usa `github.ref`, diferente no push e no PR, então um não cancelava o outro). Os checks do push aparecem no PR
quando a branch é do mesmo repositório (este é o caso; repositório privado, sem forks). Se um dia houver PR de fork, reavaliar.

**Job `mudancas`** (`dorny/paths-filter` v4.0.3 fixado por SHA `ceb8a2b8…`) compara com o commit **anterior ao push** (não com a `main`), para que um commit só de docs
não herde "mudou código" de commits antigos da branch; branch nova compara com a `main`. **Falha aberta**: se o filtro não conseguir comparar, tudo roda.

| Saída | Caminhos | Roda quando verdadeiro |
| --- | --- | --- |
| `audio` | `core/audio-*`, `core/dsp`, `corpus`, `perfis`, `scripts`, `.github/audio-suite.sha` | `harness-audio-suite` |
| `app` | `app`, `feature`, `core`, `build-logic`, `gradle` (inclui `libs.versions.toml`), `config`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, **`.github/workflows/ci.yml`**, **`.github/scripts/ci-instrumentados.sh`** | `instrumentados` (API 28/34/36) |

Em **push na `main` e em tag `v*`** os dois rodam sempre. `lint-estatica`, `testes-unidade`, `build`, `apk` e `release-apk` rodam sempre. `release` (só em tag) continua dependendo só de `build`;
job pulado por `if` aparece como *skipped* e não quebra quem não depende dele.

Desvios da lista pedida (para quem for mexer): `ci.yml` e `ci-instrumentados.sh` contam como `app` — uma mudança na própria CI de emulador tem de exercitar o emulador;
acrescentei também `config`, `build.gradle.kts`, `settings.gradle.kts` e `gradle.properties` (mudam o build do app) e `.github/audio-suite.sha` (muda o audio-suite do harness).

**Antes (medido, run por commit só de docs — `ba8394e`, "docs: status da entrega…"):**

| Run | Evento | Duração | Jobs |
| --- | --- | --- | --- |
| 37700502263 | push | 22,0 min | todos (8, com emuladores 28/34/36 nesse tempo: API 34 e 36) |
| 37706667373 | pull_request | 24,1 min | todos, repetido |
| **Total** | **2 runs** | **46,1 min de relógio de CI (2 × a CI inteira)** | |

**Depois:** ver a linha "Depois (medido)" abaixo, preenchida com o run do commit só de docs (`docs/uso.md`).

## Onda 1 — Achar e revisar (verificação ≠ validação)

**Verificação** = o que a CI/máquina prova. **Validação** = o que só o dono prova no celular. Nada abaixo é marcado "feito" sem a evidência citada.

| Critério | Verificação (evidência) | Validação (dono, no celular) |
| --- | --- | --- |
| Achar trecho marcado em gravação de 60 min em ≤3 toques | `OndaMarcadoresTest.acharTrechoMarcado…` (2 toques); instrumentado verde em API 34 e 36 (run 37779440170, commit f26e739) | Achar um marcador real numa aula longa |
| Marcador da notificação aparece na onda | `OndaMarcadoresTest` (instrumentado, verde em API 34 e 36, run 37779440170) | Tocar "Marcar" com a tela bloqueada (a avaliação heurística aponta falta de confirmação — severidade 3) |
| Pular silêncio não remove fala (corpus pt-BR) e tempo economizado | Harness do VAD (ADR-014): 0 trechos de fala cortados nos níveis oferecidos; sala silenciosa economiza 46,5%; ruído contínuo 0%. Job `harness do VAD` corrigido (numpy em venv), reexecução pendente | Ouvir aulas reais com Pular silêncio |
| 10 s de silêncio → pular ≤1 s a mais que a fala | `PularSilencioTest` (instrumentado, verde em API 34 e 36, run 37779440170) | — |
| Trecho exportado toca em outro player | `ExportarTrechoTest` (MediaPlayer, WAV/M4A/MP3); falhas reais achadas e corrigidas (clip além do fim) | Abrir o trecho em outro app |
| Tags filtram a lista | `EtiquetasTest` (verde em API 34 e 36, run 37779440170) | — |
| Nota 141 rejeitada / 140 salva | `OndaMarcadoresTest.nota141…`, `MarkerDialogTest` | — |
| Migração preserva gravações e tags | `MigracaoRoomTest` (Robolectric, DDL v3 exato) e `EdicaoRoomTest` | — |
| Pinça na waveform de 1 h a 60 fps (Macrobenchmark) | Job `macrobenchmark-onda` só informa (emulador por software); ainda sem execução bem-sucedida | Medir no aparelho |
| APK release ≤25 MB | `assembleRelease` não assinado = 7,86 MB (medido localmente). O job assinado exige segredos (`docs/release-assinatura.md`) e falha sem eles | Instalar o APK assinado |
| GitHub Release | Workflow pronto (`gh release create` com APK + SHA-256); **nenhuma tag `v*` criada** | Decidir a tag |
| Avaliação heurística | `docs/ux/heuristica.md` (agente independente; capturas Robolectric, não emulador) | Uso real |
| Spike de transcrição | ADR-013: WER e tempo no host; recomendação base q5_1 | **Bateria e temperatura: PENDENTE** (`spike/whisper/scripts/medir-no-celular.sh`) |

Filtro de caminhos: nova saída `benchmark` (`macrobenchmark/**`, `app/src/benchmark/**`) liga o job do macrobenchmark; `audio` passou a incluir o próprio `ci.yml`.

**Estado da CI na entrega (honesto):** no run 37779440170 (`f26e739`) os instrumentados de API 34 e 36 passaram e o detekt falhou por uma linha longa (corrigida em `efe6417`). O job de API 28 terminou em falha sem log (HTTP 404) e o macrobenchmark não subiu o emulador. O run seguinte (37785758466, `efe6417`) falhou em todos os jobs em ~4 s, sem passos executados, o que aponta para o runner e não para o código; não consegui reexecutar nem disparar por `workflow_dispatch` (403 para a integração). Falta, portanto: um run completo e verde de toda a matriz (API 28 incluída), a reexecução do harness do VAD e o macrobenchmark.

## Onda 1 — fechamento sem CI (2026-10-08): verificação local × validação no celular

**Regime:** a CI do GitHub está desligada (limite de gasto da conta; `ci.yml` só roda por `workflow_dispatch`). Verificação = o que
esta máquina executou, abaixo, com a saída real. Validação = o dono instalando o APK (`docs/teste-aparelho-real.md`, parte A).

### Os 4 achados de severidade 3 (`docs/ux/heuristica.md`)

| Achado | Correção | Teste (executado, verde) |
| --- | --- | --- |
| "Marcar" da notificação sem feedback | Texto "Marcador N às mm:ss" na notificação + uma vibração de 60 ms (`MarkerFeedback`, `SystemHaptics`; permissão `VIBRATE`) | `MarkerFeedbackTest` (6, Robolectric): texto pt-BR e en, relógio, pausada, uma vibração |
| Cancelar o diálogo salva marcador vazio | Marcador novo é **rascunho** até Salvar (`MarkerEditor`, `MarkerDialogTarget.Draft`) | `MarkerEditorTest` (cancelar não grava; salvar cria 1; 141 rejeita) e `MarkerDialogTest` (cancelar sem salvar, rascunho sem "Excluir") |
| Excluir marcador sem confirmação/desfazer | "Marcador excluído — Desfazer" por 5 s; volta com o mesmo id, posição, nota, nome e cor | `MarkerEditorTest` (restaura tudo; expira aos 5 s; 2ª exclusão substitui a 1ª) |
| Fechar o player para a reprodução | ExoPlayer único (`PlaybackHub`) + `PlaybackService` (MediaSessionService, notificação de mídia com pausar/retomar); fechar o painel e sair da aba não pausam; gravação privada mostra "Gravação privada" | `SegundoPlanoTest` (8, Robolectric): mesmo player na tela e no serviço, fechar não pausa, serviço iniciado, pausa pela sessão, título privado, fim encerra o serviço, manifesto (mediaPlayback, não exportado) |

**Não coberto por teste automatizado:** a notificação de mídia aparecendo de verdade, a vibração real e o áudio em segundo plano com a tela
bloqueada (dependem de aparelho): são os passos A1, A9–A12 do roteiro.

### Comandos locais (saída real)

`./gradlew --no-daemon detekt lintDebug testDebugUnitTest koverVerify --continue` → **`BUILD SUCCESSFUL in 2m 48s`**, `rc=0`
(629 tarefas: 128 executadas, 501 já atualizadas — módulos sem mudança vieram do cache do Gradle, já verdes na rodada anterior).
Antes dessa rodada houve duas falhas minhas, corrigidas: detekt (5 linhas longas/complexidade em código novo) e erro de
compilação em `app/build.gradle.kts` (`java.util` dentro de `android {}`).

Testes unitários (soma dos `build/test-results/testDebugUnitTest`): **71 executados, 10 pulados, 0 falhas** (os 10 pulados: 4 de
`CapturasTest`, que só rodam com `GRAVASOM_CAPTURAS`; 5 de `WebRtcVadHostTest` e 1 de `VadCorpusEvalTest`, que exigem a biblioteca nativa de host e o corpus
(`scripts/test-vad-host.sh`) e **não rodaram nesta rodada**).
`koverVerify` rodou nos módulos que o definem (`core:domain`, `core:audio-engine`) e passou.

**Testes instrumentados: NÃO rodaram** (sem emulador nesta máquina). Lista em `docs/teste-aparelho-real.md`, parte B.

### APK entregue

`./gradlew :app:assembleRelease` (R8 ligado, `mapping.txt` gerado) → `app-release.apk`:

| Item | Valor |
| --- | --- |
| versionCode / versionName | **60** / `1.1.0-onda1` (`app/version.properties`; sobe a cada entrega) |
| Tamanho | 8 313 035 bytes = **7,93 MiB** (teto 25 MB) |
| SHA-256 | `a8c353dffb049061363d2d2e498f76d63b522c493d51044a7e7bca8a07b6415b` |
| Assinatura | `apksigner verify`: **OK**; certificado `CN=Android Debug`, SHA-256 `bec8a13c…9b2bd1` = o de `config/debug.keystore` (a mesma chave de todas as entregas: instala por cima) |
| Manifesto | `PlaybackService` com `foregroundServiceType=mediaPlayback`; permissões `VIBRATE` e `FOREGROUND_SERVICE_MEDIA_PLAYBACK` |

A chave de debug é pública (senha "android"): serve para atualizar por cima, **não** é chave de produção. Quando houver chave de release, definir
`GRAVASOM_KEYSTORE_FILE` e as senhas (`docs/release-assinatura.md`): mudar de chave exige desinstalar a versão anterior.

**Não verificado:** o APK nunca foi instalado nem executado (sem aparelho/emulador aqui); "instala por cima de uma versão anterior" só foi
verificado pela identidade do certificado, não por uma instalação real. O R8 pode ter removido algo da mídia: confira A9–A10.

## Onda 2 — Som melhor e proteção (2026-10-09): fechamento em verificação local

Branch `claude/compassionate-johnson-cwdldf` a partir do `1aa545d`, com merge da `main` (PR #5, identidade visual; sem
conflitos — áreas disjuntas). **Verificação ≠ validação**: tudo abaixo é verificação local; a validação de ouvido é o roteiro
B1–B14 de `docs/teste-aparelho-real.md`, para o dono no celular com o APK `1.2.0-onda2` (versionCode 61).

### O que entrou

- **Melhorar som (tela)**: rota `melhorar/{recordingId}` alcançada pelo chip do painel do player e pelo menu (⋮) da gravação;
  ajustes de redutor (intensidade) e volume (alvo em LUFS) com **prévia A/B da mesma janela** (regenerada ao mexer), progresso,
  **cancelamento** (sair da tela também cancela: nada fica pela metade) e cópia nova "(melhorada)" em WAV. DI: `EnhanceModule`
  (`EditProjectStore`, `AppEnhanceHost`, `NoiseReducerFactory` = RNNoise nativo, `PreviewPlayer` por ViewModel).
- **Backup cifrado**: pacote único `.gravasom` (Tink AES256_GCM_HKDF_4KB em fluxo + keyset envolvido por PBKDF2-HMAC-SHA256
  com sal por pacote), gravado/lido por SAF com senha do usuário. Restauração em duas fases: decifra e confere o **SHA-256 de
  tudo** em pasta temporária antes de gravar qualquer coisa — **senha errada restaura e apaga nada**. Restaurar só acrescenta
  (marcadores, etiquetas, favorito e projeto voltam); do cofre sai decifrado e volta ao cofre.
- **Dividir/juntar**: "Dividir aqui" no player (duas gravações novas, mesmo formato, sem reencodar — ClipExporter) e seleção
  por toque longo na lista para juntar (ordem explícita no diálogo; JoinExporter concatena PCM/quadros; recusa formatos ou
  taxas/canais diferentes). **Originais nunca alterados** (ADR-008), conferido bit a bit nos testes.
- **Correções da Onda 1** (já no `d28d554`, reverificadas aqui): pular silêncio em segundo plano e aviso do "Marcar" do widget
  fora de gravação.
- **WAV por gravação no modo avançado**: chips de formato cobertos por teste no modo manual (fora de preset = personalizado).
- detekt limpo **refatorando** (Run/PassSearch/NormParams no EnhancePipeline, Meters no LoudnessAnalyzer, WavSink no
  AudioToWav; 6 linhas longas quebradas) — regras inalteradas.

### Verificação local (a máquina tem 4 GB: daemon com heap reduzido, sem paralelismo)

`detekt lintDebug testDebugUnitTest koverVerify --continue` → **tudo verde** (rodado em dois blocos por causa da memória;
lint precisou de `--no-parallel --max-workers=1`). Lint exigiu `<plurals>` + `pluralStringResource` (6 erros
PluralsCandidate/ResourceType corrigidos, sem baseline). `koverVerify` passou em `core:domain` e `core:audio-engine`
(refatorações mantiveram ≥ 80%).

Testes unitários novos: enhance (conteúdo 8, ViewModel 4 — inclui **cancelar no meio: nenhum arquivo novo e original intacto**),
backup (5 — ida e volta com **o mesmo SHA-256 por arquivo**, senha errada não toca em nada, corrompido recusa), dividir/juntar
(7 — original intacto bit a bit nos dois), formato manual WAV (2), capturas (gera 16 PNGs com `GRAVASOM_CAPTURAS`).

**Harness de áudio** (a parte da verificação que independe do emulador):
- `scripts/onda2-normalizacao.sh`: 48 arquivos, fala FLEURS pt-BR a −30 LUFS em 16/44,1/48 kHz → **dentro de ±1 LU do alvo
  (−16) e pico verdadeiro ≤ −1,30 dBTP** (medidor independente: pyloudnorm + ffmpeg ebur128 + pico 8x).
- `scripts/onda2-ruido.sh`: 72 arquivos/taxa (fala + ruído em 4 SNRs), redutor nativo via JNI no host em 40% e 100% →
  **40% melhora o SI-SDRi do ruído estacionário, a fala limpa fica com STOI ≥ 0,9909 e a saída alinhada** (≤ 1 amostra).
  Uma iteração (k=1.0, 44,1 kHz) falhou por memória da máquina e passou ao ser reexecutada — sem mudança de código.
- `:core:dsp:testDebugUnitTest` com a lib de host: 11 executados, 0 falhas (inclui **"zero por cento é bit a bit idêntico"**);
  `VadCorpusEvalTest` segue pulado sem `GRAVASOM_VAD_CORPUS` (como na CI).

**Testes instrumentados: NÃO rodaram** (sem emulador). Os que tocam nas telas novas (enhance/backup/juntar) ainda nem existem
como instrumentados — a cobertura nova é Robolectric; lista na parte B de `docs/teste-aparelho-real.md`.

### APK entregue

`./gradlew :app:assembleRelease` (R8, `mapping.txt` gerado), assinado com `config/debug.keystore` (fallback do build; a mesma
chave de todas as entregas — instala por cima). **Não verificado:** instalação real (sem aparelho aqui); a validação de ouvido
é o roteiro B1–B14.

| Item | Valor |
| --- | --- |
| versionCode / versionName | **61** / `1.2.0-onda2` |
| Tamanho | 19 729 376 bytes = **18,81 MiB** (teto 25 MB) |
| SHA-256 | `0114a3db127bfbac910732a0a442e89067f62a1853cdbdc2c72bc639c82111a0` |
| Assinatura | `apksigner verify`: OK; certificado `CN=Android Debug`, SHA-256 `bec8a13c…9b2bd1` = o de `config/debug.keystore` (a mesma chave da Onda 1: instala por cima) |
| Manifesto | `native-code` arm64-v8a + x86_64; ABIs do `app/build.gradle.kts` |

A máquina tem só 4 GB de RAM: o build exigiu daemon com heap controlado, `--no-parallel --max-workers=1` e o nativo
compilado antes do R8 (o linker deu `Bus error` sob pressão de memória).

---

# Gravasom Mixlirous — integração da Mixlirous (F0–F9)

Atualizado: 2026-10-10 (F0 concluída)

Estados herdam o vocabulário acima. O plano-mestre está no arquivo `plano` na raiz do repositório.

## Resumo por fase (integração)

| Fase | Estado | Falta |
| --- | --- | --- |
| F0 — Base | APROVADA-PARCIAL | **assembleDebug verde (Gate 0)**: APK debug 45 MB com applicationId com.gravasom.mixlirous; instalação lado a lado + backup no aparelho seguem para o dono |
| F1 — Motor Rust no Android | APROVADA-PARCIAL | `.so` arm64 compilada (cargo-ndk, 12,2 MB) e DENTRO do APK (verificado por `unzip -l`); bug do build.gradle.kts (Exec × TaskProvider) corrigido; JNA adicionado; prévia A/B/fingerprint no aparelho seguem para o dono |
| F2 — Análise | APROVADA-PARCIAL | Room v6 (mix_analysis) + MIGRATION_5_6 escritos e testados (MigracaoV6Test 3/3, Robolectric); AnaliseMixWorker com progresso/cancelamento sem etiqueta parcial; etiqueta + filtros BPM/tom na LibraryScreen; medição de tempo no aparelho pendente |
| F3 — Criar versão (manual) | APROVADA-PARCIAL | feature/criar (CriarScreen + CriarSonsViewModel) e CriacaoWorker escritos; recusa de crossfade > 3000 ms provada no host com a mensagem da ValidationLayer; worker serializa TODOS os params (bug do workDataOf corrigido); render/fingerprint no aparelho pendentes |
| F4 — Spike da SLM | APROVADA-PARCIAL | corpus de 80 casos + ADR-016/017 prontos e verificados no host; medições no aparelho pendentes (roteiro do dono) |
| F5 — Agente | APROVADA-PARCIAL | kernel ReAct + callbacks Kotlin + fallback verificados no host; teste de propriedade com 1000 saídas: 0 receitas fora dos limites; telas HITL em Kotlin pendentes |
| F6 — Criar novos sons | APROVADA-PARCIAL | 4 funções em Rust com testes (loop 20× sem estalo, mescla avisa tons incompatíveis, textura 2–8×, melhores momentos com marcadores); telas em Kotlin pendentes |
| F7 — Transcrição e texto | PENDENTE | whisper embutido (ADR-013 herdado) e casos armadilha no corpus; integração JNI no aparelho pendente |
| F8 — Identidade e acabamento | APROVADA-PARCIAL | paleta Mixlirous padrão com testes atualizados; seletor/tile/widget (Parte B) e acessibilidade pendentes |
| F9 — Entrega | APROVADA-PARCIAL | README, roteiro do dono, ADRs e status completos; APK release arm64 assinado exige SDK/NDK (não existe neste ambiente) |

## F0 — Base

| Gate | Estado | Evidência |
| --- | --- | --- |
| Histórico completo do Gravasom no destino | APROVADA | `git push destino --all` e `--tags` OK; remoto: `main` em `b1933f3`, tag `v1.2.0-onda2` (`git ls-remote destino`) |
| HEAD da base ≥ 2e3a595 | APROVADA | clone em `2e3a595daf0ee6db46bc4b03a18644e428c925b2` (exato) |
| Plano de integração preservado | APROVADA | merge `--allow-unrelated-histories` (`b1933f3`); arquivo `plano` na raiz |
| applicationId `com.gravasom.mixlirous` | APROVADA | `app/build.gradle.kts` (namespace mantido `com.gravasom` de propósito) |
| Nome "Gravasom Mixlirous" | APROVADA | `app/src/main/res/values/strings.xml` e `values-en/strings.xml` |
| Ícone próprio derivado de docs/design/icone | APROVADA | SVGs em `docs/design/icone-mixlirous/`; drawables recoloridos (anel 7,26:1, barras 5,50:1 sobre fundo — mín. 3:1) |
| Paleta Mixlirous como padrão | APROVADA | `Paleta.PADRAO = MIXLIROUS` (`core/ui/.../Paleta.kt`); `GravasomTheme` usa o padrão; testes `TemaGeradoTest` atualizados ao novo contrato |
| versionCode 100 | APROVADA | `app/version.properties` (`versionCode=100`, `versionName=0.1.0-mixlirous`) |
| Mixlirous em third_party/mixlirous @ 5048a10 | APROVADA | `git subtree add --squash` — commits `078ecae` + merge `fbcc2e2` |
| Docs da Mixlirous lidos | APROVADA | README, CLAUDE.md, CHANGELOG, docs/00, 03 (contratos + adendo R2), 04, 05, 16, 17.1 — registrados em docs/worklog.md |
| Compila e instala ao lado do Gravasom | NÃO VERIFICADA | ambiente sem Android SDK — roteiro: `./gradlew :app:assembleDebug` + instalação no aparelho |
| Testes herdados verdes (unit) | NÃO VERIFICADA | exige Gradle/SDK; mesmo regime do item acima |

### F1 — Motor Rust no Android

| Gate | Estado | Evidência |
| --- | --- | --- |
| Crate `rust/mixlirous_mobile` (UniFFI) | APROVADA | 16 unit + 2 integração verdes; bindings Kotlin geradas e commitadas |
| `cargo test` do workspace Rust no host | APROVADA | `mixlirous_mobile` 18/18; subtree: `audio_agent` 71, `audio_core` 207 + 90 de integração/acústicos, 0 falhas |
| `cargo clippy -D warnings` | APROVADA | 0 erros (workspace `rust/`) |
| `reqwest` atrás de feature desligada | APROVADA | P1: `default-features = false` no app; `cargo test` do upstream intacto com default |
| Golden master (host) | APROVADA | `golden_master.rs`: fingerprint < 0,05 sobre WAV real do corpus; pico ≤ teto; I15 |
| Teste JNI com golden master no aparelho | NÃO VERIFICADA | sem emulador/NDK aqui; vira item do roteiro do dono (o caminho Rust já provado no host) |
| `:core:mixengine` compila no Gradle | NÃO VERIFICADA | exige SDK/NDK; tarefas cargo-ndk escritas (debug arm64+x86_64, release arm64) |
| Detekt/lint/testes Kotlin | NÃO VERIFICADA | idem |

### F4 — Spike da SLM

| Gate | Estado | Evidência |
| --- | --- | --- |
| Corpus ≥ 80 intenções pt-BR (60 remix + 20 texto), dev/teste | APROVADA | docs/ia/avaliacao/ (36+24 remix, 12+8 texto); contagens verificadas por `cargo test --test corpus_avaliacao` |
| Receitas esperadas validam | APROVADA | mesmo teste: todas passam na ValidationLayer |
| ADR-016 com protocolo | APROVADA | docs/adr/016-slm-local.md (números: a medir no aparelho) |
| ModelManager (consentimento/SHA-256/importação) | PROPOSTA | ADR-017; implementação Kotlin pendente de SDK |

### F5 — Agente

| Gate | Estado | Evidência |
| --- | --- | --- |
| LlmProvider local via callback UniFFI | APROVADA | `agente.rs`: `ProvedorLlmKotlin` (with_foreign) → `LlmProvider` por spawn_blocking; eventos → `EventosAgenteKotlin` |
| ValidationLayer obrigatória + prompt_guard | APROVADA | injeção rejeitada (teste); receita sempre validada antes do app |
| Fallback para receita manual | APROVADA | SLM fora → `caiu_no_fallback` com a receita manual preservada (teste) |
| 1000 saídas aleatórias → 0 fora dos limites | APROVADA | `tests/propriedade_modelo.rs` (verde); 3 falhas reais corrigidas na barreira |
| ≥ 90% válidas na 1ª tentativa | NÃO VERIFICADA | depende da SLM escolhida rodando no aparelho (ADR-016) |

### F6 — Criar novos sons

| Gate | Estado | Evidência |
| --- | --- | --- |
| Melhores momentos (knapsack + marcadores obrigatórios) | APROVADA (host) | teste: marcadores entram; ordem cronológica |
| Loop de N compassos sem clique | APROVADA (host) | teste: 4 compassos × 20 repetições, emenda ≤ 1,5× do corpo (I4.1) |
| Textura 2×–8× fechada em loop | APROVADA (host) | teste: 4× com fechamento; fora da faixa recusado |
| Mesclar + aviso de tons incompatíveis | APROVADA (host) | teste: Lá menor × Mi♭ maior avisa e recusa sem confirmação (HITL) |
| Loops/telas no aparelho | NÃO VERIFICADA | Kotlin + aparelho do dono |

### F9 — Entrega

| Gate | Estado | Evidência |
| --- | --- | --- |
| README e roteiro do dono | APROVADA | README.md (nova identidade) + docs/teste-aparelho-real-mixlirous.md |
| docs/status.md completo | APROVADA | este documento |
| APK release arm64 assinado | NÃO VERIFICADA | exige SDK/NDK; roteiro de preparo no roteiro do dono |
| PR com resumo | APROVADA | branch relatorio/entrega-f0-f9 → main |

## Gates do roteiro de aparelho (sessão de ambiente com SDK — 2026-10-11)

Formato do roteiro. Ambiente: sandbox Linux 2 CPUs / 4 GB RAM / 10 GB disco,
SDK 36 + NDK 27.2 + Rust 1.99 + cargo-ndk 4.1.2 instalados nesta sessão.
Sem aparelho físico, sem emulador e sem KVM — Gates 1–4 no aparelho seguem o
roteiro do dono (docs/teste-aparelho-real-mixlirous.md).

### Gate 0 — Build debug mínima
- Status: ✅ Verificado
- Comando executado: `./gradlew --no-daemon :app:assembleDebug` (após limpeza de disco ≥ 1 GB livre, `org.gradle.jvmargs=-Xmx1536m` local)
- Saída/resultado: `BUILD SUCCESSFUL` — `app/build/outputs/apk/debug/app-debug.apk` com **45 MB**; `unzip -l` confirma `lib/arm64-v8a/libmixlirous_mobile.so` (12.273.736 bytes), `libjnidispatch.so` (JNA/UniFFI), `libgravasom_dsp.so`, `libmp3lame.so` — só arm64 (`-PabiArm64Somente`, CI/emulador mantém x86_64)
- Observações: (1) bug real corrigido em `core/mixengine/build.gradle.kts` — `fun rustBuildTask(...): Exec` não compila (`tasks.register` devolve `TaskProvider<Exec>`); (2) bindings UniFFI regeneradas com as 5 funções F6 (`criarLoop`, `criarTextura`, `melhoresMomentos`, `avisosDeMescla`, `mesclar`) — a versão commitada estava velha (sem F6) e o construtor virou `MixEngine.novo()` no uniffi 0.32; (3) JNA (`net.java.dev.jna:jna@aar`) faltava no módulo — `:core:mixengine` NUNCA tinha compilado no Gradle (bate com o estado NÃO VERIFICADA anterior); (4) stamp `core/mixengine/build/rust/.so-built.stamp` pula o cargo-ndk quando as `.so` já existem (passo 2.3 do roteiro); (5) APK de referência copiado para fora do sandbox.

### Gate 1 — Instalação e backup
- Status: ⚠️ Parcial (bloqueado por ambiente)
- Comando executado: `adb devices` → *lista vazia*; sem `/dev/kvm` e sem imagem de emulador instalável (disco 10 GB)
- Saída/resultado: nenhum aparelho/emulador disponível nesta sessão
- Observações: dois ícones, ícone roxo, restauração `.gravasom` e paleta em claro/escuro continuam NÃO VERIFICADAS — roteiro do dono. Vira issue de acompanhamento.

### Gate 2 — Motor Rust no aparelho (golden master JNI)
- Status: ⚠️ Parcial (bloqueado por ambiente; a parte de host está verde)
- Comando executado (parte host): `cargo run --example print_receita` (temporário, apagado após o uso) + `cargo test` do workspace registrado em F1
- Saída/resultado (host): receita padrão JSON validada; mutações da tela (duração 45 s, crossfade 1200 ms, 8 blocos) passam na `validar_receita`; **crossfade 5000 ms → recusado com "Receita inválida: Validation failed: crossfade_ms deve estar entre 0 e 3000; recebido 5000"**
- Observações: prévia A/B, fingerprint < 0,05 no logcat (tag `FINGERPRINT`) e tempo de remix de 5 min (alvo ≤ 90 s) seguem para o aparelho.

### Gate 3 — Análise
- Status: ⚠️ Parcial (código escrito + testes de Room no host; medição no aparelho pendente)
- Comando executado: `./gradlew --no-daemon :core:data:testDebugUnitTest`
- Saída/resultado: `BUILD SUCCESSFUL` — 21 testes de core/data verdes, incluindo `MigracaoV6Test` (3/3): migração v5→v6 preserva gravações, `mix_analysis` nasce vazia, upsert substitui, CASCADE ao excluir, etiqueta **"120 BPM · Lá menor"** formatada pela `MixAnalysisEntity`
- Observações: `AnaliseMixWorker` (progresso por ticker de 1 s; cancelar no meio não salva nada — upsert único no fim; `runCatching` em toda chamada UniFFI com erro + stacktrace na saída); LibraryScreen ganhou etiqueta por item e filtros de BPM (≤90 / 90–130 / >130) e tom. Tempo de análise de 5 min e validação contra DAW/tuner seguem para o aparelho.

### Gate 4 — Criar versão (manual)
- Status: ⚠️ Parcial (código escrito + validação provada no host; render no aparelho pendente)
- Comando executado: `./gradlew --no-daemon :core:service:testDebugUnitTest`
- Saída/resultado: `BUILD SUCCESSFUL` — 44 testes verdes; `CriacaoWorkerVetorTest` (4/4) achou e consertou bug real: fingerprint com LUFS null era decodificado como -1.0 (sentinela) e corromperia a distância do "Refazer igual" — agora null via NaN
- Observações: o bug do `CriarSonsViewModel` do roteiro (params que não chegavam ao worker) foi corrigido POR CONSTRUÇÃO — `workDataOf` leva `KEY_RECEITA_JSON`, `KEY_MARCADORES_MS` (já em ms, nome explícito), compassos, fator e fingerprint anterior; o worker revalida a receita (`validarReceita`) antes de qualquer render e a gravação original é só lida (SHA-256 intacto por construção). Render novo, SHA-256 conferido no cofre e "Refazer igual" < 0,05 seguem para o aparelho.
