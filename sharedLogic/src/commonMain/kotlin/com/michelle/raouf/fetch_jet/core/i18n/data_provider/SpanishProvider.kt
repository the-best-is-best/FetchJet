package com.michelle.raouf.fetch_jet.core.i18n.data_provider

object SpanishProvider : AppLocalization {
    override val localeCode = "es"
    override val languageName = "Español"
    override val ok = "Aceptar"
    override val cancel = "Cancelar"
    override val close = "Cerrar"
    override val save = "Guardar"
    override val delete = "Eliminar"
    override val remove = "Quitar"
    override val open = "Abrir"
    override val copy = "Copiar"
    override val copied = "Copiado"
    override val retry = "Reintentar"
    override val pause = "Pausar"
    override val resume = "Reanudar"
    override val apply = "Aplicar"
    override val reset = "Restablecer"
    override val yes = "Sí"
    override val no = "No"
    override val back = "Atrás"
    override val done = "Listo"
    override val selectAll = "Seleccionar todo"
    override val selectNone = "No seleccionar nada"
    override val language = "Idioma"
    override val languageHint =
        "El idioma de todas las pantallas. Sistema usa el de tu dispositivo."
    override val systemLanguage = "Sistema"
    override val homeSettings = "Ajustes"
    override val homeDownloads = "Descargas"

    override val homeTabAll = "Todas"
    override val homeTabActive = "Activas"
    override val homeTabDone = "Terminadas"
    override val homeTabTorrent = "Torrent"
    override val homeTabMedia = "Multimedia"
    override val homeSidebarTypeHeader = "TIPO"
    override val homeSearchDownloadsPlaceholder = "Buscar descargas"
    override val homeAdd = "Añadir"
    override val homeAddDownload = "Añadir descarga"
    override val homePauseAll = "Pausar todo"
    override val homeResumeAll = "Reanudar todo"
    override fun homeSortBy(option: String) = "Orden: $option"
    override val homeLightTheme = "Tema claro"
    override val homeDarkTheme = "Tema oscuro"

    override fun homeUpdatingMediaTools(tool: String, percent: Int?) =
        "Actualizando herramientas multimedia · $tool" + (percent?.let { " $it%" } ?: "…")

    override val homeSearchDownloadsByNamePlaceholder = "Buscar descargas por nombre…"
    override fun homeNoDownloadsMatch(query: String) = "Ninguna descarga coincide con \"$query\""
    override val homeNoTasksInView = "No hay tareas en esta vista"
    override val homeTryAnotherName = "Prueba con otro nombre"
    override val homeAddDownloadToGetStarted = "Añade una descarga para empezar"
    override fun homeDownloadingWaitingSummary(running: Int, waiting: Int) =
        "$running descargando · $waiting en espera"

    override fun homeQueueStatus(maxAtOnce: Int) =
        "Cola: " + if (maxAtOnce <= 0) "sin límite" else "$maxAtOnce a la vez"

    override fun homeSpeedLimitStatus(limit: String?) =
        "Límite de velocidad: " + (limit ?: "desactivado")

    override val homeGroupDownloading = "Descargando"
    override val homeGroupWaiting = "En espera"
    override val homeGroupPausedFailed = "En pausa y con error"
    override val homeGroupCompleted = "Completadas"
    override fun homeSelectedCount(count: Int) =
        if (count == 1) "1 seleccionada" else "$count seleccionadas"

    override val homeExport = "Exportar"

    override fun homeStartsAt(time: String) = "Empieza $time"
    override val homeWaitingInQueue = "En cola"
    override fun homeWaitingInQueuePosition(position: Int) = "En cola (n.º $position)"
    override val homeConnecting = "Conectando…"
    override val homeStarting = "Iniciando…"
    override fun homeTimeLeft(eta: String) = "Quedan $eta"
    override fun homePausedState(percent: Int, restartsOnResume: Boolean) =
        "En pausa · $percent%" + if (restartsOnResume) " · se reinicia al reanudar" else ""

    override fun homeFailedState(message: String?) = "Error: " + (message ?: "desconocido")
    override fun homeCompletedState(size: String?) = "Completada" + (size?.let { " · $it" } ?: "")
    override val homeStartNow = "Iniciar ahora"
    override val homeColumnName = "NOMBRE"
    override val homeColumnSize = "TAMAÑO"
    override val homeColumnProgress = "PROGRESO"
    override val homeColumnSpeed = "VELOCIDAD"
    override val homeColumnLeft = "RESTANTE"
    override val homeFilterAllDownloads = "Todas las descargas"
    override val homeFilterDownloading = "Descargando"
    override val homeFilterWaiting = "En espera"
    override val homeFilterPaused = "En pausa"
    override val homeFilterCompleted = "Completadas"
    override val homeFilterFailed = "Con error"
    override val homeFilterMedia = "Vídeo y audio"
    override val homeFilterFiles = "Archivos"
    override val homeFilterTorrents = "Torrents"

    override val homeCantResumeTitle = "Esta descarga no se puede reanudar"
    override val homeCantResumeClipText =
        "Los fragmentos que se cortan al descargar no pueden continuar donde se quedaron. " +
                "Si pausas ahora, el fragmento empezará de nuevo desde el principio al reanudar."
    override val homeCantResumeServerText =
        "El servidor no permite reanudar. Si pausas ahora, la descarga " +
                "empezará de nuevo desde el principio al reanudar."
    override val homePauseAnyway = "Pausar igualmente"
    override val homeKeepDownloading = "Seguir descargando"
    override val homeMagnetLink = "Enlace magnet"
    override val homeClipboardOfferTitle = "¿Descargar el enlace que copiaste?"
    override val homeClipboardOfferHint =
        "Desactívalo en Ajustes > General > Vigilar el portapapeles."
    override val homeDownload = "Descargar"
    override val homeNotNow = "Ahora no"
    override val bridgePairTitle = "¿Permitir esta extensión del navegador?"
    override val bridgePairText =
        "Una extensión del navegador quiere enviar enlaces a FetchJet. Permítelo solo si acabas de instalar o restablecer la extensión de FetchJet."
    override val bridgePairOldText =
        "Una extensión antigua de FetchJet quiere enviar enlaces. Permítelo si es tuya; al actualizar la extensión ya no se preguntará."
    override val bridgePairAllow = "Permitir"
    override val bridgePairBlock = "Bloquear"
    override val extConnectionsGroup = "Conexiones del navegador"
    override val extConnectionsFooter =
        "Extensiones del navegador que has permitido o bloqueado para enviar enlaces a FetchJet."
    override val extAllowedLabel = "Permitida"
    override val extBlockedLabel = "Bloqueada"
    override val extRevoke = "Revocar"
    override val extUnblock = "Desbloquear"
    override val homeBrowserFallbackTitle = "¿Descargar en el navegador?"
    override val homeBrowserFallbackText =
        "Cancelaste este archivo en FetchJet. El navegador detuvo su propia descarga al " +
                "pasar el enlace, así que solo continuará si se lo devuelves."
    override val homeDownloadInBrowser = "Descargar en el navegador"
    override val homeNoDiscard = "No, descartar"

    override val homeQueueTitle = "Cola"
    override val homeQueueFooter =
        "Las descargas de más esperan y empiezan solas, la más antigua primero, cuando termina una. " +
                "\"Iniciar ahora\" en una descarga en espera se salta la cola."
    override val homeDownloadsAtSameTime = "Descargas a la vez"
    override val homeNoLimit = "Sin límite"
    override fun homeAtATime(count: Int) = "$count a la vez"

