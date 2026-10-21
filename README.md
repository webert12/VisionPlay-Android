# Vision Play TV — Xtream API

Aplicativo Android para celular, Android TV e TV Box, com interface escura e azul.

## Recursos desta versão
- Conexão por URL do servidor, usuário e senha Xtream.
- Aceita URL base ou URL completa com `player_api.php` / `get.php`; o app normaliza o endereço para a API.
- Carrega os catálogos completos de canais ao vivo, filmes e séries, sem o antigo limite de 3.000 itens.
- Leitura JSON em fluxo e carregamento em segundo plano para lidar com listas extensas.
- Pastas de canais por emissora/região quando os nomes permitem identificar; filmes pelas categorias do servidor; séries por streaming quando reconhecido no nome/categoria.
- Busca por título, categoria e pasta.
- Tela cheia em modo paisagem com botão para voltar.
- Episódios de séries consultados ao abrir uma série.
- Credenciais mantidas apenas em memória nesta versão.

## Gerar APK
O GitHub Actions compila `app-debug.apk` a cada push na branch `main` ou manualmente em Actions > Build VisionPlay APK > Run workflow. Baixe o artefato `VisionPlay-APK-debug`.

## Observações
- O aplicativo baixa metadados/catálogos, não baixa os arquivos de vídeo inteiros para o aparelho.
- Catálogos grandes (por exemplo, dezenas de milhares de filmes) podem demorar alguns minutos, dependendo do servidor e da conexão.
- Os agrupamentos por emissora/região/plataforma dependem dos nomes e categorias fornecidos pela API. Quando o provedor não informa esses dados, o item fica em uma pasta genérica.
- Prefira HTTPS. Em HTTP, usuário e senha trafegam sem criptografia.
- A disponibilidade de canais, filmes e séries depende dos direitos e do plano autorizado pelo provedor.
