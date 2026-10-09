# Gravasom Mixlirous — análise de integração e prompt do agente

Oct 9, 2026 · @Daniel

## Veredito

Os dois projetos se completam: o Gravasom captura e organiza o som, e a Mixlirous transforma o que foi gravado em material novo. O app novo fecha o ciclo **gravar → entender → recompor → masterizar → compartilhar** no próprio celular, sem que o áudio saia do aparelho.

Três princípios da Mixlirous viram regras do app novo, porque são eles que tornam segura uma IA pequena rodando no celular:

- **"Não gera música, recompõe a sua."** "Criar novos sons" significa recombinar, esticar, emendar e masterizar as suas gravações, não sintetizar áudio do nada.
- **"A IA propõe, você decide."** Toda sugestão da IA aparece como proposta com prévia A/B, e nada é aplicado sem o seu toque.
- **A IA nunca toca no áudio.** O modelo só preenche uma receita em JSON, que passa pela camada de validação em Rust antes de chegar ao motor de DSP. Uma alucinação vira erro de validação, nunca um arquivo estragado.

A SLM (modelo de linguagem pequeno) tem dois papéis: traduzir uma intenção ("versão de 30 s para o Reels, mais agressiva") em receita, e trabalhar sobre o texto das transcrições (resumo de aula, títulos, etiquetas, perguntas). Nos dois casos ela roda no celular e entrega texto estruturado e verificado.

## O que cada projeto traz

Inventário lido nos repositórios: `danzeroum/gravasom` na `main` (commit `2e3a595`, versão 1.2.0-onda2) e `danzeroum/mixlirous` (commit `5048a10`). O `gravasom-mixlirous` existe e está vazio.

| Área | Gravasom (Android, Kotlin + C++) | Mixlirous (Rust + web) |
| --- | --- | --- |
| Captura | Gravação M4A/WAV/MP3, serviço em primeiro plano, tile, widget, 6 presets | — |
| Organização | Biblioteca, etiquetas, marcadores com nota, waveform, cofre cifrado, backup | Fingerprint acústico para comparar renders |
| Análise | VAD (WebRTC), loudness | Batidas e BPM, onsets, croma, tonalidade, pitch, RMS, métricas de qualidade |
| Edição | Cortar, dividir, juntar, exportar trecho (não destrutivo) | Corte em blocos alinhados às batidas, seleção por mochila (knapsack), emenda em zero-crossing com crossfade |
| Som | Redutor RNNoise, normalização −16 LUFS, true peak | Compressor, limiter, LUFS, time-stretch |
| IA | Spike whisper.cpp (ADR-013: base q5\_1), sem tela | Loop ReAct com orçamento de ferramentas, Validation Layer tipada, propostas HITL, catálogo de receitas, provedores Ollama/OpenAI/mock |
| Qualidade | Harness de áudio com tetos calibrados, testes locais, roteiro em aparelho | Golden master, fingerprint < 0,05 entre renders, testes acústicos, CI verde |
| Interface | Compose + identidade visual com paletas Gravasom e Mixlirous | React + canvas de grafo (DAG), SSE, player A/B |

Duas pendências da Mixlirous importam para o celular: o grafo do canvas ainda não vira a configuração executada, e o crate `audio_agent` depende de `reqwest` (HTTP) para falar com o LLM. No app, o canvas web não é portado, e o provedor do LLM passa a ser o modelo local.

## Encaixe orgânico

O app novo não ganha uma "aba Mixlirous" separada: cada função da Mixlirous entra no momento da jornada em que o gravador já está. A ordem da tabela é a ordem da jornada.

