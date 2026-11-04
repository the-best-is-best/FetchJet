package com.michelle.raouf.fetch_jet.core.i18n.data_provider

object TurkishProvider : AppLocalization {
    override val localeCode = "tr"
    override val languageName = "Türkçe"
    override val ok = "Tamam"
    override val cancel = "İptal"
    override val close = "Kapat"
    override val save = "Kaydet"
    override val delete = "Sil"
    override val remove = "Kaldır"
    override val open = "Aç"
    override val copy = "Kopyala"
    override val copied = "Kopyalandı"
    override val retry = "Yeniden dene"
    override val pause = "Duraklat"
    override val resume = "Devam et"
    override val apply = "Uygula"
    override val reset = "Sıfırla"
    override val yes = "Evet"
    override val no = "Hayır"
    override val back = "Geri"
    override val done = "Bitti"
    override val selectAll = "Tümünü seç"
    override val selectNone = "Hiçbirini seçme"
    override val language = "Dil"
    override val languageHint = "Tüm ekranların dili. Sistem, cihazınızın dilini kullanır."
    override val systemLanguage = "Sistem"
    override val homeSettings = "Ayarlar"
    override val homeDownloads = "İndirmeler"

    override val homeTabAll = "Tümü"
    override val homeTabActive = "Etkin"
    override val homeTabDone = "Biten"
    override val homeTabTorrent = "Torrent"
    override val homeTabMedia = "Medya"
    override val homeSidebarTypeHeader = "TÜR"
    override val homeSearchDownloadsPlaceholder = "İndirmelerde ara"
    override val homeAdd = "Ekle"
    override val homeAddDownload = "İndirme ekle"
    override val homePauseAll = "Tümünü duraklat"
    override val homeResumeAll = "Tümünü sürdür"
    override fun homeSortBy(option: String) = "Sıralama: $option"
    override val homeLightTheme = "Açık tema"
    override val homeDarkTheme = "Koyu tema"

    override fun homeUpdatingMediaTools(tool: String, percent: Int?) =
        "Medya araçları güncelleniyor · $tool" + (percent?.let { " %$it" } ?: "…")

    override val homeSearchDownloadsByNamePlaceholder = "İndirmelerde ada göre ara…"
    override fun homeNoDownloadsMatch(query: String) = "\"$query\" ile eşleşen indirme yok"
    override val homeNoTasksInView = "Bu görünümde görev yok"
    override val homeTryAnotherName = "Başka bir ad deneyin"
    override val homeAddDownloadToGetStarted = "Başlamak için bir indirme ekleyin"
    override fun homeDownloadingWaitingSummary(running: Int, waiting: Int) =
        "$running indiriliyor · $waiting bekliyor"

    override fun homeQueueStatus(maxAtOnce: Int) =
        "Kuyruk: " + if (maxAtOnce <= 0) "sınırsız" else "aynı anda $maxAtOnce"

    override fun homeSpeedLimitStatus(limit: String?) = "Hız sınırı: " + (limit ?: "kapalı")
    override val homeGroupDownloading = "İndiriliyor"
    override val homeGroupWaiting = "Bekliyor"
    override val homeGroupPausedFailed = "Duraklatılan ve başarısız"
    override val homeGroupCompleted = "Tamamlanan"
    override fun homeSelectedCount(count: Int) = "$count seçili"
    override val homeExport = "Dışa aktar"

    override fun homeStartsAt(time: String) = "Başlangıç: $time"
    override val homeWaitingInQueue = "Kuyrukta bekliyor"
    override fun homeWaitingInQueuePosition(position: Int) = "Kuyrukta bekliyor (#$position)"
    override val homeConnecting = "Bağlanıyor…"
    override val homeStarting = "Başlatılıyor…"
    override fun homeTimeLeft(eta: String) = "$eta kaldı"
    override fun homePausedState(percent: Int, restartsOnResume: Boolean) =
        "Duraklatıldı · %$percent" + if (restartsOnResume) " · sürdürünce baştan başlar" else ""

    override fun homeFailedState(message: String?) = "Başarısız: " + (message ?: "hata")
    override fun homeCompletedState(size: String?) = "Tamamlandı" + (size?.let { " · $it" } ?: "")
    override val homeStartNow = "Şimdi başlat"
    override val homeColumnName = "AD"
    override val homeColumnSize = "BOYUT"
    override val homeColumnProgress = "İLERLEME"
    override val homeColumnSpeed = "HIZ"
    override val homeColumnLeft = "KALAN"
    override val homeFilterAllDownloads = "Tüm indirmeler"
    override val homeFilterDownloading = "İndiriliyor"
    override val homeFilterWaiting = "Bekliyor"
    override val homeFilterPaused = "Duraklatıldı"
    override val homeFilterCompleted = "Tamamlandı"
    override val homeFilterFailed = "Başarısız"
    override val homeFilterMedia = "Video ve ses"
    override val homeFilterFiles = "Dosyalar"
    override val homeFilterTorrents = "Torrentler"

    override val homeCantResumeTitle = "Bu indirme sürdürülemez"
    override val homeCantResumeClipText =
        "İndirme sırasında kesilen klipler kaldıkları yerden devam edemez. " +
                "Şimdi duraklatırsanız, sürdürdüğünüzde klip baştan başlar."
    override val homeCantResumeServerText =
        "Sunucu sürdürmeyi desteklemiyor. Şimdi duraklatırsanız, sürdürdüğünüzde " +
                "indirme baştan başlar."
    override val homePauseAnyway = "Yine de duraklat"
    override val homeKeepDownloading = "İndirmeye devam et"
    override val homeMagnetLink = "Magnet bağlantısı"
    override val homeClipboardOfferTitle = "Kopyaladığınız bağlantı indirilsin mi?"
    override val homeClipboardOfferHint =
        "Bunu Ayarlar > Genel > Panoyu izle bölümünden kapatabilirsiniz."
    override val homeDownload = "İndir"
    override val homeNotNow = "Şimdi değil"
    override val bridgePairTitle = "Bu tarayıcı uzantısına izin verilsin mi?"
    override val bridgePairText =
        "Bir tarayıcı uzantısı FetchJet'e bağlantı göndermek istiyor. Yalnızca FetchJet uzantısını yeni kurduysanız veya sıfırladıysanız izin verin."
    override val bridgePairOldText =
        "Eski bir FetchJet uzantısı bağlantı göndermek istiyor. Sizinse izin verin; uzantıyı güncellemek bu soruyu ortadan kaldırır."
    override val bridgePairAllow = "İzin ver"
    override val bridgePairBlock = "Engelle"
    override val extConnectionsGroup = "Tarayıcı bağlantıları"
    override val extConnectionsFooter =
        "FetchJet'e bağlantı göndermesine izin verdiğiniz veya engellediğiniz tarayıcı uzantıları."
    override val extAllowedLabel = "İzin verildi"
    override val extBlockedLabel = "Engellendi"
    override val extRevoke = "İzni kaldır"
    override val extUnblock = "Engeli kaldır"
    override val homeBrowserFallbackTitle = "Bunun yerine tarayıcıda indirilsin mi?"
    override val homeBrowserFallbackText =
        "Bu dosyayı FetchJet'te iptal ettiniz. Tarayıcı, bağlantıyı devrederken kendi " +
                "indirmesini durdurdu; yalnızca geri gönderirseniz devam eder."
    override val homeDownloadInBrowser = "Tarayıcıda indir"
    override val homeNoDiscard = "Hayır, vazgeç"

