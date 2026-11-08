package com.michelle.raouf.fetch_jet.core.i18n.data_provider

object PortugueseProvider : AppLocalization {
    override val localeCode = "pt"
    override val languageName = "Português"
    override val ok = "OK"
    override val cancel = "Cancelar"
    override val close = "Fechar"
    override val save = "Salvar"
    override val delete = "Excluir"
    override val remove = "Remover"
    override val open = "Abrir"
    override val copy = "Copiar"
    override val copied = "Copiado"
    override val retry = "Tentar de novo"
    override val pause = "Pausar"
    override val resume = "Retomar"
    override val apply = "Aplicar"
    override val reset = "Redefinir"
    override val yes = "Sim"
    override val no = "Não"
    override val back = "Voltar"
    override val done = "Concluído"
    override val selectAll = "Selecionar tudo"
    override val selectNone = "Desmarcar tudo"
    override val language = "Idioma"
    override val languageHint = "O idioma de todas as telas. Sistema segue o do seu dispositivo."
    override val systemLanguage = "Sistema"
    override val homeSettings = "Configurações"
    override val homeDownloads = "Downloads"

    override val homeTabAll = "Todos"
    override val homeTabActive = "Ativos"
    override val homeTabDone = "Concluídos"
    override val homeTabTorrent = "Torrent"
    override val homeTabMedia = "Mídia"
    override val homeSidebarTypeHeader = "TIPO"
    override val homeSearchDownloadsPlaceholder = "Buscar downloads"
    override val homeAdd = "Adicionar"
    override val homeAddDownload = "Adicionar download"
    override val homePauseAll = "Pausar tudo"
    override val homeResumeAll = "Retomar tudo"
    override fun homeSortBy(option: String) = "Ordem: $option"
    override val homeLightTheme = "Tema claro"
    override val homeDarkTheme = "Tema escuro"

    override fun homeUpdatingMediaTools(tool: String, percent: Int?) =
        "Atualizando ferramentas de mídia · $tool" + (percent?.let { " $it%" } ?: "…")

    override val homeSearchDownloadsByNamePlaceholder = "Buscar downloads pelo nome…"
    override fun homeNoDownloadsMatch(query: String) = "Nenhum download corresponde a \"$query\""
    override val homeNoTasksInView = "Nenhuma tarefa nesta visualização"
    override val homeTryAnotherName = "Tente outro nome"
    override val homeAddDownloadToGetStarted = "Adicione um download para começar"
    override fun homeDownloadingWaitingSummary(running: Int, waiting: Int) =
        "$running baixando · $waiting aguardando"

    override fun homeQueueStatus(maxAtOnce: Int) =
        "Fila: " + if (maxAtOnce <= 0) "sem limite" else "$maxAtOnce por vez"

    override fun homeSpeedLimitStatus(limit: String?) =
        "Limite de velocidade: " + (limit ?: "desativado")

    override val homeGroupDownloading = "Baixando"
    override val homeGroupWaiting = "Aguardando"
    override val homeGroupPausedFailed = "Pausados e com falha"
    override val homeGroupCompleted = "Concluídos"
    override fun homeSelectedCount(count: Int) =
        if (count == 1) "1 selecionado" else "$count selecionados"

    override val homeExport = "Exportar"

    override fun homeStartsAt(time: String) = "Começa $time"
    override val homeWaitingInQueue = "Na fila"
    override fun homeWaitingInQueuePosition(position: Int) = "Na fila (nº $position)"
    override val homeConnecting = "Conectando…"
    override val homeStarting = "Iniciando…"
    override fun homeTimeLeft(eta: String) = "Faltam $eta"
    override fun homePausedState(percent: Int, restartsOnResume: Boolean) =
        "Pausado · $percent%" + if (restartsOnResume) " · recomeça ao retomar" else ""

    override fun homeFailedState(message: String?) = "Falhou: " + (message ?: "erro")
    override fun homeCompletedState(size: String?) = "Concluído" + (size?.let { " · $it" } ?: "")
    override val homeStartNow = "Iniciar agora"
    override val homeColumnName = "NOME"
    override val homeColumnSize = "TAMANHO"
    override val homeColumnProgress = "PROGRESSO"
    override val homeColumnSpeed = "VELOCIDADE"
    override val homeColumnLeft = "RESTANTE"
    override val homeFilterAllDownloads = "Todos os downloads"
    override val homeFilterDownloading = "Baixando"
    override val homeFilterWaiting = "Aguardando"
    override val homeFilterPaused = "Pausados"
    override val homeFilterCompleted = "Concluídos"
    override val homeFilterFailed = "Com falha"
    override val homeFilterMedia = "Vídeo e áudio"
    override val homeFilterFiles = "Arquivos"
    override val homeFilterTorrents = "Torrents"

    override val homeCantResumeTitle = "Este download não pode ser retomado"
    override val homeCantResumeClipText =
        "Trechos cortados durante o download não continuam de onde pararam. " +
                "Se você pausar agora, o trecho recomeça do início ao retomar."
    override val homeCantResumeServerText =
        "O servidor não permite retomar. Se você pausar agora, o download " +
                "recomeça do início ao retomar."
    override val homePauseAnyway = "Pausar mesmo assim"
    override val homeKeepDownloading = "Continuar baixando"
    override val homeMagnetLink = "Link magnet"
    override val homeClipboardOfferTitle = "Baixar o link que você copiou?"
    override val homeClipboardOfferHint =
        "Desative em Configurações > Geral > Monitorar a área de transferência."
    override val homeDownload = "Baixar"
    override val homeNotNow = "Agora não"
    override val bridgePairTitle = "Permitir esta extensão do navegador?"
    override val bridgePairText =
        "Uma extensão do navegador pede para enviar links ao FetchJet. Permita apenas se você acabou de instalar ou redefinir a extensão FetchJet."
    override val bridgePairOldText =
        "Uma extensão antiga do FetchJet quer enviar links. Permita se for sua; atualizar a extensão evita esta pergunta."
    override val bridgePairAllow = "Permitir"
    override val bridgePairBlock = "Bloquear"
    override val extConnectionsGroup = "Conexões do navegador"
    override val extConnectionsFooter =
        "Extensões do navegador que você permitiu ou bloqueou de enviar links para o FetchJet."
    override val extAllowedLabel = "Permitida"
    override val extBlockedLabel = "Bloqueada"
    override val extRevoke = "Revogar"
    override val extUnblock = "Desbloquear"
    override val homeBrowserFallbackTitle = "Baixar no navegador?"
    override val homeBrowserFallbackText =
        "Você cancelou este arquivo no FetchJet. O navegador parou o próprio download ao " +
                "passar o link, então ele só continua se você o devolver."
    override val homeDownloadInBrowser = "Baixar no navegador"
    override val homeNoDiscard = "Não, descartar"