| Momento | Funcionalidade | Vem de | Adaptação para o celular |
| --- | --- | --- | --- |
| Ao salvar | **Análise automática**: BPM, tom, energia ao longo do tempo, loudness | `audio_core` (análise) | Roda em segundo plano (WorkManager) só em gravações de música ou quando pedida; resultado vira etiquetas ("120 BPM · Lá menor") e cor na waveform |
| Na biblioteca | **Filtrar e combinar** por BPM e tom | análise + Room | Filtros novos; sugestão "combina com esta" (mesmo tom, BPM próximo) |
| No player | **Criar versão** com receita manual: duração alvo, blocos de N batidas, crossfade, destino (Reels, Shorts, streaming) | `PipelineConfig` + pipeline de remix | Tela com controles simples e modo avançado; prévia A/B; resultado é gravação nova com a receita salva |
| No player | **Criar versão por intenção**, digitada ou falada | `react_kernel` + `validator` + SLM local + whisper | Frase falada vira texto (whisper), a SLM monta a receita, a validação barra o que sair dos limites, e a proposta aparece para aprovar, recusar ou ajustar |
| Ensaio longo | **Melhores momentos**: compilação dos trechos de maior energia | seleção por knapsack | Escolher a duração final; marcadores do usuário entram como trechos obrigatórios |
| Criar sons | **Loop de N compassos** com emenda sem estalo | blocos + zero-crossing + crossfade | Exporta loop que toca em repetição sem clique |
| Criar sons | **Textura** a partir de Natureza e Água | time-stretch + crossfade | Estica 2× a 8× e fecha em loop; vira fundo para outras gravações |
| Criar sons | **Mesclar** duas gravações (voz sobre textura, dois trechos de ensaio) | análise de tom/BPM + time-stretch + mixer | Ajusta andamento, equilibra volumes e masteriza; avisa quando os tons não combinam em vez de forçar |
| Exportar | **Masterização por destino** | compressor + limiter + LUFS (ordem corrigida no doc 16 da Mixlirous) | Alvos por destino: streaming −14 LUFS, voz −16 LUFS; true peak ≤ −1 dBTP |
| Sempre | **Refazer igual** | receita salva + golden master + fingerprint | A receita fica junto do resultado; renderizar de novo dá o mesmo som |
| Aulas e reuniões | **Transcrição, resumo, títulos, etiquetas e perguntas** | whisper (ADR-013) + SLM | Tudo no aparelho; a IA propõe o título, o resumo e as etiquetas, e você aceita ou edita |

**Fica de fora, de propósito:** canvas de grafo (é ferramenta de desktop; no celular a receita aparece como lista de passos editável), multi-inquilino, API web, observabilidade de servidor, separação de instrumentos (modelos grandes e lentos no celular; reavaliar depois), síntese de áudio e clonagem de voz (contrariam o princípio da Mixlirous).

## Arquitetura

&#91;embedded content: arquitetura do Gravasom Mixlirous · camadas e fronteiras\]

O Kotlin entrega PCM ao motor em Rust, à transcrição e ao DSP do Gravasom, mas nunca à SLM. Quem fala com a SLM é o kernel ReAct dentro do motor: ele pede uma receita, recebe JSON restrito e o valida antes de qualquer render. A única saída para a internet é o ModelManager, para baixar o modelo uma vez.

## SLM: modelo, runtime e travas

A escolha final é do agente, por medição, entre dois caminhos. Os dois rodam no aparelho, sem servidor, e os dois forçam a saída no formato da receita (decodificação restrita), que é o que torna um modelo pequeno confiável para preencher JSON.

| Caminho | Runtime | Modelos candidatos | A favor | Contra |
| --- | --- | --- | --- | --- |
| A (preferido) | LiteRT-LM (Google), API Kotlin estável | Gemma 4 E2B; Qwen3-0.6B como piso | GPU e NPU no Android, chamada de função e decodificação restrita nativas, formato `.litertlm` | Arquivo do Gemma 4 E2B com \~2,6 GB; termos de uso da Gemma; download no Hugging Face pode exigir aceite |
| B (alternativo) | llama.cpp (GGUF) via JNI | Qwen3 / Qwen3.5 1,7B ou 4B em Q4\_K\_M | Licença Apache 2.0, gramática GBNF/JSON Schema garante JSON válido, mesmo motor de inferência do whisper.cpp | Só CPU na prática; 4B pesa \~2,5 GB e roda perto de 10 tokens/s em aparelho de 8 GB |

**Como o agente decide:** um conjunto de avaliação com pelo menos 80 intenções em português (60 de receitas de remix e 20 de texto: resumo, título, etiquetas), separado em desenvolvimento e teste, com a receita esperada para cada uma. Mede: receitas válidas na primeira tentativa, receitas corretas, latência até a proposta, memória e temperatura. O resultado vai para o ADR-016.

**Travas que valem em qualquer caminho:**

