## Novidades da 1.4.1

- **Downloads por FTP e FTPS (novo):** os links que começam com ftp://, ftps:// ou ftpes:// agora são baixados como qualquer link direto, em várias partes e com retomada quando o servidor permite. Coloque o login no link, salve-o em Configurações > Rede > Logins de sites, ou deixe em branco para servidores anônimos. Bom saber: links FTP não passam pelo proxy; nos apps de computador, FTP sobre TLS usa uma única conexão; FTP sobre TLS ainda não está disponível no iPhone e no iPad.
- **Um arquivo que muda no servidor:** se o arquivo for substituído no servidor durante o download, o FetchJet percebe. O download para com uma mensagem clara em vez de salvar um arquivo misturado e corrompido, e "Retomar" baixa a nova versão.
- **Sem espera no final:** o fim de um download não espera mais por uma única conexão lenta. Uma conexão que se arrasta no final é substituída.
- **Áudio em OGG:** escolher OGG para um áudio do YouTube (Opus) não o recodifica mais. Ele é salvo na hora, com a qualidade e o tamanho originais.
- **Comprimir áudio:** agora oferece os mesmos formatos de Converter (M4A, MP3, Opus, OGG, FLAC, WAV), mantém OGG quando o arquivo é OGG, e o tamanho estimado fica mais próximo do real.
- **Extensão do navegador:** quando o navegador ainda não está conectado ao app, a extensão avisa na hora ("Abra o FetchJet e toque em Permitir") em vez de carregar para sempre. Depois de tocar em "Permitir", as qualidades aparecem sozinhas, sem recarregar. Recarregar uma página de vídeo tenta de novo após um erro.
- **Configurações > Extensão do navegador:** "Desparear todos" desconecta todos os navegadores pareados; cada um pedirá "Permitir" de novo na próxima vez.
- **Janela de atualização:** as notas da versão seguem o idioma do app quando você o altera.
