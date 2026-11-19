window.FJ_PAGE_TEXT = window.FJ_PAGE_TEXT || {};
window.FJ_PAGE_TEXT.ar = {
  pageTitle: "تنزيلات FetchJet", title: "تنزيلات FetchJet", subtitle: "كل الإصدارات لكل الأنظمة. اختر ما يناسبك.",
  loading: "جارٍ التحميل…", loadingReleases: "جارٍ تحميل الإصدارات من GitHub…",
  footer: 'تُنشر الملفات على <a href="{releases}">GitHub Releases</a>. أندرويد: ثبّت ملف APK المناسب لمعالج جهازك (<em>arm64-v8a</em> إن لم تكن متأكدًا). آيفون وآيباد: ملف IPA واحد للجهازين، للتثبيت الجانبي (AltStore / Sideloadly). فايرفوكس: ثبّته من Firefox Add-ons. <a href="privacy.html">سياسة الخصوصية</a>',
  winHint: "ملف ‎.msi يثبّت FetchJet ويتيح له تحديث نفسه.",
  winNotice: '<div class="notice"><h3>احصل عليه من Microsoft Store (موصى به)</h3>تثبيت بنقرة واحدة، دون تحذير SmartScreen، وويندوز يحدّثه تلقائيًا.<div class="stores"><a class="btn" href="https://apps.microsoft.com/detail/9NP3HJRHMPJS">Microsoft Store</a></div><div class="hint">تفضّل مثبّتًا؟ خذ ملف ‎.msi بالأسفل؛ فهو يحدّث نفسه من GitHub.</div></div>',
  macHint: "لأجهزة Mac بمعالجات Apple Silicon فقط (M1 أو أحدث)؛ أجهزة Intel غير مدعومة. افتح ملف ‎.dmg واسحب FetchJet إلى Applications.",
  macNotice: '<div class="notice"><h3>أول تشغيل: "Apple could not verify FetchJet"</h3>FetchJet غير موثّق من Apple بعد، لذلك يمنع macOS أول تشغيل. افتحه مرة، وأغلق التحذير، ثم اذهب إلى <b>System Settings &rarr; Privacy &amp; Security</b> واضغط <b>Open Anyway</b>. أو نفّذ هذا مرة واحدة في Terminal:<div class="hint"><code>xattr -dr com.apple.quarantine /Applications/FetchJet.app</code></div></div>',
  linuxHint: "‎.deb لـ Debian/Ubuntu؛ وملف ‎.tar.gz يعمل في أي مكان (فكّه وشغّل bin/FetchJet).",
  androidHint: "arm64-v8a يناسب تقريبًا كل هاتف صُنع بعد 2017. الهواتف الأقدم ذات 32 بت: armeabi-v7a. المحاكيات وأجهزة Chromebook: x86_64.",
  iosLabel: "آيفون · آيباد", iosHint: "ملف IPA واحد لآيفون وآيباد. غير موقّع: ثبّته جانبيًا بـ AltStore أو Sideloadly.",
  extLabel: "إضافة المتصفح", extHint: "‎.zip = Chrome / Edge / Brave (حمّلها غير مضغوطة أو اسحبها إلى chrome://extensions).",
  extNotice: '<div class="notice"><h3>ثبّتها من متجر (موصى به)</h3>التحديثات تُثبَّت تلقائيًا والمتصفح لا يعرض أي تحذيرات.<div class="stores"><a class="btn" href="https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/">Firefox Add-ons</a><a class="btn" href="https://microsoftedge.microsoft.com/addons/detail/phngekpnkcnpkjkcoggcfmlmebmociaj">Microsoft Edge Add-ons</a></div><div class="hint">Chrome وBrave وOpera وVivaldi: FetchJet غير موجود في Chrome Web Store، فاستخدم ملف ‎.zip بالأسفل.</div></div><div class="notice warn"><h3>⚠ تثبيت ملف ‎.zip بالأسفل (وضع المطوّر)</h3>ملف ‎.zip مخصّص لـ Chrome / Edge / Brave عندما لا يمكنك استخدام متجر. يعامله المتصفح كإضافة مطوّر: قد يعرض تحذير "developer mode extensions" عند البدء، و<b>لا</b> يحدّث نفسه - عُد إلى هنا للإصدارات الجديدة.<ol><li>نزّل ملف <code>.zip</code> وفك ضغطه في مجلد ستحتفظ به (لا تحذفه بعد ذلك).</li><li>افتح <code>chrome://extensions</code> (في Edge: <code>edge://extensions</code>).</li><li>شغّل <b>Developer mode</b>.</li><li>اضغط <b>Load unpacked</b> واختر المجلد المفكوك.</li></ol></div>',
  noBuild: "لم يُنشر أي إصدار لـ {p} بعد.", latestFor: "أحدث إصدار لـ {p}: {v}",
  skipped: "الإصدارات الأحدث ({list}) لم تتضمن تغييرات لـ {p}", releaseNotes: "ملاحظات الإصدار", whatsNew: "الجديد في {v}",
  hideOlder: "إخفاء الإصدارات الأقدم", showOlder: "عرض الإصدارات الأقدم ({n})", preRelease: "إصدار تجريبي مسبق", beta: "تجريبي",
  whatChanged: "ما الذي تغيّر", onGitHub: "على GitHub", release1: "إصدار واحد", releasesN: "{n} إصدارات", offline: "غير متصل",
  loadError: 'تعذّر تحميل قائمة الإصدارات ({e}). افتح <a href="{releases}">صفحة الإصدارات على GitHub</a> بدلًا من ذلك.',
  fileMsi: "المثبّت (.msi)", fileZip: "محمول، بلا تثبيت (.zip)", fileExe: "الإعداد (.exe)", fileDeb: "Debian / Ubuntu (.deb)",
  fileTar: "محمول (.tar.gz)", fileDmg: "Apple Silicon (.dmg)", fileXpi: "Firefox (.xpi)", fileExtZip: "Chrome / Edge (.zip)"
};
