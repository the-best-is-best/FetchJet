package com.michelle.raouf.fetch_jet.core.i18n.data_provider

object IndonesianProvider : AppLocalization {
    override val localeCode = "id"
    override val languageName = "Bahasa Indonesia"
    override val ok = "OK"
    override val cancel = "Batal"
    override val close = "Tutup"
    override val save = "Simpan"
    override val delete = "Hapus"
    override val remove = "Buang"
    override val open = "Buka"
    override val copy = "Salin"
    override val copied = "Disalin"
    override val retry = "Coba lagi"
    override val pause = "Jeda"
    override val resume = "Lanjutkan"
    override val apply = "Terapkan"
    override val reset = "Atur ulang"
    override val yes = "Ya"
    override val no = "Tidak"
    override val back = "Kembali"
    override val done = "Selesai"
    override val selectAll = "Pilih semua"
    override val selectNone = "Batalkan semua"
    override val language = "Bahasa"
    override val languageHint = "Bahasa untuk semua layar. Sistem mengikuti perangkat Anda."
    override val systemLanguage = "Sistem"
    override val homeSettings = "Pengaturan"
    override val homeDownloads = "Unduhan"

    override val homeTabAll = "Semua"
    override val homeTabActive = "Aktif"
    override val homeTabDone = "Selesai"
    override val homeTabTorrent = "Torrent"
    override val homeTabMedia = "Media"
    override val homeSidebarTypeHeader = "JENIS"
    override val homeSearchDownloadsPlaceholder = "Cari unduhan"
    override val homeAdd = "Tambah"
    override val homeAddDownload = "Tambah unduhan"
    override val homePauseAll = "Jeda semua"
    override val homeResumeAll = "Lanjutkan semua"
    override fun homeSortBy(option: String) = "Urutkan: $option"
    override val homeLightTheme = "Tema terang"
    override val homeDarkTheme = "Tema gelap"

    override fun homeUpdatingMediaTools(tool: String, percent: Int?) =
        "Memperbarui alat media · $tool" + (percent?.let { " $it%" } ?: "…")

    override val homeSearchDownloadsByNamePlaceholder = "Cari unduhan berdasarkan nama…"
    override fun homeNoDownloadsMatch(query: String) =
        "Tidak ada unduhan yang cocok dengan \"$query\""

    override val homeNoTasksInView = "Tidak ada tugas di tampilan ini"
    override val homeTryAnotherName = "Coba nama lain"
    override val homeAddDownloadToGetStarted = "Tambahkan unduhan untuk memulai"
    override fun homeDownloadingWaitingSummary(running: Int, waiting: Int) =
        "$running mengunduh · $waiting menunggu"

    override fun homeQueueStatus(maxAtOnce: Int) =
        "Antrean: " + if (maxAtOnce <= 0) "tanpa batas" else "$maxAtOnce sekaligus"

    override fun homeSpeedLimitStatus(limit: String?) = "Batas kecepatan: " + (limit ?: "nonaktif")
    override val homeGroupDownloading = "Mengunduh"
    override val homeGroupWaiting = "Menunggu"
    override val homeGroupPausedFailed = "Dijeda & gagal"
    override val homeGroupCompleted = "Selesai"
    override fun homeSelectedCount(count: Int) = "$count dipilih"
    override val homeExport = "Ekspor"

    override fun homeStartsAt(time: String) = "Mulai $time"
    override val homeWaitingInQueue = "Menunggu di antrean"
    override fun homeWaitingInQueuePosition(position: Int) = "Menunggu di antrean (#$position)"
    override val homeConnecting = "Menghubungkan…"
    override val homeStarting = "Memulai…"
    override fun homeTimeLeft(eta: String) = "$eta lagi"
    override fun homePausedState(percent: Int, restartsOnResume: Boolean) =
        "Dijeda · $percent%" + if (restartsOnResume) " · mulai ulang saat dilanjutkan" else ""

    override fun homeFailedState(message: String?) = "Gagal: " + (message ?: "galat")
    override fun homeCompletedState(size: String?) = "Selesai" + (size?.let { " · $it" } ?: "")
    override val homeStartNow = "Mulai sekarang"
    override val homeColumnName = "NAMA"
    override val homeColumnSize = "UKURAN"
    override val homeColumnProgress = "KEMAJUAN"
    override val homeColumnSpeed = "KECEPATAN"
    override val homeColumnLeft = "SISA"
    override val homeFilterAllDownloads = "Semua unduhan"
    override val homeFilterDownloading = "Mengunduh"
    override val homeFilterWaiting = "Menunggu"
    override val homeFilterPaused = "Dijeda"
    override val homeFilterCompleted = "Selesai"
    override val homeFilterFailed = "Gagal"
    override val homeFilterMedia = "Video & audio"
    override val homeFilterFiles = "File"
    override val homeFilterTorrents = "Torrent"

    override val homeCantResumeTitle = "Unduhan ini tidak bisa dilanjutkan"
    override val homeCantResumeClipText =
        "Klip yang dipotong saat mengunduh tidak bisa melanjutkan dari posisi terakhir. " +
                "Jika dijeda sekarang, klip akan dimulai lagi dari awal saat dilanjutkan."
    override val homeCantResumeServerText =
        "Server tidak mendukung lanjutan unduhan. Jika dijeda sekarang, unduhan akan " +
                "dimulai lagi dari awal saat dilanjutkan."
    override val homePauseAnyway = "Tetap jeda"
    override val homeKeepDownloading = "Terus mengunduh"
    override val homeMagnetLink = "Tautan magnet"
    override val homeClipboardOfferTitle = "Unduh tautan yang Anda salin?"
    override val homeClipboardOfferHint = "Matikan ini di Pengaturan > Umum > Pantau papan klip."
    override val homeDownload = "Unduh"
    override val homeNotNow = "Nanti saja"
    override val bridgePairTitle = "Izinkan ekstensi browser ini?"
    override val bridgePairText =
        "Sebuah ekstensi browser meminta mengirim tautan ke FetchJet. Izinkan hanya jika Anda baru memasang atau mengatur ulang ekstensi FetchJet."
    override val bridgePairOldText =
        "Ekstensi FetchJet versi lama ingin mengirim tautan. Izinkan jika itu milik Anda; memperbarui ekstensi membuat pertanyaan ini tidak muncul lagi."
    override val bridgePairAllow = "Izinkan"
    override val bridgePairBlock = "Blokir"
    override val extConnectionsGroup = "Koneksi browser"
    override val extConnectionsFooter =
        "Ekstensi browser yang Anda izinkan atau blokir untuk mengirim tautan ke FetchJet."
    override val extAllowedLabel = "Diizinkan"
    override val extBlockedLabel = "Diblokir"
    override val extRevoke = "Cabut"
    override val extUnblock = "Buka blokir"
    override val homeBrowserFallbackTitle = "Unduh di browser saja?"
    override val homeBrowserFallbackText =
        "Anda membatalkan file ini di FetchJet. Browser menghentikan unduhannya sendiri saat " +
                "menyerahkan tautan, jadi unduhan hanya berlanjut jika Anda mengirimnya kembali."
    override val homeDownloadInBrowser = "Unduh di browser"
    override val homeNoDiscard = "Tidak, buang"

