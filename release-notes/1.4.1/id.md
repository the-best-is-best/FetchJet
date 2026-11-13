## Yang baru di 1.4.1

**Unduhan**
- **Unduhan FTP dan FTPS (baru):** tautan yang diawali ftp://, ftps://, atau ftpes:// kini diunduh seperti tautan langsung lainnya, dalam beberapa bagian dan bisa dilanjutkan jika server mengizinkan. Tulis login di dalam tautan, simpan di Pengaturan > Jaringan > Login situs, atau kosongkan untuk server anonim. Perlu diketahui: tautan FTP tidak melewati proxy; di aplikasi desktop, FTP melalui TLS diambil dengan curl milik sistem; FTP melalui TLS belum tersedia di iPhone dan iPad.
- **File yang berubah di server:** jika file diganti di server saat sedang diunduh, FetchJet mengetahuinya. Unduhan berhenti dengan pesan yang jelas, bukan menyimpan file campuran yang rusak, dan Lanjutkan mengunduh versi barunya.
- **Tidak ada lagi menunggu di akhir:** akhir unduhan tidak lagi menunggu satu koneksi yang lambat. Koneksi yang merayap di akhir akan diganti.
- **Pemeriksaan sebelum menyimpan:** tempel checksum (SHA-256 atau MD5) di dialog Tambah, atau biarkan header checksum dari server yang menentukan. File yang tidak cocok tidak disimpan. File ZIP diperiksa entri demi entri, dan bagian yang rusak diunduh ulang satu kali.
- **Batas server diingat:** situs yang hanya mengizinkan beberapa koneksi diingat selama sehari, sehingga unduhan berikutnya tidak dimulai dengan koneksi yang ditolaknya. Setelah 403, FetchJet bisa mencoba lagi lewat IPv4 (Windows, Android, dan Linux).
- **Uji tautan:** tombol di dialog Tambah memberi tahu apakah tautan berfungsi, ukurannya, dan apakah bisa dilanjutkan, tanpa mengunduhnya.
- **Folder per jenis file:** folder yang Anda pilih untuk video, musik, atau arsip hanya diingat untuk jenis itu.
- **Putar dari daftar:** file video atau audio yang selesai kini menampilkan tombol ▶ yang membukanya di pemutar Anda.
- **Tanpa nama file karangan:** tautan tanpa nama file menampilkan kolom «Nama file» kosong, dan Mulai menunggu sampai Anda mengetik nama.
- **Pas di layar Anda:** jendela desktop kini terbuka di dalam layar yang terlihat, bukan sebagian di bawah bilah tugas.

**Torrent**
- **Detail:** rasio berbagi, jumlah yang diunggah, bagian dan ukuran bagian, tracker beserta statusnya, peer, "Periksa ulang", salin tautan magnet, dan "bagian pertama dan terakhir dulu" untuk pratinjau video.
- **Tambah tracker** ke torrent yang sedang berjalan, dan **berhenti berbagi pada rasio** yang Anda pilih (Pengaturan > BitTorrent).
- **Lebih aman:** torrent yang filenya akan ditulis di luar foldernya sendiri ditolak.
- **Di balik proxy HTTP:** torrent yang hanya punya tracker UDP tidak bisa menemukan peer di sana; Pengaturan > Jaringan dan pesan kesalahan kini menyebutkannya.

**Desktop**
- **Keyboard:** tombol panah, Shift, Home / End, Page Up / Down, Enter (Alt+Enter membuka atau menutup detail), Ctrl+R / P / O / E / N / F / A, Delete, Esc, dan F1 untuk daftar pintasan. Semuanya juga berfungsi dengan keyboard Arab, Persia, dan Rusia, begitu pula salin / tempel di setiap kolom.
- **Baris perintah:** `/d` tautan, `/p` folder, `/f` nama file, `/a` tambah dalam keadaan dijeda tanpa dialog Tambah, `/n` tambah dan langsung mulai tanpa jendela, `/s` mulai semua yang menunggu.
- **Batas kecepatan hanya saat Anda memakai komputer,** dan **membangunkan komputer untuk unduhan terjadwal** (Windows).
- **Hal-hal kecil:** versi di judul jendela, Esc menutup dialog Tambah, lencana jenis file terbaca jelas di tema terang, dan judul Kirim ke perangkat diterjemahkan di mana-mana.

**Audio**
- **OGG:** memilih OGG untuk audio YouTube (Opus) tidak lagi mengenkode ulang. File disimpan seketika, dengan kualitas dan ukuran asli.
- **Kompres audio:** kini menawarkan format yang sama dengan Ubah (M4A, MP3, Opus, OGG, FLAC, WAV), mempertahankan OGG jika filenya OGG, dan perkiraan ukurannya lebih mendekati ukuran sebenarnya.

**Ekstensi browser (3.4)**
- **Koneksi:** saat browser belum terhubung ke aplikasi, ekstensi langsung mengatakannya ("Buka FetchJet lalu ketuk Izinkan") dan tidak memuat tanpa henti. Setelah Anda mengetuk Izinkan, kualitas muncul dengan sendirinya. Jika browser bertanya "Buka FetchJet?", tab kini tetap ada sampai Anda menjawab. Pengaturan > Ekstensi browser memiliki "Putuskan semua".
- **Browser menyimpan unduhan sampai aplikasi menerimanya,** formulir yang Anda kirim tetap di browser, dan jendela pribadi memakai cookie-nya sendiri.
- **Tautan:** tautan file diambil setelah kode klik milik halaman itu berjalan, sehingga situs yang dulu menjawab 403 kini berfungsi. Tautan PDF dibuka di browser kecuali halaman menandainya sebagai unduhan. "Unduh semua tautan" juga menemukan tautan yang ditandai sebagai unduhan dan tautan yang disimpan di atribut data-*, dan Google Drive punya baris "File asli".
- **Tombol video:** berfungsi dengan keyboard (Tab, Enter), menempel ke tepi saat Anda menyeretnya ke sana, ada satu tombol per kelompok video yang bertumpuk, dan tidak ada tombol pada video yang tersembunyi di bawah halaman. Anda bisa menyembunyikannya di suatu situs, dan Telegram Web menjelaskan mengapa tidak bisa diunduh.

**Pembaruan**
- **Jendela pembaruan** mengikuti bahasa aplikasi, dan catatan rilis diterjemahkan.
