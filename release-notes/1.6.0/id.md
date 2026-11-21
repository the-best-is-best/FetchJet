## Yang baru di 1.6.0

**Dialog tambah yang lebih cerdas**
- **FetchJet memilih mesin dari tautan:** file, tautan magnet, atau situs video yang dikenal langsung diarahkan ke Direct, Torrent, atau Media. Tautan diperiksa satu kali dulu ("Memeriksa tautan…"), dan pilihan Direct / Media hanya muncul untuk halaman yang berisi video atau tautan yang tidak bisa diperiksa.
- **Tidak ada lagi pemeriksaan di setiap huruf yang Anda ketik:** pemeriksaan dimulai saat Anda berhenti mengetik (langsung untuk tautan yang ditempel). Mengubah tautan membatalkan pembacaan lama, jadi dialog tidak lagi macet di "Mencari nama…", misalnya pada magnet yang tidak menemukan sumber.

**Cari file di halaman ini**
- **Baru:** tempel tautan halaman lalu pakai "Cari file di halaman ini". FetchJet mendaftar file di halaman itu: gambar, tautan, dan alamat yang ditulis sebagai teks biasa. Untuk tautan tanpa nama file, nama asli dan ukurannya ditanyakan ke server. Jika Anda mau, halaman yang ditautkan juga dibaca, satu atau dua tingkat ke bawah.
- **Hasil muncul saat membaca:** file muncul selagi halaman dibaca, dan "Hentikan pembacaan" menyimpan yang sudah ditemukan.
- **Pilih yang diunduh:** jenis file berupa tombol yang menyembunyikan atau menampilkan file tersebut, dan file yang disembunyikan tidak diunduh. Setiap baris menampilkan jenisnya (ZIP, MP3, PNG…).
- **Disimpan sebagai grup:** file masuk ke folder dan grup yang dinamai sesuai halaman. Nanti, "Periksa file baru" menandai file yang sudah Anda punya dan menguncinya ("N sudah diunduh sebelumnya").
- **Halaman rilis GitHub** dibaca dalam satu permintaan.
- **Halaman yang tidak bisa dibaca:** halaman yang ditolak situs (karena pemeriksaan atau pemblokiran) dihitung. Halaman yang membuat tautannya dengan JavaScript disebutkan, disertai petunjuk untuk membukanya di browser dan memakai "Unduh semua tautan" dari ekstensi.

**Playlist dan subtitle**
- **Playlist:** video yang sudah pernah Anda unduh ditandai dan dihitung ("N sudah diunduh sebelumnya"), dan playlist yang disimpan sebagai grup bisa diperiksa untuk video baru.
- **Subtitle:** pilih "Sematkan ke video" atau "File terpisah", lalu SRT atau VTT. Bahasa diminta satu per satu, dan bahasa yang sesaat ditolak situs diminta lagi. Anda juga bisa menambahkan subtitle ke video yang sudah selesai dari detailnya atau dari menu barisnya.
- **"Kirim ke perangkat" mempertahankan subtitle:** video yang sudah selesai dan dikirim ke perangkat lain tetap menawarkan "Tambah subtitle" di sana, tahu bahasa apa saja yang sudah ada di file, dan file subtitle di sebelahnya ikut terkirim.

**Video**
- **Google Drive:** setiap tautan file Drive diunduh langsung, dan file yang pemiliknya mematikan pengunduhan akan menyatakannya.
- **Video terlindungi:** video yang dilindungi DRM disebut demikian sebelum daftar kualitas, bukan gagal belakangan.
- **Video yang bisa diputar di mana saja:** sakelar ini (kini di Pengaturan > Alat media, dan juga di Android) memilih file H.264 + AAC yang dibuka oleh semua TV dan pemutar, atau file AV1 / VP9 yang lebih kecil.

**Pengaturan**
- **Ditata ulang:** Unduhan (lokasi, koneksi, antrean), halaman baru Batas kecepatan (preset, batas saat tidak aktif, jadwal), dan halaman baru File & folder (urutkan menurut jenis, kelompokkan menurut folder, ekstrak, Tempat Sampah, pemindaian, folder menurut situs).

**Kecepatan**
- File besar memakai hingga 32 koneksi secara default.
- Permintaan yang tidak menerima data selama 20 detik dibatalkan dan diminta lagi, jadi satu koneksi yang macet tidak lagi menahan unduhan selama semenit.
- Pada transfer yang singkat dan cepat, koneksi yang lambat diganti lebih awal.
- macOS mengizinkan 64 koneksi per situs, jadi unduhan kedua dari situs yang sama tidak lagi menunggu.

**Hal-hal kecil**
- "Saat unduhan selesai" juga menunggu arsip yang sedang diekstrak atau file yang sedang dipindai.
- Torrent bernama panjang tidak lagi melebarkan kartu filenya, dan baris filenya menampilkan nama file lebih dulu.
- Tautan dan jalur folder mempertahankan arahnya dalam bahasa Arab dan Persia.
- Android: playlist yang dibaca di latar belakang tidak lagi membuat aplikasi crash.
- Jendela desktop digambar ulang sepenuhnya setelah ditampilkan atau diubah ukurannya, tanpa bagian yang belum tergambar.
- Aplikasi macOS dibuat agar berjalan di macOS 12 dan yang lebih baru.
- **Android: tidak ada daftar rusak setelah instal ulang atau ganti ponsel:** daftar unduhan tidak lagi dipulihkan dari cadangan, karena tautannya terkunci pada ponsel dan tidak bisa dibaca di ponsel lain. File yang sudah Anda unduh tetap ada di folder Anda.
- **Pesan subtitle yang lebih jelas:** saat tautan unduhan lama tidak bisa dibaca, "Tambah subtitle" menyatakan tidak bisa membaca subtitle, bukan menyatakan video tidak punya subtitle.