    override val homeQueueTitle = "Antrean"
    override val homeQueueFooter =
        "Unduhan tambahan menunggu dan mulai sendiri, yang terlama lebih dulu, saat satu unduhan selesai. " +
                "\"Mulai sekarang\" pada unduhan yang menunggu akan melewati antrean."
    override val homeDownloadsAtSameTime = "Unduhan bersamaan"
    override val homeNoLimit = "Tanpa batas"
    override fun homeAtATime(count: Int) = "$count sekaligus"

    override val homePiecesLabel = "Bagian"
    override fun homeSegmentsCount(count: Int) = "$count segmen"
    override fun homePiecesCount(count: Int) = "$count bagian"
    override fun homeActiveCount(count: Int) = "$count aktif"
    override fun homeWaitingCount(count: Int) = "$count menunggu"
    override fun homeDoneCount(count: Int) = "$count selesai"
    override fun homeSegmentsProgress(finished: Int, total: Int, downloading: String?) =
        "Segmen $finished/$total" + (downloading?.let { " · mengunduh $it" } ?: "")

    override fun homeRetryingNote(note: String?) = "$note · mencoba lagi"
    override fun clipStartAfterEnd(start: String, end: String) =
        "Waktu mulai ($start) harus sebelum waktu akhir ($end)"

    override fun clipEndExceedsVideo(end: String, duration: String) =
        "Waktu akhir ($end) melebihi durasi video ($duration)"

    override fun clipStartExceedsVideo(duration: String) =
        "Waktu mulai melebihi durasi video ($duration)"

    override val setStartTimeTitle = "Atur waktu mulai (dari)"
    override val setEndTimeTitle = "Atur waktu akhir (sampai)"
    override val addDialogTitle = "Tambah unduhan baru"
    override val addDialogSubtitle = "Masukkan tautan untuk mulai mengunduh"
    override val addLinkLabel = "Tautan"
    override val addImportTxt = "Impor .txt"
    override val openTorrentFile = "Buka file .torrent"
    override val urlPlaceholder = "Tempel tautan atau magnet"
    override val addPaste = "Tempel"
    override val addDownloadEngine = "Mesin unduhan"
    override val engineDirect = "Langsung"
    override val engineMedia = "Media"
    override val engineTorrent = "Torrent"
    override val addFileNameLabel = "Nama file"
    override val findingName = "Mencari nama…"
    override val saveDestination = "Folder tujuan"
    override val quickFolderDownloads = "Unduhan"
    override val quickFolderDocuments = "Dokumen"
    override val quickFolderMedia = "Media"
    override val quickFolderDesktop = "Desktop"
    override val addRetryInspect = "Periksa ulang"
    override val addStarting = "Memulai..."
    override val addInspecting = "Memeriksa..."
    override val addInspectionFailed = "Pemeriksaan gagal"
    override val addEnterUrl = "Masukkan URL"
    override fun scheduleLinks(n: Int) = "Jadwalkan $n tautan"
    override fun downloadLinks(n: Int) = "Unduh $n tautan"
    override val inspectVideoFirst = "Periksa video dulu"
    override val inspectTorrentFirst = "Periksa torrent dulu"
    override val invalidTimeRange = "Rentang waktu tidak valid"
    override fun scheduleVideos(n: Int) = "Jadwalkan $n video"
    override fun downloadVideos(n: Int) = "Unduh $n video"
    override val addScheduleDownload = "Jadwalkan unduhan"
    override val addStartDownload = "Mulai unduh"
    override val availableAudioFormats = "Format audio tersedia"
    override val availableQualities = "Kualitas tersedia"
    override val subtitlesTitle = "Subtitle"
    override val subtitlesAuto = "otomatis"
    override val subtitlesEmbedInVideo = "Sematkan ke video"
    override val subtitlesSeparateFile = "File terpisah"
    override val addSelectAll = "Pilih semua"
    override val addClearSelection = "Hapus"
    override fun selectedOfAvailable(selected: Int, total: Int) =
        "Dipilih: $selected dari $total yang tersedia"

    override fun qualityFormatLine(format: String, details: String) = "Format: $format$details"
    override val inspectingVideoQualities = "Memeriksa kualitas video..."
    override fun readingCourseLectures(done: Int, total: Int) =
        "Membaca materi kursus: ${done} / ${total}"

    override val readingCourseLecturesHint =
        "Ekstensi browser membacanya di tab kursus: biarkan tetap terbuka"
    override val inspectingVideoQualitiesHint =
        "Memindai resolusi video dan aliran audio yang tersedia"
    override val videoInspectionFailed = "Pemeriksaan video gagal"
    override val addRetryInspection = "Coba periksa lagi"
    override val mediaEngineStarting = "Mesin media sedang dimulai..."
    override val mediaEngineNotReady = "Mesin media belum siap"
    override val mediaEngineRequiresYtDlp =
        "Ekstraksi aliran video memerlukan yt-dlp yang terpasang atau tertanam."
    override val preparingEngine = "Menyiapkan mesin..."
    override val startMediaEngine = "Unduh / mulai mesin media"
    override val inspectAudioFormats = "Periksa & pilih format audio"
    override val inspectVideoQuality = "Periksa & pilih kualitas video"
    override fun clipSectionTitle(isAudio: Boolean) =
        if (isAudio) "Potong bagian audio (opsional)" else "Potong bagian video (opsional)"

    override val clipSectionHint = "Unduh hanya rentang waktu tertentu"
    override fun mediaDurationFullRange(isAudio: Boolean, clock: String, friendly: String) =
        "Durasi ${if (isAudio) "audio" else "video"}: $clock ($friendly) - Seluruh rentang dipilih secara default"

    override val clipStartTimeFrom = "Waktu mulai (dari)"
    override val clipEndTimeTo = "Waktu akhir (sampai)"
    override fun fullMediaChip(isAudio: Boolean, duration: String) =
        if (isAudio) "Audio penuh ($duration)" else "Video penuh ($duration)"

    override val clipFirst5Min = "5 mnt pertama"
    override val clipFirst15Min = "15 mnt pertama"
    override val clipFirst30Min = "30 mnt pertama"
    override val clipFirst1Hour = "1 jam pertama"
    override val clipModeTitle = "Cara mengunduh klip"
    override val clipOnlyTitle = "Hanya klip"
    override val clipOnlyLine1 = "Hanya mengunduh bagian yang Anda pilih"
    override val clipOnlyLine2 = "Lebih lambat: YouTube membatasi kecepatan"
    override val clipOnlyLine3 = "Jeda akan memulai klip dari awal"
    override fun fullThenCutTitle(isAudio: Boolean) =
        if (isAudio) "Audio penuh, lalu potong" else "Video penuh, lalu potong"

    override fun fullThenCutLine1(isAudio: Boolean, sizeText: String) =
        if (isAudio) "Mengunduh seluruh audio$sizeText" else "Mengunduh seluruh video$sizeText"