    override val homeQueueTitle = "Fila"
    override val homeQueueFooter =
        "Os downloads extras esperam e começam sozinhos, do mais antigo ao mais novo, quando um termina. " +
                "\"Iniciar agora\" em um download aguardando pula a fila."
    override val homeDownloadsAtSameTime = "Downloads ao mesmo tempo"
    override val homeNoLimit = "Sem limite"
    override fun homeAtATime(count: Int) = "$count por vez"

    override val homePiecesLabel = "Partes"
    override fun homeSegmentsCount(count: Int) =
        if (count == 1) "1 segmento" else "$count segmentos"

    override fun homePiecesCount(count: Int) = if (count == 1) "1 parte" else "$count partes"
    override fun homeActiveCount(count: Int) = if (count == 1) "1 ativa" else "$count ativas"
    override fun homeWaitingCount(count: Int) = "$count aguardando"
    override fun homeDoneCount(count: Int) = if (count == 1) "1 concluída" else "$count concluídas"
    override fun homeSegmentsProgress(finished: Int, total: Int, downloading: String?) =
        "Segmentos $finished/$total" + (downloading?.let { " · baixando $it" } ?: "")

    override fun homeRetryingNote(note: String?) = "$note · tentando de novo"
    override fun clipStartAfterEnd(start: String, end: String) =
        "O início ($start) deve ser antes do fim ($end)"

    override fun clipEndExceedsVideo(end: String, duration: String) =
        "O fim ($end) passa da duração do vídeo ($duration)"

    override fun clipStartExceedsVideo(duration: String) =
        "O início passa da duração do vídeo ($duration)"

    override val setStartTimeTitle = "Hora de início (de)"
    override val setEndTimeTitle = "Hora de fim (até)"
    override val addDialogTitle = "Novo download"
    override val addDialogSubtitle = "Insira um link para começar a baixar"
    override val addLinkLabel = "Link"
    override val addImportTxt = "Importar .txt"
    override val openTorrentFile = "Abrir arquivo .torrent"
    override val urlPlaceholder = "Cole links ou magnets"
    override val addPaste = "Colar"
    override val addDownloadEngine = "Motor de download"
    override val engineDirect = "Direto"
    override val engineMedia = "Mídia"
    override val engineTorrent = "Torrent"
    override val addFileNameLabel = "Nome do arquivo"
    override val findingName = "Buscando o nome…"
    override val saveDestination = "Pasta de destino"
    override val quickFolderDownloads = "Downloads"
    override val quickFolderDocuments = "Documentos"
    override val quickFolderMedia = "Mídia"
    override val quickFolderDesktop = "Área de trabalho"
    override val addRetryInspect = "Analisar de novo"
    override val addStarting = "Iniciando..."
    override val addInspecting = "Analisando..."
    override val addInspectionFailed = "Falha na análise"
    override val addEnterUrl = "Insira a URL"
    override fun scheduleLinks(n: Int) = if (n == 1) "Agendar 1 link" else "Agendar $n links"
    override fun downloadLinks(n: Int) = if (n == 1) "Baixar 1 link" else "Baixar $n links"
    override val inspectVideoFirst = "Analise o vídeo primeiro"
    override val inspectTorrentFirst = "Analise o torrent primeiro"
    override val invalidTimeRange = "Intervalo de tempo inválido"
    override fun scheduleVideos(n: Int) = if (n == 1) "Agendar 1 vídeo" else "Agendar $n vídeos"
    override fun downloadVideos(n: Int) = if (n == 1) "Baixar 1 vídeo" else "Baixar $n vídeos"
    override val addScheduleDownload = "Agendar download"
    override val addStartDownload = "Iniciar download"
    override val availableAudioFormats = "Formatos de áudio disponíveis"
    override val availableQualities = "Qualidades disponíveis"
    override val subtitlesTitle = "Legendas"
    override val subtitlesAuto = "auto"
    override val subtitlesEmbedInVideo = "Embutir no vídeo"
    override val subtitlesSeparateFile = "Arquivo separado"
    override val addSelectAll = "Selecionar tudo"
    override val addClearSelection = "Limpar"
    override fun selectedOfAvailable(selected: Int, total: Int) =
        "Selecionadas: $selected de $total disponíveis"

    override fun qualityFormatLine(format: String, details: String) = "Formato: $format$details"
    override val inspectingVideoQualities = "Analisando qualidades do vídeo..."
    override fun readingCourseLectures(done: Int, total: Int) =
        "Lendo as aulas do curso: ${done} / ${total}"

    override val readingCourseLecturesHint =
        "A extensão do navegador as lê na aba do curso: deixe-a aberta"
    override val inspectingVideoQualitiesHint =
        "Buscando as resoluções de vídeo e faixas de áudio disponíveis"
    override val videoInspectionFailed = "Falha ao analisar o vídeo"
    override val addRetryInspection = "Analisar de novo"
    override val mediaEngineStarting = "Iniciando o motor de mídia..."
    override val mediaEngineNotReady = "O motor de mídia não está pronto"
    override val mediaEngineRequiresYtDlp =
        "Para extrair vídeos é preciso ter o yt-dlp instalado ou embutido."
    override val preparingEngine = "Preparando o motor..."
    override val startMediaEngine = "Baixar / iniciar o motor de mídia"
    override val inspectAudioFormats = "Analisar e escolher formatos de áudio"
    override val inspectVideoQuality = "Analisar e escolher a qualidade do vídeo"
    override fun clipSectionTitle(isAudio: Boolean) =
        if (isAudio) "Cortar trecho do áudio (opcional)" else "Cortar trecho do vídeo (opcional)"

    override val clipSectionHint = "Baixe só um intervalo de tempo"
    override fun mediaDurationFullRange(isAudio: Boolean, clock: String, friendly: String) =
        "Duração do ${if (isAudio) "áudio" else "vídeo"}: $clock ($friendly) - completo por padrão"

    override val clipStartTimeFrom = "Início (de)"
    override val clipEndTimeTo = "Fim (até)"
    override fun fullMediaChip(isAudio: Boolean, duration: String) =
        if (isAudio) "Áudio completo ($duration)" else "Vídeo completo ($duration)"

    override val clipFirst5Min = "Primeiros 5 min"
    override val clipFirst15Min = "Primeiros 15 min"
    override val clipFirst30Min = "Primeiros 30 min"
    override val clipFirst1Hour = "Primeira hora"
    override val clipModeTitle = "Como baixar o trecho"
    override val clipOnlyTitle = "Só o trecho"
    override val clipOnlyLine1 = "Baixa só a parte que você escolheu"
    override val clipOnlyLine2 = "Mais lento: o YouTube limita a velocidade"
    override val clipOnlyLine3 = "Pausar faz o trecho recomeçar"
    override fun fullThenCutTitle(isAudio: Boolean) =
        if (isAudio) "Áudio completo e depois cortar" else "Vídeo completo e depois cortar"

