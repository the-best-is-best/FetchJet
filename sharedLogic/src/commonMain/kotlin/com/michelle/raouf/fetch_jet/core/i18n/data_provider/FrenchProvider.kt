package com.michelle.raouf.fetch_jet.core.i18n.data_provider

object FrenchProvider : AppLocalization {
    override val localeCode = "fr"
    override val languageName = "Français"
    override val ok = "OK"
    override val cancel = "Annuler"
    override val close = "Fermer"
    override val save = "Enregistrer"
    override val delete = "Supprimer"
    override val remove = "Retirer"
    override val open = "Ouvrir"
    override val copy = "Copier"
    override val copied = "Copié"
    override val retry = "Réessayer"
    override val pause = "Pause"
    override val resume = "Reprendre"
    override val apply = "Appliquer"
    override val reset = "Réinitialiser"
    override val yes = "Oui"
    override val no = "Non"
    override val back = "Retour"
    override val done = "Terminé"
    override val selectAll = "Tout sélectionner"
    override val selectNone = "Ne rien sélectionner"
    override val language = "Langue"
    override val languageHint = "La langue de tous les écrans. Système suit votre appareil."
    override val systemLanguage = "Système"
    override val homeSettings = "Paramètres"
    override val homeDownloads = "Téléchargements"

    override val homeTabAll = "Tous"
    override val homeTabActive = "En cours"
    override val homeTabDone = "Terminés"
    override val homeTabTorrent = "Torrent"
    override val homeTabMedia = "Médias"
    override val homeSidebarTypeHeader = "TYPE"
    override val homeSearchDownloadsPlaceholder = "Rechercher"
    override val homeAdd = "Ajouter"
    override val homeAddDownload = "Ajouter un téléchargement"
    override val homePauseAll = "Tout mettre en pause"
    override val homeResumeAll = "Tout reprendre"
    override fun homeSortBy(option: String) = "Tri : $option"
    override val homeLightTheme = "Thème clair"
    override val homeDarkTheme = "Thème sombre"

    override fun homeUpdatingMediaTools(tool: String, percent: Int?) =
        "Mise à jour des outils média · $tool" + (percent?.let { " $it %" } ?: "…")

    override val homeSearchDownloadsByNamePlaceholder = "Rechercher par nom…"
    override fun homeNoDownloadsMatch(query: String) =
        "Aucun téléchargement ne correspond à « $query »"

    override val homeNoTasksInView = "Aucune tâche dans cette vue"
    override val homeTryAnotherName = "Essayez un autre nom"
    override val homeAddDownloadToGetStarted = "Ajoutez un téléchargement pour commencer"
    override fun homeDownloadingWaitingSummary(running: Int, waiting: Int) =
        "$running en cours · $waiting en attente"

    override fun homeQueueStatus(maxAtOnce: Int) =
        "File : " + if (maxAtOnce <= 0) "sans limite" else "$maxAtOnce à la fois"

    override fun homeSpeedLimitStatus(limit: String?) =
        "Limite de vitesse : " + (limit ?: "désactivée")

    override val homeGroupDownloading = "En cours"
    override val homeGroupWaiting = "En attente"
    override val homeGroupPausedFailed = "En pause et échoués"
    override val homeGroupCompleted = "Terminés"
    override fun homeSelectedCount(count: Int) =
        if (count <= 1) "$count sélectionné" else "$count sélectionnés"

    override val homeExport = "Exporter"

    override fun homeStartsAt(time: String) = "Début : $time"
    override val homeWaitingInQueue = "En attente dans la file"
    override fun homeWaitingInQueuePosition(position: Int) =
        "En attente dans la file (n° $position)"

    override val homeConnecting = "Connexion…"
    override val homeStarting = "Démarrage…"
    override fun homeTimeLeft(eta: String) = "$eta restant"
    override fun homePausedState(percent: Int, restartsOnResume: Boolean) =
        "En pause · $percent %" + if (restartsOnResume) " · reprend depuis le début" else ""

    override fun homeFailedState(message: String?) = "Échec : " + (message ?: "erreur")
    override fun homeCompletedState(size: String?) = "Terminé" + (size?.let { " · $it" } ?: "")
    override val homeStartNow = "Démarrer"
    override val homeColumnName = "NOM"
    override val homeColumnSize = "TAILLE"
    override val homeColumnProgress = "PROGRESSION"
    override val homeColumnSpeed = "VITESSE"
    override val homeColumnLeft = "RESTANT"
    override val homeFilterAllDownloads = "Tous les téléchargements"
    override val homeFilterDownloading = "En cours"
    override val homeFilterWaiting = "En attente"
    override val homeFilterPaused = "En pause"
    override val homeFilterCompleted = "Terminés"
    override val homeFilterFailed = "Échoués"
    override val homeFilterMedia = "Vidéo et audio"
    override val homeFilterFiles = "Fichiers"
    override val homeFilterTorrents = "Torrents"

    override val homeCantResumeTitle = "Ce téléchargement ne peut pas être repris"
    override val homeCantResumeClipText =
        "Un extrait découpé pendant le téléchargement ne peut pas reprendre là où il s'est arrêté. " +
                "Si vous le mettez en pause, l'extrait recommencera depuis le début à la reprise."
    override val homeCantResumeServerText =
        "Le serveur ne permet pas la reprise. Si vous le mettez en pause, le téléchargement " +
                "recommencera depuis le début à la reprise."
    override val homePauseAnyway = "Mettre en pause quand même"
    override val homeKeepDownloading = "Continuer"
    override val homeMagnetLink = "Lien magnet"
    override val homeClipboardOfferTitle = "Télécharger le lien copié ?"
    override val homeClipboardOfferHint =
        "Désactivez ceci dans Paramètres > Général > Surveiller le presse-papiers."
    override val homeDownload = "Télécharger"
    override val homeNotNow = "Plus tard"
    override val bridgePairTitle = "Autoriser cette extension de navigateur ?"
    override val bridgePairText =
        "Une extension de navigateur demande à envoyer des liens à FetchJet. Autorisez-la seulement si vous venez d'installer ou de réinitialiser l'extension FetchJet."
    override val bridgePairOldText =
        "Une ancienne extension FetchJet veut envoyer des liens. Autorisez-la si c'est la vôtre ; mettre l'extension à jour évite cette question."
    override val bridgePairAllow = "Autoriser"
    override val bridgePairBlock = "Bloquer"
    override val extConnectionsGroup = "Connexions du navigateur"
    override val extConnectionsFooter =
        "Extensions de navigateur que vous avez autorisées ou bloquées pour envoyer des liens à FetchJet."
    override val extAllowedLabel = "Autorisée"
    override val extBlockedLabel = "Bloquée"
    override val extRevoke = "Révoquer"
    override val extUnblock = "Débloquer"
    override val homeBrowserFallbackTitle = "Télécharger plutôt dans le navigateur ?"
    override val homeBrowserFallbackText =
        "Vous avez annulé ce fichier dans FetchJet. Le navigateur a arrêté son propre téléchargement " +
                "en transmettant le lien, il ne reprendra que si vous le lui renvoyez."
    override val homeDownloadInBrowser = "Télécharger dans le navigateur"
    override val homeNoDiscard = "Non, abandonner"

    override val homeQueueTitle = "File d'attente"
    override val homeQueueFooter =
        "Les téléchargements en trop attendent et démarrent seuls, du plus ancien au plus récent, dès qu'un autre se termine. " +
                "« Démarrer » sur un téléchargement en attente le fait passer devant."
    override val homeDownloadsAtSameTime = "Téléchargements simultanés"
    override val homeNoLimit = "Sans limite"
    override fun homeAtATime(count: Int) = "$count à la fois"

    override val homePiecesLabel = "Morceaux"
    override fun homeSegmentsCount(count: Int) =
        if (count <= 1) "$count segment" else "$count segments"

    override fun homePiecesCount(count: Int) =
        if (count <= 1) "$count morceau" else "$count morceaux"

