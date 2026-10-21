# Vision Play TV Premium

Aplicativo Android horizontal para catálogos Xtream Codes, com reprodução via Media3/ExoPlayer.

## Funcionalidades
- Catálogos de canais ao vivo, filmes e séries via API Xtream.
- Pastas e busca por categorias.
- Temporadas e episódios por série.
- Detalhes e EPG quando fornecidos pelo provedor.
- Capas/logotipos a partir dos metadados do provedor.
- Favoritos persistentes.
- Histórico recente de canais, filmes e episódios.
- Retomada individual de filmes e episódios, salvando posição e duração por conteúdo.
- Perfil salvo localmente; a senha é criptografada com Android Keystore.
- URLs de streaming não são gravadas no histórico; são reconstruídas depois de autenticar.

## Build
GitHub Actions compila o APK debug com Java 17 e Gradle 8.13. Abra Actions e baixe o artefato `VisionPlay-APK-debug` após uma execução verde.

## Notas
Capas, logotipos, sinopses e EPG dependem das informações disponibilizadas pela API do provedor. Canais ao vivo podem aparecer no histórico recente, mas não têm retomada de posição como filmes e episódios sob demanda.