    override fun fullThenCutLine1(isAudio: Boolean, sizeText: String) =
        if (isAudio) "Baixa o áudio inteiro$sizeText" else "Baixa o vídeo inteiro$sizeText"

    override val fullThenCutLine2 = "Mais rápido: velocidade máxima"
    override val fullThenCutLine3 = "Depois guarda só a sua parte"
    override val fullThenCutLine4 = "Pausar / retomar funciona"
    override val inspectingTorrent = "Analisando os metadados do torrent..."
    override val inspectingTorrentHint = "Buscando a lista de arquivos e os peers"
    override val torrentInspectionFailed = "Falha ao analisar o torrent"
    override val inspectTorrentFiles = "Analisar arquivos do torrent"
    override val disableTorrentUpload = "Desativar upload do torrent (só download)"
    override val torrentFiles = "Arquivos do torrent"
    override val addDeselectAll = "Desmarcar tudo"
    override val torrentPriorityHigh = "Alta"
    override val torrentPriorityLow = "Baixa"
    override val torrentPriorityNormal = "Normal"
    override fun selectedOfLinks(selected: Int, total: Int) =
        if (total == 1) "$selected de 1 link" else "$selected de $total links"

    override val readingTorrentFiles = "Lendo os arquivos do torrent…"
    override fun couldNotReadTorrentFiles(error: String) =
        "Não foi possível ler os arquivos ($error); todos serão baixados."

    override val linkTagVideo = "VÍDEO"
    override val linkTagAudio = "ÁUDIO"
    override val linkTagTorrent = "TORRENT"
    override val linkTagFile = "ARQUIVO"
    override val linkListMagnet = "Link magnet"
    override fun linkListYouTubeVideo(id: String) = "Vídeo do YouTube $id"
    override fun playlistSummary(videos: Int, selected: Int) =
        "Playlist · " + (if (videos == 1) "1 vídeo" else "$videos vídeos") +
                " · " + (if (selected == 1) "1 selecionado" else "$selected selecionados")

    override val playlistFolder = "Salvar na pasta"
    override val playlistFolderHint =
        "Cada arquivo vai para esta pasta. Apague para salvá-los diretamente."
    override val qualityForAllVideos = "Qualidade para todos os vídeos"
    override val qualityForAllVideosHint =
        "Se um vídeo não tiver essa qualidade, será usada a menor mais próxima."
    override val downloadOnlyThisVideo = "Baixar só este vídeo"
    override val whenToStart = "Quando começar"
    override val scheduleNow = "Agora"
    override val scheduleIn30Min = "Em 30 min"
    override val scheduleIn1Hour = "Em 1 hora"
    override val scheduleAtTime = "Em um horário…"
    override val scheduleDayLabel = "Dia"
    override val scheduleToday = "Hoje"
    override val scheduleTomorrow = "Amanhã"
    override val scheduleIn2Days = "Em 2 dias"
    override val scheduleIn3Days = "Em 3 dias"
    override val scheduleInAWeek = "Em uma semana"
    override val scheduleTimePassed =
        "Esse horário já passou hoje. Escolha um horário mais tarde ou outro dia."
    override val scheduleHourLabel = "Hora"
    override val scheduleMinuteLabel = "Minuto"
    override val scheduleSet = "Definir"
    override val scheduleSetTime = "Definir horário"
    override fun scheduleStartsAt(time: String) = "Começa $time (o FetchJet precisa estar aberto)"
    override val addConnectionsTitle = "Conexões"
    override val connectionsAuto = "Auto"
    override fun connectionsAutoWithDefault(n: Int) = "Auto ($n)"
    override fun pickerExceedsVideo(duration: String) = "Passa da duração do vídeo ($duration)"
    override fun pickerMustBeAfterStart(start: String) = "Deve ser depois do início ($start)"
    override val pickerStartZero = "Início (00:00)"
    override val pickerHours = "Horas"
    override val pickerMinutes = "Minutos"
    override val pickerSeconds = "Segundos"
    override fun pickerSelectedTime(time: String, summary: String) =
        "Horário escolhido: $time ($summary)"

    override fun pickerEndOfVideo(time: String) = "Fim do vídeo ($time)"
    override fun pickerMin(time: String) = "Mín. ($time)"
    override fun pickerApply(time: String) = "Aplicar ($time)"
    override val pickerInvalidTime = "Horário inválido"
    override val dirPickerSelectFolder = "Escolher pasta"
    override val dirPickerUp = "Acima"
    override fun dirPickerSubfolders(n: Int) = "Subpastas ($n)"
    override val dirPickerHiddenOn = "Ocultas: sim"
    override val dirPickerHiddenOff = "Ocultas: não"
    override val dirPickerNewFolder = "Nova pasta"
    override val dirPickerFolderName = "Nome da pasta…"
    override val dirPickerCreateFailed = "Não foi possível criar a pasta"
    override val dirPickerCreate = "Criar"
    override val dirPickerNoSubfolders =
        "Não há subpastas nesta pasta.\nOs arquivos serão salvos diretamente aqui."
    override val dirPickerHiddenTag = "(oculta)"
    override val speedLimitUnlimited = "Ilimitado (máx.)"
    override val speedPresetsTitle = "Predefinições de velocidade"
    override val speedPresetsFooter =
        "Defina uma velocidade para Rápida, Média e Baixa. Escolha uma rapidamente no cabeçalho; a tartaruga é sempre 512 KB/s."
    override val speedPresetHigh = "Alta"
    override val speedPresetFast = "Rápida"
    override val speedPresetMedium = "Média"
    override val speedPresetLow = "Baixa"
    override val speedScheduleTitle = "Agendamento de velocidade"
    override val speedScheduleFooter =
        "Limite a velocidade em certos horários; sem limite fora deles."
    override val speedScheduleAdd = "Adicionar uma faixa de horário"
    override val speedScheduleFrom = "De"
    override val speedScheduleTo = "Até"
    override val speedScheduleActive = "Limite agendado ativo"
    override val speedScheduleEmpty = "Ainda sem faixas de horário — adicione uma."
    override val priorityLabel = "Prioridade"
    override val priorityHigh = "Alta"
    override val priorityNormal = "Normal"
    override val priorityLow = "Baixa"
    override val checksumLabel = "Soma de verificação (opcional)"
    override val checksumHint = "sha256 ou md5"
    override val checksumVerified = "Soma de verificação válida"
    override val checksumMismatch = "Soma de verificação não corresponde"
    override val checksumChecking = "Verificando soma de verificação…"
    override val speedLimitNoLimit = "Sem limite (velocidade máx.)"
    override val speedLimitCustom = "Personalizado..."
    override val speedLimitValue = "Valor"
    override val scheduleAm = "AM"
    override val schedulePm = "PM"
    override val detailsChipMedia = "Mídia"
    override val detailsChipTorrent = "Torrent"
    override val detailsChipFile = "Arquivo"
    override val detailsChipResumable = "Pode retomar"
    override val detailsChipNoResume = "Sem retomada"
    override fun detailsClipChip(start: String, end: String?, cutAfterDownload: Boolean) =
        "Trecho $start–${end ?: "Fim"}" + if (cutAfterDownload) " · corta após baixar" else ""