    override fun homeActiveCount(count: Int) = if (count <= 1) "$count actif" else "$count actifs"
    override fun homeWaitingCount(count: Int) = "$count en attente"
    override fun homeDoneCount(count: Int) = if (count <= 1) "$count terminé" else "$count terminés"
    override fun homeSegmentsProgress(finished: Int, total: Int, downloading: String?) =
        "Segments $finished/$total" + (downloading?.let { " · téléchargement de $it" } ?: "")

    override fun homeRetryingNote(note: String?) = "$note · nouvelle tentative"
    override fun clipStartAfterEnd(start: String, end: String) =
        "Le début ($start) doit être avant la fin ($end)"

    override fun clipEndExceedsVideo(end: String, duration: String) =
        "La fin ($end) dépasse la durée de la vidéo ($duration)"

    override fun clipStartExceedsVideo(duration: String) =
        "Le début dépasse la durée de la vidéo ($duration)"

    override val setStartTimeTitle = "Heure de début (de)"
    override val setEndTimeTitle = "Heure de fin (à)"
    override val addDialogTitle = "Nouveau téléchargement"
    override val addDialogSubtitle = "Saisissez un lien pour lancer le téléchargement"
    override val addLinkLabel = "Lien"
    override val addImportTxt = "Importer un .txt"
    override val openTorrentFile = "Ouvrir un fichier .torrent"
    override val urlPlaceholder = "Collez des liens ou des magnets"
    override val addPaste = "Coller"
    override val addDownloadEngine = "Moteur de téléchargement"
    override val engineDirect = "Direct"
    override val engineMedia = "Média"
    override val engineTorrent = "Torrent"
    override val addFileNameLabel = "Nom du fichier"
    override val findingName = "Recherche du nom…"
    override val saveDestination = "Dossier de destination"
    override val quickFolderDownloads = "Téléchargements"
    override val quickFolderDocuments = "Documents"
    override val quickFolderMedia = "Médias"
    override val quickFolderDesktop = "Bureau"
    override val addRetryInspect = "Réanalyser"
    override val addStarting = "Démarrage..."
    override val addInspecting = "Analyse..."
    override val addInspectionFailed = "Échec de l'analyse"
    override val addEnterUrl = "Saisir une URL"
    override fun scheduleLinks(n: Int) = if (n <= 1) "Programmer $n lien" else "Programmer $n liens"
    override fun downloadLinks(n: Int) =
        if (n <= 1) "Télécharger $n lien" else "Télécharger $n liens"

    override val inspectVideoFirst = "Analysez d'abord la vidéo"
    override val inspectTorrentFirst = "Analysez d'abord le torrent"
    override val invalidTimeRange = "Plage horaire invalide"
    override fun scheduleVideos(n: Int) =
        if (n <= 1) "Programmer $n vidéo" else "Programmer $n vidéos"

    override fun downloadVideos(n: Int) =
        if (n <= 1) "Télécharger $n vidéo" else "Télécharger $n vidéos"

    override val addScheduleDownload = "Programmer"
    override val addStartDownload = "Télécharger"
    override val availableAudioFormats = "Formats audio disponibles"
    override val availableQualities = "Qualités disponibles"
    override val subtitlesTitle = "Sous-titres"
    override val subtitlesAuto = "auto"
    override val subtitlesEmbedInVideo = "Intégrer à la vidéo"
    override val subtitlesSeparateFile = "Fichier séparé"
    override val addSelectAll = "Tout sélectionner"
    override val addClearSelection = "Effacer"
    override fun selectedOfAvailable(selected: Int, total: Int) = "Sélection : $selected sur $total"
    override fun qualityFormatLine(format: String, details: String) = "Format : $format$details"
    override val inspectingVideoQualities = "Analyse des qualités vidéo..."
    override fun readingCourseLectures(done: Int, total: Int) =
        "Lecture des leçons du cours : ${done} / ${total}"

    override val readingCourseLecturesHint =
        "L'extension du navigateur les lit dans l'onglet du cours : gardez-le ouvert"
    override val inspectingVideoQualitiesHint =
        "Recherche des résolutions vidéo et des pistes audio disponibles"
    override val videoInspectionFailed = "Échec de l'analyse de la vidéo"
    override val addRetryInspection = "Relancer l'analyse"
    override val mediaEngineStarting = "Démarrage du moteur média..."
    override val mediaEngineNotReady = "Le moteur média n'est pas prêt"
    override val mediaEngineRequiresYtDlp =
        "L'extraction vidéo nécessite yt-dlp, installé ou intégré."
    override val preparingEngine = "Préparation du moteur..."
    override val startMediaEngine = "Télécharger / démarrer le moteur média"
    override val inspectAudioFormats = "Analyser et choisir le format audio"
    override val inspectVideoQuality = "Analyser et choisir la qualité vidéo"
    override fun clipSectionTitle(isAudio: Boolean) =
        if (isAudio) "Extrait audio (facultatif)" else "Extrait vidéo (facultatif)"

    override val clipSectionHint = "Ne télécharger qu'une plage de temps précise"
    override val audioConvertTo = "Convertir en"
    override val audioConvertAllTo = "Convertir tout l'audio en"
    override val audioOriginal = "Original"
    override val fileMissingTitle = "Fichier introuvable"
    override fun fileMissingMessage(name: String) = "« $name » n'est plus sur cet appareil. Il a peut-être été déplacé, renommé ou supprimé."
    override val browserNotInstalled = "Non installé sur cet appareil"
    override val extensionConnected = "Extension connectée"
    override val extensionNotAddedYet = "Extension pas encore ajoutée"
    override val addExtension = "Ajouter l'extension"
    override fun browserMissingTitle(name: String) = "${name} n'est pas installé"
    override fun browserMissingMessage(name: String) = "${name} n'est pas sur cet appareil. Installez-le d'abord, puis revenez ajouter l'extension."
    override fun downloadBrowser(name: String) = "Télécharger ${name}"
    override fun loadUnpackedTitle(name: String) = "Ajouter FetchJet à ${name}"
    override fun loadUnpackedSteps(name: String, page: String) = "${name} a ouvert sa page d'extensions (${page}). Là :\n1. Activez le mode développeur.\n2. Cliquez sur Charger l'extension non empaquetée.\n3. Choisissez ce dossier (son chemin est déjà copié, collez-le) :"
    override val loadUnpackedUpdates = "Une seule fois : l'extension se met ensuite à jour avec FetchJet."
    override val copyPath = "Copier le chemin"
    override fun extensionConnectedVersion(version: String) = "Extension connectée · ${version}"
    override fun extensionNeedsUpdate(found: String, wanted: String) = "L'extension ${found} est plus ancienne que ${wanted} : mettez-la à jour"
    override fun extensionOldVersion(wanted: String) = "Connectée avec une extension antérieure à ${wanted} : mettez-la à jour"
    override val updateExtension = "Mettre à jour l'extension"
    override fun storeHasUpdate(version: String) = "La boutique a déjà la ${version} : le navigateur la met à jour tout seul."
    override val updateFromAppHint = "La boutique ne l'a pas encore : mettez à jour depuis la copie incluse dans FetchJet."
    override fun storeWaiting(wanted: String) = "La version ${wanted} attend l'approbation de la boutique ; le navigateur la mettra à jour tout seul ensuite."
    override val openStore = "Ouvrir la boutique"
    override val updateFromApp = "Mettre à jour depuis FetchJet"
    override fun updateFromGitHubHint(version: String) = "La boutique ne l'a pas encore : FetchJet peut installer la ${version} signée de sa version."
    override val updateFromGitHub = "Mettre à jour"
    override val extensionDownloadFailed = "Impossible de télécharger l'extension. Vérifiez la connexion et réessayez."
    override fun installFromFileTitle(name: String) = "Installer FetchJet dans ${name}"
    override fun installFromFileSteps(name: String, file: String) = "L'extension a été enregistrée dans Downloads/${file}. Dans ${name} :\n1. Paramètres > À propos de ${name} : touchez le logo 5 fois (la première fois seulement).\n2. De retour dans Paramètres : Installer une extension depuis un fichier.\n3. Choisissez ${file} et touchez Ajouter."
    override fun openBrowser(name: String) = "Ouvrir ${name}"
    override fun mediaDurationFullRange(isAudio: Boolean, clock: String, friendly: String) =
        "Durée ${if (isAudio) "de l'audio" else "de la vidéo"} : $clock ($friendly) - durée complète sélectionnée par défaut"