    override val fullThenCutLine2 = "Lebih cepat: kecepatan penuh"
    override val fullThenCutLine3 = "Lalu hanya menyimpan bagian Anda"
    override val fullThenCutLine4 = "Jeda / lanjutkan berfungsi"
    override val inspectingTorrent = "Memeriksa metadata torrent..."
    override val inspectingTorrentHint = "Mengambil daftar file dan peer"
    override val torrentInspectionFailed = "Pemeriksaan torrent gagal"
    override val inspectTorrentFiles = "Periksa file torrent"
    override val disableTorrentUpload = "Matikan unggahan torrent (mode hanya unduh)"
    override val torrentFiles = "File torrent"
    override val addDeselectAll = "Batalkan semua"
    override val torrentPriorityHigh = "Tinggi"
    override val torrentPriorityLow = "Rendah"
    override val torrentPriorityNormal = "Normal"
    override fun selectedOfLinks(selected: Int, total: Int) = "$selected dari $total tautan"
    override val readingTorrentFiles = "Membaca file torrent…"
    override fun couldNotReadTorrentFiles(error: String) =
        "Tidak dapat membaca file-nya ($error); semuanya akan diunduh."

    override val linkTagVideo = "VIDEO"
    override val linkTagAudio = "AUDIO"
    override val linkTagTorrent = "TORRENT"
    override val linkTagFile = "FILE"
    override val linkListMagnet = "Tautan magnet"
    override fun linkListYouTubeVideo(id: String) = "Video YouTube $id"
    override fun playlistSummary(videos: Int, selected: Int) =
        "Playlist · $videos video · $selected dipilih"

    override val playlistFolder = "Simpan di folder"
    override val playlistFolderHint =
        "Setiap file masuk ke folder ini. Kosongkan untuk menyimpannya langsung."
    override val qualityForAllVideos = "Kualitas untuk semua video"
    override val qualityForAllVideosHint =
        "Jika video tidak punya kualitas ini, dipilih kualitas lebih rendah yang terdekat."
    override val downloadOnlyThisVideo = "Unduh video ini saja"
    override val whenToStart = "Kapan dimulai"
    override val scheduleNow = "Sekarang"
    override val scheduleIn30Min = "30 mnt lagi"
    override val scheduleIn1Hour = "1 jam lagi"
    override val scheduleAtTime = "Pada jam tertentu…"
    override val scheduleDayLabel = "Hari"
    override val scheduleToday = "Hari ini"
    override val scheduleTomorrow = "Besok"
    override val scheduleIn2Days = "2 hari lagi"
    override val scheduleIn3Days = "3 hari lagi"
    override val scheduleInAWeek = "1 minggu lagi"
    override val scheduleTimePassed =
        "Waktu itu sudah lewat hari ini. Pilih jam yang lebih lambat atau hari lain."
    override val scheduleHourLabel = "Jam"
    override val scheduleMinuteLabel = "Menit"
    override val scheduleSet = "Atur"
    override val scheduleSetTime = "Atur waktu"
    override fun scheduleStartsAt(time: String) = "Mulai $time (FetchJet harus berjalan)"
    override val addConnectionsTitle = "Koneksi"
    override val connectionsAuto = "Otomatis"
    override fun connectionsAutoWithDefault(n: Int) = "Otomatis ($n)"
    override fun pickerExceedsVideo(duration: String) = "Melebihi durasi video ($duration)"
    override fun pickerMustBeAfterStart(start: String) = "Harus setelah waktu mulai ($start)"
    override val pickerStartZero = "Awal (00:00)"
    override val pickerHours = "Jam"
    override val pickerMinutes = "Menit"
    override val pickerSeconds = "Detik"
    override fun pickerSelectedTime(time: String, summary: String) =
        "Waktu dipilih: $time ($summary)"

    override fun pickerEndOfVideo(time: String) = "Akhir video ($time)"
    override fun pickerMin(time: String) = "Min ($time)"
    override fun pickerApply(time: String) = "Terapkan ($time)"
    override val pickerInvalidTime = "Waktu tidak valid"
    override val dirPickerSelectFolder = "Pilih folder"
    override val dirPickerUp = "Naik"
    override fun dirPickerSubfolders(n: Int) = "Subfolder ($n)"
    override val dirPickerHiddenOn = "Tersembunyi: Aktif"
    override val dirPickerHiddenOff = "Tersembunyi: Nonaktif"
    override val dirPickerNewFolder = "Folder baru"
    override val dirPickerFolderName = "Nama folder…"
    override val dirPickerCreateFailed = "Gagal membuat folder"
    override val dirPickerCreate = "Buat"
    override val dirPickerNoSubfolders =
        "Tidak ada subfolder di direktori ini.\nFile akan disimpan langsung di sini."
    override val dirPickerHiddenTag = "(tersembunyi)"
    override val speedLimitUnlimited = "Tanpa batas (maks)"
    override val speedPresetsTitle = "Prasetel kecepatan"
    override val speedPresetsFooter =
        "Atur kecepatan untuk Cepat, Sedang, dan Rendah. Pilih salah satu dengan cepat dari header; kura-kura selalu 512 KB/s."
    override val speedPresetHigh = "Tinggi"
    override val speedPresetFast = "Cepat"
    override val speedPresetMedium = "Sedang"
    override val speedPresetLow = "Rendah"
    override val speedScheduleTitle = "Jadwal kecepatan"
    override val speedScheduleFooter =
        "Batasi kecepatan pada jam tertentu; tanpa batas di luar itu."
    override val speedScheduleAdd = "Tambah rentang waktu"
    override val speedScheduleFrom = "Dari"
    override val speedScheduleTo = "Sampai"
    override val speedScheduleActive = "Batas terjadwal aktif"
    override val speedScheduleEmpty = "Belum ada rentang waktu — tambahkan satu."
    override val priorityLabel = "Prioritas"
    override val priorityHigh = "Tinggi"
    override val priorityNormal = "Normal"
    override val priorityLow = "Rendah"
    override val checksumLabel = "Checksum (opsional)"
    override val checksumHint = "sha256 atau md5"
    override val checksumVerified = "Checksum terverifikasi"
    override val checksumMismatch = "Checksum tidak cocok"
    override val checksumChecking = "Memverifikasi checksum…"
    override val speedLimitNoLimit = "Tanpa batas (kecepatan maks)"
    override val speedLimitCustom = "Kustom..."
    override val speedLimitValue = "Nilai"
    override val scheduleAm = "AM"
    override val schedulePm = "PM"
    override val detailsChipMedia = "Media"
    override val detailsChipTorrent = "Torrent"
    override val detailsChipFile = "File"
    override val detailsChipResumable = "Bisa dilanjutkan"
    override val detailsChipNoResume = "Tidak bisa dilanjutkan"
    override fun detailsClipChip(start: String, end: String?, cutAfterDownload: Boolean) =
        "Klip $start–${end ?: "Akhir"}" + if (cutAfterDownload) " · dipotong setelah diunduh" else ""

    override fun detailsTorrentFiles(selected: Int, total: Int) =
        "File torrent ($selected dipilih / $total)"

