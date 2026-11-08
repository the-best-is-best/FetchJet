package com.michelle.raouf.fetch_jet.core.i18n.data_provider

object GermanProvider : AppLocalization {
    override val localeCode = "de"
    override val languageName = "Deutsch"
    override val ok = "OK"
    override val cancel = "Abbrechen"
    override val close = "Schließen"
    override val save = "Speichern"
    override val delete = "Löschen"
    override val remove = "Entfernen"
    override val open = "Öffnen"
    override val copy = "Kopieren"
    override val copied = "Kopiert"
    override val retry = "Erneut versuchen"
    override val pause = "Pausieren"
    override val resume = "Fortsetzen"
    override val apply = "Übernehmen"
    override val reset = "Zurücksetzen"
    override val yes = "Ja"
    override val no = "Nein"
    override val back = "Zurück"
    override val done = "Fertig"
    override val selectAll = "Alle auswählen"
    override val selectNone = "Keine auswählen"
    override val language = "Sprache"
    override val languageHint = "Die Sprache aller Bildschirme. System folgt deinem Gerät."
    override val systemLanguage = "System"
    override val homeSettings = "Einstellungen"
    override val homeDownloads = "Downloads"

    override val homeTabAll = "Alle"
    override val homeTabActive = "Aktiv"
    override val homeTabDone = "Fertig"
    override val homeTabTorrent = "Torrent"
    override val homeTabMedia = "Medien"
    override val homeSidebarTypeHeader = "TYP"
    override val homeSearchDownloadsPlaceholder = "Downloads durchsuchen"
    override val homeAdd = "Hinzufügen"
    override val homeAddDownload = "Download hinzufügen"
    override val homePauseAll = "Alle pausieren"
    override val homeResumeAll = "Alle fortsetzen"
    override fun homeSortBy(option: String) = "Sortierung: $option"
    override val homeLightTheme = "Helles Design"
    override val homeDarkTheme = "Dunkles Design"

    override fun homeUpdatingMediaTools(tool: String, percent: Int?) =
        "Medien-Tools werden aktualisiert · $tool" + (percent?.let { " $it %" } ?: "…")

    override val homeSearchDownloadsByNamePlaceholder = "Downloads nach Namen durchsuchen…"
    override fun homeNoDownloadsMatch(query: String) = "Keine Downloads passen zu „$query“"
    override val homeNoTasksInView = "Keine Einträge in dieser Ansicht"
    override val homeTryAnotherName = "Versuch einen anderen Namen"
    override val homeAddDownloadToGetStarted = "Füge einen Download hinzu, um loszulegen"
    override fun homeDownloadingWaitingSummary(running: Int, waiting: Int) =
        "$running laden · $waiting warten"

    override fun homeQueueStatus(maxAtOnce: Int) =
        "Warteschlange: " + if (maxAtOnce <= 0) "unbegrenzt" else "$maxAtOnce gleichzeitig"

    override fun homeSpeedLimitStatus(limit: String?) = "Tempolimit: " + (limit ?: "aus")
    override val homeGroupDownloading = "Wird geladen"
    override val homeGroupWaiting = "Wartend"
    override val homeGroupPausedFailed = "Pausiert & fehlgeschlagen"
    override val homeGroupCompleted = "Abgeschlossen"
    override fun homeSelectedCount(count: Int) = "$count ausgewählt"
    override val homeExport = "Exportieren"

    override fun homeStartsAt(time: String) = "Start: $time"
    override val homeWaitingInQueue = "Wartet in der Warteschlange"
    override fun homeWaitingInQueuePosition(position: Int) =
        "Wartet in der Warteschlange (Nr. $position)"

    override val homeConnecting = "Verbinde…"
    override val homeStarting = "Starte…"
    override fun homeTimeLeft(eta: String) = "noch $eta"
    override fun homePausedState(percent: Int, restartsOnResume: Boolean) =
        "Pausiert · $percent %" + if (restartsOnResume) " · beginnt beim Fortsetzen neu" else ""

    override fun homeFailedState(message: String?) = "Fehlgeschlagen: " + (message ?: "Fehler")
    override fun homeCompletedState(size: String?) =
        "Abgeschlossen" + (size?.let { " · $it" } ?: "")

    override val homeStartNow = "Jetzt starten"
    override val homeColumnName = "NAME"
    override val homeColumnSize = "GRÖSSE"
    override val homeColumnProgress = "FORTSCHRITT"
    override val homeColumnSpeed = "TEMPO"
    override val homeColumnLeft = "REST"
    override val homeFilterAllDownloads = "Alle Downloads"
    override val homeFilterDownloading = "Wird geladen"
    override val homeFilterWaiting = "Wartend"
    override val homeFilterPaused = "Pausiert"
    override val homeFilterCompleted = "Abgeschlossen"
    override val homeFilterFailed = "Fehlgeschlagen"
    override val homeFilterMedia = "Video & Audio"
    override val homeFilterFiles = "Dateien"
    override val homeFilterTorrents = "Torrents"

    override val homeCantResumeTitle = "Dieser Download kann nicht fortgesetzt werden"
    override val homeCantResumeClipText =
        "Clips, die beim Herunterladen geschnitten werden, können nicht dort weitermachen, wo sie aufgehört haben. " +
                "Wenn du jetzt pausierst, beginnt der Clip beim Fortsetzen von vorn."
    override val homeCantResumeServerText =
        "Der Server unterstützt kein Fortsetzen. Wenn du jetzt pausierst, beginnt der Download " +
                "beim Fortsetzen von vorn."
    override val homePauseAnyway = "Trotzdem pausieren"
    override val homeKeepDownloading = "Weiterladen"
    override val homeMagnetLink = "Magnet-Link"
    override val homeClipboardOfferTitle = "Den kopierten Link herunterladen?"
    override val homeClipboardOfferHint =
        "Abschalten unter Einstellungen > Allgemein > Zwischenablage überwachen."
    override val homeDownload = "Herunterladen"
    override val homeNotNow = "Nicht jetzt"
    override val bridgePairTitle = "Diese Browsererweiterung zulassen?"
    override val bridgePairText =
        "Eine Browsererweiterung möchte Links an FetchJet senden. Erlauben Sie das nur, wenn Sie die FetchJet-Erweiterung gerade installiert oder zurückgesetzt haben."
    override val bridgePairOldText =
        "Eine ältere FetchJet-Erweiterung möchte Links senden. Erlauben Sie es, wenn es Ihre ist; nach einem Update der Erweiterung entfällt diese Frage."
    override val bridgePairAllow = "Zulassen"
    override val bridgePairBlock = "Blockieren"
    override val extConnectionsGroup = "Browser-Verbindungen"
    override val extConnectionsFooter =
        "Browser-Erweiterungen, denen du das Senden von Links an FetchJet erlaubt oder verboten hast."
    override val extAllowedLabel = "Erlaubt"
    override val extBlockedLabel = "Blockiert"
    override val extRevoke = "Entziehen"
    override val extUnblock = "Freigeben"
    override val homeBrowserFallbackTitle = "Stattdessen im Browser herunterladen?"
    override val homeBrowserFallbackText =
        "Du hast diese Datei in FetchJet abgebrochen. Der Browser hat seinen eigenen Download beendet, " +
                "als er den Link übergeben hat. Er läuft nur weiter, wenn du ihn zurückschickst."
    override val homeDownloadInBrowser = "Im Browser herunterladen"
    override val homeNoDiscard = "Nein, verwerfen"

