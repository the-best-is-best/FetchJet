package com.michelle.raouf.fetch_jet.core.i18n.data_provider

object ItalianProvider : AppLocalization {
    override val localeCode = "it"
    override val languageName = "Italiano"
    override val ok = "OK"
    override val cancel = "Annulla"
    override val close = "Chiudi"
    override val save = "Salva"
    override val delete = "Elimina"
    override val remove = "Rimuovi"
    override val open = "Apri"
    override val copy = "Copia"
    override val copied = "Copiato"
    override val retry = "Riprova"
    override val pause = "Pausa"
    override val resume = "Riprendi"
    override val apply = "Applica"
    override val reset = "Ripristina"
    override val yes = "Sì"
    override val no = "No"
    override val back = "Indietro"
    override val done = "Fatto"
    override val selectAll = "Seleziona tutto"
    override val selectNone = "Deseleziona tutto"
    override val language = "Lingua"
    override val languageHint = "La lingua di tutte le schermate. Sistema segue il dispositivo."
    override val systemLanguage = "Sistema"
    override val homeSettings = "Impostazioni"
    override val homeDownloads = "Download"

    override val homeTabAll = "Tutti"
    override val homeTabActive = "Attivi"
    override val homeTabDone = "Completati"
    override val homeTabTorrent = "Torrent"
    override val homeTabMedia = "Media"
    override val homeSidebarTypeHeader = "TIPO"
    override val homeSearchDownloadsPlaceholder = "Cerca download"
    override val homeAdd = "Aggiungi"
    override val homeAddDownload = "Aggiungi download"
    override val homePauseAll = "Metti tutto in pausa"
    override val homeResumeAll = "Riprendi tutto"
    override fun homeSortBy(option: String) = "Ordina: $option"
    override val homeLightTheme = "Tema chiaro"
    override val homeDarkTheme = "Tema scuro"

    override fun homeUpdatingMediaTools(tool: String, percent: Int?) =
        "Aggiornamento strumenti media · $tool" + (percent?.let { " $it%" } ?: "…")

    override val homeSearchDownloadsByNamePlaceholder = "Cerca download per nome…"
    override fun homeNoDownloadsMatch(query: String) = "Nessun download corrisponde a \"$query\""
    override val homeNoTasksInView = "Nessun download in questa vista"
    override val homeTryAnotherName = "Prova un altro nome"
    override val homeAddDownloadToGetStarted = "Aggiungi un download per iniziare"
    override fun homeDownloadingWaitingSummary(running: Int, waiting: Int) =
        "$running in download · $waiting in attesa"

    override fun homeQueueStatus(maxAtOnce: Int) =
        "Coda: " + if (maxAtOnce <= 0) "nessun limite" else "$maxAtOnce alla volta"

    override fun homeSpeedLimitStatus(limit: String?) =
        "Limite velocità: " + (limit ?: "disattivato")

    override val homeGroupDownloading = "In download"
    override val homeGroupWaiting = "In attesa"
    override val homeGroupPausedFailed = "In pausa e non riusciti"
    override val homeGroupCompleted = "Completati"
    override fun homeSelectedCount(count: Int) =
        if (count == 1) "1 selezionato" else "$count selezionati"

    override val homeExport = "Esporta"

    override fun homeStartsAt(time: String) = "Inizia $time"
    override val homeWaitingInQueue = "In coda"
    override fun homeWaitingInQueuePosition(position: Int) = "In coda (n. $position)"
    override val homeConnecting = "Connessione…"
    override val homeStarting = "Avvio…"
    override fun homeTimeLeft(eta: String) = "$eta rimanenti"
    override fun homePausedState(percent: Int, restartsOnResume: Boolean) =
        "In pausa · $percent%" + if (restartsOnResume) " · riparte da capo alla ripresa" else ""

    override fun homeFailedState(message: String?) = "Non riuscito: " + (message ?: "errore")
    override fun homeCompletedState(size: String?) = "Completato" + (size?.let { " · $it" } ?: "")
    override val homeStartNow = "Avvia ora"
    override val homeColumnName = "NOME"
    override val homeColumnSize = "DIMENSIONE"
    override val homeColumnProgress = "AVANZAMENTO"
    override val homeColumnSpeed = "VELOCITÀ"
    override val homeColumnLeft = "RIMANENTE"
    override val homeFilterAllDownloads = "Tutti i download"
    override val homeFilterDownloading = "In download"
    override val homeFilterWaiting = "In attesa"
    override val homeFilterPaused = "In pausa"
    override val homeFilterCompleted = "Completati"
    override val homeFilterFailed = "Non riusciti"
    override val homeFilterMedia = "Video e audio"
    override val homeFilterFiles = "File"
    override val homeFilterTorrents = "Torrent"

    override val homeCantResumeTitle = "Questo download non può essere ripreso"
    override val homeCantResumeClipText =
        "Le clip tagliate durante il download non possono continuare da dove si sono fermate. " +
                "Se metti in pausa ora, alla ripresa la clip ripartirà dall'inizio."
    override val homeCantResumeServerText =
        "Il server non supporta la ripresa. Se metti in pausa ora, alla ripresa il download " +
                "ripartirà dall'inizio."
    override val homePauseAnyway = "Metti in pausa comunque"
    override val homeKeepDownloading = "Continua il download"
    override val homeMagnetLink = "Link magnet"
    override val homeClipboardOfferTitle = "Scaricare il link che hai copiato?"
    override val homeClipboardOfferHint =
        "Puoi disattivarlo in Impostazioni > Generali > Controlla gli appunti."
    override val homeDownload = "Scarica"
    override val homeNotNow = "Non ora"
    override val bridgePairTitle = "Consentire questa estensione del browser?"
    override val bridgePairText =
        "Un'estensione del browser chiede di inviare link a FetchJet. Consentilo solo se hai appena installato o reimpostato l'estensione FetchJet."
    override val bridgePairOldText =
        "Una vecchia estensione FetchJet vuole inviare link. Consentilo se è tua; aggiornando l'estensione questa domanda non comparirà più."
    override val bridgePairAllow = "Consenti"
    override val bridgePairBlock = "Blocca"
    override val extConnectionsGroup = "Connessioni del browser"
    override val extConnectionsFooter =
        "Estensioni del browser a cui hai consentito o impedito di inviare link a FetchJet."
    override val extAllowedLabel = "Consentita"
    override val extBlockedLabel = "Bloccata"
    override val extRevoke = "Revoca"
    override val extUnblock = "Sblocca"
    override val homeBrowserFallbackTitle = "Scaricare invece nel browser?"
    override val homeBrowserFallbackText =
        "Hai annullato questo file in FetchJet. Il browser ha fermato il suo download quando " +
                "ha passato il link, quindi continuerà solo se glielo rimandi."
    override val homeDownloadInBrowser = "Scarica nel browser"
    override val homeNoDiscard = "No, scarta"