    override val detailsPriorityHigh = "Tinggi"
    override val detailsPriorityLow = "Rendah"
    override val detailsPriorityNormal = "Normal"
    override val detailsStatusCompleted = "Selesai"
    override val detailsStatusPaused = "Dijeda"
    override val detailsStatusFailed = "Gagal"
    override val detailsStatusWaiting = "Menunggu"
    override val detailsStatusQueued = "Dalam antrean"
    override val detailsStatusNotStarted = "Belum dimulai"
    override val detailsUnknownSize = "Tidak diketahui"
    override val detailsMergingAudioVideo = "Menggabungkan audio & video..."
    override fun detailsTorrentSpeedUploadDisabled(download: String) =
        "Unduh: $download (Unggah nonaktif)"

    override fun detailsTorrentSpeed(download: String, upload: String) =
        "Unduh: $download | Unggah: $upload"

    override val detailsConnecting = "Menghubungkan..."
    override fun detailsStartsAt(time: String) = "Mulai $time"
    override fun detailsWaitingInQueuePosition(position: Int) = "Menunggu di antrean (#$position)"
    override val detailsWaitingInQueue = "Menunggu di antrean"
    override val detailsDownloaded = "Terunduh"
    override val detailsSpeed = "Kecepatan"
    override val detailsState = "Status"
    override val detailsTimeLeft = "Sisa waktu"
    override val detailsAdded = "Ditambahkan"
    override val detailsSavedTo = "Disimpan di"
    override val detailsSource = "Sumber"
    override val detailsPage = "Halaman"
    override val detailsOpenFile = "Buka file"
    override val detailsOpenFolder = "Buka folder"
    override val detailsStartNow = "Mulai sekarang"
    override val detailsRemoveFromList = "Hapus dari daftar"
    override val detailsCancelDownload = "Batalkan unduhan"
    override val detailsHeaderDownload = "Unduhan"
    override val detailsHeaderDetails = "DETAIL"
    override fun detailsAddedToday(clock: String) = "Hari ini $clock"
    override fun detailsAddedYesterday(clock: String) = "Kemarin $clock"

    override fun removeDialogTitle(count: Int) =
        if (count > 1) "Hapus $count unduhan" else "Hapus unduhan"

    override fun removeDialogMessage(count: Int) =
        if (count > 1) "Yakin ingin menghapus $count tugas unduhan ini?"
        else "Yakin ingin menghapus tugas unduhan ini?"

    override val removeDialogAlsoDeleteFiles = "Hapus juga file yang sudah diunduh dari disk"

    override val duplicateDialogTitle = "Unduhan sudah ada"
    override val duplicateDialogSubtitle = "Item ini sudah ada di antrean unduhan Anda"
    override fun duplicateDialogStatus(status: String) = "Status: $status"
    override val duplicateDialogQuestion =
        "Lanjutkan unduhan yang ada atau mulai lagi dari awal?"
    override val duplicateDialogResumeExisting = "Lanjutkan yang ada"
    override val duplicateDialogRedownload = "Unduh ulang (dari awal)"

    override val overwriteDialogTitle = "File sudah ada"
    override val overwriteDialogSubtitle = "File dengan nama ini sudah ada di folder tujuan"
    override val overwriteDialogFileNameLabel = "Nama file: "
    override val overwriteDialogFolderLabel = "Folder: "
    override val overwriteDialogNewCopyLabel = "Salinan baru: "
    override val overwriteDialogQuestion =
        "Simpan unduhan ini sebagai salinan baru atau timpa file yang ada?"

    override fun overwriteDialogSaveAsCopy(name: String) = "Simpan sebagai salinan ($name)"
    override val overwriteDialogOverwriteExisting = "Timpa file yang ada"

    override val desktopWindowTagline = "Pengunduh multi-mesin berkecepatan tinggi"
    override val desktopTrayIdleTooltip = "FetchJet - Berjalan di latar belakang"
    override val desktopTrayDownloadsHeader = "Unduhan FetchJet:"
    override fun desktopTrayMore(count: Int) = "+$count lainnya"
    override val desktopTrayShowApp = "Tampilkan aplikasi"
    override val desktopTrayCloseApp = "Tutup aplikasi"
    override val sectionGeneral = "Umum"
    override val sectionGeneralBlurb = "Tampilan aplikasi dan cara memulainya"
    override val sectionDownloads = "Unduhan"
    override val sectionDownloadsBlurb = "Lokasi file dan berapa yang berjalan bersamaan"
    override val sectionBrowser = "Ekstensi browser"
    override val sectionBrowserBlurb = "Kirim tautan dan video dari browser ke FetchJet"
    override val sectionSpeed = "Batas kecepatan"
    override val sectionSpeedBlurb = "Batasi bandwidth yang dipakai FetchJet"
    override val sectionMedia = "Alat media"
    override val sectionMediaBlurb = "yt-dlp dan FFmpeg untuk situs video"
    override val sectionTorrent = "BitTorrent"
    override val sectionTorrentBlurb = "Peer, unggahan, dan mesin torrent"
    override val sectionUpdates = "Pembaruan"
    override val sectionUpdatesBlurb = "Versi Anda dan rilis baru"
    override val summaryNoQueueLimit = "tanpa batas"
    override fun summaryAtATime(n: Int) = "$n sekaligus"
    override val summaryNoSpeedLimit = "Tanpa batas"
    override val summarySetupNeeded = "Perlu disiapkan"
    override val summaryUploadingOff = "Unggahan nonaktif"
    override val summaryUploadingOn = "Unggahan aktif"
    override fun summaryVersionAvailable(version: String) = "$version tersedia"
    override fun summaryVersion(version: String) = "Versi $version"
    override val changesSavedAutomatically = "Perubahan disimpan otomatis."
    override val settingsAllSettings = "Semua pengaturan"

    override val clipboardGroup = "Papan klip"
    override val clipboardFooter =
        "Salin tautan file (zip, mp4, pdf, apk…) atau tautan magnet di mana saja, dan FetchJet akan menawarkan untuk mengunduhnya."
    override val watchClipboard = "Pantau papan klip"
    override val watchClipboardOn = "Aktif · tautan file yang disalin ditawarkan untuk diunduh"
    override val settingOff = "Nonaktif"
    override val alsoVideoPages = "Juga halaman video"
    override val alsoVideoPagesHint =
        "Tautan YouTube, Facebook, TikTok, Vimeo… juga ditawarkan (bukan hanya file)"
    override val diagnosticsGroup = "Diagnostik"
    override fun logSavedTo(path: String) =
        "Tersimpan: $path — kirim file ini saat melaporkan masalah."

    override val diagnosticsFooter =
        "Menyimpan log aplikasi (apa yang dilakukan yt-dlp dan mesin) ke folder Unduhan."
    override val saveLogToDownloads = "Simpan log ke Unduhan"
    override val logCouldNotSave = "tidak dapat disimpan"
    override val appearanceGroup = "Tampilan"
    override val themeTitle = "Tema"
    override val themeHint = "Tampilan terang atau gelap untuk semua layar"
    override val settingsThemeDark = "Gelap"
    override val settingsThemeSystem = "Sistem"
    override val settingsThemeLight = "Terang"
    override val speedShownIn = "Satuan kecepatan"
    override val speedShownInHint =
        "Byte (MB/s) seperti ukuran file, atau bit (Mbps) seperti paket internet Anda"

