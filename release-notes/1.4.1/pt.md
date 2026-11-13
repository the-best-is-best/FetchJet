## Novidades da 1.4.1

**Downloads**
- **Downloads por FTP e FTPS (novo):** os links que começam com ftp://, ftps:// ou ftpes:// agora são baixados como qualquer link direto, em várias partes e com retomada quando o servidor permite. Coloque o login no link, salve-o em Configurações > Rede > Logins de sites, ou deixe em branco para servidores anônimos. Bom saber: links FTP não passam pelo proxy; nos apps de computador, FTP sobre TLS é obtido com o curl do sistema; FTP sobre TLS ainda não está disponível no iPhone e no iPad.
- **Um arquivo que muda no servidor:** se o arquivo for substituído no servidor enquanto é baixado, o FetchJet percebe. O download para com uma mensagem clara em vez de salvar um arquivo misturado e quebrado, e Retomar baixa a nova versão.
- **Sem espera no final:** o final de um download não espera mais por uma única conexão lenta. Uma conexão que se arrasta no final é substituída.
- **Verificações antes de salvar:** cole um checksum (SHA-256 ou MD5) na janela Adicionar, ou deixe o cabeçalho de checksum do próprio servidor decidir. Um arquivo que não confere não é salvo. Arquivos ZIP são verificados entrada por entrada, e uma parte danificada é baixada de novo uma vez.
- **Os limites do servidor são lembrados:** um site que permite poucas conexões é lembrado por um dia, para que o próximo download não comece com conexões que ele recusa. Depois de um 403, o FetchJet pode tentar de novo via IPv4 (Windows, Android e Linux).
- **Testar o link:** um botão na janela Adicionar diz se o link funciona, o tamanho e se pode ser retomado, sem baixá-lo.
- **Uma pasta por tipo de arquivo:** a pasta que você escolhe para vídeos, músicas ou arquivos compactados é lembrada apenas para aquele tipo.

**Torrents**
- **Detalhes:** proporção de compartilhamento, quantidade enviada, partes e tamanho da parte, trackers com seu estado, peers, "Verificar de novo", copiar o link magnet e "primeiras e últimas partes primeiro" para pré-visualizar vídeos.
- **Adicionar um tracker** a um torrent em andamento, e **parar de compartilhar em uma proporção** que você escolher (Configurações > BitTorrent).
- **Mais seguro:** um torrent cujos arquivos seriam gravados fora da sua própria pasta é recusado.
- **Atrás de um proxy HTTP:** torrents que só têm trackers UDP não encontram peers ali; Configurações > Rede e a mensagem de erro agora dizem isso.

**Computador**
- **Teclado:** setas, Shift, Home / End, Page Up / Down, Enter (Alt+Enter abre ou fecha os detalhes), Ctrl+R / P / O / E / N / F / A, Delete, Esc e F1 para a lista de atalhos. Funcionam também com teclados árabe, persa e russo, e copiar / colar em cada campo também.
- **Linha de comando:** `/d` link, `/p` pasta, `/f` nome do arquivo, `/a` adicionar pausado sem a janela Adicionar, `/n` adicionar e iniciar na hora sem janela, `/s` iniciar tudo que está esperando.
- **Limite de velocidade só enquanto você usa o computador,** e **acordar o computador para downloads agendados** (Windows).
- **Coisas menores:** a versão no título da janela, Esc fecha a janela Adicionar, os selos de tipo de arquivo ficam legíveis no tema claro, e o título Enviar para um dispositivo está traduzido em todos os lugares.

**Áudio**
- **OGG:** escolher OGG para um áudio do YouTube (Opus) não recodifica mais. Ele é salvo na hora, com a qualidade e o tamanho originais.
- **Comprimir áudio:** agora oferece os mesmos formatos de Converter (M4A, MP3, Opus, OGG, FLAC, WAV), mantém OGG quando o arquivo é OGG, e o tamanho estimado fica mais próximo do real.

**Extensão do navegador (3.4)**
- **Conexão:** quando o navegador ainda não está conectado ao app, a extensão avisa na hora ("Abra o FetchJet e toque em Permitir") em vez de carregar para sempre. Depois de tocar em Permitir, as qualidades aparecem sozinhas. Se o navegador perguntar "Abrir o FetchJet?", a aba agora fica até você responder. Em Configurações > Extensão do navegador há "Desparear todos".
- **O navegador guarda um download até o app recebê-lo,** os formulários que você enviou ficam no navegador, e as janelas privadas usam seus próprios cookies.
- **Links:** um link de arquivo é capturado depois que o código de clique da própria página é executado, então sites que antes respondiam 403 agora funcionam. Um link de PDF abre no navegador, a menos que a página o marque como download. "Baixar todos os links" também encontra links marcados como download e links guardados em atributos data-*, e o Google Drive tem uma linha "Arquivo original".
- **O botão de vídeo:** funciona com o teclado (Tab, Enter), gruda na borda quando você o arrasta até lá, há um botão por grupo de vídeos empilhados e nenhum em um vídeo escondido sob a página. Você pode ocultá-lo em um site, e o Telegram Web explica por que não pode ser baixado.

**Atualizações**
- **A janela de atualização** segue o idioma do app, e as notas da versão estão traduzidas.
