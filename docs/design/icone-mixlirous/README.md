# Ícones do Gravasom Mixlirous

Derivados de [`../icone/`](../icone/): a mesma silhueta — **anel** (o botão de gravar) com **três barras de voz** —
recolorida com a paleta **Mixlirous** (`docs/design/tokens.json → color.themes.mixlirous`) para o app ter ícone
próprio e distinto do Gravasom no launcher.

| Arquivo | Fundo | Anel (foreground) | Barras (accent) | Contraste |
| --- | --- | --- | --- | --- |
| `ic_launcher_background.svg` | `#662F79` (primaryContainer dark) | — | — | — |
| `ic_launcher_foreground.svg` | — | `#FAD7FF` (primaryContainer light) | `#EFB0FF` (inversePrimary) | 7,26:1 / 5,50:1 (mín. 3:1) |
| `ic_launcher_monochrome.svg` | — | preto (o sistema pinta) | preto | — |

Silhuetas de 24 dp (`ic_stat_*`, `ic_tile_*`) são brancas sobre transparente e servem aos dois apps sem alteração.
Conversão igual à da pasta original: `<path>` sólido, `fill-rule="evenodd"`, 108 dp no launcher.