    override val saveLocationGroup = "Lokasi penyimpanan"
    override val saveLocationFooter =
        "Unduhan baru disimpan di sini kecuali Anda memilih folder lain saat menambahkannya."
    override val settingsFolderPath = "Jalur folder"
    override val settingsChooseFolder = "Pilih folder…"
    override val settingsUseDownloads = "Gunakan Unduhan"
    override val settingsConnectionsGroup = "Koneksi"
    override val connectionsFooter =
        "Jumlah bagian untuk memecah unduhan langsung. Tiap bagian adalah satu koneksi; saat satu selesai, ia mengambil separuh dari bagian tersisa yang terbesar, jadi tidak ada yang menganggur. Otomatis memilih berdasarkan ukuran file (4–8). Beberapa server membatasi koneksi per klien."
    override val settingsConnectionsPerDownload = "Koneksi per unduhan"
    override val settingsConnectionsAuto = "Otomatis"
    override val organizeGroup = "Atur"
    override val organizeFooter =
        "Subfolder dibuat di dalam lokasi penyimpanan: Video, Musik, Arsip, Dokumen, Program, Gambar, Torrent."
    override val sortIntoFolders = "Pisahkan ke folder menurut jenis"
    override val sortIntoFoldersOn = "Aktif · mis. Downloads/Video/kuliah.mp4"
    override val sortIntoFoldersOff = "Nonaktif · semua langsung masuk ke folder"

    override val speedLimitsFooter =
        "Langsung diterapkan dan dibagi di antara unduhan yang sedang berjalan."
    override val speedLimitsFooterIos =
        "Langsung diterapkan dan dibagi di antara unduhan yang sedang berjalan. Di iOS, unduhan HTTP yang sudah berjalan di latar belakang tetap dengan kecepatannya; batas berlaku untuk unduhan yang dimulai sesudahnya."
    override val speedLimitDownload = "Unduh"
    override val speedLimitUploadTorrents = "Unggah (torrent)"

    override val torrentSharingGroup = "Berbagi"
    override val downloadOnlyNoUpload = "Hanya unduh (jangan unggah)"
    override val downloadOnlyNoUploadHint =
        "Menghemat bandwidth unggah dan kuota data. Beberapa tracker memperlambat unduhan untuk peer yang tidak berbagi."
    override val torrentEngineGroup = "Mesin"
    override val settingsReady = "Siap"
    override val settingsNotReady = "Belum siap"

    override val extInstallGroup = "Pasang"
    override val extensionInstallFooter =
        "Dengan ekstensi, tombol \"Unduh dengan FetchJet\" muncul pada video dan tautan, dan browser dapat menyerahkan unduhannya ke FetchJet. Ekstensi hanya berkomunikasi dengan aplikasi ini di perangkat Anda."
    override val edgeAddedHint =
        "Ditambahkan · mulai ulang Edge lalu klik \"Aktifkan ekstensi\" saat diminta"
    override val edgeFromStoreHint = "Dari Microsoft Edge Add-ons · diperbarui otomatis"
    override val openInEdgeStore = "Buka di toko Edge"
    override val edgeAddedNote =
        "Selesai: mulai ulang Edge, nanti akan diminta untuk mengaktifkan FetchJet."
    override val extAddToEdge = "Tambahkan ke Edge"
    override val firefoxFromStoreHint = "Dari Firefox Add-ons · diperbarui otomatis"
    override val extAddToFirefox = "Tambahkan ke Firefox"
    override val chromeZipHint =
        "Tidak ada di Chrome Web Store: pasang file .zip dalam mode pengembang (langkahnya ada di halaman). Tidak diperbarui otomatis."
    override val getTheZip = "Ambil file .zip"
    override val firefoxAndroidHint =
        "Buka ini di Firefox pada ponsel ini. Tautan yang Anda kirim langsung masuk ke aplikasi ini."
    override val keepReadyForBrowser = "Tetap siap untuk browser"
    override val keepReadyOn =
        "Kualitas video langsung tampil di Firefox. Notifikasi senyap tetap muncul selama ini aktif."
    override val keepReadyOff =
        "Nonaktif: Firefox membuka FetchJet dan Anda memilih kualitas di sini. Aktif: kualitas tampil di Firefox (menyisakan notifikasi)."
    override val popUpOverBrowser = "Muncul di atas browser"
    override val popUpOverBrowserOn =
        "Aktif · FetchJet terbuka sendiri saat Firefox mengirim tautan"
    override val popUpOverBrowserOff =
        "Nonaktif · notifikasi muncul sebagai gantinya; ketuk untuk mengizinkan \"tampilkan di atas aplikasi lain\""

    override val mediaToolsGroup = "Alat"
    override val ytDlpHint = "Menemukan video dan audio di YouTube dan situs lain"
    override val settingsMissing = "Tidak ada"
    override val ffmpegHint =
        "Menggabungkan video + audio (1080p ke atas), memotong klip, membuat MP3"
    override val ffmpegMissing = "Tidak ada · maksimal 720p"
    override fun jsRuntimeTitle(name: String) = "Runtime JS · $name"
    override val jsRuntimeHint =
        "Menyelesaikan pemeriksaan YouTube agar semua kualitas muncul, lebih cepat"

    override fun missingPress(button: String) = "Tidak ada · tekan $button"
    override val mediaToolsFolder = "Folder alat"
    override val mediaUpdatingTools = "Memperbarui…"
    override val mediaDownloadingTools = "Mengunduh alat…"
    override fun mediaDownloadingTool(name: String) = "Mengunduh $name…"
    override fun mediaDownloadingToolPercent(name: String, percent: Int) =
        "Mengunduh $name… $percent%"

    override val mediaUpdateTools = "Perbarui alat"
    override val mediaDownloadTheTools = "Unduh alat"

    override val updateNewVersionAvailable = "Versi baru tersedia"
    override fun updateVersionLine(version: String, current: String) =
        "FetchJet $version  ·  versi Anda $current"

