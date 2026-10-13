#!/usr/bin/env python3
"""Procura strings.xml com % literal não escapado em strings que também têm argumentos posicionais.

Bug de campo (2026-10-12): "acontece 100% no aparelho" num string com %1$s → IllegalFormatFlagsException
na composição → o app fecha ao abrir a tela de transcrição.
"""
import glob
import re
import sys

PADRAO_ARG = re.compile(r"%[0-9]+\$")


def tem_porcento_bare(corpo: str) -> bool:
    """Remove tokens de formato válidos e procura % restante (bug de campo 2026-10-12)."""
    limpo = re.sub(r"%%", "", corpo)                      # % literal escapado
    limpo = re.sub(r"%[0-9]+\$[.0-9]*[sdf]", "", limpo)   # %1$s, %1$.4f, %1$05d
    limpo = re.sub(r"%[sdf]", "", limpo)                  # %s/%d/%f não posicional
    return "%" in limpo


falhas = 0
for arquivo in sorted(glob.glob("*/src/main/res/values/strings.xml") + glob.glob("core/*/src/main/res/values/strings.xml") + glob.glob("feature/*/src/main/res/values/strings.xml") + glob.glob("app/src/main/res/values*/strings.xml")):
    texto = open(arquivo, encoding="utf-8").read()
    for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', texto, re.S):
        nome, corpo = m.groups()
        if "<xliff" in corpo:
            continue
        tem_args = bool(PADRAO_ARG.search(corpo))
        barra = tem_porcento_bare(corpo)
        if tem_args and barra:
            print(f"{arquivo}:{nome}")
            print(f"   → {corpo.strip()[:120]}")
            falhas += 1
        elif barra and not tem_args:
            # % literal em string sem args: só quebra se alguém formatar; registrar informativo
            print(f"[sem args, ok se nunca formatado] {arquivo}:{nome} → {corpo.strip()[:80]}")

print(f"\n{falhas} string(s) com % literal + args posicionais (crash na formatação)")
sys.exit(0)
