package com.michelle.raouf.fetch_jet.core.i18n.data_provider

object ArabicProvider : AppLocalization {
    override val localeCode = "ar"
    override val languageName = "العربية"
    override val ok = "حسنًا"
    override val cancel = "إلغاء"
    override val close = "إغلاق"
    override val save = "حفظ"
    override val delete = "حذف"
    override val remove = "إزالة"
    override val open = "فتح"
    override val copy = "نسخ"
    override val copied = "تم النسخ"
    override val retry = "إعادة المحاولة"
    override val pause = "إيقاف مؤقت"
    override val resume = "استئناف"
    override val apply = "تطبيق"
    override val reset = "إعادة الضبط"
    override val yes = "نعم"
    override val no = "لا"
    override val back = "رجوع"
    override val done = "تم"
    override val selectAll = "تحديد الكل"
    override val selectNone = "إلغاء التحديد"
    override val language = "اللغة"
    override val languageHint = "لغة كل الشاشات. خيار «لغة النظام» يتبع لغة جهازك."
    override val systemLanguage = "لغة النظام"
    override val homeSettings = "الإعدادات"
    override val homeDownloads = "التنزيلات"

    override val homeTabAll = "الكل"
    override val homeTabActive = "النشطة"
    override val homeTabDone = "المكتملة"
    override val homeTabTorrent = "تورنت"
    override val homeTabMedia = "الوسائط"
    override val homeSidebarTypeHeader = "النوع"
    override val homeSearchDownloadsPlaceholder = "ابحث في التنزيلات"
    override val homeAdd = "إضافة"
    override val homeAddDownload = "إضافة تنزيل"
    override val homePauseAll = "إيقاف الكل مؤقتًا"
    override val homeResumeAll = "استئناف الكل"
    override fun homeSortBy(option: String) = "الترتيب: $option"
    override val homeLightTheme = "المظهر الفاتح"
    override val homeDarkTheme = "المظهر الداكن"

    override fun homeUpdatingMediaTools(tool: String, percent: Int?) =
        "جارٍ تحديث أدوات الوسائط · $tool" + (percent?.let { " $it%" } ?: "…")

    override val homeSearchDownloadsByNamePlaceholder = "ابحث في التنزيلات بالاسم…"
    override fun homeNoDownloadsMatch(query: String) = "لا توجد تنزيلات تطابق «$query»"
    override val homeNoTasksInView = "لا توجد تنزيلات في هذا العرض"
    override val homeTryAnotherName = "جرّب اسمًا آخر"
    override val homeAddDownloadToGetStarted = "أضف تنزيلًا للبدء"
    override fun homeDownloadingWaitingSummary(running: Int, waiting: Int) =
        "قيد التنزيل: $running · في الانتظار: $waiting"

    override fun homeQueueStatus(maxAtOnce: Int) =
        "قائمة الانتظار: " + if (maxAtOnce <= 0) "بلا حد" else arDownloadsAtOnce(maxAtOnce)

    override fun homeSpeedLimitStatus(limit: String?) = "حد السرعة: " + (limit ?: "غير مفعّل")
    override val homeGroupDownloading = "قيد التنزيل"
    override val homeGroupWaiting = "في الانتظار"
    override val homeGroupPausedFailed = "المتوقفة مؤقتًا والفاشلة"
    override val homeGroupCompleted = "المكتملة"
    override fun homeSelectedCount(count: Int) =
        if (count == 0) "لم يتم تحديد أي عنصر"
        else "تم تحديد " + arCount(count, "عنصر واحد", "عنصرين", "عناصر", "عنصرًا", "عنصر")

    override val homeExport = "تصدير"

    override fun homeStartsAt(time: String) = "يبدأ $time"
    override val homeWaitingInQueue = "في قائمة الانتظار"
    override fun homeWaitingInQueuePosition(position: Int) = "في قائمة الانتظار (#$position)"
    override val homeConnecting = "جارٍ الاتصال…"
    override val homeStarting = "جارٍ البدء…"
    override fun homeTimeLeft(eta: String) = "متبقٍ $eta"
    override fun homePausedState(percent: Int, restartsOnResume: Boolean) =
        "متوقف مؤقتًا · $percent%" + if (restartsOnResume) " · يبدأ من جديد عند الاستئناف" else ""

    override fun homeFailedState(message: String?) = "فشل: " + (message ?: "خطأ")
    override fun homeCompletedState(size: String?) = "مكتمل" + (size?.let { " · $it" } ?: "")
    override val homeStartNow = "ابدأ الآن"
    override val homeColumnName = "الاسم"
    override val homeColumnSize = "الحجم"
    override val homeColumnProgress = "التقدم"
    override val homeColumnSpeed = "السرعة"
    override val homeColumnLeft = "المتبقي"
    override val homeFilterAllDownloads = "كل التنزيلات"
    override val homeFilterDownloading = "قيد التنزيل"
    override val homeFilterWaiting = "في الانتظار"
    override val homeFilterPaused = "المتوقفة مؤقتًا"
    override val homeFilterCompleted = "المكتملة"
    override val homeFilterFailed = "الفاشلة"
    override val homeFilterMedia = "فيديو وصوت"
    override val homeFilterFiles = "الملفات"
    override val homeFilterTorrents = "تورنت"

    override val homeCantResumeTitle = "لا يمكن استئناف هذا التنزيل"
    override val homeCantResumeClipText =
        "المقاطع التي تُقص أثناء التنزيل لا يمكنها المتابعة من حيث توقفت. " +
                "إذا أوقفته مؤقتًا الآن، فسيبدأ المقطع من البداية عند الاستئناف."
    override val homeCantResumeServerText =
        "الخادم لا يدعم الاستئناف. إذا أوقفته مؤقتًا الآن، فسيبدأ التنزيل " +
                "من البداية عند الاستئناف."
    override val homePauseAnyway = "إيقاف مؤقت على أي حال"
    override val homeKeepDownloading = "متابعة التنزيل"
    override val homeMagnetLink = "رابط magnet"
    override val homeClipboardOfferTitle = "هل تريد تنزيل الرابط الذي نسخته؟"
    override val homeClipboardOfferHint = "يمكنك إيقاف هذا من الإعدادات > عام > مراقبة الحافظة."
    override val homeDownload = "تنزيل"
    override val homeNotNow = "ليس الآن"
    override val bridgePairTitle = "هل تسمح لإضافة المتصفح هذه؟"
    override val bridgePairText =
        "إضافة متصفح تطلب إرسال روابط إلى FetchJet. اسمح لها فقط إذا ثبّت إضافة FetchJet أو أعدت ضبطها للتو."
    override val bridgePairOldText =
        "إضافة FetchJet قديمة تريد إرسال روابط. اسمح لها إذا كانت إضافتك؛ تحديث الإضافة يلغي هذا السؤال."
    override val bridgePairAllow = "السماح"
    override val bridgePairBlock = "حظر"
    override val extConnectionsGroup = "اتصالات المتصفح"
    override val extConnectionsFooter =
        "إضافات المتصفح التي سمحت لها أو حظرتها من إرسال الروابط إلى FetchJet."
    override val extAllowedLabel = "مسموح"
    override val extBlockedLabel = "محظور"
    override val extRevoke = "إلغاء السماح"
    override val extUnblock = "رفع الحظر"
    override val homeBrowserFallbackTitle = "هل تريد التنزيل في المتصفح بدلًا من ذلك؟"
    override val homeBrowserFallbackText =
        "لقد ألغيت هذا الملف في FetchJet. أوقف المتصفح تنزيله عندما سلّم الرابط، " +
                "لذا لن يتابع التنزيل إلا إذا أعدته إليه."
    override val homeDownloadInBrowser = "التنزيل في المتصفح"
    override val homeNoDiscard = "لا، تجاهله"

    override val homeQueueTitle = "قائمة الانتظار"
    override val homeQueueFooter =
        "التنزيلات الإضافية تنتظر وتبدأ تلقائيًا، الأقدم أولًا، عند انتهاء أحدها. " +
                "زر «ابدأ الآن» على تنزيل منتظر يتخطى قائمة الانتظار."
    override val homeDownloadsAtSameTime = "عدد التنزيلات المتزامنة"
    override val homeNoLimit = "بلا حد"
    override fun homeAtATime(count: Int) = arDownloadsAtOnce(count)

    override val homePiecesLabel = "الأجزاء"
    override fun homeSegmentsCount(count: Int) =
        arCount(count, "مقطع واحد", "مقطعان", "مقاطع", "مقطعًا", "مقطع")

    override fun homePiecesCount(count: Int) =
        arCount(count, "جزء واحد", "جزآن", "أجزاء", "جزءًا", "جزء")

    override fun homeActiveCount(count: Int) = "نشطة: $count"
    override fun homeWaitingCount(count: Int) = "في الانتظار: $count"
    override fun homeDoneCount(count: Int) = "مكتملة: $count"
    override fun homeSegmentsProgress(finished: Int, total: Int, downloading: String?) =
        "المقاطع $finished/$total" + (downloading?.let { " · جارٍ تنزيل $it" } ?: "")

