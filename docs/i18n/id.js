window.FJ_PAGE_TEXT = window.FJ_PAGE_TEXT || {};
window.FJ_PAGE_TEXT.id = {
  pageTitle: "Unduhan FetchJet", title: "Unduhan FetchJet", subtitle: "Semua versi, untuk semua platform. Pilih punyamu.",
  loading: "memuat…", loadingReleases: "Memuat rilis dari GitHub…",
  footer: 'File diterbitkan di <a href="{releases}">GitHub Releases</a>. Android: pasang APK untuk CPU-mu (<em>arm64-v8a</em> jika ragu). iPhone dan iPad: satu IPA untuk keduanya, untuk sideload (AltStore / Sideloadly). Firefox: pasang dari Firefox Add-ons. <a href="privacy.html">Kebijakan privasi</a>',
  winHint: "File .msi memasang FetchJet dan membiarkannya memperbarui diri sendiri.",
  winNotice: '<div class="notice"><h3>Dapatkan dari Microsoft Store (disarankan)</h3>Pasang dengan satu klik, tanpa peringatan SmartScreen, dan Windows menjaganya tetap terbaru.<div class="stores"><a class="btn" href="https://apps.microsoft.com/detail/9NP3HJRHMPJS">Microsoft Store</a></div><div class="hint">Lebih suka penginstal? Ambil .msi di bawah; ia memperbarui diri dari GitHub.</div></div>',
  macHint: "Hanya untuk Mac Apple Silicon (M1 atau lebih baru); Mac Intel tidak didukung. Buka .dmg dan seret FetchJet ke Applications.",
  macNotice: '<div class="notice"><h3>Pembukaan pertama: "Apple could not verify FetchJet"</h3>FetchJet belum dinotarisasi oleh Apple, jadi macOS memblokir pembukaan pertama. Buka sekali, tutup peringatannya, lalu buka <b>System Settings &rarr; Privacy &amp; Security</b> dan klik <b>Open Anyway</b>. Atau jalankan ini sekali di Terminal:<div class="hint"><code>xattr -dr com.apple.quarantine /Applications/FetchJet.app</code></div></div>',
  linuxHint: ".deb untuk Debian/Ubuntu; .tar.gz berjalan di mana saja (ekstrak lalu jalankan bin/FetchJet).",
  androidHint: "arm64-v8a cocok untuk hampir semua ponsel buatan setelah 2017. Ponsel 32-bit lama: armeabi-v7a. Emulator dan Chromebook: x86_64.",
  iosLabel: "iPhone · iPad", iosHint: "Satu IPA untuk iPhone dan iPad. Tidak ditandatangani: sideload dengan AltStore atau Sideloadly.",
  extLabel: "Ekstensi browser", extHint: ".zip = Chrome / Edge / Brave (muat tanpa paket atau seret ke chrome://extensions).",
  extNotice: '<div class="notice"><h3>Pasang dari toko (disarankan)</h3>Pembaruan terpasang sendiri dan browser tidak menampilkan peringatan.<div class="stores"><a class="btn" href="https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/">Firefox Add-ons</a><a class="btn" href="https://microsoftedge.microsoft.com/addons/detail/phngekpnkcnpkjkcoggcfmlmebmociaj">Microsoft Edge Add-ons</a></div><div class="hint">Chrome, Brave, Opera, dan Vivaldi: FetchJet tidak ada di Chrome Web Store, jadi gunakan .zip di bawah.</div></div><div class="notice warn"><h3>⚠ Memasang .zip di bawah (mode pengembang)</h3>File .zip untuk Chrome / Edge / Brave saat kamu tidak bisa memakai toko. Browser menganggapnya ekstensi pengembang: bisa muncul peringatan "developer mode extensions" saat mulai, dan ia <b>tidak</b> memperbarui diri - kembali ke sini untuk versi baru.<ol><li>Unduh <code>.zip</code> dan ekstrak ke folder yang akan kamu simpan (jangan dihapus setelahnya).</li><li>Buka <code>chrome://extensions</code> (Edge: <code>edge://extensions</code>).</li><li>Nyalakan <b>Mode pengembang</b>.</li><li>Klik <b>Muat yang belum dibuka</b> dan pilih folder hasil ekstrak.</li></ol></div>',
  noBuild: "Belum ada versi untuk {p} yang diterbitkan.", latestFor: "Terbaru untuk {p}: {v}",
  skipped: "rilis yang lebih baru ({list}) tidak punya perubahan untuk {p}", releaseNotes: "Catatan rilis", whatsNew: "Yang baru di {v}",
  hideOlder: "Sembunyikan versi lama", showOlder: "Tampilkan versi lama ({n})", preRelease: "pra-rilis", beta: "beta",
  whatChanged: "apa yang berubah", onGitHub: "di GitHub", release1: "1 rilis", releasesN: "{n} rilis", offline: "luring",
  loadError: 'Daftar rilis tidak bisa dimuat ({e}). Buka <a href="{releases}">halaman rilis di GitHub</a> sebagai gantinya.',
  fileMsi: "Penginstal (.msi)", fileZip: "Portabel, tanpa instal (.zip)", fileExe: "Setup (.exe)", fileDeb: "Debian / Ubuntu (.deb)",
  fileTar: "Portabel (.tar.gz)", fileDmg: "Apple Silicon (.dmg)", fileXpi: "Firefox (.xpi)", fileExtZip: "Chrome / Edge (.zip)"
};