1. A saída da SLM é sempre restrita ao esquema das ferramentas da Mixlirous (`AudioToolDef`).
2. Toda receita passa pela `ValidationLayer` em Rust; fora dos limites vira observação para o modelo replanejar, dentro do orçamento de ferramentas.
3. Nada é renderizado sem a sua aprovação, com prévia A/B.
4. Se o modelo falhar ou não estiver instalado, o app cai na receita manual: a IA ajuda, mas nunca é obrigatória.
5. O texto da sua intenção passa pelo `prompt_guard` da Mixlirous antes de chegar ao modelo.

## Fases do agente

Dez fases em sequência, sem parar entre elas. Cada uma termina com verificação local verde, commit, entrada em `docs/status.md` e no `docs/worklog.md`. O agente só para antes do fim por `DECISAO_NECESSARIA` (decisão irreversível ou que muda o produto).

| Fase | Entrega | Gate para avançar |
| --- | --- | --- |
| F0 — Base | `gravasom-mixlirous` com todo o histórico do Gravasom; app id `com.gravasom.mixlirous` e nome "Gravasom Mixlirous"; paleta Mixlirous como padrão; Mixlirous em `third_party/mixlirous` (subtree no commit `5048a10`) | App compila e instala ao lado do Gravasom original; todos os testes herdados verdes |
| F1 — Motor Rust no Android | Módulo `:core:mixengine` com crate `mixlirous_mobile` (UniFFI), `audio_core` e `audio_agent` compilados com cargo-ndk; `reqwest` atrás de feature desligada | `cargo test` do workspace verde no host; teste JNI roda o pipeline de remix sobre um WAV do corpus e bate o fingerprint do golden master |
| F2 — Análise | BPM, tom, energia e loudness ao salvar ou sob pedido; Room v6; etiquetas automáticas; filtros | Erro de BPM ≤ 2% e tom correto em ≥ 80% no corpus sintético da Mixlirous; análise de 5 min medida no aparelho |
| F3 — Criar versão (manual) | Tela de receita, prévia A/B, render não destrutivo, receita salva com o resultado, "Refazer igual" | Zero estalos no harness; fingerprint < 0,05 entre dois renders da mesma receita; original intacto (SHA-256) |
| F4 — Spike da SLM | Conjunto de avaliação pt-BR, caminhos A e B medidos, ADR-016, `ModelManager` (download com consentimento ou importação, SHA-256) | ADR-016 com números; modelo escolhido instala e responde no aparelho |
| F5 — Agente | `LlmProvider` local via callback UniFFI, decodificação restrita ao esquema, propostas HITL, raciocínio visível, fallback manual | ≥ 90% de receitas válidas na 1ª tentativa no conjunto de teste; 0 receitas fora dos limites chegam ao render (teste de propriedade com saídas aleatórias do modelo) |
| F6 — Criar novos sons | Melhores momentos, loops, texturas, mesclar | Loops sem clique na emenda; mesclar avisa tons incompatíveis; tudo não destrutivo |
| F7 — Transcrição e texto | whisper base q5\_1 embutido (ADR-013), intenção falada, resumo, títulos, etiquetas, perguntas sobre as transcrições | WER pt-BR medido; resumo nunca inventa nome ou número que não está na transcrição (teste com casos armadilha) |
| F8 — Identidade e acabamento | Parte B da identidade visual, acessibilidade, bateria e temperatura, APK só arm64 | Contraste AA nas paletas; nenhum processamento pesado sem barra de progresso e cancelar |
| F9 — Entrega | README, roteiro de aparelho, status final, APK assinado, PR | Verificação local completa verde; o dono valida pelo roteiro |

## Decisões e riscos

Como você quer o agente trabalhando sem parar, deixei decididas no prompt as escolhas que o travariam. Todas podem ser revertidas antes de mandar.

