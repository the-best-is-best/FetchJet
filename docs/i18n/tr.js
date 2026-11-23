window.FJ_PAGE_TEXT = window.FJ_PAGE_TEXT || {};
window.FJ_PAGE_TEXT.tr = {
  pageTitle: "FetchJet indirmeleri", title: "FetchJet indirmeleri", subtitle: "Her platform için her sürüm. Kendinkini seç.",
  loading: "yükleniyor…", loadingReleases: "Sürümler GitHub'dan yükleniyor…",
  footer: 'Dosyalar <a href="{releases}">GitHub Releases</a> üzerinde yayınlanır. Android: işlemcine uygun APK’yı kur (emin değilsen <em>arm64-v8a</em>). iPhone ve iPad: ikisi için tek IPA, yan yükleme için (AltStore / Sideloadly). Firefox: Firefox Add-ons’tan kur. <a href="privacy.html">Gizlilik politikası</a>',
  winHint: ".msi FetchJet’i kurar ve kendini güncellemesine izin verir.",
  winNotice: '<div class="notice"><h3>Microsoft Store’dan al (önerilen)</h3>Tek tıkla kurulum, SmartScreen uyarısı yok ve Windows onu güncel tutar.<div class="stores"><a class="btn" href="https://apps.microsoft.com/detail/9NP3HJRHMPJS">Microsoft Store</a></div><div class="hint">Kurulum dosyası mı istiyorsun? Aşağıdaki .msi’yi al; GitHub’dan kendini günceller.</div></div>',
  macHint: "Yalnızca Apple Silicon Mac’ler için (M1 veya daha yeni); Intel Mac’ler desteklenmez. .dmg’yi aç ve FetchJet’i Uygulamalar’a sürükle.",
  macNotice: '<div class="notice"><h3>İlk açılış: "Apple could not verify FetchJet"</h3>FetchJet henüz Apple tarafından onaylanmadı (notarized), bu yüzden macOS ilk açılışı engeller. Bir kez aç, uyarıyı kapat, sonra <b>Sistem Ayarları &rarr; Gizlilik ve Güvenlik</b>’e git ve <b>Yine de Aç</b>’a tıkla. Ya da bunu Terminal’de bir kez çalıştır:<div class="hint"><code>xattr -dr com.apple.quarantine /Applications/FetchJet.app</code></div></div>',
  linuxHint: "Debian/Ubuntu için .deb; .tar.gz her yerde çalışır (aç ve bin/FetchJet’i başlat).",
  androidHint: "arm64-v8a 2017’den sonra üretilen neredeyse her telefona uyar. Eski 32 bit telefonlar: armeabi-v7a. Emülatörler ve Chromebook’lar: x86_64.",
  iosLabel: "iPhone · iPad", iosHint: "iPhone ve iPad için tek IPA. İmzasız: AltStore veya Sideloadly ile yan yükle.",
  extLabel: "Tarayıcı eklentisi", extHint: ".zip = Chrome / Edge / Brave (paketlenmemiş olarak yükle veya chrome://extensions’a sürükle).",
  extNotice: '<div class="notice"><h3>Bir mağazadan kur (önerilen)</h3>Güncellemeler kendiliğinden kurulur ve tarayıcı uyarı göstermez.<div class="stores"><a class="btn" href="https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/">Firefox Add-ons</a><a class="btn" href="https://microsoftedge.microsoft.com/addons/detail/phngekpnkcnpkjkcoggcfmlmebmociaj">Microsoft Edge Add-ons</a></div><div class="hint">Chrome, Brave, Opera ve Vivaldi: FetchJet Chrome Web Store’da yok, bu yüzden aşağıdaki .zip’i kullan.</div></div><div class="notice warn"><h3>⚠ Aşağıdaki .zip’i kurmak (geliştirici modu)</h3>.zip, mağaza kullanamadığında Chrome / Edge / Brave içindir. Tarayıcı onu geliştirici eklentisi sayar: açılışta "developer mode extensions" uyarısı gösterebilir ve kendini <b>güncellemez</b> - yeni sürümler için buraya dön.<ol><li><code>.zip</code>’i indir ve saklayacağın bir klasöre çıkar (sonra silme).</li><li><code>chrome://extensions</code>’ı aç (Edge: <code>edge://extensions</code>).</li><li><b>Geliştirici modu</b>’nu aç.</li><li><b>Paketlenmemiş öğe yükle</b>’ye tıkla ve çıkarılan klasörü seç.</li></ol></div>',
  noBuild: "{p} için henüz bir sürüm yayınlanmadı.", latestFor: "{p} için en yeni: {v}",
  skipped: "daha yeni sürümlerde ({list}) {p} için değişiklik yoktu", releaseNotes: "Sürüm notları", whatsNew: "{v} sürümündeki yenilikler",
  hideOlder: "Eski sürümleri gizle", showOlder: "Eski sürümleri göster ({n})", preRelease: "ön sürüm", beta: "beta",
  whatChanged: "neler değişti", onGitHub: "GitHub’da", release1: "1 sürüm", releasesN: "{n} sürüm", offline: "çevrimdışı",
  loadError: 'Sürüm listesi yüklenemedi ({e}). Bunun yerine <a href="{releases}">GitHub’daki sürümler sayfasını</a> aç.',
  fileMsi: "Kurulum (.msi)", fileZip: "Taşınabilir, kurulumsuz (.zip)", fileExe: "Kurulum sihirbazı (.exe)", fileDeb: "Debian / Ubuntu (.deb)",
  fileTar: "Taşınabilir (.tar.gz)", fileDmg: "Apple Silicon (.dmg)", fileXpi: "Firefox (.xpi)", fileExtZip: "Chrome / Edge (.zip)"
};
