window.FJ_PAGE_TEXT = window.FJ_PAGE_TEXT || {};
window.FJ_PAGE_TEXT.zh = {
  pageTitle: "FetchJet 下载", title: "FetchJet 下载", subtitle: "所有平台的所有版本，选择适合你的。",
  loading: "加载中…", loadingReleases: "正在从 GitHub 加载版本…",
  footer: '文件发布在 <a href="{releases}">GitHub Releases</a>。Android：安装适合你处理器的 APK（不确定就选 <em>arm64-v8a</em>）。iPhone 和 iPad：两者共用一个 IPA，用于侧载（AltStore / Sideloadly）。Firefox：从 Firefox Add-ons 安装。<a href="privacy.html">隐私政策</a>',
  winHint: ".msi 会安装 FetchJet，并让它自动更新。",
  winNotice: '<div class="notice"><h3>从 Microsoft Store 获取（推荐）</h3>一键安装，没有 SmartScreen 警告，Windows 会自动保持更新。<div class="stores"><a class="btn" href="https://apps.microsoft.com/detail/9NP3HJRHMPJS">Microsoft Store</a></div><div class="hint">更喜欢安装程序？使用下面的 .msi，它会从 GitHub 自动更新。</div></div>',
  macHint: "仅适用于 Apple Silicon Mac（M1 或更新）；不支持 Intel Mac。打开 .dmg，把 FetchJet 拖到“应用程序”。",
  macNotice: '<div class="notice"><h3>首次打开：“Apple could not verify FetchJet”</h3>FetchJet 还没有经过 Apple 公证，所以 macOS 会阻止首次打开。先打开一次并关闭警告，然后前往<b>系统设置 &rarr; 隐私与安全性</b>，点击<b>仍要打开</b>。或者在“终端”里运行一次：<div class="hint"><code>xattr -dr com.apple.quarantine /Applications/FetchJet.app</code></div></div>',
  linuxHint: ".deb 适用于 Debian/Ubuntu；.tar.gz 可在任何系统运行（解压后启动 bin/FetchJet）。",
  androidHint: "arm64-v8a 适合几乎所有 2017 年以后的手机。较旧的 32 位手机：armeabi-v7a。模拟器和 Chromebook：x86_64。",
  iosLabel: "iPhone · iPad", iosHint: "iPhone 和 iPad 共用一个 IPA。未签名：用 AltStore 或 Sideloadly 侧载。",
  extLabel: "浏览器扩展", extHint: ".zip = Chrome / Edge / Brave（加载已解压的扩展，或拖到 chrome://extensions）。",
  extNotice: '<div class="notice"><h3>从商店安装（推荐）</h3>更新会自动安装，浏览器不会显示任何警告。<div class="stores"><a class="btn" href="https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/">Firefox Add-ons</a><a class="btn" href="https://microsoftedge.microsoft.com/addons/detail/phngekpnkcnpkjkcoggcfmlmebmociaj">Microsoft Edge Add-ons</a></div><div class="hint">Chrome、Brave、Opera 和 Vivaldi：FetchJet 不在 Chrome Web Store 中，请使用下面的 .zip。</div></div><div class="notice warn"><h3>⚠ 安装下面的 .zip（开发者模式）</h3>.zip 适用于无法使用商店时的 Chrome / Edge / Brave。浏览器会把它当作开发者扩展：启动时可能显示“developer mode extensions”警告，而且它<b>不会</b>自动更新——新版本请回到这里下载。<ol><li>下载 <code>.zip</code> 并解压到一个会保留的文件夹（之后不要删除）。</li><li>打开 <code>chrome://extensions</code>（Edge：<code>edge://extensions</code>）。</li><li>打开<b>开发者模式</b>。</li><li>点击<b>加载已解压的扩展程序</b>，选择解压后的文件夹。</li></ol></div>',
  noBuild: "{p} 还没有发布任何版本。", latestFor: "{p} 最新版本：{v}",
  skipped: "更新的版本（{list}）对 {p} 没有改动", releaseNotes: "版本说明", whatsNew: "{v} 的新内容",
  hideOlder: "隐藏旧版本", showOlder: "显示旧版本（{n}）", preRelease: "预发布", beta: "测试版",
  whatChanged: "改动内容", onGitHub: "在 GitHub 上", release1: "1 个版本", releasesN: "{n} 个版本", offline: "离线",
  loadError: '无法加载版本列表（{e}）。请改为打开 <a href="{releases}">GitHub 上的版本页面</a>。',
  fileMsi: "安装程序 (.msi)", fileZip: "便携版，无需安装 (.zip)", fileExe: "安装向导 (.exe)", fileDeb: "Debian / Ubuntu (.deb)",
  fileTar: "便携版 (.tar.gz)", fileDmg: "Apple Silicon (.dmg)", fileXpi: "Firefox (.xpi)", fileExtZip: "Chrome / Edge (.zip)"
};
