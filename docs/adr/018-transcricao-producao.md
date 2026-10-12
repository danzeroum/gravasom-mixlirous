# ADR 018 — Transcrição em produção: `:core:whisper` (JNI) + ModelManager para o whisper

Status: aceito (código completo, testes de host verdes; validação e números no aparelho pendentes — Gate 7)

## Contexto

O spike ADR-013 provou o whisper.cpp 1.8.5 no aparelho (WER pt-BR e RTF medidos no host; base q5_1
recomendado). O ADR-017 definiu o ModelManager para os modelos da SLM e decidiu que o whisper base
q5_1 continuaria **embutido** no APK. O ciclo F7 (roteiro de 2026-10-12) sobrepõe essa parte: a
transcrição vira tela no app e o modelo do whisper passa a vir pelo **ModelManager** (consentimento +
origem fixa + SHA-256), sem modelo embutido. Restava decidir COMO o whisper.cpp entra no build de
produção e como o áudio chega ao motor sem estourar memória.

## Decisão

1. **`:core:whisper`, JNI C++ (não UniFFI).** O spike já era JNI comprovado; UniFFI acrescentaria
   uma camada Rust nova no caminho crítico sem ganho. Mesma fonte do spike (sdist pywhispercpp 1.5.1,
   **sha256 5df897e5…**, whisper.cpp 1.8.5) baixada por `core/whisper/scripts/buscar-whisper.sh`
   antes de qualquer build nativo (nada da fonte no git). Símbolos renomeados para
   `com.gravasom.core.whisper.WhisperJni`.
2. **Buffer DIRETO de shorts, não FloatArray.** 10 min de áudio = 9,6 M amostras: um `FloatArray`
   de heap Java seria 384 MB (OOM); em shorts nativos são 19 MB. O Kotlin decodifica
   (`PcmDecoder`, o mesmo do serviço), faz downmix mono e reamostra 16 kHz (linear, `Resample16k`
   com fila de sobras para pedaços quebrados) num `ByteBuffer.allocateDirect`; o C++ converte
   s16→f32 e chama `whisper_full`. JNI extra: `nativeContarTokens` (prova ADR-016: tokensUsados).
3. **ModelManager para o whisper (sobreposição do ADR-013/§embutido).** Catálogo FIXO de 6 modelos
   ggml (`tiny`/`base`/`small`, f16 e q5_1) com URL da origem oficial e SHA-256 codificados no app:
   tiny/base conferidos contra `corpus/modelos.sha256` (ADR-013) e idênticos na API da origem em
   2026-10-12; small obtidos da mesma API. Download via `DownloadManager` (única porta de rede,
   ADR-017), hash conferido ANTES de qualquer uso (arquivo divergente é apagado), importação SAF
   sem rede com hash exibido para conferência manual, metadados JSON em `files/models/`,
   `removerModelo()` para o protocolo de medição. Consentimento persistente antes do 1º download.
4. **Room v7→v8: `recording_annotations`.** Resumo estruturado (`[mm:ss] trecho` por linha) +
   transcrição bruta + `duracaoInferenciaMs` + modelo + idioma + tokens; FK CASCADE em `recordings`.
   Cadeia não destrutiva (ADR-008): a gravação é só lida.
5. **Tela com edição antes de salvar (Gate 7).** A inferência NÃO grava nada: o resumo volta
   editável e só nasce anotação quando o usuário confirma. Estados fechados: Consentimento →
   Baixando(x%) → Pronto → Preparando → Transcrevendo(t) → Sucesso/Salvo, Erro.
6. **Cancelamento cooperativo.** Preparo do áudio cancela na hora; `whisper_full` é síncrono —
   cancelar durante a inferência descarta o resultado no fim (nada é salvo). Limitação documentada
   do whisper.cpp; thread própria + `whisper_full` parcial exigiria callback nativo (futuro).

## Alternativas descastadas

- **whisper-rs no motor Mixlirous (UniFFI)**: acoplaria remix e transcrição num ciclo de build
  Rust só (e o ADR-013 já mediu o caminho JNI).
- **FloatArray pela ponte**: 384 MB de heap para 10 min — OOM na maioria dos aparelhos.
- **Modelo embutido (ADR-013 original)**: +60–488 MB por APK e sem atualização de modelo;
  sobreposto pelo roteiro F7/ADR-017.
- **Modelo selecionável por URL livre**: contradiz a origem fixa + SHA-256 (ADR-017).

## Consequências

- APK release arm64 **17,8 MB** — sem modelo dentro, segue sob o teto de 25 MB da Onda 1; o modelo
  entra no aparelho só por consentimento (32–488 MB em `filesDir/models`, privado).
- Um modelo por vez na memória (`TranscricaoWhisper` é `AutoCloseable`; a sessão fecha por transcrição).
- A única rede do app segue sendo o baixador de modelos — verificação do dono com `netstat`
  durante uso normal sem download (roteiro Gate 7).

## Verificação (host, 2026-10-12)

- `testDebugUnitTest` (todos os módulos) **BUILD SUCCESSFUL**: `MigracaoV8Test` (v7→v8 preserva
  gravações + CASCADE), `ModelStoreTest` (consentimento, metadados, hash NIST "abc", catálogo),
  `Resample16kTest` (48k/44,1k→16k com zero-crossing, downmix, pedaços ímpares, passagem 1:1),
  `ResumoFormatterTest` (timestamps [mm:ss], bruto, parse JNI).
- `:app:assembleRelease -PabiArm64Somente=true` **BUILD SUCCESSFUL**; `apksigner verify` OK.
- Números de latência/RAM/temperatura no aparelho: **PENDENTES** (tabela do Gate 7 em `docs/status.md`).