    override val updateWhatsNew = "Yang baru"
    override val updateNoReleaseNotes = "Tidak ada catatan rilis."
    override fun downloadingUpdate(percent: Int) = "Mengunduh pembaruan… $percent%"
    override val updateDownloadedAndroid =
        "Pembaruan sudah diunduh. Android akan meminta Anda mengonfirmasi pemasangan; unduhan Anda tetap ada di daftar."
    override val updateDownloadedDesktop =
        "Pembaruan sudah diunduh. FetchJet akan ditutup, memasangnya, lalu terbuka lagi. Unduhan yang berjalan dijeda dan bisa dilanjutkan."
    override val storeInstallsItselfOpenNow =
        "Microsoft Store memasangnya sendiri. Buka Store untuk memperbarui sekarang."
    override val updateInstall = "Pasang"
    override val updateRestartAndUpdate = "Mulai ulang & perbarui"
    override val updateDownloading = "Mengunduh…"
    override val updateDownloadAndInstall = "Unduh & pasang"
    override val updateOpenMicrosoftStore = "Buka Microsoft Store"
    override fun updateDownloadWithSize(size: String) = "Unduh $size"
    override val updateOpenReleasePage = "Buka halaman rilis"
    override val updateSkipThisVersion = "Lewati versi ini"
    override val updateLater = "Nanti"
    override fun versionIsAvailable(version: String) = "Versi $version tersedia"
    override val youHaveLatest = "Anda memakai versi yang terpasang di sini"
    override val updateSeeWhatsNew = "Lihat yang baru"
    override val updateChecking = "Memeriksa…"
    override val updateCheckNow = "Periksa sekarang"
    override val checkUpdatesAutomatically = "Periksa pembaruan otomatis"
    override val checkUpdatesAutomaticallyHint =
        "Mencari versi baru saat FetchJet dimulai (paling banyak dua kali sehari)."
    override val downloadUpdatesAutomatically = "Unduh pembaruan otomatis"
    override val downloadUpdatesAutomaticallyHintAndroid =
        "Mengunduh versi baru di latar belakang; Anda hanya mengonfirmasi pemasangan."
    override val downloadUpdatesAutomaticallyHint =
        "Mengunduh versi baru di latar belakang; Anda hanya mengonfirmasi mulai ulang."
    override val installingUpdates = "Memasang pembaruan"
    override val installingUpdatesStore =
        "Microsoft Store memasang versi baru sendiri; FetchJet memberi tahu Anda saat ada yang baru."
    override val installingUpdatesManual =
        "Di perangkat ini FetchJet memberi tahu versi baru dan menautkannya; Anda yang memasangnya."
    override val updateReleaseNotes = "Catatan rilis"
    override val updateReleaseNotesHint = "Setiap versi dan perubahannya"
    override val installHintReleasePage = "Buka halaman rilis untuk mengunduh versi baru."
    override val installHintApk = "Unduh APK lalu buka untuk memasang pembaruan di atas versi ini."
    override val installHintIpa =
        "Unduh file .ipa dan pasang dengan AltStore / Sideloadly, seperti saat Anda memasang FetchJet."
    override val installHintDmg =
        "Unduh file .dmg, buka, lalu seret FetchJet ke Applications (ganti yang lama)."
    override val installHintDeb =
        "Unduh file .deb lalu pasang (klik dua kali, atau sudo apt install ./file.deb)."
    override val installHintOther = "Unduh versi baru dan pasang di atas versi ini."

    override val startupGroup = "Saat memulai"
    override val startupStoreFooter =
        "Versi Store dijalankan oleh Windows: aktifkan FetchJet di Settings > Apps > Startup."
    override val startupStartAtLogin = "Jalankan FetchJet saat masuk"
    override val startupManagedByWindows = "Dikelola oleh Windows"
    override val startupOpenWindowsSettings = "Buka pengaturan Windows"
    override val startupStartHidden = "Mulai tersembunyi di tray"
    override val startupStartHiddenHint =
        "Saat dijalankan ketika masuk, tetap di tray alih-alih membuka jendela."
    override val startupStartAtLoginHint =
        "Unduhan terjadwal dan dalam antrean tetap berjalan, dan ekstensi browser selalu menemukan aplikasi."

    override fun startupCouldNotChange(message: String) =
        "Tidak dapat mengubah pengaturan: $message"

    override val whenDoneGroup = "Saat unduhan selesai"
    override val whenDoneHint = "Hanya untuk sesi ini. Sebelumnya ada 60 detik untuk membatalkan."
    override val whenDoneNothing = "Tidak melakukan apa pun"
    override val whenDoneExit = "Keluar dari FetchJet"
    override val whenDoneSleep = "Tidur"
    override val whenDoneShutDown = "Matikan"
    override val whenDoneTitle = "Semua unduhan selesai"
    override fun whenDoneExitIn(seconds: Int) = "FetchJet akan keluar dalam ${seconds} dtk."
    override fun whenDoneSleepIn(seconds: Int) = "Komputer akan tidur dalam ${seconds} dtk."
    override fun whenDoneShutDownIn(seconds: Int) = "Komputer akan dimatikan dalam ${seconds} dtk."
    override val whenDoneNow = "Sekarang"
    override fun whenDoneBanner(action: String) = "Saat unduhan selesai: ${action}"
    override fun whenDoneFailed(message: String) = "Tidak bisa: ${message}"
    override val sectionNetwork = "Jaringan"
    override val sectionNetworkBlurb = "Proxy dan login situs"
    override val netProxyGroup = "Proxy"
    override val netProxyOff = "Mati"
    override val netProxyHost = "Server"
    override val netProxyPort = "Port"
    override val netProxyUser = "Nama pengguna (opsional)"
    override val netProxyPassword = "Kata sandi"
    override val netUser = "Nama pengguna"
    override val netSave = "Simpan"
    override val netSaved = "Tersimpan"
    override val netProxyHint =
        "Dipakai oleh unduhan, unduhan video (yt-dlp), dan torrent. Berlaku untuk unduhan yang dimulai setelah disimpan."
    override val netProxyHintIos =
        "Dipakai oleh unduhan dan unduhan video. Torrent di iOS tersambung langsung. Berlaku untuk unduhan yang dimulai setelah disimpan."
    override val netProxyBadPort = "Masukkan server dan port dari 1 sampai 65535"
    override val netLoginsGroup = "Login situs"
    override val netLoginsHint =
        "Untuk unduhan yang meminta nama pengguna dan kata sandi (HTTP basic auth). Sebuah situs juga mencakup subdomainnya."
    override val netLoginSite = "Situs (example.com)"
    override val netLoginAdd = "Tambah"
    override val netLoginsEmpty = "Belum ada login tersimpan"
    override val netRemove = "Hapus"
    override fun netLoginsCount(n: Int) = "${n} login"
    override val sysTabAllDownloads = "Semua unduhan"
    override val sysTabActive = "Aktif"
    override val sysTabCompleted = "Selesai"
    override val sysTabMediaExtractor = "Ekstraktor media"
    override val sysTabSettings = "Pengaturan"
    override val sysSortNewestFirst = "Terbaru dulu"
    override val sysSortOldestFirst = "Terlama dulu"
    override val sysSortLargestFirst = "Terbesar dulu"
    override val sysSortSmallestFirst = "Terkecil dulu"
    override val sysSortNameAz = "Nama A–Z"
    override val sysEnterValidUrlOrMagnet = "Masukkan URL atau tautan magnet yang valid"
    override val sysCannotStartVideoInspectionFailed =
        "Tidak dapat memulai unduhan: pemeriksaan video gagal."
    override val sysCannotStartTorrentInspectionFailed =
        "Tidak dapat memulai unduhan: pemeriksaan torrent gagal atau tidak ada file yang dipilih."
    override val sysClipEndExceedsDuration = "Waktu akhir klip melebihi durasi video."
    override val sysClipStartAfterEnd = "Waktu mulai klip harus sebelum waktu akhir."
    override fun sysStartedDownload(name: String) = "Unduhan dimulai: $name"
    override val sysFailedToQueueDownload = "Gagal menambahkan unduhan ke antrean"
    override fun sysQualitiesNotAdded(count: Int) = "$count kualitas tidak dapat ditambahkan."
    override fun sysAddedQualityDownloads(count: Int, title: String) =
        "Menambahkan $count unduhan dari \"$title\""

    override val sysSelectAtLeastOnePlaylistVideo = "Pilih setidaknya satu video dari playlist."
    override fun sysVideosNotAdded(count: Int) = "$count video tidak dapat ditambahkan."
    override fun sysAddedPlaylistVideos(count: Int, playlistTitle: String) =
        "Menambahkan $count video dari \"$playlistTitle\""

