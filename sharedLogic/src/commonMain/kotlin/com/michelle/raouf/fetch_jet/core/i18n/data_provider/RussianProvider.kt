package com.michelle.raouf.fetch_jet.core.i18n.data_provider

object RussianProvider : AppLocalization {
    override val localeCode = "ru"
    override val languageName = "Русский"
    override val ok = "OK"
    override val cancel = "Отмена"
    override val close = "Закрыть"
    override val save = "Сохранить"
    override val delete = "Удалить"
    override val remove = "Убрать"
    override val open = "Открыть"
    override val copy = "Копировать"
    override val copied = "Скопировано"
    override val retry = "Повторить"
    override val pause = "Пауза"
    override val resume = "Продолжить"
    override val apply = "Применить"
    override val reset = "Сбросить"
    override val yes = "Да"
    override val no = "Нет"
    override val back = "Назад"
    override val done = "Готово"
    override val selectAll = "Выбрать все"
    override val selectNone = "Снять выбор"
    override val language = "Язык"
    override val languageHint = "Язык всех экранов. «Системный» — как на устройстве."
    override val systemLanguage = "Системный"
    override val homeSettings = "Настройки"
    override val homeDownloads = "Загрузки"

    override val homeTabAll = "Все"
    override val homeTabActive = "Активные"
    override val homeTabDone = "Готовые"
    override val homeTabTorrent = "Торренты"
    override val homeTabMedia = "Медиа"
    override val homeSidebarTypeHeader = "ТИП"
    override val homeSearchDownloadsPlaceholder = "Поиск загрузок"
    override val homeAdd = "Добавить"
    override val homeAddDownload = "Добавить загрузку"
    override val homePauseAll = "Приостановить все"
    override val homeResumeAll = "Продолжить все"
    override fun homeSortBy(option: String) = "Сортировка: $option"
    override val homeLightTheme = "Светлая тема"
    override val homeDarkTheme = "Тёмная тема"

    override fun homeUpdatingMediaTools(tool: String, percent: Int?) =
        "Обновление медиаинструментов · $tool" + (percent?.let { " $it%" } ?: "…")

    override val homeSearchDownloadsByNamePlaceholder = "Поиск загрузок по имени…"
    override fun homeNoDownloadsMatch(query: String) = "Нет загрузок по запросу «$query»"
    override val homeNoTasksInView = "Здесь пока нет загрузок"
    override val homeTryAnotherName = "Попробуйте другое имя"
    override val homeAddDownloadToGetStarted = "Добавьте загрузку, чтобы начать"
    override fun homeDownloadingWaitingSummary(running: Int, waiting: Int) =
        "Загружается: $running · в очереди: $waiting"

    override fun homeQueueStatus(maxAtOnce: Int) =
        "Очередь: " + if (maxAtOnce <= 0) "без ограничений" else "по $maxAtOnce одновременно"

    override fun homeSpeedLimitStatus(limit: String?) =
        "Ограничение скорости: " + (limit ?: "выкл.")

    override val homeGroupDownloading = "Загружаются"
    override val homeGroupWaiting = "В очереди"
    override val homeGroupPausedFailed = "Приостановленные и с ошибкой"
    override val homeGroupCompleted = "Завершённые"
    override fun homeSelectedCount(count: Int) = "Выбрано: $count"
    override val homeExport = "Экспорт"

    override fun homeStartsAt(time: String) = "Начало: $time"
    override val homeWaitingInQueue = "В очереди"
    override fun homeWaitingInQueuePosition(position: Int) = "В очереди (№$position)"
    override val homeConnecting = "Подключение…"
    override val homeStarting = "Запуск…"
    override fun homeTimeLeft(eta: String) = "Осталось $eta"
    override fun homePausedState(percent: Int, restartsOnResume: Boolean) =
        "Пауза · $percent%" + if (restartsOnResume) " · при продолжении начнётся заново" else ""

    override fun homeFailedState(message: String?) = "Ошибка: " + (message ?: "сбой")
    override fun homeCompletedState(size: String?) = "Завершено" + (size?.let { " · $it" } ?: "")
    override val homeStartNow = "Начать сейчас"
    override val homeColumnName = "ИМЯ"
    override val homeColumnSize = "РАЗМЕР"
    override val homeColumnProgress = "ПРОГРЕСС"
    override val homeColumnSpeed = "СКОРОСТЬ"
    override val homeColumnLeft = "ОСТАЛОСЬ"
    override val homeFilterAllDownloads = "Все загрузки"
    override val homeFilterDownloading = "Загружаются"
    override val homeFilterWaiting = "В очереди"
    override val homeFilterPaused = "Приостановлены"
    override val homeFilterCompleted = "Завершены"
    override val homeFilterFailed = "С ошибкой"
    override val homeFilterMedia = "Видео и аудио"
    override val homeFilterFiles = "Файлы"
    override val homeFilterTorrents = "Торренты"

    override val homeCantResumeTitle = "Эту загрузку нельзя продолжить"
    override val homeCantResumeClipText =
        "Фрагменты, которые вырезаются во время загрузки, не могут продолжиться с места остановки. " +
                "Если поставить на паузу сейчас, при продолжении фрагмент начнётся с начала."
    override val homeCantResumeServerText =
        "Сервер не поддерживает докачку. Если поставить на паузу сейчас, при продолжении " +
                "загрузка начнётся с начала."
    override val homePauseAnyway = "Всё равно приостановить"
    override val homeKeepDownloading = "Продолжить загрузку"
    override val homeMagnetLink = "Magnet-ссылка"
    override val homeClipboardOfferTitle = "Скачать скопированную ссылку?"
    override val homeClipboardOfferHint =
        "Это можно отключить: Настройки > Общие > Следить за буфером обмена."
    override val homeDownload = "Скачать"
    override val homeNotNow = "Не сейчас"
    override val bridgePairTitle = "Разрешить это расширение браузера?"
    override val bridgePairText =
        "Расширение браузера хочет отправлять ссылки в FetchJet. Разрешайте, только если вы только что установили или сбросили расширение FetchJet."
    override val bridgePairOldText =
        "Старое расширение FetchJet хочет отправлять ссылки. Разрешите, если оно ваше; после обновления расширения этот вопрос больше не появится."
    override val bridgePairAllow = "Разрешить"
    override val bridgePairBlock = "Заблокировать"
    override val extConnectionsGroup = "Подключения браузера"
    override val extConnectionsFooter =
        "Расширения браузера, которым вы разрешили или запретили отправлять ссылки в FetchJet."
    override val extAllowedLabel = "Разрешено"
    override val extBlockedLabel = "Заблокировано"
    override val extRevoke = "Отозвать"
    override val extUnblock = "Разблокировать"
    override val homeBrowserFallbackTitle = "Скачать в браузере?"
    override val homeBrowserFallbackText =
        "Вы отменили этот файл в FetchJet. Браузер остановил свою загрузку, когда " +
                "передал ссылку, поэтому она продолжится, только если вернуть её браузеру."
    override val homeDownloadInBrowser = "Скачать в браузере"
    override val homeNoDiscard = "Нет, отменить"

