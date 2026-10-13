#!/usr/bin/env bash
# Baixa os 3 modelos whisper MAIS LEVES do catálogo (CatalogoModelos.kt) para
# app/src/main/assets/modelos/ — embutidos no APK na próxima build.
# São arquivos GRANDES (≤ ~78 MB cada): NÃO entram no git (vide .gitignore);
# rode este script de novo em qualquer clone antes de gerar APK com modelos.
# Origem fixa (ADR-017): huggingface.co/ggerganov/whisper.cpp — hashes conferidos
# contra corpus/modelos.sha256 (ADR-013).
set -euo pipefail
cd "$(dirname "$0")/.."

DESTINO="app/src/main/assets/modelos"
mkdir -p "$DESTINO"

# nome|sha256|url — os três mais leves do catálogo (tiny q5_1 32 MB, base q5_1 60 MB, tiny f16 78 MB)
MODELOS=(
  "ggml-tiny-q5_1.bin|818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7|https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin"
  "ggml-base-q5_1.bin|422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898|https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"
  "ggml-tiny.bin|be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21|https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
)

for linha in "${MODELOS[@]}"; do
  IFS='|' read -r nome hash url <<< "$linha"
  alvo="$DESTINO/$nome"
  if [ -f "$alvo" ] && echo "$hash  $alvo" | sha256sum -c - >/dev/null 2>&1; then
    echo "[ok] $nome já presente e com hash conferido"
    continue
  fi
  echo "[..] baixando $nome ..."
  curl -fL --retry 3 --progress-bar -o "$alvo" "$url"
  echo "$hash  $alvo" | sha256sum -c -
done

echo "Concluído — modelos embutíveis em $DESTINO:"
ls -la "$DESTINO"