    override fun homeRetryingNote(note: String?) = "$note · جارٍ إعادة المحاولة"
    override fun clipStartAfterEnd(start: String, end: String) =
        "يجب أن يكون وقت البداية ($start) قبل وقت النهاية ($end)"

    override fun clipEndExceedsVideo(end: String, duration: String) =
        "وقت النهاية ($end) يتجاوز مدة الفيديو ($duration)"

    override fun clipStartExceedsVideo(duration: String) =
        "وقت البداية يتجاوز مدة الفيديو ($duration)"

    override val setStartTimeTitle = "تحديد وقت البداية (من)"
    override val setEndTimeTitle = "تحديد وقت النهاية (إلى)"
    override val addDialogTitle = "إضافة تنزيل جديد"
    override val addDialogSubtitle = "أدخل رابطًا لبدء التنزيل"
    override val addLinkLabel = "الرابط"
    override val addImportTxt = "استيراد .txt"
    override val openTorrentFile = "فتح ملف .torrent"
    override val urlPlaceholder = "الصق الروابط أو روابط magnet"
    override val addPaste = "لصق"
    override val addDownloadEngine = "محرك التنزيل"
    override val engineDirect = "مباشر"
    override val engineMedia = "وسائط"
    override val engineTorrent = "تورنت"
    override val addFileNameLabel = "اسم الملف"
    override val findingName = "جارٍ جلب الاسم…"
    override val saveDestination = "مجلد الحفظ"
    override val quickFolderDownloads = "التنزيلات"
    override val quickFolderDocuments = "المستندات"
    override val quickFolderMedia = "الوسائط"
    override val quickFolderDesktop = "سطح المكتب"
    override val addRetryInspect = "إعادة الفحص"
    override val addStarting = "جارٍ البدء..."
    override val addInspecting = "جارٍ الفحص..."
    override val addInspectionFailed = "فشل الفحص"
    override val addEnterUrl = "أدخل الرابط"
    override fun scheduleLinks(n: Int) = if (n == 0) "جدولة الروابط" else "جدولة " + arLinksGen(n)
    override fun downloadLinks(n: Int) = if (n == 0) "تنزيل الروابط" else "تنزيل " + arLinksGen(n)
    override val inspectVideoFirst = "افحص الفيديو أولًا"
    override val inspectTorrentFirst = "افحص التورنت أولًا"
    override val invalidTimeRange = "نطاق وقت غير صالح"
    override fun scheduleVideos(n: Int) =
        if (n == 0) "جدولة الفيديوهات" else "جدولة " + arVideosGen(n)

    override fun downloadVideos(n: Int) =
        if (n == 0) "تنزيل الفيديوهات" else "تنزيل " + arVideosGen(n)

    override val addScheduleDownload = "جدولة التنزيل"
    override val addStartDownload = "بدء التنزيل"
    override val availableAudioFormats = "صيغ الصوت المتاحة"
    override val availableQualities = "الجودات المتاحة"
    override val subtitlesTitle = "الترجمة"
    override val subtitlesAuto = "تلقائي"
    override val subtitlesEmbedInVideo = "دمج في الفيديو"
    override val subtitlesSeparateFile = "ملف منفصل"
    override val addSelectAll = "تحديد الكل"
    override val addClearSelection = "مسح"
    override fun selectedOfAvailable(selected: Int, total: Int) =
        "المحدد: $selected من $total متاحة"

    override fun qualityFormatLine(format: String, details: String) = "الصيغة: $format$details"
    override val inspectingVideoQualities = "جارٍ فحص جودات الفيديو..."
    override fun readingCourseLectures(done: Int, total: Int) =
        "جارٍ قراءة محاضرات الكورس: ${done} / ${total}"

    override val readingCourseLecturesHint = "إضافة المتصفح تقرأها في تبويب الكورس: اتركه مفتوحًا"
    override val inspectingVideoQualitiesHint = "جارٍ البحث عن دقات الفيديو ومسارات الصوت المتاحة"
    override val videoInspectionFailed = "فشل فحص الفيديو"
    override val addRetryInspection = "إعادة الفحص"
    override val mediaEngineStarting = "جارٍ تشغيل محرك الوسائط..."
    override val mediaEngineNotReady = "محرك الوسائط غير جاهز"
    override val mediaEngineRequiresYtDlp =
        "استخراج الفيديو يتطلب تثبيت yt-dlp أو تضمينه في التطبيق."
    override val preparingEngine = "جارٍ تجهيز المحرك..."
    override val startMediaEngine = "تنزيل / تشغيل محرك الوسائط"
    override val inspectAudioFormats = "فحص صيغ الصوت واختيارها"
    override val inspectVideoQuality = "فحص جودة الفيديو واختيارها"
    override fun clipSectionTitle(isAudio: Boolean) =
        if (isAudio) "قص جزء من الصوت (اختياري)" else "قص جزء من الفيديو (اختياري)"

    override val clipSectionHint = "نزّل نطاقًا زمنيًا محددًا فقط"
    override val audioConvertTo = "تحويل إلى"
    override val audioConvertAllTo = "تحويل كل الصوتيات إلى"
    override val audioOriginal = "الأصلي"
    override val fileMissingTitle = "الملف غير موجود"
    override fun fileMissingMessage(name: String) = "\"$name\" لم يعد موجودًا على هذا الجهاز. ربما تم نقله أو إعادة تسميته أو حذفه."
    override val browserNotInstalled = "غير مثبّت على هذا الجهاز"
    override val extensionConnected = "الإضافة متصلة"
    override val extensionNotAddedYet = "لم تتم إضافة الإضافة بعد"
    override val addExtension = "إضافة الإضافة"
    override fun browserMissingTitle(name: String) = "${name} غير مثبّت"
    override fun browserMissingMessage(name: String) = "${name} غير موجود على هذا الجهاز. ثبّته أولًا، ثم ارجع وأضف الإضافة."
    override fun downloadBrowser(name: String) = "تنزيل ${name}"
    override fun loadUnpackedTitle(name: String) = "إضافة FetchJet إلى ${name}"
    override fun loadUnpackedSteps(name: String, page: String) = "فتح ${name} صفحة الإضافات (${page}). هناك:\n1. فعّل وضع المطوّر (Developer mode).\n2. اضغط Load unpacked.\n3. اختر هذا المجلد (مساره منسوخ بالفعل، فقط الصقه):"
    override val loadUnpackedUpdates = "مرة واحدة فقط: بعدها تتحدّث الإضافة وحدها كلما تحدّث FetchJet."
    override val copyPath = "نسخ المسار"
    override fun extensionConnectedVersion(version: String) = "الإضافة متصلة · ${version}"
    override fun extensionNeedsUpdate(found: String, wanted: String) = "الإضافة ${found} أقدم من ${wanted}: حدّثها"
    override fun extensionOldVersion(wanted: String) = "متصلة بإضافة أقدم من ${wanted}: حدّثها"
    override val updateExtension = "تحديث الإضافة"
    override fun storeHasUpdate(version: String) = "المتجر فيه ${version} بالفعل: المتصفح يحدّثها وحده."
    override val updateFromAppHint = "المتجر لا يحتويها بعد: حدّثها من النسخة الموجودة داخل FetchJet."
    override fun storeWaiting(wanted: String) = "الإصدار ${wanted} في انتظار موافقة المتجر، والمتصفح سيحدّثها وحده بعد الموافقة."
    override val openStore = "فتح المتجر"
    override val updateFromApp = "التحديث من FetchJet"
    override fun updateFromGitHubHint(version: String) = "المتجر لا يحتويها بعد: يستطيع FetchJet تثبيت النسخة الموقّعة ${version} من إصداره."
    override val updateFromGitHub = "حدّث الآن"
    override val extensionDownloadFailed = "تعذّر تنزيل الإضافة. تحقق من الاتصال وحاول مرة أخرى."
    override fun installFromFileTitle(name: String) = "تثبيت FetchJet في ${name}"
    override fun installFromFileSteps(name: String, file: String) = "تم حفظ الإضافة في Downloads/${file}. في ${name}:\n1. الإعدادات > About ${name}: اضغط على الشعار 5 مرات (أول مرة فقط).\n2. ارجع إلى الإعدادات: Install extension from file.\n3. اختر ${file} واضغط Add."
    override fun openBrowser(name: String) = "فتح ${name}"
    override fun mediaDurationFullRange(isAudio: Boolean, clock: String, friendly: String) =
        "مدة ${if (isAudio) "الصوت" else "الفيديو"}: $clock ($friendly) - المدة كاملة محددة افتراضيًا"

    override val clipStartTimeFrom = "وقت البداية (من)"
    override val clipEndTimeTo = "وقت النهاية (إلى)"
    override fun fullMediaChip(isAudio: Boolean, duration: String) =
        if (isAudio) "الصوت كاملًا ($duration)" else "الفيديو كاملًا ($duration)"

