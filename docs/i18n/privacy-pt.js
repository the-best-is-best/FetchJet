window.FJ_PRIVACY = window.FJ_PRIVACY || {};
window.FJ_PRIVACY.pt = { title: "Política de privacidade do FetchJet", html: `
<h1>Política de privacidade</h1>
<p class="date">App FetchJet e extensão de navegador FetchJet Download Manager · última atualização em 16 de novembro de 2026</p>
<p class="date">Esta é uma tradução da política em inglês. Em caso de diferença, vale a versão em inglês.</p>
<h2>Resumo</h2>
<p>O FetchJet não coleta dados pessoais. Não há contas nem rastreamento. Nada sobre você ou sua navegação é enviado ao desenvolvedor ou a terceiros. A única coisa que o app envia é uma contagem de uso anônima por dia, descrita abaixo. A extensão do navegador não envia nada ao desenvolvedor.</p>
<h2>A extensão do navegador</h2>
<p>Quando você escolhe baixar um link, vídeo ou página, a extensão envia esse link, o referer e os cookies desse site <strong>somente para o app FetchJet no seu próprio dispositivo</strong> (endereço 127.0.0.1, porta 28450). Os cookies são usados para que downloads que exigem login funcionem exatamente como no navegador. Eles nunca saem do seu dispositivo pela extensão.</p>
<p>As configurações da extensão (captura ligada/desligada, tipos de arquivo, tamanho mínimo, sites excluídos) ficam no armazenamento local da extensão no navegador.</p>
<h2>O app FetchJet</h2>
<p>O app se conecta apenas aos sites dos quais você baixa, e ao GitHub para verificar se há uma nova versão (essa solicitação contém a versão do app, nada mais). Downloads, configurações e histórico ficam no seu dispositivo.</p>
<p><strong>Contagem de uso anônima.</strong> Uma vez por dia o app envia uma pequena mensagem a um servidor do desenvolvedor (Cloudflare Workers) para que ele veja quantas pessoas usam o FetchJet. A mensagem contém um ID aleatório gerado no seu dispositivo, a versão do app e a plataforma (Windows, macOS, Linux, Android ou iOS). O país é identificado pela Cloudflare a partir da conexão. O desenvolvedor não guarda seu endereço IP, nome, e-mail, links, nomes de arquivos nem nada que você baixa. Os totais são públicos na <a href="https://fetchjet.the-best-is-best.workers.dev/dashboard">página de estatísticas</a>. Você pode desativar isso definindo a variável de ambiente <code>FETCHJET_NO_USAGE_PING=1</code> antes de abrir o app.</p>
<h2>Mudanças e contato</h2>
<p>Se esta política mudar, a nova versão será publicada nesta página. Dúvidas: <a href="mailto:eng.michelle.raouf@gmail.com">eng.michelle.raouf@gmail.com</a>.</p>
<footer><a href="./?lang=pt">Downloads do FetchJet</a></footer>
` };