    override val homePiecesLabel = "Partes"
    override fun homeSegmentsCount(count: Int) =
        if (count == 1) "1 segmento" else "$count segmentos"

    override fun homePiecesCount(count: Int) = if (count == 1) "1 parte" else "$count partes"
    override fun homeActiveCount(count: Int) = if (count == 1) "1 activa" else "$count activas"
    override fun homeWaitingCount(count: Int) = "$count en espera"
    override fun homeDoneCount(count: Int) = if (count == 1) "1 terminada" else "$count terminadas"
    override fun homeSegmentsProgress(finished: Int, total: Int, downloading: String?) =
        "Segmentos $finished/$total" + (downloading?.let { " · descargando $it" } ?: "")

    override fun homeRetryingNote(note: String?) = "$note · reintentando"
    override fun clipStartAfterEnd(start: String, end: String) =
        "El inicio ($start) debe ser anterior al final ($end)"

    override fun clipEndExceedsVideo(end: String, duration: String) =
        "El final ($end) supera la duración del vídeo ($duration)"

    override fun clipStartExceedsVideo(duration: String) =
        "El inicio supera la duración del vídeo ($duration)"

    override val setStartTimeTitle = "Hora de inicio (desde)"
    override val setEndTimeTitle = "Hora de fin (hasta)"
    override val addDialogTitle = "Nueva descarga"
    override val addDialogSubtitle = "Introduce un enlace para empezar a descargar"
    override val addLinkLabel = "Enlace"
    override val addImportTxt = "Importar .txt"
    override val openTorrentFile = "Abrir archivo .torrent"
    override val urlPlaceholder = "Pega enlaces o magnets"
    override val addPaste = "Pegar"
    override val addDownloadEngine = "Motor de descarga"
    override val engineDirect = "Directo"
    override val engineMedia = "Multimedia"
    override val engineTorrent = "Torrent"
    override val addFileNameLabel = "Nombre del archivo"
    override val findingName = "Buscando el nombre…"
    override val saveDestination = "Carpeta de destino"
    override val quickFolderDownloads = "Descargas"
    override val quickFolderDocuments = "Documentos"
    override val quickFolderMedia = "Multimedia"
    override val quickFolderDesktop = "Escritorio"
    override val addRetryInspect = "Volver a analizar"
    override val addStarting = "Iniciando..."
    override val addInspecting = "Analizando..."
    override val addInspectionFailed = "Error al analizar"
    override val addEnterUrl = "Introduce una URL"
    override fun scheduleLinks(n: Int) =
        if (n == 1) "Programar 1 enlace" else "Programar $n enlaces"

    override fun downloadLinks(n: Int) =
        if (n == 1) "Descargar 1 enlace" else "Descargar $n enlaces"

    override val inspectVideoFirst = "Analiza el vídeo primero"
    override val inspectTorrentFirst = "Analiza el torrent primero"
    override val invalidTimeRange = "Intervalo de tiempo no válido"
    override fun scheduleVideos(n: Int) = if (n == 1) "Programar 1 vídeo" else "Programar $n vídeos"
    override fun downloadVideos(n: Int) = if (n == 1) "Descargar 1 vídeo" else "Descargar $n vídeos"
    override val addScheduleDownload = "Programar descarga"
    override val addStartDownload = "Iniciar descarga"
    override val availableAudioFormats = "Formatos de audio disponibles"
    override val availableQualities = "Calidades disponibles"
    override val subtitlesTitle = "Subtítulos"
    override val subtitlesAuto = "auto"
    override val subtitlesEmbedInVideo = "Incrustar en el vídeo"
    override val subtitlesSeparateFile = "Archivo aparte"
    override val addSelectAll = "Seleccionar todo"
    override val addClearSelection = "Borrar"
    override fun selectedOfAvailable(selected: Int, total: Int) =
        "Seleccionadas: $selected de $total disponibles"

    override fun qualityFormatLine(format: String, details: String) = "Formato: $format$details"
    override val inspectingVideoQualities = "Analizando calidades de vídeo..."
    override fun readingCourseLectures(done: Int, total: Int) =
        "Leyendo las clases del curso: ${done} / ${total}"

    override val readingCourseLecturesHint =
        "La extensión del navegador las lee en la pestaña del curso: déjala abierta"
    override val inspectingVideoQualitiesHint =
        "Buscando las resoluciones de vídeo y pistas de audio disponibles"
    override val videoInspectionFailed = "Error al analizar el vídeo"
    override val addRetryInspection = "Volver a analizar"
    override val mediaEngineStarting = "Iniciando el motor multimedia..."
    override val mediaEngineNotReady = "El motor multimedia no está listo"
    override val mediaEngineRequiresYtDlp =
        "Para extraer vídeos hace falta yt-dlp instalado o integrado."
    override val preparingEngine = "Preparando el motor..."
    override val startMediaEngine = "Descargar / iniciar el motor multimedia"
    override val inspectAudioFormats = "Analizar y elegir formatos de audio"
    override val inspectVideoQuality = "Analizar y elegir calidad de vídeo"
    override fun clipSectionTitle(isAudio: Boolean) =
        if (isAudio) "Recortar audio (opcional)" else "Recortar vídeo (opcional)"

    override val clipSectionHint = "Descarga solo un intervalo de tiempo"
    override val audioConvertTo = "Convertir a"
    override val audioConvertAllTo = "Convertir todo el audio a"
    override val audioOriginal = "Original"
    override val fileMissingTitle = "Archivo no encontrado"
    override fun fileMissingMessage(name: String) = "«$name» ya no está en este dispositivo. Puede que se haya movido, renombrado o eliminado."
    override val browserNotInstalled = "No instalado en este dispositivo"
    override val extensionConnected = "Extensión conectada"
    override val extensionNotAddedYet = "Extensión aún no añadida"
    override val addExtension = "Añadir extensión"
    override fun browserMissingTitle(name: String) = "${name} no está instalado"
    override fun browserMissingMessage(name: String) = "${name} no está en este dispositivo. Instálalo primero y luego vuelve a añadir la extensión."
    override fun downloadBrowser(name: String) = "Descargar ${name}"
    override fun loadUnpackedTitle(name: String) = "Añadir FetchJet a ${name}"
    override fun loadUnpackedSteps(name: String, page: String) = "${name} abrió su página de extensiones (${page}). Allí:\n1. Activa el modo de desarrollador.\n2. Pulsa Cargar descomprimida.\n3. Elige esta carpeta (su ruta ya está copiada, solo pégala):"
    override val loadUnpackedUpdates = "Solo una vez: después la extensión se actualiza sola con FetchJet."
    override val copyPath = "Copiar ruta"
    override fun extensionConnectedVersion(version: String) = "Extensión conectada · ${version}"
    override fun extensionNeedsUpdate(found: String, wanted: String) = "La extensión ${found} es anterior a ${wanted}: actualízala"
    override fun extensionOldVersion(wanted: String) = "Conectada con una extensión anterior a ${wanted}: actualízala"
    override val updateExtension = "Actualizar extensión"
    override fun storeHasUpdate(version: String) = "La tienda ya tiene la ${version}: el navegador la actualiza solo."
    override val updateFromAppHint = "La tienda aún no la tiene: actualiza desde la copia incluida en FetchJet."
    override fun storeWaiting(wanted: String) = "La versión ${wanted} espera la aprobación de la tienda; el navegador la actualizará solo después."
    override val openStore = "Abrir tienda"
    override val updateFromApp = "Actualizar desde FetchJet"
    override fun updateFromGitHubHint(version: String) = "La tienda aún no la tiene: FetchJet puede instalar la ${version} firmada de su versión."
    override val updateFromGitHub = "Actualizar ahora"
    override val extensionDownloadFailed = "No se pudo descargar la extensión. Revisa la conexión e inténtalo de nuevo."
    override fun installFromFileTitle(name: String) = "Instalar FetchJet en ${name}"
    override fun installFromFileSteps(name: String, file: String) = "La extensión se guardó en Downloads/${file}. En ${name}:\n1. Ajustes > Acerca de ${name}: toca el logo 5 veces (solo la primera vez).\n2. De vuelta en Ajustes: Instalar extensión desde archivo.\n3. Elige ${file} y toca Añadir."
    override fun openBrowser(name: String) = "Abrir ${name}"
    override fun mediaDurationFullRange(isAudio: Boolean, clock: String, friendly: String) =
        "Duración del ${if (isAudio) "audio" else "vídeo"}: $clock ($friendly) - completo por defecto"

