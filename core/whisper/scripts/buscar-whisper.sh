#!/usr/bin/env bash
# Baixa o codigo-fonte do whisper.cpp 1.8.5 (MIT) POR HASH e o extrai em $1 (nada do whisper.cpp fica no git).
# A fonte e o sdist do pywhispercpp 1.5.1 no PyPI, que embute o whisper.cpp 1.8.5 sem alteracoes.
# Mesma fonte/hash do spike ADR-013 (spike/whisper/scripts/buscar-whisper.sh) — uma unica referencia.
set -euo pipefail
DEST="${1:?uso: buscar-whisper.sh <dir_destino>}"
HASH="5df897e5dd9d9f16804fc937bd85b9f5880a0b29a55f8843585e0e03d76195e5"
URL="https://files.pythonhosted.org/packages/9f/35/610787a277e177e6b7983277a35a2969c0876b46c0d889bc6a0c4b4ac4a7/pywhispercpp-1.5.1.tar.gz"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
curl -fsSL --retry 3 -o "$TMP/src.tar.gz" "$URL"
echo "$HASH  $TMP/src.tar.gz" | sha256sum -c - >&2
tar xzf "$TMP/src.tar.gz" -C "$TMP" --wildcards '*/whisper.cpp'
rm -rf "$DEST"; mkdir -p "$(dirname "$DEST")"
mv "$TMP"/pywhispercpp-1.5.1/whisper.cpp "$DEST"
grep -m1 'project("whisper.cpp"' "$DEST/CMakeLists.txt" >&2
echo "$DEST"
