# Entrega da integração F0–F9 — resumo verificado × não verificado

Branch de relatório para o PR de fechamento do ciclo F0–F9. O trabalho está na
`main`, fase a fase (um commit por etapa, push ao fim de cada fase); este
documento consolida o que foi **verificado com comando e saída** e o que
**depende do aparelho do dono** (ambiente de trabalho sem Android SDK/NDK nem
emulador).

## Verificado (comando → saída real)

| Fase | Verificação | Evidência |
| --- | --- | --- |
| F0 | histórico + tags no destino | `git ls-remote`: `main` em b1933f3, tag `v1.2.0-onda2` |
| F0 | subtree @ 5048a10 | commits 078ecae + fbcc2e2 |
| F0 | identidade (applicationId, nome, ícone, paleta, versionCode 100) | arquivos + testes de tema atualizados |
| F1 | crate mixlirous_mobile | 18 testes verdes; bindings UniFFI geradas e commitadas |
| F1 | suite da subtree no host | `cargo test -p audio_agent -p audio_core`: 71 + 207 + 90 acústicos, 0 falhas |
| F1 | clippy + fmt | `cargo clippy --all-targets -- -D warnings` limpo |
| F1 | golden master (host) | `tests/golden_master.rs`: fingerprint < 0,05 sobre WAV real; pico ≤ teto; I15 |
| F5 | teste de propriedade | `tests/propriedade_modelo.rs`: 1000 saídas aleatórias → 0 fora dos limites; 3 falhas reais corrigidas |
| F6 | criações | loop 4 compassos × 20 sem estalo (I4.1); textura 2–8×; mescla avisa/recusa tons incompatíveis (HITL); melhores momentos com marcadores |
| F4 | corpus 80 casos | `tests/corpus_avaliacao.rs`: contagens, receitas esperadas, armadilhas |

## Não verificado (depende de SDK/NDK/aparelho — roteiro do dono)

- F0: compilação/instalação Android ao lado do Gravasom.
- F1: `cargo ndk` arm64, APK com o .so, teste JNI com golden master no aparelho.
- F2: Room v6 + WorkManager + etiquetas/filtros (Kotlin) e tempos no aparelho.
- F3: telas de receita/prévia A/B (Kotlin), SHA-256 do original no aparelho.
- F4: spike da SLM no aparelho → números do ADR-016; ModelManager (Kotlin).
- F5: ≥ 90% válidas na 1ª tentativa (precisa da SLM escolhida rodando).
- F6: telas de criação (Kotlin).
- F7: JNI do whisper e resumo/título/etiquetas no aparelho.
- F8: Parte B (seletor, tile/widget, acessibilidade).
- F9: APK release arm64 assinado com SHA-256 entregue pelo chat.

## Pendências/achados registrados

- `docs/mixlirous-patches.md`: patch P1 (rede atrás de feature), achados
  (knapsack ~21% abaixo do alvo em material uniforme; ambiguidade de oitava no
  BPM; `target_duration` e `peak_db` sem newtype no upstream — bloqueados no
  app até virar newtype lá).
- `docs/adr/016-slm-local.md` e `docs/adr/017-model-manager.md`.
- `docs/teste-aparelho-real-mixlirous.md`: roteiro ≤ 30 min para o dono.