    override val clipStartTimeFrom = "Inicio (desde)"
    override val clipEndTimeTo = "Fin (hasta)"
    override fun fullMediaChip(isAudio: Boolean, duration: String) =
        if (isAudio) "Audio completo ($duration)" else "Vídeo completo ($duration)"

    override val clipFirst5Min = "Primeros 5 min"
    override val clipFirst15Min = "Primeros 15 min"
    override val clipFirst30Min = "Primeros 30 min"
    override val clipFirst1Hour = "Primera hora"
    override val clipModeTitle = "Cómo descargar el fragmento"
    override val clipOnlyTitle = "Solo el fragmento"
    override val clipOnlyLine1 = "Descarga solo la parte que elegiste"
    override val clipOnlyLine2 = "Más lento: YouTube limita la velocidad"
    override val clipOnlyLine3 = "Al pausar, el fragmento empieza de nuevo"
    override fun fullThenCutTitle(isAudio: Boolean) =
        if (isAudio) "Audio completo y luego recortar" else "Vídeo completo y luego recortar"

    override fun fullThenCutLine1(isAudio: Boolean, sizeText: String) =
        if (isAudio) "Descarga todo el audio$sizeText" else "Descarga todo el vídeo$sizeText"

    override val fullThenCutLine2 = "Más rápido: velocidad máxima"
    override val fullThenCutLine3 = "Luego guarda solo tu parte"
    override val fullThenCutLine4 = "Se puede pausar y reanudar"
    override val inspectingTorrent = "Analizando los metadatos del torrent..."
    override val inspectingTorrentHint = "Obteniendo la lista de archivos y los pares"
    override val torrentInspectionFailed = "Error al analizar el torrent"
    override val inspectTorrentFiles = "Analizar archivos del torrent"
    override val disableTorrentUpload = "Desactivar la subida del torrent (solo descarga)"
    override val torrentFiles = "Archivos del torrent"
    override val addDeselectAll = "Deseleccionar todo"
    override val torrentPriorityHigh = "Alta"
    override val torrentPriorityLow = "Baja"
    override val torrentPriorityNormal = "Normal"
    override fun selectedOfLinks(selected: Int, total: Int) =
        if (total == 1) "$selected de 1 enlace" else "$selected de $total enlaces"

    override val readingTorrentFiles = "Leyendo los archivos del torrent…"
    override fun couldNotReadTorrentFiles(error: String) =
        "No se pudieron leer sus archivos ($error); se descargarán todos."

    override val linkTagVideo = "VÍDEO"
    override val linkTagAudio = "AUDIO"
    override val linkTagTorrent = "TORRENT"
    override val linkTagFile = "ARCHIVO"
    override val linkListMagnet = "Enlace magnet"
    override fun linkListYouTubeVideo(id: String) = "Vídeo de YouTube $id"
    override fun playlistSummary(videos: Int, selected: Int) =
        "Lista de reproducción · " + (if (videos == 1) "1 vídeo" else "$videos vídeos") +
                " · " + (if (selected == 1) "1 seleccionado" else "$selected seleccionados")

    override val playlistFolder = "Guardar en la carpeta"
    override val playlistFolderHint =
        "Cada archivo va a esta carpeta. Vacíala para guardarlos directamente."
    override val qualityForAllVideos = "Calidad para todos los vídeos"
    override val qualityForAllVideosHint =
        "Si un vídeo no tiene esa calidad, se elige la inferior más cercana."
    override val downloadOnlyThisVideo = "Descargar solo este vídeo"
    override val whenToStart = "Cuándo empezar"
    override val scheduleNow = "Ahora"
    override val scheduleIn30Min = "En 30 min"
    override val scheduleIn1Hour = "En 1 hora"
    override val scheduleAtTime = "A una hora…"
    override val scheduleDayLabel = "Día"
    override val scheduleToday = "Hoy"
    override val scheduleTomorrow = "Mañana"
    override val scheduleIn2Days = "En 2 días"
    override val scheduleIn3Days = "En 3 días"
    override val scheduleInAWeek = "En una semana"
    override val scheduleTimePassed = "Esa hora ya pasó hoy. Elige una hora posterior u otro día."
    override val scheduleHourLabel = "Hora"
    override val scheduleMinuteLabel = "Minuto"
    override val scheduleSet = "Fijar"
    override val scheduleSetTime = "Fijar hora"
    override fun scheduleStartsAt(time: String) = "Empieza $time (FetchJet debe estar abierto)"
    override val addConnectionsTitle = "Conexiones"
    override val connectionsAuto = "Auto"
    override fun connectionsAutoWithDefault(n: Int) = "Auto ($n)"
    override fun pickerExceedsVideo(duration: String) = "Supera la duración del vídeo ($duration)"
    override fun pickerMustBeAfterStart(start: String) = "Debe ser posterior al inicio ($start)"
    override val pickerStartZero = "Inicio (00:00)"
    override val pickerHours = "Horas"
    override val pickerMinutes = "Minutos"
    override val pickerSeconds = "Segundos"
    override fun pickerSelectedTime(time: String, summary: String) =
        "Hora elegida: $time ($summary)"

    override fun pickerEndOfVideo(time: String) = "Final del vídeo ($time)"
    override fun pickerMin(time: String) = "Mín. ($time)"
    override fun pickerApply(time: String) = "Aplicar ($time)"
    override val pickerInvalidTime = "Hora no válida"
    override val dirPickerSelectFolder = "Elegir carpeta"
    override val dirPickerUp = "Subir"
    override fun dirPickerSubfolders(n: Int) = "Subcarpetas ($n)"
    override val dirPickerHiddenOn = "Ocultas: sí"
    override val dirPickerHiddenOff = "Ocultas: no"
    override val dirPickerNewFolder = "Nueva carpeta"
    override val dirPickerFolderName = "Nombre de la carpeta…"
    override val dirPickerCreateFailed = "No se pudo crear la carpeta"
    override val dirPickerCreate = "Crear"
    override val dirPickerNoSubfolders =
        "No hay subcarpetas en esta carpeta.\nLos archivos se guardarán aquí directamente."
    override val dirPickerHiddenTag = "(oculta)"
    override val speedLimitUnlimited = "Ilimitada (máx.)"
    override val speedPresetsTitle = "Preajustes de velocidad"
    override val speedPresetsFooter =
        "Define una velocidad para Rápida, Media y Baja. Elige una rápidamente desde la cabecera; la tortuga siempre es 512 KB/s."
    override val speedPresetHigh = "Alta"
    override val speedPresetFast = "Rápida"
    override val speedPresetMedium = "Media"
    override val speedPresetLow = "Baja"
    override val speedScheduleTitle = "Programación de velocidad"
    override val speedScheduleFooter =
        "Limita la velocidad en ciertas horas; sin límite fuera de ellas."
    override val speedScheduleAdd = "Añadir una franja horaria"
    override val speedScheduleFrom = "Desde"
    override val speedScheduleTo = "Hasta"
    override val speedScheduleActive = "Límite programado activo"
    override val speedScheduleEmpty = "Aún no hay franjas horarias; añade una."
    override val priorityLabel = "Prioridad"
    override val priorityHigh = "Alta"
    override val priorityNormal = "Normal"
    override val priorityLow = "Baja"
    override val checksumLabel = "Suma de verificación (opcional)"
    override val checksumHint = "sha256 o md5"
    override val checksumVerified = "Suma de verificación correcta"
    override val checksumMismatch = "La suma de verificación no coincide"
    override val checksumChecking = "Verificando suma de verificación…"
    override val speedLimitNoLimit = "Sin límite (velocidad máx.)"
    override val speedLimitCustom = "Personalizado..."
    override val speedLimitValue = "Valor"
    override val scheduleAm = "a. m."
    override val schedulePm = "p. m."
    override val detailsChipMedia = "Multimedia"
    override val detailsChipTorrent = "Torrent"
    override val detailsChipFile = "Archivo"
    override val detailsChipResumable = "Reanudable"
    override val detailsChipNoResume = "No reanudable"
    override fun detailsClipChip(start: String, end: String?, cutAfterDownload: Boolean) =
        "Fragmento $start–${end ?: "Final"}" + if (cutAfterDownload) " · se corta tras descargar" else ""

