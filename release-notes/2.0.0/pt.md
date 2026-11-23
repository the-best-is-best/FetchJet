## Novidades da 2.0.0

**Uma janela de adicionar mais inteligente**
- **O FetchJet escolhe o mecanismo pelo link:** um arquivo, um link magnet ou um site de vídeo conhecido vai sozinho para Direct, Torrent ou Media. Ele verifica o link uma vez primeiro ("Verificando o link…"), e a escolha entre Direct e Media só aparece para uma página com vídeo ou para um link que não pôde ser verificado.
- **Chega de verificar a cada letra digitada:** a verificação começa quando você para de digitar (na hora, se o link for colado). Trocar o link cancela a leitura anterior, então a janela não fica mais presa em "Buscando o nome…", por exemplo com um magnet que não encontra pares.

**Encontrar arquivos nesta página**
- **Novo:** cole o link de uma página e use "Encontrar arquivos nesta página". O FetchJet lista os arquivos da página: imagens, links e endereços escritos como texto simples. Para links sem nome de arquivo, ele pergunta ao servidor o nome real e o tamanho. Se você pedir, ele também lê as páginas para as quais ela aponta, um ou dois níveis abaixo.
- **Resultados durante a leitura:** os arquivos aparecem enquanto a página é lida, e "Parar a leitura" mantém o que foi encontrado.
- **Escolha o que baixar:** os tipos de arquivo são botões que ocultam ou mostram esses arquivos, e os arquivos ocultos não são baixados. Cada linha mostra seu tipo (ZIP, MP3, PNG…).
- **Salvos como grupo:** os arquivos vão para uma pasta e um grupo com o nome da página. Depois, "Procurar arquivos novos" marca os que você já tem e os bloqueia ("N já baixados").
- **Páginas de releases do GitHub** são lidas em uma única requisição.
- **Páginas que não podem ser lidas:** as páginas que o site recusou (uma verificação ou um bloqueio) são contadas. As páginas que montam seus links com JavaScript são nomeadas, com a dica de abri-las no navegador e usar "Baixar todos os links" da extensão.

**Playlists e legendas**
- **Playlists:** os vídeos que você já baixou são marcados e contados ("N já baixados"), e uma playlist salva como grupo pode ser verificada em busca de vídeos novos.
- **Legendas:** escolha "Embutir no vídeo" ou "Arquivo separado" e depois SRT ou VTT. Os idiomas são pedidos um por vez, e um idioma que o site recusa por um momento é pedido de novo. Você também pode adicionar legendas a um vídeo já concluído, pelos detalhes ou pelo menu da linha.
- **"Enviar para dispositivo" mantém as legendas:** um vídeo concluído enviado a outro dispositivo continua oferecendo "Adicionar legendas" lá, sabe quais idiomas já estão no arquivo, e os arquivos de legenda ao lado dele viajam junto.

**Vídeos**
- **Google Drive:** qualquer link de arquivo do Drive é baixado diretamente, e um arquivo cujo dono desativou o download avisa isso.
- **Vídeos protegidos:** um vídeo protegido por DRM é identificado como tal antes da lista de qualidades, em vez de falhar depois.
- **Vídeos que tocam em qualquer lugar:** a chave (agora em Configurações > Ferramentas de mídia, e também no Android) escolhe arquivos H.264 + AAC que qualquer TV e player abre, ou arquivos AV1 / VP9 menores.

**Configurações**
- **Reorganizadas:** Downloads (local, conexões, fila), uma nova página Limites de velocidade (predefinições, limite em inatividade, agendamento) e uma nova página Arquivos e pastas (ordenar por tipo, agrupar por pasta, extrair, Lixeira, verificação, pastas por site).

**Velocidade**
- Arquivos grandes usam por padrão até 32 conexões.
- Uma requisição que fica 20 segundos sem receber dados é descartada e pedida de novo, então uma conexão travada não paralisa mais um download por um minuto.
- Em transferências curtas e rápidas, uma conexão lenta é substituída mais cedo.
- O macOS permite 64 conexões por site, então um segundo download do mesmo site não espera mais.

**Detalhes menores**
- "Quando os downloads terminarem" também espera um arquivo compactado que está sendo extraído ou um arquivo que está sendo verificado.
- Um torrent com nome longo não estica mais o cartão de arquivos, e as linhas de seus arquivos mostram primeiro o nome do arquivo.
- Links e caminhos de pastas mantêm sua direção em árabe e persa.
- Android: uma playlist lida em segundo plano não faz mais o app travar.
- A janela do desktop é redesenhada por inteiro depois de aparecer ou ser redimensionada, sem áreas sem pintura.
- O app para macOS é compilado para funcionar no macOS 12 e posteriores.
- **Android: sem lista quebrada após reinstalar ou trocar de celular:** a lista de downloads não é mais restaurada de um backup, porque seus links ficam presos ao celular e não podiam ser lidos em outro. Os arquivos que você baixou continuam na sua pasta.
- **Mensagem de legendas mais clara:** quando o link de um download antigo não pode ser lido, "Adicionar legendas" diz que não conseguiu ler as legendas em vez de dizer que o vídeo não tem.
- **Fila mais rápida:** o próximo download em espera começa assim que uma vaga é liberada (50 arquivos pequenos: de 16 s para menos de 1 s), e juntar as partes de um arquivo é mais rápido.
- **Uma página Mais** substitui Configurações na navegação: versão e atualização, Configurações, FetchJet em todos os dispositivos, Compartilhar o FetchJet, e Comunidade e ajuda.
- **Mesmo nome de arquivo:** dois downloads que terminam com o mesmo nome em uma pasta agora são salvos como "nome (1).ext", e um download não some mais de uma fila longa.
- **Uma fonte por idioma** em todas as plataformas (o app ficou cerca de 7 MB maior).
- **Sites de vídeo lembrados:** um site em que uma verificação Media encontrou qualidades passa a ser tratado como site de vídeo na próxima vez, e os tamanhos das qualidades HLS são estimados e exibidos com ~.
- **A janela de adicionar começa vazia** depois de fechada.
- **Windows:** nada de janelas de console piscando, cada versão traz o SHA256SUMS.txt e a versão portátil se atualiza sozinha.
- **Também da 1.5.1:** a verificação de atualização da Microsoft Store, nomes de arquivo em árabe em ZIPs feitos em um Mac e torrents no iOS por proxy.
