## Yang baru di 2.1.1

**Pemutar media**
- **Baru:** putar audio dan video yang sudah selesai langsung di FetchJet di semua platform: tombol putar di baris, Putar untuk seluruh folder, daftar putar, atau grup, antrean dengan sebelumnya / berikutnya dan daftarnya, pilihan kecepatan, serta tahan untuk 2x. Posisi terakhir diingat.
- **Kontrol ringkas:** kontrol berada di atas video dan menghilang setelah 3 detik. Bilah posisi yang tipis, satu baris kecil berisi kecepatan, sakelar **Putar otomatis**, dan **CC**. Saat video dijeda, atau video berikutnya menunggu karena Putar otomatis mati, satu ▶ besar muncul di tengah.
- **Putar otomatis:** jika aktif, item berikutnya mulai sendiri; jika mati, item dimuat dalam keadaan dijeda.
- **Subtitel:** menu CC menampilkan subtitel di dalam video, file subtitel di sebelahnya (bahasa Anda lebih dulu), dan «Buka file subtitel…» (SRT, VTT, ASS). Pilihan diingat untuk setiap unduhan. Subtitel ada di bawah dan naik di atas kontrol saat kontrol tampil. CC terisi penuh selama subtitel aktif.
- **Ponsel dan tablet:** geser ke atas atau ke bawah di separuh kanan untuk volume dan di separuh kiri untuk kecerahan (hanya di pemutar).
- **Komputer:** penggeser volume hingga 200% dengan tombol bisukan, dan layar penuh. Di Windows, macOS, dan Linux pustaka pemutar hanya diunduh saat pertama kali memutar, dengan kemajuan yang ditampilkan, dan file terbuka sendiri saat siap.

**Potong, ekstrak, dan kompres**
- **Potong video:** dari menu baris atau detail, berdasarkan waktu atau di garis waktu. Garis waktu bekerja seperti editor video: skala waktu tetap, seret atau gulir untuk bergeser, perbesar dari beberapa detik sampai seluruh video, pegangan yang menggulir garis waktu di tepi, gambar mini, dan bilah gulir tipis. Klip adalah file baru yang tercantum di bawah videonya.
- **Potong audio:** file audio bisa dipotong dengan cara yang sama, di garis waktu bergambar gelombang suara. Bagian MP3 di-encode ulang dengan bitrate sumber agar tidak ada suara yang hilang dan file tidak membesar.
- **Ekstrak audio:** menyimpan trek suara video sebagai file tersendiri tanpa encode ulang.
- **Hapus file asli:** jika belum ada yang dibuat dari sebuah file, Potong dan Kompres menawarkan «Hapus file asli». File baru lalu menjadi unduhan utama: Anda bisa menambahkan subtitel (waktunya mengikuti klip), memotong, atau mengompresnya lagi. Menghapus sebuah unduhan juga menghapus klip dan salinan yang dibuat darinya, dan konfirmasinya menyebutkan jumlahnya.

**Pembaca PDF**
- **Baru:** buka file PDF di dalam FetchJet: halaman tersusun dari atas ke bawah, nomor halaman dan «Ke halaman», pas lebar, zoom, dan halaman terakhir yang Anda baca diingat. Android dan iOS memakai mesin PDF sistem; Windows, macOS, dan Linux mengunduh pustaka PDF kecil saat pertama kali. Sebuah pengaturan memilih pembaca di aplikasi, aplikasi sistem, atau bertanya setiap kali.