    override val homeQueueTitle = "Coda"
    override val homeQueueFooter =
        "I download in più aspettano e partono da soli, dal più vecchio, quando uno finisce. " +
                "\"Avvia ora\" su un download in attesa salta la coda."
    override val homeDownloadsAtSameTime = "Download contemporanei"
    override val homeNoLimit = "Nessun limite"
    override fun homeAtATime(count: Int) = "$count alla volta"

    override val homePiecesLabel = "Parti"
    override fun homeSegmentsCount(count: Int) = if (count == 1) "1 segmento" else "$count segmenti"
    override fun homePiecesCount(count: Int) = if (count == 1) "1 parte" else "$count parti"
    override fun homeActiveCount(count: Int) = if (count == 1) "1 attivo" else "$count attivi"
    override fun homeWaitingCount(count: Int) = "$count in attesa"
    override fun homeDoneCount(count: Int) = if (count == 1) "1 completato" else "$count completati"
    override fun homeSegmentsProgress(finished: Int, total: Int, downloading: String?) =
        "Segmenti $finished/$total" + (downloading?.let { " · download di $it" } ?: "")

    override fun homeRetryingNote(note: String?) = "$note · nuovo tentativo"
    override fun clipStartAfterEnd(start: String, end: String) =
        "L'inizio ($start) deve precedere la fine ($end)"

    override fun clipEndExceedsVideo(end: String, duration: String) =
        "La fine ($end) supera la durata del video ($duration)"

    override fun clipStartExceedsVideo(duration: String) =
        "L'inizio supera la durata del video ($duration)"

    override val setStartTimeTitle = "Imposta inizio (da)"
    override val setEndTimeTitle = "Imposta fine (a)"
    override val addDialogTitle = "Nuovo download"
    override val addDialogSubtitle = "Inserisci un link per iniziare il download"
    override val addLinkLabel = "Link"
    override val addImportTxt = "Importa .txt"
    override val openTorrentFile = "Apri file .torrent"
    override val urlPlaceholder = "Incolla link o magnet"
    override val addPaste = "Incolla"
    override val addDownloadEngine = "Motore di download"
    override val engineDirect = "Diretto"
    override val engineMedia = "Media"
    override val engineTorrent = "Torrent"
    override val addFileNameLabel = "Nome file"
    override val findingName = "Ricerca del nome…"
    override val saveDestination = "Cartella di destinazione"
    override val quickFolderDownloads = "Download"
    override val quickFolderDocuments = "Documenti"
    override val quickFolderMedia = "Media"
    override val quickFolderDesktop = "Scrivania"
    override val addRetryInspect = "Riprova analisi"
    override val addStarting = "Avvio..."
    override val addInspecting = "Analisi..."
    override val addInspectionFailed = "Analisi non riuscita"
    override val addEnterUrl = "Inserisci URL"
    override fun scheduleLinks(n: Int) = if (n == 1) "Pianifica 1 link" else "Pianifica $n link"
    override fun downloadLinks(n: Int) = if (n == 1) "Scarica 1 link" else "Scarica $n link"
    override val inspectVideoFirst = "Prima analizza il video"
    override val inspectTorrentFirst = "Prima analizza il torrent"
    override val invalidTimeRange = "Intervallo non valido"
    override fun scheduleVideos(n: Int) = "Pianifica $n video"
    override fun downloadVideos(n: Int) = "Scarica $n video"
    override val addScheduleDownload = "Pianifica download"
    override val addStartDownload = "Avvia download"
    override val availableAudioFormats = "Formati audio disponibili"
    override val availableQualities = "Qualità disponibili"
    override val subtitlesTitle = "Sottotitoli"
    override val subtitlesAuto = "auto"
    override val subtitlesEmbedInVideo = "Incorpora nel video"
    override val subtitlesSeparateFile = "File separato"
    override val addSelectAll = "Seleziona tutto"
    override val addClearSelection = "Cancella"
    override fun selectedOfAvailable(selected: Int, total: Int) =
        "Selezionati: $selected di $total disponibili"

    override fun qualityFormatLine(format: String, details: String) = "Formato: $format$details"
    override val inspectingVideoQualities = "Analisi delle qualità video..."
    override fun readingCourseLectures(done: Int, total: Int) =
        "Lettura delle lezioni del corso: ${done} / ${total}"

    override val readingCourseLecturesHint =
        "L'estensione del browser le legge nella scheda del corso: lasciala aperta"
    override val inspectingVideoQualitiesHint =
        "Ricerca delle risoluzioni video e delle tracce audio disponibili"
    override val videoInspectionFailed = "Analisi del video non riuscita"
    override val addRetryInspection = "Riprova analisi"
    override val mediaEngineStarting = "Avvio del motore media..."
    override val mediaEngineNotReady = "Il motore media non è pronto"
    override val mediaEngineRequiresYtDlp =
        "Per estrarre i video serve yt-dlp, installato o integrato."
    override val preparingEngine = "Preparazione del motore..."
    override val startMediaEngine = "Scarica / avvia il motore media"
    override val inspectAudioFormats = "Analizza e scegli i formati audio"
    override val inspectVideoQuality = "Analizza e scegli la qualità video"
    override fun clipSectionTitle(isAudio: Boolean) =
        if (isAudio) "Ritaglia una parte dell'audio (facoltativo)" else "Ritaglia una parte del video (facoltativo)"

    override val clipSectionHint = "Scarica solo un intervallo di tempo"
    override fun mediaDurationFullRange(isAudio: Boolean, clock: String, friendly: String) =
        "Durata ${if (isAudio) "audio" else "video"}: $clock ($friendly) - intera durata selezionata"

    override val clipStartTimeFrom = "Inizio (da)"
    override val clipEndTimeTo = "Fine (a)"
    override fun fullMediaChip(isAudio: Boolean, duration: String) =
        if (isAudio) "Audio completo ($duration)" else "Video completo ($duration)"