    override val homeQueueTitle = "Kuyruk"
    override val homeQueueFooter =
        "Fazladan indirmeler bekler ve biri bittiğinde en eskisinden başlayarak kendiliğinden başlar. " +
                "Bekleyen bir indirmede \"Şimdi başlat\" kuyruğu atlar."
    override val homeDownloadsAtSameTime = "Aynı anda indirme sayısı"
    override val homeNoLimit = "Sınırsız"
    override fun homeAtATime(count: Int) = "Aynı anda $count"

    override val homePiecesLabel = "Parçalar"
    override fun homeSegmentsCount(count: Int) = "$count segment"
    override fun homePiecesCount(count: Int) = "$count parça"
    override fun homeActiveCount(count: Int) = "$count etkin"
    override fun homeWaitingCount(count: Int) = "$count bekliyor"
    override fun homeDoneCount(count: Int) = "$count bitti"
    override fun homeSegmentsProgress(finished: Int, total: Int, downloading: String?) =
        "Segmentler $finished/$total" + (downloading?.let { " · $it indiriliyor" } ?: "")

    override fun homeRetryingNote(note: String?) = "$note · yeniden deneniyor"
    override fun clipStartAfterEnd(start: String, end: String) =
        "Başlangıç ($start), bitişten ($end) önce olmalı"

    override fun clipEndExceedsVideo(end: String, duration: String) =
        "Bitiş ($end), video süresini ($duration) aşıyor"

    override fun clipStartExceedsVideo(duration: String) =
        "Başlangıç, video süresini ($duration) aşıyor"

    override val setStartTimeTitle = "Başlangıç zamanı (itibaren)"
    override val setEndTimeTitle = "Bitiş zamanı (kadar)"
    override val addDialogTitle = "Yeni indirme ekle"
    override val addDialogSubtitle = "İndirmeye başlamak için bağlantı girin"
    override val addLinkLabel = "Bağlantı"
    override val addImportTxt = ".txt içe aktar"
    override val openTorrentFile = ".torrent dosyası aç"
    override val urlPlaceholder = "Bağlantı veya magnet yapıştırın"
    override val addPaste = "Yapıştır"
    override val addDownloadEngine = "İndirme motoru"
    override val engineDirect = "Doğrudan"
    override val engineMedia = "Medya"
    override val engineTorrent = "Torrent"
    override val addFileNameLabel = "Dosya adı"
    override val findingName = "Ad bulunuyor…"
    override val saveDestination = "Kayıt klasörü"
    override val quickFolderDownloads = "İndirilenler"
    override val quickFolderDocuments = "Belgeler"
    override val quickFolderMedia = "Medya"
    override val quickFolderDesktop = "Masaüstü"
    override val addRetryInspect = "Yeniden incele"
    override val addStarting = "Başlatılıyor..."
    override val addInspecting = "İnceleniyor..."
    override val addInspectionFailed = "İnceleme başarısız"
    override val addEnterUrl = "URL girin"
    override fun scheduleLinks(n: Int) = "$n bağlantıyı zamanla"
    override fun downloadLinks(n: Int) = "$n bağlantıyı indir"
    override val inspectVideoFirst = "Önce videoyu inceleyin"
    override val inspectTorrentFirst = "Önce torrenti inceleyin"
    override val invalidTimeRange = "Geçersiz zaman aralığı"
    override fun scheduleVideos(n: Int) = "$n videoyu zamanla"
    override fun downloadVideos(n: Int) = "$n videoyu indir"
    override val addScheduleDownload = "İndirmeyi zamanla"
    override val addStartDownload = "İndirmeyi başlat"
    override val availableAudioFormats = "Kullanılabilir ses biçimleri"
    override val availableQualities = "Kullanılabilir kaliteler"
    override val addSelectAll = "Tümünü seç"
    override val addClearSelection = "Temizle"
    override fun selectedOfAvailable(selected: Int, total: Int) = "Seçili: $selected / $total"
    override fun qualityFormatLine(format: String, details: String) = "Biçim: $format$details"
    override val inspectingVideoQualities = "Video kaliteleri inceleniyor..."
    override fun readingCourseLectures(done: Int, total: Int) =
        "Kurs dersleri okunuyor: ${done} / ${total}"

    override val readingCourseLecturesHint =
        "Tarayıcı eklentisi bunları kurs sekmesinde okuyor: sekmeyi açık tutun"
    override val inspectingVideoQualitiesHint =
        "Kullanılabilir video çözünürlükleri ve ses akışları taranıyor"
    override val videoInspectionFailed = "Video incelemesi başarısız"
    override val addRetryInspection = "İncelemeyi yeniden dene"
    override val mediaEngineStarting = "Medya motoru başlatılıyor..."
    override val mediaEngineNotReady = "Medya motoru hazır değil"
    override val mediaEngineRequiresYtDlp =
        "Video akışı çıkarmak için yt-dlp yüklü veya gömülü olmalı."
    override val preparingEngine = "Motor hazırlanıyor..."
    override val startMediaEngine = "Medya motorunu indir / başlat"
    override val inspectAudioFormats = "Ses biçimlerini incele ve seç"
    override val inspectVideoQuality = "Video kalitesini incele ve seç"
    override fun clipSectionTitle(isAudio: Boolean) =
        if (isAudio) "Sesten bölüm kes (isteğe bağlı)" else "Videodan bölüm kes (isteğe bağlı)"

    override val clipSectionHint = "Yalnızca belirli bir zaman aralığını indirin"
    override fun mediaDurationFullRange(isAudio: Boolean, clock: String, friendly: String) =
        "${if (isAudio) "Ses" else "Video"} süresi: $clock ($friendly) - Varsayılan olarak tamamı seçili"

    override val clipStartTimeFrom = "Başlangıç (itibaren)"
    override val clipEndTimeTo = "Bitiş (kadar)"
    override fun fullMediaChip(isAudio: Boolean, duration: String) =
        if (isAudio) "Tüm ses ($duration)" else "Tüm video ($duration)"

