# Gravasom Mixlirous

Gravador de áudio para Android, nativo em Kotlin, que grava em **M4A (AAC)**, **WAV (PCM)** ou **MP3**, funciona totalmente offline e guarda tudo no aparelho — **e agora recompõe**: o motor da [Mixlirous](https://github.com/danzeroum/mixlirous) (Rust, em `third_party/mixlirous`) roda no aparelho para analisar (BPM, tom, energia), criar versões remixadas por receita validada, criar loops/texturas/mesclas e masterizar por destino, com uma SLM local propondo receitas que **você aprova**.

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84) ![Kotlin 2.0](https://img.shields.io/badge/Kotlin%202.x-7F52FF) ![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4) ![Rust](https://img.shields.io/badge/motor-Rust%20%2B%20UniFFI-DEA584) ![Offline](https://img.shields.io/badge/rede-só%20o%20ModelManager-lightgrey)

**Princípios inegociáveis** (herdados da Mixlirous): não gera música, recompõe a sua · a IA nunca toca no PCM, só preenche receitas validadas · nada da IA é aplicado sem aprovação · tudo não destrutivo (original com o mesmo SHA-256) · áudio, transcrições e receitas nunca saem do aparelho (a única rede é o download de modelo, com consentimento e SHA-256 — ADR-017).

Estado da integração F0–F9, verificado × não verificado: [`docs/status.md`](docs/status.md) · Roteiro do dono no aparelho (≤ 30 min): [`docs/teste-aparelho-real-mixlirous.md`](docs/teste-aparelho-real-mixlirous.md) · Patches na Mixlirous: [`docs/mixlirous-patches.md`](docs/mixlirous-patches.md) · ADRs novos: [016 — SLM local](docs/adr/016-slm-local.md) · [017 — ModelManager](docs/adr/017-model-manager.md).

---

## Sumário

- [Funcionalidades](#funcionalidades)
- [Instalação no celular](#instalação-no-celular)
- [Como usar](#como-usar)
- [Formatos de gravação](#formatos-de-gravação)
- [Privacidade e segurança](#privacidade-e-segurança)
- [Limitações conhecidas](#limitações-conhecidas)
- [Arquitetura](#arquitetura)
- [Estrutura do repositório](#estrutura-do-repositório)
- [Desenvolvimento](#desenvolvimento)
- [Qualidade e testes](#qualidade-e-testes)
- [CI/CD e releases](#cicd-e-releases)
- [Documentação](#documentação)
- [Roadmap](#roadmap)
- [Licenças](#licenças)

---

## Funcionalidades

**Gravação**
- Um toque para gravar; pausar, retomar e parar sem perder o áudio.
- Grava com a tela bloqueada (serviço em primeiro plano com notificação: pausar, retomar, parar, marcador).
- Cronômetro e medidor de nível (VU) em tempo real.
- Escolha de formato e qualidade: taxa de amostragem (8–48 kHz), mono/estéreo, bitrate.
- Fonte de entrada: microfone do aparelho, microfone Bluetooth ou USB, com indicação da entrada ativa.
- Pausa automática ao receber ligação, com aviso para retomar.
- Aviso de pouco espaço, com tempo estimado de gravação restante.
- Recuperação após o sistema encerrar o app: a gravação interrompida é reparada e aparece na lista.

**Biblioteca e player**
- Lista com busca, ordenação, favoritos, renomear e excluir com **Desfazer**.
- **Etiquetas** por gravação e filtro por elas (a gravação precisa ter todas as escolhidas); nome automático "data · formato" (ex.: `08/10/2026 14:32 · M4A 128 kbps mono`).
- **Forma de onda** calculada ao salvar, com zoom por pinça (ou botões + e −), arrasto e toque para ir ao ponto.
- **Marcadores com nota** (até 140 caracteres): criados pela tela, pelo botão **Marcar** da notificação (grava com a tela apagada) e pelo widget; aparecem na onda e numa lista; anterior/próximo marcador.
- **Pular silêncio** ao ouvir (WebRTC VAD, analisado ao salvar; níveis Suave e Normal): mostra quanto vai pular. Em ambiente barulhento (ventilador, murmúrio) o VAD enxerga tudo como fala e quase não pula — ver [ADR-014](docs/adr/014-vad-webrtc-pular-silencio.md).
- **Exportar trecho**: selecione na onda (ou "do marcador até o próximo") e salve como gravação nova, sem reencodar (M4A, MP3 e WAV).
- Velocidade de 0,5x a 2x.
- Edição não destrutiva (cortar trechos) com histórico; o arquivo original nunca é alterado.
- Conversão entre formatos (ex.: WAV → MP3).
- Compartilhar com qualquer app (WhatsApp, e-mail, Drive) e "Salvar em…" (Arquivos, Drive).
- Receber áudio de outros apps pela opção "Compartilhar com Gravasom".

**Atalhos**
- Bloco de Configurações Rápidas (Quick Settings Tile).
- Widget de tela inicial.
- Atalho "Gravar agora" ao tocar e segurar o ícone.

**Seus números**
- Tela em Ajustes com gravações feitas, tempo total gravado e formato mais usado, calculados no próprio aparelho.

---

## Instalação no celular

O app ainda não está na Play Store. A instalação é feita pelo APK publicado na CI.

**Requisitos:** Android 8.0 (API 26) ou mais novo, processador de 64 bits (arm64).

1. No GitHub, abra **Actions**, escolha o último run verde da branch principal e baixe o artefato **`gravasom-release-apk`**.
2. Extraia o `.zip` e envie o arquivo `.apk` para o celular (Drive, cabo USB ou e-mail).
3. No celular, abra o `.apk`. Quando o Android pedir, toque em **Configurações → Permitir desta fonte** e volte.
4. Toque em **Instalar**. Se o Play Protect avisar "app desconhecido", toque em **Mais detalhes → Instalar mesmo assim**.
5. Depois de instalar, desative a permissão de fontes desconhecidas no app usado para abrir o arquivo.

**Conferir o arquivo (opcional):** o SHA-256 e a impressão digital do certificado de cada release ficam em [`docs/status.md`](docs/status.md).

> **Vindo de uma versão de debug?** O release é assinado com outra chave e o Android não instala por cima. Desinstale a versão de debug antes. Gravações que já estão em **Música/Recordings** são mantidas; as do cofre privado são apagadas junto com o app.

---

## Como usar

1. Abra o app e toque em **Gravar**. Na primeira vez, o app explica e pede as permissões de microfone e de notificações.
2. Durante a gravação, use **Pausar** e **Parar** na tela ou na notificação.
3. Abra **Gravações** para ouvir, renomear, cortar, compartilhar ou excluir.
4. Em **Ajustes**, escolha o formato padrão, a qualidade, a fonte de entrada e se as gravações devem ir para o cofre privado.

As gravações públicas ficam em **Música → Recordings** e aparecem no app Arquivos e em outros players.

---

## Formatos de gravação

| Formato | Uso indicado | Tamanho aproximado | Observação |
| --- | --- | --- | --- |
| **M4A (AAC-LC)** — padrão | Uso geral, reuniões presenciais, aulas | ~1 MB/min a 128 kbps | Codificador nativo do Android, menor consumo de bateria |
| **WAV (PCM 16 bits)** | Música, edição posterior, máxima fidelidade | ~5 MB/min a 44,1 kHz mono | Sem perdas; arquivos grandes |
| **MP3** | Compatibilidade com qualquer player | ~1 MB/min a 128 kbps | Codificado com LAME 3.100 (biblioteca nativa, LGPL) |

---

## Privacidade e segurança

Voz gravada é dado pessoal. O Gravasom foi desenhado para que nada saia do aparelho sem uma ação sua.

- **Sem internet:** o app não pede permissão de rede.
- **Permissões mínimas:** microfone, notificações e serviço em primeiro plano. Permissão de armazenamento só no Android 8 e 9, onde é exigida para salvar em Música.
- **Cofre privado (opcional):** gravações cifradas com AES-256-GCM (Google Tink), guardadas na área interna do app e fora da lista pública.
- **Compartilhamento seguro:** arquivos saem só por link temporário (`FileProvider`), nunca pelo caminho no disco.
- **Backup:** gravações do cofre ficam fora do backup do Android.
- **Métricas só locais:** "Seus números" é calculado no aparelho, sem áudio, nome de arquivo ou localização; pode ser desligado e apagado em Ajustes.
- **Excluir tudo:** Ajustes oferece apagar todas as gravações e todos os dados de uso.

> Gravar outras pessoas pode exigir o consentimento delas. Avise antes de gravar uma conversa.

---

## Limitações conhecidas

- **Não grava o áudio interno de ligações nem de apps de reunião** (WhatsApp, Meet, Zoom, Teams). O Android bloqueia essa captura por privacidade, e durante uma chamada o microfone fica reservado para o app da reunião.
- **O APK não está na Play Store**; atualizações são manuais.
- **Duração do M4A:** em alguns aparelhos a partida do codificador varia; a diferença medida entre a duração do arquivo e o tempo real fica dentro de ±500 ms.
- Itens validados só por roteiro humano, e não por teste automático: compartilhamento pelo WhatsApp, microfones Bluetooth/USB e TalkBack. O resultado de cada um fica em [`docs/teste-aparelho-real.md`](docs/teste-aparelho-real.md).

---

## Arquitetura

Clean Architecture em módulos Gradle, com dependências sempre apontando para o domínio:

```
:feature:*  ──►  :core:domain  ◄──  :core:data · :core:audio-* · :core:service
```

O domínio e o motor de áudio são Kotlin puro (sem Android), o que permite testá-los só com o JDK.

| Camada | Responsabilidade |
| --- | --- |
| UI (`:feature:*`) | Telas Compose + ViewModels, estado em `StateFlow` (UDF/MVI) |
| Domínio (`:core:domain`) | Casos de uso, modelos e interfaces |
| Motor (`:core:audio-engine`) | Máquina de estados da gravação, cabeçalho WAV, medidor de nível |
| Plataforma (`:core:audio-android`, `:core:dsp`) | `MediaRecorder`, `AudioRecord`, LAME e DSP em C++ via JNI |
| Serviço (`:core:service`) | Serviço em primeiro plano, notificação, tile, widget |
| Dados (`:core:data`) | Room, MediaStore, DataStore, cofre Tink |

**Máquina de estados da gravação:** `Idle → Preparing → Recording ⇄ Paused → Stopping → Saved`, com `Error` alcançável de qualquer estado.

**Padrões principais:** Strategy por formato (`AudioRecorder` com implementações M4A, WAV e MP3), Factory para escolher a estratégia, Repository para esconder Room e MediaStore.

Diagramas C4 (Contexto, Container, Componente, Código) e o diagrama de sequência de interrupções estão em [`docs/architecture/`](docs/architecture/). As decisões estão registradas como ADRs em [`docs/adr/`](docs/adr/).

---

## Estrutura do repositório

```
app/                    Aplicação, navegação, Hilt, atalhos
feature/
  record/               Tela Gravar
  library/              Tela Gravações
  player/               Player: onda com zoom, marcadores, pular silêncio, trecho
  settings/             Ajustes e "Seus números"
core/
  domain/               Casos de uso e modelos (JVM puro)
  audio-engine/         Máquina de estados, WAV, VU (JVM puro)
  audio-android/        Gravadores M4A/WAV/MP3, LAME vendorizado
  dsp/                  Cadeia de processamento em C++ (JNI)
  data/                 Room, MediaStore, DataStore, cofre
  service/              Serviço em primeiro plano, tile, widget
  ui/                   Tema e componentes
baselineprofile/        Geração do Baseline Profile
macrobenchmark/         Inicialização e zoom da onda (1 h)
spike/whisper/          Prova de conceito de transcrição (ADR-013), só com -Pspike
build-logic/            Plugins de convenção do Gradle
config/detekt/          Regras de análise estática
corpus/ · perfis/       Corpus de áudio e tetos calibrados do harness
scripts/                Harness de qualidade de áudio e utilitários
docs/                   ADRs, arquitetura, UX, status, licenças
evidencias/             Logs e medições que comprovam cada gate
```

---

## Desenvolvimento

**Pré-requisitos**
- JDK 17 (o toolchain do Gradle é fixado em 17)
- Android SDK com platform 36, build-tools e NDK 27
- Android Studio (opcional)

**Comandos principais**

```bash
# Compilar e instalar a versão de debug num aparelho conectado
./gradlew installDebug

# Testes de unidade + cobertura
./gradlew testDebugUnitTest koverVerify

# Testes instrumentados (emulador ou aparelho)
./gradlew connectedDebugAndroidTest

# Análise estática
./gradlew detekt lintDebug

# APK e AAB de release (exige as variáveis de assinatura abaixo)
./gradlew assembleRelease bundleRelease
```

**Assinatura do release (local):** defina as variáveis de ambiente abaixo. A chave nunca é versionada.

| Variável | Conteúdo |
| --- | --- |
| `GRAVASOM_KEYSTORE_BASE64` | Keystore em Base64 |
| `GRAVASOM_KEYSTORE_PASSWORD` | Senha da keystore |
| `GRAVASOM_KEY_ALIAS` | Alias da chave |
| `GRAVASOM_KEY_PASSWORD` | Senha da chave |

Sem essas variáveis, o build de release falha com mensagem explícita em vez de gerar um APK sem assinatura.

---

## Qualidade e testes

O projeto separa **verificação** (o código está correto?) de **validação** (faz o que o usuário precisa?). Nenhum item é marcado como aprovado sem a saída real do comando que o comprova, registrada em [`evidencias/`](evidencias/).

| Nível | Ferramentas | O que cobre |
| --- | --- | --- |
| Unidade | JUnit 5, MockK, Turbine, Kover | Máquina de estados, cabeçalho WAV, VU, casos de uso; cobertura ≥ 80% em domínio, motor e dados |
| Integração | Room, testes instrumentados com áudio real | Duração dos arquivos pelo `MediaExtractor`, migrações do banco, JNI do DSP |
| Sistema | Compose UI Test, UI Automator, emuladores API 34 e 36 em **pt-BR e en** | Fluxo gravar → listar → tocar → excluir, interrupções, tile, widget |
| Aceitação | Cenários BDD + roteiro em aparelho real | Jornadas das personas, itens que só uma pessoa consegue validar |

**Análise estática e segurança:** Detekt (complexidade ciclomática ≤ 10), Android Lint com avisos como erro, Semgrep com regras OWASP MASVS e `AccessibilityChecks` nos testes Compose.

**Qualidade de áudio:** um harness compara gravações processadas com a referência (duração, F0, cliques, clipping/true peak, STOI), usando um corpus sintético e locuções reais do LibriSpeech (CC BY 4.0). Cada teto foi calibrado com um controle positivo que precisa ser reprovado. Ver [`CORPUS.md`](CORPUS.md) e [ADR-010](docs/adr/010-medicao-com-referencia.md).

**Metas medidas em emulador**

| Meta | Alvo | Medido |
| --- | --- | --- |
| Toque em Gravar até estar gravando | ≤ 2 s | 666 ms |
| Achar uma gravação entre 20 pelo nome | ≤ 3 toques | 2 toques |
| Interrupções sem perda de arquivo | 100 ciclos | 100 ciclos, 0 corrompidos |
| Tamanho do APK de release | ≤ 25 MB | ver `docs/status.md` |

---

## CI/CD e releases

Pipeline em GitHub Actions ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)), executada a cada push. Pushes novos cancelam os runs anteriores da mesma branch.

| Job | O que faz |
| --- | --- |
| `lint-estatica` | ktlint, Detekt, Android Lint, Semgrep |
| `testes-unidade` | Testes de unidade + Kover |
| `build` | Release com R8 (APK e AAB) |
| `APK de debug` / `release` | Publica os artefatos `gravasom-apk` e `gravasom-release-apk` |
| `instrumentados (API 34/36)` | Testes no emulador, em pt-BR e en |
| `harness audio-suite` | Tetos de qualidade de áudio com controles positivos |

**Segredos do repositório:** as quatro variáveis de assinatura acima e `PLAY_SERVICE_ACCOUNT_JSON` (envio ao canal interno do Play, executado só em tags `v*`).

**Gerar uma versão:** atualize `versionCode` e `versionName` em `app/build.gradle.kts`, crie uma tag `vX.Y.Z` e envie. O job de release assina, mede o tamanho e publica.

---

## Documentação

| Documento | Conteúdo |
| --- | --- |
| [`docs/status.md`](docs/status.md) | Estado de cada gate, com evidência ou motivo da pendência |
| [`docs/adr/`](docs/adr/) | Decisões de arquitetura, com trade-offs explícitos |
| [`docs/architecture/`](docs/architecture/) | Diagramas C4 e de sequência |
| [`docs/ux/`](docs/ux/) | Personas, Crazy 8s, wireframes, jornadas, análise heurística |
| [`docs/design/`](docs/design/) | Identidade visual: tokens (cores, tipo, formas, movimento), ícones, telas com tokens, decisões |
| [`docs/aprendizado.md`](docs/aprendizado.md) | Ciclos de hipótese, medição e decisão |
| [`docs/teste-aparelho-real.md`](docs/teste-aparelho-real.md) | Roteiro e resultados dos testes em aparelho real |
| [`docs/licencas/`](docs/licencas/) | Licenças de terceiros |
| [`evidencias/`](evidencias/) | Logs e medições de cada fase |

---

## Roadmap

- [ ] Redutor de ruído com intensidade ajustável e comparação A/B
- [ ] Realçador de voz
- [ ] Filtros aplicados durante a captação
- [ ] Gravar o áudio reproduzido no celular (música, vídeos), com ou sem o microfone — Android 10+
- [ ] Tela "Sobre e licenças" no app
- [ ] Publicação na Play Store

---

## Licenças

- **LAME 3.100** (`libmp3lame`) — LGPL v2, ligado como biblioteca compartilhada separada e substituível. Detalhes e oferta de código-fonte em [`docs/licencas/`](docs/licencas/).
- **LibriSpeech** (corpus de testes) — CC BY 4.0, baixado por hash e não distribuído no app.
- Demais dependências (AndroidX, Media3, Hilt, Room, Tink, Timber) — Apache 2.0.