    override val sysSelectAtLeastOneLink = "Pilih setidaknya satu tautan."
    override fun sysAddedLinkDownloads(added: Int, skipped: Int) =
        "Menambahkan $added unduhan" + (if (skipped > 0) " ($skipped sudah ada di daftar atau gagal)" else "")

    override val sysCouldNotReadTorrent = "Tidak dapat membaca torrent"
    override fun sysCouldNotStartDownload(reason: String) = "Tidak dapat memulai unduhan: $reason"
    override val sysUnknownError = "galat tidak diketahui"
    override fun sysQueuedFromBrowser(added: Int, skipped: Int) =
        "$added unduhan dari browser masuk antrean" + (if (skipped > 0) " ($skipped dilewati)" else "")

    override val sysEnterTorrentOrMagnetFirst = "Masukkan torrent atau tautan magnet dulu"
    override val sysFailedToParseTorrentMetadata = "Gagal membaca metadata torrent"
    override val sysEnterMediaUrlFirst = "Masukkan URL media dulu"
    override fun sysUnableToFetchFormatsWithReason(reason: String) =
        "Tidak dapat mengambil format video: $reason"

    override val sysUnableToFetchFormats =
        "Tidak dapat mengambil format video. Periksa URL atau koneksi internet Anda."
    override val sysFailedToInspectMediaQualities = "Gagal memeriksa kualitas media"
    override fun sysTorrentEngineError(message: String) = "Galat mesin BitTorrent: $message"
    override fun sysTorrentEngineInstallFailed(message: String) =
        "Gagal memasang mesin BitTorrent: $message"

    override val sysMediaToolsDownloaded = "Alat media berhasil diunduh!"
    override val sysMediaToolsDownloadFailed = "Gagal mengunduh alat media"
    override val sysMediaToolsUpdated = "Alat media diperbarui"
    override fun sysSavedLinks(count: Int, path: String) = "Menyimpan $count tautan ke $path"
    override fun sysCouldNotSaveLinks(reason: String) = "Tidak dapat menyimpan tautan: $reason"
    override val sysErrorFallback = "galat"
    override val sysDownloadFailed = "Unduhan gagal"
    override val sysDownloadFailedNetwork = "Unduhan gagal karena galat jaringan"
    override val sysCouldNotStart = "Tidak dapat dimulai"
    override val sysUnknownErrorCapital = "Galat tidak diketahui"
    override fun sysCouldNotSaveFileTo(dir: String) = "Tidak dapat menyimpan file ke $dir"
    override val sysPlaylistFallbackTitle = "Playlist"
    override val sysPlaylistQualityBest = "Terbaik"
    override val sysPlaylistQualityAudioOnly = "Hanya audio"
    override val sysToolsReadyYtDlpFfmpeg =
        "Siap: yt-dlp & ffmpeg terpasang (dukungan penuh 1080p+)"
    override val sysToolsReadyYtDlpNoFfmpeg = "Siap: yt-dlp terpasang (tanpa ffmpeg - maks 720p)"
    override val sysToolsNotInstalled = "Belum terpasang. Klik 'Unduh alat' di bawah."
    override val sysToolsInstalledBuiltIn = "Terpasang & siap (bawaan)"
    override fun sysYtDlpVersionReady(version: String) = "yt-dlp $version (Siap)"
    override val sysMediaEngineBridgeNotRegistered = "Jembatan mesin media belum terdaftar"
    override val sysWebKitBuiltIn = "WebKit (bawaan)"
    override val sysTorrentEngineBuiltIn = "Siap: mesin BitTorrent bawaan"
    override fun sysTorrentEngineReadyVersion(version: String) =
        "Siap: mesin BitTorrent (libtorrent $version)"

    override fun sysTorrentEngineFailedToLoad(message: String) =
        "Mesin BitTorrent gagal dimuat: $message"

    override val sysTorrentEngineAvailable =
        "Siap: mesin BitTorrent tersedia (dimuat saat torrent pertama)"
    override val sysTorrentEngineMissing = "Mesin BitTorrent tidak ada. Klik 'Unduh alat' di bawah."
    override val sysTorrentEngineNotAvailable = "Mesin BitTorrent tidak tersedia di build ini"
    override fun sysCouldNotStartInstaller(message: String) =
        "Tidak dapat menjalankan penginstal: $message"

    override val sysCheckingForUpdates = "Memeriksa pembaruan…"
    override val sysNoPublishedRelease = "Belum ada rilis yang diterbitkan."
    override fun sysVersionNoChangesForDevice(version: String, current: String) =
        "Versi $version tidak punya perubahan untuk perangkat ini; Anda sudah memakai build terbaru untuknya ($current)."

    override fun sysVersionAvailable(version: String) = "Versi $version tersedia."
    override fun sysUpToDate(current: String) = "Aplikasi Anda sudah terbaru ($current)."
    override fun sysCouldNotCheckForUpdates(reason: String) =
        "Tidak dapat memeriksa pembaruan: $reason"

    override val sysNetworkError = "galat jaringan"
    override val sysUpdateNoInstallerForSystem =
        "Unduhan pembaruan gagal: rilis ini tidak punya penginstal untuk sistem ini."

    override fun sysDownloadingAsset(name: String) = "Mengunduh $name…"
    override fun sysVersionReadyToInstall(version: String) = "Versi $version siap dipasang."
    override fun sysUpdateDownloadFailed(message: String) = "Unduhan pembaruan gagal: $message"
    override val sysAllowUnknownAppsThenInstall =
        "izinkan \"Instal aplikasi tidak dikenal\" untuk FetchJet, lalu ketuk Pasang lagi."
    override val sysDownloadsChannelName = "Unduhan FetchJet"
    override val sysDownloadsChannelDescription = "Menampilkan kemajuan unduhan secara langsung"
    override val sysNotificationAppTitle = "FetchJet Downloader"
    override val sysAppRunningInBackground = "Aplikasi berjalan di latar belakang"
    override val sysNotificationDownloadsFallback = "Unduhan"
    override fun sysFilesDownloading(count: Int) = "$count file sedang diunduh"
    override fun sysPercentActive(percent: Int, count: Int) = "$percent% • $count aktif"
    override fun sysActiveCount(count: Int) = "$count aktif"
    override fun sysDownloadCompleteSize(size: String) = "Unduhan selesai • $size"
    override fun sysMoreDownloading(count: Int) = "+ $count lainnya sedang diunduh"
    override val sysComplete = "Selesai"
    override fun sysNotificationDownloadsTitle(speed: String) = "Unduhan FetchJet ($speed)"
    override fun sysDownloadingPercent(count: Int, percent: Int) = "$count mengunduh • $percent%"
    override val sysDownloadTaskFallback = "Tugas unduhan"
    override val sysBrowserReadyTitle = "FetchJet siap untuk browser Anda"
    override val sysBrowserReadyText = "Kualitas video langsung tampil di Firefox"
    override val sysTurnOff = "Matikan"
    override val sysBrowserReadyChannelName = "Ekstensi browser"
    override val sysBrowserReadyChannelDescription =
        "Ditampilkan selama FetchJet tetap siap untuk ekstensi browser"
    override val sysDownloadingFileFallback = "Mengunduh file"
    override val sysUnknownSize = "Tidak diketahui"
    override val sysStatusCompleted = "Selesai"
    override val sysStatusDownloading = "Mengunduh"
    override val sysDownloadCompleteTitle = "Unduhan selesai"
    override fun sysFinishedDownloading(size: String) = "Selesai diunduh • $size"
    override val sysSentToFetchJet = "Dikirim ke FetchJet"
    override val sysLinksChannelName = "Tautan dari browser"
    override val sysLinksChannelDescription =
        "Ketuk untuk membuka tautan yang baru diterima FetchJet"
    override val sysLinkReceivedTitle = "Tautan diterima"
    override val sysLinkReceivedText = "Ketuk untuk membukanya di FetchJet"