    override val homeQueueTitle = "Очередь"
    override val homeQueueFooter =
        "Лишние загрузки ждут и запускаются сами, начиная со старых, когда одна завершится. " +
                "«Начать сейчас» на ожидающей загрузке запускает её вне очереди."
    override val homeDownloadsAtSameTime = "Одновременных загрузок"
    override val homeNoLimit = "Без ограничений"
    override fun homeAtATime(count: Int) = "$count одновременно"

    override val homePiecesLabel = "Части"
    override fun homeSegmentsCount(count: Int) =
        "$count " + ruPlural(count, "сегмент", "сегмента", "сегментов")

    override fun homePiecesCount(count: Int) =
        "$count " + ruPlural(count, "часть", "части", "частей")

    override fun homeActiveCount(count: Int) = "Активно: $count"
    override fun homeWaitingCount(count: Int) = "Ожидает: $count"
    override fun homeDoneCount(count: Int) = "Готово: $count"
    override fun homeSegmentsProgress(finished: Int, total: Int, downloading: String?) =
        "Сегменты $finished/$total" + (downloading?.let { " · загружается $it" } ?: "")

    override fun homeRetryingNote(note: String?) = "$note · повтор"
    override fun clipStartAfterEnd(start: String, end: String) =
        "Начало ($start) должно быть раньше конца ($end)"

    override fun clipEndExceedsVideo(end: String, duration: String) =
        "Конец ($end) превышает длительность видео ($duration)"

    override fun clipStartExceedsVideo(duration: String) =
        "Начало превышает длительность видео ($duration)"

    override val setStartTimeTitle = "Начало (с)"
    override val setEndTimeTitle = "Конец (по)"
    override val addDialogTitle = "Новая загрузка"
    override val addDialogSubtitle = "Вставьте ссылку, чтобы начать загрузку"
    override val addLinkLabel = "Ссылка"
    override val addImportTxt = "Импорт .txt"
    override val openTorrentFile = "Открыть файл .torrent"
    override val urlPlaceholder = "Вставьте ссылки или magnet"
    override val addPaste = "Вставить"
    override val addDownloadEngine = "Способ загрузки"
    override val engineDirect = "Прямая"
    override val engineMedia = "Медиа"
    override val engineTorrent = "Торрент"
    override val addFileNameLabel = "Имя файла"
    override val findingName = "Определяем имя…"
    override val saveDestination = "Папка для сохранения"
    override val quickFolderDownloads = "Загрузки"
    override val quickFolderDocuments = "Документы"
    override val quickFolderMedia = "Медиа"
    override val quickFolderDesktop = "Рабочий стол"
    override val addRetryInspect = "Проверить снова"
    override val addStarting = "Запуск..."
    override val addInspecting = "Проверка..."
    override val addInspectionFailed = "Не удалось проверить"
    override val addEnterUrl = "Введите URL"
    override fun scheduleLinks(n: Int) =
        "Запланировать $n " + ruPlural(n, "ссылку", "ссылки", "ссылок")

    override fun downloadLinks(n: Int) = "Скачать $n " + ruPlural(n, "ссылку", "ссылки", "ссылок")
    override val inspectVideoFirst = "Сначала проверьте видео"
    override val inspectTorrentFirst = "Сначала проверьте торрент"
    override val invalidTimeRange = "Неверный интервал"
    override fun scheduleVideos(n: Int) = "Запланировать $n видео"
    override fun downloadVideos(n: Int) = "Скачать $n видео"
    override val addScheduleDownload = "Запланировать загрузку"
    override val addStartDownload = "Начать загрузку"
    override val availableAudioFormats = "Доступные аудиоформаты"
    override val availableQualities = "Доступное качество"
    override val subtitlesTitle = "Субтитры"
    override val subtitlesAuto = "авто"
    override val subtitlesEmbedInVideo = "Встроить в видео"
    override val subtitlesSeparateFile = "Отдельный файл"
    override val addSelectAll = "Выбрать все"
    override val addClearSelection = "Сбросить"
    override fun selectedOfAvailable(selected: Int, total: Int) = "Выбрано: $selected из $total"
    override fun qualityFormatLine(format: String, details: String) = "Формат: $format$details"
    override val inspectingVideoQualities = "Получаем варианты качества..."
    override fun readingCourseLectures(done: Int, total: Int) =
        "Чтение лекций курса: ${done} / ${total}"

    override val readingCourseLecturesHint =
        "Расширение браузера читает их во вкладке курса: не закрывайте её"
    override val inspectingVideoQualitiesHint = "Ищем доступные разрешения видео и аудиодорожки"
    override val videoInspectionFailed = "Не удалось проверить видео"
    override val addRetryInspection = "Проверить снова"
    override val mediaEngineStarting = "Запуск медиадвижка..."
    override val mediaEngineNotReady = "Медиадвижок не готов"
    override val mediaEngineRequiresYtDlp =
        "Для извлечения видео нужен yt-dlp (установленный или встроенный)."
    override val preparingEngine = "Подготовка движка..."
    override val startMediaEngine = "Скачать / запустить медиадвижок"
    override val inspectAudioFormats = "Проверить и выбрать аудиоформат"
    override val inspectVideoQuality = "Проверить и выбрать качество видео"
    override fun clipSectionTitle(isAudio: Boolean) =
        if (isAudio) "Вырезать фрагмент аудио (необязательно)" else "Вырезать фрагмент видео (необязательно)"

    override val clipSectionHint = "Скачать только заданный отрезок времени"
    override fun mediaDurationFullRange(isAudio: Boolean, clock: String, friendly: String) =
        "Длительность ${if (isAudio) "аудио" else "видео"}: $clock ($friendly) - по умолчанию выбрано целиком"

    override val clipStartTimeFrom = "Начало (с)"
    override val clipEndTimeTo = "Конец (по)"
    override fun fullMediaChip(isAudio: Boolean, duration: String) =
        if (isAudio) "Всё аудио ($duration)" else "Всё видео ($duration)"

    override val clipFirst5Min = "Первые 5 мин"
    override val clipFirst15Min = "Первые 15 мин"
    override val clipFirst30Min = "Первые 30 мин"
    override val clipFirst1Hour = "Первый час"
    override val clipModeTitle = "Как скачать фрагмент"
    override val clipOnlyTitle = "Только фрагмент"
    override val clipOnlyLine1 = "Скачивает только выбранную часть"
    override val clipOnlyLine2 = "Медленнее: YouTube ограничивает скорость"
    override val clipOnlyLine3 = "После паузы фрагмент начнётся заново"
    override fun fullThenCutTitle(isAudio: Boolean) =
        if (isAudio) "Всё аудио, затем обрезать" else "Всё видео, затем обрезать"