    override val homeQueueTitle = "Warteschlange"
    override val homeQueueFooter =
        "Weitere Downloads warten und starten von selbst, die ältesten zuerst, sobald einer fertig ist. " +
                "„Jetzt starten“ bei einem wartenden Download überspringt die Warteschlange."
    override val homeDownloadsAtSameTime = "Gleichzeitige Downloads"
    override val homeNoLimit = "Unbegrenzt"
    override fun homeAtATime(count: Int) = "$count gleichzeitig"

    override val homePiecesLabel = "Teile"
    override fun homeSegmentsCount(count: Int) =
        if (count == 1) "$count Segment" else "$count Segmente"

    override fun homePiecesCount(count: Int) = if (count == 1) "$count Teil" else "$count Teile"
    override fun homeActiveCount(count: Int) = "$count aktiv"
    override fun homeWaitingCount(count: Int) = "$count wartend"
    override fun homeDoneCount(count: Int) = "$count fertig"
    override fun homeSegmentsProgress(finished: Int, total: Int, downloading: String?) =
        "Segmente $finished/$total" + (downloading?.let { " · lade $it" } ?: "")

    override fun homeRetryingNote(note: String?) = "$note · neuer Versuch"
    override fun clipStartAfterEnd(start: String, end: String) =
        "Die Startzeit ($start) muss vor der Endzeit ($end) liegen"

    override fun clipEndExceedsVideo(end: String, duration: String) =
        "Die Endzeit ($end) überschreitet die Videolänge ($duration)"

    override fun clipStartExceedsVideo(duration: String) =
        "Die Startzeit überschreitet die Videolänge ($duration)"

    override val setStartTimeTitle = "Startzeit festlegen (von)"
    override val setEndTimeTitle = "Endzeit festlegen (bis)"
    override val addDialogTitle = "Neuer Download"
    override val addDialogSubtitle = "Link eingeben, um den Download zu starten"
    override val addLinkLabel = "Link"
    override val addImportTxt = ".txt importieren"
    override val openTorrentFile = ".torrent-Datei öffnen"
    override val urlPlaceholder = "Links oder Magnets einfügen"
    override val addPaste = "Einfügen"
    override val addDownloadEngine = "Download-Engine"
    override val engineDirect = "Direkt"
    override val engineMedia = "Medien"
    override val engineTorrent = "Torrent"
    override val addFileNameLabel = "Dateiname"
    override val findingName = "Name wird ermittelt…"
    override val saveDestination = "Zielordner"
    override val quickFolderDownloads = "Downloads"
    override val quickFolderDocuments = "Dokumente"
    override val quickFolderMedia = "Medien"
    override val quickFolderDesktop = "Schreibtisch"
    override val addRetryInspect = "Erneut prüfen"
    override val addStarting = "Starte..."
    override val addInspecting = "Wird geprüft..."
    override val addInspectionFailed = "Prüfung fehlgeschlagen"
    override val addEnterUrl = "URL eingeben"
    override fun scheduleLinks(n: Int) = if (n == 1) "$n Link planen" else "$n Links planen"
    override fun downloadLinks(n: Int) =
        if (n == 1) "$n Link herunterladen" else "$n Links herunterladen"

    override val inspectVideoFirst = "Zuerst Video prüfen"
    override val inspectTorrentFirst = "Zuerst Torrent prüfen"
    override val invalidTimeRange = "Ungültiger Zeitraum"
    override fun scheduleVideos(n: Int) = if (n == 1) "$n Video planen" else "$n Videos planen"
    override fun downloadVideos(n: Int) =
        if (n == 1) "$n Video herunterladen" else "$n Videos herunterladen"

    override val addScheduleDownload = "Download planen"
    override val addStartDownload = "Download starten"
    override val availableAudioFormats = "Verfügbare Audioformate"
    override val availableQualities = "Verfügbare Qualitäten"
    override val subtitlesTitle = "Untertitel"
    override val subtitlesAuto = "auto"
    override val subtitlesEmbedInVideo = "Ins Video einbetten"
    override val subtitlesSeparateFile = "Separate Datei"
    override val addSelectAll = "Alle auswählen"
    override val addClearSelection = "Leeren"
    override fun selectedOfAvailable(selected: Int, total: Int) =
        "Ausgewählt: $selected von $total verfügbar"

    override fun qualityFormatLine(format: String, details: String) = "Format: $format$details"
    override val inspectingVideoQualities = "Videoqualitäten werden geprüft..."
    override fun readingCourseLectures(done: Int, total: Int) =
        "Kurslektionen werden gelesen: ${done} / ${total}"

    override val readingCourseLecturesHint =
        "Die Browsererweiterung liest sie im Kurs-Tab: Tab offen lassen"
    override val inspectingVideoQualitiesHint =
        "Verfügbare Auflösungen und Audiospuren werden gesucht"
    override val videoInspectionFailed = "Videoprüfung fehlgeschlagen"
    override val addRetryInspection = "Prüfung wiederholen"
    override val mediaEngineStarting = "Medien-Engine startet..."
    override val mediaEngineNotReady = "Medien-Engine ist nicht bereit"
    override val mediaEngineRequiresYtDlp =
        "Zum Extrahieren von Videos wird yt-dlp benötigt (installiert oder eingebettet)."
    override val preparingEngine = "Engine wird vorbereitet..."
    override val startMediaEngine = "Medien-Engine herunterladen / starten"
    override val inspectAudioFormats = "Audioformate prüfen & auswählen"
    override val inspectVideoQuality = "Videoqualität prüfen & auswählen"
    override fun clipSectionTitle(isAudio: Boolean) =
        if (isAudio) "Audioausschnitt (optional)" else "Videoausschnitt (optional)"

    override val clipSectionHint = "Nur einen bestimmten Zeitraum herunterladen"
    override fun mediaDurationFullRange(isAudio: Boolean, clock: String, friendly: String) =
        "${if (isAudio) "Audiolänge" else "Videolänge"}: $clock ($friendly) - standardmäßig komplett ausgewählt"

    override val clipStartTimeFrom = "Startzeit (von)"
    override val clipEndTimeTo = "Endzeit (bis)"
    override fun fullMediaChip(isAudio: Boolean, duration: String) =
        if (isAudio) "Ganzes Audio ($duration)" else "Ganzes Video ($duration)"

    override val clipFirst5Min = "Erste 5 Min."
    override val clipFirst15Min = "Erste 15 Min."
    override val clipFirst30Min = "Erste 30 Min."
    override val clipFirst1Hour = "Erste Stunde"
    override val clipModeTitle = "Wie der Ausschnitt geladen wird"
    override val clipOnlyTitle = "Nur der Ausschnitt"
    override val clipOnlyLine1 = "Lädt nur den gewählten Teil"
    override val clipOnlyLine2 = "Langsamer: YouTube drosselt das Tempo"
    override val clipOnlyLine3 = "Pause startet den Ausschnitt neu"
    override fun fullThenCutTitle(isAudio: Boolean) =
        if (isAudio) "Ganzes Audio, dann schneiden" else "Ganzes Video, dann schneiden"

