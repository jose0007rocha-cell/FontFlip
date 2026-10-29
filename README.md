# FontFlip

Instalador de fontes (.ttf) para Samsung One UI, reescrito a partir do app "Mono Font Installer 2.1".

## Como gerar o APK (GitHub Actions)
1. Suba esta pasta para um repositório no GitHub (branch `main`).
2. Aba **Actions** → **Build APK** → baixe o artefato `FontFlip-apk`.

Ou localmente: Android Studio → Open → Run. 

## O que mudou em relação ao original
| Antes | Agora |
|---|---|
| Permissões READ/WRITE_EXTERNAL_STORAGE e pasta fixa `monofonts/` (quebra no Android 11+) | Seletor de arquivos do sistema; fontes guardadas no armazenamento privado do app |
| Chaves de teste públicas do AOSP (platform, media, shared, testkey) e keystore dentro do APK | Chave RSA própria gerada no Android Keystore do aparelho; APK assinado (v1+v2) e verificado antes de instalar |
| Template `samsungsans_original.apk` (1,1 MB, com a fonte da Samsung) embutido | APK da fonte montado na hora (manifesto binário + XML + TTF), sem arquivos da Samsung |
| Views/XML + DataBinding + 4 RecyclerViews | Jetpack Compose + Material 3 (cores dinâmicas, modo escuro), 1 tela |
| Sem validação do arquivo | Valida cabeçalho TTF e limite de 50 MB |
| Passos longos e confusos | Cartão de status (Samsung Sans ausente / original / personalizada) + guia curto |
| APK de ~3,3 MB | Sem libs desnecessárias; R8 + shrinkResources |

## Limitações
- Mesmo método do original: substitui o pacote `com.monotype.android.font.samsungsans`. Pensado para One UI 2–3 (Android 10/11); versões novas podem bloquear fontes de terceiros.
- Não foi compilado nem testado em aparelho real. Se o build falhar, envie o log do Actions.
