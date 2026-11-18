## Yang baru di 1.5.0

**Arsip**
- **Ekstrak setelah unduhan:** file ZIP, TAR, GZ, dan XZ bisa diekstrak dengan sendirinya saat selesai, atau dengan tombol Ekstrak yang baru di baris. Di desktop, 7Z, RAR, arsip terpisah (.001, .part1.rar, .z01), dan arsip dengan kata sandi juga berfungsi jika 7-Zip terpasang; FetchJet akan meminta kata sandinya.
- **Ke mana file disimpan:** folder dengan nama arsip, selalu sebuah folder, atau di samping arsip. Anda juga bisa memindahkan arsip ke Recycle Bin setelah diekstrak.

**Unduhan**
- **Menu klik kanan pada unduhan** (tekan lama di ponsel): buka, buka folder, salin tautan, jeda, lanjutkan, hapus, dan lainnya.
- **Folder per situs:** kirim semua yang berasal dari sebuah situs, atau dari tautan yang awalnya sama (kursus, proyek), ke folder yang Anda pilih. Kursus yang dikirim dari browser juga mengikutinya.
- **File yang dihapus masuk ke Recycle Bin** (Tempat Sampah di macOS dan Linux), tidak hilang begitu saja. Anda bisa mematikannya di Pengaturan.
- **Pindai setelah unduhan:** Windows memeriksa file yang selesai dengan antivirus Anda, dan unduhan yang diblokir antivirus langsung berhenti dengan pesan yang jelas. Di macOS, file yang diunduh dan diekstrak membawa tanda "diunduh dari internet" seperti biasa.

**Jaringan (desktop)**
- **DNS Anda sendiri:** pilih server DNS untuk unduhan FetchJet (DNS over HTTPS atau server DNS biasa), dan pilih adaptor jaringan yang dipakai. Hanya unduhan FetchJet yang terpengaruh, bukan seluruh komputer.

**Windows**
- **Penginstal baru:** Setup.exe dan MSI baru, dengan pilihan pintasan di desktop dan menu Start. Keduanya memberi tahu jika versi ini, versi yang lebih baru, atau yang lebih lama sudah terpasang, dan bisa menjalankan FetchJet di akhir.
- **Versi portabel:** sebuah .zip yang berjalan tanpa dipasang, dengan datanya di folder di sampingnya.
- **ffmpeg yang lebih baru** di Windows dan Linux (alat yang menggabungkan video dan audio). Diperbarui dengan sendirinya saat FetchJet dijalankan berikutnya.

**Android**
- **Aplikasi lebih kecil:** sekitar 53 MB, bukan 83 MB, di sebagian besar ponsel, dan memakai lebih sedikit ruang setelah dipasang.

**iPhone dan iPad**
- **Halaman yang perlu login, seperti rekaman Zoom:** buka halaman di dalam FetchJet, ketik kode sandi sekali, lalu unduh dengan sesi halaman itu. Tombol halaman di Safari juga punya "Buka halaman ini di FetchJet".

**Untuk pengguna mahir**
- **Baris perintah:** `FetchJet status`, `pause`, `start`, `remove`, dan `delete` mengendalikan FetchJet yang sedang berjalan. Semuanya mati sampai Anda menjalankan `FetchJet automation on`.

**Ekstensi browser (3.5)**
- Mengambil setiap unduhan browser, kecuali Anda membatasinya ke jenis file yang Anda daftarkan.
- Tombol unduh pada rekaman Zoom yang hanya berisi audio.
- Pemutar di dalam frame lebih andal menemukan videonya.

**Privasi**
- FetchJet kini mengirim satu pesan anonim sehari agar kami tahu berapa banyak orang yang memakainya: angka acak untuk instalasi ini, versi aplikasi, dan sistem; server membaca negara dari koneksi. Tanpa tautan, file, atau data pribadi. Totalnya ada di halaman publik yang ditautkan dari README.