    override fun fullThenCutLine1(isAudio: Boolean, sizeText: String) =
        if (isAudio) "Lädt das ganze Audio$sizeText" else "Lädt das ganze Video$sizeText"

    override val fullThenCutLine2 = "Schneller: volles Tempo"
    override val fullThenCutLine3 = "Behält danach nur deinen Teil"
    override val fullThenCutLine4 = "Pausieren / Fortsetzen funktioniert"
    override val inspectingTorrent = "Torrent-Metadaten werden geprüft..."
    override val inspectingTorrentHint = "Dateiliste und Peers werden abgerufen"
    override val torrentInspectionFailed = "Torrent-Prüfung fehlgeschlagen"
    override val inspectTorrentFiles = "Torrent-Dateien prüfen"
    override val disableTorrentUpload = "Torrent-Upload deaktivieren (nur Leechen)"
    override val torrentFiles = "Torrent-Dateien"
    override val addDeselectAll = "Alle abwählen"
    override val torrentPriorityHigh = "Hoch"
    override val torrentPriorityLow = "Niedrig"
    override val torrentPriorityNormal = "Normal"
    override fun selectedOfLinks(selected: Int, total: Int) =
        if (total == 1) "$selected von $total Link" else "$selected von $total Links"

    override val readingTorrentFiles = "Dateien des Torrents werden gelesen…"
    override fun couldNotReadTorrentFiles(error: String) =
        "Dateien konnten nicht gelesen werden ($error); alle werden heruntergeladen."

    override val linkTagVideo = "VIDEO"
    override val linkTagAudio = "AUDIO"
    override val linkTagTorrent = "TORRENT"
    override val linkTagFile = "DATEI"
    override val linkListMagnet = "Magnet-Link"
    override fun linkListYouTubeVideo(id: String) = "YouTube-Video $id"
    override fun playlistSummary(videos: Int, selected: Int) =
        "Playlist · $videos ${if (videos == 1) "Video" else "Videos"} · $selected ausgewählt"

    override val playlistFolder = "In Ordner speichern"
    override val playlistFolderHint =
        "Alle Dateien kommen in diesen Ordner. Leeren, um sie direkt zu speichern."
    override val qualityForAllVideos = "Qualität für alle Videos"
    override val qualityForAllVideosHint =
        "Hat ein Video diese Qualität nicht, wird die nächstniedrigere genommen."
    override val downloadOnlyThisVideo = "Stattdessen nur dieses Video herunterladen"
    override val whenToStart = "Wann starten"
    override val scheduleNow = "Jetzt"
    override val scheduleIn30Min = "In 30 Min."
    override val scheduleIn1Hour = "In 1 Stunde"
    override val scheduleAtTime = "Zu einer Uhrzeit…"
    override val scheduleDayLabel = "Tag"
    override val scheduleToday = "Heute"
    override val scheduleTomorrow = "Morgen"
    override val scheduleIn2Days = "In 2 Tagen"
    override val scheduleIn3Days = "In 3 Tagen"
    override val scheduleInAWeek = "In einer Woche"
    override val scheduleTimePassed =
        "Diese Uhrzeit ist heute schon vorbei. Wähle eine spätere Zeit oder einen anderen Tag."
    override val scheduleHourLabel = "Stunde"
    override val scheduleMinuteLabel = "Minute"
    override val scheduleSet = "Festlegen"
    override val scheduleSetTime = "Uhrzeit festlegen"
    override fun scheduleStartsAt(time: String) = "Start: $time (FetchJet muss laufen)"
    override val addConnectionsTitle = "Verbindungen"
    override val connectionsAuto = "Auto"
    override fun connectionsAutoWithDefault(n: Int) = "Auto ($n)"
    override fun pickerExceedsVideo(duration: String) = "Überschreitet die Videolänge ($duration)"
    override fun pickerMustBeAfterStart(start: String) = "Muss nach der Startzeit liegen ($start)"
    override val pickerStartZero = "Anfang (00:00)"
    override val pickerHours = "Stunden"
    override val pickerMinutes = "Minuten"
    override val pickerSeconds = "Sekunden"
    override fun pickerSelectedTime(time: String, summary: String) =
        "Gewählte Zeit: $time ($summary)"

    override fun pickerEndOfVideo(time: String) = "Videoende ($time)"
    override fun pickerMin(time: String) = "Min. ($time)"
    override fun pickerApply(time: String) = "Übernehmen ($time)"
    override val pickerInvalidTime = "Ungültige Zeit"
    override val dirPickerSelectFolder = "Ordner wählen"
    override val dirPickerUp = "Nach oben"
    override fun dirPickerSubfolders(n: Int) = "Unterordner ($n)"
    override val dirPickerHiddenOn = "Versteckte: an"
    override val dirPickerHiddenOff = "Versteckte: aus"
    override val dirPickerNewFolder = "Neuer Ordner"
    override val dirPickerFolderName = "Ordnername…"
    override val dirPickerCreateFailed = "Ordner konnte nicht erstellt werden"
    override val dirPickerCreate = "Erstellen"
    override val dirPickerNoSubfolders =
        "Keine Unterordner in diesem Verzeichnis.\nDateien werden direkt hier gespeichert."
    override val dirPickerHiddenTag = "(versteckt)"
    override val speedLimitUnlimited = "Unbegrenzt (Max.)"
    override val speedPresetsTitle = "Geschwindigkeits-Voreinstellungen"
    override val speedPresetsFooter =
        "Lege eine Geschwindigkeit für Schnell, Mittel und Niedrig fest. Wähle schnell eine in der Kopfzeile; die Schildkröte ist immer 512 KB/s."
    override val speedPresetHigh = "Hoch"
    override val speedPresetFast = "Schnell"
    override val speedPresetMedium = "Mittel"
    override val speedPresetLow = "Niedrig"
    override val speedScheduleTitle = "Geschwindigkeitsplan"
    override val speedScheduleFooter =
        "Begrenze die Geschwindigkeit zu festgelegten Zeiten; sonst unbegrenzt."
    override val speedScheduleAdd = "Zeitfenster hinzufügen"
    override val speedScheduleFrom = "Von"
    override val speedScheduleTo = "Bis"
    override val speedScheduleActive = "Geplantes Limit aktiv"
    override val speedScheduleEmpty = "Noch keine Zeitfenster – füge eines hinzu."
    override val priorityLabel = "Priorität"
    override val priorityHigh = "Hoch"
    override val priorityNormal = "Normal"
    override val priorityLow = "Niedrig"
    override val checksumLabel = "Prüfsumme (optional)"
    override val checksumHint = "sha256 oder md5"
    override val checksumVerified = "Prüfsumme bestätigt"
    override val checksumMismatch = "Prüfsumme stimmt nicht"
    override val checksumChecking = "Prüfsumme wird überprüft…"
    override val speedLimitNoLimit = "Kein Limit (volles Tempo)"
    override val speedLimitCustom = "Eigener Wert..."
    override val speedLimitValue = "Wert"
    override val scheduleAm = "AM"
    override val schedulePm = "PM"
    override val detailsChipMedia = "Medien"
    override val detailsChipTorrent = "Torrent"
    override val detailsChipFile = "Datei"
    override val detailsChipResumable = "Fortsetzbar"
    override val detailsChipNoResume = "Nicht fortsetzbar"
    override fun detailsClipChip(start: String, end: String?, cutAfterDownload: Boolean) =
        "Ausschnitt $start–${end ?: "Ende"}" + if (cutAfterDownload) " · nach dem Download geschnitten" else ""

