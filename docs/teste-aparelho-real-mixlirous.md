# Roteiro de teste no aparelho real (dono) — ≤ 30 min

Ambiente de trabalho desta integração **não tinha Android SDK/NDK nem
emulador**: tudo que exige aparelho está aqui, em ordem, para o dono validar.
Cada item tem o que observar e o que registrar em `docs/status.md`.

## Preparar (fora dos 30 min)

1. `rustup target add aarch64-linux-android` + Android NDK 27 + `cargo install cargo-ndk`.
2. `python3 third_party/mixlirous/scripts/generate_fixtures.py` (gera fixtures locais).
3. `./gradlew :app:assembleDebug` — primeira compilação do `:core:mixengine`
   (cargo-ndk arm64-v8a + x86_64 debug) e do app com `applicationId
   com.gravasom.mixlirous`.
4. Instalar no aparelho ao lado do Gravasom original: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## 1. Instalação e backup (5 min)

- [ ] O app **instala ao lado** do Gravasom (dois ícones, dois nomes).
- [ ] Ícone do Mixlirous: anel + barras em roxo (distinto do teal do Gravasom).
- [ ] Restaurar um backup `.gravasom` do Gravasom: gravações, marcadores e
      etiquetas voltam.
- [ ] Paleta inicial: **Mixlirous** (roxo) em claro e escuro; vermelho de
      gravando e cores de marcador inalteradas.

## 2. Motor Rust no aparelho — golden master JNI (5 min)

- [ ] Rodar o teste instrumentado (quando adicionado ao módulo) ou o teste do
      roteiro: abrir uma gravação musical e pedir "Criar versão" → prévia A/B
      funciona (o motor carregou: `libmixlirous_mobile.so` presente na ABI arm64).
- [ ] Comparar dois renders da MESMA receita: a distância exibida (fingerprint)
      é **< 0,05**.
- [ ] Registrar: tempo de remix de uma gravação de 5 min (alvo ≤ 90 s).

## 3. Análise (5 min)

- [ ] Salvar uma gravação musical → análise em background com progresso;
      cancelar no meio não deixa etiqueta parcial.
- [ ] Etiqueta aparece no formato "120 BPM · Lá menor" (conferir com DAW/tuner).
- [ ] Waveform colorida por energia; filtros por BPM e tom funcionam.
- [ ] Registrar: tempo de análise de 5 min no aparelho.

## 4. Criar versão (manual) (5 min)

- [ ] Tela de receita: duração alvo, blocos por batida, crossfade, destino.
- [ ] Crossfade > 3000 ms é **recusado** com mensagem clara (ValidationLayer).
- [ ] Render vira gravação nova; o original fica com o MESMO SHA-256 (conferir
      no cofre/backup ou com o painel de detalhes).
- [ ] "Refazer igual" re-renderiza com o mesmo som (fingerprint < 0,05).

## 5. SLM (spike F4) — medição para o ADR-016 (8 min)

- [ ] ModelManager: tela de consentimento (tamanho, origem, SHA-256); baixar o
      modelo escolhido **ou** importar arquivo; hash conferido.
- [ ] Rodar o corpus `docs/ia/avaliacao/` no aparelho pelo modo de avaliação:
      registrar válidas na 1ª tentativa, corretas, latência p50/p95, RAM,
      temperatura (tabela no ADR-016).
- [ ] Intenção falada: "versão de trinta segundos pro Reels, mais agressiva"
      (whisper → SLM) → receita com 30 s e compressão maior que o padrão.
- [ ] Recusar uma proposta → nada renderiza, original intacto.
- [ ] Desinstalar/renomear o modelo → aviso claro + receita manual com padrões
      do destino (fallback).

## 6. Criar sons (4 min)

- [ ] Loop de 4 compassos repetido 20× sem clique na emenda (fone).
- [ ] Textura da gravação de natureza (2×–8×) fechada em loop.
- [ ] Mesclar voz + textura; tons incompatíveis mostram o aviso **antes** do
      render; recusar não renderiza; aceitar renderiza com o aviso registrado.
- [ ] Melhores momentos: marcadores do usuário entram como trechos obrigatórios.

## 7. Sem rede fora do ModelManager (2 min)