    override val clipStartTimeFrom = "Début (de)"
    override val clipEndTimeTo = "Fin (à)"
    override fun fullMediaChip(isAudio: Boolean, duration: String) =
        if (isAudio) "Audio complet ($duration)" else "Vidéo complète ($duration)"

    override val clipFirst5Min = "5 premières min"
    override val clipFirst15Min = "15 premières min"
    override val clipFirst30Min = "30 premières min"
    override val clipFirst1Hour = "Première heure"
    override val clipModeTitle = "Comment télécharger l'extrait"
    override val clipOnlyTitle = "L'extrait seulement"
    override val clipOnlyLine1 = "Télécharge uniquement la partie choisie"
    override val clipOnlyLine2 = "Plus lent : YouTube limite la vitesse"
    override val clipOnlyLine3 = "Une pause relance l'extrait depuis le début"
    override fun fullThenCutTitle(isAudio: Boolean) =
        if (isAudio) "Audio complet, puis découpe" else "Vidéo complète, puis découpe"

    override fun fullThenCutLine1(isAudio: Boolean, sizeText: String) =
        if (isAudio) "Télécharge tout l'audio$sizeText" else "Télécharge toute la vidéo$sizeText"

    override val fullThenCutLine2 = "Plus rapide : pleine vitesse"
    override val fullThenCutLine3 = "Puis ne garde que votre partie"
    override val fullThenCutLine4 = "Pause / reprise possible"
    override val inspectingTorrent = "Analyse des métadonnées du torrent..."
    override val inspectingTorrentHint = "Récupération de la liste des fichiers et des pairs"
    override val torrentInspectionFailed = "Échec de l'analyse du torrent"
    override val inspectTorrentFiles = "Analyser les fichiers du torrent"
    override val disableTorrentUpload = "Désactiver l'envoi (mode leech uniquement)"
    override val torrentFiles = "Fichiers du torrent"
    override val addDeselectAll = "Tout désélectionner"
    override val torrentPriorityHigh = "Haute"
    override val torrentPriorityLow = "Basse"
    override val torrentPriorityNormal = "Normale"
    override fun selectedOfLinks(selected: Int, total: Int) =
        if (total <= 1) "$selected sur $total lien" else "$selected sur $total liens"

    override val readingTorrentFiles = "Lecture des fichiers du torrent…"
    override fun couldNotReadTorrentFiles(error: String) =
        "Impossible de lire ses fichiers ($error) ; ils seront tous téléchargés."

    override val linkTagVideo = "VIDÉO"
    override val linkTagAudio = "AUDIO"
    override val linkTagTorrent = "TORRENT"
    override val linkTagFile = "FICHIER"
    override val linkListMagnet = "Lien magnet"
    override fun linkListYouTubeVideo(id: String) = "Vidéo YouTube $id"
    override fun playlistSummary(videos: Int, selected: Int) =
        "Playlist · $videos ${if (videos <= 1) "vidéo" else "vidéos"} · $selected ${if (selected <= 1) "sélectionnée" else "sélectionnées"}"

    override val playlistFolder = "Enregistrer dans le dossier"
    override val playlistFolderHint =
        "Chaque fichier va dans ce dossier. Videz-le pour les enregistrer directement."
    override val qualityForAllVideos = "Qualité pour toutes les vidéos"
    override val qualityForAllVideosHint =
        "Si une vidéo n'a pas cette qualité, la plus proche en dessous est prise."
    override val downloadOnlyThisVideo = "Télécharger seulement cette vidéo"
    override val whenToStart = "Quand démarrer"
    override val scheduleNow = "Maintenant"
    override val scheduleIn30Min = "Dans 30 min"
    override val scheduleIn1Hour = "Dans 1 heure"
    override val scheduleAtTime = "À une heure précise…"
    override val scheduleDayLabel = "Jour"
    override val scheduleToday = "Aujourd'hui"
    override val scheduleTomorrow = "Demain"
    override val scheduleIn2Days = "Dans 2 jours"
    override val scheduleIn3Days = "Dans 3 jours"
    override val scheduleInAWeek = "Dans une semaine"
    override val scheduleTimePassed =
        "Cette heure est déjà passée aujourd'hui. Choisissez une heure plus tardive ou un autre jour."
    override val scheduleHourLabel = "Heure"
    override val scheduleMinuteLabel = "Minute"
    override val scheduleSet = "Valider"
    override val scheduleSetTime = "Choisir l'heure"
    override fun scheduleStartsAt(time: String) = "Début : $time (FetchJet doit être ouvert)"
    override val addConnectionsTitle = "Connexions"
    override val connectionsAuto = "Auto"
    override fun connectionsAutoWithDefault(n: Int) = "Auto ($n)"
    override fun pickerExceedsVideo(duration: String) = "Dépasse la durée de la vidéo ($duration)"
    override fun pickerMustBeAfterStart(start: String) = "Doit être après le début ($start)"
    override val pickerStartZero = "Début (00:00)"
    override val pickerHours = "Heures"
    override val pickerMinutes = "Minutes"
    override val pickerSeconds = "Secondes"
    override fun pickerSelectedTime(time: String, summary: String) =
        "Heure choisie : $time ($summary)"

    override fun pickerEndOfVideo(time: String) = "Fin de la vidéo ($time)"
    override fun pickerMin(time: String) = "Min ($time)"
    override fun pickerApply(time: String) = "Appliquer ($time)"
    override val pickerInvalidTime = "Heure invalide"
    override val dirPickerSelectFolder = "Choisir un dossier"
    override val dirPickerUp = "Remonter"
    override fun dirPickerSubfolders(n: Int) = "Sous-dossiers ($n)"
    override val dirPickerHiddenOn = "Cachés : affichés"
    override val dirPickerHiddenOff = "Cachés : masqués"
    override val dirPickerNewFolder = "Nouveau dossier"
    override val dirPickerFolderName = "Nom du dossier…"
    override val dirPickerCreateFailed = "Impossible de créer le dossier"
    override val dirPickerCreate = "Créer"
    override val dirPickerNoSubfolders =
        "Aucun sous-dossier ici.\nLes fichiers seront enregistrés directement dans ce dossier."
    override val dirPickerHiddenTag = "(caché)"
    override val speedLimitUnlimited = "Illimitée (max)"
    override val speedPresetsTitle = "Préréglages de vitesse"
    override val speedPresetsFooter =
        "Définissez une vitesse pour Rapide, Moyenne et Basse. Choisissez-en une rapidement depuis l’en-tête ; la tortue est toujours à 512 Ko/s."
    override val speedPresetHigh = "Élevée"
    override val speedPresetFast = "Rapide"
    override val speedPresetMedium = "Moyenne"
    override val speedPresetLow = "Basse"
    override val speedScheduleTitle = "Planning de vitesse"
    override val speedScheduleFooter =
        "Limitez la vitesse à certaines heures ; illimité en dehors."
    override val speedScheduleAdd = "Ajouter une plage horaire"
    override val speedScheduleFrom = "De"
    override val speedScheduleTo = "À"
    override val speedScheduleActive = "Limite planifiée active"
    override val speedScheduleEmpty = "Aucune plage horaire — ajoutez-en une."
    override val priorityLabel = "Priorité"
    override val priorityHigh = "Élevée"
    override val priorityNormal = "Normale"
    override val priorityLow = "Basse"
    override val checksumLabel = "Somme de contrôle (facultatif)"
    override val checksumHint = "sha256 ou md5"
    override val checksumVerified = "Somme de contrôle vérifiée"
    override val checksumMismatch = "Somme de contrôle incorrecte"
    override val checksumChecking = "Vérification de la somme de contrôle…"
    override val speedLimitNoLimit = "Sans limite (vitesse max)"
    override val speedLimitCustom = "Personnalisée..."
    override val speedLimitValue = "Valeur"
    override val scheduleAm = "AM"
    override val schedulePm = "PM"
    override val detailsChipMedia = "Média"
    override val detailsChipTorrent = "Torrent"
    override val detailsChipFile = "Fichier"
    override val detailsChipResumable = "Reprise possible"
    override val detailsChipNoResume = "Sans reprise"
    override fun detailsClipChip(start: String, end: String?, cutAfterDownload: Boolean) =
        "Extrait $start–${end ?: "fin"}" + if (cutAfterDownload) " · découpé après téléchargement" else ""