    override fun detailsTorrentFiles(selected: Int, total: Int) =
        "Torrent-Dateien ($selected ausgewählt / $total)"

    override val detailsPriorityHigh = "Hoch"
    override val detailsPriorityLow = "Niedrig"
    override val detailsPriorityNormal = "Normal"
    override val detailsStatusCompleted = "Abgeschlossen"
    override val detailsStatusPaused = "Pausiert"
    override val detailsStatusFailed = "Fehlgeschlagen"
    override val detailsStatusWaiting = "Wartend"
    override val detailsStatusQueued = "In Warteschlange"
    override val detailsStatusNotStarted = "Nicht gestartet"
    override val detailsUnknownSize = "Unbekannt"
    override val detailsMergingAudioVideo = "Audio & Video werden zusammengeführt..."
    override fun detailsTorrentSpeedUploadDisabled(download: String) =
        "DL: $download (UL deaktiviert)"

    override fun detailsTorrentSpeed(download: String, upload: String) =
        "DL: $download | UL: $upload"

    override val detailsConnecting = "Verbinde..."
    override fun detailsStartsAt(time: String) = "Start: $time"
    override fun detailsWaitingInQueuePosition(position: Int) =
        "Wartet in der Warteschlange (Nr. $position)"

    override val detailsWaitingInQueue = "Wartet in der Warteschlange"
    override val detailsDownloaded = "Heruntergeladen"
    override val detailsSpeed = "Tempo"
    override val detailsState = "Status"
    override val detailsTimeLeft = "Restzeit"
    override val detailsAdded = "Hinzugefügt"
    override val detailsSavedTo = "Gespeichert in"
    override val detailsSource = "Quelle"
    override val detailsPage = "Seite"
    override val detailsOpenFile = "Datei öffnen"
    override val detailsOpenFolder = "Ordner öffnen"
    override val detailsStartNow = "Jetzt starten"
    override val detailsRemoveFromList = "Aus der Liste entfernen"
    override val detailsCancelDownload = "Download abbrechen"
    override val detailsHeaderDownload = "Download"
    override val detailsHeaderDetails = "DETAILS"
    override fun detailsAddedToday(clock: String) = "Heute $clock"
    override fun detailsAddedYesterday(clock: String) = "Gestern $clock"

    override fun removeDialogTitle(count: Int) =
        if (count > 1) "$count Downloads entfernen" else "Download entfernen"

    override fun removeDialogMessage(count: Int) =
        if (count > 1) "Möchtest du diese $count Downloads wirklich entfernen?"
        else "Möchtest du diesen Download wirklich entfernen?"

    override val removeDialogAlsoDeleteFiles =
        "Heruntergeladene Datei(en) auch vom Datenträger löschen"

    override val duplicateDialogTitle = "Download existiert bereits"
    override val duplicateDialogSubtitle = "Dieser Eintrag ist bereits in deiner Download-Liste"
    override fun duplicateDialogStatus(status: String) = "Status: $status"
    override val duplicateDialogQuestion =
        "Möchtest du den vorhandenen Download fortsetzen oder ganz von vorn beginnen?"
    override val duplicateDialogResumeExisting = "Vorhandenen fortsetzen"
    override val duplicateDialogRedownload = "Neu herunterladen (von vorn)"

    override val overwriteDialogTitle = "Datei existiert bereits"
    override val overwriteDialogSubtitle =
        "Im Zielordner gibt es bereits eine Datei mit diesem Namen"
    override val overwriteDialogFileNameLabel = "Dateiname: "
    override val overwriteDialogFolderLabel = "Ordner: "
    override val overwriteDialogNewCopyLabel = "Neue Kopie: "
    override val overwriteDialogQuestion =
        "Möchtest du diesen Download als neue Kopie speichern oder die vorhandene Datei überschreiben?"

    override fun overwriteDialogSaveAsCopy(name: String) = "Als Kopie speichern ($name)"
    override val overwriteDialogOverwriteExisting = "Vorhandene Datei überschreiben"

    override val desktopWindowTagline = "Schneller Download-Manager mit mehreren Engines"
    override val desktopTrayIdleTooltip = "FetchJet - läuft im Hintergrund"
    override val desktopTrayDownloadsHeader = "FetchJet-Downloads:"
    override fun desktopTrayMore(count: Int) = "+$count weitere"
    override val desktopTrayShowApp = "App anzeigen"
    override val desktopTrayCloseApp = "App beenden"
    override val sectionGeneral = "Allgemein"
    override val sectionGeneralBlurb = "Aussehen der App und Startverhalten"
    override val sectionDownloads = "Downloads"
    override val sectionDownloadsBlurb = "Wohin Dateien gehen und wie viele gleichzeitig laufen"
    override val sectionBrowser = "Browser-Erweiterung"
    override val sectionBrowserBlurb = "Links und Videos aus dem Browser an FetchJet senden"
    override val sectionSpeed = "Tempolimits"
    override val sectionSpeedBlurb = "Bandbreite von FetchJet begrenzen"
    override val sectionMedia = "Medien-Tools"
    override val sectionMediaBlurb = "yt-dlp und FFmpeg für Videoseiten"
    override val sectionTorrent = "BitTorrent"
    override val sectionTorrentBlurb = "Peers, Upload und die Torrent-Engine"
    override val sectionUpdates = "Updates"
    override val sectionUpdatesBlurb = "Deine Version und neue Versionen"
    override val summaryNoQueueLimit = "unbegrenzt"
    override fun summaryAtATime(n: Int) = "$n gleichzeitig"
    override val summaryNoSpeedLimit = "Kein Limit"
    override val summarySetupNeeded = "Einrichtung nötig"
    override val summaryUploadingOff = "Upload aus"
    override val summaryUploadingOn = "Upload an"
    override fun summaryVersionAvailable(version: String) = "$version verfügbar"
    override fun summaryVersion(version: String) = "Version $version"
    override val changesSavedAutomatically = "Änderungen werden automatisch gespeichert."
    override val settingsAllSettings = "Alle Einstellungen"

    override val clipboardGroup = "Zwischenablage"
    override val clipboardFooter =
        "Kopiere irgendwo einen Link zu einer Datei (zip, mp4, pdf, apk…) oder einen Magnet-Link, und FetchJet fragt, ob er heruntergeladen werden soll."
    override val watchClipboard = "Zwischenablage überwachen"
    override val watchClipboardOn = "An · kopierte Dateilinks werden zum Download angeboten"
    override val settingOff = "Aus"
    override val alsoVideoPages = "Auch Videoseiten"
    override val alsoVideoPagesHint =
        "Auch Links von YouTube, Facebook, TikTok, Vimeo… werden angeboten (nicht nur Dateien)"
    override val diagnosticsGroup = "Diagnose"
    override fun logSavedTo(path: String) =
        "Gespeichert: $path — schick diese Datei mit, wenn du ein Problem meldest."