    override val clipFirst5Min = "İlk 5 dk"
    override val clipFirst15Min = "İlk 15 dk"
    override val clipFirst30Min = "İlk 30 dk"
    override val clipFirst1Hour = "İlk 1 saat"
    override val clipModeTitle = "Klip nasıl indirilsin"
    override val clipOnlyTitle = "Yalnızca klip"
    override val clipOnlyLine1 = "Yalnızca seçtiğiniz kısmı indirir"
    override val clipOnlyLine2 = "Daha yavaş: YouTube hızı sınırlar"
    override val clipOnlyLine3 = "Duraklatınca klip baştan başlar"
    override fun fullThenCutTitle(isAudio: Boolean) =
        if (isAudio) "Tüm ses, sonra kes" else "Tüm video, sonra kes"

    override fun fullThenCutLine1(isAudio: Boolean, sizeText: String) =
        if (isAudio) "Sesin tamamını indirir$sizeText" else "Videonun tamamını indirir$sizeText"

    override val fullThenCutLine2 = "Daha hızlı: tam hız"
    override val fullThenCutLine3 = "Sonra yalnızca sizin kısmınızı tutar"
    override val fullThenCutLine4 = "Duraklatma / sürdürme çalışır"
    override val inspectingTorrent = "Torrent bilgileri inceleniyor..."
    override val inspectingTorrentHint = "Dosya listesi ve eşler alınıyor"
    override val torrentInspectionFailed = "Torrent incelemesi başarısız"
    override val inspectTorrentFiles = "Torrent dosyalarını incele"
    override val disableTorrentUpload = "Torrent yüklemeyi kapat (yalnızca indirme)"
    override val torrentFiles = "Torrent dosyaları"
    override val addDeselectAll = "Seçimi kaldır"
    override val torrentPriorityHigh = "Yüksek"
    override val torrentPriorityLow = "Düşük"
    override val torrentPriorityNormal = "Normal"
    override fun selectedOfLinks(selected: Int, total: Int) = "$selected / $total bağlantı"
    override val readingTorrentFiles = "Torrent dosyaları okunuyor…"
    override fun couldNotReadTorrentFiles(error: String) =
        "Dosyaları okunamadı ($error); hepsi indirilecek."

    override val linkTagVideo = "VİDEO"
    override val linkTagAudio = "SES"
    override val linkTagTorrent = "TORRENT"
    override val linkTagFile = "DOSYA"
    override val linkListMagnet = "Magnet bağlantısı"
    override fun linkListYouTubeVideo(id: String) = "YouTube videosu $id"
    override fun playlistSummary(videos: Int, selected: Int) =
        "Oynatma listesi · $videos video · $selected seçili"

    override val playlistFolder = "Klasöre kaydet"
    override val playlistFolderHint =
        "Her dosya bu klasöre gider. Doğrudan kaydetmek için boş bırakın."
    override val qualityForAllVideos = "Tüm videolar için kalite"
    override val qualityForAllVideosHint =
        "Bir videoda bu kalite yoksa en yakın düşük kalite alınır."
    override val downloadOnlyThisVideo = "Bunun yerine yalnızca bu videoyu indir"
    override val whenToStart = "Ne zaman başlasın"
    override val scheduleNow = "Şimdi"
    override val scheduleIn30Min = "30 dk sonra"
    override val scheduleIn1Hour = "1 saat sonra"
    override val scheduleAtTime = "Belirli bir saatte…"
    override val scheduleDayLabel = "Gün"
    override val scheduleToday = "Bugün"
    override val scheduleTomorrow = "Yarın"
    override val scheduleIn2Days = "2 gün sonra"
    override val scheduleIn3Days = "3 gün sonra"
    override val scheduleInAWeek = "1 hafta sonra"
    override val scheduleTimePassed =
        "Bu saat bugün için geçti. Daha geç bir saat veya başka bir gün seçin."
    override val scheduleHourLabel = "Saat"
    override val scheduleMinuteLabel = "Dakika"
    override val scheduleSet = "Ayarla"
    override val scheduleSetTime = "Saati ayarla"
    override fun scheduleStartsAt(time: String) = "Başlangıç: $time (FetchJet açık olmalı)"
    override val addConnectionsTitle = "Bağlantılar"
    override val connectionsAuto = "Otomatik"
    override fun connectionsAutoWithDefault(n: Int) = "Otomatik ($n)"
    override fun pickerExceedsVideo(duration: String) = "Video süresini aşıyor ($duration)"
    override fun pickerMustBeAfterStart(start: String) = "Başlangıçtan ($start) sonra olmalı"
    override val pickerStartZero = "Başlangıç (00:00)"
    override val pickerHours = "Saat"
    override val pickerMinutes = "Dakika"
    override val pickerSeconds = "Saniye"
    override fun pickerSelectedTime(time: String, summary: String) =
        "Seçilen zaman: $time ($summary)"

    override fun pickerEndOfVideo(time: String) = "Videonun sonu ($time)"
    override fun pickerMin(time: String) = "En az ($time)"
    override fun pickerApply(time: String) = "Uygula ($time)"
    override val pickerInvalidTime = "Geçersiz zaman"
    override val dirPickerSelectFolder = "Klasör seç"
    override val dirPickerUp = "Yukarı"
    override fun dirPickerSubfolders(n: Int) = "Alt klasörler ($n)"
    override val dirPickerHiddenOn = "Gizli: Açık"
    override val dirPickerHiddenOff = "Gizli: Kapalı"
    override val dirPickerNewFolder = "Yeni klasör"
    override val dirPickerFolderName = "Klasör adı…"
    override val dirPickerCreateFailed = "Klasör oluşturulamadı"
    override val dirPickerCreate = "Oluştur"
    override val dirPickerNoSubfolders =
        "Bu klasörde alt klasör yok.\nDosyalar doğrudan buraya kaydedilecek."
    override val dirPickerHiddenTag = "(gizli)"
    override val speedLimitUnlimited = "Sınırsız (en yüksek)"
    override val speedPresetsTitle = "Hız ön ayarları"
    override val speedPresetsFooter =
        "Yüksek, Orta ve Düşük için bir hız belirleyin. Üst çubuktan hızlıca birini seçin; kaplumbağa her zaman 512 KB/s."
    override val speedPresetHigh = "Yüksek"
    override val speedPresetMedium = "Orta"
    override val speedPresetLow = "Düşük"
    override val speedLimitNoLimit = "Sınır yok (en yüksek hız)"
    override val speedLimitCustom = "Özel..."
    override val speedLimitValue = "Değer"
    override val scheduleAm = "ÖÖ"
    override val schedulePm = "ÖS"
    override val detailsChipMedia = "Medya"
    override val detailsChipTorrent = "Torrent"
    override val detailsChipFile = "Dosya"
    override val detailsChipResumable = "Sürdürülebilir"
    override val detailsChipNoResume = "Sürdürülemez"
    override fun detailsClipChip(start: String, end: String?, cutAfterDownload: Boolean) =
        "Klip $start–${end ?: "Son"}" + if (cutAfterDownload) " · indirdikten sonra kesilir" else ""