    override fun detailsTorrentFiles(selected: Int, total: Int) =
        "Fichiers du torrent ($selected ${if (selected <= 1) "sélectionné" else "sélectionnés"} / $total)"

    override val detailsPriorityHigh = "Haute"
    override val detailsPriorityLow = "Basse"
    override val detailsPriorityNormal = "Normale"
    override val detailsStatusCompleted = "Terminé"
    override val detailsStatusPaused = "En pause"
    override val detailsStatusFailed = "Échec"
    override val detailsStatusWaiting = "En attente"
    override val detailsStatusQueued = "Dans la file"
    override val detailsStatusNotStarted = "Pas démarré"
    override val detailsUnknownSize = "Inconnue"
    override val detailsMergingAudioVideo = "Fusion de l'audio et de la vidéo..."
    override fun detailsTorrentSpeedUploadDisabled(download: String) =
        "Réception : $download (envoi désactivé)"

    override fun detailsTorrentSpeed(download: String, upload: String) =
        "Réception : $download | Envoi : $upload"

    override val detailsConnecting = "Connexion..."
    override fun detailsStartsAt(time: String) = "Début : $time"
    override fun detailsWaitingInQueuePosition(position: Int) =
        "En attente dans la file (n° $position)"

    override val detailsWaitingInQueue = "En attente dans la file"
    override val detailsDownloaded = "Téléchargé"
    override val detailsSpeed = "Vitesse"
    override val detailsState = "État"
    override val detailsTimeLeft = "Temps restant"
    override val detailsAdded = "Ajouté"
    override val detailsSavedTo = "Enregistré dans"
    override val detailsSource = "Source"
    override val detailsPage = "Page"
    override val detailsOpenFile = "Ouvrir le fichier"
    override val detailsOpenFolder = "Ouvrir le dossier"
    override val detailsStartNow = "Démarrer"
    override val detailsRemoveFromList = "Retirer de la liste"
    override val detailsCancelDownload = "Annuler le téléchargement"
    override val detailsHeaderDownload = "Téléchargement"
    override val detailsHeaderDetails = "DÉTAILS"
    override fun detailsAddedToday(clock: String) = "Aujourd'hui $clock"
    override fun detailsAddedYesterday(clock: String) = "Hier $clock"

    override fun removeDialogTitle(count: Int) =
        if (count > 1) "Retirer $count téléchargements" else "Retirer le téléchargement"

    override fun removeDialogMessage(count: Int) =
        if (count > 1) "Voulez-vous vraiment retirer ces $count téléchargements ?"
        else "Voulez-vous vraiment retirer ce téléchargement ?"

    override val removeDialogAlsoDeleteFiles = "Supprimer aussi le ou les fichiers du disque"

    override val duplicateDialogTitle = "Téléchargement déjà présent"
    override val duplicateDialogSubtitle =
        "Cet élément est déjà dans votre liste de téléchargements"

    override fun duplicateDialogStatus(status: String) = "État : $status"
    override val duplicateDialogQuestion =
        "Voulez-vous reprendre le téléchargement existant ou tout recommencer depuis le début ?"
    override val duplicateDialogResumeExisting = "Reprendre l'existant"
    override val duplicateDialogRedownload = "Retélécharger (depuis le début)"

    override val overwriteDialogTitle = "Le fichier existe déjà"
    override val overwriteDialogSubtitle =
        "Un fichier portant ce nom existe déjà dans la destination"
    override val overwriteDialogFileNameLabel = "Nom du fichier : "
    override val overwriteDialogFolderLabel = "Dossier : "
    override val overwriteDialogNewCopyLabel = "Nouvelle copie : "
    override val overwriteDialogQuestion =
        "Voulez-vous enregistrer ce téléchargement comme nouvelle copie ou remplacer le fichier existant ?"

    override fun overwriteDialogSaveAsCopy(name: String) = "Enregistrer une copie ($name)"
    override val overwriteDialogOverwriteExisting = "Remplacer le fichier existant"

    override val desktopWindowTagline = "Gestionnaire de téléchargement rapide multi-moteur"
    override val desktopTrayIdleTooltip = "FetchJet - en arrière-plan"
    override val desktopTrayDownloadsHeader = "Téléchargements FetchJet :"
    override fun desktopTrayMore(count: Int) = "+$count de plus"
    override val desktopTrayShowApp = "Afficher l'application"
    override val desktopTrayCloseApp = "Quitter l'application"
    override val sectionGeneral = "Général"
    override val sectionGeneralBlurb = "Apparence de l'application et démarrage"
    override val sectionDownloads = "Téléchargements"
    override val sectionDownloadsBlurb =
        "Emplacement des fichiers et nombre de téléchargements simultanés"
    override val sectionBrowser = "Extension de navigateur"
    override val sectionBrowserBlurb = "Envoyez liens et vidéos de votre navigateur vers FetchJet"
    override val sectionSpeed = "Limites de vitesse"
    override val sectionSpeedBlurb = "Limitez la bande passante utilisée par FetchJet"
    override val sectionMedia = "Outils média"
    override val sectionMediaBlurb = "yt-dlp et FFmpeg pour les sites de vidéos"
    override val sectionTorrent = "BitTorrent"
    override val sectionTorrentBlurb = "Pairs, envoi et moteur torrent"
    override val sectionUpdates = "Mises à jour"
    override val sectionUpdatesBlurb = "Votre version et les nouvelles versions"
    override val summaryNoQueueLimit = "sans limite"
    override fun summaryAtATime(n: Int) = "$n à la fois"
    override val summaryNoSpeedLimit = "Sans limite"
    override val summarySetupNeeded = "Configuration requise"
    override val summaryUploadingOff = "Envoi désactivé"
    override val summaryUploadingOn = "Envoi activé"
    override fun summaryVersionAvailable(version: String) = "$version disponible"
    override fun summaryVersion(version: String) = "Version $version"
    override val changesSavedAutomatically = "Les modifications sont enregistrées automatiquement."
    override val settingsAllSettings = "Tous les paramètres"

    override val clipboardGroup = "Presse-papiers"
    override val clipboardFooter =
        "Copiez n'importe où un lien vers un fichier (zip, mp4, pdf, apk…) ou un lien magnet, et FetchJet vous propose de le télécharger."
    override val watchClipboard = "Surveiller le presse-papiers"
    override val watchClipboardOn =
        "Activé · les liens de fichiers copiés sont proposés au téléchargement"
    override val settingOff = "Désactivé"
    override val alsoVideoPages = "Aussi les pages vidéo"
    override val alsoVideoPagesHint =
        "Les liens YouTube, Facebook, TikTok, Vimeo… sont aussi proposés (pas seulement les fichiers)"
    override val diagnosticsGroup = "Diagnostic"
    override fun logSavedTo(path: String) =
        "Enregistré : $path — envoyez ce fichier pour signaler un problème."

    override val diagnosticsFooter =
        "Enregistre le journal de l'application (ce qu'ont fait yt-dlp et les moteurs) dans votre dossier Téléchargements."
    override val saveLogToDownloads = "Enregistrer le journal dans Téléchargements"
    override val logCouldNotSave = "enregistrement impossible"
    override val appearanceGroup = "Apparence"
    override val themeTitle = "Thème"
    override val themeHint = "Apparence claire ou sombre pour tous les écrans"
    override val settingsThemeDark = "Sombre"
    override val settingsThemeSystem = "Système"
    override val settingsThemeLight = "Clair"
    override val speedShownIn = "Unité de vitesse"
    override val speedShownInHint =
        "Octets (MB/s) comme la taille des fichiers, ou bits (Mbps) comme votre abonnement Internet"