    override val clipFirst5Min = "أول 5 دقائق"
    override val clipFirst15Min = "أول 15 دقيقة"
    override val clipFirst30Min = "أول 30 دقيقة"
    override val clipFirst1Hour = "أول ساعة"
    override val clipModeTitle = "طريقة تنزيل المقطع"
    override val clipOnlyTitle = "المقطع فقط"
    override val clipOnlyLine1 = "ينزّل الجزء الذي اخترته فقط"
    override val clipOnlyLine2 = "أبطأ: YouTube يحدّ من السرعة"
    override val clipOnlyLine3 = "الإيقاف المؤقت يعيد المقطع من البداية"
    override fun fullThenCutTitle(isAudio: Boolean) =
        if (isAudio) "الصوت كاملًا ثم القص" else "الفيديو كاملًا ثم القص"

    override fun fullThenCutLine1(isAudio: Boolean, sizeText: String) =
        if (isAudio) "ينزّل الصوت كاملًا$sizeText" else "ينزّل الفيديو كاملًا$sizeText"

    override val fullThenCutLine2 = "أسرع: بأقصى سرعة"
    override val fullThenCutLine3 = "ثم يحتفظ بالجزء الذي اخترته فقط"
    override val fullThenCutLine4 = "الإيقاف المؤقت والاستئناف يعملان"
    override val inspectingTorrent = "جارٍ فحص بيانات التورنت..."
    override val inspectingTorrentHint = "جارٍ جلب قائمة الملفات والأقران"
    override val torrentInspectionFailed = "فشل فحص التورنت"
    override val inspectTorrentFiles = "فحص ملفات التورنت"
    override val disableTorrentUpload = "إيقاف رفع التورنت (تنزيل فقط)"
    override val torrentFiles = "ملفات التورنت"
    override val addDeselectAll = "إلغاء تحديد الكل"
    override val torrentPriorityHigh = "عالية"
    override val torrentPriorityLow = "منخفضة"
    override val torrentPriorityNormal = "عادية"
    override fun selectedOfLinks(selected: Int, total: Int) =
        "المحدد $selected من " + arLinksGen(total)

    override val readingTorrentFiles = "جارٍ قراءة ملفات التورنت…"
    override fun couldNotReadTorrentFiles(error: String) =
        "تعذّرت قراءة ملفاته ($error)؛ سيتم تنزيلها كلها."

    override val linkTagVideo = "فيديو"
    override val linkTagAudio = "صوت"
    override val linkTagTorrent = "تورنت"
    override val linkTagFile = "ملف"
    override val linkListMagnet = "رابط magnet"
    override fun linkListYouTubeVideo(id: String) = "فيديو YouTube $id"
    override fun playlistSummary(videos: Int, selected: Int) =
        "قائمة تشغيل · " + arCount(
            videos,
            "فيديو واحد",
            "فيديوان",
            "فيديوهات",
            "فيديو",
            "فيديو"
        ) + " · المحدد $selected"

    override val playlistFolder = "الحفظ في مجلد"
    override val playlistFolderHint = "كل الملفات تُحفظ في هذا المجلد. امسح الاسم لحفظها مباشرة."
    override val qualityForAllVideos = "الجودة لكل الفيديوهات"
    override val qualityForAllVideosHint =
        "إذا لم تتوفر هذه الجودة في فيديو ما، تُختار أقرب جودة أقل منها."
    override val downloadOnlyThisVideo = "تنزيل هذا الفيديو فقط بدلًا من ذلك"
    override val whenToStart = "موعد البدء"
    override val scheduleNow = "الآن"
    override val scheduleIn30Min = "بعد 30 دقيقة"
    override val scheduleIn1Hour = "بعد ساعة"
    override val scheduleAtTime = "في وقت محدد…"
    override val scheduleDayLabel = "يوم"
    override val scheduleToday = "اليوم"
    override val scheduleTomorrow = "غدًا"
    override val scheduleIn2Days = "بعد يومين"
    override val scheduleIn3Days = "بعد 3 أيام"
    override val scheduleInAWeek = "بعد أسبوع"
    override val scheduleTimePassed = "هذا الوقت مضى اليوم. اختر وقتًا لاحقًا أو يومًا آخر."
    override val scheduleHourLabel = "الساعة"
    override val scheduleMinuteLabel = "الدقيقة"
    override val scheduleSet = "تعيين"
    override val scheduleSetTime = "تعيين الوقت"
    override fun scheduleStartsAt(time: String) = "يبدأ $time (يجب أن يكون FetchJet قيد التشغيل)"
    override val addConnectionsTitle = "الاتصالات"
    override val connectionsAuto = "تلقائي"
    override fun connectionsAutoWithDefault(n: Int) = "تلقائي ($n)"
    override fun pickerExceedsVideo(duration: String) = "يتجاوز مدة الفيديو ($duration)"
    override fun pickerMustBeAfterStart(start: String) = "يجب أن يكون بعد وقت البداية ($start)"
    override val pickerStartZero = "البداية (00:00)"
    override val pickerHours = "ساعات"
    override val pickerMinutes = "دقائق"
    override val pickerSeconds = "ثوانٍ"
    override fun pickerSelectedTime(time: String, summary: String) =
        "الوقت المحدد: $time ($summary)"

    override fun pickerEndOfVideo(time: String) = "نهاية الفيديو ($time)"
    override fun pickerMin(time: String) = "الحد الأدنى ($time)"
    override fun pickerApply(time: String) = "تطبيق ($time)"
    override val pickerInvalidTime = "وقت غير صالح"
    override val dirPickerSelectFolder = "اختيار مجلد"
    override val dirPickerUp = "للأعلى"
    override fun dirPickerSubfolders(n: Int) = "المجلدات الفرعية ($n)"
    override val dirPickerHiddenOn = "المخفية: ظاهرة"
    override val dirPickerHiddenOff = "المخفية: غير ظاهرة"
    override val dirPickerNewFolder = "مجلد جديد"
    override val dirPickerFolderName = "اسم المجلد…"
    override val dirPickerCreateFailed = "تعذّر إنشاء المجلد"
    override val dirPickerCreate = "إنشاء"
    override val dirPickerNoSubfolders = "لا توجد مجلدات فرعية هنا.\nسيتم حفظ الملفات هنا مباشرة."
    override val dirPickerHiddenTag = "(مخفي)"
    override val speedLimitUnlimited = "غير محدود (أقصى سرعة)"
    override val speedPresetsTitle = "سرعات جاهزة"
    override val speedPresetsFooter =
        "حدّد سرعة لكل من سريعة ومتوسطة ومنخفضة. اختَر واحدة بسرعة من الأعلى؛ السلحفاة دائمًا 512 ك.ب/ث."
    override val speedPresetHigh = "عالية"
    override val speedPresetFast = "سريعة"
    override val speedPresetMedium = "متوسطة"
    override val speedPresetLow = "منخفضة"
    override val speedScheduleTitle = "جدولة السرعة"
    override val speedScheduleFooter =
        "حدّد سرعة في أوقات معيّنة من اليوم؛ بدون حد بره الأوقات دي."
    override val speedScheduleAdd = "أضف فترة زمنية"
    override val speedScheduleFrom = "من"
    override val speedScheduleTo = "إلى"
    override val speedScheduleActive = "حد السرعة المجدول شغّال دلوقتي"
    override val speedScheduleEmpty = "مفيش فترات لسه — ضيف واحدة."
    override val priorityLabel = "الأولويّة"
    override val priorityHigh = "عالية"
    override val priorityNormal = "عادية"
    override val priorityLow = "منخفضة"
    override val checksumLabel = "بصمة الملف (اختياري)"
    override val checksumHint = "sha256 أو md5"
    override val checksumVerified = "البصمة مطابقة"
    override val checksumMismatch = "البصمة غير مطابقة"
    override val checksumChecking = "جارٍ التحقق من البصمة…"
    override val speedLimitNoLimit = "بلا حد (أقصى سرعة)"
    override val speedLimitCustom = "مخصص..."
    override val speedLimitValue = "القيمة"
    override val scheduleAm = "ص"
    override val schedulePm = "م"
    override val detailsChipMedia = "وسائط"
    override val detailsChipTorrent = "تورنت"
    override val detailsChipFile = "ملف"
    override val detailsChipResumable = "قابل للاستئناف"
    override val detailsChipNoResume = "لا يدعم الاستئناف"
    override fun detailsClipChip(start: String, end: String?, cutAfterDownload: Boolean) =
        "مقطع $start–${end ?: "النهاية"}" + if (cutAfterDownload) " · يُقص بعد التنزيل" else ""

    override fun detailsTorrentFiles(selected: Int, total: Int) =
        "ملفات التورنت (المحدد $selected من $total)"