    override fun detailsTorrentFiles(selected: Int, total: Int) =
        "Archivos del torrent ($selected seleccionados / $total)"

    override val detailsPriorityHigh = "Alta"
    override val detailsPriorityLow = "Baja"
    override val detailsPriorityNormal = "Normal"
    override val detailsStatusCompleted = "Completada"
    override val detailsStatusPaused = "En pausa"
    override val detailsStatusFailed = "Con error"
    override val detailsStatusWaiting = "En espera"
    override val detailsStatusQueued = "En cola"
    override val detailsStatusNotStarted = "Sin iniciar"
    override val detailsUnknownSize = "Desconocido"
    override val detailsMergingAudioVideo = "Uniendo audio y vídeo..."
    override fun detailsTorrentSpeedUploadDisabled(download: String) =
        "Bajada: $download (subida desactivada)"

    override fun detailsTorrentSpeed(download: String, upload: String) =
        "Bajada: $download | Subida: $upload"

    override val detailsConnecting = "Conectando..."
    override fun detailsStartsAt(time: String) = "Empieza $time"
    override fun detailsWaitingInQueuePosition(position: Int) = "En cola (n.º $position)"
    override val detailsWaitingInQueue = "En cola"
    override val detailsDownloaded = "Descargado"
    override val detailsSpeed = "Velocidad"
    override val detailsState = "Estado"
    override val detailsTimeLeft = "Tiempo restante"
    override val detailsAdded = "Añadida"
    override val detailsSavedTo = "Guardada en"
    override val detailsSource = "Origen"
    override val detailsPage = "Página"
    override val detailsOpenFile = "Abrir archivo"
    override val detailsOpenFolder = "Abrir carpeta"
    override val detailsStartNow = "Iniciar ahora"
    override val detailsRemoveFromList = "Quitar de la lista"
    override val detailsCancelDownload = "Cancelar descarga"
    override val detailsHeaderDownload = "Descarga"
    override val detailsHeaderDetails = "DETALLES"
    override fun detailsAddedToday(clock: String) = "Hoy $clock"
    override fun detailsAddedYesterday(clock: String) = "Ayer $clock"

    override fun removeDialogTitle(count: Int) =
        if (count > 1) "Quitar $count descargas" else "Quitar descarga"

    override fun removeDialogMessage(count: Int) =
        if (count > 1) "¿Seguro que quieres quitar estas $count descargas?"
        else "¿Seguro que quieres quitar esta descarga?"

    override val removeDialogAlsoDeleteFiles = "Eliminar también los archivos descargados del disco"

    override val duplicateDialogTitle = "La descarga ya existe"
    override val duplicateDialogSubtitle = "Este elemento ya está en tu cola de descargas"
    override fun duplicateDialogStatus(status: String) = "Estado: $status"
    override val duplicateDialogQuestion =
        "¿Quieres reanudar la descarga existente o empezar de cero?"
    override val duplicateDialogResumeExisting = "Reanudar la existente"
    override val duplicateDialogRedownload = "Volver a descargar (desde cero)"

    override val overwriteDialogTitle = "El archivo ya existe"
    override val overwriteDialogSubtitle = "Ya hay un archivo con este nombre en el destino"
    override val overwriteDialogFileNameLabel = "Nombre: "
    override val overwriteDialogFolderLabel = "Carpeta: "
    override val overwriteDialogNewCopyLabel = "Nueva copia: "
    override val overwriteDialogQuestion =
        "¿Quieres guardar esta descarga como una copia nueva o sobrescribir el archivo existente?"

    override fun overwriteDialogSaveAsCopy(name: String) = "Guardar como copia ($name)"
    override val overwriteDialogOverwriteExisting = "Sobrescribir el archivo"

    override val desktopWindowTagline = "Gestor de descargas rápido con varios motores"
    override val desktopTrayIdleTooltip = "FetchJet - En segundo plano"
    override val desktopTrayDownloadsHeader = "Descargas de FetchJet:"
    override fun desktopTrayMore(count: Int) = "+$count más"
    override val desktopTrayShowApp = "Mostrar FetchJet"
    override val desktopTrayCloseApp = "Cerrar FetchJet"
    override val sectionGeneral = "General"
    override val sectionGeneralBlurb = "Aspecto de la app y cómo se inicia"
    override val sectionDownloads = "Descargas"
    override val sectionDownloadsBlurb = "Dónde se guardan los archivos y cuántos a la vez"
    override val sectionBrowser = "Extensión del navegador"
    override val sectionBrowserBlurb = "Envía enlaces y vídeos del navegador a FetchJet"
    override val sectionSpeed = "Límites de velocidad"
    override val sectionSpeedBlurb = "Limita el ancho de banda que usa FetchJet"
    override val sectionMedia = "Herramientas multimedia"
    override val sectionMediaBlurb = "yt-dlp y FFmpeg para sitios de vídeo"
    override val sectionTorrent = "BitTorrent"
    override val sectionTorrentBlurb = "Pares, subida y el motor de torrent"
    override val sectionUpdates = "Actualizaciones"
    override val sectionUpdatesBlurb = "Tu versión y las nuevas"
    override val summaryNoQueueLimit = "sin límite"
    override fun summaryAtATime(n: Int) = "$n a la vez"
    override val summaryNoSpeedLimit = "Sin límite"
    override val summarySetupNeeded = "Hay que configurarlo"
    override val summaryUploadingOff = "Subida desactivada"
    override val summaryUploadingOn = "Subida activada"
    override fun summaryVersionAvailable(version: String) = "$version disponible"
    override fun summaryVersion(version: String) = "Versión $version"
    override val changesSavedAutomatically = "Los cambios se guardan automáticamente."
    override val settingsAllSettings = "Todos los ajustes"

    override val clipboardGroup = "Portapapeles"
    override val clipboardFooter =
        "Copia en cualquier sitio un enlace a un archivo (zip, mp4, pdf, apk…) o un enlace magnet y FetchJet te preguntará si quieres descargarlo."
    override val watchClipboard = "Vigilar el portapapeles"
    override val watchClipboardOn = "Activado · se ofrece descargar los enlaces a archivos copiados"
    override val settingOff = "Desactivado"
    override val alsoVideoPages = "También páginas de vídeo"
    override val alsoVideoPagesHint =
        "También se ofrecen enlaces de YouTube, Facebook, TikTok, Vimeo… (no solo archivos)"
    override val diagnosticsGroup = "Diagnóstico"
    override fun logSavedTo(path: String) =
        "Guardado: $path — envía este archivo al informar de un problema."