    override val saveLocationGroup = "Emplacement d'enregistrement"
    override val saveLocationFooter =
        "Les nouveaux téléchargements vont ici, sauf si vous choisissez un autre dossier en les ajoutant."
    override val settingsFolderPath = "Chemin du dossier"
    override val settingsChooseFolder = "Choisir un dossier…"
    override val settingsUseDownloads = "Utiliser Téléchargements"
    override val settingsConnectionsGroup = "Connexions"
    override val connectionsFooter =
        "Nombre de morceaux d'un téléchargement direct. Chaque morceau est une connexion ; quand l'un se termine, il reprend la moitié du plus gros morceau restant, aucun ne reste inactif. Auto choisit selon la taille du fichier (4–8). Certains serveurs limitent les connexions par client."
    override val settingsConnectionsPerDownload = "Connexions par téléchargement"
    override val settingsConnectionsAuto = "Auto"
    override val organizeGroup = "Organisation"
    override val organizeFooter =
        "Des sous-dossiers sont créés dans l'emplacement d'enregistrement : Video, Music, Archives, Documents, Programs, Images, Torrents."
    override val sortIntoFolders = "Classer par type dans des dossiers"
    override val sortIntoFoldersOn = "Activé · ex. Downloads/Video/cours.mp4"
    override val sortIntoFoldersOff = "Désactivé · tout va directement dans le dossier"

    override val speedLimitsFooter =
        "Appliqué immédiatement et partagé entre les téléchargements en cours."
    override val speedLimitsFooterIos =
        "Appliqué immédiatement et partagé entre les téléchargements en cours. Sur iOS, les téléchargements HTTP déjà en cours en arrière-plan gardent leur vitesse ; la limite s'applique à ceux lancés ensuite."
    override val speedLimitDownload = "Réception"
    override val speedLimitUploadTorrents = "Envoi (torrents)"

    override val torrentSharingGroup = "Partage"
    override val downloadOnlyNoUpload = "Télécharger seulement (sans envoi)"
    override val downloadOnlyNoUploadHint =
        "Économise votre bande passante montante et votre forfait. Certains trackers ralentissent les pairs qui ne partagent pas."
    override val torrentEngineGroup = "Moteur"
    override val settingsReady = "Prêt"
    override val settingsNotReady = "Pas prêt"

    override val extInstallGroup = "Installation"
    override val extensionInstallFooter =
        "Avec l'extension, un bouton « Télécharger avec FetchJet » apparaît sur les vidéos et les liens, et le navigateur peut confier ses téléchargements à FetchJet. Elle ne communique qu'avec cette application, sur votre appareil."
    override val edgeAddedHint =
        "Ajoutée · redémarrez Edge et cliquez sur « Activer l'extension » quand il le demande"
    override val edgeFromStoreHint = "Depuis Microsoft Edge Add-ons · se met à jour toute seule"
    override val openInEdgeStore = "Ouvrir dans la boutique Edge"
    override val edgeAddedNote =
        "C'est fait : redémarrez Edge, il vous proposera d'activer FetchJet."
    override val extAddToEdge = "Ajouter à Edge"
    override val firefoxFromStoreHint = "Depuis Firefox Add-ons · se met à jour toute seule"
    override val extAddToFirefox = "Ajouter à Firefox"
    override val chromeZipHint =
        "Absente du Chrome Web Store : installez le .zip en mode développeur (étapes sur la page). Elle ne se met pas à jour toute seule."
    override val getTheZip = "Obtenir le .zip"
    override val firefoxAndroidHint =
        "Ouvrez ceci dans Firefox sur ce téléphone. Les liens envoyés arrivent directement dans cette application."
    override val keepReadyForBrowser = "Rester prêt pour le navigateur"
    override val keepReadyOn =
        "Les qualités vidéo s'affichent directement dans Firefox. Une notification discrète reste affichée tant que c'est activé."
    override val keepReadyOff =
        "Désactivé : Firefox ouvre FetchJet et vous choisissez la qualité ici. Activé : les qualités s'affichent dans Firefox (avec une notification)."
    override val popUpOverBrowser = "S'afficher par-dessus le navigateur"
    override val popUpOverBrowserOn =
        "Activé · FetchJet s'ouvre tout seul quand Firefox envoie un lien"
    override val popUpOverBrowserOff =
        "Désactivé · une notification s'affiche à la place ; touchez-la pour autoriser « Superposition sur d'autres applis »"

    override val mediaToolsGroup = "Outils"
    override val ytDlpHint = "Trouve la vidéo et l'audio sur YouTube et d'autres sites"
    override val settingsMissing = "Manquant"
    override val ffmpegHint =
        "Assemble vidéo + audio (1080p et plus), découpe les extraits, crée des MP3"
    override val ffmpegMissing = "Manquant · jusqu'à 720p"
    override fun jsRuntimeTitle(name: String) = "Moteur JS · $name"
    override val jsRuntimeHint =
        "Résout les vérifications de YouTube pour afficher toutes les qualités, plus vite"

    override fun missingPress(button: String) = "Manquant · appuyez sur $button"
    override val mediaToolsFolder = "Dossier des outils"
    override val mediaUpdatingTools = "Mise à jour…"
    override val mediaDownloadingTools = "Téléchargement des outils…"
    override fun mediaDownloadingTool(name: String) = "Téléchargement de $name…"
    override fun mediaDownloadingToolPercent(name: String, percent: Int) =
        "Téléchargement de $name… $percent %"

    override val mediaUpdateTools = "Mettre à jour les outils"
    override val mediaDownloadTheTools = "Télécharger les outils"

    override val updateNewVersionAvailable = "Nouvelle version disponible"
    override fun updateVersionLine(version: String, current: String) =
        "FetchJet $version  ·  vous avez la $current"

    override val updateWhatsNew = "Nouveautés"
    override val updateNoReleaseNotes = "Aucune note de version."
    override fun downloadingUpdate(percent: Int) = "Téléchargement de la mise à jour… $percent %"
    override val updateDownloadedAndroid =
        "La mise à jour est téléchargée. Android vous demande de confirmer l'installation ; vos téléchargements restent dans la liste."
    override val updateDownloadedDesktop =
        "La mise à jour est téléchargée. FetchJet va se fermer, l'installer et se rouvrir. Les téléchargements en cours sont mis en pause et pourront être repris."
    override val storeInstallsItselfOpenNow =
        "Le Microsoft Store l'installe tout seul. Ouvrez le Store pour mettre à jour maintenant."
    override val updateInstall = "Installer"
    override val updateRestartAndUpdate = "Redémarrer et mettre à jour"
    override val updateDownloading = "Téléchargement…"
    override val updateDownloadAndInstall = "Télécharger et installer"
    override val updateOpenMicrosoftStore = "Ouvrir le Microsoft Store"
    override fun updateDownloadWithSize(size: String) = "Télécharger ($size)"
    override val updateOpenReleasePage = "Ouvrir la page de la version"
    override val updateSkipThisVersion = "Ignorer cette version"
    override val updateLater = "Plus tard"
    override fun versionIsAvailable(version: String) = "La version $version est disponible"
    override val youHaveLatest = "Vous avez la version installée ici"
    override val updateSeeWhatsNew = "Voir les nouveautés"
    override val updateChecking = "Vérification…"
    override val updateCheckNow = "Vérifier maintenant"
    override val checkUpdatesAutomatically = "Rechercher les mises à jour automatiquement"
    override val checkUpdatesAutomaticallyHint =
        "Cherche une nouvelle version au démarrage de FetchJet (deux fois par jour au plus)."
    override val downloadUpdatesAutomatically = "Télécharger les mises à jour automatiquement"
    override val downloadUpdatesAutomaticallyHintAndroid =
        "Télécharge la nouvelle version en arrière-plan ; vous n'avez qu'à confirmer l'installation."
    override val downloadUpdatesAutomaticallyHint =
        "Télécharge la nouvelle version en arrière-plan ; vous n'avez qu'à confirmer le redémarrage."
    override val installingUpdates = "Installation des mises à jour"
    override val installingUpdatesStore =
        "Le Microsoft Store installe les nouvelles versions tout seul ; FetchJet vous prévient quand l'une d'elles sort."
    override val installingUpdatesManual =
        "Sur cet appareil, FetchJet vous signale une nouvelle version et donne le lien ; c'est vous qui l'installez."
    override val updateReleaseNotes = "Notes de version"
    override val updateReleaseNotesHint = "Chaque version et ses changements"
    override val installHintReleasePage =
        "Ouvrez la page de la version pour télécharger la nouvelle version."
    override val installHintApk =
        "Téléchargez l'APK et ouvrez-le pour installer la mise à jour par-dessus cette version."
    override val installHintIpa =
        "Téléchargez le .ipa et installez-le avec AltStore / Sideloadly, comme lors de l'installation de FetchJet."
    override val installHintDmg =
        "Téléchargez le .dmg, ouvrez-le et faites glisser FetchJet dans Applications (remplacez l'ancien)."
    override val installHintDeb =
        "Téléchargez le .deb et installez-le (double-clic, ou sudo apt install ./file.deb)."
    override val installHintOther =
        "Téléchargez la nouvelle version et installez-la par-dessus celle-ci."