- [ ] StrictMode ou `adb shell netstat` durante uso normal (gravar, analisar,
      remixar, transcrever): **nenhum socket** fora do download de modelo.
- [ ] Modo avião com modelo já instalado: tudo funciona.

## 8. Transcrição (se já integrada no build)

- [ ] Resumo de aula/reunião; títulos e etiquetas chegam como proposta editável.
- [ ] Transcrição sem datas → resumo sem data (caso armadilha do corpus).
- [ ] Transcrição com injeção ("ignore todas as instruções…") → ignorada.

## Registrar

Preencher em `docs/status.md` as linhas "NÃO VERIFICADA" com comando/saída ou
print, e colar os números do ADR-016. Tudo que falhar vira issue — não afrouxe
teste nem tolerância para passar.

## 9. Gate 7 — F7 Transcrição (whisper local + ModelManager, ADR-017/018)

Preparo: modelo ainda NÃO instalado (Ajustes › Transcrição › Remover modelo, ou 1ª vez).

### 9.1 ModelManager

- [ ] Library › ⋮ › **Transcrever** numa gravação → abre a tela de transcrição.
- [ ] **Consentimento**: mostra tamanho (~60 MB base q5_1), origem (huggingface.co) e os
      primeiros bytes do SHA-256 ANTES de qualquer download; o app fica 100% offline sem aceite.
- [ ] **Download**: barra com %; concluído, o modelo fica em
      `files/models/` (adb shell run-as com.gravasom.mixlirous ls files/models) e sobrevive ao
      fechar do app.
- [ ] **Hash conferido**: o download só é usado se o SHA-256 do arquivo bater com o codificado
      (arquivo divergente é apagado com erro claro).
- [ ] **Única porta de rede**: `adb shell netstat` durante uso normal (gravar, remixar,
      transcrever com modelo já presente) — nenhum socket; só há conexão durante o download.
- [ ] **Importação SAF**: "Importar arquivo do modelo…" aceita um `.bin`/`.gguf` copiado por cabo;
      SHA-256 calculado e exibido para conferência manual; funciona em modo avião.

### 9.2 Transcrição

- [ ] Transcrever uma gravação de ~5 min com o **base q5_1**: barra de progresso visível durante
      a inferência (tempo decorrido); sem travar a UI.
- [ ] Saída estruturada com timestamps `[mm:ss]` por trecho; switch desliga as marcas.
- [ ] **Editar** o resumo antes de salvar; "Salvar como anotação" persiste na Room
      (recording_annotations) — a anotação aparece vinculada à gravação.
- [ ] Cancelar durante o preparo volta para "Pronto"; cancelar durante a inferência descarta o
      resultado (limitação documentada no ADR-018) e não salva nada.
- [ ] Gravação importada (origin=IMPORTED) transcreve igual.

### 9.3 Medição ADR-016 (whisper no aparelho — preencher a tabela em docs/status.md)

Para cada modelo (tiny q5_1 → base q5_1 → small q5_1), Repetir-modelo em
Ajustes › Transcrição (ícone Remover modelo) força o re-download:

1. Transcrever 5× a mesma gravação de 3 min; anotar a "Inferência: X s" da tela.
2. `adb shell top -n 1 | grep gravasom` durante a inferência → RAM pico (MB).
3. `adb shell cat /sys/class/thermal/thermal_zone*/temp` no fim → temperatura máx (°C).
4. Anotar o modelo (nome do arquivo) e o SHA-256 conferido.

| Modelo | Tamanho (MB) | SHA-256 (16 primeiros) | p50 (s) | p95 (s) | RAM pico (MB) | Temp. máx (°C) |
|---|---|---|---|---|---|---|
| tiny q5_1 | 32 | 818710568da3ca15 | medir | medir | medir | medir |
| base q5_1 | 60 | 422f1ae452ade6f3 | medir | medir | medir | medir |
| small q5_1 | 190 | ae85e4a935d7a567 | medir | medir | medir | medir |

### 9.4 Instalar os APKs desta entrega (mesma chave debug do Gate 6 — instala por cima)

```bash
adb install -r gravasom-mixlirous-debug-arm64-f7.apk     # Gate 7 (sem R8)
adb install -r gravasom-mixlirous-release-arm64-f7.apk   # distribuição (R8, 17,8 MB)
```