    override val diagnosticsFooter =
        "Speichert das Protokoll der App (was yt-dlp und die Engines getan haben) in deinem Downloads-Ordner."
    override val saveLogToDownloads = "Protokoll in Downloads speichern"
    override val logCouldNotSave = "konnte nicht gespeichert werden"
    override val appearanceGroup = "Darstellung"
    override val themeTitle = "Design"
    override val themeHint = "Helles oder dunkles Aussehen für alle Bildschirme"
    override val settingsThemeDark = "Dunkel"
    override val settingsThemeLight = "Hell"
    override val speedShownIn = "Tempo anzeigen in"
    override val speedShownInHint =
        "Bytes (MB/s) wie bei Dateigrößen oder Bits (Mbps) wie bei deinem Internettarif"

    override val saveLocationGroup = "Speicherort"
    override val saveLocationFooter =
        "Neue Downloads landen hier, außer du wählst beim Hinzufügen einen anderen Ordner."
    override val settingsFolderPath = "Ordnerpfad"
    override val settingsChooseFolder = "Ordner wählen…"
    override val settingsUseDownloads = "Downloads verwenden"
    override val settingsConnectionsGroup = "Verbindungen"
    override val connectionsFooter =
        "In wie viele Teile ein direkter Download aufgeteilt wird. Jedes Teil ist eine Verbindung; ist eines fertig, übernimmt es die Hälfte des größten verbleibenden Teils, sodass keines untätig bleibt. Auto wählt nach Dateigröße (4–8). Manche Server begrenzen die Verbindungen pro Client."
    override val settingsConnectionsPerDownload = "Verbindungen pro Download"
    override val settingsConnectionsAuto = "Auto"
    override val organizeGroup = "Ordnen"
    override val organizeFooter =
        "Im Speicherort werden Unterordner angelegt: Video, Music, Archives, Documents, Programs, Images, Torrents."
    override val sortIntoFolders = "Nach Typ in Ordner sortieren"
    override val sortIntoFoldersOn = "An · z. B. Downloads/Video/vorlesung.mp4"
    override val sortIntoFoldersOff = "Aus · alles kommt direkt in den Ordner"

    override val speedLimitsFooter = "Gilt sofort und wird auf die laufenden Downloads aufgeteilt."
    override val speedLimitsFooterIos =
        "Gilt sofort und wird auf die laufenden Downloads aufgeteilt. Unter iOS behalten HTTP-Downloads, die bereits im Hintergrund laufen, ihr Tempo; das Limit gilt für danach gestartete."
    override val speedLimitDownload = "Download"
    override val speedLimitUploadTorrents = "Upload (Torrents)"

    override val torrentSharingGroup = "Teilen"
    override val downloadOnlyNoUpload = "Nur herunterladen (kein Upload)"
    override val downloadOnlyNoUploadHint =
        "Spart Upload-Bandbreite und Datenvolumen. Manche Tracker laden für Peers, die nicht teilen, langsamer."
    override val torrentEngineGroup = "Engine"
    override val settingsReady = "Bereit"
    override val settingsNotReady = "Nicht bereit"

    override val extInstallGroup = "Installieren"
    override val extensionInstallFooter =
        "Mit der Erweiterung erscheint auf Videos und Links ein Button „Mit FetchJet herunterladen“, und der Browser kann seine Downloads an FetchJet übergeben. Sie kommuniziert nur mit dieser App auf deinem Gerät."
    override val edgeAddedHint =
        "Hinzugefügt · starte Edge neu und klicke auf „Erweiterung aktivieren“, wenn es gefragt wird"
    override val edgeFromStoreHint = "Aus Microsoft Edge Add-ons · aktualisiert sich selbst"
    override val openInEdgeStore = "Im Edge-Store öffnen"
    override val edgeAddedNote =
        "Fertig: Starte Edge neu, dann fragt es, ob FetchJet aktiviert werden soll."
    override val extAddToEdge = "Zu Edge hinzufügen"
    override val firefoxFromStoreHint = "Aus Firefox Add-ons · aktualisiert sich selbst"
    override val extAddToFirefox = "Zu Firefox hinzufügen"
    override val chromeZipHint =
        "Nicht im Chrome Web Store: Installiere die .zip im Entwicklermodus (Schritte auf der Seite). Sie aktualisiert sich nicht selbst."
    override val getTheZip = ".zip holen"
    override val firefoxAndroidHint =
        "Öffne dies in Firefox auf diesem Handy. Gesendete Links kommen direkt in diese App."
    override val keepReadyForBrowser = "Für den Browser bereithalten"
    override val keepReadyOn =
        "Videoqualitäten erscheinen direkt in Firefox. Solange das an ist, bleibt eine stille Benachrichtigung."
    override val keepReadyOff =
        "Aus: Firefox öffnet FetchJet und du wählst die Qualität hier. An: Qualitäten erscheinen in Firefox (mit Benachrichtigung)."
    override val popUpOverBrowser = "Über dem Browser einblenden"
    override val popUpOverBrowserOn =
        "An · FetchJet öffnet sich selbst, wenn Firefox einen Link sendet"
    override val popUpOverBrowserOff =
        "Aus · stattdessen erscheint eine Benachrichtigung; tippe, um „Über anderen Apps einblenden“ zu erlauben"

    override val mediaToolsGroup = "Tools"
    override val ytDlpHint = "Findet Video und Audio auf YouTube und anderen Seiten"
    override val settingsMissing = "Fehlt"
    override val ffmpegHint =
        "Fügt Video + Audio zusammen (ab 1080p), schneidet Ausschnitte, erstellt MP3"
    override val ffmpegMissing = "Fehlt · bis 720p"
    override fun jsRuntimeTitle(name: String) = "JS-Laufzeit · $name"
    override val jsRuntimeHint =
        "Löst die Prüfungen von YouTube, damit jede Qualität erscheint, schneller"

    override fun missingPress(button: String) = "Fehlt · tippe auf $button"
    override val mediaToolsFolder = "Tools-Ordner"
    override val mediaUpdatingTools = "Wird aktualisiert…"
    override val mediaDownloadingTools = "Tools werden heruntergeladen…"
    override fun mediaDownloadingTool(name: String) = "$name wird heruntergeladen…"
    override fun mediaDownloadingToolPercent(name: String, percent: Int) =
        "$name wird heruntergeladen… $percent %"

    override val mediaUpdateTools = "Tools aktualisieren"
    override val mediaDownloadTheTools = "Tools herunterladen"

    override val updateNewVersionAvailable = "Neue Version verfügbar"
    override fun updateVersionLine(version: String, current: String) =
        "FetchJet $version  ·  du hast $current"

