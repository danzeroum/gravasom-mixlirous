# ADR 017 — ModelManager: como o modelo de IA chega ao aparelho (e por que é a única porta de rede)

Status: aceito (verificação no aparelho pendente — sem Android SDK no ambiente
de trabalho; o desenho segue as regras de rede do plano)

## Contexto

A SLM (ADR-016) e os modelos dela não vão dentro do APK: 1–2,6 GB deixaria cada
atualização do app lenta e pesada, e a regra do produto é que **a única
permissão de internet** do Gravasom Mixlirous é o baixador de modelos, com
consentimento explícito, origem fixa e SHA-256. Áudio, transcrições e receitas
nunca saem do aparelho. O whisper base q5_1 (ADR-013) continua **embutido** —
não passa pelo ModelManager.

## Decisão

1. **Permissão única e verificável.** `android.permission.INTERNET` declarada
   **somente** no processo/componente do ModelManager (isolamento por processo
   não é possível no Android; o isolamento é por arquitetura + auditoria:
   nenhum outro código do app toca em rede — verificado no roteiro do dono com
   StrictMode/inspeção de sockets).
2. **Duas formas de entrada:**
   - **Download com consentimento**: origem fixa (URL do modelo, codificada no
     app — Hugging Face oficial do modelo escolhido no ADR-016), SHA-256
     esperado codificado junto, barra de progresso e botão Cancelar. Download
     em storage privado do app (`filesDir/models/`), **nunca** em storage
     compartilhado.
   - **Importação via "Abrir arquivo"** (Sistema › Modelos › Importar): o dono
     copia o `.gguf`/`.litertlm` por cabo e importa; o SHA-256 é calculado e
     **exibido** para conferência manual com a publicação da origem.
3. **Consentimento explícito**: tela própria antes do primeiro download, com
   tamanho, origem, SHA-256 e a frase "este download é a única conexão de
   rede do app". Sem aceite, o app segue 100% offline (receita manual).
4. **Integridade**: arquivo baixado sem o SHA-256 esperado é **deletado** com
   erro claro; nada é carregado pelo motor sem hash conferido.
5. **Um modelo carregado por vez**: o ModelManager é também o guardião do
   ciclo de vida (carregar/descarregar); quem entra na memória sai quando outra
   superfície pede modelo (SLM vs whisper), regra de memória do plano.
6. **Sem telemetria**: o download não envia nada além dos headers padrão de
   `Range`/`GET` à origem fixa.

## Alternativas descartadas

- **Modelo dentro do APK**: atualização de ~2,6 GB por release; contra a regra
  do plano.
- **Downloads sem origem fixa** (qualquer URL): abre porta para arquivo
  modificado; contradiz "origem fixa e SHA-256".
- **Bundle Play Asset Delivery**: exige Play Store (app pessoal distribuído à
  mão, fora do escopo).

## Trade-offs

- "Toda decisão tem seu preço": a origem fixa + SHA-256 codificados no app
  significam que trocar de modelo é atualização de aplicativo — aceitável para
  app pessoal de um único dono, e é o preço da garantia de integridade.
- Importação manual é o plano B sem rede (celular sem dados); custa o trabalho
  de conferir o hash à mão, uma vez.

## Verificação

- Sem Android SDK no ambiente de trabalho: a implementação Kotlin entra com a
  fase F4 no aparelho. Testes de unidade do checksum e do estado de
  consentimento podem rodar no host (JVM puro) quando o módulo existir.
- Roteiro do dono: confirmação de que nenhum socket abre fora do ModelManager
  (StrictMode + captura do `netstat` durante uso normal sem download).