    override val clipFirst5Min = "Primi 5 min"
    override val clipFirst15Min = "Primi 15 min"
    override val clipFirst30Min = "Primi 30 min"
    override val clipFirst1Hour = "Prima ora"
    override val clipModeTitle = "Come scaricare la clip"
    override val clipOnlyTitle = "Solo la clip"
    override val clipOnlyLine1 = "Scarica solo la parte scelta"
    override val clipOnlyLine2 = "Più lento: YouTube limita la velocità"
    override val clipOnlyLine3 = "La pausa fa ripartire la clip da capo"
    override fun fullThenCutTitle(isAudio: Boolean) =
        if (isAudio) "Audio completo, poi taglia" else "Video completo, poi taglia"

    override fun fullThenCutLine1(isAudio: Boolean, sizeText: String) =
        if (isAudio) "Scarica tutto l'audio$sizeText" else "Scarica tutto il video$sizeText"

    override val fullThenCutLine2 = "Più veloce: velocità piena"
    override val fullThenCutLine3 = "Poi tiene solo la tua parte"
    override val fullThenCutLine4 = "Pausa e ripresa funzionano"
    override val inspectingTorrent = "Analisi dei metadati del torrent..."
    override val inspectingTorrentHint = "Recupero dell'elenco file e dei peer"
    override val torrentInspectionFailed = "Analisi del torrent non riuscita"
    override val inspectTorrentFiles = "Analizza i file del torrent"
    override val disableTorrentUpload = "Disattiva l'upload del torrent (solo download)"
    override val torrentFiles = "File del torrent"
    override val addDeselectAll = "Deseleziona tutto"
    override val torrentPriorityHigh = "Alta"
    override val torrentPriorityLow = "Bassa"
    override val torrentPriorityNormal = "Normale"
    override fun selectedOfLinks(selected: Int, total: Int) = "$selected di $total link"
    override val readingTorrentFiles = "Lettura dei file del torrent…"
    override fun couldNotReadTorrentFiles(error: String) =
        "Impossibile leggere i file ($error); verranno scaricati tutti."

    override val linkTagVideo = "VIDEO"
    override val linkTagAudio = "AUDIO"
    override val linkTagTorrent = "TORRENT"
    override val linkTagFile = "FILE"
    override val linkListMagnet = "Link magnet"
    override fun linkListYouTubeVideo(id: String) = "Video YouTube $id"
    override fun playlistSummary(videos: Int, selected: Int) =
        "Playlist · $videos video · " + if (selected == 1) "1 selezionato" else "$selected selezionati"

    override val playlistFolder = "Salva nella cartella"
    override val playlistFolderHint =
        "Ogni file va in questa cartella. Svuotala per salvarli direttamente."
    override val qualityForAllVideos = "Qualità per tutti i video"
    override val qualityForAllVideosHint =
        "Se un video non ha questa qualità, viene presa la più vicina inferiore."
    override val downloadOnlyThisVideo = "Scarica solo questo video"
    override val whenToStart = "Quando iniziare"
    override val scheduleNow = "Ora"
    override val scheduleIn30Min = "Tra 30 min"
    override val scheduleIn1Hour = "Tra 1 ora"
    override val scheduleAtTime = "A un orario…"
    override val scheduleDayLabel = "Giorno"
    override val scheduleToday = "Oggi"
    override val scheduleTomorrow = "Domani"
    override val scheduleIn2Days = "Tra 2 giorni"
    override val scheduleIn3Days = "Tra 3 giorni"
    override val scheduleInAWeek = "Tra una settimana"
    override val scheduleTimePassed =
        "Quell'orario di oggi è già passato. Scegli un orario più tardi o un altro giorno."
    override val scheduleHourLabel = "Ora"
    override val scheduleMinuteLabel = "Minuto"
    override val scheduleSet = "Imposta"
    override val scheduleSetTime = "Imposta orario"
    override fun scheduleStartsAt(time: String) = "Inizia $time (FetchJet deve essere aperto)"
    override val addConnectionsTitle = "Connessioni"
    override val connectionsAuto = "Auto"
    override fun connectionsAutoWithDefault(n: Int) = "Auto ($n)"
    override fun pickerExceedsVideo(duration: String) = "Supera la durata del video ($duration)"
    override fun pickerMustBeAfterStart(start: String) = "Deve essere dopo l'inizio ($start)"
    override val pickerStartZero = "Inizio (00:00)"
    override val pickerHours = "Ore"
    override val pickerMinutes = "Minuti"
    override val pickerSeconds = "Secondi"
    override fun pickerSelectedTime(time: String, summary: String) =
        "Orario scelto: $time ($summary)"

    override fun pickerEndOfVideo(time: String) = "Fine del video ($time)"
    override fun pickerMin(time: String) = "Min ($time)"
    override fun pickerApply(time: String) = "Applica ($time)"
    override val pickerInvalidTime = "Orario non valido"
    override val dirPickerSelectFolder = "Scegli cartella"
    override val dirPickerUp = "Su"
    override fun dirPickerSubfolders(n: Int) = "Sottocartelle ($n)"
    override val dirPickerHiddenOn = "Nascosti: sì"
    override val dirPickerHiddenOff = "Nascosti: no"
    override val dirPickerNewFolder = "Nuova cartella"
    override val dirPickerFolderName = "Nome cartella…"
    override val dirPickerCreateFailed = "Impossibile creare la cartella"
    override val dirPickerCreate = "Crea"
    override val dirPickerNoSubfolders =
        "Nessuna sottocartella qui.\nI file verranno salvati direttamente in questa cartella."
    override val dirPickerHiddenTag = "(nascosta)"
    override val speedLimitUnlimited = "Illimitata (max)"
    override val speedPresetsTitle = "Preimpostazioni di velocità"
    override val speedPresetsFooter =
        "Imposta una velocità per Veloce, Media e Bassa. Scegline una rapidamente dall’intestazione; la tartaruga è sempre 512 KB/s."
    override val speedPresetHigh = "Alta"
    override val speedPresetFast = "Veloce"
    override val speedPresetMedium = "Media"
    override val speedPresetLow = "Bassa"
    override val speedScheduleTitle = "Pianificazione velocità"
    override val speedScheduleFooter =
        "Limita la velocità in orari prestabiliti; illimitata al di fuori."
    override val speedScheduleAdd = "Aggiungi una fascia oraria"
    override val speedScheduleFrom = "Dalle"
    override val speedScheduleTo = "Alle"
    override val speedScheduleActive = "Limite pianificato attivo"
    override val speedScheduleEmpty = "Ancora nessuna fascia oraria — aggiungine una."
    override val priorityLabel = "Priorità"
    override val priorityHigh = "Alta"
    override val priorityNormal = "Normale"
    override val priorityLow = "Bassa"
    override val checksumLabel = "Checksum (facoltativo)"
    override val checksumHint = "sha256 o md5"
    override val checksumVerified = "Checksum verificato"
    override val checksumMismatch = "Checksum non corrispondente"
    override val checksumChecking = "Verifica del checksum…"
    override val speedLimitNoLimit = "Nessun limite (velocità max)"
    override val speedLimitCustom = "Personalizzato..."
    override val speedLimitValue = "Valore"
    override val scheduleAm = "AM"
    override val schedulePm = "PM"
    override val detailsChipMedia = "Media"
    override val detailsChipTorrent = "Torrent"
    override val detailsChipFile = "File"
    override val detailsChipResumable = "Riprendibile"
    override val detailsChipNoResume = "Non riprendibile"
    override fun detailsClipChip(start: String, end: String?, cutAfterDownload: Boolean) =
        "Clip $start–${end ?: "Fine"}" + if (cutAfterDownload) " · taglio dopo il download" else ""