    override fun detailsTorrentFiles(selected: Int, total: Int) =
        "Torrent dosyaları ($selected seçili / $total)"

    override val detailsPriorityHigh = "Yüksek"
    override val detailsPriorityLow = "Düşük"
    override val detailsPriorityNormal = "Normal"
    override val detailsStatusCompleted = "Tamamlandı"
    override val detailsStatusPaused = "Duraklatıldı"
    override val detailsStatusFailed = "Başarısız"
    override val detailsStatusWaiting = "Bekliyor"
    override val detailsStatusQueued = "Kuyrukta"
    override val detailsStatusNotStarted = "Başlamadı"
    override val detailsUnknownSize = "Bilinmiyor"
    override val detailsMergingAudioVideo = "Ses ve video birleştiriliyor..."
    override fun detailsTorrentSpeedUploadDisabled(download: String) =
        "İndirme: $download (Yükleme kapalı)"

    override fun detailsTorrentSpeed(download: String, upload: String) =
        "İndirme: $download | Yükleme: $upload"

    override val detailsConnecting = "Bağlanıyor..."
    override fun detailsStartsAt(time: String) = "Başlangıç: $time"
    override fun detailsWaitingInQueuePosition(position: Int) = "Kuyrukta bekliyor (#$position)"
    override val detailsWaitingInQueue = "Kuyrukta bekliyor"
    override val detailsDownloaded = "İndirilen"
    override val detailsSpeed = "Hız"
    override val detailsState = "Durum"
    override val detailsTimeLeft = "Kalan süre"
    override val detailsAdded = "Eklenme"
    override val detailsSavedTo = "Kayıt yeri"
    override val detailsSource = "Kaynak"
    override val detailsPage = "Sayfa"
    override val detailsOpenFile = "Dosyayı aç"
    override val detailsOpenFolder = "Klasörü aç"
    override val detailsStartNow = "Şimdi başlat"
    override val detailsRemoveFromList = "Listeden kaldır"
    override val detailsCancelDownload = "İndirmeyi iptal et"
    override val detailsHeaderDownload = "İndirme"
    override val detailsHeaderDetails = "AYRINTILAR"
    override fun detailsAddedToday(clock: String) = "Bugün $clock"
    override fun detailsAddedYesterday(clock: String) = "Dün $clock"

    override fun removeDialogTitle(count: Int) =
        if (count > 1) "$count indirmeyi kaldır" else "İndirmeyi kaldır"

    override fun removeDialogMessage(count: Int) =
        if (count > 1) "Bu $count indirme görevini kaldırmak istediğinizden emin misiniz?"
        else "Bu indirme görevini kaldırmak istediğinizden emin misiniz?"

    override val removeDialogAlsoDeleteFiles = "İndirilen dosyaları da diskten sil"

    override val duplicateDialogTitle = "İndirme zaten var"
    override val duplicateDialogSubtitle = "Bu öğe zaten indirme kuyruğunuzda"
    override fun duplicateDialogStatus(status: String) = "Durum: $status"
    override val duplicateDialogQuestion =
        "Mevcut indirmeyi sürdürmek mi yoksa baştan başlamak mı istersiniz?"
    override val duplicateDialogResumeExisting = "Mevcut olanı sürdür"
    override val duplicateDialogRedownload = "Yeniden indir (baştan)"

    override val overwriteDialogTitle = "Dosya zaten var"
    override val overwriteDialogSubtitle = "Hedef klasörde bu adda bir dosya zaten var"
    override val overwriteDialogFileNameLabel = "Dosya adı: "
    override val overwriteDialogFolderLabel = "Klasör: "
    override val overwriteDialogNewCopyLabel = "Yeni kopya: "
    override val overwriteDialogQuestion =
        "Bu indirmeyi yeni bir kopya olarak mı kaydetmek istersiniz, yoksa mevcut dosyanın üzerine mi yazılsın?"

    override fun overwriteDialogSaveAsCopy(name: String) = "Kopya olarak kaydet ($name)"
    override val overwriteDialogOverwriteExisting = "Mevcut dosyanın üzerine yaz"

    override val desktopWindowTagline = "Yüksek hızlı, çok motorlu indirme yöneticisi"
    override val desktopTrayIdleTooltip = "FetchJet - Arka planda çalışıyor"
    override val desktopTrayDownloadsHeader = "FetchJet indirmeleri:"
    override fun desktopTrayMore(count: Int) = "+$count tane daha"
    override val desktopTrayShowApp = "Uygulamayı göster"
    override val desktopTrayCloseApp = "Uygulamayı kapat"
    override val sectionGeneral = "Genel"
    override val sectionGeneralBlurb = "Uygulamanın görünümü ve nasıl başladığı"
    override val sectionDownloads = "İndirmeler"
    override val sectionDownloadsBlurb = "Dosyaların nereye gittiği ve kaçının aynı anda çalıştığı"
    override val sectionBrowser = "Tarayıcı eklentisi"
    override val sectionBrowserBlurb = "Tarayıcınızdan FetchJet'e bağlantı ve video gönderin"
    override val sectionSpeed = "Hız sınırları"
    override val sectionSpeedBlurb = "FetchJet'in kullandığı bant genişliğini sınırlayın"
    override val sectionMedia = "Medya araçları"
    override val sectionMediaBlurb = "Video siteleri için yt-dlp ve FFmpeg"
    override val sectionTorrent = "BitTorrent"
    override val sectionTorrentBlurb = "Eşler, yükleme ve torrent motoru"
    override val sectionUpdates = "Güncellemeler"
    override val sectionUpdatesBlurb = "Sürümünüz ve yeni sürümler"
    override val summaryNoQueueLimit = "sınırsız"
    override fun summaryAtATime(n: Int) = "aynı anda $n"
    override val summaryNoSpeedLimit = "Sınırsız"
    override val summarySetupNeeded = "Kurulum gerekli"
    override val summaryUploadingOff = "Yükleme kapalı"
    override val summaryUploadingOn = "Yükleme açık"
    override fun summaryVersionAvailable(version: String) = "$version mevcut"
    override fun summaryVersion(version: String) = "Sürüm $version"
    override val changesSavedAutomatically = "Değişiklikler otomatik kaydedilir."
    override val settingsAllSettings = "Tüm ayarlar"

    override val clipboardGroup = "Pano"
    override val clipboardFooter =
        "Herhangi bir yerde bir dosya bağlantısı (zip, mp4, pdf, apk…) veya magnet bağlantısı kopyalayın, FetchJet indirilip indirilmeyeceğini sorar."
    override val watchClipboard = "Panoyu izle"
    override val watchClipboardOn = "Açık · kopyalanan dosya bağlantıları indirme için önerilir"
    override val settingOff = "Kapalı"
    override val alsoVideoPages = "Video sayfaları da"
    override val alsoVideoPagesHint =
        "YouTube, Facebook, TikTok, Vimeo… bağlantıları da önerilir (yalnızca dosyalar değil)"
    override val diagnosticsGroup = "Tanılama"
    override fun logSavedTo(path: String) =
        "Kaydedildi: $path — sorun bildirirken bu dosyayı gönderin."

