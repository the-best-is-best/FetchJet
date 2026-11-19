window.FJ_PAGE_TEXT = window.FJ_PAGE_TEXT || {};
window.FJ_PAGE_TEXT.pt = {
  pageTitle: "Downloads do FetchJet", title: "Downloads do FetchJet", subtitle: "Todas as versões, para todas as plataformas. Escolha a sua.",
  loading: "carregando…", loadingReleases: "Carregando versões do GitHub…",
  footer: 'Os arquivos são publicados no <a href="{releases}">GitHub Releases</a>. Android: instale o APK do seu processador (<em>arm64-v8a</em> se não tiver certeza). iPhone e iPad: um único IPA para os dois, para sideload (AltStore / Sideloadly). Firefox: instale pelo Firefox Add-ons. <a href="privacy.html">Política de privacidade</a>',
  winHint: "O .msi instala o FetchJet e deixa ele se atualizar sozinho.",
  winNotice: '<div class="notice"><h3>Baixe na Microsoft Store (recomendado)</h3>Instalação com um clique, sem aviso do SmartScreen, e o Windows mantém atualizado.<div class="stores"><a class="btn" href="https://apps.microsoft.com/detail/9NP3HJRHMPJS">Microsoft Store</a></div><div class="hint">Prefere um instalador? Use o .msi abaixo; ele se atualiza pelo GitHub.</div></div>',
  macHint: "Somente para Macs com Apple Silicon (M1 ou mais novo); Macs Intel não são compatíveis. Abra o .dmg e arraste o FetchJet para Aplicativos.",
  macNotice: '<div class="notice"><h3>Primeira abertura: "Apple could not verify FetchJet"</h3>O FetchJet ainda não é notarizado pela Apple, então o macOS bloqueia a primeira abertura. Abra uma vez, feche o aviso, depois vá em <b>Ajustes do Sistema &rarr; Privacidade e Segurança</b> e clique em <b>Abrir Mesmo Assim</b>. Ou execute isto uma vez no Terminal:<div class="hint"><code>xattr -dr com.apple.quarantine /Applications/FetchJet.app</code></div></div>',
  linuxHint: ".deb para Debian/Ubuntu; o .tar.gz roda em qualquer lugar (descompacte e inicie bin/FetchJet).",
  androidHint: "arm64-v8a serve para quase todo celular feito depois de 2017. Celulares antigos de 32 bits: armeabi-v7a. Emuladores e Chromebooks: x86_64.",
  iosLabel: "iPhone · iPad", iosHint: "Um único IPA para iPhone e iPad. Sem assinatura: instale com AltStore ou Sideloadly.",
  extLabel: "Extensão do navegador", extHint: ".zip = Chrome / Edge / Brave (carregue sem compactação ou arraste para chrome://extensions).",
  extNotice: '<div class="notice"><h3>Instale por uma loja (recomendado)</h3>As atualizações se instalam sozinhas e o navegador não mostra avisos.<div class="stores"><a class="btn" href="https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/">Firefox Add-ons</a><a class="btn" href="https://microsoftedge.microsoft.com/addons/detail/phngekpnkcnpkjkcoggcfmlmebmociaj">Microsoft Edge Add-ons</a></div><div class="hint">Chrome, Brave, Opera e Vivaldi: o FetchJet não está na Chrome Web Store, então use o .zip abaixo.</div></div><div class="notice warn"><h3>⚠ Instalando o .zip abaixo (modo desenvolvedor)</h3>O .zip é para Chrome / Edge / Brave quando você não pode usar uma loja. O navegador trata como extensão de desenvolvedor: pode mostrar um aviso de "developer mode extensions" ao iniciar, e ela <b>não</b> se atualiza sozinha - volte aqui para novas versões.<ol><li>Baixe o <code>.zip</code> e descompacte numa pasta que você vai manter (não apague depois).</li><li>Abra <code>chrome://extensions</code> (Edge: <code>edge://extensions</code>).</li><li>Ative o <b>Modo do desenvolvedor</b>.</li><li>Clique em <b>Carregar sem compactação</b> e escolha a pasta descompactada.</li></ol></div>',
  noBuild: "Nenhuma versão para {p} foi publicada ainda.", latestFor: "Mais recente para {p}: {v}",
  skipped: "versões mais novas ({list}) não tiveram mudanças para {p}", releaseNotes: "Notas da versão", whatsNew: "Novidades da {v}",
  hideOlder: "Ocultar versões antigas", showOlder: "Mostrar versões antigas ({n})", preRelease: "pré-lançamento", beta: "beta",
  whatChanged: "o que mudou", onGitHub: "no GitHub", release1: "1 versão", releasesN: "{n} versões", offline: "offline",
  loadError: 'Não foi possível carregar a lista de versões ({e}). Abra <a href="{releases}">a página de versões no GitHub</a>.',
  fileMsi: "Instalador (.msi)", fileZip: "Portátil, sem instalação (.zip)", fileExe: "Instalação (.exe)", fileDeb: "Debian / Ubuntu (.deb)",
  fileTar: "Portátil (.tar.gz)", fileDmg: "Apple Silicon (.dmg)", fileXpi: "Firefox (.xpi)", fileExtZip: "Chrome / Edge (.zip)"
};