    override fun detailsTorrentFiles(selected: Int, total: Int) =
        "File del torrent ($selected selezionati / $total)"

    override val detailsPriorityHigh = "Alta"
    override val detailsPriorityLow = "Bassa"
    override val detailsPriorityNormal = "Normale"
    override val detailsStatusCompleted = "Completato"
    override val detailsStatusPaused = "In pausa"
    override val detailsStatusFailed = "Non riuscito"
    override val detailsStatusWaiting = "In attesa"
    override val detailsStatusQueued = "In coda"
    override val detailsStatusNotStarted = "Non avviato"
    override val detailsUnknownSize = "Sconosciuta"
    override val detailsMergingAudioVideo = "Unione di audio e video..."
    override fun detailsTorrentSpeedUploadDisabled(download: String) =
        "↓ $download (upload disattivato)"

    override fun detailsTorrentSpeed(download: String, upload: String) = "↓ $download | ↑ $upload"
    override val detailsConnecting = "Connessione..."
    override fun detailsStartsAt(time: String) = "Inizia $time"
    override fun detailsWaitingInQueuePosition(position: Int) = "In coda (n. $position)"
    override val detailsWaitingInQueue = "In coda"
    override val detailsDownloaded = "Scaricato"
    override val detailsSpeed = "Velocità"
    override val detailsState = "Stato"
    override val detailsTimeLeft = "Tempo rimanente"
    override val detailsAdded = "Aggiunto"
    override val detailsSavedTo = "Salvato in"
    override val detailsSource = "Origine"
    override val detailsPage = "Pagina"
    override val detailsOpenFile = "Apri file"
    override val detailsOpenFolder = "Apri cartella"
    override val detailsStartNow = "Avvia ora"
    override val detailsRemoveFromList = "Rimuovi dall'elenco"
    override val detailsCancelDownload = "Annulla download"
    override val detailsHeaderDownload = "Download"
    override val detailsHeaderDetails = "DETTAGLI"
    override fun detailsAddedToday(clock: String) = "Oggi $clock"
    override fun detailsAddedYesterday(clock: String) = "Ieri $clock"

    override fun removeDialogTitle(count: Int) =
        if (count > 1) "Rimuovi $count download" else "Rimuovi download"

    override fun removeDialogMessage(count: Int) =
        if (count > 1) "Vuoi davvero rimuovere questi $count download?"
        else "Vuoi davvero rimuovere questo download?"

    override val removeDialogAlsoDeleteFiles = "Elimina anche i file scaricati dal disco"

    override val duplicateDialogTitle = "Download già presente"
    override val duplicateDialogSubtitle = "Questo elemento è già nella coda dei download"
    override fun duplicateDialogStatus(status: String) = "Stato: $status"
    override val duplicateDialogQuestion =
        "Vuoi riprendere il download esistente o ricominciare da capo?"
    override val duplicateDialogResumeExisting = "Riprendi esistente"
    override val duplicateDialogRedownload = "Scarica di nuovo (da capo)"

    override val overwriteDialogTitle = "Il file esiste già"
    override val overwriteDialogSubtitle = "Nella destinazione esiste già un file con questo nome"
    override val overwriteDialogFileNameLabel = "Nome file: "
    override val overwriteDialogFolderLabel = "Cartella: "
    override val overwriteDialogNewCopyLabel = "Nuova copia: "
    override val overwriteDialogQuestion =
        "Vuoi salvare questo download come nuova copia o sovrascrivere il file esistente?"

    override fun overwriteDialogSaveAsCopy(name: String) = "Salva come copia ($name)"
    override val overwriteDialogOverwriteExisting = "Sovrascrivi il file esistente"

    override val desktopWindowTagline = "Download manager veloce multi-motore"
    override val desktopTrayIdleTooltip = "FetchJet - In esecuzione in background"
    override val desktopTrayDownloadsHeader = "Download di FetchJet:"
    override fun desktopTrayMore(count: Int) = "+$count altri"
    override val desktopTrayShowApp = "Mostra app"
    override val desktopTrayCloseApp = "Chiudi app"
    override val sectionGeneral = "Generali"
    override val sectionGeneralBlurb = "Aspetto dell'app e avvio"
    override val sectionDownloads = "Download"
    override val sectionDownloadsBlurb = "Dove vanno i file e quanti ne partono insieme"
    override val sectionBrowser = "Estensione browser"
    override val sectionBrowserBlurb = "Invia link e video dal browser a FetchJet"
    override val sectionSpeed = "Limiti di velocità"
    override val sectionSpeedBlurb = "Limita la banda usata da FetchJet"
    override val sectionMedia = "Strumenti media"
    override val sectionMediaBlurb = "yt-dlp e FFmpeg per i siti video"
    override val sectionTorrent = "BitTorrent"
    override val sectionTorrentBlurb = "Peer, upload e motore torrent"
    override val sectionUpdates = "Aggiornamenti"
    override val sectionUpdatesBlurb = "La tua versione e le nuove release"
    override val summaryNoQueueLimit = "nessun limite"
    override fun summaryAtATime(n: Int) = "$n alla volta"
    override val summaryNoSpeedLimit = "Nessun limite"
    override val summarySetupNeeded = "Da configurare"
    override val summaryUploadingOff = "Upload disattivato"
    override val summaryUploadingOn = "Upload attivo"
    override fun summaryVersionAvailable(version: String) = "$version disponibile"
    override fun summaryVersion(version: String) = "Versione $version"
    override val changesSavedAutomatically = "Le modifiche vengono salvate automaticamente."
    override val settingsAllSettings = "Tutte le impostazioni"

