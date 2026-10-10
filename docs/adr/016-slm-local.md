# ADR 016 — SLM local: escolha por medição entre LiteRT-LM (Gemma/Qwen3-0.6B) e llama.cpp (Qwen3 Q4_K_M)

Status: **proposto — aguardando medição no aparelho** (o protocolo e o corpus
estão prontos; os números só saem de verdade no aparelho do dono)

## Contexto

F4/F5 pedem uma SLM **local** que traduza intenções pt-BR em receitas validadas
(`PipelineConfig` + ferramentas) e trabalhe sobre transcrições (resumo, título,
etiquetas, perguntas). Regras do produto: a saída é sempre restrita ao esquema
das ferramentas; toda receita passa pela ValidationLayer em Rust; sem modelo
instalado, tudo continua funcionando pela receita manual. O áudio, as
transcrições e as receitas nunca saem do aparelho.

Dois caminhos candidatos (do plano):

| Caminho | Runtime | Modelos | A favor | Contra |
| --- | --- | --- | --- | --- |
| **A** | LiteRT-LM (API Kotlin estável) | Gemma 4 E2B (~2,6 GB); piso Qwen3-0.6B | GPU/NPU no Android; chamada de função e decodificação restrita nativas | arquivo grande; termos de uso da Gemma; download pode exigir aceite no HF |
| **B** | llama.cpp (GGUF) via JNI | Qwen3 / Qwen3.5 1,7B ou 4B Q4_K_M + gramática GBNF | Apache-2.0; gramática garante JSON válido; mesmo motor do whisper.cpp (ADR-013) | só CPU na prática; 4B pesa ~2,5 GB e roda perto de 10 tok/s em 8 GB |

## Decisão (processo)

A escolha sai do **conjunto de avaliação** (`docs/ia/avaliacao/`): 80 intenções
pt-BR (60 remix com receita esperada, 20 de texto), separadas em dev e teste.
O mesmo corpus roda nos dois caminhos; vence o que cumprir o piso:

1. ≥ 90% de receitas válidas na 1ª tentativa no conjunto de **teste**;
2. latência p95 da intenção à proposta ≤ 8 s;
3. pico de RAM que conviva com a regra "um modelo carregado por vez";
4. temperatura sob 10 execuções seguidas dentro do normal de uso;
5. licença compatível com app pessoal (Qwen: Apache-2.0; Gemma: termos próprios —
   aceite registrado aqui antes da entrega).

Desempate: caminho A (preferido do plano) por GPU/NPU e decodificação restrita
nativa; caminho B se o A falhar nos itens 1–3, ou se o aceite de termos da
Gemma for um problema.

## O que já é independente da escolha (feito e verificado no host)

- A barreira de alucinação é a **ValidationLayer do app** (`mixlirous_mobile`),
  não o modelo: `tests/propriedade_modelo.rs` prova 1000 saídas aleatórias com
  **zero** receitas fora dos limites chegando ao render, e o fallback manual
  para SLM ausente/quebrada (kernel ReAct do upstream, doc 05).
- O prompt_guard do upstream roda antes do modelo; injeção na intenção é
  rejeitada (teste no host).
- O corpus de avaliação é validado por `tests/corpus_avaliacao.rs` (contagens,
  receitas esperadas, armadilhas).

## Trade-offs explícitos

- "Toda decisão tem seu preço": a decodificação restrita do caminho A/B reduz
  alucinação de formato, mas não de conteúdo — por isso a ValidationLayer
  permanece obrigatória nos dois caminhos (defesa em profundidade).
- "Uma decisão só pode ser avaliada em seu contexto": os números de tokens/s
  citados no plano vêm de benchmarks públicos; a métrica que vale é a do corpus
  pt-BR deste app no aparelho do dono.
- Um modelo carregado por vez (SLM **ou** whisper) é regra de memória, não
  preferência de runtime.

## Consequências

- ModelManager (ADR-017) baixa/importa o modelo escolhido com SHA-256 e
  consentimento explícito; nenhuma outra superfície do app usa rede.
- Os números medidos entram aqui (tabela abaixo) antes do F9.

| Métrica (conjunto de teste) | Caminho A | Caminho B |
| --- | --- | --- |
| Válidas na 1ª tentativa | — medir no aparelho — | — medir no aparelho — |
| Corretas (todas as chaves) | — | — |
| Latência p50/p95 | — | — |
| RAM pico | — | — |
| Temperatura após 10 execuções | — | — |
| Tamanho do modelo | — | — |
