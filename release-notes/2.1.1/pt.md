## Novidades da 2.1.1

**Player de mídia**
- **Novo:** reproduza áudio e vídeo concluídos dentro do FetchJet em todas as plataformas: um botão de reproduzir na linha, «Reproduzir» para uma pasta, playlist ou grupo inteiro, uma fila com anterior / próximo e a lista, velocidades, e segurar para 2x. A última posição é guardada.
- **Controles compactos:** os controles ficam sobre o vídeo e somem depois de 3 segundos. Uma barra de progresso fina, uma única linha pequena com a velocidade, uma chave de **Reprodução automática** e **CC**. Quando um vídeo está pausado, ou o próximo espera com a reprodução automática desligada, um ▶ grande aparece no meio.
- **Reprodução automática:** ligada, o próximo item começa sozinho; desligada, ele é carregado pausado.
- **Legendas:** o menu CC mostra as legendas dentro do vídeo, os arquivos de legenda ao lado dele (seu idioma primeiro) e «Abrir arquivo de legendas…» (SRT, VTT, ASS). A escolha é guardada para cada download. As legendas ficam embaixo e sobem acima dos controles quando eles aparecem. O CC fica preenchido enquanto uma legenda está ativa.
- **Celulares e tablets:** deslize para cima ou para baixo na metade direita para o volume e na metade esquerda para o brilho (só o do player).
- **Computadores:** um controle de volume até 200% com botão de mudo, e tela cheia. No Windows, macOS e Linux a biblioteca do player é baixada só na primeira vez que você reproduz algo, com o progresso na tela, e o arquivo abre sozinho quando ela fica pronta.

**Cortar, extrair e comprimir**
- **Cortar vídeo:** pelo menu da linha ou pelos detalhes, por tempo ou em uma linha do tempo. A linha do tempo funciona como em um editor de vídeo: uma escala de tempo fixa, arrastar ou rolar para mover, zoom de poucos segundos até o vídeo inteiro, alças que rolam a vista nas bordas, miniaturas e uma barra de rolagem fina. O trecho é um arquivo novo listado abaixo do seu vídeo.
- **Cortar áudio:** arquivos de áudio podem ser cortados do mesmo jeito, em uma linha do tempo com a onda sonora. As partes em MP3 são recodificadas com a taxa de bits da original, então nenhum som se perde e o arquivo não cresce.
- **Extrair áudio:** salva a faixa de som de um vídeo como um arquivo separado, sem recodificar.
- **Excluir o original:** enquanto nada mais tiver sido criado a partir de um arquivo, Cortar e Comprimir oferecem «Excluir o original». O novo arquivo passa então a ser o download principal: você pode adicionar legendas (o tempo delas acompanha o trecho), cortá-lo ou comprimi-lo de novo. Remover um download também remove os trechos e cópias feitos a partir dele, e a confirmação diz quantos são.

**Leitor de PDF**
- **Novo:** abra arquivos PDF dentro do FetchJet: páginas uma embaixo da outra, número da página e «Ir para a página», ajustar à largura, zoom, e a última página lida é guardada. Android e iOS usam o mecanismo de PDF do sistema; Windows, macOS e Linux baixam uma pequena biblioteca de PDF na primeira vez. Uma configuração escolhe o leitor do app, o app do sistema ou perguntar.

**Downloads ocultos**
- **Novo:** oculte um download, uma playlist ou um grupo de pasta atrás de um PIN de 4 a 6 dígitos ou de um padrão (Mais > Ocultos). Os itens ocultos saem da lista, da galeria e do gerenciador de arquivos, e seus arquivos vão para uma pasta privada do app. Ocultar é instantâneo e mostra uma linha de progresso enquanto os arquivos são movidos. Ao reexibir, os arquivos voltam para a pasta deles.
- **Áudio e vídeo são criptografados:** o áudio e o vídeo que você oculta são criptografados com uma chave ligada ao dispositivo, os nomes dos arquivos são embaralhados e eles só são descriptografados enquanto você os reproduz no player do app. Os outros tipos de arquivo são apenas tirados de vista. Um aviso explica isso na primeira vez.
- **Sair do app o cobre:** ao voltar, ele pede sua digital, Face ID, Windows Hello ou PIN, e um vídeo oculto que estava tocando é fechado.
- **Novos downloads começam ocultos** quando você os inicia pela tela Ocultos (o botão +). Downloads ocultos não mostram notificação.
- **Esqueceu o PIN:** confirme com o bloqueio do seu celular ou computador e defina um novo PIN. Seus arquivos ocultos continuam lá. Não disponível no Linux.
- **Código de recuperação:** um código para todo o cofre, mostrado uma única vez. Com ele você pode reabrir o áudio e o vídeo ocultos se a chave do dispositivo for perdida, por exemplo depois de restaurar em um celular novo. Excluir o app ou limpar seus dados apaga os arquivos ocultos, então reexiba-os antes de desinstalar.

**Backup e restauração**
- **«Fazer backup dos downloads»** salva em um único arquivo a lista dos seus downloads, as pastas e as configurações, sem senhas nem cookies. **«Restaurar de um backup»** traz tudo de volta e mescla com o que já existe. Os arquivos baixados não estão no backup: os downloads concluídos se reconectam aos seus arquivos, e os não concluídos voltam pausados.

**Navegador e logins**
- **Navegador no app** (Mais > Navegador, Android e iOS): uma barra de endereço, voltar / avançar / recarregar; um arquivo ou vídeo que você abrir ali vai para a janela Adicionar com o login da página.
- **Entrar com o navegador** (Configurações > Rede > Logins de sites): entre em um site uma vez e os downloads dele usam essa sessão. Remover o login do site também apaga os cookies do navegador para ele.