    override val clipboardGroup = "Appunti"
    override val clipboardFooter =
        "Copia ovunque un link a un file (zip, mp4, pdf, apk…) o un link magnet e FetchJet ti chiede se scaricarlo."
    override val watchClipboard = "Controlla gli appunti"
    override val watchClipboardOn =
        "Attivo · i link a file copiati vengono proposti per il download"
    override val settingOff = "Disattivato"
    override val alsoVideoPages = "Anche pagine video"
    override val alsoVideoPagesHint =
        "Vengono proposti anche i link di YouTube, Facebook, TikTok, Vimeo… (non solo file)"
    override val diagnosticsGroup = "Diagnostica"
    override fun logSavedTo(path: String) =
        "Salvato: $path — invia questo file quando segnali un problema."

    override val diagnosticsFooter =
        "Salva il log dell'app (cosa hanno fatto yt-dlp e i motori) nella cartella Download."
    override val saveLogToDownloads = "Salva il log in Download"
    override val logCouldNotSave = "impossibile salvare"
    override val appearanceGroup = "Aspetto"
    override val themeTitle = "Tema"
    override val themeHint = "Aspetto chiaro o scuro per tutte le schermate"
    override val settingsThemeDark = "Scuro"
    override val settingsThemeLight = "Chiaro"
    override val speedShownIn = "Velocità mostrata in"
    override val speedShownInHint =
        "Byte (MB/s) come le dimensioni dei file, o bit (Mbps) come la tua connessione"

    override val saveLocationGroup = "Percorso di salvataggio"
    override val saveLocationFooter =
        "I nuovi download finiscono qui, a meno che tu non scelga un'altra cartella quando li aggiungi."
    override val settingsFolderPath = "Percorso cartella"
    override val settingsChooseFolder = "Scegli cartella…"
    override val settingsUseDownloads = "Usa Download"
    override val settingsConnectionsGroup = "Connessioni"
    override val connectionsFooter =
        "In quante parti viene diviso un download diretto. Ogni parte è una connessione; quando una finisce prende metà della parte rimanente più grande, così nessuna resta ferma. Auto sceglie in base alla dimensione del file (4–8). Alcuni server limitano le connessioni per client."
    override val settingsConnectionsPerDownload = "Connessioni per download"
    override val settingsConnectionsAuto = "Auto"
    override val organizeGroup = "Organizza"
    override val organizeFooter =
        "Le sottocartelle vengono create nel percorso di salvataggio: Video, Music, Archives, Documents, Programs, Images, Torrents."
    override val sortIntoFolders = "Ordina in cartelle per tipo"
    override val sortIntoFoldersOn = "Attivo · es. Downloads/Video/lezione.mp4"
    override val sortIntoFoldersOff = "Disattivato · tutto va direttamente nella cartella"

    override val speedLimitsFooter = "Applicato subito e condiviso tra i download in corso."
    override val speedLimitsFooterIos =
        "Applicato subito e condiviso tra i download in corso. Su iOS, i download HTTP già in corso in background mantengono la loro velocità; il limite vale per quelli avviati dopo."
    override val speedLimitDownload = "Download"
    override val speedLimitUploadTorrents = "Upload (torrent)"

    override val torrentSharingGroup = "Condivisione"
    override val downloadOnlyNoUpload = "Solo download (nessun upload)"
    override val downloadOnlyNoUploadHint =
        "Risparmia banda in upload e traffico dati. Alcuni tracker scaricano più lentamente per i peer che non condividono."
    override val torrentEngineGroup = "Motore"
    override val settingsReady = "Pronto"
    override val settingsNotReady = "Non pronto"

    override val extInstallGroup = "Installa"
    override val extensionInstallFooter =
        "Con l'estensione, su video e link compare il pulsante \"Scarica con FetchJet\" e il browser può passare i suoi download a FetchJet. Comunica solo con questa app sul tuo dispositivo."
    override val edgeAddedHint =
        "Aggiunta · riavvia Edge e fai clic su \"Attiva estensione\" quando lo chiede"
    override val edgeFromStoreHint =
        "Da Componenti aggiuntivi di Microsoft Edge · si aggiorna da sola"
    override val openInEdgeStore = "Apri nello store di Edge"
    override val edgeAddedNote = "Fatto: riavvia Edge, ti chiederà di attivare FetchJet."
    override val extAddToEdge = "Aggiungi a Edge"
    override val firefoxFromStoreHint = "Da Componenti aggiuntivi di Firefox · si aggiorna da sola"
    override val extAddToFirefox = "Aggiungi a Firefox"
    override val chromeZipHint =
        "Non è nel Chrome Web Store: installa il .zip in modalità sviluppatore (istruzioni nella pagina). Non si aggiorna da sola."
    override val getTheZip = "Scarica il .zip"
    override val firefoxAndroidHint =
        "Aprilo in Firefox su questo telefono. I link che invii arrivano direttamente a questa app."
    override val keepReadyForBrowser = "Tieni pronto per il browser"
    override val keepReadyOn =
        "Le qualità video appaiono direttamente in Firefox. Finché è attivo resta una notifica silenziosa."
    override val keepReadyOff =
        "Disattivato: Firefox apre FetchJet e scegli la qualità qui. Attivo: le qualità appaiono in Firefox (con una notifica fissa)."
    override val popUpOverBrowser = "Apri sopra il browser"
    override val popUpOverBrowserOn =
        "Attivo · FetchJet si apre da solo quando Firefox invia un link"
    override val popUpOverBrowserOff =
        "Disattivato · compare invece una notifica; tocca per consentire \"mostra sopra altre app\""

    override val mediaToolsGroup = "Strumenti"
    override val ytDlpHint = "Trova video e audio su YouTube e altri siti"
    override val settingsMissing = "Mancante"
    override val ffmpegHint = "Unisce video + audio (1080p e oltre), taglia clip, crea MP3"
    override val ffmpegMissing = "Mancante · fino a 720p"
    override fun jsRuntimeTitle(name: String) = "Runtime JS · $name"
    override val jsRuntimeHint =
        "Risolve i controlli di YouTube così compaiono tutte le qualità, più in fretta"

    override fun missingPress(button: String) = "Mancante · premi $button"
    override val mediaToolsFolder = "Cartella strumenti"
    override val mediaUpdatingTools = "Aggiornamento…"
    override val mediaDownloadingTools = "Download degli strumenti…"
    override fun mediaDownloadingTool(name: String) = "Download di $name…"
    override fun mediaDownloadingToolPercent(name: String, percent: Int) =
        "Download di $name… $percent%"

    override val mediaUpdateTools = "Aggiorna strumenti"
    override val mediaDownloadTheTools = "Scarica gli strumenti"