    override val startupGroup = "Démarrage"
    override val startupStoreFooter =
        "La version Store est lancée par Windows : activez FetchJet dans Paramètres > Applications > Démarrage."
    override val startupStartAtLogin = "Lancer FetchJet à l'ouverture de session"
    override val startupManagedByWindows = "Géré par Windows"
    override val startupOpenWindowsSettings = "Ouvrir les paramètres Windows"
    override val startupStartHidden = "Démarrer caché dans la zone de notification"
    override val startupStartHiddenHint =
        "Au lancement à l'ouverture de session, rester dans la zone de notification sans ouvrir la fenêtre."
    override val startupStartAtLoginHint =
        "Les téléchargements programmés et en file continuent, et l'extension du navigateur trouve toujours l'application."

    override fun startupCouldNotChange(message: String) =
        "Impossible de modifier le paramètre : $message"

    override val whenDoneGroup = "Quand les téléchargements sont terminés"
    override val whenDoneHint =
        "Pour cette session seulement. Vous avez d'abord 60 secondes pour annuler."
    override val whenDoneNothing = "Ne rien faire"
    override val whenDoneExit = "Quitter FetchJet"
    override val whenDoneSleep = "Mettre en veille"
    override val whenDoneShutDown = "Éteindre"
    override val whenDoneTitle = "Tous les téléchargements sont terminés"
    override fun whenDoneExitIn(seconds: Int) = "FetchJet se fermera dans ${seconds} s."
    override fun whenDoneSleepIn(seconds: Int) =
        "L'ordinateur se mettra en veille dans ${seconds} s."

    override fun whenDoneShutDownIn(seconds: Int) = "L'ordinateur s'éteindra dans ${seconds} s."
    override val whenDoneNow = "Maintenant"
    override fun whenDoneBanner(action: String) = "Fin des téléchargements : ${action}"
    override fun whenDoneFailed(message: String) = "Impossible : ${message}"
    override val sectionNetwork = "Réseau"
    override val sectionNetworkBlurb = "Proxy et identifiants de sites"
    override val netProxyGroup = "Proxy"
    override val netProxyOff = "Désactivé"
    override val netProxyHost = "Serveur"
    override val netProxyPort = "Port"
    override val netProxyUser = "Nom d'utilisateur (facultatif)"
    override val netProxyPassword = "Mot de passe"
    override val netUser = "Nom d'utilisateur"
    override val netSave = "Enregistrer"
    override val netSaved = "Enregistré"
    override val netProxyHint =
        "Utilisé par les téléchargements, les vidéos (yt-dlp) et les torrents. S'applique aux téléchargements lancés après l'enregistrement."
    override val netProxyHintIos =
        "Utilisé par les téléchargements et les vidéos. Les torrents sur iOS se connectent directement. S'applique aux téléchargements lancés après l'enregistrement."
    override val netProxyBadPort = "Saisissez un serveur et un port de 1 à 65535"
    override val netLoginsGroup = "Identifiants de sites"
    override val netLoginsHint =
        "Pour les téléchargements qui demandent un nom d'utilisateur et un mot de passe (HTTP basic auth). Un site couvre aussi ses sous-domaines."
    override val netLoginSite = "Site (example.com)"
    override val netLoginAdd = "Ajouter"
    override val netLoginsEmpty = "Aucun identifiant enregistré"
    override val netRemove = "Supprimer"
    override fun netLoginsCount(n: Int) = "${n} identifiants"
    override val sysTabAllDownloads = "Tous les téléchargements"
    override val sysTabActive = "En cours"
    override val sysTabCompleted = "Terminés"
    override val sysTabMediaExtractor = "Extracteur média"
    override val sysTabSettings = "Paramètres"
    override val sysSortNewestFirst = "Plus récents d'abord"
    override val sysSortOldestFirst = "Plus anciens d'abord"
    override val sysSortLargestFirst = "Plus gros d'abord"
    override val sysSortSmallestFirst = "Plus petits d'abord"
    override val sysSortNameAz = "Nom A–Z"
    override val sysEnterValidUrlOrMagnet = "Saisissez une URL ou un lien magnet valide"
    override val sysCannotStartVideoInspectionFailed =
        "Impossible de lancer le téléchargement : l'analyse de la vidéo a échoué."
    override val sysCannotStartTorrentInspectionFailed =
        "Impossible de lancer le téléchargement : l'analyse du torrent a échoué ou aucun fichier n'est sélectionné."
    override val sysClipEndExceedsDuration = "La fin de l'extrait dépasse la durée de la vidéo."
    override val sysClipStartAfterEnd = "Le début de l'extrait doit être avant la fin."
    override fun sysStartedDownload(name: String) = "Téléchargement lancé : $name"
    override val sysFailedToQueueDownload = "Impossible d'ajouter le téléchargement à la file"
    override fun sysQualitiesNotAdded(count: Int) =
        if (count <= 1) "$count qualité n'a pas pu être ajoutée." else "$count qualités n'ont pas pu être ajoutées."

    override fun sysAddedQualityDownloads(count: Int, title: String) =
        "${if (count <= 1) "$count téléchargement ajouté" else "$count téléchargements ajoutés"} pour « $title »"

    override val sysSelectAtLeastOnePlaylistVideo =
        "Sélectionnez au moins une vidéo de la playlist."

    override fun sysVideosNotAdded(count: Int) =
        if (count <= 1) "$count vidéo n'a pas pu être ajoutée." else "$count vidéos n'ont pas pu être ajoutées."

    override fun sysAddedPlaylistVideos(count: Int, playlistTitle: String) =
        "${if (count <= 1) "$count vidéo ajoutée" else "$count vidéos ajoutées"} depuis « $playlistTitle »"

    override val sysSelectAtLeastOneLink = "Sélectionnez au moins un lien."
    override fun sysAddedLinkDownloads(added: Int, skipped: Int) =
        (if (added <= 1) "$added téléchargement ajouté" else "$added téléchargements ajoutés") +
                (if (skipped > 0) " ($skipped déjà dans la liste ou en échec)" else "")

    override val sysCouldNotReadTorrent = "Impossible de lire le torrent"
    override fun sysCouldNotStartDownload(reason: String) =
        "Impossible de lancer le téléchargement : $reason"

    override val sysUnknownError = "erreur inconnue"
    override fun sysQueuedFromBrowser(added: Int, skipped: Int) =
        (if (added <= 1) "$added téléchargement ajouté depuis le navigateur" else "$added téléchargements ajoutés depuis le navigateur") +
                (if (skipped > 0) " ($skipped ignorés)" else "")

