# Vision Play TV Premium v9

Projeto Android nativo para reproduzir conteúdos autorizados de servidores Xtream Codes e listas M3U.

## Destaques desta atualização
- Tela inicial horizontal com atalhos para Canais ao vivo, Filmes e Séries.
- Trilhos horizontais com pôsteres para favoritos, continuar assistindo e conteúdos recentes.
- Favoritos persistidos localmente no aparelho.
- Salvamento local da posição de reprodução e tentativa de retomada ao reabrir o mesmo item.
- Consulta de sinopse, capas e metadados via endpoints Xtream; guia EPG quando disponível no servidor.
- Perfil de acesso guardado localmente; senha cifrada usando Android Keystore.

## Gerar APK
O workflow GitHub Actions em `.github/workflows/build-apk.yml` compila o APK de depuração e publica o artefato `VisionPlay-APK-debug`.

## Observações
- O servidor precisa fornecer pôsteres, metadados e EPG para que esses dados sejam exibidos.
- A posição de reprodução é salva no dispositivo e pode não funcionar para canais ao vivo.
- Este projeto foi empacotado, mas a compilação Android precisa ser confirmada pelo workflow do GitHub Actions.