    override val updateNewVersionAvailable = "Nuova versione disponibile"
    override fun updateVersionLine(version: String, current: String) =
        "FetchJet $version  ·  hai la $current"

    override val updateWhatsNew = "Novità"
    override val updateNoReleaseNotes = "Nessuna nota di rilascio."
    override fun downloadingUpdate(percent: Int) = "Download dell'aggiornamento… $percent%"
    override val updateDownloadedAndroid =
        "Aggiornamento scaricato. Android ti chiede di confermare l'installazione; i tuoi download restano nell'elenco."
    override val updateDownloadedDesktop =
        "Aggiornamento scaricato. FetchJet si chiuderà, lo installerà e si riaprirà. I download in corso vengono messi in pausa e potrai riprenderli."
    override val storeInstallsItselfOpenNow =
        "Microsoft Store lo installa da solo. Apri lo Store per aggiornare ora."
    override val updateInstall = "Installa"
    override val updateRestartAndUpdate = "Riavvia e aggiorna"
    override val updateDownloading = "Download…"
    override val updateDownloadAndInstall = "Scarica e installa"
    override val updateOpenMicrosoftStore = "Apri Microsoft Store"
    override fun updateDownloadWithSize(size: String) = "Scarica $size"
    override val updateOpenReleasePage = "Apri la pagina della release"
    override val updateSkipThisVersion = "Salta questa versione"
    override val updateLater = "Più tardi"
    override fun versionIsAvailable(version: String) = "È disponibile la versione $version"
    override val youHaveLatest = "Hai la versione installata qui"
    override val updateSeeWhatsNew = "Vedi le novità"
    override val updateChecking = "Verifica…"
    override val updateCheckNow = "Verifica ora"
    override val checkUpdatesAutomatically = "Cerca aggiornamenti automaticamente"
    override val checkUpdatesAutomaticallyHint =
        "Cerca una nuova versione all'avvio di FetchJet (al massimo due volte al giorno)."
    override val downloadUpdatesAutomatically = "Scarica aggiornamenti automaticamente"
    override val downloadUpdatesAutomaticallyHintAndroid =
        "Scarica la nuova versione in background; devi solo confermare l'installazione."
    override val downloadUpdatesAutomaticallyHint =
        "Scarica la nuova versione in background; devi solo confermare il riavvio."
    override val installingUpdates = "Installazione aggiornamenti"
    override val installingUpdatesStore =
        "Microsoft Store installa le nuove versioni da solo; FetchJet ti avvisa quando ne esce una."
    override val installingUpdatesManual =
        "Su questo dispositivo FetchJet ti avvisa di una nuova versione e ti dà il link; la installi tu."
    override val updateReleaseNotes = "Note di rilascio"
    override val updateReleaseNotesHint = "Tutte le versioni e cosa è cambiato"
    override val installHintReleasePage =
        "Apri la pagina della release per scaricare la nuova versione."
    override val installHintApk =
        "Scarica l'APK e aprilo per installare l'aggiornamento su questa versione."
    override val installHintIpa =
        "Scarica il file .ipa e installalo con AltStore / Sideloadly, come hai installato FetchJet."
    override val installHintDmg =
        "Scarica il file .dmg, aprilo e trascina FetchJet in Applicazioni (sostituisci quello vecchio)."
    override val installHintDeb =
        "Scarica il file .deb e installalo (doppio clic, o sudo apt install ./file.deb)."
    override val installHintOther = "Scarica la nuova versione e installala sopra questa."

    override val startupGroup = "Avvio"
    override val startupStoreFooter =
        "La versione dello Store viene avviata da Windows: attiva FetchJet in Impostazioni > App > Avvio."
    override val startupStartAtLogin = "Avvia FetchJet all'accesso"
    override val startupManagedByWindows = "Gestito da Windows"
    override val startupOpenWindowsSettings = "Apri impostazioni di Windows"
    override val startupStartHidden = "Avvia nascosto nell'area di notifica"
    override val startupStartHiddenHint =
        "Se avviato all'accesso, resta nell'area di notifica invece di aprire la finestra."
    override val startupStartAtLoginHint =
        "I download pianificati e in coda continuano e l'estensione del browser trova sempre l'app."

    override fun startupCouldNotChange(message: String) =
        "Impossibile modificare l'impostazione: $message"

    override val whenDoneGroup = "Al termine dei download"
    override val whenDoneHint = "Solo per questa sessione. Prima avrai 60 secondi per annullare."
    override val whenDoneNothing = "Non fare nulla"
    override val whenDoneExit = "Esci da FetchJet"
    override val whenDoneSleep = "Sospendi"
    override val whenDoneShutDown = "Spegni"
    override val whenDoneTitle = "Tutti i download sono terminati"
    override fun whenDoneExitIn(seconds: Int) = "FetchJet si chiuderà tra ${seconds} s."
    override fun whenDoneSleepIn(seconds: Int) =
        "Il computer andrà in sospensione tra ${seconds} s."

    override fun whenDoneShutDownIn(seconds: Int) = "Il computer si spegnerà tra ${seconds} s."
    override val whenDoneNow = "Ora"
    override fun whenDoneBanner(action: String) = "Al termine dei download: ${action}"
    override fun whenDoneFailed(message: String) = "Impossibile: ${message}"
    override val sectionNetwork = "Rete"
    override val sectionNetworkBlurb = "Proxy e accessi ai siti"
    override val netProxyGroup = "Proxy"
    override val netProxyOff = "Disattivato"
    override val netProxyHost = "Server"
    override val netProxyPort = "Porta"
    override val netProxyUser = "Nome utente (facoltativo)"
    override val netProxyPassword = "Password"
    override val netUser = "Nome utente"
    override val netSave = "Salva"
    override val netSaved = "Salvato"
    override val netProxyHint =
        "Usato da download, video (yt-dlp) e torrent. Vale per i download avviati dopo il salvataggio."
    override val netProxyHintIos =
        "Usato da download e video. I torrent su iOS si collegano direttamente. Vale per i download avviati dopo il salvataggio."
    override val netProxyBadPort = "Inserisci un server e una porta da 1 a 65535"
    override val netLoginsGroup = "Accessi ai siti"
    override val netLoginsHint =
        "Per i download che chiedono nome utente e password (HTTP basic auth). Un sito include anche i suoi sottodomini."
    override val netLoginSite = "Sito (example.com)"
    override val netLoginAdd = "Aggiungi"
    override val netLoginsEmpty = "Nessun accesso salvato"
    override val netRemove = "Rimuovi"
    override fun netLoginsCount(n: Int) = "${n} accessi"
    override val sysTabAllDownloads = "Tutti i download"
    override val sysTabActive = "Attivi"
    override val sysTabCompleted = "Completati"
    override val sysTabMediaExtractor = "Estrattore media"
    override val sysTabSettings = "Impostazioni"
    override val sysSortNewestFirst = "Più recenti"
    override val sysSortOldestFirst = "Meno recenti"
    override val sysSortLargestFirst = "Più grandi"
    override val sysSortSmallestFirst = "Più piccoli"
    override val sysSortNameAz = "Nome A–Z"
    override val sysEnterValidUrlOrMagnet = "Inserisci un URL o un link magnet valido"
    override val sysCannotStartVideoInspectionFailed =
        "Impossibile avviare il download: analisi del video non riuscita."
    override val sysCannotStartTorrentInspectionFailed =
        "Impossibile avviare il download: analisi del torrent non riuscita o nessun file selezionato."
    override val sysClipEndExceedsDuration = "La fine della clip supera la durata del video."
    override val sysClipStartAfterEnd = "L'inizio della clip deve precedere la fine."
    override fun sysStartedDownload(name: String) = "Download avviato: $name"
    override val sysFailedToQueueDownload = "Impossibile mettere in coda il download"
    override fun sysQualitiesNotAdded(count: Int) =
        if (count == 1) "Impossibile aggiungere 1 qualità." else "Impossibile aggiungere $count qualità."

