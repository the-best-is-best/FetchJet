## Novidades da 1.5.0

**Arquivos compactados**
- **Extrair depois do download:** arquivos ZIP, TAR, GZ e XZ podem ser extraídos sozinhos quando terminam, ou com o novo botão Extrair na linha. No computador, 7Z, RAR, arquivos divididos (.001, .part1.rar, .z01) e arquivos com senha também funcionam quando o 7-Zip está instalado; o FetchJet pede a senha.
- **Para onde vão os arquivos:** para uma pasta com o nome do arquivo compactado, sempre para uma pasta, ou ao lado do arquivo compactado. Você também pode mover o arquivo compactado para a Lixeira depois de extraí-lo.

**Downloads**
- **Menu do clique direito em um download** (toque longo nos celulares): abrir, abrir a pasta, copiar o link, pausar, retomar, excluir e mais.
- **Pastas por site:** mande tudo o que vem de um site que você escolher para uma pasta própria.
- **Arquivos excluídos vão para a Lixeira** (também no macOS e no Linux) em vez de sumir. Você pode desativar isso em Configurações.
- **Verificação depois do download:** o Windows verifica o arquivo concluído com o seu antivírus, e um download bloqueado pelo antivírus para na hora com uma mensagem clara. No macOS, os arquivos baixados e extraídos levam a marca habitual de "baixado da internet".
- **Relatório mensal:** Configurações > Downloads mostra, para cada mês, quantos arquivos você baixou, o tamanho total e os sites de onde vieram. A contagem é feita só no seu dispositivo.

**Rede (computador)**
- **Seu próprio DNS:** escolha um servidor DNS para os downloads do FetchJet (DNS over HTTPS ou um servidor DNS comum) e o adaptador de rede que eles usam. Só os downloads do FetchJet são afetados, não o resto do computador.

**Windows**
- **Novos instaladores:** um Setup.exe e um novo MSI em que você escolhe criar um atalho na área de trabalho e no menu Iniciar. Eles avisam se esta versão, uma mais nova ou uma mais antiga já está instalada, e podem abrir o FetchJet no final.
- **Versão portátil:** um .zip que roda sem instalar, com os dados em uma pasta ao lado dele.
- **ffmpeg mais novo** no Windows e no Linux (a ferramenta que junta vídeo e áudio). Ele se atualiza sozinho na próxima vez que o FetchJet abrir.

**Android**
- **App menor:** cerca de 53 MB em vez de 83 MB na maioria dos celulares, e ocupa menos espaço depois de instalado.

**iPhone e iPad**
- **Páginas que pedem login, como gravações do Zoom:** abra a página dentro do FetchJet, digite a senha de acesso uma vez e baixe com a sessão da página. O botão da página no Safari também tem "Abrir esta página no FetchJet".

**Para usuários avançados**
- **Linha de comando:** `FetchJet status`, `pause`, `start`, `remove` e `delete` controlam um FetchJet em execução. Ficam desativados até você executar `FetchJet automation on`.

**Extensão do navegador (3.5)**
- Assume todos os downloads do navegador, a menos que você a limite aos tipos de arquivo da sua lista.
- Um botão de download nas gravações do Zoom só de áudio.
- Players dentro de frames encontram o vídeo com mais confiabilidade.

**Privacidade**
- O FetchJet agora envia uma mensagem anônima por dia para sabermos quantas pessoas o usam: um número aleatório para esta instalação, a versão do app e o sistema; o servidor lê o país a partir da conexão. Nenhum link, arquivo ou dado pessoal. Os totais ficam em uma página pública com link no README.