    override fun fullThenCutLine1(isAudio: Boolean, sizeText: String) =
        if (isAudio) "Скачивает всё аудио$sizeText" else "Скачивает всё видео$sizeText"

    override val fullThenCutLine2 = "Быстрее: полная скорость"
    override val fullThenCutLine3 = "Затем оставляет только вашу часть"
    override val fullThenCutLine4 = "Пауза и продолжение работают"
    override val inspectingTorrent = "Получаем метаданные торрента..."
    override val inspectingTorrentHint = "Загружаем список файлов и пиров"
    override val torrentInspectionFailed = "Не удалось проверить торрент"
    override val inspectTorrentFiles = "Показать файлы торрента"
    override val disableTorrentUpload = "Отключить отдачу (только скачивание)"
    override val torrentFiles = "Файлы торрента"
    override val addDeselectAll = "Снять выбор"
    override val torrentPriorityHigh = "Высокий"
    override val torrentPriorityLow = "Низкий"
    override val torrentPriorityNormal = "Обычный"
    override fun selectedOfLinks(selected: Int, total: Int) =
        "$selected из $total " + ruPlural(total, "ссылки", "ссылок", "ссылок")

    override val readingTorrentFiles = "Читаем файлы торрента…"
    override fun couldNotReadTorrentFiles(error: String) =
        "Не удалось прочитать список файлов ($error); будут скачаны все."

    override val linkTagVideo = "ВИДЕО"
    override val linkTagAudio = "АУДИО"
    override val linkTagTorrent = "ТОРРЕНТ"
    override val linkTagFile = "ФАЙЛ"
    override val linkListMagnet = "Magnet-ссылка"
    override fun linkListYouTubeVideo(id: String) = "Видео YouTube $id"
    override fun playlistSummary(videos: Int, selected: Int) =
        "Плейлист · $videos видео · выбрано $selected"

    override val playlistFolder = "Сохранить в папку"
    override val playlistFolderHint =
        "Все файлы попадут в эту папку. Очистите поле, чтобы сохранить их напрямую."
    override val qualityForAllVideos = "Качество для всех видео"
    override val qualityForAllVideosHint =
        "Если у видео нет такого качества, берётся ближайшее более низкое."
    override val downloadOnlyThisVideo = "Скачать только это видео"
    override val whenToStart = "Когда начать"
    override val scheduleNow = "Сейчас"
    override val scheduleIn30Min = "Через 30 мин"
    override val scheduleIn1Hour = "Через 1 час"
    override val scheduleAtTime = "В определённое время…"
    override val scheduleDayLabel = "День"
    override val scheduleToday = "Сегодня"
    override val scheduleTomorrow = "Завтра"
    override val scheduleIn2Days = "Через 2 дня"
    override val scheduleIn3Days = "Через 3 дня"
    override val scheduleInAWeek = "Через неделю"
    override val scheduleTimePassed =
        "Это время сегодня уже прошло. Выберите время позже или другой день."
    override val scheduleHourLabel = "Час"
    override val scheduleMinuteLabel = "Минута"
    override val scheduleSet = "Готово"
    override val scheduleSetTime = "Задать время"
    override fun scheduleStartsAt(time: String) = "Начало: $time (FetchJet должен быть запущен)"
    override val addConnectionsTitle = "Соединения"
    override val connectionsAuto = "Авто"
    override fun connectionsAutoWithDefault(n: Int) = "Авто ($n)"
    override fun pickerExceedsVideo(duration: String) = "Больше длительности видео ($duration)"
    override fun pickerMustBeAfterStart(start: String) = "Должно быть позже начала ($start)"
    override val pickerStartZero = "Начало (00:00)"
    override val pickerHours = "Часы"
    override val pickerMinutes = "Минуты"
    override val pickerSeconds = "Секунды"
    override fun pickerSelectedTime(time: String, summary: String) = "Выбрано: $time ($summary)"
    override fun pickerEndOfVideo(time: String) = "Конец видео ($time)"
    override fun pickerMin(time: String) = "Мин. ($time)"
    override fun pickerApply(time: String) = "Применить ($time)"
    override val pickerInvalidTime = "Неверное время"
    override val dirPickerSelectFolder = "Выбор папки"
    override val dirPickerUp = "Вверх"
    override fun dirPickerSubfolders(n: Int) = "Вложенные папки ($n)"
    override val dirPickerHiddenOn = "Скрытые: вкл."
    override val dirPickerHiddenOff = "Скрытые: выкл."
    override val dirPickerNewFolder = "Новая папка"
    override val dirPickerFolderName = "Имя папки…"
    override val dirPickerCreateFailed = "Не удалось создать папку"
    override val dirPickerCreate = "Создать"
    override val dirPickerNoSubfolders =
        "В этой папке нет вложенных папок.\nФайлы будут сохранены прямо сюда."
    override val dirPickerHiddenTag = "(скрытая)"
    override val speedLimitUnlimited = "Без ограничений (макс.)"
    override val speedPresetsTitle = "Предустановки скорости"
    override val speedPresetsFooter =
        "Задайте скорость для «Быстрая», «Средняя» и «Низкая». Быстро выберите одну в шапке; черепаха всегда 512 КБ/с."
    override val speedPresetHigh = "Высокая"
    override val speedPresetFast = "Быстрая"
    override val speedPresetMedium = "Средняя"
    override val speedPresetLow = "Низкая"
    override val speedScheduleTitle = "Расписание скорости"
    override val speedScheduleFooter =
        "Ограничивайте скорость в заданное время; без лимита вне его."
    override val speedScheduleAdd = "Добавить интервал времени"
    override val speedScheduleFrom = "С"
    override val speedScheduleTo = "До"
    override val speedScheduleActive = "Запланированный лимит активен"
    override val speedScheduleEmpty = "Пока нет интервалов — добавьте один."
    override val priorityLabel = "Приоритет"
    override val priorityHigh = "Высокий"
    override val priorityNormal = "Обычный"
    override val priorityLow = "Низкий"
    override val checksumLabel = "Контрольная сумма (необязательно)"
    override val checksumHint = "sha256 или md5"
    override val checksumVerified = "Контрольная сумма совпадает"
    override val checksumMismatch = "Контрольная сумма не совпадает"
    override val checksumChecking = "Проверка контрольной суммы…"
    override val speedLimitNoLimit = "Без ограничений (макс. скорость)"
    override val speedLimitCustom = "Своё значение..."
    override val speedLimitValue = "Значение"
    override val scheduleAm = "AM"
    override val schedulePm = "PM"
    override val detailsChipMedia = "Медиа"
    override val detailsChipTorrent = "Торрент"
    override val detailsChipFile = "Файл"
    override val detailsChipResumable = "Докачка"
    override val detailsChipNoResume = "Без докачки"
    override fun detailsClipChip(start: String, end: String?, cutAfterDownload: Boolean) =
        "Фрагмент $start–${end ?: "конец"}" + if (cutAfterDownload) " · обрезка после загрузки" else ""