    override fun detailsTorrentFiles(selected: Int, total: Int) =
        "Arquivos do torrent ($selected selecionados / $total)"

    override val detailsPriorityHigh = "Alta"
    override val detailsPriorityLow = "Baixa"
    override val detailsPriorityNormal = "Normal"
    override val detailsStatusCompleted = "Concluído"
    override val detailsStatusPaused = "Pausado"
    override val detailsStatusFailed = "Falhou"
    override val detailsStatusWaiting = "Aguardando"
    override val detailsStatusQueued = "Na fila"
    override val detailsStatusNotStarted = "Não iniciado"
    override val detailsUnknownSize = "Desconhecido"
    override val detailsMergingAudioVideo = "Juntando áudio e vídeo..."
    override fun detailsTorrentSpeedUploadDisabled(download: String) =
        "Down: $download (upload desativado)"

    override fun detailsTorrentSpeed(download: String, upload: String) =
        "Down: $download | Up: $upload"

    override val detailsConnecting = "Conectando..."
    override fun detailsStartsAt(time: String) = "Começa $time"
    override fun detailsWaitingInQueuePosition(position: Int) = "Na fila (nº $position)"
    override val detailsWaitingInQueue = "Na fila"
    override val detailsDownloaded = "Baixado"
    override val detailsSpeed = "Velocidade"
    override val detailsState = "Estado"
    override val detailsTimeLeft = "Tempo restante"
    override val detailsAdded = "Adicionado"
    override val detailsSavedTo = "Salvo em"
    override val detailsSource = "Origem"
    override val detailsPage = "Página"
    override val detailsOpenFile = "Abrir arquivo"
    override val detailsOpenFolder = "Abrir pasta"
    override val detailsStartNow = "Iniciar agora"
    override val detailsRemoveFromList = "Remover da lista"
    override val detailsCancelDownload = "Cancelar download"
    override val detailsHeaderDownload = "Download"
    override val detailsHeaderDetails = "DETALHES"
    override fun detailsAddedToday(clock: String) = "Hoje $clock"
    override fun detailsAddedYesterday(clock: String) = "Ontem $clock"

    override fun removeDialogTitle(count: Int) =
        if (count > 1) "Remover $count downloads" else "Remover download"

    override fun removeDialogMessage(count: Int) =
        if (count > 1) "Tem certeza de que deseja remover estes $count downloads?"
        else "Tem certeza de que deseja remover este download?"

    override val removeDialogAlsoDeleteFiles = "Excluir também os arquivos baixados do disco"

    override val duplicateDialogTitle = "O download já existe"
    override val duplicateDialogSubtitle = "Este item já está na sua fila de downloads"
    override fun duplicateDialogStatus(status: String) = "Status: $status"
    override val duplicateDialogQuestion =
        "Deseja retomar o download existente ou começar do zero?"
    override val duplicateDialogResumeExisting = "Retomar o existente"
    override val duplicateDialogRedownload = "Baixar de novo (do zero)"

    override val overwriteDialogTitle = "O arquivo já existe"
    override val overwriteDialogSubtitle = "Já existe um arquivo com este nome no destino"
    override val overwriteDialogFileNameLabel = "Nome do arquivo: "
    override val overwriteDialogFolderLabel = "Pasta: "
    override val overwriteDialogNewCopyLabel = "Nova cópia: "
    override val overwriteDialogQuestion =
        "Deseja salvar este download como uma nova cópia ou substituir o arquivo existente?"

    override fun overwriteDialogSaveAsCopy(name: String) = "Salvar como cópia ($name)"
    override val overwriteDialogOverwriteExisting = "Substituir o arquivo"

    override val desktopWindowTagline = "Gerenciador de downloads rápido com vários motores"
    override val desktopTrayIdleTooltip = "FetchJet - Em segundo plano"
    override val desktopTrayDownloadsHeader = "Downloads do FetchJet:"
    override fun desktopTrayMore(count: Int) = "+$count a mais"
    override val desktopTrayShowApp = "Mostrar FetchJet"
    override val desktopTrayCloseApp = "Fechar FetchJet"
    override val sectionGeneral = "Geral"
    override val sectionGeneralBlurb = "Aparência do app e como ele inicia"
    override val sectionDownloads = "Downloads"
    override val sectionDownloadsBlurb = "Onde os arquivos ficam e quantos rodam ao mesmo tempo"
    override val sectionBrowser = "Extensão do navegador"
    override val sectionBrowserBlurb = "Envie links e vídeos do navegador para o FetchJet"
    override val sectionSpeed = "Limites de velocidade"
    override val sectionSpeedBlurb = "Limite a banda que o FetchJet usa"
    override val sectionMedia = "Ferramentas de mídia"
    override val sectionMediaBlurb = "yt-dlp e FFmpeg para sites de vídeo"
    override val sectionTorrent = "BitTorrent"
    override val sectionTorrentBlurb = "Peers, upload e o motor de torrent"
    override val sectionUpdates = "Atualizações"
    override val sectionUpdatesBlurb = "Sua versão e as novas"
    override val summaryNoQueueLimit = "sem limite"
    override fun summaryAtATime(n: Int) = "$n por vez"
    override val summaryNoSpeedLimit = "Sem limite"
    override val summarySetupNeeded = "Precisa configurar"
    override val summaryUploadingOff = "Upload desativado"
    override val summaryUploadingOn = "Upload ativado"
    override fun summaryVersionAvailable(version: String) = "$version disponível"
    override fun summaryVersion(version: String) = "Versão $version"
    override val changesSavedAutomatically = "As alterações são salvas automaticamente."
    override val settingsAllSettings = "Todas as configurações"

    override val clipboardGroup = "Área de transferência"
    override val clipboardFooter =
        "Copie em qualquer lugar um link de arquivo (zip, mp4, pdf, apk…) ou um link magnet, e o FetchJet pergunta se você quer baixá-lo."
    override val watchClipboard = "Monitorar a área de transferência"
    override val watchClipboardOn =
        "Ativado · links de arquivos copiados são oferecidos para download"
    override val settingOff = "Desativado"
    override val alsoVideoPages = "Também páginas de vídeo"
    override val alsoVideoPagesHint =
        "Links do YouTube, Facebook, TikTok, Vimeo… também são oferecidos (não só arquivos)"
    override val diagnosticsGroup = "Diagnóstico"
    override fun logSavedTo(path: String) =
        "Salvo: $path — envie este arquivo ao relatar um problema."