| Decisão | Escolha no prompt | Por quê |
| --- | --- | --- |
| Convivem no celular? | Sim: app id `com.gravasom.mixlirous`, ícone e nome próprios | Você mantém o Gravasom estável enquanto testa o novo |
| Como as gravações chegam ao app novo | Restaurando o backup `.gravasom` do Gravasom | O backup cifrado já existe e foi testado |
| Como a SLM chega ao aparelho | Download na primeira vez, com consentimento e SHA-256, ou importação de arquivo; whisper continua embutido | Um modelo de 1 a 2,6 GB dentro do APK deixa cada atualização lenta e pesada; a permissão de internet fica restrita ao baixador de modelos, e o áudio nunca sai |
| Mixlirous dentro do repo | Subtree em `third_party/mixlirous`, fixada no commit `5048a10`; mudanças registradas em `docs/mixlirous-patches.md` | Dá para compilar sem rede e devolver as melhorias ao projeto original depois |
| Ponte Kotlin ↔ Rust | UniFFI | Gera as ligações em Kotlin e permite que o LLM local seja implementado em Kotlin e chamado pelo kernel em Rust |
| Dois motores de DSP (C++ do Gravasom e Rust da Mixlirous) | C++ continua com redutor de ruído e "Melhorar som"; Rust fica com análise, remix e masterização das criações | Cada função com um dono só, sem reescrever o que já passou no harness |
| CI | Desligada; verificação local e APK entregue pelo chat | Mesmo regime do Gravasom |
| Assinatura | `config/debug.keystore` | App pessoal; instala por cima a cada versão |

**Riscos principais:**

- **Compilar Rust para Android** (cargo-ndk, UniFFI, `symphonia`, `rubato`) é a parte mais trabalhosa e a que mais pode travar a F1. Por isso ela vem logo depois da base: se não passar, o resto do plano muda.
- **Golden master em ARM:** contas de ponto flutuante podem variar entre computador e celular. O fingerprint tem tolerância (< 0,05), mas os hashes exatos dos testes da Mixlirous podem não bater; o agente registra a diferença em vez de afrouxar o teste.
- **Memória e temperatura:** SLM, whisper e DSP juntos podem passar de 3 GB de RAM. Regra: um modelo carregado por vez, com descarregamento ao sair da tela.
- **Bugs abertos da Mixlirous:** a própria Mixlirous registra problemas de DSP em aberto. O agente lê o CHANGELOG e o doc 16 antes de confiar em cada etapa.
- **Licença do modelo:** Gemma tem termos de uso próprios; Qwen é Apache 2.0. Para app pessoal, os dois servem.

## Prompt completo para o agente

Variante estendida do padrão (`<system>` + `<plan>`), porque o agente vai executar um plano longo sem supervisão. Cole o bloco inteiro.

