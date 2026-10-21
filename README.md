# Vision Play TV

Aplicativo Android de streaming com suporte à API Xtream Codes e importação de arquivo M3U.

## Interface v7
- Tela inicial com opções separadas para Canais ao vivo, Filmes e Séries.
- Catálogos acessados somente após selecionar uma seção.
- Reprodução em tela cheia horizontal ao escolher um conteúdo.
- Toque no vídeo para mostrar ou ocultar controles, sem sair da reprodução.
- Barra de progresso arrastável, posição/duração, pausa/reprodução, saltos de 15 segundos e próximo item.
- Exibe metadados de filmes e séries quando a API do provedor os disponibiliza.
- Navegação de voltar para catálogo e tela inicial.

## Compilar
O workflow GitHub Actions em `.github/workflows/build-apk.yml` compila o APK debug e publica o artefato `VisionPlay-APK-debug`.

## Conexão
Informe o endereço-base do servidor Xtream, usuário e senha. Os dados de acesso ficam apenas em memória nesta versão. Prefira HTTPS quando disponível. O aplicativo não hospeda nem inclui conteúdo: a disponibilidade depende da conta e do servidor do usuário.

## Observação
O código deve ser compilado e testado no GitHub Actions e no dispositivo antes de considerar esta versão validada.