    override val detailsPriorityHigh = "عالية"
    override val detailsPriorityLow = "منخفضة"
    override val detailsPriorityNormal = "عادية"
    override val detailsStatusCompleted = "مكتمل"
    override val detailsStatusPaused = "متوقف مؤقتًا"
    override val detailsStatusFailed = "فشل"
    override val detailsStatusWaiting = "في الانتظار"
    override val detailsStatusQueued = "في قائمة الانتظار"
    override val detailsStatusNotStarted = "لم يبدأ"
    override val detailsUnknownSize = "غير معروف"
    override val detailsMergingAudioVideo = "جارٍ دمج الصوت والفيديو..."
    override fun detailsTorrentSpeedUploadDisabled(download: String) =
        "تنزيل: $download (الرفع معطّل)"

    override fun detailsTorrentSpeed(download: String, upload: String) =
        "تنزيل: $download | رفع: $upload"

    override val detailsConnecting = "جارٍ الاتصال..."
    override fun detailsStartsAt(time: String) = "يبدأ $time"
    override fun detailsWaitingInQueuePosition(position: Int) = "في قائمة الانتظار (#$position)"
    override val detailsWaitingInQueue = "في قائمة الانتظار"
    override val detailsDownloaded = "تم تنزيله"
    override val detailsSpeed = "السرعة"
    override val detailsState = "الحالة"
    override val detailsTimeLeft = "الوقت المتبقي"
    override val detailsAdded = "تاريخ الإضافة"
    override val detailsSavedTo = "مكان الحفظ"
    override val detailsSource = "المصدر"
    override val detailsPage = "الصفحة"
    override val detailsOpenFile = "فتح الملف"
    override val detailsOpenFolder = "فتح المجلد"
    override val detailsStartNow = "ابدأ الآن"
    override val detailsRemoveFromList = "إزالة من القائمة"
    override val detailsCancelDownload = "إلغاء التنزيل"
    override val detailsHeaderDownload = "تنزيل"
    override val detailsHeaderDetails = "التفاصيل"
    override fun detailsAddedToday(clock: String) = "اليوم $clock"
    override fun detailsAddedYesterday(clock: String) = "أمس $clock"

    override fun removeDialogTitle(count: Int) =
        if (count > 1) "إزالة " + arDownloadsGen(count) else "إزالة التنزيل"

    override fun removeDialogMessage(count: Int) = when {
        count <= 1 -> "هل تريد بالتأكيد إزالة هذا التنزيل؟"
        count == 2 -> "هل تريد بالتأكيد إزالة هذين التنزيلين؟"
        else -> "هل تريد بالتأكيد إزالة ${arDownloadsGen(count)}؟"
    }

    override val removeDialogAlsoDeleteFiles = "حذف الملفات التي تم تنزيلها من الجهاز أيضًا"

    override val duplicateDialogTitle = "التنزيل موجود بالفعل"
    override val duplicateDialogSubtitle = "هذا العنصر موجود بالفعل في قائمة التنزيلات"
    override fun duplicateDialogStatus(status: String) = "الحالة: $status"
    override val duplicateDialogQuestion =
        "هل تريد استئناف التنزيل الحالي أم البدء من جديد؟"
    override val duplicateDialogResumeExisting = "استئناف الحالي"
    override val duplicateDialogRedownload = "إعادة التنزيل (من البداية)"

    override val overwriteDialogTitle = "الملف موجود بالفعل"
    override val overwriteDialogSubtitle = "يوجد ملف بهذا الاسم في مجلد الحفظ"
    override val overwriteDialogFileNameLabel = "اسم الملف: "
    override val overwriteDialogFolderLabel = "المجلد: "
    override val overwriteDialogNewCopyLabel = "النسخة الجديدة: "
    override val overwriteDialogQuestion =
        "هل تريد حفظ هذا التنزيل كنسخة جديدة أم استبدال الملف الموجود؟"

    override fun overwriteDialogSaveAsCopy(name: String) = "حفظ كنسخة ($name)"
    override val overwriteDialogOverwriteExisting = "استبدال الملف الموجود"

    override val desktopWindowTagline = "مدير تنزيل فائق السرعة بعدة محركات"
    override val desktopTrayIdleTooltip = "FetchJet - يعمل في الخلفية"
    override val desktopTrayDownloadsHeader = "تنزيلات FetchJet:"
    override fun desktopTrayMore(count: Int) = "+$count أخرى"
    override val desktopTrayShowApp = "إظهار التطبيق"
    override val desktopTrayCloseApp = "إغلاق التطبيق"
    override val sectionGeneral = "عام"
    override val sectionGeneralBlurb = "مظهر التطبيق وطريقة تشغيله"
    override val sectionDownloads = "التنزيلات"
    override val sectionDownloadsBlurb = "مكان حفظ الملفات وعدد التنزيلات المتزامنة"
    override val sectionBrowser = "إضافة المتصفح"
    override val sectionBrowserBlurb = "أرسل الروابط والفيديوهات من متصفحك إلى FetchJet"
    override val sectionSpeed = "حدود السرعة"
    override val sectionSpeedBlurb = "حدّد سرعة الإنترنت التي يستخدمها FetchJet"
    override val sectionMedia = "أدوات الوسائط"
    override val sectionMediaBlurb = "yt-dlp و FFmpeg لمواقع الفيديو"
    override val sectionTorrent = "BitTorrent"
    override val sectionTorrentBlurb = "الأقران والرفع ومحرك التورنت"
    override val sectionUpdates = "التحديثات"
    override val sectionUpdatesBlurb = "إصدارك الحالي والإصدارات الجديدة"
    override val summaryNoQueueLimit = "بلا حد"
    override fun summaryAtATime(n: Int) = arDownloadsAtOnce(n)
    override val summaryNoSpeedLimit = "بلا حد"
    override val summarySetupNeeded = "يحتاج إلى إعداد"
    override val summaryUploadingOff = "الرفع متوقف"
    override val summaryUploadingOn = "الرفع مفعّل"
    override fun summaryVersionAvailable(version: String) = "الإصدار $version متاح"
    override fun summaryVersion(version: String) = "الإصدار $version"
    override val changesSavedAutomatically = "تُحفظ التغييرات تلقائيًا."
    override val settingsAllSettings = "كل الإعدادات"

    override val clipboardGroup = "الحافظة"
    override val clipboardFooter =
        "انسخ رابط ملف (zip، mp4، pdf، apk…) أو رابط magnet من أي مكان، وسيسألك FetchJet إن كنت تريد تنزيله."
    override val watchClipboard = "مراقبة الحافظة"
    override val watchClipboardOn = "مفعّل · تُعرض روابط الملفات المنسوخة للتنزيل"
    override val settingOff = "غير مفعّل"
    override val alsoVideoPages = "صفحات الفيديو أيضًا"
    override val alsoVideoPagesHint =
        "تُعرض أيضًا روابط YouTube و Facebook و TikTok و Vimeo… (وليس الملفات فقط)"
    override val diagnosticsGroup = "التشخيص"
    override fun logSavedTo(path: String) = "تم الحفظ: $path — أرسل هذا الملف عند الإبلاغ عن مشكلة."
    override val diagnosticsFooter =
        "يحفظ سجل التطبيق (ما قام به yt-dlp والمحركات) في مجلد التنزيلات."
    override val saveLogToDownloads = "حفظ السجل في التنزيلات"
    override val logCouldNotSave = "تعذّر الحفظ"
    override val appearanceGroup = "المظهر"
    override val themeTitle = "السمة"
    override val themeHint = "مظهر فاتح أو داكن لكل الشاشات"
    override val settingsThemeDark = "داكن"
    override val settingsThemeSystem = "النظام"
    override val settingsThemeLight = "فاتح"
    override val speedShownIn = "وحدة عرض السرعة"
    override val speedShownInHint =
        "بالبايت (MB/s) مثل أحجام الملفات، أو بالبت (Mbps) مثل باقة الإنترنت"

    override val saveLocationGroup = "مكان الحفظ"
    override val saveLocationFooter =
        "تُحفظ التنزيلات الجديدة هنا ما لم تختر مجلدًا آخر عند إضافتها."
    override val settingsFolderPath = "مسار المجلد"
    override val settingsChooseFolder = "اختيار مجلد…"
    override val settingsUseDownloads = "استخدام مجلد التنزيلات"
    override val settingsConnectionsGroup = "الاتصالات"
    override val connectionsFooter =
        "عدد الأجزاء التي يُقسَّم إليها التنزيل المباشر. كل جزء اتصال، وعندما ينتهي أحدها يأخذ نصف أكبر جزء متبقٍ، فلا يبقى أي اتصال بلا عمل. الوضع التلقائي يختار حسب حجم الملف (4–8). بعض الخوادم تحدّ عدد الاتصالات لكل مستخدم."
    override val settingsConnectionsPerDownload = "عدد الاتصالات لكل تنزيل"
    override val settingsConnectionsAuto = "تلقائي"
    override val organizeGroup = "التنظيم"
    override val organizeFooter =
        "تُنشأ مجلدات فرعية داخل مكان الحفظ: Video و Music و Archives و Documents و Programs و Images و Torrents."
    override val sortIntoFolders = "الفرز في مجلدات حسب النوع"
    override val sortIntoFoldersOn = "مفعّل · مثلًا Downloads/Video/lecture.mp4"
    override val sortIntoFoldersOff = "غير مفعّل · كل الملفات تُحفظ في المجلد مباشرة"