    override fun detailsTorrentFiles(selected: Int, total: Int) =
        "Файлы торрента (выбрано $selected из $total)"

    override val detailsPriorityHigh = "Высокий"
    override val detailsPriorityLow = "Низкий"
    override val detailsPriorityNormal = "Обычный"
    override val detailsStatusCompleted = "Завершено"
    override val detailsStatusPaused = "Приостановлено"
    override val detailsStatusFailed = "Ошибка"
    override val detailsStatusWaiting = "Ожидание"
    override val detailsStatusQueued = "В очереди"
    override val detailsStatusNotStarted = "Не начато"
    override val detailsUnknownSize = "Неизвестно"
    override val detailsMergingAudioVideo = "Объединение аудио и видео..."
    override fun detailsTorrentSpeedUploadDisabled(download: String) =
        "↓ $download (отдача выключена)"

    override fun detailsTorrentSpeed(download: String, upload: String) = "↓ $download | ↑ $upload"
    override val detailsConnecting = "Подключение..."
    override fun detailsStartsAt(time: String) = "Начало: $time"
    override fun detailsWaitingInQueuePosition(position: Int) = "В очереди (№$position)"
    override val detailsWaitingInQueue = "В очереди"
    override val detailsDownloaded = "Загружено"
    override val detailsSpeed = "Скорость"
    override val detailsState = "Состояние"
    override val detailsTimeLeft = "Осталось"
    override val detailsAdded = "Добавлено"
    override val detailsSavedTo = "Сохранено в"
    override val detailsSource = "Источник"
    override val detailsPage = "Страница"
    override val detailsOpenFile = "Открыть файл"
    override val detailsOpenFolder = "Открыть папку"
    override val detailsStartNow = "Начать сейчас"
    override val detailsRemoveFromList = "Убрать из списка"
    override val detailsCancelDownload = "Отменить загрузку"
    override val detailsHeaderDownload = "Загрузка"
    override val detailsHeaderDetails = "ПОДРОБНОСТИ"
    override fun detailsAddedToday(clock: String) = "Сегодня $clock"
    override fun detailsAddedYesterday(clock: String) = "Вчера $clock"

    override fun removeDialogTitle(count: Int) =
        if (count > 1) "Удалить $count " + ruPlural(
            count,
            "загрузку",
            "загрузки",
            "загрузок"
        ) else "Удалить загрузку"

    override fun removeDialogMessage(count: Int) =
        if (count > 1) "Удалить эти загрузки ($count) из списка?"
        else "Удалить эту загрузку из списка?"

    override val removeDialogAlsoDeleteFiles = "Также удалить скачанные файлы с диска"

    override val duplicateDialogTitle = "Загрузка уже есть"
    override val duplicateDialogSubtitle = "Этот элемент уже есть в очереди загрузок"
    override fun duplicateDialogStatus(status: String) = "Состояние: $status"
    override val duplicateDialogQuestion =
        "Продолжить существующую загрузку или начать заново?"
    override val duplicateDialogResumeExisting = "Продолжить существующую"
    override val duplicateDialogRedownload = "Скачать заново"

    override val overwriteDialogTitle = "Файл уже существует"
    override val overwriteDialogSubtitle = "В папке назначения уже есть файл с таким именем"
    override val overwriteDialogFileNameLabel = "Имя файла: "
    override val overwriteDialogFolderLabel = "Папка: "
    override val overwriteDialogNewCopyLabel = "Новая копия: "
    override val overwriteDialogQuestion =
        "Сохранить эту загрузку как новую копию или перезаписать существующий файл?"

    override fun overwriteDialogSaveAsCopy(name: String) = "Сохранить как копию ($name)"
    override val overwriteDialogOverwriteExisting = "Перезаписать существующий файл"

    override val desktopWindowTagline = "Быстрый многодвижковый менеджер загрузок"
    override val desktopTrayIdleTooltip = "FetchJet - работает в фоне"
    override val desktopTrayDownloadsHeader = "Загрузки FetchJet:"
    override fun desktopTrayMore(count: Int) = "+ ещё $count"
    override val desktopTrayShowApp = "Показать приложение"
    override val desktopTrayCloseApp = "Закрыть приложение"
    override val sectionGeneral = "Общие"
    override val sectionGeneralBlurb = "Внешний вид приложения и запуск"
    override val sectionDownloads = "Загрузки"
    override val sectionDownloadsBlurb = "Куда сохранять файлы и сколько качать одновременно"
    override val sectionBrowser = "Расширение браузера"
    override val sectionBrowserBlurb = "Отправляйте ссылки и видео из браузера в FetchJet"
    override val sectionSpeed = "Ограничение скорости"
    override val sectionSpeedBlurb = "Ограничьте трафик, который использует FetchJet"
    override val sectionMedia = "Медиаинструменты"
    override val sectionMediaBlurb = "yt-dlp и FFmpeg для видеосайтов"
    override val sectionTorrent = "BitTorrent"
    override val sectionTorrentBlurb = "Пиры, отдача и торрент-движок"
    override val sectionUpdates = "Обновления"
    override val sectionUpdatesBlurb = "Ваша версия и новые выпуски"
    override val summaryNoQueueLimit = "без ограничений"
    override fun summaryAtATime(n: Int) = "$n одновременно"
    override val summaryNoSpeedLimit = "Без ограничений"
    override val summarySetupNeeded = "Нужна настройка"
    override val summaryUploadingOff = "Отдача выключена"
    override val summaryUploadingOn = "Отдача включена"
    override fun summaryVersionAvailable(version: String) = "Доступна $version"
    override fun summaryVersion(version: String) = "Версия $version"
    override val changesSavedAutomatically = "Изменения сохраняются автоматически."
    override val settingsAllSettings = "Все настройки"

    override val clipboardGroup = "Буфер обмена"
    override val clipboardFooter =
        "Скопируйте где угодно ссылку на файл (zip, mp4, pdf, apk…) или magnet-ссылку, и FetchJet предложит её скачать."
    override val watchClipboard = "Следить за буфером обмена"
    override val watchClipboardOn = "Вкл. · скопированные ссылки на файлы предлагаются для загрузки"
    override val settingOff = "Выкл."
    override val alsoVideoPages = "Также страницы с видео"
    override val alsoVideoPagesHint =
        "Предлагаются и ссылки YouTube, Facebook, TikTok, Vimeo… (не только файлы)"
    override val diagnosticsGroup = "Диагностика"
    override fun logSavedTo(path: String) =
        "Сохранено: $path — отправьте этот файл, когда сообщаете о проблеме."