    override val sysEnterTorrentOrMagnetFirst = "Saisissez d'abord un torrent ou un lien magnet"
    override val sysFailedToParseTorrentMetadata = "Impossible de lire les métadonnées du torrent"
    override val sysEnterMediaUrlFirst = "Saisissez d'abord l'URL d'un média"
    override fun sysUnableToFetchFormatsWithReason(reason: String) =
        "Impossible de récupérer les formats vidéo : $reason"

    override val sysUnableToFetchFormats =
        "Impossible de récupérer les formats vidéo. Vérifiez l'URL ou votre connexion Internet."
    override val sysFailedToInspectMediaQualities = "Impossible d'analyser les qualités du média"
    override fun sysTorrentEngineError(message: String) = "Erreur du moteur BitTorrent : $message"
    override fun sysTorrentEngineInstallFailed(message: String) =
        "Échec de l'installation du moteur BitTorrent : $message"

    override val sysMediaToolsDownloaded = "Outils média téléchargés !"
    override val sysMediaToolsDownloadFailed = "Échec du téléchargement des outils média"
    override val sysMediaToolsUpdated = "Outils média mis à jour"
    override fun sysSavedLinks(count: Int, path: String) =
        "${if (count <= 1) "$count lien enregistré" else "$count liens enregistrés"} dans $path"

    override fun sysCouldNotSaveLinks(reason: String) =
        "Impossible d'enregistrer les liens : $reason"

    override val sysErrorFallback = "erreur"
    override val sysDownloadFailed = "Échec du téléchargement"
    override val sysDownloadFailedNetwork = "Échec du téléchargement à cause d'une erreur réseau"
    override val sysCouldNotStart = "Démarrage impossible"
    override val sysUnknownErrorCapital = "Erreur inconnue"
    override fun sysCouldNotSaveFileTo(dir: String) =
        "Impossible d'enregistrer le fichier dans $dir"

    override val sysPlaylistFallbackTitle = "Playlist"
    override val sysPlaylistQualityBest = "Meilleure"
    override val sysPlaylistQualityAudioOnly = "Audio seulement"
    override val sysToolsReadyYtDlpFfmpeg = "Prêt : yt-dlp et ffmpeg installés (1080p et plus)"
    override val sysToolsReadyYtDlpNoFfmpeg = "Prêt : yt-dlp installé (sans ffmpeg - limité à 720p)"
    override val sysToolsNotInstalled =
        "Non installés. Cliquez sur « Télécharger les outils » ci-dessous."
    override val sysToolsInstalledBuiltIn = "Installés et prêts (intégrés)"
    override fun sysYtDlpVersionReady(version: String) = "yt-dlp $version (prêt)"
    override val sysMediaEngineBridgeNotRegistered = "Le pont du moteur média n'est pas enregistré"
    override val sysWebKitBuiltIn = "WebKit (intégré)"
    override val sysTorrentEngineBuiltIn = "Prêt : moteur BitTorrent intégré"
    override fun sysTorrentEngineReadyVersion(version: String) =
        "Prêt : moteur BitTorrent (libtorrent $version)"

    override fun sysTorrentEngineFailedToLoad(message: String) =
        "Le moteur BitTorrent n'a pas pu se charger : $message"

    override val sysTorrentEngineAvailable =
        "Prêt : moteur BitTorrent disponible (chargé au premier torrent)"
    override val sysTorrentEngineMissing =
        "Moteur BitTorrent manquant. Cliquez sur « Télécharger les outils » ci-dessous."
    override val sysTorrentEngineNotAvailable =
        "Le moteur BitTorrent n'est pas disponible dans cette version"

    override fun sysCouldNotStartInstaller(message: String) =
        "Impossible de lancer l'installateur : $message"

    override val sysCheckingForUpdates = "Recherche de mises à jour…"
    override val sysNoPublishedRelease = "Aucune version publiée pour l'instant."
    override fun sysVersionNoChangesForDevice(version: String, current: String) =
        "La version $version n'apporte rien pour cet appareil ; vous avez déjà la dernière version pour lui ($current)."

    override fun sysVersionAvailable(version: String) = "La version $version est disponible."
    override fun sysUpToDate(current: String) = "Vous êtes à jour ($current)."
    override fun sysCouldNotCheckForUpdates(reason: String) =
        "Impossible de rechercher les mises à jour : $reason"

    override val sysNetworkError = "erreur réseau"
    override val sysUpdateNoInstallerForSystem =
        "Échec du téléchargement de la mise à jour : cette version n'a pas d'installateur pour ce système."

    override fun sysDownloadingAsset(name: String) = "Téléchargement de $name…"
    override fun sysVersionReadyToInstall(version: String) =
        "La version $version est prête à être installée."

    override fun sysUpdateDownloadFailed(message: String) =
        "Échec du téléchargement de la mise à jour : $message"

    override val sysAllowUnknownAppsThenInstall =
        "autorisez « Installer des applis inconnues » pour FetchJet, puis touchez à nouveau Installer."
    override val sysDownloadsChannelName = "Téléchargements FetchJet"
    override val sysDownloadsChannelDescription =
        "Affiche la progression des téléchargements en temps réel"
    override val sysNotificationAppTitle = "FetchJet Downloader"
    override val sysAppRunningInBackground = "L'application fonctionne en arrière-plan"
    override val sysNotificationDownloadsFallback = "Téléchargements"
    override fun sysFilesDownloading(count: Int) =
        if (count <= 1) "$count fichier en téléchargement" else "$count fichiers en téléchargement"

    override fun sysPercentActive(percent: Int, count: Int) =
        "$percent % • ${if (count <= 1) "$count actif" else "$count actifs"}"

    override fun sysActiveCount(count: Int) = if (count <= 1) "$count actif" else "$count actifs"
    override fun sysDownloadCompleteSize(size: String) = "Téléchargement terminé • $size"
    override fun sysMoreDownloading(count: Int) = "+ $count autres en téléchargement"
    override val sysComplete = "Terminé"
    override fun sysNotificationDownloadsTitle(speed: String) = "Téléchargements FetchJet ($speed)"
    override fun sysDownloadingPercent(count: Int, percent: Int) = "$count en cours • $percent %"
    override val sysDownloadTaskFallback = "Téléchargement"
    override val sysBrowserReadyTitle = "FetchJet est prêt pour votre navigateur"
    override val sysBrowserReadyText = "Les qualités vidéo s'affichent directement dans Firefox"
    override val sysTurnOff = "Désactiver"
    override val sysBrowserReadyChannelName = "Extension de navigateur"
    override val sysBrowserReadyChannelDescription =
        "Affichée tant que FetchJet reste prêt pour l'extension de navigateur"
    override val sysDownloadingFileFallback = "Téléchargement du fichier"
    override val sysUnknownSize = "Inconnue"
    override val sysStatusCompleted = "Terminé"
    override val sysStatusDownloading = "Téléchargement"
    override val sysDownloadCompleteTitle = "Téléchargement terminé"
    override fun sysFinishedDownloading(size: String) = "Téléchargement terminé • $size"
    override val sysSentToFetchJet = "Envoyé à FetchJet"
    override val sysLinksChannelName = "Liens du navigateur"
    override val sysLinksChannelDescription =
        "Touchez pour ouvrir le lien que FetchJet vient de recevoir"
    override val sysLinkReceivedTitle = "Lien reçu"
    override val sysLinkReceivedText = "Touchez pour l'ouvrir dans FetchJet"