    override val speedLimitsFooter = "يُطبَّق فورًا ويُوزَّع على التنزيلات الجارية."
    override val speedLimitsFooterIos =
        "يُطبَّق فورًا ويُوزَّع على التنزيلات الجارية. على iOS، تحتفظ تنزيلات HTTP الجارية في الخلفية بسرعتها، ويُطبَّق الحد على التنزيلات التي تبدأ بعده."
    override val speedLimitDownload = "التنزيل"
    override val speedLimitUploadTorrents = "الرفع (التورنت)"

    override val torrentSharingGroup = "المشاركة"
    override val downloadOnlyNoUpload = "تنزيل فقط (بدون رفع)"
    override val downloadOnlyNoUploadHint =
        "يوفّر سرعة الرفع وباقة البيانات. بعض المتتبعات تبطئ التنزيل للأقران الذين لا يشاركون."
    override val torrentEngineGroup = "المحرك"
    override val settingsReady = "جاهز"
    override val settingsNotReady = "غير جاهز"

    override val extInstallGroup = "التثبيت"
    override val extensionInstallFooter =
        "مع الإضافة يظهر زر «Download with FetchJet» على الفيديوهات والروابط، ويستطيع المتصفح تسليم تنزيلاته إلى FetchJet. وهي تتواصل فقط مع هذا التطبيق على جهازك."
    override val edgeAddedHint =
        "تمت الإضافة · أعد تشغيل Edge ووافق على تشغيل الإضافة عندما يطلب ذلك"
    override val edgeFromStoreHint = "من متجر إضافات Microsoft Edge · تتحدّث تلقائيًا"
    override val openInEdgeStore = "فتح في متجر Edge"
    override val edgeAddedNote = "تم: أعد تشغيل Edge، وسيطلب منك تشغيل FetchJet."
    override val extAddToEdge = "إضافة إلى Edge"
    override val firefoxFromStoreHint = "من إضافات Firefox · تتحدّث تلقائيًا"
    override val extAddToFirefox = "إضافة إلى Firefox"
    override val chromeZipHint =
        "غير متوفرة في سوق Chrome الإلكتروني: ثبّت ملف .zip في وضع المطوّر (الخطوات في الصفحة). لا تتحدّث تلقائيًا."
    override val getTheZip = "تنزيل ملف .zip"
    override val firefoxAndroidHint =
        "افتح هذا في Firefox على هذا الهاتف. الروابط التي ترسلها تصل مباشرة إلى هذا التطبيق."
    override val keepReadyForBrowser = "البقاء جاهزًا للمتصفح"
    override val keepReadyOn =
        "تظهر جودات الفيديو مباشرة في Firefox. يبقى إشعار هادئ ما دام هذا الخيار مفعّلًا."
    override val keepReadyOff =
        "غير مفعّل: يفتح Firefox تطبيق FetchJet وتختار الجودة هنا. مفعّل: تظهر الجودات في Firefox (مع بقاء إشعار)."
    override val popUpOverBrowser = "الظهور فوق المتصفح"
    override val popUpOverBrowserOn = "مفعّل · يفتح FetchJet تلقائيًا عندما يرسل Firefox رابطًا"
    override val popUpOverBrowserOff =
        "غير مفعّل · يظهر إشعار بدلًا من ذلك، اضغط عليه للسماح بـ«الظهور فوق التطبيقات الأخرى»"

    override val mediaToolsGroup = "الأدوات"
    override val ytDlpHint = "يعثر على الفيديو والصوت في YouTube والمواقع الأخرى"
    override val settingsMissing = "غير موجود"
    override val ffmpegHint = "يدمج الفيديو والصوت (1080p وأعلى)، ويقص المقاطع، وينشئ ملفات MP3"
    override val ffmpegMissing = "غير موجود · حتى 720p"
    override fun jsRuntimeTitle(name: String) = "بيئة تشغيل JS · $name"
    override val jsRuntimeHint = "يتجاوز فحوصات YouTube لتظهر كل الجودات وبسرعة أكبر"
    override fun missingPress(button: String) = "غير موجود · اضغط «$button»"
    override val mediaToolsFolder = "مجلد الأدوات"
    override val mediaUpdatingTools = "جارٍ التحديث…"
    override val mediaDownloadingTools = "جارٍ تنزيل الأدوات…"
    override fun mediaDownloadingTool(name: String) = "جارٍ تنزيل $name…"
    override fun mediaDownloadingToolPercent(name: String, percent: Int) =
        "جارٍ تنزيل $name… $percent%"

    override val mediaUpdateTools = "تحديث الأدوات"
    override val mediaDownloadTheTools = "تنزيل الأدوات"

    override val updateNewVersionAvailable = "يتوفر إصدار جديد"
    override fun updateVersionLine(version: String, current: String) =
        "FetchJet $version  ·  لديك $current"

    override val updateWhatsNew = "ما الجديد"
    override val updateNoReleaseNotes = "لا توجد ملاحظات لهذا الإصدار."
    override fun downloadingUpdate(percent: Int) = "جارٍ تنزيل التحديث… $percent%"
    override val updateDownloadedAndroid =
        "تم تنزيل التحديث. سيطلب منك Android تأكيد التثبيت، وتبقى تنزيلاتك في القائمة."
    override val updateDownloadedDesktop =
        "تم تنزيل التحديث. سيُغلق FetchJet ويثبّته ثم يفتح من جديد. ستتوقف التنزيلات الجارية مؤقتًا ويمكن استئنافها."
    override val storeInstallsItselfOpenNow =
        "يثبّته Microsoft Store تلقائيًا. افتح المتجر للتحديث الآن."
    override val updateInstall = "تثبيت"
    override val updateRestartAndUpdate = "إعادة التشغيل والتحديث"
    override val updateDownloading = "جارٍ التنزيل…"
    override val updateDownloadAndInstall = "تنزيل وتثبيت"
    override val updateOpenMicrosoftStore = "فتح Microsoft Store"
    override fun updateDownloadWithSize(size: String) = "تنزيل $size"
    override val updateOpenReleasePage = "فتح صفحة الإصدار"
    override val updateSkipThisVersion = "تخطي هذا الإصدار"
    override val updateLater = "لاحقًا"
    override fun versionIsAvailable(version: String) = "الإصدار $version متاح"
    override val youHaveLatest = "الإصدار المثبّت لديك هو الأحدث"
    override val updateSeeWhatsNew = "عرض الجديد"
    override val updateChecking = "جارٍ التحقق…"
    override val updateCheckNow = "تحقق الآن"
    override val checkUpdatesAutomatically = "التحقق من التحديثات تلقائيًا"
    override val checkUpdatesAutomaticallyHint =
        "يبحث عن إصدار جديد عند تشغيل FetchJet (مرتين يوميًا على الأكثر)."
    override val downloadUpdatesAutomatically = "تنزيل التحديثات تلقائيًا"
    override val downloadUpdatesAutomaticallyHintAndroid =
        "ينزّل الإصدار الجديد في الخلفية، وما عليك إلا تأكيد التثبيت."
    override val downloadUpdatesAutomaticallyHint =
        "ينزّل الإصدار الجديد في الخلفية، وما عليك إلا تأكيد إعادة التشغيل."
    override val installingUpdates = "تثبيت التحديثات"
    override val installingUpdatesStore =
        "يثبّت Microsoft Store الإصدارات الجديدة تلقائيًا، ويخبرك FetchJet عند صدور أي منها."
    override val installingUpdatesManual =
        "على هذا الجهاز يخبرك FetchJet بالإصدار الجديد ويعطيك رابطه، وأنت تثبّته."
    override val updateReleaseNotes = "ملاحظات الإصدار"
    override val updateReleaseNotesHint = "كل الإصدارات وما تغيّر فيها"
    override val installHintReleasePage = "افتح صفحة الإصدار لتنزيل الإصدار الجديد."
    override val installHintApk = "نزّل ملف APK وافتحه لتثبيت التحديث فوق هذا الإصدار."
    override val installHintIpa =
        "نزّل ملف .ipa وثبّته باستخدام AltStore / Sideloadly، كما ثبّت FetchJet من قبل."
    override val installHintDmg =
        "نزّل ملف .dmg وافتحه، ثم اسحب FetchJet إلى مجلد Applications (واستبدل النسخة القديمة)."
    override val installHintDeb =
        "نزّل ملف .deb وثبّته (بالنقر المزدوج، أو sudo apt install ./file.deb)."
    override val installHintOther = "نزّل الإصدار الجديد وثبّته فوق هذا الإصدار."