    override val updateWhatsNew = "Neuerungen"
    override val updateNoReleaseNotes = "Keine Versionshinweise."
    override fun downloadingUpdate(percent: Int) = "Update wird heruntergeladen… $percent %"
    override val updateDownloadedAndroid =
        "Das Update ist heruntergeladen. Android bittet dich, die Installation zu bestätigen; deine Downloads bleiben in der Liste."
    override val updateDownloadedDesktop =
        "Das Update ist heruntergeladen. FetchJet wird geschlossen, installiert es und startet neu. Laufende Downloads werden pausiert und können fortgesetzt werden."
    override val storeInstallsItselfOpenNow =
        "Der Microsoft Store installiert es selbst. Öffne den Store, um jetzt zu aktualisieren."
    override val updateInstall = "Installieren"
    override val updateRestartAndUpdate = "Neu starten & aktualisieren"
    override val updateDownloading = "Wird heruntergeladen…"
    override val updateDownloadAndInstall = "Herunterladen & installieren"
    override val updateOpenMicrosoftStore = "Microsoft Store öffnen"
    override fun updateDownloadWithSize(size: String) = "$size herunterladen"
    override val updateOpenReleasePage = "Release-Seite öffnen"
    override val updateSkipThisVersion = "Diese Version überspringen"
    override val updateLater = "Später"
    override fun versionIsAvailable(version: String) = "Version $version ist verfügbar"
    override val youHaveLatest = "Du hast die hier installierte Version"
    override val updateSeeWhatsNew = "Neuerungen ansehen"
    override val updateChecking = "Wird geprüft…"
    override val updateCheckNow = "Jetzt prüfen"
    override val checkUpdatesAutomatically = "Automatisch nach Updates suchen"
    override val checkUpdatesAutomaticallyHint =
        "Sucht beim Start von FetchJet nach einer neuen Version (höchstens zweimal am Tag)."
    override val downloadUpdatesAutomatically = "Updates automatisch herunterladen"
    override val downloadUpdatesAutomaticallyHintAndroid =
        "Lädt eine neue Version im Hintergrund; du bestätigst nur die Installation."
    override val downloadUpdatesAutomaticallyHint =
        "Lädt eine neue Version im Hintergrund; du bestätigst nur den Neustart."
    override val installingUpdates = "Updates installieren"
    override val installingUpdatesStore =
        "Der Microsoft Store installiert neue Versionen selbst; FetchJet sagt dir, wenn eine erschienen ist."
    override val installingUpdatesManual =
        "Auf diesem Gerät weist FetchJet auf eine neue Version hin und verlinkt sie; installieren musst du sie selbst."
    override val updateReleaseNotes = "Versionshinweise"
    override val updateReleaseNotesHint = "Jede Version und was sich geändert hat"
    override val installHintReleasePage =
        "Öffne die Release-Seite, um die neue Version herunterzuladen."
    override val installHintApk =
        "Lade die APK herunter und öffne sie, um das Update über diese Version zu installieren."
    override val installHintIpa =
        "Lade die .ipa herunter und installiere sie mit AltStore / Sideloadly, so wie du FetchJet installiert hast."
    override val installHintDmg =
        "Lade die .dmg herunter, öffne sie und zieh FetchJet in „Programme“ (die alte Version ersetzen)."
    override val installHintDeb =
        "Lade die .deb herunter und installiere sie (Doppelklick oder sudo apt install ./file.deb)."
    override val installHintOther = "Lade die neue Version herunter und installiere sie über diese."

    override val startupGroup = "Autostart"
    override val startupStoreFooter =
        "Die Store-Version wird von Windows gestartet: Aktiviere FetchJet unter Einstellungen > Apps > Autostart."
    override val startupStartAtLogin = "FetchJet bei der Anmeldung starten"
    override val startupManagedByWindows = "Von Windows verwaltet"
    override val startupOpenWindowsSettings = "Windows-Einstellungen öffnen"
    override val startupStartHidden = "Versteckt im Infobereich starten"
    override val startupStartHiddenHint =
        "Beim Start nach der Anmeldung im Infobereich bleiben, statt das Fenster zu öffnen."
    override val startupStartAtLoginHint =
        "Geplante Downloads und die Warteschlange laufen weiter, und die Browser-Erweiterung findet die App immer."

    override fun startupCouldNotChange(message: String) =
        "Einstellung konnte nicht geändert werden: $message"

    override val whenDoneGroup = "Wenn alle Downloads fertig sind"
    override val whenDoneHint = "Nur für diese Sitzung. Vorher haben Sie 60 Sekunden zum Abbrechen."
    override val whenDoneNothing = "Nichts tun"
    override val whenDoneExit = "FetchJet beenden"
    override val whenDoneSleep = "Energie sparen"
    override val whenDoneShutDown = "Herunterfahren"
    override val whenDoneTitle = "Alle Downloads fertig"
    override fun whenDoneExitIn(seconds: Int) = "FetchJet wird in ${seconds} s beendet."
    override fun whenDoneSleepIn(seconds: Int) =
        "Der Computer geht in ${seconds} s in den Energiesparmodus."

    override fun whenDoneShutDownIn(seconds: Int) =
        "Der Computer wird in ${seconds} s heruntergefahren."

    override val whenDoneNow = "Jetzt"
    override fun whenDoneBanner(action: String) = "Nach den Downloads: ${action}"
    override fun whenDoneFailed(message: String) = "Nicht möglich: ${message}"
    override val sectionNetwork = "Netzwerk"
    override val sectionNetworkBlurb = "Proxy und Website-Anmeldungen"
    override val netProxyGroup = "Proxy"
    override val netProxyOff = "Aus"
    override val netProxyHost = "Server"
    override val netProxyPort = "Port"
    override val netProxyUser = "Benutzername (optional)"
    override val netProxyPassword = "Passwort"
    override val netUser = "Benutzername"
    override val netSave = "Speichern"
    override val netSaved = "Gespeichert"
    override val netProxyHint =
        "Wird von Downloads, Video-Downloads (yt-dlp) und Torrents verwendet. Gilt für Downloads, die nach dem Speichern starten."
    override val netProxyHintIos =
        "Wird von Downloads und Video-Downloads verwendet. Torrents unter iOS verbinden sich direkt. Gilt für Downloads, die nach dem Speichern starten."
    override val netProxyBadPort = "Gib einen Server und einen Port von 1 bis 65535 ein"
    override val netLoginsGroup = "Website-Anmeldungen"
    override val netLoginsHint =
        "Für Downloads, die Benutzername und Passwort verlangen (HTTP Basic Auth). Eine Website umfasst auch ihre Subdomains."
    override val netLoginSite = "Website (example.com)"
    override val netLoginAdd = "Hinzufügen"
    override val netLoginsEmpty = "Keine gespeicherten Anmeldungen"
    override val netRemove = "Entfernen"
    override fun netLoginsCount(n: Int) = "${n} Anmeldungen"
    override val sysTabAllDownloads = "Alle Downloads"
    override val sysTabActive = "Aktiv"
    override val sysTabCompleted = "Abgeschlossen"
    override val sysTabMediaExtractor = "Medien-Extraktor"
    override val sysTabSettings = "Einstellungen"
    override val sysSortNewestFirst = "Neueste zuerst"
    override val sysSortOldestFirst = "Älteste zuerst"
    override val sysSortLargestFirst = "Größte zuerst"
    override val sysSortSmallestFirst = "Kleinste zuerst"
    override val sysSortNameAz = "Name A–Z"
    override val sysEnterValidUrlOrMagnet = "Bitte gib eine gültige URL oder einen Magnet-Link ein"
    override val sysCannotStartVideoInspectionFailed =
        "Download kann nicht starten: Videoprüfung fehlgeschlagen."
    override val sysCannotStartTorrentInspectionFailed =
        "Download kann nicht starten: Torrent-Prüfung fehlgeschlagen oder keine Dateien ausgewählt."
    override val sysClipEndExceedsDuration =
        "Das Ende des Ausschnitts überschreitet die Videolänge."
    override val sysClipStartAfterEnd = "Der Anfang des Ausschnitts muss vor dem Ende liegen."
    override fun sysStartedDownload(name: String) = "Download gestartet: $name"
    override val sysFailedToQueueDownload = "Download konnte nicht eingereiht werden"
    override fun sysQualitiesNotAdded(count: Int) =
        if (count == 1) "$count Qualität konnte nicht hinzugefügt werden." else "$count Qualitäten konnten nicht hinzugefügt werden."