    override val diagnosticsFooter =
        "Сохраняет журнал приложения (что делали yt-dlp и движки) в папку «Загрузки»."
    override val saveLogToDownloads = "Сохранить журнал в «Загрузки»"
    override val logCouldNotSave = "не удалось сохранить"
    override val appearanceGroup = "Внешний вид"
    override val themeTitle = "Тема"
    override val themeHint = "Светлое или тёмное оформление всех экранов"
    override val settingsThemeDark = "Тёмная"
    override val settingsThemeSystem = "Системная"
    override val settingsThemeLight = "Светлая"
    override val speedShownIn = "Единицы скорости"
    override val speedShownInHint =
        "Байты (MB/s), как размеры файлов, или биты (Mbps), как в тарифе интернета"

    override val saveLocationGroup = "Место сохранения"
    override val saveLocationFooter =
        "Новые загрузки сохраняются сюда, если при добавлении не выбрана другая папка."
    override val settingsFolderPath = "Путь к папке"
    override val settingsChooseFolder = "Выбрать папку…"
    override val settingsUseDownloads = "Использовать «Загрузки»"
    override val settingsConnectionsGroup = "Соединения"
    override val connectionsFooter =
        "На сколько частей делится прямая загрузка. Каждая часть — отдельное соединение; когда одна завершается, она берёт половину самой большой оставшейся части, чтобы ни одна не простаивала. «Авто» выбирает по размеру файла (4–8). Некоторые серверы ограничивают число соединений на клиента."
    override val settingsConnectionsPerDownload = "Соединений на загрузку"
    override val settingsConnectionsAuto = "Авто"
    override val organizeGroup = "Сортировка"
    override val organizeFooter =
        "Подпапки создаются внутри места сохранения: Video, Music, Archives, Documents, Programs, Images, Torrents."
    override val sortIntoFolders = "Раскладывать по папкам по типу"
    override val sortIntoFoldersOn = "Вкл. · напр. Downloads/Video/lecture.mp4"
    override val sortIntoFoldersOff = "Выкл. · всё сохраняется прямо в папку"

    override val speedLimitsFooter = "Применяется сразу и делится между текущими загрузками."
    override val speedLimitsFooterIos =
        "Применяется сразу и делится между текущими загрузками. В iOS HTTP-загрузки, уже идущие в фоне, сохраняют свою скорость; ограничение действует на начатые после него."
    override val speedLimitDownload = "Загрузка"
    override val speedLimitUploadTorrents = "Отдача (торренты)"

    override val torrentSharingGroup = "Раздача"
    override val downloadOnlyNoUpload = "Только скачивание (без отдачи)"
    override val downloadOnlyNoUploadHint =
        "Экономит исходящий канал и трафик. Некоторые трекеры медленнее отдают тем, кто не раздаёт."
    override val torrentEngineGroup = "Движок"
    override val settingsReady = "Готов"
    override val settingsNotReady = "Не готов"

    override val extInstallGroup = "Установка"
    override val extensionInstallFooter =
        "С расширением на видео и ссылках появляется кнопка «Скачать с FetchJet», а браузер может передавать свои загрузки в FetchJet. Оно связывается только с этим приложением на вашем устройстве."
    override val edgeAddedHint =
        "Добавлено · перезапустите Edge и нажмите «Включить расширение», когда он спросит"
    override val edgeFromStoreHint = "Из магазина надстроек Microsoft Edge · обновляется само"
    override val openInEdgeStore = "Открыть в магазине Edge"
    override val edgeAddedNote = "Готово: перезапустите Edge, он предложит включить FetchJet."
    override val extAddToEdge = "Добавить в Edge"
    override val firefoxFromStoreHint = "Из дополнений Firefox · обновляется само"
    override val extAddToFirefox = "Добавить в Firefox"
    override val chromeZipHint =
        "Его нет в Chrome Web Store: установите .zip в режиме разработчика (инструкция на странице). Само не обновляется."
    override val getTheZip = "Скачать .zip"
    override val firefoxAndroidHint =
        "Откройте это в Firefox на этом телефоне. Отправленные ссылки сразу попадут в это приложение."
    override val keepReadyForBrowser = "Держать наготове для браузера"
    override val keepReadyOn =
        "Варианты качества видео показываются прямо в Firefox. Пока это включено, висит тихое уведомление."
    override val keepReadyOff =
        "Выкл.: Firefox открывает FetchJet, и качество выбирается здесь. Вкл.: качество показывается в Firefox (с уведомлением)."
    override val popUpOverBrowser = "Открываться поверх браузера"
    override val popUpOverBrowserOn =
        "Вкл. · FetchJet открывается сам, когда Firefox присылает ссылку"
    override val popUpOverBrowserOff =
        "Выкл. · вместо этого появляется уведомление; нажмите, чтобы разрешить «поверх других приложений»"

    override val mediaToolsGroup = "Инструменты"
    override val ytDlpHint = "Находит видео и аудио на YouTube и других сайтах"
    override val settingsMissing = "Нет"
    override val ffmpegHint =
        "Объединяет видео и аудио (1080p и выше), вырезает фрагменты, делает MP3"
    override val ffmpegMissing = "Нет · до 720p"
    override fun jsRuntimeTitle(name: String) = "JS-среда · $name"
    override val jsRuntimeHint =
        "Проходит проверки YouTube, чтобы показывались все варианты качества, и быстрее"

    override fun missingPress(button: String) = "Нет · нажмите «$button»"
    override val mediaToolsFolder = "Папка инструментов"
    override val mediaUpdatingTools = "Обновление…"
    override val mediaDownloadingTools = "Загрузка инструментов…"
    override fun mediaDownloadingTool(name: String) = "Загрузка $name…"
    override fun mediaDownloadingToolPercent(name: String, percent: Int) =
        "Загрузка $name… $percent%"

    override val mediaUpdateTools = "Обновить инструменты"
    override val mediaDownloadTheTools = "Скачать инструменты"

    override val updateNewVersionAvailable = "Доступна новая версия"
    override fun updateVersionLine(version: String, current: String) =
        "FetchJet $version  ·  у вас $current"

