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