    override fun sysAddedQualityDownloads(count: Int, title: String) =
        "${if (count == 1) "$count Download" else "$count Downloads"} von „$title“ hinzugefügt"

    override val sysSelectAtLeastOnePlaylistVideo = "Wähle mindestens ein Video der Playlist aus."
    override fun sysVideosNotAdded(count: Int) =
        if (count == 1) "$count Video konnte nicht hinzugefügt werden." else "$count Videos konnten nicht hinzugefügt werden."

    override fun sysAddedPlaylistVideos(count: Int, playlistTitle: String) =
        "${if (count == 1) "$count Video" else "$count Videos"} aus „$playlistTitle“ hinzugefügt"

    override val sysSelectAtLeastOneLink = "Wähle mindestens einen Link aus."
    override fun sysAddedLinkDownloads(added: Int, skipped: Int) =
        "${if (added == 1) "$added Download" else "$added Downloads"} hinzugefügt" +
                (if (skipped > 0) " ($skipped bereits in der Liste oder fehlgeschlagen)" else "")

    override val sysCouldNotReadTorrent = "Torrent konnte nicht gelesen werden"
    override fun sysCouldNotStartDownload(reason: String) =
        "Download konnte nicht gestartet werden: $reason"

    override val sysUnknownError = "unbekannter Fehler"
    override fun sysQueuedFromBrowser(added: Int, skipped: Int) =
        "${if (added == 1) "$added Download" else "$added Downloads"} aus dem Browser eingereiht" +
                (if (skipped > 0) " ($skipped übersprungen)" else "")

    override val sysEnterTorrentOrMagnetFirst =
        "Bitte gib zuerst einen Torrent oder Magnet-Link ein"
    override val sysFailedToParseTorrentMetadata = "Torrent-Metadaten konnten nicht gelesen werden"
    override val sysEnterMediaUrlFirst = "Bitte gib zuerst eine Medien-URL ein"
    override fun sysUnableToFetchFormatsWithReason(reason: String) =
        "Videoformate konnten nicht abgerufen werden: $reason"

    override val sysUnableToFetchFormats =
        "Videoformate konnten nicht abgerufen werden. Prüfe die URL oder deine Internetverbindung."
    override val sysFailedToInspectMediaQualities = "Medienqualitäten konnten nicht geprüft werden"
    override fun sysTorrentEngineError(message: String) = "Fehler der BitTorrent-Engine: $message"
    override fun sysTorrentEngineInstallFailed(message: String) =
        "Installation der BitTorrent-Engine fehlgeschlagen: $message"

    override val sysMediaToolsDownloaded = "Medien-Tools erfolgreich heruntergeladen!"
    override val sysMediaToolsDownloadFailed = "Medien-Tools konnten nicht heruntergeladen werden"
    override val sysMediaToolsUpdated = "Medien-Tools aktualisiert"
    override fun sysSavedLinks(count: Int, path: String) =
        "${if (count == 1) "$count Link" else "$count Links"} gespeichert in $path"

    override fun sysCouldNotSaveLinks(reason: String) =
        "Links konnten nicht gespeichert werden: $reason"

    override val sysErrorFallback = "Fehler"
    override val sysDownloadFailed = "Download fehlgeschlagen"
    override val sysDownloadFailedNetwork = "Download wegen eines Netzwerkfehlers fehlgeschlagen"
    override val sysCouldNotStart = "Start nicht möglich"
    override val sysUnknownErrorCapital = "Unbekannter Fehler"
    override fun sysCouldNotSaveFileTo(dir: String) =
        "Datei konnte nicht in $dir gespeichert werden"

    override val sysPlaylistFallbackTitle = "Playlist"
    override val sysPlaylistQualityBest = "Beste"
    override val sysPlaylistQualityAudioOnly = "Nur Audio"
    override val sysToolsReadyYtDlpFfmpeg = "Bereit: yt-dlp & ffmpeg installiert (1080p und mehr)"
    override val sysToolsReadyYtDlpNoFfmpeg = "Bereit: yt-dlp installiert (kein ffmpeg - max. 720p)"
    override val sysToolsNotInstalled = "Nicht installiert. Klicke unten auf „Tools herunterladen“."
    override val sysToolsInstalledBuiltIn = "Installiert & bereit (integriert)"
    override fun sysYtDlpVersionReady(version: String) = "yt-dlp $version (bereit)"
    override val sysMediaEngineBridgeNotRegistered = "Brücke der Medien-Engine nicht registriert"
    override val sysWebKitBuiltIn = "WebKit (integriert)"
    override val sysTorrentEngineBuiltIn = "Bereit: BitTorrent-Engine integriert"
    override fun sysTorrentEngineReadyVersion(version: String) =
        "Bereit: BitTorrent-Engine (libtorrent $version)"

    override fun sysTorrentEngineFailedToLoad(message: String) =
        "BitTorrent-Engine konnte nicht geladen werden: $message"

    override val sysTorrentEngineAvailable =
        "Bereit: BitTorrent-Engine verfügbar (lädt beim ersten Torrent)"
    override val sysTorrentEngineMissing =
        "BitTorrent-Engine fehlt. Klicke unten auf „Tools herunterladen“."
    override val sysTorrentEngineNotAvailable =
        "Die BitTorrent-Engine ist in diesem Build nicht verfügbar"

    override fun sysCouldNotStartInstaller(message: String) =
        "Installer konnte nicht gestartet werden: $message"

    override val sysCheckingForUpdates = "Suche nach Updates…"
    override val sysNoPublishedRelease = "Noch keine veröffentlichte Version."
    override fun sysVersionNoChangesForDevice(version: String, current: String) =
        "Version $version bringt nichts Neues für dieses Gerät; du hast bereits den neuesten Build dafür ($current)."

    override fun sysVersionAvailable(version: String) = "Version $version ist verfügbar."
    override fun sysUpToDate(current: String) = "Du bist auf dem neuesten Stand ($current)."
    override fun sysCouldNotCheckForUpdates(reason: String) =
        "Suche nach Updates fehlgeschlagen: $reason"

