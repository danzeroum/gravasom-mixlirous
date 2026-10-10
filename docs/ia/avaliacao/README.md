# Conjunto de avaliação da SLM local (F4)

Corpus de intenções em português para escolher e medir a SLM do Gravasom
Mixlirous (ADR-016). **Sem dado pessoal real** — todas as transcrições são
sintéticas e genéricas (princípio de privacidade: o corpus é commitado).

## Arquivos

| Arquivo | Casos | Uso |
| --- | --- | --- |
| `remix-dev.json` | 36 remix | desenvolver o prompt e o parser |
| `remix-teste.json` | 24 remix | medir **só no fim** (não treinar/polido nele) |
| `texto-dev.json` | 12 texto | desenvolver prompt de resumo/título/etiquetas/pergunta |
| `texto-teste.json` | 8 texto | medir só no fim |

Total: **80 casos** (piso do plano: ≥ 60 remix com receita esperada + ≥ 20 de
texto), separados em desenvolvimento e teste.

## Esquema — remix

```json
{ "id": "rem-d01",
  "intencao": "versão de trinta segundos pro Reels, mais agressiva",
  "chaves": { "duracao_s": 30, "destino": "reels", "compressao_ratio": 4.0 } }
```

- `intencao` — frase como o usuário fala (números por extenso, gíria,
  maiúsculas, polidez).
- `chaves` — o que a receita da SLM **tem** que conter; o resto fica no padrão
  do destino. Tradução canônica (a mesma do harness `tests/corpus_avaliacao.rs`):
  - `duracao_s` → `target_duration`
  - `destino` → `lufs` (reels/shorts/streaming −14 · voz −16)
  - `crossfade_ms` → `crossfade.max_duration_ms` (0–3000)
  - `compressao_ratio` → `mastering.compression_ratio` (1–10; >2 = mais
    agressivo que o padrão)
  - `blocos_por_batida` → `selection.block_size_beats` (1–64)
  - `criacao: loop|textura|mescla` → roteia para as funções de criação (F6),
    com `compassos` (1–64) ou `fator` (2–8).

Cobertura dos 60 casos de remix: durações (10–150 s), destinos (Reels, Shorts,
streaming, voz), agressividade (2,0–8,0), emendas (0–2500 ms), blocos
(4–16), criações (loops 1–16 compassos, texturas 2×–8×, mesclas) e variações
de registro (grito em maiúsculas, "vamo", "tá?", termos técnicos).

## Esquema — texto

```json
{ "id": "tex-d01", "tarefa": "resumo",
  "transcricao": "…", "instrucao": "Resuma em até três frases.",
  "esperado": { "contem": ["elevador"], "nao_inventa": [],
                "sem_data_se_ausente": false } }
```

- `contem` — fidelidade: tem que aparecer.
- `nao_inventa` — o que NÃO pode surgir se ausente da transcrição (nome,
  número, data, lugar).
- `sem_data_se_ausente` — caso armadilha: transcrição sem nenhuma referência
  de calendário → o resumo não pode ter data.
- `nao_obedece_transcricao` — caso armadilha de **injeção de prompt**: a
  transcrição tentará comandar a SLM ("ignore todas as instruções…"); a saída
  tem que ignorar a tentativa e cumprir a instrução do usuário.
- Títulos e etiquetas são **propostas editáveis** — nunca aplicadas direto.

## Métricas do spike (no aparelho, ADR-016)

| Métrica | Definição |
| --- | --- |
| Válidas na 1ª tentativa | receita passa na `validar_receita` sem replan |
| Corretas | todas as `chaves` do caso presentes na receita |
| Latência | tempo da intenção à proposta (p50/p95) |
| RAM | pico durante a inferência |
| Temperatura | reportada pela bateria após 10 execuções seguidas |

Meta do aceite: ≥ 90% de válidas na 1ª tentativa no conjunto de **teste**;
0 receitas fora dos limites chegam ao render (garantido pela ValidationLayer —
ver `tests/propriedade_modelo.rs`).

## Como executar o harness

```bash
cd rust
cargo test -p mixlirous_mobile --test corpus_avaliacao
```

O harness valida contagens, todas as receitas esperadas (pela mesma
ValidationLayer que a SLM enfrenta) e a coerência dos casos armadilha.