```xml
<system>
You are a senior Android + Rust audio engineer. You build production-grade apps,
verify every claim by running commands, and never report a test, build or gate as
passing without its real output. You work autonomously through all phases and
stop only for DECISAO_NECESSARIA.
</system>

<lang>Kotlin 2.x + Android (minSdk 26, targetSdk 36), Jetpack Compose, Room, Media3, Hilt; Rust stable (cargo-ndk, UniFFI); C++/JNI; whisper.cpp; runtime de SLM (LiteRT-LM ou llama.cpp)</lang>

<task>Criar o app "Gravasom Mixlirous" no repositório vazio danzeroum/gravasom-mixlirous: partir do Gravasom inteiro e integrar de forma orgânica o motor de remix da Mixlirous (danzeroum/mixlirous), adaptado ao celular, com uma SLM local que transforma intenções em receitas validadas e trabalha sobre transcrições. O app grava, entende, recompõe, masteriza e compartilha, tudo no aparelho.</task>

<plan>
F0 Base: clone danzeroum/gravasom (main, commit 2e3a595 ou mais novo) e envie todo o histórico e as tags para danzeroum/gravasom-mixlirous. Troque applicationId para com.gravasom.mixlirous, nome "Gravasom Mixlirous", ícone próprio derivado de docs/design/icone, paleta Mixlirous como padrão. Adicione a Mixlirous como git subtree em third_party/mixlirous no commit 5048a10. Leia o README, CLAUDE.md, CHANGELOG, docs/00, 03, 04, 05, 16 e 17.1 da Mixlirous antes de qualquer código Rust.
F1 Motor Rust no Android: crate rust/mixlirous_mobile (UniFFI) que expõe análise, pipeline de remix, validação e o kernel ReAct; reqwest/tokio de rede atrás de feature desligada no mobile. Módulo :core:mixengine compila com cargo-ndk (arm64-v8a no release, x86_64 só em debug). Decodificação continua no Kotlin (PcmDecoder do Gravasom) e entrega PCM ao Rust.
F2 Análise: BPM, grade de batidas, tom, energia por trecho e loudness, ao salvar (música/presets musicais) ou sob pedido, em WorkManager com progresso e cancelar. Room v6 com migração escrita à mão. Etiquetas automáticas ("120 BPM · Lá menor"), cor de energia na waveform, filtros por BPM e tom, sugestão "combina com esta".
F3 Criar versão manual: tela de receita (duração alvo, blocos de N batidas, crossfade, destino Reels/Shorts/streaming/voz) com modo avançado; prévia A/B; render não destrutivo como gravação nova; receita salva junto (JSON) e ação "Refazer igual".
F4 Spike da SLM: conjunto de avaliação em docs/ia/avaliacao/ com ≥ 80 intenções pt-BR (60 de remix com a receita esperada, 20 de texto), separado em dev e teste. Meça o caminho A (LiteRT-LM + Gemma 4 E2B; piso Qwen3-0.6B) e o caminho B (llama.cpp + Qwen3/Qwen3.5 1,7B e 4B Q4_K_M com gramática JSON). Registre no ADR-016: válidas na 1ª tentativa, corretas, latência, RAM, temperatura, tamanho, licença. Escolha e implemente o ModelManager (download com consentimento explícito + SHA-256, ou importação via "Abrir arquivo").
F5 Agente: LlmProvider local implementado em Kotlin e chamado pelo kernel Rust via callback UniFFI; saída restrita ao esquema de AudioToolDef; ValidationLayer obrigatória; prompt_guard na entrada; propostas HITL com Aprovar/Recusar/Ajustar; raciocínio visível em streaming; orçamento de ferramentas; fallback para a receita manual se o modelo faltar ou falhar.
F6 Criar novos sons (recompondo só material do usuário): Melhores momentos (knapsack por energia, marcadores do usuário como trechos obrigatórios), Loop de N compassos sem clique, Textura de Natureza/Água (time-stretch 2×–8× com fechamento em loop), Mesclar duas gravações (ajuste de andamento, equilíbrio de volume, masterização, aviso de tons incompatíveis).
F7 Transcrição e texto: whisper base q5_1 embutido (ADR-013); intenção falada para F5; resumo de aula/reunião, título e etiquetas sugeridos, perguntas sobre as transcrições (busca FTS4 + SLM), sempre como proposta editável.
F8 Identidade e acabamento: Parte B da identidade (docs/design/parte-b.md: seletor de paleta, tile/widget com estado real, notificação colorida, pico no VU, cor do marcador), acessibilidade, um modelo carregado por vez, bateria e temperatura.
F9 Entrega: README, docs/status.md, docs/teste-aparelho-real.md (roteiro ≤ 30 min), APK release arm64 assinado, PR para main.
</plan>

<context>
App pessoal de um único usuário (aulas, reuniões, ideias, música e ensaios, entrevistas, natureza). Sem CI: o ci.yml só roda por workflow_dispatch. Verificação local + validação do dono no celular. Tamanho do app pode ser grande se for coerente com a qualidade, mas a SLM não vai dentro do APK. Gravasom já tem: gravação, biblioteca, marcadores, waveform, VAD, redutor RNNoise e "Melhorar som" (C++), backup cifrado .gravasom, cofre, identidade visual (tokens.json, paletas Gravasom/Mixlirous). Mixlirous já tem: análise (batidas, croma, tom, pitch, RMS, qualidade), knapsack, emenda zero-crossing + crossfade, compressor/limiter/LUFS/time-stretch, fingerprint e golden master, kernel ReAct, ValidationLayer, prompt_guard, catálogo de receitas. Pendências conhecidas da Mixlirous: canvas não serializa o grafo; get_track_peaks vazio; audio_agent depende de reqwest.
</context>

<rules>
- Execute F0 a F9 em ordem, sem pedir confirmação entre fases. Um commit por etapa lógica (Conventional Commits), push ao fim de cada fase. Pare só com DECISAO_NECESSARIA (decisão irreversível ou que muda o produto), listando as opções.
- Princípios inegociáveis: não sintetizar áudio nem clonar voz (só recompor o material do usuário); a IA nunca toca em PCM, só preenche receitas validadas; nada da IA é aplicado sem aprovação; tudo não destrutivo (original com o mesmo SHA-256).
- Rede: única permissão de internet para o ModelManager baixar modelos, com consentimento, origem fixa e SHA-256; áudio, transcrições e receitas nunca saem do aparelho.
- Cada função com um dono: C++ do Gravasom para redutor e "Melhorar som"; Rust da Mixlirous para análise, remix e masterização das criações. Mudanças na Mixlirous ficam em third_party/mixlirous e são listadas em docs/mixlirous-patches.md.
- Memória: um modelo (SLM ou whisper) carregado por vez; processamento pesado sempre em WorkManager com progresso e cancelar.
- Verificação local ao fim de cada fase: cargo test e cargo clippy -D warnings do workspace Rust; ./gradlew detekt lintDebug testDebugUnitTest koverVerify; harness de áudio quando a fase mexer em som. Instrumentados só se houver emulador; senão, viram itens do roteiro do dono. Nunca afrouxe regra de detekt, lint ou teste para passar.
- docs/status.md e docs/worklog.md atualizados a cada fase, separando verificado (comando e saída) de não verificado. ADR para cada decisão nova, com trade-offs.
- Telas usam só os tokens de core/ui; vermelho só para gravar e excluir; textos em pt-BR e en.
- APK release: arm64-v8a, R8, assinado com config/debug.keystore, versionCode começando em 100; entregue pelo chat com SHA-256. Ao fim de F3 e F6, entregue também um APK intermediário para o dono acompanhar.
</rules>

<aceite>
- O app instala ao lado do Gravasom e restaura um backup .gravasom com todas as gravações, marcadores e etiquetas.
- Análise: erro de BPM ≤ 2% e tom correto em ≥ 80% no corpus sintético; tempo para analisar 5 min registrado no aparelho.
- Remix: zero estalos e zero clipping no harness; true peak ≤ −1 dBTP; loudness ±1 LU do destino; fingerprint < 0,05 entre dois renders da mesma receita; tempo de remix de 5 min registrado (alvo ≤ 90 s).
- Agente: ≥ 90% de receitas válidas na 1ª tentativa no conjunto de teste; 0 receitas fora dos limites chegam ao render; sem modelo, todas as funções de criação continuam disponíveis pela receita manual.
- Texto: o resumo não inventa nome, número ou data ausentes da transcrição nos casos armadilha; títulos e etiquetas são propostas editáveis.
- Nenhuma conexão de rede fora do ModelManager (teste com StrictMode/inspeção de sockets).
- README, ADR-016, docs/status.md e roteiro do dono completos; PR aberto com resumo de verificado × não verificado.
</aceite>

<testes>
- Receita com crossfade de 5000 ms vinda da SLM → ValidationLayer rejeita, vira observação, o modelo replaneja dentro do orçamento.
- Teste de propriedade: 1000 saídas aleatórias no lugar do modelo → nenhuma chega ao render fora dos limites.
- Recusar uma proposta → nada renderizado, original intacto, nova proposta sem gastar orçamento.
- Modelo ausente ou falhando → aviso claro e tela de receita manual preenchida com o padrão do destino.
- Loop de 4 compassos tocado 20 vezes seguidas → nenhum clique na emenda (harness de glitch).
- Mesclar gravações em Lá menor e Mi♭ maior → aviso de tons incompatíveis antes de renderizar.
- Mesma receita renderizada duas vezes → fingerprint < 0,05.
- Intenção falada "versão de trinta segundos pro Reels, mais agressiva" → receita com target_duration 30 s e compressão maior que a padrão.
- Transcrição sem nenhuma data → resumo sem data.
- Migração Room v5 → v6 preserva gravações, marcadores, etiquetas e projetos de edição.
</testes>

<recomendacao>
- [RECOMENDAÇÃO] Design Centrado no Usuário: considere o processo completo de design — empatizar, definir, idear, prototipar e testar — não apenas o código.
- [RECOMENDAÇÃO] Prototipação Rápida: use protótipos como ferramenta de co-criação, não apenas validação; produza Crazy 8s e baixa fidelidade das telas novas (Criar versão, Proposta da IA, Criar sons).
- [RECOMENDAÇÃO] Arquitetura de Informação: avalie organização, navegação, rotulação e hierarquia dos conteúdos.
- [RECOMENDAÇÃO] Usabilidade Heurística: análise heurística (Nielsen) cobrindo facilidade de aprender, eficiência, retenção, erros e satisfação.
- [RECOMENDAÇÃO] Atributos de Qualidade: avalie escalabilidade, performance, segurança e manutenibilidade, e se as decisões minimizam o custo cumulativo ao longo da vida útil do sistema.
- [RECOMENDAÇÃO] Observabilidade: logging, tracing e monitoramento que permitam compreender o comportamento interno e identificar gargalos de performance (Perfetto nas etapas de análise, remix e inferência).
- [RECOMENDAÇÃO] Segurança por Design: criptografia de dados sensíveis, privilégio mínimo e conformidade com LGPD e GDPR desde o início.
- [RECOMENDAÇÃO] Atributos de Qualidade (-ilities): identifique e documente escalabilidade, performance, segurança, disponibilidade, manutenibilidade e testabilidade.
- [RECOMENDAÇÃO] Leis da Arquitetura: "Toda decisão tem seu preço" e "Uma decisão só pode ser avaliada em relação ao seu contexto"; trade-offs explícitos em cada ADR.
- [RECOMENDAÇÃO] Documentação C4/4+1: níveis Contexto, Container, Componente e Código, mais diagramas de sequência (intenção → SLM → validação → proposta → render).
- [RECOMENDAÇÃO] Refatoração: reduza acoplamento, automatize testes, identifique padrões emergentes e planeje para escalar.
- [RECOMENDAÇÃO] Verificação e Validação: separe verificação (está correto?) de validação (é o que deveria fazer?) em cada nível.
- [RECOMENDAÇÃO] Níveis de Teste: unidade, integração, sistema e aceitação, com foco em limites, riscos e áreas de maior complexidade.
- [RECOMENDAÇÃO] TDD/BDD: código novo por TDD; comportamentos em Given/When/Then.
- [RECOMENDAÇÃO] Análise Estática e SAST: código morto, duplicado, complexidade ciclomática alta, vulnerabilidades OWASP e violações de padrão (detekt, lint, clippy, Semgrep).
- [RECOMENDAÇÃO] Overfitting e Generalização: separe dev e teste no conjunto de avaliação da SLM, use métrica condizente com o uso real e confirme que generaliza para intenções reais.
- [RECOMENDAÇÃO] Qualidade dos Dados: verifique completude, consistência e veracidade dos dados de avaliação e de corpus (garbage in, trash out) e o respeito à LGPD no tratamento de dados sensíveis.
- [RECOMENDAÇÃO] Segurança de IA: avalie a robustez contra entradas adversas (injeção de prompt na intenção ou na transcrição), a supervisão humana e os princípios de IA responsável.
- [RECOMENDAÇÃO] Viés e Ética: avalie se o conjunto de avaliação cobre sotaques, gêneros musicais e situações diversas.
- [RECOMENDAÇÃO] 7 Princípios PbD: proativo, privacidade por padrão, privacidade no design, soma positiva, transparência, segurança ponta a ponta e centrado no usuário.
- [RECOMENDAÇÃO] Dados Pessoais vs Sensíveis: classifique voz e transcrições; para dados sensíveis, exija proteção adicional e justificativa de processamento.
</recomendacao>
```