    override val diagnosticsFooter =
        "Guarda el registro de la app (lo que hicieron yt-dlp y los motores) en tu carpeta Descargas."
    override val saveLogToDownloads = "Guardar registro en Descargas"
    override val logCouldNotSave = "no se pudo guardar"
    override val appearanceGroup = "Apariencia"
    override val themeTitle = "Tema"
    override val themeHint = "Aspecto claro u oscuro en todas las pantallas"
    override val settingsThemeDark = "Oscuro"
    override val settingsThemeSystem = "Sistema"
    override val settingsThemeLight = "Claro"
    override val speedShownIn = "Mostrar la velocidad en"
    override val speedShownInHint =
        "Bytes (MB/s), como el tamaño de los archivos, o bits (Mbps), como tu plan de internet"

    override val saveLocationGroup = "Ubicación"
    override val saveLocationFooter =
        "Las descargas nuevas se guardan aquí, salvo que elijas otra carpeta al añadirlas."
    override val settingsFolderPath = "Ruta de la carpeta"
    override val settingsChooseFolder = "Elegir carpeta…"
    override val settingsUseDownloads = "Usar Descargas"
    override val settingsConnectionsGroup = "Conexiones"
    override val connectionsFooter =
        "En cuántas partes se divide una descarga directa. Cada parte es una conexión; cuando una termina, toma la mitad de la parte más grande que queda, así ninguna se queda parada. Auto elige según el tamaño del archivo (4–8). Algunos servidores limitan las conexiones por cliente."
    override val settingsConnectionsPerDownload = "Conexiones por descarga"
    override val settingsConnectionsAuto = "Auto"
    override val organizeGroup = "Organizar"
    override val organizeFooter =
        "Las subcarpetas se crean dentro de la ubicación: Video, Music, Archives, Documents, Programs, Images, Torrents."
    override val sortIntoFolders = "Ordenar en carpetas por tipo"
    override val sortIntoFoldersOn = "Activado · p. ej. Downloads/Video/clase.mp4"
    override val sortIntoFoldersOff = "Desactivado · todo va directamente a la carpeta"

    override val speedLimitsFooter =
        "Se aplica al instante y se reparte entre las descargas en curso."
    override val speedLimitsFooterIos =
        "Se aplica al instante y se reparte entre las descargas en curso. En iOS, las descargas HTTP que ya están en segundo plano mantienen su velocidad; el límite se aplica a las que empiecen después."
    override val speedLimitDownload = "Descarga"
    override val speedLimitUploadTorrents = "Subida (torrents)"

    override val torrentSharingGroup = "Compartir"
    override val downloadOnlyNoUpload = "Solo descargar (no subir)"
    override val downloadOnlyNoUploadHint =
        "Ahorra ancho de banda de subida y datos. Algunos trackers descargan más lento para los pares que no comparten."
    override val torrentEngineGroup = "Motor"
    override val settingsReady = "Listo"
    override val settingsNotReady = "No está listo"

    override val extInstallGroup = "Instalar"
    override val extensionInstallFooter =
        "Con la extensión aparece un botón \"Descargar con FetchJet\" en vídeos y enlaces, y el navegador puede pasar sus descargas a FetchJet. Solo se comunica con esta app en tu dispositivo."
    override val edgeAddedHint =
        "Añadida · reinicia Edge y pulsa \"Activar extensión\" cuando te lo pida"
    override val edgeFromStoreHint = "Desde Complementos de Microsoft Edge · se actualiza sola"
    override val openInEdgeStore = "Abrir en la tienda de Edge"
    override val edgeAddedNote = "Listo: reinicia Edge y te pedirá activar FetchJet."
    override val extAddToEdge = "Añadir a Edge"
    override val firefoxFromStoreHint = "Desde Complementos de Firefox · se actualiza sola"
    override val extAddToFirefox = "Añadir a Firefox"
    override val chromeZipHint =
        "No está en Chrome Web Store: instala el .zip en modo desarrollador (pasos en la página). No se actualiza sola."
    override val getTheZip = "Obtener el .zip"
    override val firefoxAndroidHint =
        "Ábrelo en Firefox en este teléfono. Los enlaces que envíes llegan directamente a esta app."
    override val keepReadyForBrowser = "Mantener lista para el navegador"
    override val keepReadyOn =
        "Las calidades de vídeo se muestran en Firefox. Mientras esté activado queda una notificación discreta."
    override val keepReadyOff =
        "Desactivado: Firefox abre FetchJet y eliges la calidad aquí. Activado: las calidades se muestran en Firefox (deja una notificación)."
    override val popUpOverBrowser = "Mostrar sobre el navegador"
    override val popUpOverBrowserOn =
        "Activado · FetchJet se abre solo cuando Firefox envía un enlace"
    override val popUpOverBrowserOff =
        "Desactivado · aparece una notificación; tócala para permitir \"mostrar sobre otras apps\""

    override val mediaToolsGroup = "Herramientas"
    override val ytDlpHint = "Encuentra el vídeo y el audio en YouTube y otros sitios"
    override val settingsMissing = "No instalado"
    override val ffmpegHint = "Une vídeo + audio (1080p o más), recorta fragmentos y crea MP3"
    override val ffmpegMissing = "No instalado · hasta 720p"
    override fun jsRuntimeTitle(name: String) = "Entorno JS · $name"
    override val jsRuntimeHint =
        "Resuelve las comprobaciones de YouTube para que aparezcan todas las calidades, más rápido"

    override fun missingPress(button: String) = "No instalado · pulsa $button"
    override val mediaToolsFolder = "Carpeta de herramientas"
    override val mediaUpdatingTools = "Actualizando…"
    override val mediaDownloadingTools = "Descargando herramientas…"
    override fun mediaDownloadingTool(name: String) = "Descargando $name…"
    override fun mediaDownloadingToolPercent(name: String, percent: Int) =
        "Descargando $name… $percent%"

    override val mediaUpdateTools = "Actualizar herramientas"
    override val mediaDownloadTheTools = "Descargar las herramientas"

    override val updateNewVersionAvailable = "Nueva versión disponible"
    override fun updateVersionLine(version: String, current: String) =
        "FetchJet $version  ·  tienes la $current"