**Downloads**
- **Conexões escolhidas sozinhas:** o FetchJet começa com 4 conexões, adiciona mais enquanto a velocidade ainda sobe e lembra o que funcionou em cada site.
- **Trechos com tempos exatos:** um trecho agora começa e termina exatamente em De e Até, e De / Até aceitam frações de segundo (12.500). Antes, um trecho podia começar atrasado (De 12 começava no fim do segundo 12) ou adiantado (11.5 começava em 11), porque o corte ia para o quadro-chave mais próximo. Isso acontecia em todas as plataformas desde que os trechos foram criados.
- **Retomar mostra uma seta de download** (⬇) em vez de ▶, para não parecer mais que vai reproduzir um vídeo. ▶ agora é só para reproduzir um arquivo concluído.
- **O curso inteiro da Udemy** também traz os arquivos do curso: aulas em PDF, e-book, slides e código, e os recursos anexados às aulas, cada um logo depois da sua aula.
- **Os detalhes** voltam a mostrar o link de origem; um link que carrega um login ou um token mostra uma nota no lugar.
- **Novo ícone** do app em todas as plataformas.

**Segurança**
- Remover um download não concluído ou com falha usando «também excluir os arquivos» não apaga mais um arquivo seu mais antigo com o mesmo nome. Remover um arquivo restaurado de um backup verifica antes se ele é mesmo o arquivo daquele download.
- Um download que não pôde ser movido para o lugar não deixa mais um arquivo cortado com o nome final, e um tamanho errado no fim não joga mais fora as partes já baixadas.
- Arquivos 7z e RAR são verificados antes de serem extraídos (sem links, sem caminhos que saem da pasta, limites de tamanho), e a senha do arquivo não fica mais visível para outros programas.
- A conexão local com a extensão do navegador sempre exige o pareamento; a API de automação só pode ser ativada por você, não pela própria API.
- Configurações que não puderam ser descriptografadas por um momento não substituem mais o pareamento da extensão.

**Android**
- **«Só o trecho» voltou a funcionar:** desde a 1.5.0, «Só o trecho» falhava no Android com «ffmpeg exited with code 8» em todos os sites, porque o ffmpeg menor feito para o app não tinha acesso à rede. O ffmpeg voltou a ler a parte do vídeo direto do site e, se isso falhar algum dia, o app baixa o vídeo inteiro e o corta com exatidão em vez de falhar.
- **Junção de partes de vídeo:** desde a 1.5.0, alguns sites que enviam um vídeo em partes (HLS) falhavam com «dimensions not set». Corrigido.
- Um trecho cortado com exatidão no celular não fica mais muito maior que sua parte do vídeo (um trecho de 5 segundos de um vídeo pequeno tinha 1,6 MB).
- No player, deslizar nas bordas laterais muda o volume ou o brilho em vez de Voltar, e Voltar fecha primeiro um menu aberto antes do player.
- Links abertos de outros apps ou páginas da web sempre mostram primeiro a janela Adicionar, um arquivo torrent aberto de outro app é verificado primeiro, e a pergunta sobre a bateria é feita só uma vez.

**Windows, macOS e Linux**
- As bibliotecas do player e de PDF só são carregadas das pastas do próprio FetchJet, nunca da pasta atual nem do PATH.
- macOS e Linux: atualizações e arquivos temporários usam uma pasta que só você pode abrir, e uma atualização é verificada de novo logo antes de ser instalada.
- A escolha de «Cortar áudio» mostra as duas opções na largura toda.

**iOS**
- **«Só o trecho»:** um trecho podia ser salvo como um arquivo minúsculo que não tocava. O app agora percebe isso, baixa o vídeo inteiro e o corta com exatidão.
- O navegador no app para o som e libera a memória quando você o fecha.

**Extensão do navegador 3.8.0**
- **Abrir no FetchJet:** uma seta ao lado do botão de download da página e uma linha «Abrir no FetchJet» enviam o link da página ao app na hora, e você escolhe a qualidade lá. Isso também funciona em páginas cujas qualidades não podem ser listadas, por exemplo uma aula da Udemy.
- **Abrir o FetchJet diretamente:** uma nova opção entrega os links ao app sem a ponte local.
- **Canva:** cada vídeo em reprodução ganha o botão de download, com o nome da sua página; imagem e som são salvos em um único arquivo.
- **Página inicial do YouTube:** o botão de um vídeo que toca quando você aponta para ele não pisca mais quando você move o mouse até ele, e sempre pertence ao vídeo que está sob o mouse. Antes ele sumia e voltava, e podia mostrar as qualidades do primeiro vídeo em outro.
- **Udemy:** o stream de uma aula recebe o nome da aula em vez de um código aleatório.
- **Mais segura:** só as páginas da própria extensão podem mudar as configurações dela, os cookies de uma página são enviados só para o mesmo site, e a leitura de cursos da Udemy só funciona em udemy.com.

**Detalhes menores**
- Um toque longo em um download na tela Ocultos abre um menu. Todas as listas agora usam o mesmo menu de linha, dividido em seções, com Principal primeiro.
- Android: se um arquivo foi baixado por outra instalação do FetchJet e não pode ser lido, o FetchJet agora abre a página «Permitir acesso a todos os arquivos» em vez de falhar em silêncio.
- A janela Adicionar não tem mais a opção «Baixar como oculto».
