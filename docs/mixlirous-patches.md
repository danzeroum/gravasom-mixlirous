# Patches na Mixlirous (third_party/mixlirous)

A Mixlirous vive neste repositório como `git subtree` (`--squash`), fixada no commit
`5048a107a28524fc515bd7bd26805514a37187d9`. Toda mudança feita na copia local
`third_party/mixlirous/` é um patch que precisa ser:

1. Registrado aqui com: motivo, arquivos tocados e teste que cobre.
2. Limitado ao mínimo necessário — preferir expor algo no crate `mixlirous_mobile`
   a alterar a Mixlirous.
3. Candidato a devolver ao upstream (`danzeroum/mixlirous`) via PR quando tiver
   valor geral.

## Pendências conhecidas herdadas (sem patch)

- Canvas não serializa o grafo para `pipeline_config` — irrelevante no celular
  (o canvas web não é portado; a receita é a fonte de verdade).
- `get_track_peaks` retorna vazio (C9) — o app usa a waveform do próprio
  Gravasom (decodificação no Kotlin); não afeta o motor.
- `audio_agent` depende de `reqwest` — no celular fica atrás de feature
  desligada (ver F1); o provedor LLM local entra por callback UniFFI.

## Patches aplicados

(nenhum ainda — F0 adicionou a subtree sem alterar nada dentro de `third_party/mixlirous/`)