## Fontes

- Repositório [danzeroum/mixlirous](https://github.com/danzeroum/mixlirous), commit `5048a10`: README, `docs/00-VISAO-ESCOPO.md`, `crates/audio_core`, `crates/audio_agent` (`react_kernel.rs`, `tools.rs`, `validator.rs`), `prompts/catalog.json`.
- Repositório [danzeroum/gravasom](https://github.com/danzeroum/gravasom), `main` no commit `2e3a595` (versão 1.2.0-onda2).
- [LiteRT-LM — visão geral](https://developers.google.com/edge/litert-lm/overview) — Google AI Edge (API Kotlin estável, chamada de função com decodificação restrita, GPU/NPU no Android, modelos suportados).
- [Google LiteRT-LM e Gemma 4](https://infoq.com/news/2026/06/google-litertlm-gemma4) — InfoQ, junho de 2026 (Gemma 4 E2B/E4B no aparelho, \~2,58 GB o E2B).
- [Qwen 3.5 Small para celular](https://toolhalla.ai/blog/qwen-3-5-small-best-llm-for-phone-2026) — ToolHalla, março de 2026 (tamanhos, Apache 2.0, desempenho aproximado em celular).
- [Modelos para IA no celular em 2026](https://www.ertas.ai/blog/best-models-on-device-mobile-ai-2026) — Ertas (tamanhos em GGUF Q4 e licenças de Llama 3.2, Gemma 3, Phi-4 Mini e Qwen 2.5).
