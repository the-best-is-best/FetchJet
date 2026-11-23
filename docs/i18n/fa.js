window.FJ_PAGE_TEXT = window.FJ_PAGE_TEXT || {};
window.FJ_PAGE_TEXT.fa = {
  pageTitle: "دانلودهای FetchJet", title: "دانلودهای FetchJet", subtitle: "همهٔ نسخه‌ها برای همهٔ سیستم‌ها. نسخهٔ خودتان را انتخاب کنید.",
  loading: "در حال بارگذاری…", loadingReleases: "در حال بارگذاری نسخه‌ها از GitHub…",
  footer: 'فایل‌ها در <a href="{releases}">GitHub Releases</a> منتشر می‌شوند. اندروید: APK مناسب پردازندهٔ دستگاه را نصب کنید (اگر مطمئن نیستید <em>arm64-v8a</em>). آیفون و آیپد: یک IPA برای هر دو، برای نصب جانبی (AltStore / Sideloadly). فایرفاکس: از Firefox Add-ons نصب کنید. <a href="privacy.html">سیاست حریم خصوصی</a>',
  winHint: "فایل ‎.msi برنامهٔ FetchJet را نصب می‌کند و اجازه می‌دهد خودش به‌روز شود.",
  winNotice: '<div class="notice"><h3>از Microsoft Store بگیرید (پیشنهادی)</h3>نصب با یک کلیک، بدون هشدار SmartScreen، و ویندوز آن را به‌روز نگه می‌دارد.<div class="stores"><a class="btn" href="https://apps.microsoft.com/detail/9NP3HJRHMPJS">Microsoft Store</a></div><div class="hint">نصب‌کننده می‌خواهید؟ فایل ‎.msi پایین را بگیرید؛ از GitHub خودش به‌روز می‌شود.</div></div>',
  macHint: "فقط برای مک‌های Apple Silicon (M1 یا جدیدتر)؛ مک‌های Intel پشتیبانی نمی‌شوند. فایل ‎.dmg را باز کنید و FetchJet را به Applications بکشید.",
  macNotice: '<div class="notice"><h3>اولین اجرا: "Apple could not verify FetchJet"</h3>FetchJet هنوز توسط Apple تأیید (notarize) نشده، پس macOS اولین اجرا را مسدود می‌کند. یک بار آن را باز کنید، هشدار را ببندید، سپس به <b>System Settings &rarr; Privacy &amp; Security</b> بروید و <b>Open Anyway</b> را بزنید. یا یک بار این را در Terminal اجرا کنید:<div class="hint"><code>xattr -dr com.apple.quarantine /Applications/FetchJet.app</code></div></div>',
  linuxHint: "‎.deb برای Debian/Ubuntu؛ ‎.tar.gz همه‌جا اجرا می‌شود (باز کنید و bin/FetchJet را اجرا کنید).",
  androidHint: "arm64-v8a تقریباً برای هر گوشی ساخته‌شده بعد از ۲۰۱۷ مناسب است. گوشی‌های قدیمی ۳۲ بیتی: armeabi-v7a. شبیه‌سازها و Chromebook: x86_64.",
  iosLabel: "آیفون · آیپد", iosHint: "یک IPA برای آیفون و آیپد. امضا نشده: با AltStore یا Sideloadly نصب جانبی کنید.",
  extLabel: "افزونهٔ مرورگر", extHint: "‎.zip = Chrome / Edge / Brave (به‌صورت Load unpacked بارگذاری کنید یا روی chrome://extensions بکشید).",
  extNotice: '<div class="notice"><h3>از فروشگاه نصب کنید (پیشنهادی)</h3>به‌روزرسانی‌ها خودکار نصب می‌شوند و مرورگر هیچ هشداری نشان نمی‌دهد.<div class="stores"><a class="btn" href="https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/">Firefox Add-ons</a><a class="btn" href="https://microsoftedge.microsoft.com/addons/detail/phngekpnkcnpkjkcoggcfmlmebmociaj">Microsoft Edge Add-ons</a></div><div class="hint">Chrome، Brave، Opera و Vivaldi: FetchJet در Chrome Web Store نیست، پس از فایل ‎.zip پایین استفاده کنید.</div></div><div class="notice warn"><h3>⚠ نصب فایل ‎.zip پایین (حالت توسعه‌دهنده)</h3>فایل ‎.zip برای Chrome / Edge / Brave است وقتی نمی‌توانید از فروشگاه استفاده کنید. مرورگر آن را افزونهٔ توسعه‌دهنده می‌داند: ممکن است هنگام شروع هشدار "developer mode extensions" نشان دهد و خودش به‌روز <b>نمی‌شود</b> - برای نسخه‌های جدید به اینجا برگردید.<ol><li>فایل <code>.zip</code> را دانلود کنید و در پوشه‌ای که نگه می‌دارید باز کنید (بعداً حذفش نکنید).</li><li><code>chrome://extensions</code> را باز کنید (در Edge: <code>edge://extensions</code>).</li><li><b>Developer mode</b> را روشن کنید.</li><li><b>Load unpacked</b> را بزنید و پوشهٔ باز شده را انتخاب کنید.</li></ol></div>',
  noBuild: "هنوز نسخه‌ای برای {p} منتشر نشده است.", latestFor: "جدیدترین برای {p}: {v}",
  skipped: "نسخه‌های جدیدتر ({list}) تغییری برای {p} نداشتند", releaseNotes: "یادداشت‌های انتشار", whatsNew: "تازه‌های {v}",
  hideOlder: "پنهان کردن نسخه‌های قدیمی‌تر", showOlder: "نمایش نسخه‌های قدیمی‌تر ({n})", preRelease: "پیش‌انتشار", beta: "بتا",
  whatChanged: "چه چیزی تغییر کرد", onGitHub: "در GitHub", release1: "۱ نسخه", releasesN: "{n} نسخه", offline: "آفلاین",
  loadError: 'فهرست نسخه‌ها بارگذاری نشد ({e}). به‌جای آن <a href="{releases}">صفحهٔ نسخه‌ها در GitHub</a> را باز کنید.',
  fileMsi: "نصب‌کننده (.msi)", fileZip: "قابل حمل، بدون نصب (.zip)", fileExe: "راه‌اندازی (.exe)", fileDeb: "Debian / Ubuntu (.deb)",
  fileTar: "قابل حمل (.tar.gz)", fileDmg: "Apple Silicon (.dmg)", fileXpi: "Firefox (.xpi)", fileExtZip: "Chrome / Edge (.zip)"
};