    override val updateWhatsNew = "Novedades"
    override val updateNoReleaseNotes = "Sin notas de la versión."
    override fun downloadingUpdate(percent: Int) = "Descargando la actualización… $percent%"
    override val updateDownloadedAndroid =
        "La actualización se ha descargado. Android te pedirá confirmar la instalación; tus descargas siguen en la lista."
    override val updateDownloadedDesktop =
        "La actualización se ha descargado. FetchJet se cerrará, la instalará y volverá a abrirse. Las descargas en curso se pausan y se pueden reanudar."
    override val storeInstallsItselfOpenNow =
        "Microsoft Store la instala sola. Abre la Store para actualizar ahora."
    override val updateInstall = "Instalar"
    override val updateRestartAndUpdate = "Reiniciar y actualizar"
    override val updateDownloading = "Descargando…"
    override val updateDownloadAndInstall = "Descargar e instalar"
    override val updateOpenMicrosoftStore = "Abrir Microsoft Store"
    override fun updateDownloadWithSize(size: String) = "Descargar $size"
    override val updateOpenReleasePage = "Abrir la página de la versión"
    override val updateSkipThisVersion = "Omitir esta versión"
    override val updateLater = "Más tarde"
    override fun versionIsAvailable(version: String) = "La versión $version está disponible"
    override val youHaveLatest = "Tienes la última versión para este dispositivo"
    override val updateSeeWhatsNew = "Ver novedades"
    override val updateChecking = "Comprobando…"
    override val updateCheckNow = "Comprobar ahora"
    override val checkUpdatesAutomatically = "Buscar actualizaciones automáticamente"
    override val checkUpdatesAutomaticallyHint =
        "Busca una versión nueva al iniciar FetchJet (como mucho dos veces al día)."
    override val downloadUpdatesAutomatically = "Descargar actualizaciones automáticamente"
    override val downloadUpdatesAutomaticallyHintAndroid =
        "Descarga la nueva versión en segundo plano; solo tienes que confirmar la instalación."
    override val downloadUpdatesAutomaticallyHint =
        "Descarga la nueva versión en segundo plano; solo tienes que confirmar el reinicio."
    override val installingUpdates = "Instalar actualizaciones"
    override val installingUpdatesStore =
        "Microsoft Store instala las versiones nuevas sola; FetchJet te avisa cuando sale una."
    override val installingUpdatesManual =
        "En este dispositivo FetchJet te avisa de las versiones nuevas y te da el enlace; la instalas tú."
    override val updateReleaseNotes = "Notas de la versión"
    override val updateReleaseNotesHint = "Todas las versiones y sus cambios"
    override val installHintReleasePage = "Abre la página de la versión para descargarla."
    override val installHintApk =
        "Descarga el APK y ábrelo para instalar la actualización sobre esta versión."
    override val installHintIpa =
        "Descarga el .ipa e instálalo con AltStore / Sideloadly, como instalaste FetchJet."
    override val installHintDmg =
        "Descarga el .dmg, ábrelo y arrastra FetchJet a Aplicaciones (sustituye el anterior)."
    override val installHintDeb =
        "Descarga el .deb e instálalo (doble clic, o sudo apt install ./file.deb)."
    override val installHintOther = "Descarga la nueva versión e instálala sobre esta."

    override val startupGroup = "Inicio"
    override val startupStoreFooter =
        "La versión de la Store la inicia Windows: activa FetchJet en Configuración > Aplicaciones > Inicio."
    override val startupStartAtLogin = "Iniciar FetchJet al iniciar sesión"
    override val startupManagedByWindows = "Lo gestiona Windows"
    override val startupOpenWindowsSettings = "Abrir Configuración de Windows"
    override val startupStartHidden = "Iniciar oculto en la bandeja"
    override val startupStartHiddenHint =
        "Al iniciarse con la sesión, se queda en la bandeja en lugar de abrir la ventana."
    override val startupStartAtLoginHint =
        "Las descargas programadas y en cola continúan, y la extensión del navegador siempre encuentra la app."

    override fun startupCouldNotChange(message: String) = "No se pudo cambiar el ajuste: $message"
    override val whenDoneGroup = "Al terminar las descargas"
    override val whenDoneHint = "Solo para esta sesión. Antes tendrás 60 segundos para cancelar."
    override val whenDoneNothing = "No hacer nada"
    override val whenDoneExit = "Salir de FetchJet"
    override val whenDoneSleep = "Suspender"
    override val whenDoneShutDown = "Apagar"
    override val whenDoneTitle = "Todas las descargas terminaron"
    override fun whenDoneExitIn(seconds: Int) = "FetchJet se cerrará en ${seconds} s."
    override fun whenDoneSleepIn(seconds: Int) = "El equipo se suspenderá en ${seconds} s."
    override fun whenDoneShutDownIn(seconds: Int) = "El equipo se apagará en ${seconds} s."
    override val whenDoneNow = "Ahora"
    override fun whenDoneBanner(action: String) = "Al terminar las descargas: ${action}"
    override fun whenDoneFailed(message: String) = "No se pudo: ${message}"
    override val sectionNetwork = "Red"
    override val sectionNetworkBlurb = "Proxy e inicios de sesión de sitios"
    override val netProxyGroup = "Proxy"
    override val netProxyOff = "Desactivado"
    override val netProxyHost = "Servidor"
    override val netProxyPort = "Puerto"
    override val netProxyUser = "Usuario (opcional)"
    override val netProxyPassword = "Contraseña"
    override val netUser = "Usuario"
    override val netSave = "Guardar"
    override val netSaved = "Guardado"
    override val netProxyHint =
        "Lo usan las descargas, los vídeos (yt-dlp) y los torrents. Se aplica a las descargas iniciadas después de guardar."
    override val netProxyHintIos =
        "Lo usan las descargas y los vídeos. Los torrents en iOS se conectan directamente. Se aplica a las descargas iniciadas después de guardar."
    override val netProxyBadPort = "Introduce un servidor y un puerto del 1 al 65535"
    override val netLoginsGroup = "Inicios de sesión de sitios"
    override val netLoginsHint =
        "Para descargas que piden usuario y contraseña (HTTP basic auth). Un sitio incluye también sus subdominios."
    override val netLoginSite = "Sitio (example.com)"
    override val netLoginAdd = "Añadir"
    override val netLoginsEmpty = "No hay inicios de sesión guardados"
    override val netRemove = "Quitar"
    override fun netLoginsCount(n: Int) = "${n} inicios de sesión"
    override val sysTabAllDownloads = "Todas las descargas"
    override val sysTabActive = "Activas"
    override val sysTabCompleted = "Completadas"
    override val sysTabMediaExtractor = "Extractor multimedia"
    override val sysTabSettings = "Ajustes"
    override val sysSortNewestFirst = "Más recientes primero"
    override val sysSortOldestFirst = "Más antiguas primero"
    override val sysSortLargestFirst = "Más grandes primero"
    override val sysSortSmallestFirst = "Más pequeñas primero"
    override val sysSortNameAz = "Nombre A–Z"
    override val sysEnterValidUrlOrMagnet = "Introduce una URL o un enlace magnet válido"
    override val sysCannotStartVideoInspectionFailed =
        "No se puede iniciar la descarga: falló el análisis del vídeo."
    override val sysCannotStartTorrentInspectionFailed =
        "No se puede iniciar la descarga: falló el análisis del torrent o no hay archivos seleccionados."
    override val sysClipEndExceedsDuration = "El final del fragmento supera la duración del vídeo."
    override val sysClipStartAfterEnd = "El inicio del fragmento debe ser anterior al final."
    override fun sysStartedDownload(name: String) = "Descarga iniciada: $name"
    override val sysFailedToQueueDownload = "No se pudo poner la descarga en cola"
    override fun sysQualitiesNotAdded(count: Int) =
        if (count == 1) "No se pudo añadir 1 calidad." else "No se pudieron añadir $count calidades."

    override fun sysAddedQualityDownloads(count: Int, title: String) =
        if (count == 1) "Se añadió 1 descarga de \"$title\"" else "Se añadieron $count descargas de \"$title\""

    override val sysSelectAtLeastOnePlaylistVideo = "Selecciona al menos un vídeo de la lista."
    override fun sysVideosNotAdded(count: Int) =
        if (count == 1) "No se pudo añadir 1 vídeo." else "No se pudieron añadir $count vídeos."

    override fun sysAddedPlaylistVideos(count: Int, playlistTitle: String) =
        if (count == 1) "Se añadió 1 vídeo de \"$playlistTitle\"" else "Se añadieron $count vídeos de \"$playlistTitle\""