    override fun sysAddedQualityDownloads(count: Int, title: String) =
        if (count == 1) "Aggiunto 1 download di \"$title\"" else "Aggiunti $count download di \"$title\""

    override val sysSelectAtLeastOnePlaylistVideo = "Seleziona almeno un video della playlist."
    override fun sysVideosNotAdded(count: Int) =
        if (count == 1) "Impossibile aggiungere 1 video." else "Impossibile aggiungere $count video."

    override fun sysAddedPlaylistVideos(count: Int, playlistTitle: String) =
        if (count == 1) "Aggiunto 1 video da \"$playlistTitle\"" else "Aggiunti $count video da \"$playlistTitle\""

    override val sysSelectAtLeastOneLink = "Seleziona almeno un link."
    override fun sysAddedLinkDownloads(added: Int, skipped: Int) =
        (if (added == 1) "Aggiunto 1 download" else "Aggiunti $added download") +
                (if (skipped > 0) " ($skipped già nell'elenco o non riusciti)" else "")

    override val sysCouldNotReadTorrent = "Impossibile leggere il torrent"
    override fun sysCouldNotStartDownload(reason: String) =
        "Impossibile avviare il download: $reason"

    override val sysUnknownError = "errore sconosciuto"
    override fun sysQueuedFromBrowser(added: Int, skipped: Int) =
        (if (added == 1) "1 download messo in coda dal browser" else "$added download messi in coda dal browser") +
                (if (skipped > 0) " ($skipped saltati)" else "")

    override val sysEnterTorrentOrMagnetFirst = "Inserisci prima un torrent o un link magnet"
    override val sysFailedToParseTorrentMetadata = "Impossibile leggere i metadati del torrent"
    override val sysEnterMediaUrlFirst = "Inserisci prima l'URL di un video"
    override fun sysUnableToFetchFormatsWithReason(reason: String) =
        "Impossibile ottenere i formati video: $reason"

    override val sysUnableToFetchFormats =
        "Impossibile ottenere i formati video. Controlla l'URL o la connessione a Internet."
    override val sysFailedToInspectMediaQualities = "Impossibile analizzare le qualità del video"
    override fun sysTorrentEngineError(message: String) = "Errore del motore BitTorrent: $message"
    override fun sysTorrentEngineInstallFailed(message: String) =
        "Installazione del motore BitTorrent non riuscita: $message"

    override val sysMediaToolsDownloaded = "Strumenti media scaricati!"
    override val sysMediaToolsDownloadFailed = "Impossibile scaricare gli strumenti media"
    override val sysMediaToolsUpdated = "Strumenti media aggiornati"
    override fun sysSavedLinks(count: Int, path: String) =
        if (count == 1) "1 link salvato in $path" else "$count link salvati in $path"

    override fun sysCouldNotSaveLinks(reason: String) = "Impossibile salvare i link: $reason"
    override val sysErrorFallback = "errore"
    override val sysDownloadFailed = "Download non riuscito"
    override val sysDownloadFailedNetwork = "Download non riuscito per un errore di rete"
    override val sysCouldNotStart = "Impossibile avviare"
    override val sysUnknownErrorCapital = "Errore sconosciuto"
    override fun sysCouldNotSaveFileTo(dir: String) = "Impossibile salvare il file in $dir"
    override val sysPlaylistFallbackTitle = "Playlist"
    override val sysPlaylistQualityBest = "Migliore"
    override val sysPlaylistQualityAudioOnly = "Solo audio"
    override val sysToolsReadyYtDlpFfmpeg =
        "Pronto: yt-dlp e ffmpeg installati (supporto completo 1080p+)"
    override val sysToolsReadyYtDlpNoFfmpeg =
        "Pronto: yt-dlp installato (senza ffmpeg - massimo 720p)"
    override val sysToolsNotInstalled =
        "Non installato. Fai clic su 'Scarica gli strumenti' qui sotto."
    override val sysToolsInstalledBuiltIn = "Installato e pronto (integrato)"
    override fun sysYtDlpVersionReady(version: String) = "yt-dlp $version (pronto)"
    override val sysMediaEngineBridgeNotRegistered = "Bridge del motore media non registrato"
    override val sysWebKitBuiltIn = "WebKit (integrato)"
    override val sysTorrentEngineBuiltIn = "Pronto: motore BitTorrent integrato"
    override fun sysTorrentEngineReadyVersion(version: String) =
        "Pronto: motore BitTorrent (libtorrent $version)"

    override fun sysTorrentEngineFailedToLoad(message: String) =
        "Impossibile caricare il motore BitTorrent: $message"

    override val sysTorrentEngineAvailable =
        "Pronto: motore BitTorrent disponibile (si carica al primo torrent)"
    override val sysTorrentEngineMissing =
        "Motore BitTorrent mancante. Fai clic su 'Scarica gli strumenti' qui sotto."
    override val sysTorrentEngineNotAvailable =
        "Il motore BitTorrent non è disponibile in questa build"

    override fun sysCouldNotStartInstaller(message: String) =
        "Impossibile avviare il programma di installazione: $message"