    override val startupGroup = "بدء التشغيل"
    override val startupStoreFooter =
        "نسخة المتجر يشغّلها Windows: فعّل FetchJet من الإعدادات > التطبيقات > بدء التشغيل."
    override val startupStartAtLogin = "تشغيل FetchJet عند تسجيل الدخول"
    override val startupManagedByWindows = "يديره Windows"
    override val startupOpenWindowsSettings = "فتح إعدادات Windows"
    override val startupStartHidden = "البدء مخفيًا في منطقة الإشعارات"
    override val startupStartHiddenHint =
        "عند التشغيل مع تسجيل الدخول، يبقى في منطقة الإشعارات بدلًا من فتح النافذة."
    override val startupStartAtLoginHint =
        "تستمر التنزيلات المجدولة والمنتظرة، وتجد إضافة المتصفح التطبيق دائمًا."

    override fun startupCouldNotChange(message: String) = "تعذّر تغيير الإعداد: $message"
    override val whenDoneGroup = "عند انتهاء التحميلات"
    override val whenDoneHint = "لهذه الجلسة فقط. قبل التنفيذ لديك 60 ثانية للإلغاء."
    override val whenDoneNothing = "لا شيء"
    override val whenDoneExit = "إغلاق FetchJet"
    override val whenDoneSleep = "سكون"
    override val whenDoneShutDown = "إيقاف التشغيل"
    override val whenDoneTitle = "انتهت كل التحميلات"
    override fun whenDoneExitIn(seconds: Int) = "سيُغلق FetchJet خلال ${seconds} ث."
    override fun whenDoneSleepIn(seconds: Int) = "سيدخل الجهاز في وضع السكون خلال ${seconds} ث."
    override fun whenDoneShutDownIn(seconds: Int) = "سيتم إيقاف تشغيل الجهاز خلال ${seconds} ث."
    override val whenDoneNow = "الآن"
    override fun whenDoneBanner(action: String) = "عند انتهاء التحميلات: ${action}"
    override fun whenDoneFailed(message: String) = "تعذّر التنفيذ: ${message}"
    override val sectionNetwork = "الشبكة"
    override val sectionNetworkBlurb = "البروكسي وتسجيل الدخول للمواقع"
    override val netProxyGroup = "البروكسي"
    override val netProxyOff = "متوقف"
    override val netProxyHost = "الخادم"
    override val netProxyPort = "المنفذ"
    override val netProxyUser = "اسم المستخدم (اختياري)"
    override val netProxyPassword = "كلمة المرور"
    override val netUser = "اسم المستخدم"
    override val netSave = "حفظ"
    override val netSaved = "تم الحفظ"
    override val netProxyHint =
        "تستخدمه التنزيلات وتنزيلات الفيديو (yt-dlp) والتورنت. يطبَّق على التنزيلات التي تبدأ بعد الحفظ."
    override val netProxyHintIos =
        "تستخدمه التنزيلات وتنزيلات الفيديو. التورنت على iOS يتصل مباشرة. يطبَّق على التنزيلات التي تبدأ بعد الحفظ."
    override val netProxyBadPort = "أدخل خادمًا ومنفذًا من 1 إلى 65535"
    override val netLoginsGroup = "تسجيل الدخول للمواقع"
    override val netLoginsHint =
        "للتنزيلات التي تطلب اسم مستخدم وكلمة مرور (HTTP basic auth). الموقع يشمل نطاقاته الفرعية أيضًا."
    override val netLoginSite = "الموقع (example.com)"
    override val netLoginAdd = "إضافة"
    override val netLoginsEmpty = "لا توجد بيانات دخول محفوظة"
    override val netRemove = "حذف"
    override fun netLoginsCount(n: Int) = "${n} حسابات"
    override val sysTabAllDownloads = "كل التنزيلات"
    override val sysTabActive = "النشطة"
    override val sysTabCompleted = "المكتملة"
    override val sysTabMediaExtractor = "مستخرج الوسائط"
    override val sysTabSettings = "الإعدادات"
    override val sysSortNewestFirst = "الأحدث أولًا"
    override val sysSortOldestFirst = "الأقدم أولًا"
    override val sysSortLargestFirst = "الأكبر أولًا"
    override val sysSortSmallestFirst = "الأصغر أولًا"
    override val sysSortNameAz = "الاسم أ–ي"
    override val sysEnterValidUrlOrMagnet = "أدخل رابطًا صالحًا أو رابط magnet"
    override val sysCannotStartVideoInspectionFailed = "تعذّر بدء التنزيل: فشل فحص الفيديو."
    override val sysCannotStartTorrentInspectionFailed =
        "تعذّر بدء التنزيل: فشل فحص التورنت أو لم يتم تحديد أي ملفات."
    override val sysClipEndExceedsDuration = "وقت نهاية المقطع يتجاوز مدة الفيديو."
    override val sysClipStartAfterEnd = "يجب أن يكون وقت بداية المقطع قبل وقت نهايته."
    override fun sysStartedDownload(name: String) = "بدأ التنزيل: $name"
    override val sysFailedToQueueDownload = "تعذّرت إضافة التنزيل إلى قائمة الانتظار"
    override fun sysQualitiesNotAdded(count: Int) =
        "تعذّرت إضافة " + arCount(count, "جودة واحدة", "جودتين", "جودات", "جودةً", "جودة") + "."

    override fun sysAddedQualityDownloads(count: Int, title: String) =
        "تمت إضافة ${arDownloadsGen(count)} من «$title»"

    override val sysSelectAtLeastOnePlaylistVideo = "اختر فيديو واحدًا على الأقل من قائمة التشغيل."
    override fun sysVideosNotAdded(count: Int) = "تعذّرت إضافة ${arVideosGen(count)}."
    override fun sysAddedPlaylistVideos(count: Int, playlistTitle: String) =
        "تمت إضافة ${arVideosGen(count)} من «$playlistTitle»"

    override val sysSelectAtLeastOneLink = "اختر رابطًا واحدًا على الأقل."
    override fun sysAddedLinkDownloads(added: Int, skipped: Int) =
        (if (added == 0) "لم تتم إضافة أي تنزيل" else "تمت إضافة ${arDownloadsGen(added)}") +
                (if (skipped > 0) " (تم تخطي ${arLinksGen(skipped)}: موجودة بالفعل في القائمة أو فشلت)" else "")

    override val sysCouldNotReadTorrent = "تعذّرت قراءة التورنت"
    override fun sysCouldNotStartDownload(reason: String) = "تعذّر بدء التنزيل: $reason"
    override val sysUnknownError = "خطأ غير معروف"
    override fun sysQueuedFromBrowser(added: Int, skipped: Int) =
        (if (added == 0) "لم تتم إضافة أي تنزيل من المتصفح" else "تمت إضافة ${arDownloadsGen(added)} من المتصفح") +
                (if (skipped > 0) " (تم تخطي $skipped)" else "")

    override val sysEnterTorrentOrMagnetFirst = "أدخل رابط تورنت أو magnet أولًا"
    override val sysFailedToParseTorrentMetadata = "تعذّرت قراءة بيانات التورنت"
    override val sysEnterMediaUrlFirst = "أدخل رابط الفيديو أولًا"
    override fun sysUnableToFetchFormatsWithReason(reason: String) =
        "تعذّر جلب صيغ الفيديو: $reason"

    override val sysUnableToFetchFormats =
        "تعذّر جلب صيغ الفيديو. تحقق من الرابط أو من اتصالك بالإنترنت."
    override val sysFailedToInspectMediaQualities = "تعذّر فحص جودات الوسائط"
    override fun sysTorrentEngineError(message: String) = "خطأ في محرك BitTorrent: $message"
    override fun sysTorrentEngineInstallFailed(message: String) =
        "فشل تثبيت محرك BitTorrent: $message"

    override val sysMediaToolsDownloaded = "تم تنزيل أدوات الوسائط بنجاح!"
    override val sysMediaToolsDownloadFailed = "تعذّر تنزيل أدوات الوسائط"
    override val sysMediaToolsUpdated = "تم تحديث أدوات الوسائط"
    override fun sysSavedLinks(count: Int, path: String) = "تم حفظ ${arLinksGen(count)} في $path"
    override fun sysCouldNotSaveLinks(reason: String) = "تعذّر حفظ الروابط: $reason"
    override val sysErrorFallback = "خطأ"
    override val sysDownloadFailed = "فشل التنزيل"
    override val sysDownloadFailedNetwork = "فشل التنزيل بسبب خطأ في الشبكة"
    override val sysCouldNotStart = "تعذّر البدء"
    override val sysUnknownErrorCapital = "خطأ غير معروف"
    override fun sysCouldNotSaveFileTo(dir: String) = "تعذّر حفظ الملف في $dir"
    override val sysPlaylistFallbackTitle = "قائمة تشغيل"
    override val sysPlaylistQualityBest = "الأفضل"
    override val sysPlaylistQualityAudioOnly = "الصوت فقط"
    override val sysToolsReadyYtDlpFfmpeg =
        "جاهز: yt-dlp و ffmpeg مثبّتان (دعم كامل لـ 1080p وأعلى)"
    override val sysToolsReadyYtDlpNoFfmpeg = "جاهز: yt-dlp مثبّت (بدون ffmpeg - حتى 720p)"
    override val sysToolsNotInstalled = "غير مثبّت. اضغط «تنزيل الأدوات» بالأسفل."
    override val sysToolsInstalledBuiltIn = "مثبّت وجاهز (مدمج)"
    override fun sysYtDlpVersionReady(version: String) = "yt-dlp $version (جاهز)"
    override val sysMediaEngineBridgeNotRegistered = "جسر محرك الوسائط غير مسجّل"
    override val sysWebKitBuiltIn = "WebKit (مدمج)"
    override val sysTorrentEngineBuiltIn = "جاهز: محرك BitTorrent مدمج"
    override fun sysTorrentEngineReadyVersion(version: String) =
        "جاهز: محرك BitTorrent (libtorrent $version)"