    override val diagnosticsFooter =
        "Salva o log do app (o que o yt-dlp e os motores fizeram) na sua pasta Downloads."
    override val saveLogToDownloads = "Salvar log em Downloads"
    override val logCouldNotSave = "não foi possível salvar"
    override val appearanceGroup = "Aparência"
    override val themeTitle = "Tema"
    override val themeHint = "Visual claro ou escuro em todas as telas"
    override val settingsThemeDark = "Escuro"
    override val settingsThemeLight = "Claro"
    override val speedShownIn = "Mostrar velocidade em"
    override val speedShownInHint =
        "Bytes (MB/s), como o tamanho dos arquivos, ou bits (Mbps), como seu plano de internet"

    override val saveLocationGroup = "Local de salvamento"
    override val saveLocationFooter =
        "Os novos downloads vão para cá, a menos que você escolha outra pasta ao adicioná-los."
    override val settingsFolderPath = "Caminho da pasta"
    override val settingsChooseFolder = "Escolher pasta…"
    override val settingsUseDownloads = "Usar Downloads"
    override val settingsConnectionsGroup = "Conexões"
    override val connectionsFooter =
        "Em quantas partes um download direto é dividido. Cada parte é uma conexão; quando uma termina, ela pega metade da maior parte restante, então nenhuma fica parada. Auto escolhe pelo tamanho do arquivo (4–8). Alguns servidores limitam as conexões por cliente."
    override val settingsConnectionsPerDownload = "Conexões por download"
    override val settingsConnectionsAuto = "Auto"
    override val organizeGroup = "Organizar"
    override val organizeFooter =
        "As subpastas são criadas dentro do local de salvamento: Video, Music, Archives, Documents, Programs, Images, Torrents."
    override val sortIntoFolders = "Separar em pastas por tipo"
    override val sortIntoFoldersOn = "Ativado · ex.: Downloads/Video/aula.mp4"
    override val sortIntoFoldersOff = "Desativado · tudo vai direto para a pasta"

    override val speedLimitsFooter = "Aplicado na hora e dividido entre os downloads em andamento."
    override val speedLimitsFooterIos =
        "Aplicado na hora e dividido entre os downloads em andamento. No iOS, os downloads HTTP que já rodam em segundo plano mantêm a velocidade; o limite vale para os iniciados depois."
    override val speedLimitDownload = "Download"
    override val speedLimitUploadTorrents = "Upload (torrents)"

    override val torrentSharingGroup = "Compartilhamento"
    override val downloadOnlyNoUpload = "Só baixar (sem upload)"
    override val downloadOnlyNoUploadHint =
        "Economiza sua banda de upload e sua franquia de dados. Alguns trackers baixam mais devagar para peers que não compartilham."
    override val torrentEngineGroup = "Motor"
    override val settingsReady = "Pronto"
    override val settingsNotReady = "Não está pronto"

    override val extInstallGroup = "Instalar"
    override val extensionInstallFooter =
        "Com a extensão, um botão \"Baixar com o FetchJet\" aparece em vídeos e links, e o navegador pode passar os downloads dele para o FetchJet. Ela só se comunica com este app no seu dispositivo."
    override val edgeAddedHint =
        "Adicionada · reinicie o Edge e clique em \"Ativar extensão\" quando ele pedir"
    override val edgeFromStoreHint = "Dos Complementos do Microsoft Edge · atualiza sozinha"
    override val openInEdgeStore = "Abrir na loja do Edge"
    override val edgeAddedNote = "Pronto: reinicie o Edge, ele vai pedir para ativar o FetchJet."
    override val extAddToEdge = "Adicionar ao Edge"
    override val firefoxFromStoreHint = "Dos Complementos do Firefox · atualiza sozinha"
    override val extAddToFirefox = "Adicionar ao Firefox"
    override val chromeZipHint =
        "Não está na Chrome Web Store: instale o .zip no modo de desenvolvedor (passos na página). Não atualiza sozinha."
    override val getTheZip = "Baixar o .zip"
    override val firefoxAndroidHint =
        "Abra isto no Firefox deste celular. Os links que você enviar vão direto para este app."
    override val keepReadyForBrowser = "Manter pronto para o navegador"
    override val keepReadyOn =
        "As qualidades do vídeo aparecem direto no Firefox. Uma notificação discreta fica enquanto isso estiver ativado."
    override val keepReadyOff =
        "Desativado: o Firefox abre o FetchJet e você escolhe a qualidade aqui. Ativado: as qualidades aparecem no Firefox (mantém uma notificação)."
    override val popUpOverBrowser = "Abrir sobre o navegador"
    override val popUpOverBrowserOn =
        "Ativado · o FetchJet abre sozinho quando o Firefox envia um link"
    override val popUpOverBrowserOff =
        "Desativado · aparece uma notificação; toque para permitir \"sobrepor a outros apps\""

    override val mediaToolsGroup = "Ferramentas"
    override val ytDlpHint = "Encontra o vídeo e o áudio no YouTube e em outros sites"
    override val settingsMissing = "Não instalado"
    override val ffmpegHint = "Junta vídeo + áudio (1080p ou mais), corta trechos, cria MP3"
    override val ffmpegMissing = "Não instalado · até 720p"
    override fun jsRuntimeTitle(name: String) = "Runtime JS · $name"
    override val jsRuntimeHint =
        "Resolve as verificações do YouTube para que todas as qualidades apareçam, mais rápido"

    override fun missingPress(button: String) = "Não instalado · toque em $button"
    override val mediaToolsFolder = "Pasta das ferramentas"
    override val mediaUpdatingTools = "Atualizando…"
    override val mediaDownloadingTools = "Baixando ferramentas…"
    override fun mediaDownloadingTool(name: String) = "Baixando $name…"
    override fun mediaDownloadingToolPercent(name: String, percent: Int) =
        "Baixando $name… $percent%"

    override val mediaUpdateTools = "Atualizar ferramentas"
    override val mediaDownloadTheTools = "Baixar as ferramentas"

    override val updateNewVersionAvailable = "Nova versão disponível"
    override fun updateVersionLine(version: String, current: String) =
        "FetchJet $version  ·  você tem a $current"