    override val sysCheckingForUpdates = "Ricerca aggiornamenti…"
    override val sysNoPublishedRelease = "Nessuna release pubblicata."
    override fun sysVersionNoChangesForDevice(version: String, current: String) =
        "La versione $version non ha novità per questo dispositivo; hai già la build più recente ($current)."

    override fun sysVersionAvailable(version: String) = "È disponibile la versione $version."
    override fun sysUpToDate(current: String) = "Sei aggiornato ($current)."
    override fun sysCouldNotCheckForUpdates(reason: String) =
        "Impossibile cercare aggiornamenti: $reason"

    override val sysNetworkError = "errore di rete"
    override val sysUpdateNoInstallerForSystem =
        "Download dell'aggiornamento non riuscito: questa release non ha un installer per questo sistema."

    override fun sysDownloadingAsset(name: String) = "Download di $name…"
    override fun sysVersionReadyToInstall(version: String) =
        "La versione $version è pronta per l'installazione."

    override fun sysUpdateDownloadFailed(message: String) =
        "Download dell'aggiornamento non riuscito: $message"

    override val sysAllowUnknownAppsThenInstall =
        "consenti \"Installa app sconosciute\" per FetchJet, poi tocca di nuovo Installa."
    override val sysDownloadsChannelName = "Download di FetchJet"
    override val sysDownloadsChannelDescription = "Mostra in tempo reale l'avanzamento dei download"
    override val sysNotificationAppTitle = "FetchJet Downloader"
    override val sysAppRunningInBackground = "L'app è in esecuzione in background"
    override val sysNotificationDownloadsFallback = "Download"
    override fun sysFilesDownloading(count: Int) =
        if (count == 1) "1 file in download" else "$count file in download"

    override fun sysPercentActive(percent: Int, count: Int) =
        "$percent% • " + if (count == 1) "1 attivo" else "$count attivi"

    override fun sysActiveCount(count: Int) = if (count == 1) "1 attivo" else "$count attivi"
    override fun sysDownloadCompleteSize(size: String) = "Download completato • $size"
    override fun sysMoreDownloading(count: Int) =
        if (count == 1) "+ 1 altro in download" else "+ altri $count in download"

    override val sysComplete = "Completato"
    override fun sysNotificationDownloadsTitle(speed: String) = "Download di FetchJet ($speed)"
    override fun sysDownloadingPercent(count: Int, percent: Int) = "$count in download • $percent%"
    override val sysDownloadTaskFallback = "Download"
    override val sysBrowserReadyTitle = "FetchJet è pronto per il browser"
    override val sysBrowserReadyText = "Le qualità video appaiono direttamente in Firefox"
    override val sysTurnOff = "Disattiva"
    override val sysBrowserReadyChannelName = "Estensione browser"
    override val sysBrowserReadyChannelDescription =
        "Visibile mentre FetchJet resta pronto per l'estensione del browser"
    override val sysDownloadingFileFallback = "Download del file"
    override val sysUnknownSize = "Sconosciuta"
    override val sysStatusCompleted = "Completato"
    override val sysStatusDownloading = "In download"
    override val sysDownloadCompleteTitle = "Download completato"
    override fun sysFinishedDownloading(size: String) = "Download terminato • $size"
    override val sysSentToFetchJet = "Inviato a FetchJet"
    override val sysLinksChannelName = "Link dal browser"
    override val sysLinksChannelDescription = "Tocca per aprire il link appena ricevuto da FetchJet"
    override val sysLinkReceivedTitle = "Link ricevuto"
    override val sysLinkReceivedText = "Tocca per aprirlo in FetchJet"

    override val stepCompleted = "Completato"
    override val stepSeeding = "In seeding"
    override val stepFetchingMetadata = "Recupero dei metadati"
    override val stepConnectingToPeers = "Connessione ai peer"
    override val stepCheckingFiles = "Verifica dei file"
    override val stepCheckingResumeData = "Verifica dei dati di ripresa"
    override val stepDownloading = "Download in corso"
    override val stepFinished = "Terminato"
    override val stepConnecting = "Connessione"
    override val stepPaused = "In pausa"
    override val stepError = "Errore"
    override val stepResolvingStreams = "Analisi dei flussi"
    override val stepDownloadingParts = "Download delle parti"
    override val stepPreparingDownload = "Preparazione del download"
    override val stepClippingVideo = "Ritaglio del video"
    override val stepDownloadingMedia = "Download dei contenuti"
    override val stepAlreadyDownloaded = "Già scaricato"
    override val stepDownloadingVideo = "Download del video"
    override val stepDownloadingAudio = "Download dell'audio"
    override val stepMergingAudioVideo = "Unione di audio e video"
    override val stepConvertingToMp3 = "Conversione in MP3"
    override val stepCuttingClip = "Taglio della clip"
    override val stepSavingToFolder = "Salvataggio nella cartella"
    override val stepFinalizingVideo = "Finalizzazione del video"
    override val stepConvertingVideo = "Conversione del video"
    override val stepRepackagingVideo = "Reimpacchettamento del video"
    override val stepYtDlpFallback = "Ripiego su yt-dlp"
    override val stepNotPlainStream = "non è un flusso semplice"
    override val stepSingleConnection = "Connessione singola"
    override val stepServerNoRanges = "il server non supporta gli intervalli"
    override val stepAgeRestrictedExpiredCookies =
        "Limitato per età: cookie scaduti in File > FetchJet"
    override val stepAgeRestrictedPlaceCookies =
        "Limitato per età: metti cookies.txt in File > FetchJet"
    override val stepPreparingMediaEngine =
        "Preparazione del motore multimediale integrato (avvio di Python, caricamento di yt-dlp)"
    override val stepMediaEngineNotStarted =
        "Motore multimediale non avviato. Tocca \"Scarica strumenti\"."

    override fun stepNumber(step: Int, total: Int) = "Passo ${step} di ${total}"
    override fun stepSavingAs(ext: String) = "Salvataggio come .${ext}"
    override fun stepJoiningParts(count: Int) =
        "Unione di ${count} ${if (count == 1) "parte" else "parti"} in MP4"

    override fun stepNoDataFor(seconds: Int) = "Nessun dato da ${seconds} s"
    override fun stepPeers(peers: Int, seeds: Int) = "${peers} peer (${seeds} seed)"
    override fun stepDownloadingFrom(host: String) = "Download da ${host}"
    override fun stepMediaEngineNotReady(error: String) = "Motore multimediale non pronto: ${error}"
    override fun stepTorrentEngineFailed(error: String) =
        "Impossibile avviare il motore BitTorrent: ${error}"

    override fun stepReadyWith(details: String) = "Pronto: ${details}"
}
