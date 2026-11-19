window.FJ_PRIVACY = window.FJ_PRIVACY || {};
window.FJ_PRIVACY.zh = { title: "FetchJet 隐私政策", html: `
<h1>隐私政策</h1>
<p class="date">FetchJet 应用和 FetchJet Download Manager 浏览器扩展 · 最后更新于 2026 年 11 月 16 日</p>
<p class="date">本文是英文政策的翻译。如有不一致，以英文版本为准。</p>
<h2>概要</h2>
<p>FetchJet 不收集个人数据。没有账号，也没有跟踪。关于你或你的浏览的任何信息都不会发送给开发者或任何第三方。应用唯一发送的内容是每天一次的匿名使用计数，详见下文。浏览器扩展不会向开发者发送任何内容。</p>
<h2>浏览器扩展</h2>
<p>当你选择下载某个链接、视频或页面时，扩展会把该链接、referer 和该网站的 Cookie <strong>只发送给你自己设备上的 FetchJet 应用</strong>（地址 127.0.0.1，端口 28450）。使用 Cookie 是为了让需要登录的下载像在浏览器里一样正常工作。它们绝不会通过扩展离开你的设备。</p>
<p>扩展的设置（捕获开关、文件类型、最小大小、排除的网站）保存在浏览器的本地扩展存储中。</p>
<h2>FetchJet 应用</h2>
<p>应用只会连接你下载所用的网站，以及 GitHub 以检查是否有新版本（该请求只包含应用版本号）。下载、设置和历史记录都保留在你的设备上。</p>
<p><strong>匿名使用计数。</strong>应用每天向开发者运营的服务器（Cloudflare Workers）发送一条小消息，让开发者了解有多少人在使用 FetchJet。消息包含在你设备上生成的随机 ID、应用版本和平台（Windows、macOS、Linux、Android 或 iOS）。国家由 Cloudflare 根据连接判断。开发者不会保存你的 IP 地址、姓名、邮箱、链接、文件名或你下载的任何内容。总数公开显示在<a href="https://fetchjet.the-best-is-best.workers.dev/dashboard">统计页面</a>上。你可以在启动应用前设置环境变量 <code>FETCHJET_NO_USAGE_PING=1</code> 来关闭它。</p>
<h2>变更与联系</h2>
<p>如果本政策发生变更，新版本将发布在此页面。问题请联系：<a href="mailto:eng.michelle.raouf@gmail.com">eng.michelle.raouf@gmail.com</a>。</p>
<footer><a href="./?lang=zh">FetchJet 下载</a></footer>
` };