    override val updateWhatsNew = "Novidades"
    override val updateNoReleaseNotes = "Sem notas da versão."
    override fun downloadingUpdate(percent: Int) = "Baixando a atualização… $percent%"
    override val updateDownloadedAndroid =
        "A atualização foi baixada. O Android vai pedir para confirmar a instalação; seus downloads continuam na lista."
    override val updateDownloadedDesktop =
        "A atualização foi baixada. O FetchJet vai fechar, instalar e abrir de novo. Os downloads em andamento são pausados e podem ser retomados."
    override val storeInstallsItselfOpenNow =
        "A Microsoft Store instala sozinha. Abra a Store para atualizar agora."
    override val updateInstall = "Instalar"
    override val updateRestartAndUpdate = "Reiniciar e atualizar"
    override val updateDownloading = "Baixando…"
    override val updateDownloadAndInstall = "Baixar e instalar"
    override val updateOpenMicrosoftStore = "Abrir a Microsoft Store"
    override fun updateDownloadWithSize(size: String) = "Baixar $size"
    override val updateOpenReleasePage = "Abrir a página da versão"
    override val updateSkipThisVersion = "Pular esta versão"
    override val updateLater = "Depois"
    override fun versionIsAvailable(version: String) = "A versão $version está disponível"
    override val youHaveLatest = "Você tem a versão mais recente para este dispositivo"
    override val updateSeeWhatsNew = "Ver novidades"
    override val updateChecking = "Verificando…"
    override val updateCheckNow = "Verificar agora"
    override val checkUpdatesAutomatically = "Verificar atualizações automaticamente"
    override val checkUpdatesAutomaticallyHint =
        "Procura uma nova versão quando o FetchJet inicia (no máximo duas vezes por dia)."
    override val downloadUpdatesAutomatically = "Baixar atualizações automaticamente"
    override val downloadUpdatesAutomaticallyHintAndroid =
        "Baixa a nova versão em segundo plano; você só confirma a instalação."
    override val downloadUpdatesAutomaticallyHint =
        "Baixa a nova versão em segundo plano; você só confirma a reinicialização."
    override val installingUpdates = "Instalação de atualizações"
    override val installingUpdatesStore =
        "A Microsoft Store instala as novas versões sozinha; o FetchJet avisa quando sai uma."
    override val installingUpdatesManual =
        "Neste dispositivo, o FetchJet avisa sobre uma nova versão e mostra o link; você mesmo instala."
    override val updateReleaseNotes = "Notas da versão"
    override val updateReleaseNotesHint = "Todas as versões e o que mudou"
    override val installHintReleasePage = "Abra a página da versão para baixar a nova versão."
    override val installHintApk =
        "Baixe o APK e abra-o para instalar a atualização por cima desta versão."
    override val installHintIpa =
        "Baixe o .ipa e instale com o AltStore / Sideloadly, como você instalou o FetchJet."
    override val installHintDmg =
        "Baixe o .dmg, abra e arraste o FetchJet para Aplicativos (substitua o antigo)."
    override val installHintDeb =
        "Baixe o .deb e instale (clique duas vezes, ou sudo apt install ./file.deb)."
    override val installHintOther = "Baixe a nova versão e instale por cima desta."

    override val startupGroup = "Inicialização"
    override val startupStoreFooter =
        "A versão da Store é iniciada pelo Windows: ative o FetchJet em Configurações > Aplicativos > Inicialização."
    override val startupStartAtLogin = "Iniciar o FetchJet ao entrar no sistema"
    override val startupManagedByWindows = "Gerenciado pelo Windows"
    override val startupOpenWindowsSettings = "Abrir as Configurações do Windows"
    override val startupStartHidden = "Iniciar oculto na bandeja"
    override val startupStartHiddenHint =
        "Ao iniciar com o sistema, fica na bandeja em vez de abrir a janela."
    override val startupStartAtLoginHint =
        "Os downloads agendados e na fila continuam, e a extensão do navegador sempre encontra o app."

    override fun startupCouldNotChange(message: String) =
        "Não foi possível alterar a configuração: $message"

    override val whenDoneGroup = "Quando os downloads terminarem"
    override val whenDoneHint = "Só nesta sessão. Antes você terá 60 segundos para cancelar."
    override val whenDoneNothing = "Não fazer nada"
    override val whenDoneExit = "Sair do FetchJet"
    override val whenDoneSleep = "Suspender"
    override val whenDoneShutDown = "Desligar"
    override val whenDoneTitle = "Todos os downloads terminaram"
    override fun whenDoneExitIn(seconds: Int) = "O FetchJet será fechado em ${seconds} s."
    override fun whenDoneSleepIn(seconds: Int) = "O computador será suspenso em ${seconds} s."
    override fun whenDoneShutDownIn(seconds: Int) = "O computador será desligado em ${seconds} s."
    override val whenDoneNow = "Agora"
    override fun whenDoneBanner(action: String) = "Quando os downloads terminarem: ${action}"
    override fun whenDoneFailed(message: String) = "Não foi possível: ${message}"
    override val sectionNetwork = "Rede"
    override val sectionNetworkBlurb = "Proxy e logins de sites"
    override val netProxyGroup = "Proxy"
    override val netProxyOff = "Desligado"
    override val netProxyHost = "Servidor"
    override val netProxyPort = "Porta"
    override val netProxyUser = "Usuário (opcional)"
    override val netProxyPassword = "Senha"
    override val netUser = "Usuário"
    override val netSave = "Salvar"
    override val netSaved = "Salvo"
    override val netProxyHint =
        "Usado por downloads, vídeos (yt-dlp) e torrents. Vale para downloads iniciados depois de salvar."
    override val netProxyHintIos =
        "Usado por downloads e vídeos. Torrents no iOS conectam diretamente. Vale para downloads iniciados depois de salvar."
    override val netProxyBadPort = "Informe um servidor e uma porta de 1 a 65535"
    override val netLoginsGroup = "Logins de sites"
    override val netLoginsHint =
        "Para downloads que pedem usuário e senha (HTTP basic auth). Um site também cobre seus subdomínios."
    override val netLoginSite = "Site (example.com)"
    override val netLoginAdd = "Adicionar"
    override val netLoginsEmpty = "Nenhum login salvo"
    override val netRemove = "Remover"
    override fun netLoginsCount(n: Int) = "${n} logins"
    override val sysTabAllDownloads = "Todos os downloads"
    override val sysTabActive = "Ativos"
    override val sysTabCompleted = "Concluídos"
    override val sysTabMediaExtractor = "Extrator de mídia"
    override val sysTabSettings = "Configurações"
    override val sysSortNewestFirst = "Mais recentes primeiro"
    override val sysSortOldestFirst = "Mais antigos primeiro"
    override val sysSortLargestFirst = "Maiores primeiro"
    override val sysSortSmallestFirst = "Menores primeiro"
    override val sysSortNameAz = "Nome A–Z"
    override val sysEnterValidUrlOrMagnet = "Insira uma URL ou um link magnet válido"
    override val sysCannotStartVideoInspectionFailed =
        "Não foi possível iniciar o download: a análise do vídeo falhou."
    override val sysCannotStartTorrentInspectionFailed =
        "Não foi possível iniciar o download: a análise do torrent falhou ou nenhum arquivo foi selecionado."
    override val sysClipEndExceedsDuration = "O fim do trecho passa da duração do vídeo."
    override val sysClipStartAfterEnd = "O início do trecho deve ser antes do fim."
    override fun sysStartedDownload(name: String) = "Download iniciado: $name"
    override val sysFailedToQueueDownload = "Não foi possível colocar o download na fila"
    override fun sysQualitiesNotAdded(count: Int) =
        if (count == 1) "Não foi possível adicionar 1 qualidade." else "Não foi possível adicionar $count qualidades."