    override val sysSelectAtLeastOneLink = "Selecciona al menos un enlace."
    override fun sysAddedLinkDownloads(added: Int, skipped: Int) =
        (if (added == 1) "Se añadió 1 descarga" else "Se añadieron $added descargas") +
                (if (skipped > 0) " ($skipped ya estaban en la lista o fallaron)" else "")

    override val sysCouldNotReadTorrent = "No se pudo leer el torrent"
    override fun sysCouldNotStartDownload(reason: String) =
        "No se pudo iniciar la descarga: $reason"

    override val sysUnknownError = "error desconocido"
    override fun sysQueuedFromBrowser(added: Int, skipped: Int) =
        (if (added == 1) "1 descarga en cola desde el navegador" else "$added descargas en cola desde el navegador") +
                (if (skipped > 0) " ($skipped omitidas)" else "")

    override val sysEnterTorrentOrMagnetFirst = "Introduce primero un torrent o un enlace magnet"
    override val sysFailedToParseTorrentMetadata = "No se pudieron leer los metadatos del torrent"
    override val sysEnterMediaUrlFirst = "Introduce primero una URL de vídeo o audio"
    override fun sysUnableToFetchFormatsWithReason(reason: String) =
        "No se pudieron obtener los formatos del vídeo: $reason"

    override val sysUnableToFetchFormats =
        "No se pudieron obtener los formatos del vídeo. Comprueba la URL o tu conexión a internet."
    override val sysFailedToInspectMediaQualities = "No se pudieron analizar las calidades"
    override fun sysTorrentEngineError(message: String) = "Error del motor BitTorrent: $message"
    override fun sysTorrentEngineInstallFailed(message: String) =
        "No se pudo instalar el motor BitTorrent: $message"

    override val sysMediaToolsDownloaded = "¡Herramientas multimedia descargadas!"
    override val sysMediaToolsDownloadFailed =
        "No se pudieron descargar las herramientas multimedia"
    override val sysMediaToolsUpdated = "Herramientas multimedia actualizadas"
    override fun sysSavedLinks(count: Int, path: String) =
        if (count == 1) "Se guardó 1 enlace en $path" else "Se guardaron $count enlaces en $path"

    override fun sysCouldNotSaveLinks(reason: String) =
        "No se pudieron guardar los enlaces: $reason"

    override val sysErrorFallback = "error"
    override val sysDownloadFailed = "Error en la descarga"
    override val sysDownloadFailedNetwork = "Error en la descarga por un problema de red"
    override val sysCouldNotStart = "No se pudo iniciar"
    override val sysUnknownErrorCapital = "Error desconocido"
    override fun sysCouldNotSaveFileTo(dir: String) = "No se pudo guardar el archivo en $dir"
    override val sysPlaylistFallbackTitle = "Lista de reproducción"
    override val sysPlaylistQualityBest = "La mejor"
    override val sysPlaylistQualityAudioOnly = "Solo audio"
    override val sysToolsReadyYtDlpFfmpeg = "Listo: yt-dlp y ffmpeg instalados (1080p o más)"
    override val sysToolsReadyYtDlpNoFfmpeg = "Listo: yt-dlp instalado (sin ffmpeg, hasta 720p)"
    override val sysToolsNotInstalled = "No instalado. Pulsa 'Descargar las herramientas' abajo."
    override val sysToolsInstalledBuiltIn = "Instalado y listo (integrado)"
    override fun sysYtDlpVersionReady(version: String) = "yt-dlp $version (listo)"
    override val sysMediaEngineBridgeNotRegistered =
        "El puente del motor multimedia no está registrado"
    override val sysWebKitBuiltIn = "WebKit (integrado)"
    override val sysTorrentEngineBuiltIn = "Listo: motor BitTorrent integrado"
    override fun sysTorrentEngineReadyVersion(version: String) =
        "Listo: motor BitTorrent (libtorrent $version)"

    override fun sysTorrentEngineFailedToLoad(message: String) =
        "No se pudo cargar el motor BitTorrent: $message"

    override val sysTorrentEngineAvailable =
        "Listo: motor BitTorrent disponible (se carga con el primer torrent)"
    override val sysTorrentEngineMissing =
        "Falta el motor BitTorrent. Pulsa 'Descargar las herramientas' abajo."
    override val sysTorrentEngineNotAvailable =
        "El motor BitTorrent no está disponible en esta versión"

    override fun sysCouldNotStartInstaller(message: String) =
        "No se pudo iniciar el instalador: $message"

    override val sysCheckingForUpdates = "Buscando actualizaciones…"
    override val sysNoPublishedRelease = "Todavía no hay ninguna versión publicada."
    override fun sysVersionNoChangesForDevice(version: String, current: String) =
        "La versión $version no trae cambios para este dispositivo; ya tienes la última para él ($current)."

    override fun sysVersionAvailable(version: String) = "La versión $version está disponible."
    override fun sysUpToDate(current: String) = "Estás al día ($current)."
    override fun sysCouldNotCheckForUpdates(reason: String) =
        "No se pudieron buscar actualizaciones: $reason"

    override val sysNetworkError = "error de red"
    override val sysUpdateNoInstallerForSystem =
        "Error al descargar la actualización: esta versión no tiene instalador para este sistema."

    override fun sysDownloadingAsset(name: String) = "Descargando $name…"
    override fun sysVersionReadyToInstall(version: String) =
        "La versión $version está lista para instalar."

    override fun sysUpdateDownloadFailed(message: String) =
        "Error al descargar la actualización: $message"

    override val sysAllowUnknownAppsThenInstall =
        "permite \"Instalar aplicaciones desconocidas\" para FetchJet y vuelve a tocar Instalar."
    override val sysDownloadsChannelName = "Descargas de FetchJet"
    override val sysDownloadsChannelDescription =
        "Muestra el progreso de las descargas en tiempo real"
    override val sysNotificationAppTitle = "FetchJet Downloader"
    override val sysAppRunningInBackground = "La app se está ejecutando en segundo plano"
    override val sysNotificationDownloadsFallback = "Descargas"
    override fun sysFilesDownloading(count: Int) =
        if (count == 1) "1 archivo descargándose" else "$count archivos descargándose"

    override fun sysPercentActive(percent: Int, count: Int) =
        "$percent% • " + if (count == 1) "1 activa" else "$count activas"

    override fun sysActiveCount(count: Int) = if (count == 1) "1 activa" else "$count activas"
    override fun sysDownloadCompleteSize(size: String) = "Descarga completada • $size"
    override fun sysMoreDownloading(count: Int) = "+ $count más descargando"
    override val sysComplete = "Completada"
    override fun sysNotificationDownloadsTitle(speed: String) = "Descargas de FetchJet ($speed)"
    override fun sysDownloadingPercent(count: Int, percent: Int) = "$count descargando • $percent%"
    override val sysDownloadTaskFallback = "Descarga"
    override val sysBrowserReadyTitle = "FetchJet está listo para tu navegador"
    override val sysBrowserReadyText = "Las calidades de vídeo se muestran en Firefox"
    override val sysTurnOff = "Desactivar"
    override val sysBrowserReadyChannelName = "Extensión del navegador"
    override val sysBrowserReadyChannelDescription =
        "Se muestra mientras FetchJet está listo para la extensión del navegador"
    override val sysDownloadingFileFallback = "Descargando archivo"
    override val sysUnknownSize = "Desconocido"
    override val sysStatusCompleted = "Completada"
    override val sysStatusDownloading = "Descargando"
    override val sysDownloadCompleteTitle = "Descarga completada"
    override fun sysFinishedDownloading(size: String) = "Descarga terminada • $size"
    override val sysSentToFetchJet = "Enviado a FetchJet"
    override val sysLinksChannelName = "Enlaces del navegador"
    override val sysLinksChannelDescription =
        "Toca para abrir el enlace que acaba de recibir FetchJet"
    override val sysLinkReceivedTitle = "Enlace recibido"
    override val sysLinkReceivedText = "Toca para abrirlo en FetchJet"