    override val diagnosticsFooter =
        "Uygulama günlüğünü (yt-dlp ve motorların ne yaptığını) İndirilenler klasörüne kaydeder."
    override val saveLogToDownloads = "Günlüğü İndirilenler'e kaydet"
    override val logCouldNotSave = "kaydedilemedi"
    override val appearanceGroup = "Görünüm"
    override val themeTitle = "Tema"
    override val themeHint = "Tüm ekranlar için açık veya koyu görünüm"
    override val settingsThemeDark = "Koyu"
    override val settingsThemeLight = "Açık"
    override val speedShownIn = "Hız birimi"
    override val speedShownInHint =
        "Dosya boyutları gibi bayt (MB/s) ya da internet paketiniz gibi bit (Mbps)"

    override val saveLocationGroup = "Kayıt konumu"
    override val saveLocationFooter =
        "Eklerken başka bir klasör seçmezseniz yeni indirmeler buraya kaydedilir."
    override val settingsFolderPath = "Klasör yolu"
    override val settingsChooseFolder = "Klasör seç…"
    override val settingsUseDownloads = "İndirilenler'i kullan"
    override val settingsConnectionsGroup = "Bağlantılar"
    override val connectionsFooter =
        "Doğrudan bir indirmenin kaç parçaya bölüneceği. Her parça bir bağlantıdır; biri bitince kalan en büyük parçanın yarısını alır, böylece hiçbiri boşta kalmaz. Otomatik, dosya boyutuna göre seçer (4–8). Bazı sunucular istemci başına bağlantı sayısını sınırlar."
    override val settingsConnectionsPerDownload = "İndirme başına bağlantı"
    override val settingsConnectionsAuto = "Otomatik"
    override val organizeGroup = "Düzenle"
    override val organizeFooter =
        "Kayıt konumunun içinde alt klasörler oluşturulur: Video, Müzik, Arşivler, Belgeler, Programlar, Resimler, Torrentler."
    override val sortIntoFolders = "Türe göre klasörlere ayır"
    override val sortIntoFoldersOn = "Açık · ör. Downloads/Video/ders.mp4"
    override val sortIntoFoldersOff = "Kapalı · her şey doğrudan klasöre gider"

    override val speedLimitsFooter = "Hemen uygulanır ve çalışan indirmeler arasında paylaştırılır."
    override val speedLimitsFooterIos =
        "Hemen uygulanır ve çalışan indirmeler arasında paylaştırılır. iOS'ta arka planda zaten çalışan HTTP indirmeleri hızlarını korur; sınır, sonradan başlatılanlara uygulanır."
    override val speedLimitDownload = "İndirme"
    override val speedLimitUploadTorrents = "Yükleme (torrentler)"

    override val torrentSharingGroup = "Paylaşım"
    override val downloadOnlyNoUpload = "Yalnızca indir (yükleme yapma)"
    override val downloadOnlyNoUploadHint =
        "Yükleme bant genişliğinizi ve veri kotanızı korur. Bazı izleyiciler paylaşım yapmayan eşlere daha yavaş indirme sağlar."
    override val torrentEngineGroup = "Motor"
    override val settingsReady = "Hazır"
    override val settingsNotReady = "Hazır değil"

    override val extInstallGroup = "Yükle"
    override val extensionInstallFooter =
        "Eklentiyle videolarda ve bağlantılarda \"FetchJet ile indir\" düğmesi görünür ve tarayıcı indirmelerini FetchJet'e devredebilir. Yalnızca cihazınızdaki bu uygulamayla iletişim kurar."
    override val edgeAddedHint =
        "Eklendi · Edge'i yeniden başlatın ve sorduğunda \"Uzantıyı aç\"a tıklayın"
    override val edgeFromStoreHint = "Microsoft Edge Eklentileri'nden · kendiliğinden güncellenir"
    override val openInEdgeStore = "Edge mağazasında aç"
    override val edgeAddedNote = "Tamam: Edge'i yeniden başlatın, FetchJet'i açmanızı isteyecek."
    override val extAddToEdge = "Edge'e ekle"
    override val firefoxFromStoreHint = "Firefox Eklentileri'nden · kendiliğinden güncellenir"
    override val extAddToFirefox = "Firefox'a ekle"
    override val chromeZipHint =
        "Chrome Web Mağazası'nda yok: .zip dosyasını geliştirici modunda yükleyin (adımlar sayfada). Kendiliğinden güncellenmez."
    override val getTheZip = ".zip dosyasını al"
    override val firefoxAndroidHint =
        "Bunu bu telefondaki Firefox'ta açın. Gönderdiğiniz bağlantılar doğrudan bu uygulamaya gelir."
    override val keepReadyForBrowser = "Tarayıcı için hazır tut"
    override val keepReadyOn =
        "Video kaliteleri doğrudan Firefox'ta görünür. Bu açıkken sessiz bir bildirim kalır."
    override val keepReadyOff =
        "Kapalı: Firefox FetchJet'i açar ve kaliteyi burada seçersiniz. Açık: kaliteler Firefox'ta görünür (bir bildirim kalır)."
    override val popUpOverBrowser = "Tarayıcının üstünde aç"
    override val popUpOverBrowserOn =
        "Açık · Firefox bağlantı gönderdiğinde FetchJet kendiliğinden açılır"
    override val popUpOverBrowserOff =
        "Kapalı · bunun yerine bildirim görünür; \"diğer uygulamaların üzerinde göster\" iznini vermek için dokunun"

    override val mediaToolsGroup = "Araçlar"
    override val ytDlpHint = "YouTube ve diğer sitelerde videoyu ve sesi bulur"
    override val settingsMissing = "Eksik"
    override val ffmpegHint = "Video + sesi birleştirir (1080p ve üstü), klip keser, MP3 yapar"
    override val ffmpegMissing = "Eksik · en fazla 720p"
    override fun jsRuntimeTitle(name: String) = "JS çalışma ortamı · $name"
    override val jsRuntimeHint =
        "YouTube'un kontrollerini çözer, böylece tüm kaliteler daha hızlı görünür"

    override fun missingPress(button: String) = "Eksik · şuna basın: $button"
    override val mediaToolsFolder = "Araçlar klasörü"
    override val mediaUpdatingTools = "Güncelleniyor…"
    override val mediaDownloadingTools = "Araçlar indiriliyor…"
    override fun mediaDownloadingTool(name: String) = "$name indiriliyor…"
    override fun mediaDownloadingToolPercent(name: String, percent: Int) =
        "$name indiriliyor… %$percent"

    override val mediaUpdateTools = "Araçları güncelle"
    override val mediaDownloadTheTools = "Araçları indir"