**Unduhan tersembunyi**
- **Baru:** sembunyikan unduhan, daftar putar, atau grup folder di balik PIN 4 sampai 6 digit atau pola (Lainnya > Tersembunyi). Item yang disembunyikan keluar dari daftar, galeri, dan pengelola file, dan filenya pindah ke folder pribadi aplikasi. Menyembunyikan berlangsung seketika dan menampilkan satu baris kemajuan saat file dipindahkan. Saat Anda menampilkannya lagi, file kembali ke foldernya.
- **Audio dan video dienkripsi:** audio dan video yang Anda sembunyikan dienkripsi dengan kunci yang terikat ke perangkat, nama filenya diacak, dan baru didekripsi saat Anda memutarnya di pemutar aplikasi. Jenis file lain hanya dipindahkan dari pandangan. Pemberitahuan menjelaskan hal ini saat pertama kali.
- **Meninggalkan aplikasi akan menutupinya:** saat Anda kembali, aplikasi meminta sidik jari, Face ID, Windows Hello, atau PIN, dan video tersembunyi yang sedang diputar ditutup.
- **Unduhan baru dimulai tersembunyi** jika Anda memulainya dari layar Tersembunyi (tombol +). Unduhan tersembunyi tidak menampilkan notifikasi.
- **Lupa PIN:** konfirmasi dengan kunci ponsel atau komputer Anda lalu atur PIN baru. File tersembunyi Anda tetap ada. Tidak tersedia di Linux.
- **Kode pemulihan:** satu kode untuk seluruh brankas, ditampilkan sekali. Dengan kode ini Anda bisa membuka lagi audio dan video tersembunyi jika kunci perangkat hilang, misalnya setelah memulihkan ke ponsel baru. Menghapus aplikasi atau data aplikasinya akan menghapus file tersembunyi, jadi tampilkan kembali file itu sebelum mencopot pemasangan.

**Cadangan dan pemulihan**
- **«Cadangkan unduhan»** menyimpan daftar unduhan, folder, dan pengaturan Anda ke satu file, tanpa kata sandi atau cookie. **«Pulihkan dari cadangan»** mengembalikannya dan menggabungkannya dengan yang sudah ada. File yang diunduh tidak ada di dalam cadangan: unduhan yang selesai tersambung kembali ke filenya, dan yang belum selesai kembali dalam keadaan dijeda.

**Browser dan login**
- **Browser di dalam aplikasi** (Lainnya > Browser, Android dan iOS): bilah alamat, kembali / maju / muat ulang; file atau video yang Anda buka di sana masuk ke dialog Tambah bersama login halaman itu.
- **Masuk dengan browser** (Pengaturan > Jaringan > Login situs): masuk ke sebuah situs sekali, dan unduhannya memakai sesi itu. Menghapus login situs juga menghapus cookie browser untuk situs itu.

**Unduhan**
- **Jumlah koneksi dipilih sendiri:** FetchJet mulai dengan 4 koneksi, menambah lagi selama kecepatan masih naik, dan mengingat apa yang berhasil untuk tiap situs.
- **Waktu klip tepat:** klip kini mulai dan berakhir tepat di Dari dan Sampai, dan Dari / Sampai menerima pecahan detik (12.500). Sebelumnya klip bisa mulai terlambat (Dari 12 mulai di akhir detik ke-12) atau terlalu awal (11.5 mulai di 11), karena potongan bergeser ke key frame terdekat. Ini terjadi di semua platform sejak klip ditambahkan.
- **Lanjutkan menampilkan panah unduh** (⬇), bukan ▶, jadi tidak lagi terlihat seperti memutar video. ▶ kini hanya untuk memutar file yang sudah selesai.
- **Seluruh kursus Udemy** juga membawa file kursus: kuliah PDF, e-book, slide, dan kode, serta sumber yang dilampirkan pada kuliah, masing-masing tepat setelah kuliahnya.
- **Detail** kembali menampilkan tautan Sumber; tautan yang membawa login atau token menampilkan catatan sebagai gantinya.
- **Ikon aplikasi baru** di semua platform.

**Keamanan**
- Menghapus unduhan yang belum selesai atau gagal dengan «hapus juga file» tidak lagi menghapus file lama Anda yang bernama sama. Menghapus file yang dipulihkan dari cadangan terlebih dulu memeriksa bahwa itu benar file milik unduhan tersebut.
- Unduhan yang tidak bisa dipindahkan ke tempatnya tidak lagi meninggalkan file terpotong dengan nama akhir, dan ukuran yang salah di akhir tidak lagi membuang bagian yang sudah diunduh.
- Arsip 7z dan RAR diperiksa sebelum diekstrak (tanpa tautan, tanpa jalur yang keluar dari folder, ada batas ukuran), dan kata sandi arsip tidak lagi terlihat oleh program lain.
- Koneksi lokal ke ekstensi browser selalu memerlukan pemasangan (pairing); API otomasi hanya bisa dinyalakan oleh Anda, bukan lewat API itu sendiri.
- Pengaturan yang sesaat tidak bisa didekripsi tidak lagi menggantikan pemasangan ekstensi.