    override val stepCompleted = "Terminé"
    override val stepSeeding = "En partage"
    override val stepFetchingMetadata = "Récupération des métadonnées"
    override val stepConnectingToPeers = "Connexion aux pairs"
    override val stepCheckingFiles = "Vérification des fichiers"
    override val stepCheckingResumeData = "Vérification des données de reprise"
    override val stepDownloading = "Téléchargement"
    override val stepFinished = "Terminé"
    override val stepConnecting = "Connexion"
    override val stepPaused = "En pause"
    override val stepError = "Erreur"
    override val stepResolvingStreams = "Analyse des flux"
    override val stepDownloadingParts = "Téléchargement des parties"
    override val stepPreparingDownload = "Préparation du téléchargement"
    override val stepClippingVideo = "Découpage de la vidéo"
    override val stepDownloadingMedia = "Téléchargement du média"
    override val stepAlreadyDownloaded = "Déjà téléchargé"
    override val stepDownloadingVideo = "Téléchargement de la vidéo"
    override val stepDownloadingAudio = "Téléchargement de l'audio"
    override val stepMergingAudioVideo = "Fusion de l'audio et de la vidéo"
    override val stepConvertingToMp3 = "Conversion en MP3"
    override val stepCuttingClip = "Découpage de l'extrait"
    override val stepSavingToFolder = "Enregistrement dans le dossier"
    override val stepFinalizingVideo = "Finalisation de la vidéo"
    override val stepConvertingVideo = "Conversion de la vidéo"
    override val stepRepackagingVideo = "Réempaquetage de la vidéo"
    override val stepYtDlpFallback = "Repli sur yt-dlp"
    override val stepNotPlainStream = "pas un flux simple"
    override val stepSingleConnection = "Connexion unique"
    override val stepServerNoRanges = "le serveur ne gère pas les plages"
    override val stepAgeRestrictedExpiredCookies =
        "Restriction d'âge : cookies expirés dans Fichiers > FetchJet"
    override val stepAgeRestrictedPlaceCookies =
        "Restriction d'âge : placez cookies.txt dans Fichiers > FetchJet"
    override val stepPreparingMediaEngine =
        "Préparation du moteur multimédia intégré (démarrage de Python, chargement de yt-dlp)"
    override val stepMediaEngineNotStarted =
        "Moteur multimédia non démarré. Touchez « Télécharger les outils »."

    override fun stepNumber(step: Int, total: Int) = "Étape ${step} sur ${total}"
    override fun stepSavingAs(ext: String) = "Enregistrement en .${ext}"
    override fun stepJoiningParts(count: Int) =
        "Assemblage de ${count} ${if (count <= 1) "partie" else "parties"} en MP4"

    override fun stepNoDataFor(seconds: Int) = "Aucune donnée depuis ${seconds} s"
    override fun stepPeers(peers: Int, seeds: Int) =
        "${peers} ${if (peers <= 1) "pair" else "pairs"} (${seeds} ${if (seeds <= 1) "source" else "sources"})"

    override fun stepDownloadingFrom(host: String) = "Téléchargement depuis ${host}"
    override fun stepMediaEngineNotReady(error: String) = "Moteur multimédia non prêt : ${error}"
    override fun stepTorrentEngineFailed(error: String) =
        "Échec du démarrage du moteur BitTorrent : ${error}"

    override fun stepReadyWith(details: String) = "Prêt : ${details}"

    override val audioCompressTitle = "Compresser l'audio"
    override val audioCompressOriginalSize = "Taille d'origine"
    override val audioCompressNewSize = "Taille compressée"
    override val audioCompressReading = "Lecture du fichier…"
    override val audioCompressMode = "Mode"
    override val audioCompressSimple = "Simple"
    override val audioCompressAdvanced = "Avancé"
    override val audioCompressLevel = "Niveau de compression"
    override val audioCompressLevelHint = "Une valeur plus élevée compresse davantage"
    override val audioCompressBitrate = "Débit binaire"
    override val audioCompressSampleRate = "Fréquence d'échantillonnage"
    override val audioCompressChannels = "Canaux"
    override val audioCompressAuto = "Auto"
    override val audioCompressMono = "Mono"
    override val audioCompressStereo = "Stéréo"
    override val audioCompressStart = "Compresser"
    override val audioCompressRunning = "Compression…"
    override val audioCompressFailed = "Impossible de compresser ce fichier"
    override val audioCompressCopies = "Copies compressées"
    override val audioCompressDelete = "Supprimer"
    override val audioCompressHigherQuality = "Meilleure qualité"
    override val audioCompressSmallerFile = "Fichier plus petit"

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
    override val visibleToDevices = "Visible par les autres appareils"
    override val visibleToDevicesHint = "Les autres appareils FetchJet de ce réseau peuvent trouver celui-ci et lui envoyer des téléchargements. Désactivé : vous pouvez toujours leur en envoyer."
    override val sysDeviceSearch = "Rechercher un appareil"
    override val sysDeviceOthers = "Autres appareils"
    override val sysDeviceNoMatch = "Aucun appareil ne correspond"
    override val sectionDevices = "Appareils"
    override val sectionDevicesBlurb = "Envoyer des téléchargements entre vos appareils"
    override val pairedDevicesTitle = "Appareils associés"
    override val noPairedDevices = "Aucun appareil associé. Associez-en un via Envoyer vers un appareil sur un téléchargement."
    override val unpairDevice = "Dissocier"
    override val summaryVisible = "Visible"
    override val summaryHidden = "Masqué"
    override val blockDevice = "Bloquer"
    override val unblockDevice = "Débloquer"
    override val blockedDevicesTitle = "Appareils bloqués"
    override val noBlockedDevices = "Aucun appareil bloqué"
    override val pairRefusedBlocked = "L'autre appareil a bloqué celui-ci"
    override val pairRefusedWait = "Attendez {seconds} s avant de redemander à cet appareil"
    override val pairRefusedBusy = "L'autre appareil répond à une autre demande d'association. Réessayez dans un instant."
    override val pairDeclined = "L'autre appareil a refusé l'association. Renvoyez pour réessayer."
    override val pairExpired = "L'autre appareil n'a pas répondu à temps. Renvoyez pour réessayer."
    override val sysDevicePairedLabel = "Paired"
    override val sysDeviceThisDevice = "This device"
    override val sysDeviceManualConnect = "Connect by IP"
    override val sysDeviceConnect = "Connect"
    override val sysDeviceOfferTitle = "Recevoir un téléchargement ?"
    override val sysDeviceOfferFrom = "De {device}"
    override val sysDeviceOfferAccept = "Accepter"
    override val sysDeviceOfferReject = "Refuser"
    override val deviceNoAnswer = "L'autre appareil n'a pas répondu, réessayez"
    override val stepPreparingFile = "Préparation du fichier"
    override val sysDeviceReceivingTitle = "Réception d'un téléchargement"
    override val sysDeviceReceiveFailed = "Échec de la réception"
    override val mainSetupTitle = "Faire de FetchJet votre gestionnaire de téléchargements"
    override val mainSetupText = "Un seul appui configure tout ce que cet appareil permet :"
    override val mainSetupTorrents = "Ouvrir les fichiers .torrent et les liens magnet avec FetchJet"
    override val mainSetupClipboard = "Surveiller le presse-papiers pour les liens"
    override val mainSetupStartup = "Lancer FetchJet à l'ouverture de session"
    override val mainSetupKeepReady = "Garder FetchJet prêt pour le navigateur"
    override val mainSetupExtension = "Ajouter l'extension du navigateur"
    override val mainSetupLater = "Vous pourrez modifier chaque point plus tard dans les paramètres."
    override val mainSetupAccept = "En faire mon gestionnaire"
    override val mainSetupGroup = "Gestionnaire principal"
    override val defaultAppRowTitle = "Application par défaut pour les torrents"
    override val defaultAppIsDefault = "FetchJet ouvre les fichiers .torrent et les liens magnet"
    override val defaultAppNotDefault = "Une autre application ouvre les fichiers .torrent ou les liens magnet"
    override val defaultAppMakeDefault = "Définir par défaut"
    override val defaultAppSystemPageHint = "Les paramètres du système vont s'ouvrir : choisissez-y FetchJet pour .torrent et magnet."
    override val mainSetupExtensionRow = "Extension du navigateur"
    override val mainSetupExtensionConnected = "Un navigateur envoie déjà les téléchargements à FetchJet"
    override val mainSetupExtensionMissing = "Aucun navigateur n'a encore l'extension"
}