    override val updateNewVersionAvailable = "Yeni sürüm mevcut"
    override fun updateVersionLine(version: String, current: String) =
        "FetchJet $version  ·  sizdeki: $current"

    override val updateWhatsNew = "Yenilikler"
    override val updateNoReleaseNotes = "Sürüm notu yok."
    override fun downloadingUpdate(percent: Int) = "Güncelleme indiriliyor… %$percent"
    override val updateDownloadedAndroid =
        "Güncelleme indirildi. Android kurulumu onaylamanızı ister; indirmeleriniz listede kalır."
    override val updateDownloadedDesktop =
        "Güncelleme indirildi. FetchJet kapanacak, güncellemeyi kuracak ve yeniden açılacak. Çalışan indirmeler duraklatılır ve sürdürülebilir."
    override val storeInstallsItselfOpenNow =
        "Microsoft Store güncellemeyi kendisi kurar. Şimdi güncellemek için Store'u açın."
    override val updateInstall = "Yükle"
    override val updateRestartAndUpdate = "Yeniden başlat ve güncelle"
    override val updateDownloading = "İndiriliyor…"
    override val updateDownloadAndInstall = "İndir ve yükle"
    override val updateOpenMicrosoftStore = "Microsoft Store'u aç"
    override fun updateDownloadWithSize(size: String) = "İndir ($size)"
    override val updateOpenReleasePage = "Sürüm sayfasını aç"
    override val updateSkipThisVersion = "Bu sürümü atla"
    override val updateLater = "Daha sonra"
    override fun versionIsAvailable(version: String) = "Sürüm $version mevcut"
    override val youHaveLatest = "Buradaki sürüm güncel"
    override val updateSeeWhatsNew = "Yenilikleri gör"
    override val updateChecking = "Denetleniyor…"
    override val updateCheckNow = "Şimdi denetle"
    override val checkUpdatesAutomatically = "Güncellemeleri otomatik denetle"
    override val checkUpdatesAutomaticallyHint =
        "FetchJet başladığında yeni sürüm arar (günde en fazla iki kez)."
    override val downloadUpdatesAutomatically = "Güncellemeleri otomatik indir"
    override val downloadUpdatesAutomaticallyHintAndroid =
        "Yeni sürümü arka planda indirir; siz yalnızca kurulumu onaylarsınız."
    override val downloadUpdatesAutomaticallyHint =
        "Yeni sürümü arka planda indirir; siz yalnızca yeniden başlatmayı onaylarsınız."
    override val installingUpdates = "Güncellemeleri yükleme"
    override val installingUpdatesStore =
        "Microsoft Store yeni sürümleri kendisi yükler; yeni sürüm çıktığında FetchJet size haber verir."
    override val installingUpdatesManual =
        "Bu cihazda FetchJet yeni sürümü bildirir ve bağlantısını verir; yüklemeyi siz yaparsınız."
    override val updateReleaseNotes = "Sürüm notları"
    override val updateReleaseNotesHint = "Tüm sürümler ve nelerin değiştiği"
    override val installHintReleasePage = "Yeni sürümü indirmek için sürüm sayfasını açın."
    override val installHintApk = "APK'yı indirin ve bu sürümün üzerine yüklemek için açın."
    override val installHintIpa =
        "FetchJet'i nasıl yüklediyseniz, .ipa dosyasını indirip AltStore / Sideloadly ile yükleyin."
    override val installHintDmg =
        ".dmg dosyasını indirin, açın ve FetchJet'i Uygulamalar klasörüne sürükleyin (eskisini değiştirin)."
    override val installHintDeb =
        ".deb dosyasını indirip yükleyin (çift tıklayın veya sudo apt install ./file.deb)."
    override val installHintOther = "Yeni sürümü indirin ve bunun üzerine yükleyin."

    override val startupGroup = "Başlangıç"
    override val startupStoreFooter =
        "Store sürümünü Windows başlatır: FetchJet'i Ayarlar > Uygulamalar > Başlangıç bölümünden açın."
    override val startupStartAtLogin = "Oturum açınca FetchJet'i başlat"
    override val startupManagedByWindows = "Windows tarafından yönetiliyor"
    override val startupOpenWindowsSettings = "Windows ayarlarını aç"
    override val startupStartHidden = "Sistem tepsisinde gizli başlat"
    override val startupStartHiddenHint =
        "Oturum açılışında başlatıldığında pencereyi açmak yerine tepside kalır."
    override val startupStartAtLoginHint =
        "Zamanlanmış ve kuyruktaki indirmeler devam eder, tarayıcı eklentisi de uygulamayı her zaman bulur."

    override fun startupCouldNotChange(message: String) = "Ayar değiştirilemedi: $message"
    override val whenDoneGroup = "İndirmeler bitince"
    override val whenDoneHint = "Yalnızca bu oturum için. Önce iptal etmek için 60 saniyeniz olur."
    override val whenDoneNothing = "Hiçbir şey yapma"
    override val whenDoneExit = "FetchJet'ten çık"
    override val whenDoneSleep = "Uyku"
    override val whenDoneShutDown = "Bilgisayarı kapat"
    override val whenDoneTitle = "Tüm indirmeler bitti"
    override fun whenDoneExitIn(seconds: Int) = "FetchJet ${seconds} sn içinde kapanacak."
    override fun whenDoneSleepIn(seconds: Int) =
        "Bilgisayar ${seconds} sn içinde uyku moduna geçecek."