    override val updateWhatsNew = "Что нового"
    override val updateNoReleaseNotes = "Нет описания изменений."
    override fun downloadingUpdate(percent: Int) = "Загрузка обновления… $percent%"
    override val updateDownloadedAndroid =
        "Обновление загружено. Android попросит подтвердить установку; ваши загрузки останутся в списке."
    override val updateDownloadedDesktop =
        "Обновление загружено. FetchJet закроется, установит его и откроется снова. Текущие загрузки будут приостановлены, их можно продолжить."
    override val storeInstallsItselfOpenNow =
        "Microsoft Store установит его сам. Откройте Store, чтобы обновить сейчас."
    override val updateInstall = "Установить"
    override val updateRestartAndUpdate = "Перезапустить и обновить"
    override val updateDownloading = "Загрузка…"
    override val updateDownloadAndInstall = "Скачать и установить"
    override val updateOpenMicrosoftStore = "Открыть Microsoft Store"
    override fun updateDownloadWithSize(size: String) = "Скачать $size"
    override val updateOpenReleasePage = "Открыть страницу выпуска"
    override val updateSkipThisVersion = "Пропустить эту версию"
    override val updateLater = "Позже"
    override fun versionIsAvailable(version: String) = "Доступна версия $version"
    override val youHaveLatest = "У вас установлена актуальная версия"
    override val updateSeeWhatsNew = "Что нового"
    override val updateChecking = "Проверка…"
    override val updateCheckNow = "Проверить"
    override val checkUpdatesAutomatically = "Проверять обновления автоматически"
    override val checkUpdatesAutomaticallyHint =
        "Ищет новую версию при запуске FetchJet (не чаще двух раз в день)."
    override val downloadUpdatesAutomatically = "Скачивать обновления автоматически"
    override val downloadUpdatesAutomaticallyHintAndroid =
        "Скачивает новую версию в фоне; вам остаётся только подтвердить установку."
    override val downloadUpdatesAutomaticallyHint =
        "Скачивает новую версию в фоне; вам остаётся только подтвердить перезапуск."
    override val installingUpdates = "Установка обновлений"
    override val installingUpdatesStore =
        "Microsoft Store сам устанавливает новые версии; FetchJet сообщит, когда выйдет новая."
    override val installingUpdatesManual =
        "На этом устройстве FetchJet сообщает о новой версии и даёт ссылку; установка за вами."
    override val updateReleaseNotes = "Список изменений"
    override val updateReleaseNotesHint = "Все версии и что в них изменилось"
    override val installHintReleasePage = "Откройте страницу выпуска, чтобы скачать новую версию."
    override val installHintApk =
        "Скачайте APK и откройте его, чтобы установить обновление поверх этой версии."
    override val installHintIpa =
        "Скачайте .ipa и установите через AltStore / Sideloadly, как вы устанавливали FetchJet."
    override val installHintDmg =
        "Скачайте .dmg, откройте его и перетащите FetchJet в «Программы» (с заменой старой версии)."
    override val installHintDeb =
        "Скачайте .deb и установите его (двойной щелчок или sudo apt install ./file.deb)."
    override val installHintOther = "Скачайте новую версию и установите её поверх этой."

    override val startupGroup = "Автозапуск"
    override val startupStoreFooter =
        "Версию из Store запускает Windows: включите FetchJet в Параметры > Приложения > Автозагрузка."
    override val startupStartAtLogin = "Запускать FetchJet при входе в систему"
    override val startupManagedByWindows = "Управляется Windows"
    override val startupOpenWindowsSettings = "Открыть параметры Windows"
    override val startupStartHidden = "Запускать свёрнутым в трей"
    override val startupStartHiddenHint = "При автозапуске оставаться в трее, не открывая окно."
    override val startupStartAtLoginHint =
        "Запланированные загрузки и очередь продолжают работу, а расширение браузера всегда находит приложение."

    override fun startupCouldNotChange(message: String) = "Не удалось изменить настройку: $message"
    override val whenDoneGroup = "Когда загрузки завершатся"
    override val whenDoneHint = "Только для этого сеанса. Перед этим будет 60 секунд на отмену."
    override val whenDoneNothing = "Ничего не делать"
    override val whenDoneExit = "Выйти из FetchJet"
    override val whenDoneSleep = "Спящий режим"
    override val whenDoneShutDown = "Выключить"
    override val whenDoneTitle = "Все загрузки завершены"
    override fun whenDoneExitIn(seconds: Int) = "FetchJet закроется через ${seconds} с."
    override fun whenDoneSleepIn(seconds: Int) =
        "Компьютер перейдёт в спящий режим через ${seconds} с."

    override fun whenDoneShutDownIn(seconds: Int) = "Компьютер выключится через ${seconds} с."
    override val whenDoneNow = "Сейчас"
    override fun whenDoneBanner(action: String) = "После загрузок: ${action}"
    override fun whenDoneFailed(message: String) = "Не удалось: ${message}"
    override val sectionNetwork = "Сеть"
    override val sectionNetworkBlurb = "Прокси и входы на сайты"
    override val netProxyGroup = "Прокси"
    override val netProxyOff = "Выкл."
    override val netProxyHost = "Сервер"
    override val netProxyPort = "Порт"
    override val netProxyUser = "Имя пользователя (необязательно)"
    override val netProxyPassword = "Пароль"
    override val netUser = "Имя пользователя"
    override val netSave = "Сохранить"
    override val netSaved = "Сохранено"
    override val netProxyHint =
        "Используется загрузками, видео (yt-dlp) и торрентами. Действует для загрузок, начатых после сохранения."
    override val netProxyHintIos =
        "Используется загрузками и видео. Торренты на iOS подключаются напрямую. Действует для загрузок, начатых после сохранения."
    override val netProxyBadPort = "Укажите сервер и порт от 1 до 65535"
    override val netLoginsGroup = "Входы на сайты"
    override val netLoginsHint =
        "Для загрузок, которые запрашивают имя и пароль (HTTP basic auth). Сайт включает и свои поддомены."
    override val netLoginSite = "Сайт (example.com)"
    override val netLoginAdd = "Добавить"
    override val netLoginsEmpty = "Нет сохранённых входов"
    override val netRemove = "Удалить"
    override fun netLoginsCount(n: Int) = "Входов: ${n}"
    override val sysTabAllDownloads = "Все загрузки"
    override val sysTabActive = "Активные"
    override val sysTabCompleted = "Завершённые"
    override val sysTabMediaExtractor = "Извлечение медиа"
    override val sysTabSettings = "Настройки"
    override val sysSortNewestFirst = "Сначала новые"
    override val sysSortOldestFirst = "Сначала старые"
    override val sysSortLargestFirst = "Сначала большие"
    override val sysSortSmallestFirst = "Сначала маленькие"
    override val sysSortNameAz = "Имя А–Я"
    override val sysEnterValidUrlOrMagnet = "Введите правильный URL или magnet-ссылку"
    override val sysCannotStartVideoInspectionFailed =
        "Не удалось начать загрузку: проверка видео не удалась."
    override val sysCannotStartTorrentInspectionFailed =
        "Не удалось начать загрузку: проверка торрента не удалась или не выбраны файлы."
    override val sysClipEndExceedsDuration = "Конец фрагмента больше длительности видео."
    override val sysClipStartAfterEnd = "Начало фрагмента должно быть раньше конца."
    override fun sysStartedDownload(name: String) = "Загрузка начата: $name"
    override val sysFailedToQueueDownload = "Не удалось добавить загрузку в очередь"
    override fun sysQualitiesNotAdded(count: Int) = "Не удалось добавить варианты качества: $count."
    override fun sysAddedQualityDownloads(count: Int, title: String) =
        "Добавлено загрузок «$title»: $count"