    override val stepCompleted = "Completada"
    override val stepSeeding = "Sembrando"
    override val stepFetchingMetadata = "Obteniendo metadatos"
    override val stepConnectingToPeers = "Conectando con pares"
    override val stepCheckingFiles = "Comprobando archivos"
    override val stepCheckingResumeData = "Comprobando datos de reanudación"
    override val stepDownloading = "Descargando"
    override val stepFinished = "Finalizada"
    override val stepConnecting = "Conectando"
    override val stepPaused = "En pausa"
    override val stepError = "Error"
    override val stepResolvingStreams = "Obteniendo flujos"
    override val stepDownloadingParts = "Descargando partes"
    override val stepPreparingDownload = "Preparando la descarga"
    override val stepClippingVideo = "Recortando el vídeo"
    override val stepDownloadingMedia = "Descargando multimedia"
    override val stepAlreadyDownloaded = "Ya descargado"
    override val stepDownloadingVideo = "Descargando vídeo"
    override val stepDownloadingAudio = "Descargando audio"
    override val stepMergingAudioVideo = "Uniendo audio y vídeo"
    override val stepConvertingToMp3 = "Convirtiendo a MP3"
    override val stepCuttingClip = "Cortando el clip"
    override val stepSavingToFolder = "Guardando en la carpeta"
    override val stepFinalizingVideo = "Finalizando el vídeo"
    override val stepConvertingVideo = "Convirtiendo el vídeo"
    override val stepRepackagingVideo = "Reempaquetando el vídeo"
    override val stepYtDlpFallback = "Alternativa con yt-dlp"
    override val stepNotPlainStream = "no es un flujo simple"
    override val stepSingleConnection = "Conexión única"
    override val stepServerNoRanges = "el servidor no admite rangos"
    override val stepAgeRestrictedExpiredCookies =
        "Restricción de edad: cookies caducadas en Archivos > FetchJet"
    override val stepAgeRestrictedPlaceCookies =
        "Restricción de edad: coloca cookies.txt en Archivos > FetchJet"
    override val stepPreparingMediaEngine =
        "Preparando el motor multimedia integrado (iniciando Python, cargando yt-dlp)"
    override val stepMediaEngineNotStarted =
        "Motor multimedia no iniciado. Toca «Descargar herramientas»."

    override fun stepNumber(step: Int, total: Int) = "Paso ${step} de ${total}"
    override fun stepSavingAs(ext: String) = "Guardando como .${ext}"
    override fun stepJoiningParts(count: Int) =
        "Uniendo ${count} ${if (count == 1) "parte" else "partes"} en MP4"

    override fun stepNoDataFor(seconds: Int) = "Sin datos desde hace ${seconds} s"
    override fun stepPeers(peers: Int, seeds: Int) =
        "${peers} ${if (peers == 1) "par" else "pares"} (${seeds} ${if (seeds == 1) "semilla" else "semillas"})"

    override fun stepDownloadingFrom(host: String) = "Descargando desde ${host}"
    override fun stepMediaEngineNotReady(error: String) = "Motor multimedia no listo: ${error}"
    override fun stepTorrentEngineFailed(error: String) =
        "No se pudo iniciar el motor BitTorrent: ${error}"

    override fun stepReadyWith(details: String) = "Listo: ${details}"

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
    override val visibleToDevices = "Visible para otros dispositivos"
    override val visibleToDevicesHint = "Otros dispositivos FetchJet de esta red pueden encontrar este y enviarle descargas. Desactivado: aún puedes enviarles."
    override val sysDeviceSearch = "Buscar dispositivos"
    override val sysDeviceOthers = "Otros dispositivos"
    override val sysDeviceNoMatch = "Ningún dispositivo coincide"
    override val sectionDevices = "Dispositivos"
    override val sectionDevicesBlurb = "Envía descargas entre tus dispositivos"
    override val pairedDevicesTitle = "Dispositivos vinculados"
    override val noPairedDevices = "Aún no hay dispositivos vinculados. Vincula uno desde Enviar a dispositivo en una descarga."
    override val unpairDevice = "Desvincular"
    override val summaryVisible = "Visible"
    override val summaryHidden = "Oculto"
    override val blockDevice = "Bloquear"
    override val unblockDevice = "Desbloquear"
    override val blockedDevicesTitle = "Dispositivos bloqueados"
    override val noBlockedDevices = "No hay dispositivos bloqueados"
    override val pairRefusedBlocked = "El otro dispositivo bloqueó este"
    override val pairRefusedWait = "Espera {seconds} s antes de volver a pedírselo a este dispositivo"
    override val pairRefusedBusy = "El otro dispositivo está respondiendo otra solicitud de vinculación. Inténtalo en un momento."
    override val pairDeclined = "El otro dispositivo rechazó la vinculación. Vuelve a enviar para intentarlo de nuevo."
    override val pairExpired = "El otro dispositivo no respondió a tiempo. Vuelve a enviar para intentarlo de nuevo."
    override val sysDevicePairedLabel = "Paired"
    override val sysDeviceThisDevice = "This device"
    override val sysDeviceManualConnect = "Connect by IP"
    override val sysDeviceConnect = "Connect"
    override val sysDeviceOfferTitle = "¿Recibir una descarga?"
    override val sysDeviceOfferFrom = "De {device}"
    override val sysDeviceOfferAccept = "Aceptar"
    override val sysDeviceOfferReject = "Rechazar"
    override val deviceNoAnswer = "El otro dispositivo no respondió, inténtalo de nuevo"
    override val stepPreparingFile = "Preparando el archivo"
    override val sysDeviceReceivingTitle = "Recibiendo una descarga"
    override val sysDeviceReceiveFailed = "La recepción falló"
    override val mainSetupTitle = "Haz de FetchJet tu gestor de descargas principal"
    override val mainSetupText = "Un toque configura todo lo que este dispositivo permite:"
    override val mainSetupTorrents = "Abrir archivos .torrent y enlaces magnet con FetchJet"
    override val mainSetupClipboard = "Vigilar el portapapeles en busca de enlaces"
    override val mainSetupStartup = "Iniciar FetchJet al iniciar sesión"
    override val mainSetupKeepReady = "Mantener FetchJet listo para el navegador"
    override val mainSetupExtension = "Añadir la extensión del navegador"
    override val mainSetupLater = "Puedes cambiar cada opción más tarde en Ajustes."
    override val mainSetupAccept = "Hacerlo mi gestor de descargas"
    override val mainSetupGroup = "Gestor de descargas principal"
    override val defaultAppRowTitle = "App predeterminada para torrents"
    override val defaultAppIsDefault = "FetchJet abre los archivos .torrent y los enlaces magnet"
    override val defaultAppNotDefault = "Otra app abre los archivos .torrent o los enlaces magnet"
    override val defaultAppMakeDefault = "Hacer predeterminada"
    override val defaultAppSystemPageHint = "Se abrirá la configuración del sistema: elige FetchJet para .torrent y magnet allí."
    override val mainSetupExtensionRow = "Extensión del navegador"
    override val mainSetupExtensionConnected = "Un navegador ya envía descargas a FetchJet"
    override val mainSetupExtensionMissing = "Ningún navegador tiene la extensión todavía"
}
