window.FJ_PRIVACY = window.FJ_PRIVACY || {};
window.FJ_PRIVACY.ja = { title: "FetchJet プライバシーポリシー", html: `
<h1>プライバシーポリシー</h1>
<p class="date">FetchJet アプリとブラウザー拡張機能 FetchJet Download Manager · 最終更新 2026 年 11 月 16 日</p>
<p class="date">これは英語のポリシーの翻訳です。内容に違いがある場合は英語版が優先されます。</p>
<h2>概要</h2>
<p>FetchJet は個人データを収集しません。アカウントもトラッキングもありません。あなたやあなたの閲覧に関する情報が開発者や第三者に送られることはありません。アプリが送るのは、下に説明する 1 日 1 回の匿名の利用カウントだけです。ブラウザー拡張機能は開発者に何も送りません。</p>
<h2>ブラウザー拡張機能</h2>
<p>リンク、動画、ページのダウンロードを選ぶと、拡張機能はそのリンク、referer、そのサイトの Cookie を<strong>あなた自身のデバイス上の FetchJet アプリだけに</strong>送ります（アドレス 127.0.0.1、ポート 28450）。Cookie はログインが必要なダウンロードをブラウザーと同じように動かすために使われます。拡張機能を通じてあなたのデバイスの外に出ることはありません。</p>
<p>拡張機能の設定（キャプチャのオン/オフ、ファイルの種類、最小サイズ、除外するサイト）はブラウザーの拡張機能用ローカルストレージに保存されます。</p>
<h2>FetchJet アプリ</h2>
<p>アプリが接続するのは、ダウンロード元のサイトと、新しいバージョンを確認するための GitHub だけです（このリクエストにはアプリのバージョンしか含まれません）。ダウンロード、設定、履歴はあなたのデバイスに残ります。</p>
<p><strong>匿名の利用カウント。</strong>アプリは 1 日 1 回、開発者が運用するサーバー（Cloudflare Workers）に小さなメッセージを送り、開発者が FetchJet の利用者数を把握できるようにします。メッセージには、デバイス上で作られたランダムな ID、アプリのバージョン、プラットフォーム（Windows、macOS、Linux、Android、iOS）が含まれます。国は Cloudflare が接続から判断します。開発者があなたの IP アドレス、名前、メール、リンク、ファイル名、ダウンロードしたものを保存することはありません。合計は<a href="https://fetchjet.the-best-is-best.workers.dev/dashboard">統計ページ</a>で公開しています。アプリを起動する前に環境変数 <code>FETCHJET_NO_USAGE_PING=1</code> を設定すると、これをオフにできます。</p>
<h2>変更とお問い合わせ</h2>
<p>このポリシーが変わる場合、新しい版をこのページで公開します。お問い合わせ：<a href="mailto:eng.michelle.raouf@gmail.com">eng.michelle.raouf@gmail.com</a></p>
<footer><a href="./?lang=ja">FetchJet ダウンロード</a></footer>
` };
