## Yang baru di 1.4.1

- **Unduhan FTP dan FTPS (baru):** tautan yang diawali ftp://, ftps://, atau ftpes:// kini diunduh seperti tautan langsung lainnya, dalam beberapa bagian dan bisa dilanjutkan jika server mengizinkan. Tulis login di dalam tautan, simpan di Pengaturan > Jaringan > Login situs, atau kosongkan untuk server anonim. Perlu diketahui: tautan FTP tidak melewati proxy; di aplikasi desktop, FTP melalui TLS memakai satu koneksi; FTP melalui TLS belum tersedia di iPhone dan iPad.
- **File yang berubah di server:** jika file diganti di server saat sedang diunduh, FetchJet mengetahuinya. Unduhan berhenti dengan pesan yang jelas, bukan menyimpan file campuran yang rusak, dan "Lanjutkan" mengunduh versi yang baru.
- **Tidak ada lagi menunggu di akhir:** akhir unduhan tidak lagi menunggu satu koneksi yang lambat. Koneksi yang merayap di akhir akan diganti.
- **Audio dalam OGG:** memilih OGG untuk audio YouTube (Opus) tidak lagi mengodekannya ulang. Audio langsung disimpan dengan kualitas dan ukuran aslinya.
- **Kompres audio:** kini menawarkan format yang sama dengan Konversi (M4A, MP3, Opus, OGG, FLAC, WAV), tetap memakai OGG jika filenya OGG, dan perkiraan ukurannya lebih mendekati ukuran sebenarnya.
- **Ekstensi browser:** saat browser belum terhubung ke aplikasi, ekstensi langsung memberi tahu ("Buka FetchJet dan ketuk Izinkan"), bukan memuat tanpa henti. Setelah Anda mengetuk "Izinkan", daftar kualitas muncul sendiri tanpa memuat ulang. Memuat ulang halaman video akan mencoba lagi setelah terjadi kesalahan.
- **Pengaturan > Ekstensi browser:** "Putuskan semua" memutus semua browser yang terhubung; masing-masing akan meminta "Izinkan" lagi di lain waktu.
- **Jendela pembaruan:** catatan rilis mengikuti bahasa aplikasi saat Anda mengubahnya.