    override fun sysTorrentEngineFailedToLoad(message: String) =
        "تعذّر تحميل محرك BitTorrent: $message"

    override val sysTorrentEngineAvailable = "جاهز: محرك BitTorrent متاح (يُحمَّل مع أول تورنت)"
    override val sysTorrentEngineMissing =
        "محرك BitTorrent غير موجود. اضغط «تنزيل الأدوات» بالأسفل."
    override val sysTorrentEngineNotAvailable = "محرك BitTorrent غير متاح في هذا الإصدار"
    override fun sysCouldNotStartInstaller(message: String) = "تعذّر تشغيل المثبّت: $message"
    override val sysCheckingForUpdates = "جارٍ التحقق من التحديثات…"
    override val sysNoPublishedRelease = "لا يوجد إصدار منشور بعد."
    override fun sysVersionNoChangesForDevice(version: String, current: String) =
        "الإصدار $version لا يحتوي على تغييرات لهذا الجهاز، ولديك أحدث إصدار له ($current)."

    override fun sysVersionAvailable(version: String) = "الإصدار $version متاح."
    override fun sysUpToDate(current: String) = "لديك أحدث إصدار ($current)."
    override fun sysCouldNotCheckForUpdates(reason: String) = "تعذّر التحقق من التحديثات: $reason"
    override val sysNetworkError = "خطأ في الشبكة"
    override val sysUpdateNoInstallerForSystem =
        "فشل تنزيل التحديث: هذا الإصدار لا يحتوي على مثبّت لهذا النظام."

    override fun sysDownloadingAsset(name: String) = "جارٍ تنزيل $name…"
    override fun sysVersionReadyToInstall(version: String) = "الإصدار $version جاهز للتثبيت."
    override fun sysUpdateDownloadFailed(message: String) = "فشل تنزيل التحديث: $message"
    override val sysAllowUnknownAppsThenInstall =
        "اسمح لـ FetchJet بـ«تثبيت التطبيقات غير المعروفة»، ثم اضغط «تثبيت» مرة أخرى."
    override val sysDownloadsChannelName = "تنزيلات FetchJet"
    override val sysDownloadsChannelDescription = "يعرض تقدم التنزيلات لحظة بلحظة"
    override val sysNotificationAppTitle = "FetchJet"
    override val sysAppRunningInBackground = "التطبيق يعمل في الخلفية"
    override val sysNotificationDownloadsFallback = "التنزيلات"
    override fun sysFilesDownloading(count: Int) = "جارٍ تنزيل ${arFilesGen(count)}"
    override fun sysPercentActive(percent: Int, count: Int) = "$percent% • النشطة: $count"
    override fun sysActiveCount(count: Int) = "النشطة: $count"
    override fun sysDownloadCompleteSize(size: String) = "اكتمل التنزيل • $size"
    override fun sysMoreDownloading(count: Int) = "+ " +
            arCount(
                count,
                "ملف آخر",
                "ملفان آخران",
                "ملفات أخرى",
                "ملفًا آخر",
                "ملف آخر"
            ) + " قيد التنزيل"

    override val sysComplete = "مكتمل"
    override fun sysNotificationDownloadsTitle(speed: String) = "تنزيلات FetchJet ($speed)"
    override fun sysDownloadingPercent(count: Int, percent: Int) =
        "جارٍ تنزيل ${arFilesGen(count)} • $percent%"

    override val sysDownloadTaskFallback = "مهمة تنزيل"
    override val sysBrowserReadyTitle = "FetchJet جاهز لمتصفحك"
    override val sysBrowserReadyText = "تظهر جودات الفيديو مباشرة في Firefox"
    override val sysTurnOff = "إيقاف"
    override val sysBrowserReadyChannelName = "إضافة المتصفح"
    override val sysBrowserReadyChannelDescription = "يظهر ما دام FetchJet جاهزًا لإضافة المتصفح"
    override val sysDownloadingFileFallback = "جارٍ تنزيل ملف"
    override val sysUnknownSize = "غير معروف"
    override val sysStatusCompleted = "مكتمل"
    override val sysStatusDownloading = "قيد التنزيل"
    override val sysDownloadCompleteTitle = "اكتمل التنزيل"
    override fun sysFinishedDownloading(size: String) = "انتهى التنزيل • $size"
    override val sysSentToFetchJet = "تم الإرسال إلى FetchJet"
    override val sysLinksChannelName = "روابط من المتصفح"
    override val sysLinksChannelDescription = "اضغط لفتح الرابط الذي استلمه FetchJet للتو"
    override val sysLinkReceivedTitle = "تم استلام رابط"
    override val sysLinkReceivedText = "اضغط لفتحه في FetchJet"

    override val stepCompleted = "مكتمل"
    override val stepSeeding = "جارٍ البذر"
    override val stepFetchingMetadata = "جارٍ جلب البيانات الوصفية"
    override val stepConnectingToPeers = "جارٍ الاتصال بالأقران"
    override val stepCheckingFiles = "جارٍ فحص الملفات"
    override val stepCheckingResumeData = "جارٍ فحص بيانات الاستئناف"
    override val stepDownloading = "جارٍ التنزيل"
    override val stepFinished = "انتهى"
    override val stepConnecting = "جارٍ الاتصال"
    override val stepPaused = "متوقف مؤقتًا"
    override val stepError = "خطأ"
    override val stepResolvingStreams = "جارٍ تحديد مصادر البث"
    override val stepDownloadingParts = "جارٍ تنزيل الأجزاء"
    override val stepPreparingDownload = "جارٍ تجهيز التنزيل"
    override val stepClippingVideo = "جارٍ قص الفيديو"
    override val stepDownloadingMedia = "جارٍ تنزيل الوسائط"
    override val stepAlreadyDownloaded = "تم تنزيله مسبقًا"
    override val stepDownloadingVideo = "جارٍ تنزيل الفيديو"
    override val stepDownloadingAudio = "جارٍ تنزيل الصوت"
    override val stepMergingAudioVideo = "جارٍ دمج الصوت والفيديو"
    override val stepConvertingToMp3 = "جارٍ التحويل إلى MP3"
    override val stepCuttingClip = "جارٍ قص المقطع"
    override val stepSavingToFolder = "جارٍ الحفظ في المجلد"
    override val stepFinalizingVideo = "جارٍ إنهاء الفيديو"
    override val stepConvertingVideo = "جارٍ تحويل الفيديو"
    override val stepRepackagingVideo = "جارٍ إعادة تغليف الفيديو"
    override val stepYtDlpFallback = "التحويل إلى yt-dlp"
    override val stepNotPlainStream = "ليس بثًا مباشرًا بسيطًا"
    override val stepSingleConnection = "اتصال واحد"
    override val stepServerNoRanges = "الخادم لا يدعم التنزيل المجزأ"
    override val stepAgeRestrictedExpiredCookies =
        "مقيَّد بالعمر: ملفات تعريف الارتباط منتهية في الملفات > FetchJet"
    override val stepAgeRestrictedPlaceCookies =
        "مقيَّد بالعمر: ضع cookies.txt في الملفات > FetchJet"
    override val stepPreparingMediaEngine =
        "جارٍ تجهيز محرك الوسائط داخل التطبيق (تشغيل Python وتحميل yt-dlp)"
    override val stepMediaEngineNotStarted = "لم يبدأ محرك الوسائط. اضغط «تنزيل الأدوات»."
    override fun stepNumber(step: Int, total: Int) = "الخطوة ${step} من ${total}"
    override fun stepSavingAs(ext: String) = "جارٍ الحفظ بصيغة .${ext}"
    override fun stepJoiningParts(count: Int) = "جارٍ دمج ${arPartsGen(count)} في MP4"
    override fun stepNoDataFor(seconds: Int) = "لا توجد بيانات منذ ${seconds} ث"
    override fun stepPeers(peers: Int, seeds: Int) = "${arPeers(peers)} (${arSeeds(seeds)})"
    override fun stepDownloadingFrom(host: String) = "جارٍ التنزيل من ${host}"
    override fun stepMediaEngineNotReady(error: String) = "محرك الوسائط غير جاهز: ${error}"
    override fun stepTorrentEngineFailed(error: String) = "تعذّر تشغيل محرك BitTorrent: ${error}"
    override fun stepReadyWith(details: String) = "جاهز: ${details}"