    override fun whenDoneShutDownIn(seconds: Int) = "Bilgisayar ${seconds} sn içinde kapanacak."
    override val whenDoneNow = "Şimdi"
    override fun whenDoneBanner(action: String) = "İndirmeler bitince: ${action}"
    override fun whenDoneFailed(message: String) = "Yapılamadı: ${message}"
    override val sectionNetwork = "Ağ"
    override val sectionNetworkBlurb = "Proxy ve site girişleri"
    override val netProxyGroup = "Proxy"
    override val netProxyOff = "Kapalı"
    override val netProxyHost = "Sunucu"
    override val netProxyPort = "Port"
    override val netProxyUser = "Kullanıcı adı (isteğe bağlı)"
    override val netProxyPassword = "Parola"
    override val netUser = "Kullanıcı adı"
    override val netSave = "Kaydet"
    override val netSaved = "Kaydedildi"
    override val netProxyHint =
        "İndirmeler, video indirmeleri (yt-dlp) ve torrentler kullanır. Kaydettikten sonra başlayan indirmelere uygulanır."
    override val netProxyHintIos =
        "İndirmeler ve video indirmeleri kullanır. iOS'ta torrentler doğrudan bağlanır. Kaydettikten sonra başlayan indirmelere uygulanır."
    override val netProxyBadPort = "Bir sunucu ve 1-65535 arası bir port girin"
    override val netLoginsGroup = "Site girişleri"
    override val netLoginsHint =
        "Kullanıcı adı ve parola isteyen indirmeler için (HTTP basic auth). Bir site alt alan adlarını da kapsar."
    override val netLoginSite = "Site (example.com)"
    override val netLoginAdd = "Ekle"
    override val netLoginsEmpty = "Kayıtlı giriş yok"
    override val netRemove = "Kaldır"
    override fun netLoginsCount(n: Int) = "${n} giriş"
    override val sysTabAllDownloads = "Tüm indirmeler"
    override val sysTabActive = "Etkin"
    override val sysTabCompleted = "Tamamlanan"
    override val sysTabMediaExtractor = "Medya çıkarıcı"
    override val sysTabSettings = "Ayarlar"
    override val sysSortNewestFirst = "Önce en yeni"
    override val sysSortOldestFirst = "Önce en eski"
    override val sysSortLargestFirst = "Önce en büyük"
    override val sysSortSmallestFirst = "Önce en küçük"
    override val sysSortNameAz = "Ad A–Z"
    override val sysEnterValidUrlOrMagnet = "Lütfen geçerli bir URL veya magnet bağlantısı girin"
    override val sysCannotStartVideoInspectionFailed =
        "İndirme başlatılamıyor: video incelemesi başarısız."
    override val sysCannotStartTorrentInspectionFailed =
        "İndirme başlatılamıyor: torrent incelemesi başarısız veya hiç dosya seçilmedi."
    override val sysClipEndExceedsDuration = "Klip bitiş zamanı video süresini aşıyor."
    override val sysClipStartAfterEnd = "Klip başlangıcı bitişten önce olmalı."
    override fun sysStartedDownload(name: String) = "İndirme başladı: $name"
    override val sysFailedToQueueDownload = "İndirme kuyruğa eklenemedi"
    override fun sysQualitiesNotAdded(count: Int) = "$count kalite eklenemedi."
    override fun sysAddedQualityDownloads(count: Int, title: String) =
        "\"$title\" için $count indirme eklendi"

    override val sysSelectAtLeastOnePlaylistVideo = "Oynatma listesinden en az bir video seçin."
    override fun sysVideosNotAdded(count: Int) = "$count video eklenemedi."
    override fun sysAddedPlaylistVideos(count: Int, playlistTitle: String) =
        "\"$playlistTitle\" listesinden $count video eklendi"

    override val sysSelectAtLeastOneLink = "En az bir bağlantı seçin."
    override fun sysAddedLinkDownloads(added: Int, skipped: Int) =
        "$added indirme eklendi" + (if (skipped > 0) " ($skipped zaten listede veya başarısız)" else "")

    override val sysCouldNotReadTorrent = "Torrent okunamadı"
    override fun sysCouldNotStartDownload(reason: String) = "İndirme başlatılamadı: $reason"
    override val sysUnknownError = "bilinmeyen hata"
    override fun sysQueuedFromBrowser(added: Int, skipped: Int) =
        "Tarayıcıdan $added indirme kuyruğa eklendi" + (if (skipped > 0) " ($skipped atlandı)" else "")

    override val sysEnterTorrentOrMagnetFirst =
        "Lütfen önce bir torrent veya magnet bağlantısı girin"
    override val sysFailedToParseTorrentMetadata = "Torrent bilgileri çözümlenemedi"
    override val sysEnterMediaUrlFirst = "Lütfen önce bir medya URL'si girin"
    override fun sysUnableToFetchFormatsWithReason(reason: String) =
        "Video biçimleri alınamadı: $reason"

    override val sysUnableToFetchFormats =
        "Video biçimleri alınamadı. Lütfen URL'yi veya internet bağlantınızı kontrol edin."
    override val sysFailedToInspectMediaQualities = "Medya kaliteleri incelenemedi"
    override fun sysTorrentEngineError(message: String) = "BitTorrent motoru hatası: $message"
    override fun sysTorrentEngineInstallFailed(message: String) =
        "BitTorrent motoru yüklenemedi: $message"

    override val sysMediaToolsDownloaded = "Medya araçları başarıyla indirildi!"
    override val sysMediaToolsDownloadFailed = "Medya araçları indirilemedi"
    override val sysMediaToolsUpdated = "Medya araçları güncellendi"
    override fun sysSavedLinks(count: Int, path: String) = "$count bağlantı kaydedildi: $path"
    override fun sysCouldNotSaveLinks(reason: String) = "Bağlantılar kaydedilemedi: $reason"
    override val sysErrorFallback = "hata"
    override val sysDownloadFailed = "İndirme başarısız"
    override val sysDownloadFailedNetwork = "Ağ hatası nedeniyle indirme başarısız"
    override val sysCouldNotStart = "Başlatılamadı"
    override val sysUnknownErrorCapital = "Bilinmeyen hata"
    override fun sysCouldNotSaveFileTo(dir: String) = "Dosya kaydedilemedi: $dir"
    override val sysPlaylistFallbackTitle = "Oynatma listesi"
    override val sysPlaylistQualityBest = "En iyi"
    override val sysPlaylistQualityAudioOnly = "Yalnızca ses"
    override val sysToolsReadyYtDlpFfmpeg = "Hazır: yt-dlp ve ffmpeg yüklü (1080p+ tam destek)"
    override val sysToolsReadyYtDlpNoFfmpeg = "Hazır: yt-dlp yüklü (ffmpeg yok - en fazla 720p)"
    override val sysToolsNotInstalled = "Yüklü değil. Aşağıdan 'Araçları indir'e tıklayın."
    override val sysToolsInstalledBuiltIn = "Yüklü ve hazır (yerleşik)"
    override fun sysYtDlpVersionReady(version: String) = "yt-dlp $version (Hazır)"
    override val sysMediaEngineBridgeNotRegistered = "Medya motoru köprüsü kayıtlı değil"
    override val sysWebKitBuiltIn = "WebKit (yerleşik)"
    override val sysTorrentEngineBuiltIn = "Hazır: BitTorrent motoru yerleşik"
    override fun sysTorrentEngineReadyVersion(version: String) =
        "Hazır: BitTorrent motoru (libtorrent $version)"

    override fun sysTorrentEngineFailedToLoad(message: String) =
        "BitTorrent motoru yüklenemedi: $message"

    override val sysTorrentEngineAvailable =
        "Hazır: BitTorrent motoru mevcut (ilk torrentte yüklenir)"
    override val sysTorrentEngineMissing =
        "BitTorrent motoru eksik. Aşağıdan 'Araçları indir'e tıklayın."
    override val sysTorrentEngineNotAvailable = "BitTorrent motoru bu sürümde yok"
    override fun sysCouldNotStartInstaller(message: String) = "Yükleyici başlatılamadı: $message"
    override val sysCheckingForUpdates = "Güncellemeler denetleniyor…"
    override val sysNoPublishedRelease = "Henüz yayımlanmış sürüm yok."
    override fun sysVersionNoChangesForDevice(version: String, current: String) =
        "Sürüm $version bu cihaz için değişiklik içermiyor; bu cihaz için en son derlemeye sahipsiniz ($current)."