    override fun sysAddedQualityDownloads(count: Int, title: String) =
        if (count == 1) "1 download de \"$title\" adicionado" else "$count downloads de \"$title\" adicionados"

    override val sysSelectAtLeastOnePlaylistVideo = "Selecione pelo menos um vídeo da playlist."
    override fun sysVideosNotAdded(count: Int) =
        if (count == 1) "Não foi possível adicionar 1 vídeo." else "Não foi possível adicionar $count vídeos."

    override fun sysAddedPlaylistVideos(count: Int, playlistTitle: String) =
        if (count == 1) "1 vídeo de \"$playlistTitle\" adicionado" else "$count vídeos de \"$playlistTitle\" adicionados"

    override val sysSelectAtLeastOneLink = "Selecione pelo menos um link."
    override fun sysAddedLinkDownloads(added: Int, skipped: Int) =
        (if (added == 1) "1 download adicionado" else "$added downloads adicionados") +
                (if (skipped > 0) " ($skipped já estavam na lista ou falharam)" else "")

    override val sysCouldNotReadTorrent = "Não foi possível ler o torrent"
    override fun sysCouldNotStartDownload(reason: String) =
        "Não foi possível iniciar o download: $reason"

    override val sysUnknownError = "erro desconhecido"
    override fun sysQueuedFromBrowser(added: Int, skipped: Int) =
        (if (added == 1) "1 download do navegador na fila" else "$added downloads do navegador na fila") +
                (if (skipped > 0) " ($skipped ignorados)" else "")

    override val sysEnterTorrentOrMagnetFirst = "Insira primeiro um torrent ou link magnet"
    override val sysFailedToParseTorrentMetadata = "Não foi possível ler os metadados do torrent"
    override val sysEnterMediaUrlFirst = "Insira primeiro uma URL de mídia"
    override fun sysUnableToFetchFormatsWithReason(reason: String) =
        "Não foi possível obter os formatos do vídeo: $reason"

    override val sysUnableToFetchFormats =
        "Não foi possível obter os formatos do vídeo. Confira a URL ou sua conexão com a internet."
    override val sysFailedToInspectMediaQualities = "Não foi possível analisar as qualidades"
    override fun sysTorrentEngineError(message: String) = "Erro no motor BitTorrent: $message"
    override fun sysTorrentEngineInstallFailed(message: String) =
        "Falha ao instalar o motor BitTorrent: $message"

    override val sysMediaToolsDownloaded = "Ferramentas de mídia baixadas!"
    override val sysMediaToolsDownloadFailed = "Falha ao baixar as ferramentas de mídia"
    override val sysMediaToolsUpdated = "Ferramentas de mídia atualizadas"
    override fun sysSavedLinks(count: Int, path: String) =
        if (count == 1) "1 link salvo em $path" else "$count links salvos em $path"

    override fun sysCouldNotSaveLinks(reason: String) = "Não foi possível salvar os links: $reason"
    override val sysErrorFallback = "erro"
    override val sysDownloadFailed = "Falha no download"
    override val sysDownloadFailedNetwork = "Falha no download por um erro de rede"
    override val sysCouldNotStart = "Não foi possível iniciar"
    override val sysUnknownErrorCapital = "Erro desconhecido"
    override fun sysCouldNotSaveFileTo(dir: String) = "Não foi possível salvar o arquivo em $dir"
    override val sysPlaylistFallbackTitle = "Playlist"
    override val sysPlaylistQualityBest = "Melhor"
    override val sysPlaylistQualityAudioOnly = "Só áudio"
    override val sysToolsReadyYtDlpFfmpeg = "Pronto: yt-dlp e ffmpeg instalados (1080p ou mais)"
    override val sysToolsReadyYtDlpNoFfmpeg = "Pronto: yt-dlp instalado (sem ffmpeg, até 720p)"
    override val sysToolsNotInstalled = "Não instalado. Toque em 'Baixar as ferramentas' abaixo."
    override val sysToolsInstalledBuiltIn = "Instalado e pronto (embutido)"
    override fun sysYtDlpVersionReady(version: String) = "yt-dlp $version (pronto)"
    override val sysMediaEngineBridgeNotRegistered = "A ponte do motor de mídia não está registrada"
    override val sysWebKitBuiltIn = "WebKit (embutido)"
    override val sysTorrentEngineBuiltIn = "Pronto: motor BitTorrent embutido"
    override fun sysTorrentEngineReadyVersion(version: String) =
        "Pronto: motor BitTorrent (libtorrent $version)"

    override fun sysTorrentEngineFailedToLoad(message: String) =
        "Falha ao carregar o motor BitTorrent: $message"

    override val sysTorrentEngineAvailable =
        "Pronto: motor BitTorrent disponível (carrega no primeiro torrent)"
    override val sysTorrentEngineMissing =
        "Motor BitTorrent ausente. Toque em 'Baixar as ferramentas' abaixo."
    override val sysTorrentEngineNotAvailable =
        "O motor BitTorrent não está disponível nesta versão"

    override fun sysCouldNotStartInstaller(message: String) =
        "Não foi possível iniciar o instalador: $message"

    override val sysCheckingForUpdates = "Verificando atualizações…"
    override val sysNoPublishedRelease = "Ainda não há versão publicada."
    override fun sysVersionNoChangesForDevice(version: String, current: String) =
        "A versão $version não traz mudanças para este dispositivo; você já tem a mais recente para ele ($current)."

    override fun sysVersionAvailable(version: String) = "A versão $version está disponível."
    override fun sysUpToDate(current: String) = "Você está atualizado ($current)."
    override fun sysCouldNotCheckForUpdates(reason: String) =
        "Não foi possível verificar atualizações: $reason"

    override val sysNetworkError = "erro de rede"
    override val sysUpdateNoInstallerForSystem =
        "Falha ao baixar a atualização: esta versão não tem instalador para este sistema."

    override fun sysDownloadingAsset(name: String) = "Baixando $name…"
    override fun sysVersionReadyToInstall(version: String) =
        "A versão $version está pronta para instalar."

    override fun sysUpdateDownloadFailed(message: String) =
        "Falha ao baixar a atualização: $message"

    override val sysAllowUnknownAppsThenInstall =
        "permita \"Instalar apps desconhecidos\" para o FetchJet e toque em Instalar de novo."
    override val sysDownloadsChannelName = "Downloads do FetchJet"
    override val sysDownloadsChannelDescription = "Mostra o progresso dos downloads em tempo real"
    override val sysNotificationAppTitle = "FetchJet Downloader"
    override val sysAppRunningInBackground = "O app está rodando em segundo plano"
    override val sysNotificationDownloadsFallback = "Downloads"
    override fun sysFilesDownloading(count: Int) =
        if (count == 1) "1 arquivo baixando" else "$count arquivos baixando"