    override val stepCompleted = "Selesai"
    override val stepSeeding = "Membagikan"
    override val stepFetchingMetadata = "Mengambil metadata"
    override val stepConnectingToPeers = "Menghubungkan ke peer"
    override val stepCheckingFiles = "Memeriksa file"
    override val stepCheckingResumeData = "Memeriksa data lanjutan"
    override val stepDownloading = "Mengunduh"
    override val stepFinished = "Selesai"
    override val stepConnecting = "Menghubungkan"
    override val stepPaused = "Dijeda"
    override val stepError = "Kesalahan"
    override val stepResolvingStreams = "Mencari stream"
    override val stepDownloadingParts = "Mengunduh bagian"
    override val stepPreparingDownload = "Menyiapkan unduhan"
    override val stepClippingVideo = "Memotong video"
    override val stepDownloadingMedia = "Mengunduh media"
    override val stepAlreadyDownloaded = "Sudah diunduh"
    override val stepDownloadingVideo = "Mengunduh video"
    override val stepDownloadingAudio = "Mengunduh audio"
    override val stepMergingAudioVideo = "Menggabungkan audio dan video"
    override val stepConvertingToMp3 = "Mengonversi ke MP3"
    override val stepCuttingClip = "Memotong klip"
    override val stepSavingToFolder = "Menyimpan ke folder"
    override val stepFinalizingVideo = "Menyelesaikan video"
    override val stepConvertingVideo = "Mengonversi video"
    override val stepRepackagingVideo = "Mengemas ulang video"
    override val stepYtDlpFallback = "Beralih ke yt-dlp"
    override val stepNotPlainStream = "bukan stream biasa"
    override val stepSingleConnection = "Satu koneksi"
    override val stepServerNoRanges = "server tidak mendukung rentang"
    override val stepAgeRestrictedExpiredCookies =
        "Dibatasi usia: cookie kedaluwarsa di File > FetchJet"
    override val stepAgeRestrictedPlaceCookies =
        "Dibatasi usia: taruh cookies.txt di File > FetchJet"
    override val stepPreparingMediaEngine =
        "Menyiapkan mesin media dalam aplikasi (memulai Python, memuat yt-dlp)"
    override val stepMediaEngineNotStarted = "Mesin media belum dimulai. Ketuk \"Unduh alat\"."
    override fun stepNumber(step: Int, total: Int) = "Langkah ${step} dari ${total}"
    override fun stepSavingAs(ext: String) = "Menyimpan sebagai .${ext}"
    override fun stepJoiningParts(count: Int) = "Menggabungkan ${count} bagian menjadi MP4"
    override fun stepNoDataFor(seconds: Int) = "Tidak ada data selama ${seconds} dtk"
    override fun stepPeers(peers: Int, seeds: Int) = "${peers} peer (${seeds} seed)"
    override fun stepDownloadingFrom(host: String) = "Mengunduh dari ${host}"
    override fun stepMediaEngineNotReady(error: String) = "Mesin media belum siap: ${error}"
    override fun stepTorrentEngineFailed(error: String) = "Mesin BitTorrent gagal dimulai: ${error}"
    override fun stepReadyWith(details: String) = "Siap: ${details}"

    // --- Device sync (ROADMAP-1.3.0 item 4) ---
    override val sysDeviceSendTitle = "Send to device"
    override val sysDeviceNoDevices = "No devices found on your network"
    override val sysDeviceSearching = "Looking for devices…"
    override val sysDeviceModeLink = "Send link only"
    override val sysDeviceModeLinkDesc = "The other device downloads it from the start"
    override val sysDeviceModeTransfer = "Transfer what's downloaded"
    override val sysDeviceModeTransferDesc = "The other device continues from the same point"
    override val sysDeviceDeleteAfter = "Delete from this device after it's sent"
    override val sysDeviceSend = "Send"
    override val sysDevicePairTitle = "Pair device"
    override val sysDevicePairBody = "Check that this code is the same on both devices:"
    override val sysDevicePairConfirm = "Codes match"
    override val sysDevicePairConnecting = "Connecting…"
    override val sysDevicePairFailed = "Pairing failed"
    override val sysDeviceSending = "Sending…"
    override val sysDeviceWaitingAccept = "Waiting for the other device to accept…"
    override val sysDeviceSent = "Sent to {name}"
    override val sysDeviceSendFailed = "Couldn't send"
    override val sysDeviceReceived = "Download received from {name}"
    override val visibleToDevices = "Terlihat oleh perangkat lain"
    override val visibleToDevicesHint = "Perangkat FetchJet lain di jaringan ini dapat menemukan perangkat ini dan mengirim unduhan ke sini. Mati: Anda tetap bisa mengirim ke mereka."
    override val sysDeviceSearch = "Cari perangkat"
    override val sysDeviceOthers = "Perangkat lain"
    override val sysDeviceNoMatch = "Tidak ada perangkat yang cocok"
    override val sectionDevices = "Perangkat"
    override val sectionDevicesBlurb = "Kirim unduhan antar perangkat Anda"
    override val pairedDevicesTitle = "Perangkat terpasang"
    override val noPairedDevices = "Belum ada perangkat terpasang. Pasangkan dari Kirim ke perangkat pada unduhan."
    override val unpairDevice = "Lepas pasangan"
    override val summaryVisible = "Terlihat"
    override val summaryHidden = "Tersembunyi"
    override val blockDevice = "Blokir"
    override val unblockDevice = "Buka blokir"
    override val blockedDevicesTitle = "Perangkat diblokir"
    override val noBlockedDevices = "Tidak ada perangkat diblokir"
    override val pairRefusedBlocked = "Perangkat lain memblokir perangkat ini"
    override val pairRefusedWait = "Tunggu {seconds} dtk sebelum meminta lagi ke perangkat ini"
    override val pairRefusedBusy = "Perangkat lain sedang menjawab permintaan pemasangan lain. Coba lagi sebentar lagi."
    override val pairDeclined = "Perangkat lain menolak pemasangan. Kirim lagi untuk mencoba sekali lagi."
    override val pairExpired = "Perangkat lain tidak menjawab tepat waktu. Kirim lagi untuk mencoba sekali lagi."
    override val sysDevicePairedLabel = "Paired"
    override val sysDeviceThisDevice = "This device"
    override val sysDeviceManualConnect = "Connect by IP"
    override val sysDeviceConnect = "Connect"
}
