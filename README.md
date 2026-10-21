# VisionPlay Android

Projeto inicial Android nativo para importar listas M3U/M3U8 e reproduzir fluxos compatíveis usando AndroidX Media3.

## Compilar APK
O GitHub Actions compila automaticamente quando há push na branch `main`. Abra **Actions**, selecione **Build VisionPlay APK** e baixe o artefato `VisionPlay-APK-debug` após a execução terminar.

## Observações
- Este é um protótipo inicial: importa listas por URL HTTP/HTTPS ou arquivo local e reproduz os endereços de mídia encontrados.
- M3U é um formato de lista; M3U8 pode representar playlists HLS. Nem toda URL M3U8 é uma lista de canais.
- Compatibilidade depende do codec, protocolo, servidor, autenticação e disponibilidade da fonte.
- Use apenas listas e transmissões que você tenha autorização para acessar e distribuir.
- A compilação atual gera um APK debug para testes, não uma versão assinada para publicação na Play Store.