    override fun sysVersionAvailable(version: String) = "Sürüm $version mevcut."
    override fun sysUpToDate(current: String) = "Güncelsiniz ($current)."
    override fun sysCouldNotCheckForUpdates(reason: String) = "Güncellemeler denetlenemedi: $reason"
    override val sysNetworkError = "ağ hatası"
    override val sysUpdateNoInstallerForSystem =
        "Güncelleme indirilemedi: bu sürümde bu sistem için yükleyici yok."

    override fun sysDownloadingAsset(name: String) = "$name indiriliyor…"
    override fun sysVersionReadyToInstall(version: String) = "Sürüm $version yüklemeye hazır."
    override fun sysUpdateDownloadFailed(message: String) = "Güncelleme indirilemedi: $message"
    override val sysAllowUnknownAppsThenInstall =
        "FetchJet için \"Bilinmeyen uygulamaları yükle\" iznini verin, sonra tekrar Yükle'ye dokunun."
    override val sysDownloadsChannelName = "FetchJet indirmeleri"
    override val sysDownloadsChannelDescription = "İndirme durumunu canlı olarak gösterir"
    override val sysNotificationAppTitle = "FetchJet Downloader"
    override val sysAppRunningInBackground = "Uygulama arka planda çalışıyor"
    override val sysNotificationDownloadsFallback = "İndirmeler"
    override fun sysFilesDownloading(count: Int) = "$count dosya indiriliyor"
    override fun sysPercentActive(percent: Int, count: Int) = "%$percent • $count etkin"
    override fun sysActiveCount(count: Int) = "$count etkin"
    override fun sysDownloadCompleteSize(size: String) = "İndirme tamamlandı • $size"
    override fun sysMoreDownloading(count: Int) = "+ $count tane daha indiriliyor"
    override val sysComplete = "Tamamlandı"
    override fun sysNotificationDownloadsTitle(speed: String) = "FetchJet indirmeleri ($speed)"
    override fun sysDownloadingPercent(count: Int, percent: Int) = "$count indiriliyor • %$percent"
    override val sysDownloadTaskFallback = "İndirme görevi"
    override val sysBrowserReadyTitle = "FetchJet tarayıcınız için hazır"
    override val sysBrowserReadyText = "Video kaliteleri doğrudan Firefox'ta görünür"
    override val sysTurnOff = "Kapat"
    override val sysBrowserReadyChannelName = "Tarayıcı eklentisi"
    override val sysBrowserReadyChannelDescription =
        "FetchJet tarayıcı eklentisi için hazır beklerken gösterilir"
    override val sysDownloadingFileFallback = "Dosya indiriliyor"
    override val sysUnknownSize = "Bilinmiyor"
    override val sysStatusCompleted = "Tamamlandı"
    override val sysStatusDownloading = "İndiriliyor"
    override val sysDownloadCompleteTitle = "İndirme tamamlandı"
    override fun sysFinishedDownloading(size: String) = "İndirme bitti • $size"
    override val sysSentToFetchJet = "FetchJet'e gönderildi"
    override val sysLinksChannelName = "Tarayıcıdan gelen bağlantılar"
    override val sysLinksChannelDescription =
        "FetchJet'in az önce aldığı bağlantıyı açmak için dokunun"
    override val sysLinkReceivedTitle = "Bağlantı alındı"
    override val sysLinkReceivedText = "FetchJet'te açmak için dokunun"

    override val stepCompleted = "Tamamlandı"
    override val stepSeeding = "Paylaşılıyor"
    override val stepFetchingMetadata = "Meta veriler alınıyor"
    override val stepConnectingToPeers = "Eşlere bağlanılıyor"
    override val stepCheckingFiles = "Dosyalar denetleniyor"
    override val stepCheckingResumeData = "Devam verileri denetleniyor"
    override val stepDownloading = "İndiriliyor"
    override val stepFinished = "Bitti"
    override val stepConnecting = "Bağlanıyor"
    override val stepPaused = "Duraklatıldı"
    override val stepError = "Hata"
    override val stepResolvingStreams = "Akışlar çözümleniyor"
    override val stepDownloadingParts = "Parçalar indiriliyor"
    override val stepPreparingDownload = "İndirme hazırlanıyor"
    override val stepClippingVideo = "Video kırpılıyor"
    override val stepDownloadingMedia = "Medya indiriliyor"
    override val stepAlreadyDownloaded = "Zaten indirildi"
    override val stepDownloadingVideo = "Video indiriliyor"
    override val stepDownloadingAudio = "Ses indiriliyor"
    override val stepMergingAudioVideo = "Ses ve video birleştiriliyor"
    override val stepConvertingToMp3 = "MP3'e dönüştürülüyor"
    override val stepCuttingClip = "Klip kesiliyor"
    override val stepSavingToFolder = "Klasöre kaydediliyor"
    override val stepFinalizingVideo = "Video tamamlanıyor"
    override val stepConvertingVideo = "Video dönüştürülüyor"
    override val stepRepackagingVideo = "Video yeniden paketleniyor"
    override val stepYtDlpFallback = "yt-dlp'ye geçildi"
    override val stepNotPlainStream = "düz bir akış değil"
    override val stepSingleConnection = "Tek bağlantı"
    override val stepServerNoRanges = "sunucu aralık isteklerini desteklemiyor"
    override val stepAgeRestrictedExpiredCookies =
        "Yaş sınırlı: Dosyalar > FetchJet içindeki çerezlerin süresi dolmuş"
    override val stepAgeRestrictedPlaceCookies =
        "Yaş sınırlı: cookies.txt dosyasını Dosyalar > FetchJet içine koyun"
    override val stepPreparingMediaEngine =
        "Uygulama içi medya motoru hazırlanıyor (Python başlatılıyor, yt-dlp yükleniyor)"
    override val stepMediaEngineNotStarted =
        "Medya motoru başlatılmadı. \"Araçları indir\" düğmesine dokunun."

    override fun stepNumber(step: Int, total: Int) = "Adım ${step}/${total}"
    override fun stepSavingAs(ext: String) = ".${ext} olarak kaydediliyor"
    override fun stepJoiningParts(count: Int) = "${count} parça MP4 olarak birleştiriliyor"
    override fun stepNoDataFor(seconds: Int) = "${seconds} sn'dir veri yok"
    override fun stepPeers(peers: Int, seeds: Int) = "${peers} eş (${seeds} kaynak)"
    override fun stepDownloadingFrom(host: String) = "${host} adresinden indiriliyor"
    override fun stepMediaEngineNotReady(error: String) = "Medya motoru hazır değil: ${error}"
    override fun stepTorrentEngineFailed(error: String) =
        "BitTorrent motoru başlatılamadı: ${error}"

    override fun stepReadyWith(details: String) = "Hazır: ${details}"
}