    override val sysNetworkError = "Netzwerkfehler"
    override val sysUpdateNoInstallerForSystem =
        "Update-Download fehlgeschlagen: Diese Version hat keinen Installer für dieses System."

    override fun sysDownloadingAsset(name: String) = "$name wird heruntergeladen…"
    override fun sysVersionReadyToInstall(version: String) =
        "Version $version ist bereit zur Installation."

    override fun sysUpdateDownloadFailed(message: String) =
        "Update-Download fehlgeschlagen: $message"

    override val sysAllowUnknownAppsThenInstall =
        "erlaube „Unbekannte Apps installieren“ für FetchJet und tippe dann erneut auf Installieren."
    override val sysDownloadsChannelName = "FetchJet-Downloads"
    override val sysDownloadsChannelDescription = "Zeigt den Download-Fortschritt in Echtzeit"
    override val sysNotificationAppTitle = "FetchJet Downloader"
    override val sysAppRunningInBackground = "Die App läuft im Hintergrund"
    override val sysNotificationDownloadsFallback = "Downloads"
    override fun sysFilesDownloading(count: Int) =
        if (count == 1) "$count Datei wird geladen" else "$count Dateien werden geladen"

    override fun sysPercentActive(percent: Int, count: Int) = "$percent % • $count aktiv"
    override fun sysActiveCount(count: Int) = "$count aktiv"
    override fun sysDownloadCompleteSize(size: String) = "Download abgeschlossen • $size"
    override fun sysMoreDownloading(count: Int) = "+ $count weitere werden geladen"
    override val sysComplete = "Fertig"
    override fun sysNotificationDownloadsTitle(speed: String) = "FetchJet-Downloads ($speed)"
    override fun sysDownloadingPercent(count: Int, percent: Int) = "$count laden • $percent %"
    override val sysDownloadTaskFallback = "Download"
    override val sysBrowserReadyTitle = "FetchJet ist bereit für deinen Browser"
    override val sysBrowserReadyText = "Videoqualitäten erscheinen direkt in Firefox"
    override val sysTurnOff = "Ausschalten"
    override val sysBrowserReadyChannelName = "Browser-Erweiterung"
    override val sysBrowserReadyChannelDescription =
        "Wird angezeigt, solange FetchJet für die Browser-Erweiterung bereitsteht"
    override val sysDownloadingFileFallback = "Datei wird geladen"
    override val sysUnknownSize = "Unbekannt"
    override val sysStatusCompleted = "Abgeschlossen"
    override val sysStatusDownloading = "Wird geladen"
    override val sysDownloadCompleteTitle = "Download abgeschlossen"
    override fun sysFinishedDownloading(size: String) = "Download abgeschlossen • $size"
    override val sysSentToFetchJet = "An FetchJet gesendet"
    override val sysLinksChannelName = "Links aus dem Browser"
    override val sysLinksChannelDescription =
        "Tippen, um den Link zu öffnen, den FetchJet gerade erhalten hat"
    override val sysLinkReceivedTitle = "Link empfangen"
    override val sysLinkReceivedText = "Tippen, um ihn in FetchJet zu öffnen"

    override val stepCompleted = "Abgeschlossen"
    override val stepSeeding = "Wird geseedet"
    override val stepFetchingMetadata = "Metadaten werden abgerufen"
    override val stepConnectingToPeers = "Verbinde mit Peers"
    override val stepCheckingFiles = "Dateien werden geprüft"
    override val stepCheckingResumeData = "Fortsetzungsdaten werden geprüft"
    override val stepDownloading = "Wird heruntergeladen"
    override val stepFinished = "Fertig"
    override val stepConnecting = "Verbinde"
    override val stepPaused = "Pausiert"
    override val stepError = "Fehler"
    override val stepResolvingStreams = "Streams werden ermittelt"
    override val stepDownloadingParts = "Teile werden heruntergeladen"
    override val stepPreparingDownload = "Download wird vorbereitet"
    override val stepClippingVideo = "Video wird zugeschnitten"
    override val stepDownloadingMedia = "Medien werden heruntergeladen"
    override val stepAlreadyDownloaded = "Bereits heruntergeladen"
    override val stepDownloadingVideo = "Video wird heruntergeladen"
    override val stepDownloadingAudio = "Audio wird heruntergeladen"
    override val stepMergingAudioVideo = "Audio und Video werden zusammengeführt"
    override val stepConvertingToMp3 = "Wird in MP3 umgewandelt"
    override val stepCuttingClip = "Clip wird geschnitten"
    override val stepSavingToFolder = "Wird im Ordner gespeichert"
    override val stepFinalizingVideo = "Video wird fertiggestellt"
    override val stepConvertingVideo = "Video wird konvertiert"
    override val stepRepackagingVideo = "Video wird neu verpackt"
    override val stepYtDlpFallback = "Rückfall auf yt-dlp"
    override val stepNotPlainStream = "kein einfacher Stream"
    override val stepSingleConnection = "Einzelne Verbindung"
    override val stepServerNoRanges = "der Server unterstützt keine Bereiche"
    override val stepAgeRestrictedExpiredCookies =
        "Altersbeschränkt: abgelaufene Cookies in Dateien > FetchJet"
    override val stepAgeRestrictedPlaceCookies =
        "Altersbeschränkt: cookies.txt in Dateien > FetchJet ablegen"
    override val stepPreparingMediaEngine =
        "Integrierte Medien-Engine wird vorbereitet (Python startet, yt-dlp wird geladen)"
    override val stepMediaEngineNotStarted =
        "Medien-Engine nicht gestartet. Tippe auf „Tools herunterladen“."

    override fun stepNumber(step: Int, total: Int) = "Schritt ${step} von ${total}"
    override fun stepSavingAs(ext: String) = "Wird als .${ext} gespeichert"
    override fun stepJoiningParts(count: Int) =
        "${count} ${if (count == 1) "Teil wird" else "Teile werden"} zu MP4 zusammengefügt"

    override fun stepNoDataFor(seconds: Int) = "Seit ${seconds} s keine Daten"
    override fun stepPeers(peers: Int, seeds: Int) =
        "${peers} ${if (peers == 1) "Peer" else "Peers"} (${seeds} ${if (seeds == 1) "Seed" else "Seeds"})"

    override fun stepDownloadingFrom(host: String) = "Download von ${host}"
    override fun stepMediaEngineNotReady(error: String) = "Medien-Engine nicht bereit: ${error}"
    override fun stepTorrentEngineFailed(error: String) =
        "BitTorrent-Engine konnte nicht starten: ${error}"

    override fun stepReadyWith(details: String) = "Bereit: ${details}"

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
    override val visibleToDevices = "Für andere Geräte sichtbar"
    override val visibleToDevicesHint = "Andere FetchJet-Geräte in diesem Netzwerk können dieses Gerät finden und Downloads daran senden. Aus: Sie können ihnen trotzdem senden."
    override val sysDevicePairedLabel = "Paired"
    override val sysDeviceThisDevice = "This device"
    override val sysDeviceManualConnect = "Connect by IP"
    override val sysDeviceConnect = "Connect"
}
