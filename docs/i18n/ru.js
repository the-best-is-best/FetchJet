window.FJ_PAGE_TEXT = window.FJ_PAGE_TEXT || {};
window.FJ_PAGE_TEXT.ru = {
  pageTitle: "Загрузки FetchJet", title: "Загрузки FetchJet", subtitle: "Все версии для всех платформ. Выберите свою.",
  loading: "загрузка…", loadingReleases: "Загрузка версий с GitHub…",
  footer: 'Файлы публикуются на <a href="{releases}">GitHub Releases</a>. Android: установите APK для вашего процессора (если не уверены, <em>arm64-v8a</em>). iPhone и iPad: один IPA для обоих, для установки в обход App Store (AltStore / Sideloadly). Firefox: установите из Firefox Add-ons. <a href="privacy.html">Политика конфиденциальности</a>',
  winHint: ".msi устанавливает FetchJet и позволяет ему обновляться самому.",
  winNotice: '<div class="notice"><h3>Установите из Microsoft Store (рекомендуется)</h3>Установка в один клик, без предупреждения SmartScreen, и Windows сама его обновляет.<div class="stores"><a class="btn" href="https://apps.microsoft.com/detail/9NP3HJRHMPJS">Microsoft Store</a></div><div class="hint">Нужен установщик? Возьмите .msi ниже; он обновляется с GitHub.</div></div>',
  macHint: "Только для Mac на Apple Silicon (M1 и новее); Mac на Intel не поддерживаются. Откройте .dmg и перетащите FetchJet в «Программы».",
  macNotice: '<div class="notice"><h3>Первый запуск: «Apple could not verify FetchJet»</h3>FetchJet ещё не заверен Apple, поэтому macOS блокирует первый запуск. Откройте его один раз, закройте предупреждение, затем откройте <b>Системные настройки &rarr; Конфиденциальность и безопасность</b> и нажмите <b>Всё равно открыть</b>. Или один раз выполните в Терминале:<div class="hint"><code>xattr -dr com.apple.quarantine /Applications/FetchJet.app</code></div></div>',
  linuxHint: ".deb для Debian/Ubuntu; .tar.gz работает везде (распакуйте и запустите bin/FetchJet).",
  androidHint: "arm64-v8a подходит почти для всех телефонов, выпущенных после 2017 года. Старые 32-битные телефоны: armeabi-v7a. Эмуляторы и Chromebook: x86_64.",
  iosLabel: "iPhone · iPad", iosHint: "Один IPA для iPhone и iPad. Без подписи: установите через AltStore или Sideloadly.",
  extLabel: "Расширение браузера", extHint: ".zip = Chrome / Edge / Brave (загрузите распакованное или перетащите на chrome://extensions).",
  extNotice: '<div class="notice"><h3>Установите из магазина (рекомендуется)</h3>Обновления устанавливаются сами, и браузер не показывает предупреждений.<div class="stores"><a class="btn" href="https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/">Firefox Add-ons</a><a class="btn" href="https://microsoftedge.microsoft.com/addons/detail/phngekpnkcnpkjkcoggcfmlmebmociaj">Microsoft Edge Add-ons</a></div><div class="hint">Chrome, Brave, Opera и Vivaldi: FetchJet нет в Chrome Web Store, поэтому используйте .zip ниже.</div></div><div class="notice warn"><h3>⚠ Установка .zip ниже (режим разработчика)</h3>.zip нужен для Chrome / Edge / Brave, когда нельзя использовать магазин. Браузер считает его расширением разработчика: при запуске может появиться предупреждение «developer mode extensions», и оно <b>не</b> обновляется само - за новыми версиями возвращайтесь сюда.<ol><li>Скачайте <code>.zip</code> и распакуйте в папку, которую сохраните (потом не удаляйте).</li><li>Откройте <code>chrome://extensions</code> (Edge: <code>edge://extensions</code>).</li><li>Включите <b>Режим разработчика</b>.</li><li>Нажмите <b>Загрузить распакованное</b> и выберите распакованную папку.</li></ol></div>',
  noBuild: "Для {p} ещё не опубликовано ни одной версии.", latestFor: "Последняя для {p}: {v}",
  skipped: "в более новых версиях ({list}) не было изменений для {p}", releaseNotes: "Примечания к выпуску", whatsNew: "Что нового в {v}",
  hideOlder: "Скрыть старые версии", showOlder: "Показать старые версии ({n})", preRelease: "предварительная", beta: "бета",
  whatChanged: "что изменилось", onGitHub: "на GitHub", release1: "1 версия", releasesN: "версий: {n}", offline: "нет сети",
  loadError: 'Не удалось загрузить список версий ({e}). Откройте вместо этого <a href="{releases}">страницу версий на GitHub</a>.',
  fileMsi: "Установщик (.msi)", fileZip: "Портативная, без установки (.zip)", fileExe: "Установка (.exe)", fileDeb: "Debian / Ubuntu (.deb)",
  fileTar: "Портативная (.tar.gz)", fileDmg: "Apple Silicon (.dmg)", fileXpi: "Firefox (.xpi)", fileExtZip: "Chrome / Edge (.zip)"
};