    override val sysSelectAtLeastOnePlaylistVideo = "Выберите хотя бы одно видео из плейлиста."
    override fun sysVideosNotAdded(count: Int) = "Не удалось добавить видео: $count."
    override fun sysAddedPlaylistVideos(count: Int, playlistTitle: String) =
        "Добавлено $count видео из «$playlistTitle»"

    override val sysSelectAtLeastOneLink = "Выберите хотя бы одну ссылку."
    override fun sysAddedLinkDownloads(added: Int, skipped: Int) =
        "Добавлено загрузок: $added" +
                (if (skipped > 0) " ($skipped уже в списке или с ошибкой)" else "")

    override val sysCouldNotReadTorrent = "Не удалось прочитать торрент"
    override fun sysCouldNotStartDownload(reason: String) = "Не удалось начать загрузку: $reason"
    override val sysUnknownError = "неизвестная ошибка"
    override fun sysQueuedFromBrowser(added: Int, skipped: Int) =
        "Добавлено в очередь из браузера: $added" +
                (if (skipped > 0) " (пропущено: $skipped)" else "")

    override val sysEnterTorrentOrMagnetFirst = "Сначала укажите торрент или magnet-ссылку"
    override val sysFailedToParseTorrentMetadata = "Не удалось разобрать метаданные торрента"
    override val sysEnterMediaUrlFirst = "Сначала введите ссылку на видео"
    override fun sysUnableToFetchFormatsWithReason(reason: String) =
        "Не удалось получить форматы видео: $reason"

    override val sysUnableToFetchFormats =
        "Не удалось получить форматы видео. Проверьте ссылку или подключение к интернету."
    override val sysFailedToInspectMediaQualities = "Не удалось получить варианты качества"
    override fun sysTorrentEngineError(message: String) = "Ошибка движка BitTorrent: $message"
    override fun sysTorrentEngineInstallFailed(message: String) =
        "Не удалось установить движок BitTorrent: $message"

    override val sysMediaToolsDownloaded = "Медиаинструменты загружены!"
    override val sysMediaToolsDownloadFailed = "Не удалось загрузить медиаинструменты"
    override val sysMediaToolsUpdated = "Медиаинструменты обновлены"
    override fun sysSavedLinks(count: Int, path: String) =
        "Сохранено ссылок: $count в $path"

    override fun sysCouldNotSaveLinks(reason: String) = "Не удалось сохранить ссылки: $reason"
    override val sysErrorFallback = "ошибка"
    override val sysDownloadFailed = "Ошибка загрузки"
    override val sysDownloadFailedNetwork = "Загрузка не удалась из-за ошибки сети"
    override val sysCouldNotStart = "Не удалось запустить"
    override val sysUnknownErrorCapital = "Неизвестная ошибка"
    override fun sysCouldNotSaveFileTo(dir: String) = "Не удалось сохранить файл в $dir"
    override val sysPlaylistFallbackTitle = "Плейлист"
    override val sysPlaylistQualityBest = "Лучшее"
    override val sysPlaylistQualityAudioOnly = "Только аудио"
    override val sysToolsReadyYtDlpFfmpeg =
        "Готово: yt-dlp и ffmpeg установлены (полная поддержка 1080p+)"
    override val sysToolsReadyYtDlpNoFfmpeg =
        "Готово: yt-dlp установлен (без ffmpeg - не выше 720p)"
    override val sysToolsNotInstalled = "Не установлено. Нажмите «Скачать инструменты» ниже."
    override val sysToolsInstalledBuiltIn = "Установлено и готово (встроено)"
    override fun sysYtDlpVersionReady(version: String) = "yt-dlp $version (готов)"
    override val sysMediaEngineBridgeNotRegistered = "Мост медиадвижка не зарегистрирован"
    override val sysWebKitBuiltIn = "WebKit (встроенный)"
    override val sysTorrentEngineBuiltIn = "Готово: движок BitTorrent встроен"
    override fun sysTorrentEngineReadyVersion(version: String) =
        "Готово: движок BitTorrent (libtorrent $version)"

    override fun sysTorrentEngineFailedToLoad(message: String) =
        "Не удалось загрузить движок BitTorrent: $message"

    override val sysTorrentEngineAvailable =
        "Готово: движок BitTorrent доступен (загрузится при первом торренте)"
    override val sysTorrentEngineMissing =
        "Движок BitTorrent отсутствует. Нажмите «Скачать инструменты» ниже."
    override val sysTorrentEngineNotAvailable = "Движок BitTorrent недоступен в этой сборке"
    override fun sysCouldNotStartInstaller(message: String) =
        "Не удалось запустить установщик: $message"

    override val sysCheckingForUpdates = "Проверка обновлений…"
    override val sysNoPublishedRelease = "Опубликованных выпусков пока нет."
    override fun sysVersionNoChangesForDevice(version: String, current: String) =
        "В версии $version нет изменений для этого устройства; у вас последняя сборка для него ($current)."

    override fun sysVersionAvailable(version: String) = "Доступна версия $version."
    override fun sysUpToDate(current: String) = "У вас последняя версия ($current)."
    override fun sysCouldNotCheckForUpdates(reason: String) =
        "Не удалось проверить обновления: $reason"

    override val sysNetworkError = "ошибка сети"
    override val sysUpdateNoInstallerForSystem =
        "Не удалось скачать обновление: в этом выпуске нет установщика для вашей системы."

    override fun sysDownloadingAsset(name: String) = "Загрузка $name…"
    override fun sysVersionReadyToInstall(version: String) = "Версия $version готова к установке."
    override fun sysUpdateDownloadFailed(message: String) =
        "Не удалось скачать обновление: $message"

    override val sysAllowUnknownAppsThenInstall =
        "разрешите «Установку неизвестных приложений» для FetchJet и снова нажмите «Установить»."
    override val sysDownloadsChannelName = "Загрузки FetchJet"
    override val sysDownloadsChannelDescription = "Показывает ход загрузок в реальном времени"
    override val sysNotificationAppTitle = "FetchJet Downloader"
    override val sysAppRunningInBackground = "Приложение работает в фоне"
    override val sysNotificationDownloadsFallback = "Загрузки"
    override fun sysFilesDownloading(count: Int) =
        "Загружается $count " + ruPlural(count, "файл", "файла", "файлов")