    override val audioCompressTitle = "ضغط الصوت"
    override val audioCompressOriginalSize = "الحجم الأصلي"
    override val audioCompressNewSize = "الحجم بعد الضغط"
    override val audioCompressReading = "جارٍ قراءة الملف…"
    override val audioCompressMode = "الوضع"
    override val audioCompressSimple = "بسيط"
    override val audioCompressAdvanced = "متقدم"
    override val audioCompressLevel = "مستوى الضغط"
    override val audioCompressLevelHint = "القيمة الأعلى تعني ضغطًا أكبر"
    override val audioCompressBitrate = "معدل البت"
    override val audioCompressSampleRate = "معدل العينة"
    override val audioCompressChannels = "القنوات"
    override val audioCompressFormat = "الصيغة"
    override val audioCompressAuto = "تلقائي"
    override val audioCompressMono = "أحادي"
    override val audioCompressStereo = "ستيريو"
    override val audioCompressStart = "ضغط"
    override val audioCompressRunning = "جارٍ الضغط…"
    override val audioCompressFailed = "تعذّر ضغط هذا الملف"
    override val audioCompressCopies = "نسخ مضغوطة"
    override val audioCompressDelete = "حذف"
    override val audioCompressHigherQuality = "جودة أعلى"
    override val audioCompressSmallerFile = "ملف أصغر"
    override val audioCompressNothing = "الملف صغير بالفعل، والضغط لن يجعله أصغر"

    // --- Device sync (ROADMAP-1.3.0 item 4) ---
    override val sysDeviceSendTitle = "ابعت لجهاز"
    override val sysDeviceNoDevices = "مفيش أجهزة على الشبكة"
    override val sysDeviceSearching = "بيدوّر على الأجهزة…"
    override val sysDeviceModeLink = "ابعت اللينك بس"
    override val sysDeviceModeLinkDesc = "الجهاز التاني هينزّله من الأول"
    override val sysDeviceModeTransfer = "انقل اللي اتنزّل"
    override val sysDeviceModeTransferDesc = "الجهاز التاني يكمّل من نفس النقطة"
    override val sysDeviceDeleteAfter = "امسحه من الجهاز ده بعد ما يتبعت"
    override val sysDeviceSend = "ابعت"
    override val sysDevicePairTitle = "اقرن الجهاز"
    override val sysDevicePairBody = "اتأكد إن الكود ده زي بعضه على الجهازين:"
    override val sysDevicePairConfirm = "الكود متطابق"
    override val sysDevicePairConnecting = "بيتصل…"
    override val sysDevicePairFailed = "الاقتران فشل"
    override val sysDeviceSending = "بيبعت…"
    override val sysDeviceWaitingAccept = "مستني الجهاز التاني يقبل…"
    override val sysDeviceSent = "اتبعت لـ {name}"
    override val sysDeviceSendFailed = "ماقدرش يبعت"
    override val sysDeviceReceived = "وصل تنزيل من {name}"
    override val visibleToDevices = "يظهر للأجهزة الأخرى"
    override val visibleToDevicesHint = "يمكن لأجهزة FetchJet الأخرى على هذه الشبكة العثور على هذا الجهاز وإرسال التنزيلات إليه. عند الإيقاف يظل بإمكانك الإرسال إليها."
    override val sysDeviceSearch = "ابحث عن جهاز"
    override val sysDeviceOthers = "أجهزة أخرى"
    override val sysDeviceNoMatch = "لا يوجد جهاز بهذا الاسم"
    override val sectionDevices = "الأجهزة"
    override val sectionDevicesBlurb = "أرسل التنزيلات بين أجهزتك"
    override val pairedDevicesTitle = "الأجهزة المقترنة"
    override val noPairedDevices = "لا توجد أجهزة مقترنة بعد. اقترن بجهاز من «إرسال إلى جهاز» في أي تنزيل."
    override val unpairDevice = "إلغاء الاقتران"
    override val summaryVisible = "ظاهر"
    override val summaryHidden = "مخفي"
    override val blockDevice = "حظر"
    override val unblockDevice = "إلغاء الحظر"
    override val blockedDevicesTitle = "الأجهزة المحظورة"
    override val noBlockedDevices = "لا توجد أجهزة محظورة"
    override val pairRefusedBlocked = "الجهاز الآخر حظر هذا الجهاز"
    override val pairRefusedWait = "انتظر {seconds} ث قبل أن تطلب من هذا الجهاز مرة أخرى"
    override val pairRefusedBusy = "الجهاز الآخر يرد على طلب اقتران آخر. حاول مرة أخرى بعد قليل."
    override val pairDeclined = "رفض الجهاز الآخر الاقتران. أرسل مرة أخرى للمحاولة من جديد."
    override val pairExpired = "لم يرد الجهاز الآخر في الوقت المحدد. أرسل مرة أخرى للمحاولة من جديد."
    override val sysDevicePairedLabel = "مقترن"
    override val sysDeviceThisDevice = "الجهاز ده"
    override val sysDeviceManualConnect = "اتصال يدوي بالـIP"
    override val sysDeviceConnect = "اتصال"
    override val sysDeviceOfferTitle = "تستلم التحميل ده؟"
    override val sysDeviceOfferFrom = "من {device}"
    override val sysDeviceOfferAccept = "قبول"
    override val sysDeviceOfferReject = "رفض"
    override val deviceNoAnswer = "الجهاز الآخر ما ردش، حاول تاني"
    override val stepPreparingFile = "تهيئة الملف"
    override val sysDeviceReceivingTitle = "جارٍ استلام تنزيل"
    override val sysDeviceReceiveFailed = "فشل الاستلام"
    override val mainSetupTitle = "اجعل FetchJet برنامج التنزيل الرئيسي"
    override val mainSetupText = "ضغطة واحدة تُعدّ كل ما يسمح به هذا الجهاز:"
    override val mainSetupTorrents = "فتح ملفات .torrent وروابط magnet بـ FetchJet"
    override val mainSetupClipboard = "مراقبة الحافظة بحثًا عن الروابط"
    override val mainSetupStartup = "تشغيل FetchJet عند تسجيل الدخول"
    override val mainSetupKeepReady = "إبقاء FetchJet جاهزًا للمتصفح"
    override val mainSetupExtension = "إضافة امتداد المتصفح"
    override val mainSetupLater = "يمكنك تغيير كل ذلك لاحقًا من الإعدادات."
    override val mainSetupAccept = "اجعله برنامجي للتنزيل"
    override val mainSetupGroup = "برنامج التنزيل الرئيسي"
    override val defaultAppRowTitle = "التطبيق الافتراضي للتورنت"
    override val defaultAppIsDefault = "FetchJet يفتح ملفات .torrent وروابط magnet"
    override val defaultAppNotDefault = "تطبيق آخر يفتح ملفات .torrent أو روابط magnet"
    override val defaultAppMakeDefault = "اجعله الافتراضي"
    override val defaultAppSystemPageHint = "ستُفتح إعدادات النظام: اختر FetchJet لـ .torrent و magnet هناك."
    override val mainSetupExtensionRow = "امتداد المتصفح"
    override val mainSetupExtensionConnected = "متصفح يرسل التنزيلات إلى FetchJet بالفعل"
    override val mainSetupExtensionMissing = "لا يوجد متصفح عليه الامتداد بعد"
}

private fun arCount(
    n: Int,
    one: String,
    two: String,
    few: String,
    many: String,
    other: String
): String = when {
    n == 1 -> one
    n == 2 -> two
    n % 100 in 3..10 -> "$n $few"
    n % 100 in 11..99 -> "$n $many"
    else -> "$n $other"
}

private fun arDownloadsGen(n: Int): String =
    arCount(n, "تنزيل واحد", "تنزيلين", "تنزيلات", "تنزيلًا", "تنزيل")

private fun arLinksGen(n: Int): String =
    arCount(n, "رابط واحد", "رابطين", "روابط", "رابطًا", "رابط")

private fun arVideosGen(n: Int): String =
    arCount(n, "فيديو واحد", "فيديوين", "فيديوهات", "فيديو", "فيديو")

private fun arFilesGen(n: Int): String =
    arCount(n, "ملف واحد", "ملفين", "ملفات", "ملفًا", "ملف")

private fun arDownloadsAtOnce(n: Int): String =
    arCount(n, "تنزيل واحد في كل مرة", "تنزيلان معًا", "تنزيلات معًا", "تنزيلًا معًا", "تنزيل معًا")

private fun arPartsGen(n: Int): String =
    arCount(n, "جزء واحد", "جزأين", "أجزاء", "جزءًا", "جزء")

private fun arPeers(n: Int): String =
    arCount(n, "نظير واحد", "نظيران", "نظراء", "نظيرًا", "نظير")

private fun arSeeds(n: Int): String =
    arCount(n, "مصدر واحد", "مصدران", "مصادر", "مصدرًا", "مصدر")
