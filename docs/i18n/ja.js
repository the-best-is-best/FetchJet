window.FJ_PAGE_TEXT = window.FJ_PAGE_TEXT || {};
window.FJ_PAGE_TEXT.ja = {
  pageTitle: "FetchJet ダウンロード", title: "FetchJet ダウンロード", subtitle: "すべてのプラットフォームのすべてのバージョン。使うものを選んでください。",
  loading: "読み込み中…", loadingReleases: "GitHub からリリースを読み込み中…",
  footer: 'ファイルは <a href="{releases}">GitHub Releases</a> で公開しています。Android：お使いの CPU 用の APK をインストールしてください（わからない場合は <em>arm64-v8a</em>）。iPhone と iPad：両方で使える IPA が 1 つ、サイドロード用です（AltStore / Sideloadly）。Firefox：Firefox Add-ons からインストールしてください。<a href="privacy.html">プライバシーポリシー</a>',
  winHint: ".msi は FetchJet をインストールし、自動で更新できるようにします。",
  winNotice: '<div class="notice"><h3>Microsoft Store から入手（おすすめ）</h3>ワンクリックでインストール、SmartScreen の警告なし、Windows が最新に保ちます。<div class="stores"><a class="btn" href="https://apps.microsoft.com/detail/9NP3HJRHMPJS">Microsoft Store</a></div><div class="hint">インストーラーがよい場合は下の .msi を使ってください。GitHub から自動更新されます。</div></div>',
  macHint: "Apple Silicon の Mac（M1 以降）専用です。Intel Mac には対応していません。.dmg を開いて FetchJet を「アプリケーション」にドラッグしてください。",
  macNotice: '<div class="notice"><h3>初回起動：「Apple could not verify FetchJet」</h3>FetchJet はまだ Apple の公証を受けていないため、macOS が初回起動をブロックします。一度開いて警告を閉じ、<b>システム設定 &rarr; プライバシーとセキュリティ</b>で<b>このまま開く</b>をクリックしてください。またはターミナルで一度だけ実行します：<div class="hint"><code>xattr -dr com.apple.quarantine /Applications/FetchJet.app</code></div></div>',
  linuxHint: "Debian/Ubuntu は .deb、.tar.gz はどこでも動きます（展開して bin/FetchJet を起動）。",
  androidHint: "arm64-v8a は 2017 年以降のほぼすべてのスマホに対応します。古い 32 ビットのスマホ：armeabi-v7a。エミュレーターと Chromebook：x86_64。",
  iosLabel: "iPhone · iPad", iosHint: "iPhone と iPad で共通の IPA が 1 つ。未署名です：AltStore か Sideloadly でサイドロードしてください。",
  extLabel: "ブラウザー拡張機能", extHint: ".zip = Chrome / Edge / Brave（パッケージ化されていない拡張機能として読み込むか chrome://extensions にドラッグ）。",
  extNotice: '<div class="notice"><h3>ストアからインストール（おすすめ）</h3>更新は自動でインストールされ、ブラウザーに警告は表示されません。<div class="stores"><a class="btn" href="https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/">Firefox Add-ons</a><a class="btn" href="https://microsoftedge.microsoft.com/addons/detail/phngekpnkcnpkjkcoggcfmlmebmociaj">Microsoft Edge Add-ons</a></div><div class="hint">Chrome、Brave、Opera、Vivaldi：FetchJet は Chrome Web Store にないため、下の .zip を使ってください。</div></div><div class="notice warn"><h3>⚠ 下の .zip をインストールする（デベロッパーモード）</h3>.zip はストアを使えない場合の Chrome / Edge / Brave 用です。ブラウザーはデベロッパー拡張機能として扱うため、起動時に「developer mode extensions」の警告が出ることがあり、自動更新<b>されません</b>。新しいバージョンはここに戻って入手してください。<ol><li><code>.zip</code> をダウンロードし、残しておくフォルダーに展開します（あとで削除しないでください）。</li><li><code>chrome://extensions</code> を開きます（Edge：<code>edge://extensions</code>）。</li><li><b>デベロッパーモード</b>をオンにします。</li><li><b>パッケージ化されていない拡張機能を読み込む</b>をクリックし、展開したフォルダーを選びます。</li></ol></div>',
  noBuild: "{p} 向けのビルドはまだ公開されていません。", latestFor: "{p} の最新版：{v}",
  skipped: "新しいリリース（{list}）には {p} の変更はありません", releaseNotes: "リリースノート", whatsNew: "{v} の新機能",
  hideOlder: "古いバージョンを隠す", showOlder: "古いバージョンを表示（{n}）", preRelease: "プレリリース", beta: "ベータ",
  whatChanged: "変更点", onGitHub: "GitHub で見る", release1: "1 リリース", releasesN: "{n} リリース", offline: "オフライン",
  loadError: 'リリース一覧を読み込めませんでした（{e}）。代わりに <a href="{releases}">GitHub のリリースページ</a>を開いてください。',
  fileMsi: "インストーラー (.msi)", fileZip: "ポータブル、インストール不要 (.zip)", fileExe: "セットアップ (.exe)", fileDeb: "Debian / Ubuntu (.deb)",
  fileTar: "ポータブル (.tar.gz)", fileDmg: "Apple Silicon (.dmg)", fileXpi: "Firefox (.xpi)", fileExtZip: "Chrome / Edge (.zip)"
};