    override fun sysPercentActive(percent: Int, count: Int) = "$percent% • активно: $count"
    override fun sysActiveCount(count: Int) = "Активно: $count"
    override fun sysDownloadCompleteSize(size: String) = "Загрузка завершена • $size"
    override fun sysMoreDownloading(count: Int) = "+ ещё загружается: $count"
    override val sysComplete = "Завершено"
    override fun sysNotificationDownloadsTitle(speed: String) = "Загрузки FetchJet ($speed)"
    override fun sysDownloadingPercent(count: Int, percent: Int) = "Загружается: $count • $percent%"
    override val sysDownloadTaskFallback = "Загрузка"
    override val sysBrowserReadyTitle = "FetchJet готов к работе с браузером"
    override val sysBrowserReadyText = "Варианты качества видео показываются прямо в Firefox"
    override val sysTurnOff = "Выключить"
    override val sysBrowserReadyChannelName = "Расширение браузера"
    override val sysBrowserReadyChannelDescription =
        "Показывается, пока FetchJet готов к работе с расширением браузера"
    override val sysDownloadingFileFallback = "Загрузка файла"
    override val sysUnknownSize = "Неизвестно"
    override val sysStatusCompleted = "Завершено"
    override val sysStatusDownloading = "Загружается"
    override val sysDownloadCompleteTitle = "Загрузка завершена"
    override fun sysFinishedDownloading(size: String) = "Загрузка завершена • $size"
    override val sysSentToFetchJet = "Отправлено в FetchJet"
    override val sysLinksChannelName = "Ссылки из браузера"
    override val sysLinksChannelDescription =
        "Нажмите, чтобы открыть ссылку, которую только что получил FetchJet"
    override val sysLinkReceivedTitle = "Ссылка получена"
    override val sysLinkReceivedText = "Нажмите, чтобы открыть её в FetchJet"

    override val stepCompleted = "Завершено"
    override val stepSeeding = "Раздача"
    override val stepFetchingMetadata = "Получение метаданных"
    override val stepConnectingToPeers = "Подключение к пирам"
    override val stepCheckingFiles = "Проверка файлов"
    override val stepCheckingResumeData = "Проверка данных возобновления"
    override val stepDownloading = "Загрузка"
    override val stepFinished = "Готово"
    override val stepConnecting = "Подключение"
    override val stepPaused = "Приостановлено"
    override val stepError = "Ошибка"
    override val stepResolvingStreams = "Получение потоков"
    override val stepDownloadingParts = "Загрузка частей"
    override val stepPreparingDownload = "Подготовка загрузки"
    override val stepClippingVideo = "Обрезка видео"
    override val stepDownloadingMedia = "Загрузка медиа"
    override val stepAlreadyDownloaded = "Уже загружено"
    override val stepDownloadingVideo = "Загрузка видео"
    override val stepDownloadingAudio = "Загрузка аудио"
    override val stepMergingAudioVideo = "Объединение аудио и видео"
    override val stepConvertingToMp3 = "Конвертация в MP3"
    override val stepCuttingClip = "Вырезание фрагмента"
    override val stepSavingToFolder = "Сохранение в папку"
    override val stepFinalizingVideo = "Завершение обработки видео"
    override val stepConvertingVideo = "Конвертация видео"
    override val stepRepackagingVideo = "Перепаковка видео"
    override val stepYtDlpFallback = "Переход на yt-dlp"
    override val stepNotPlainStream = "не простой поток"
    override val stepSingleConnection = "Одно соединение"
    override val stepServerNoRanges = "сервер не поддерживает диапазоны"
    override val stepAgeRestrictedExpiredCookies =
        "Ограничение по возрасту: истёкшие cookies в Файлы > FetchJet"
    override val stepAgeRestrictedPlaceCookies =
        "Ограничение по возрасту: поместите cookies.txt в Файлы > FetchJet"
    override val stepPreparingMediaEngine =
        "Подготовка встроенного медиадвижка (запуск Python, загрузка yt-dlp)"
    override val stepMediaEngineNotStarted =
        "Медиадвижок не запущен. Нажмите «Загрузить инструменты»."

    override fun stepNumber(step: Int, total: Int) = "Шаг ${step} из ${total}"
    override fun stepSavingAs(ext: String) = "Сохранение как .${ext}"
    override fun stepJoiningParts(count: Int) =
        "Склейка в MP4: ${count} ${ruPlural(count, "часть", "части", "частей")}"

    override fun stepNoDataFor(seconds: Int) = "Нет данных ${seconds} с"
    override fun stepPeers(peers: Int, seeds: Int) =
        "${peers} ${ruPlural(peers, "пир", "пира", "пиров")} (${seeds} ${
            ruPlural(
                seeds,
                "сид",
                "сида",
                "сидов"
            )
        })"

    override fun stepDownloadingFrom(host: String) = "Загрузка с ${host}"
    override fun stepMediaEngineNotReady(error: String) = "Медиадвижок не готов: ${error}"
    override fun stepTorrentEngineFailed(error: String) =
        "Не удалось запустить движок BitTorrent: ${error}"

    override fun stepReadyWith(details: String) = "Готов: ${details}"

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
    override val visibleToDevices = "Виден другим устройствам"
    override val visibleToDevicesHint = "Другие устройства FetchJet в этой сети могут найти это устройство и отправлять ему загрузки. Выкл.: вы всё равно можете отправлять им."
    override val sysDeviceSearch = "Поиск устройств"
    override val sysDeviceOthers = "Другие устройства"
    override val sysDeviceNoMatch = "Нет подходящих устройств"
    override val sectionDevices = "Устройства"
    override val sectionDevicesBlurb = "Отправляйте загрузки между своими устройствами"
    override val pairedDevicesTitle = "Сопряжённые устройства"
    override val noPairedDevices = "Сопряжённых устройств пока нет. Выполните сопряжение через «Отправить на устройство» у загрузки."
    override val unpairDevice = "Отменить сопряжение"
    override val summaryVisible = "Виден"
    override val summaryHidden = "Скрыт"
    override val blockDevice = "Заблокировать"
    override val unblockDevice = "Разблокировать"
    override val blockedDevicesTitle = "Заблокированные устройства"
    override val noBlockedDevices = "Нет заблокированных устройств"
    override val pairRefusedBlocked = "Другое устройство заблокировало это"
    override val pairRefusedWait = "Подождите {seconds} с, прежде чем снова отправлять запрос этому устройству"
    override val pairRefusedBusy = "Другое устройство отвечает на другой запрос сопряжения. Попробуйте чуть позже."
    override val pairDeclined = "Другое устройство отклонило сопряжение. Отправьте ещё раз, чтобы повторить."
    override val sysDevicePairedLabel = "Paired"
    override val sysDeviceThisDevice = "This device"
    override val sysDeviceManualConnect = "Connect by IP"
    override val sysDeviceConnect = "Connect"
}

private fun ruPlural(n: Int, one: String, few: String, many: String): String {
    val abs = if (n < 0) -n else n
    val mod10 = abs % 10
    val mod100 = abs % 100
    return when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
}