    override fun sysPercentActive(percent: Int, count: Int) =
        "$percent% • " + if (count == 1) "1 ativo" else "$count ativos"

    override fun sysActiveCount(count: Int) = if (count == 1) "1 ativo" else "$count ativos"
    override fun sysDownloadCompleteSize(size: String) = "Download concluído • $size"
    override fun sysMoreDownloading(count: Int) = "+ $count baixando"
    override val sysComplete = "Concluído"
    override fun sysNotificationDownloadsTitle(speed: String) = "Downloads do FetchJet ($speed)"
    override fun sysDownloadingPercent(count: Int, percent: Int) = "$count baixando • $percent%"
    override val sysDownloadTaskFallback = "Download"
    override val sysBrowserReadyTitle = "O FetchJet está pronto para o seu navegador"
    override val sysBrowserReadyText = "As qualidades do vídeo aparecem direto no Firefox"
    override val sysTurnOff = "Desativar"
    override val sysBrowserReadyChannelName = "Extensão do navegador"
    override val sysBrowserReadyChannelDescription =
        "Mostrada enquanto o FetchJet fica pronto para a extensão do navegador"
    override val sysDownloadingFileFallback = "Baixando arquivo"
    override val sysUnknownSize = "Desconhecido"
    override val sysStatusCompleted = "Concluído"
    override val sysStatusDownloading = "Baixando"
    override val sysDownloadCompleteTitle = "Download concluído"
    override fun sysFinishedDownloading(size: String) = "Download concluído • $size"
    override val sysSentToFetchJet = "Enviado ao FetchJet"
    override val sysLinksChannelName = "Links do navegador"
    override val sysLinksChannelDescription =
        "Toque para abrir o link que o FetchJet acabou de receber"
    override val sysLinkReceivedTitle = "Link recebido"
    override val sysLinkReceivedText = "Toque para abrir no FetchJet"

    override val stepCompleted = "Concluído"
    override val stepSeeding = "Semeando"
    override val stepFetchingMetadata = "Obtendo metadados"
    override val stepConnectingToPeers = "Conectando aos pares"
    override val stepCheckingFiles = "Verificando arquivos"
    override val stepCheckingResumeData = "Verificando dados de retomada"
    override val stepDownloading = "Baixando"
    override val stepFinished = "Finalizado"
    override val stepConnecting = "Conectando"
    override val stepPaused = "Pausado"
    override val stepError = "Erro"
    override val stepResolvingStreams = "Obtendo fluxos"
    override val stepDownloadingParts = "Baixando partes"
    override val stepPreparingDownload = "Preparando o download"
    override val stepClippingVideo = "Recortando o vídeo"
    override val stepDownloadingMedia = "Baixando mídia"
    override val stepAlreadyDownloaded = "Já baixado"
    override val stepDownloadingVideo = "Baixando vídeo"
    override val stepDownloadingAudio = "Baixando áudio"
    override val stepMergingAudioVideo = "Juntando áudio e vídeo"
    override val stepConvertingToMp3 = "Convertendo para MP3"
    override val stepCuttingClip = "Cortando o clipe"
    override val stepSavingToFolder = "Salvando na pasta"
    override val stepFinalizingVideo = "Finalizando o vídeo"
    override val stepConvertingVideo = "Convertendo o vídeo"
    override val stepRepackagingVideo = "Reempacotando o vídeo"
    override val stepYtDlpFallback = "Alternativa com yt-dlp"
    override val stepNotPlainStream = "não é um fluxo simples"
    override val stepSingleConnection = "Conexão única"
    override val stepServerNoRanges = "o servidor não suporta intervalos"
    override val stepAgeRestrictedExpiredCookies =
        "Restrição de idade: cookies expirados em Arquivos > FetchJet"
    override val stepAgeRestrictedPlaceCookies =
        "Restrição de idade: coloque cookies.txt em Arquivos > FetchJet"
    override val stepPreparingMediaEngine =
        "Preparando o mecanismo de mídia do app (iniciando Python, carregando yt-dlp)"
    override val stepMediaEngineNotStarted =
        "Mecanismo de mídia não iniciado. Toque em \"Baixar ferramentas\"."

    override fun stepNumber(step: Int, total: Int) = "Etapa ${step} de ${total}"
    override fun stepSavingAs(ext: String) = "Salvando como .${ext}"
    override fun stepJoiningParts(count: Int) =
        "Juntando ${count} ${if (count == 1) "parte" else "partes"} em MP4"

    override fun stepNoDataFor(seconds: Int) = "Sem dados há ${seconds} s"
    override fun stepPeers(peers: Int, seeds: Int) =
        "${peers} ${if (peers == 1) "par" else "pares"} (${seeds} ${if (seeds == 1) "semente" else "sementes"})"

    override fun stepDownloadingFrom(host: String) = "Baixando de ${host}"
    override fun stepMediaEngineNotReady(error: String) =
        "Mecanismo de mídia não está pronto: ${error}"

    override fun stepTorrentEngineFailed(error: String) =
        "Falha ao iniciar o mecanismo BitTorrent: ${error}"

    override fun stepReadyWith(details: String) = "Pronto: ${details}"

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
    override val visibleToDevices = "Visível para outros dispositivos"
    override val visibleToDevicesHint = "Outros dispositivos FetchJet nesta rede podem encontrar este e enviar downloads para ele. Desligado: você ainda pode enviar para eles."
    override val sysDeviceSearch = "Pesquisar dispositivos"
    override val sysDeviceOthers = "Outros dispositivos"
    override val sysDeviceNoMatch = "Nenhum dispositivo corresponde"
    override val sectionDevices = "Dispositivos"
    override val sectionDevicesBlurb = "Envie downloads entre seus dispositivos"
    override val pairedDevicesTitle = "Dispositivos pareados"
    override val noPairedDevices = "Nenhum dispositivo pareado ainda. Pareie um em Enviar para dispositivo num download."
    override val unpairDevice = "Desparear"
    override val summaryVisible = "Visível"
    override val summaryHidden = "Oculto"
    override val blockDevice = "Bloquear"
    override val unblockDevice = "Desbloquear"
    override val blockedDevicesTitle = "Dispositivos bloqueados"
    override val noBlockedDevices = "Nenhum dispositivo bloqueado"
    override val pairRefusedBlocked = "O outro dispositivo bloqueou este"
    override val pairRefusedWait = "Aguarde {seconds} s antes de pedir de novo a este dispositivo"
    override val pairRefusedBusy = "O outro dispositivo está respondendo outro pedido de pareamento. Tente de novo em instantes."
    override val sysDevicePairedLabel = "Paired"
    override val sysDeviceThisDevice = "This device"
    override val sysDeviceManualConnect = "Connect by IP"
    override val sysDeviceConnect = "Connect"
}