**Android**
- **Hanya klip berfungsi lagi:** sejak 1.5.0, «Hanya klip» di Android gagal dengan "ffmpeg exited with code 8" di semua situs, karena ffmpeg yang lebih kecil yang dibuat untuk aplikasi tidak punya akses jaringan. ffmpeg kini kembali membaca bagian video dari situs, dan jika suatu saat itu gagal, aplikasi mengunduh seluruh video lalu memotongnya dengan tepat, bukan gagal.
- **Menggabungkan bagian video:** sejak 1.5.0 beberapa situs yang mengirim video dalam bagian-bagian (HLS) gagal dengan "dimensions not set". Sudah diperbaiki.
- Klip yang dipotong tepat di ponsel tidak lagi jauh lebih besar dari bagiannya di video (klip 5 detik dari video kecil sebesar 1.6 MB).
- Di pemutar, menggeser di tepi samping mengubah volume atau kecerahan alih-alih Kembali, dan Kembali menutup menu yang terbuka sebelum pemutar.
- Tautan yang dibuka dari aplikasi lain atau halaman web selalu menampilkan dialog Tambah lebih dulu, file torrent yang dibuka dari aplikasi lain diperiksa lebih dulu, dan pertanyaan baterai hanya diajukan sekali.

**Windows, macOS, dan Linux**
- Pustaka pemutar dan PDF hanya dimuat dari folder milik FetchJet sendiri, tidak pernah dari folder saat ini atau PATH.
- macOS dan Linux: pembaruan dan file sementara memakai folder yang hanya bisa Anda buka, dan pembaruan diperiksa sekali lagi tepat sebelum dipasang.
- Pilihan «Potong audio» menampilkan kedua opsi selebar penuh.

**iOS**
- **Hanya klip:** klip bisa tersimpan sebagai file sangat kecil yang tidak bisa diputar. Aplikasi kini menyadarinya, lalu mengunduh seluruh video dan memotongnya dengan tepat.
- Browser di dalam aplikasi menghentikan suaranya dan membebaskan memorinya saat Anda menutupnya.

**Ekstensi browser 3.8.0**
- **Buka di FetchJet:** panah di samping tombol unduh halaman dan baris «Buka di FetchJet» langsung mengirim tautan halaman ke aplikasi, lalu Anda memilih kualitas di sana. Ini juga berfungsi di halaman yang kualitasnya tidak bisa didaftar, misalnya kuliah Udemy.
- **Buka FetchJet langsung:** opsi baru yang menyerahkan tautan ke aplikasi tanpa jembatan lokal.
- **Canva:** setiap video yang diputar mendapat tombol unduh, dinamai sesuai halamannya; gambar dan suaranya disimpan sebagai satu file.
- **Beranda YouTube:** tombol pada video yang mulai diputar saat Anda mengarahkan kursor ke sana tidak lagi berkedip ketika Anda menggerakkan kursor ke tombol itu, dan selalu milik video di bawah kursor. Sebelumnya tombol itu hilang lalu muncul lagi, dan bisa menampilkan kualitas video pertama pada video lain.
- **Udemy:** aliran kuliah dinamai sesuai kuliahnya, bukan kode acak.
- **Lebih aman:** hanya halaman ekstensi sendiri yang bisa mengubah pengaturannya, cookie sebuah halaman hanya dikirim ke situs yang sama, dan pembacaan kursus Udemy hanya berjalan di udemy.com.

**Hal-hal kecil**
- Menekan lama sebuah unduhan di layar Tersembunyi membuka menu. Semua daftar kini memakai satu menu baris yang sama, dibagi per bagian, dengan Utama di urutan pertama.
- Android: jika file diunduh oleh pemasangan FetchJet yang lain dan tidak bisa dibaca, FetchJet sekarang membuka halaman «Izinkan akses ke semua file», bukan gagal tanpa pemberitahuan.
- Dialog Tambah tidak lagi memiliki sakelar «Unduh sebagai tersembunyi».
