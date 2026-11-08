package com.michelle.raouf.fetch_jet.core.i18n.data_provider

object JapaneseProvider : AppLocalization {
    override val localeCode = "ja"
    override val languageName = "日本語"
    override val ok = "OK"
    override val cancel = "キャンセル"
    override val close = "閉じる"
    override val save = "保存"
    override val delete = "削除"
    override val remove = "除去"
    override val open = "開く"
    override val copy = "コピー"
    override val copied = "コピーしました"
    override val retry = "再試行"
    override val pause = "一時停止"
    override val resume = "再開"
    override val apply = "適用"
    override val reset = "リセット"
    override val yes = "はい"
    override val no = "いいえ"
    override val back = "戻る"
    override val done = "完了"
    override val selectAll = "すべて選択"
    override val selectNone = "選択を解除"
    override val language = "言語"
    override val languageHint = "すべての画面の言語です。「システム」はデバイスの設定に従います。"
    override val systemLanguage = "システム"
    override val homeSettings = "設定"
    override val homeDownloads = "ダウンロード"

    override val homeTabAll = "すべて"
    override val homeTabActive = "進行中"
    override val homeTabDone = "完了"
    override val homeTabTorrent = "トレント"
    override val homeTabMedia = "メディア"
    override val homeSidebarTypeHeader = "種類"
    override val homeSearchDownloadsPlaceholder = "ダウンロードを検索"
    override val homeAdd = "追加"
    override val homeAddDownload = "ダウンロードを追加"
    override val homePauseAll = "すべて一時停止"
    override val homeResumeAll = "すべて再開"
    override fun homeSortBy(option: String) = "並べ替え: ${option}"
    override val homeLightTheme = "ライトテーマ"
    override val homeDarkTheme = "ダークテーマ"

    override fun homeUpdatingMediaTools(tool: String, percent: Int?) =
        "メディアツールを更新中 · ${tool}" + (percent?.let { " ${it}%" } ?: "…")

    override val homeSearchDownloadsByNamePlaceholder = "名前でダウンロードを検索…"
    override fun homeNoDownloadsMatch(query: String) =
        "「${query}」に一致するダウンロードはありません"

    override val homeNoTasksInView = "この表示にはタスクがありません"
    override val homeTryAnotherName = "別の名前で試してください"
    override val homeAddDownloadToGetStarted = "ダウンロードを追加して始めましょう"
    override fun homeDownloadingWaitingSummary(running: Int, waiting: Int) =
        "ダウンロード中 ${running} · 待機中 ${waiting}"

    override fun homeQueueStatus(maxAtOnce: Int) =
        "キュー: " + if (maxAtOnce <= 0) "制限なし" else "同時に ${maxAtOnce} 件"

    override fun homeSpeedLimitStatus(limit: String?) = "速度制限: " + (limit ?: "オフ")
    override val homeGroupDownloading = "ダウンロード中"
    override val homeGroupWaiting = "待機中"
    override val homeGroupPausedFailed = "一時停止・失敗"
    override val homeGroupCompleted = "完了"
    override fun homeSelectedCount(count: Int) = "${count} 件選択中"
    override val homeExport = "エクスポート"

    override fun homeStartsAt(time: String) = "${time} に開始"
    override val homeWaitingInQueue = "キューで待機中"
    override fun homeWaitingInQueuePosition(position: Int) = "キューで待機中（${position} 番目）"
    override val homeConnecting = "接続中…"
    override val homeStarting = "開始中…"
    override fun homeTimeLeft(eta: String) = "残り ${eta}"
    override fun homePausedState(percent: Int, restartsOnResume: Boolean) =
        "一時停止 · ${percent}%" + if (restartsOnResume) " · 再開時は最初から" else ""

    override fun homeFailedState(message: String?) = "失敗: " + (message ?: "エラー")
    override fun homeCompletedState(size: String?) = "完了" + (size?.let { " · ${it}" } ?: "")
    override val homeStartNow = "今すぐ開始"
    override val homeColumnName = "名前"
    override val homeColumnSize = "サイズ"
    override val homeColumnProgress = "進捗"
    override val homeColumnSpeed = "速度"
    override val homeColumnLeft = "残り"
    override val homeFilterAllDownloads = "すべてのダウンロード"
    override val homeFilterDownloading = "ダウンロード中"
    override val homeFilterWaiting = "待機中"
    override val homeFilterPaused = "一時停止中"
    override val homeFilterCompleted = "完了"
    override val homeFilterFailed = "失敗"
    override val homeFilterMedia = "動画と音声"
    override val homeFilterFiles = "ファイル"
    override val homeFilterTorrents = "トレント"

    override val homeCantResumeTitle = "このダウンロードは再開できません"
    override val homeCantResumeClipText =
        "ダウンロード中に切り出すクリップは、止めた所から続けられません。" +
                "今一時停止すると、再開時にクリップは最初からダウンロードし直します。"
    override val homeCantResumeServerText =
        "サーバーがレジュームに対応していません。今一時停止すると、再開時に" +
                "ダウンロードは最初からやり直しになります。"
    override val homePauseAnyway = "それでも一時停止"
    override val homeKeepDownloading = "ダウンロードを続ける"
    override val homeMagnetLink = "マグネットリンク"
    override val homeClipboardOfferTitle = "コピーしたリンクをダウンロードしますか？"
    override val homeClipboardOfferHint = "設定 > 一般 > クリップボードを監視 でオフにできます。"
    override val homeDownload = "ダウンロード"
    override val homeNotNow = "今はしない"
    override val bridgePairTitle = "このブラウザ拡張機能を許可しますか？"
    override val bridgePairText =
        "ブラウザ拡張機能が FetchJet へのリンク送信を求めています。FetchJet 拡張機能をインストールまたはリセットした直後の場合のみ許可してください。"
    override val bridgePairOldText =
        "古い FetchJet 拡張機能がリンクを送信しようとしています。ご自身のものなら許可してください。拡張機能を更新するとこの確認は不要になります。"
    override val bridgePairAllow = "許可"
    override val bridgePairBlock = "ブロック"
    override val extConnectionsGroup = "ブラウザ接続"
    override val extConnectionsFooter =
        "FetchJet へのリンク送信を許可またはブロックしたブラウザ拡張機能。"
    override val extAllowedLabel = "許可"
    override val extBlockedLabel = "ブロック"
    override val extRevoke = "取り消す"
    override val extUnblock = "ブロック解除"
    override val homeBrowserFallbackTitle = "代わりにブラウザでダウンロードしますか？"
    override val homeBrowserFallbackText =
        "FetchJet でこのファイルをキャンセルしました。ブラウザはリンクを渡した時点で" +
                "自身のダウンロードを止めたため、送り返した場合にのみ続行されます。"
    override val homeDownloadInBrowser = "ブラウザでダウンロード"
    override val homeNoDiscard = "いいえ、破棄する"

    override val homeQueueTitle = "キュー"
    override val homeQueueFooter =
        "上限を超えたダウンロードは待機し、どれかが終わると古い順に自動で始まります。" +
                "待機中のダウンロードで「今すぐ開始」を押すとキューを飛ばせます。"
    override val homeDownloadsAtSameTime = "同時ダウンロード数"
    override val homeNoLimit = "制限なし"
    override fun homeAtATime(count: Int) = "同時に ${count} 件"

    override val homePiecesLabel = "ピース"
    override fun homeSegmentsCount(count: Int) = "${count} セグメント"
    override fun homePiecesCount(count: Int) = "${count} ピース"
    override fun homeActiveCount(count: Int) = "進行中 ${count}"
    override fun homeWaitingCount(count: Int) = "待機中 ${count}"
    override fun homeDoneCount(count: Int) = "完了 ${count}"
    override fun homeSegmentsProgress(finished: Int, total: Int, downloading: String?) =
        "セグメント ${finished}/${total}" + (downloading?.let { " · ${it} をダウンロード中" } ?: "")

    override fun homeRetryingNote(note: String?) = "${note} · 再試行中"
    override fun clipStartAfterEnd(start: String, end: String) =
        "開始時間（${start}）は終了時間（${end}）より前にしてください"

    override fun clipEndExceedsVideo(end: String, duration: String) =
        "終了時間（${end}）が動画の長さ（${duration}）を超えています"

    override fun clipStartExceedsVideo(duration: String) =
        "開始時間が動画の長さ（${duration}）を超えています"

    override val setStartTimeTitle = "開始時間を設定（から）"
    override val setEndTimeTitle = "終了時間を設定（まで）"
    override val addDialogTitle = "新しいダウンロード"
    override val addDialogSubtitle = "リンクを入力してダウンロードを開始"
    override val addLinkLabel = "リンク"
    override val addImportTxt = ".txt を読み込む"
    override val openTorrentFile = ".torrent ファイルを開く"
    override val urlPlaceholder = "リンクまたはマグネットを貼り付け"
    override val addPaste = "貼り付け"
    override val addDownloadEngine = "ダウンロードエンジン"
    override val engineDirect = "直接"
    override val engineMedia = "メディア"
    override val engineTorrent = "トレント"
    override val addFileNameLabel = "ファイル名"
    override val findingName = "名前を取得中…"
    override val saveDestination = "保存先フォルダー"
    override val quickFolderDownloads = "ダウンロード"
    override val quickFolderDocuments = "ドキュメント"
    override val quickFolderMedia = "メディア"
    override val quickFolderDesktop = "デスクトップ"
    override val addRetryInspect = "再解析"
    override val addStarting = "開始中..."
    override val addInspecting = "解析中..."
    override val addInspectionFailed = "解析に失敗しました"
    override val addEnterUrl = "URL を入力"
    override fun scheduleLinks(n: Int) = "${n} 件のリンクを予約"
    override fun downloadLinks(n: Int) = "${n} 件のリンクをダウンロード"
    override val inspectVideoFirst = "先に動画を解析してください"
    override val inspectTorrentFirst = "先にトレントを解析してください"
    override val invalidTimeRange = "時間の範囲が無効です"
    override fun scheduleVideos(n: Int) = "${n} 本の動画を予約"
    override fun downloadVideos(n: Int) = "${n} 本の動画をダウンロード"
    override val addScheduleDownload = "ダウンロードを予約"
    override val addStartDownload = "ダウンロード開始"
    override val availableAudioFormats = "利用可能な音声形式"
    override val availableQualities = "利用可能な画質"
    override val subtitlesTitle = "字幕"
    override val subtitlesAuto = "自動"
    override val subtitlesEmbedInVideo = "動画に埋め込む"
    override val subtitlesSeparateFile = "別ファイル"
    override val addSelectAll = "すべて選択"
    override val addClearSelection = "クリア"
    override fun selectedOfAvailable(selected: Int, total: Int) =
        "選択: ${total} 件中 ${selected} 件"

    override fun qualityFormatLine(format: String, details: String) = "形式: ${format}${details}"
    override val inspectingVideoQualities = "動画の画質を解析中..."
    override fun readingCourseLectures(done: Int, total: Int) =
        "コースのレクチャーを読み取り中: ${done} / ${total}"

    override val readingCourseLecturesHint =
        "ブラウザ拡張機能がコースのタブで読み取っています: タブを開いたままにしてください"
    override val inspectingVideoQualitiesHint = "利用可能な解像度と音声ストリームを確認しています"
    override val videoInspectionFailed = "動画の解析に失敗しました"
    override val addRetryInspection = "再解析"
    override val mediaEngineStarting = "メディアエンジンを起動中..."
    override val mediaEngineNotReady = "メディアエンジンの準備ができていません"
    override val mediaEngineRequiresYtDlp =
        "動画ストリームの抽出には、インストール済みまたは内蔵の yt-dlp が必要です。"
    override val preparingEngine = "エンジンを準備中..."
    override val startMediaEngine = "メディアエンジンをダウンロード / 起動"
    override val inspectAudioFormats = "音声形式を解析して選択"
    override val inspectVideoQuality = "動画の画質を解析して選択"
    override fun clipSectionTitle(isAudio: Boolean) =
        if (isAudio) "音声の一部を切り出す（任意）" else "動画の一部を切り出す（任意）"

    override val clipSectionHint = "指定した時間の範囲だけをダウンロード"
    override fun mediaDurationFullRange(isAudio: Boolean, clock: String, friendly: String) =
        "${if (isAudio) "音声" else "動画"}の長さ: ${clock}（${friendly}）- 初期設定では全体を選択"

    override val clipStartTimeFrom = "開始時間（から）"
    override val clipEndTimeTo = "終了時間（まで）"
    override fun fullMediaChip(isAudio: Boolean, duration: String) =
        if (isAudio) "音声全体（${duration}）" else "動画全体（${duration}）"

    override val clipFirst5Min = "最初の 5 分"
    override val clipFirst15Min = "最初の 15 分"
    override val clipFirst30Min = "最初の 30 分"
    override val clipFirst1Hour = "最初の 1 時間"
    override val clipModeTitle = "クリップのダウンロード方法"
    override val clipOnlyTitle = "クリップだけ"
    override val clipOnlyLine1 = "選んだ部分だけをダウンロード"
    override val clipOnlyLine2 = "遅め: YouTube が速度を制限します"
    override val clipOnlyLine3 = "一時停止するとクリップは最初から"
    override fun fullThenCutTitle(isAudio: Boolean) =
        if (isAudio) "音声全体を取得して切り出し" else "動画全体を取得して切り出し"

    override fun fullThenCutLine1(isAudio: Boolean, sizeText: String) =
        if (isAudio) "音声全体をダウンロード${sizeText}" else "動画全体をダウンロード${sizeText}"

    override val fullThenCutLine2 = "速い: 全速でダウンロード"
    override val fullThenCutLine3 = "その後、選んだ部分だけを残します"
    override val fullThenCutLine4 = "一時停止 / 再開が使えます"
    override val inspectingTorrent = "トレントのメタデータを解析中..."
    override val inspectingTorrentHint = "ファイル一覧とピアを取得しています"
    override val torrentInspectionFailed = "トレントの解析に失敗しました"
    override val inspectTorrentFiles = "トレントのファイルを解析"
    override val disableTorrentUpload = "トレントのアップロードを無効化（ダウンロードのみ）"
    override val torrentFiles = "トレントのファイル"
    override val addDeselectAll = "すべて選択解除"
    override val torrentPriorityHigh = "高"
    override val torrentPriorityLow = "低"
    override val torrentPriorityNormal = "標準"
    override fun selectedOfLinks(selected: Int, total: Int) = "${total} 件中 ${selected} 件のリンク"
    override val readingTorrentFiles = "トレントのファイルを読み込み中…"
    override fun couldNotReadTorrentFiles(error: String) =
        "ファイルを読み込めませんでした（${error}）。すべてのファイルをダウンロードします。"

    override val linkTagVideo = "動画"
    override val linkTagAudio = "音声"
    override val linkTagTorrent = "トレント"
    override val linkTagFile = "ファイル"
    override val linkListMagnet = "マグネットリンク"
    override fun linkListYouTubeVideo(id: String) = "YouTube 動画 ${id}"
    override fun playlistSummary(videos: Int, selected: Int) =
        "プレイリスト · 動画 ${videos} 本 · ${selected} 本選択"

    override val playlistFolder = "フォルダーに保存"
    override val playlistFolderHint =
        "すべてのファイルがこのフォルダーに入ります。空にすると直接保存します。"
    override val qualityForAllVideos = "すべての動画の画質"
    override val qualityForAllVideosHint = "その画質がない動画は、最も近い低い画質になります。"
    override val downloadOnlyThisVideo = "代わりにこの動画だけをダウンロード"
    override val whenToStart = "開始のタイミング"
    override val scheduleNow = "今すぐ"
    override val scheduleIn30Min = "30 分後"
    override val scheduleIn1Hour = "1 時間後"
    override val scheduleAtTime = "時刻を指定…"
    override val scheduleDayLabel = "日"
    override val scheduleToday = "今日"
    override val scheduleTomorrow = "明日"
    override val scheduleIn2Days = "2 日後"
    override val scheduleIn3Days = "3 日後"
    override val scheduleInAWeek = "1 週間後"
    override val scheduleTimePassed =
        "今日のその時刻はすでに過ぎています。もっと後の時刻か別の日を選んでください。"
    override val scheduleHourLabel = "時"
    override val scheduleMinuteLabel = "分"
    override val scheduleSet = "設定"
    override val scheduleSetTime = "時刻を設定"
    override fun scheduleStartsAt(time: String) = "${time} に開始（FetchJet の起動が必要）"
    override val addConnectionsTitle = "接続数"
    override val connectionsAuto = "自動"
    override fun connectionsAutoWithDefault(n: Int) = "自動（${n}）"
    override fun pickerExceedsVideo(duration: String) = "動画の長さ（${duration}）を超えています"
    override fun pickerMustBeAfterStart(start: String) = "開始時間（${start}）より後にしてください"
    override val pickerStartZero = "最初（00:00）"
    override val pickerHours = "時間"
    override val pickerMinutes = "分"
    override val pickerSeconds = "秒"
    override fun pickerSelectedTime(time: String, summary: String) =
        "選択した時間: ${time}（${summary}）"

    override fun pickerEndOfVideo(time: String) = "動画の最後（${time}）"
    override fun pickerMin(time: String) = "最小（${time}）"
    override fun pickerApply(time: String) = "適用（${time}）"
    override val pickerInvalidTime = "無効な時間"
    override val dirPickerSelectFolder = "フォルダーを選択"
    override val dirPickerUp = "上へ"
    override fun dirPickerSubfolders(n: Int) = "サブフォルダー（${n}）"
    override val dirPickerHiddenOn = "隠しファイル: 表示"
    override val dirPickerHiddenOff = "隠しファイル: 非表示"
    override val dirPickerNewFolder = "新しいフォルダー"
    override val dirPickerFolderName = "フォルダー名…"
    override val dirPickerCreateFailed = "フォルダーを作成できませんでした"
    override val dirPickerCreate = "作成"
    override val dirPickerNoSubfolders =
        "このフォルダーにはサブフォルダーがありません。\nファイルはここに直接保存されます。"
    override val dirPickerHiddenTag = "（隠し）"
    override val speedLimitUnlimited = "無制限（最大）"
    override val speedPresetsTitle = "速度プリセット"
    override val speedPresetsFooter =
        "高速・中・低それぞれに速度を設定します。ヘッダーからすばやく選べます。カメは常に 512 KB/s です。"
    override val speedPresetHigh = "高"
    override val speedPresetFast = "高速"
    override val speedPresetMedium = "中"
    override val speedPresetLow = "低"
    override val speedScheduleTitle = "速度スケジュール"
    override val speedScheduleFooter =
        "指定した時間帯だけ速度を制限します。それ以外は無制限。"
    override val speedScheduleAdd = "時間帯を追加"
    override val speedScheduleFrom = "開始"
    override val speedScheduleTo = "終了"
    override val speedScheduleActive = "スケジュールの制限が有効"
    override val speedScheduleEmpty = "時間帯がありません。追加してください。"
    override val priorityLabel = "優先度"
    override val priorityHigh = "高"
    override val priorityNormal = "標準"
    override val priorityLow = "低"
    override val checksumLabel = "チェックサム（任意）"
    override val checksumHint = "sha256 または md5"
    override val checksumVerified = "チェックサム一致"
    override val checksumMismatch = "チェックサム不一致"
    override val checksumChecking = "チェックサムを確認中…"
    override val speedLimitNoLimit = "制限なし（最高速度）"
    override val speedLimitCustom = "カスタム..."
    override val speedLimitValue = "値"
    override val scheduleAm = "午前"
    override val schedulePm = "午後"
    override val detailsChipMedia = "メディア"
    override val detailsChipTorrent = "トレント"
    override val detailsChipFile = "ファイル"
    override val detailsChipResumable = "再開可能"
    override val detailsChipNoResume = "再開不可"
    override fun detailsClipChip(start: String, end: String?, cutAfterDownload: Boolean) =
        "クリップ ${start}–${end ?: "終了"}" + if (cutAfterDownload) " · ダウンロード後に切り出し" else ""

    override fun detailsTorrentFiles(selected: Int, total: Int) =
        "トレントのファイル（${total} 件中 ${selected} 件選択）"

    override val detailsPriorityHigh = "高"
    override val detailsPriorityLow = "低"
    override val detailsPriorityNormal = "標準"
    override val detailsStatusCompleted = "完了"
    override val detailsStatusPaused = "一時停止中"
    override val detailsStatusFailed = "失敗"
    override val detailsStatusWaiting = "待機中"
    override val detailsStatusQueued = "キュー内"
    override val detailsStatusNotStarted = "未開始"
    override val detailsUnknownSize = "不明"
    override val detailsMergingAudioVideo = "音声と動画を結合中..."
    override fun detailsTorrentSpeedUploadDisabled(download: String) = "DL: ${download}（UL 無効）"
    override fun detailsTorrentSpeed(download: String, upload: String) =
        "DL: ${download} | UL: ${upload}"

    override val detailsConnecting = "接続中..."
    override fun detailsStartsAt(time: String) = "${time} に開始"
    override fun detailsWaitingInQueuePosition(position: Int) = "キューで待機中（${position} 番目）"
    override val detailsWaitingInQueue = "キューで待機中"
    override val detailsDownloaded = "ダウンロード済み"
    override val detailsSpeed = "速度"
    override val detailsState = "状態"
    override val detailsTimeLeft = "残り時間"
    override val detailsAdded = "追加日時"
    override val detailsSavedTo = "保存先"
    override val detailsSource = "ソース"
    override val detailsPage = "ページ"
    override val detailsOpenFile = "ファイルを開く"
    override val detailsOpenFolder = "フォルダーを開く"
    override val detailsStartNow = "今すぐ開始"
    override val detailsRemoveFromList = "リストから除去"
    override val detailsCancelDownload = "ダウンロードをキャンセル"
    override val detailsHeaderDownload = "ダウンロード"
    override val detailsHeaderDetails = "詳細"
    override fun detailsAddedToday(clock: String) = "今日 ${clock}"
    override fun detailsAddedYesterday(clock: String) = "昨日 ${clock}"

    override fun removeDialogTitle(count: Int) =
        if (count > 1) "${count} 件のダウンロードを除去" else "ダウンロードを除去"

    override fun removeDialogMessage(count: Int) =
        if (count > 1) "これら ${count} 件のダウンロードタスクを除去しますか？"
        else "このダウンロードタスクを除去しますか？"

    override val removeDialogAlsoDeleteFiles = "ダウンロードしたファイルもディスクから削除する"

    override val duplicateDialogTitle = "ダウンロードは既に存在します"
    override val duplicateDialogSubtitle = "この項目は既にダウンロードキューにあります"
    override fun duplicateDialogStatus(status: String) = "状態: ${status}"
    override val duplicateDialogQuestion =
        "既存のダウンロードを再開しますか？それとも最初からやり直しますか？"
    override val duplicateDialogResumeExisting = "既存を再開"
    override val duplicateDialogRedownload = "再ダウンロード（最初から）"

    override val overwriteDialogTitle = "ファイルは既に存在します"
    override val overwriteDialogSubtitle = "保存先に同じ名前のファイルがあります"
    override val overwriteDialogFileNameLabel = "ファイル名: "
    override val overwriteDialogFolderLabel = "フォルダー: "
    override val overwriteDialogNewCopyLabel = "新しいコピー: "
    override val overwriteDialogQuestion =
        "新しいコピーとして保存しますか？それとも既存のファイルを上書きしますか？"

    override fun overwriteDialogSaveAsCopy(name: String) = "コピーとして保存（${name}）"
    override val overwriteDialogOverwriteExisting = "既存のファイルを上書き"

    override val desktopWindowTagline = "高速マルチエンジン・ダウンローダー"
    override val desktopTrayIdleTooltip = "FetchJet - バックグラウンドで実行中"
    override val desktopTrayDownloadsHeader = "FetchJet のダウンロード:"
    override fun desktopTrayMore(count: Int) = "ほか ${count} 件"
    override val desktopTrayShowApp = "アプリを表示"
    override val desktopTrayCloseApp = "アプリを終了"
    override val sectionGeneral = "一般"
    override val sectionGeneralBlurb = "アプリの見た目と起動のしかた"
    override val sectionDownloads = "ダウンロード"
    override val sectionDownloadsBlurb = "保存先と同時ダウンロード数"
    override val sectionBrowser = "ブラウザー拡張機能"
    override val sectionBrowserBlurb = "ブラウザーからリンクや動画を FetchJet に送る"
    override val sectionSpeed = "速度制限"
    override val sectionSpeedBlurb = "FetchJet が使う帯域を制限"
    override val sectionMedia = "メディアツール"
    override val sectionMediaBlurb = "動画サイト用の yt-dlp と FFmpeg"
    override val sectionTorrent = "BitTorrent"
    override val sectionTorrentBlurb = "ピア、アップロード、トレントエンジン"
    override val sectionUpdates = "アップデート"
    override val sectionUpdatesBlurb = "現在のバージョンと新しいリリース"
    override val summaryNoQueueLimit = "制限なし"
    override fun summaryAtATime(n: Int) = "同時に ${n} 件"
    override val summaryNoSpeedLimit = "制限なし"
    override val summarySetupNeeded = "設定が必要"
    override val summaryUploadingOff = "アップロード オフ"
    override val summaryUploadingOn = "アップロード オン"
    override fun summaryVersionAvailable(version: String) = "${version} が利用可能"
    override fun summaryVersion(version: String) = "バージョン ${version}"
    override val changesSavedAutomatically = "変更は自動で保存されます。"
    override val settingsAllSettings = "すべての設定"

    override val clipboardGroup = "クリップボード"
    override val clipboardFooter =
        "ファイル（zip、mp4、pdf、apk…）へのリンクやマグネットリンクをどこかでコピーすると、FetchJet がダウンロードするか確認します。"
    override val watchClipboard = "クリップボードを監視"
    override val watchClipboardOn = "オン · コピーしたファイルリンクのダウンロードを提案"
    override val settingOff = "オフ"
    override val alsoVideoPages = "動画ページも対象にする"
    override val alsoVideoPagesHint =
        "YouTube、Facebook、TikTok、Vimeo… のリンクも提案します（ファイル以外も）"
    override val diagnosticsGroup = "診断"
    override fun logSavedTo(path: String) =
        "保存しました: ${path} — 問題を報告するときはこのファイルを送ってください。"

    override val diagnosticsFooter =
        "アプリのログ（yt-dlp や各エンジンの動作）を「ダウンロード」フォルダーに保存します。"
    override val saveLogToDownloads = "ログを「ダウンロード」に保存"
    override val logCouldNotSave = "保存できませんでした"
    override val appearanceGroup = "外観"
    override val themeTitle = "テーマ"
    override val themeHint = "すべての画面をライトまたはダークで表示"
    override val settingsThemeDark = "ダーク"
    override val settingsThemeSystem = "システム"
    override val settingsThemeLight = "ライト"
    override val speedShownIn = "速度の単位"
    override val speedShownInHint =
        "ファイルサイズと同じバイト（MB/s）か、回線プランと同じビット（Mbps）"

    override val saveLocationGroup = "保存先"
    override val saveLocationFooter =
        "追加時に別のフォルダーを選ばない限り、新しいダウンロードはここに保存されます。"
    override val settingsFolderPath = "フォルダーのパス"
    override val settingsChooseFolder = "フォルダーを選択…"
    override val settingsUseDownloads = "「ダウンロード」を使う"
    override val settingsConnectionsGroup = "接続"
    override val connectionsFooter =
        "直接ダウンロードを何個のピースに分けるか。各ピースが 1 つの接続です。1 つ終わると残りの最大ピースの半分を引き継ぐので、空く接続はありません。「自動」はファイルサイズで決めます（4–8）。クライアントごとの接続数を制限するサーバーもあります。"
    override val settingsConnectionsPerDownload = "ダウンロードごとの接続数"
    override val settingsConnectionsAuto = "自動"
    override val organizeGroup = "整理"
    override val organizeFooter =
        "保存先の中にサブフォルダーを作ります: Video、Music、Archives、Documents、Programs、Images、Torrents。"
    override val sortIntoFolders = "種類ごとにフォルダーへ振り分け"
    override val sortIntoFoldersOn = "オン · 例: Downloads/Video/lecture.mp4"
    override val sortIntoFoldersOff = "オフ · すべてフォルダーに直接保存"

    override val speedLimitsFooter = "すぐに適用され、実行中のダウンロードで分け合います。"
    override val speedLimitsFooterIos =
        "すぐに適用され、実行中のダウンロードで分け合います。iOS では、すでにバックグラウンドで実行中の HTTP ダウンロードは速度が変わらず、制限はその後に開始したものに適用されます。"
    override val speedLimitDownload = "ダウンロード"
    override val speedLimitUploadTorrents = "アップロード（トレント）"

    override val torrentSharingGroup = "共有"
    override val downloadOnlyNoUpload = "ダウンロードのみ（アップロードしない）"
    override val downloadOnlyNoUploadHint =
        "アップロード帯域と通信量を節約します。共有しないピアのダウンロードを遅くするトラッカーもあります。"
    override val torrentEngineGroup = "エンジン"
    override val settingsReady = "準備完了"
    override val settingsNotReady = "未準備"

    override val extInstallGroup = "インストール"
    override val extensionInstallFooter =
        "拡張機能を入れると、動画やリンクに「FetchJet でダウンロード」ボタンが表示され、ブラウザーのダウンロードを FetchJet に渡せます。通信先はこのデバイス上のこのアプリだけです。"
    override val edgeAddedHint =
        "追加済み · Edge を再起動し、確認されたら「拡張機能をオンにする」をクリック"
    override val edgeFromStoreHint = "Microsoft Edge アドオンから · 自動で更新"
    override val openInEdgeStore = "Edge ストアで開く"
    override val edgeAddedNote = "完了: Edge を再起動すると、FetchJet をオンにするか確認されます。"
    override val extAddToEdge = "Edge に追加"
    override val firefoxFromStoreHint = "Firefox アドオンから · 自動で更新"
    override val extAddToFirefox = "Firefox に追加"
    override val chromeZipHint =
        "Chrome ウェブストアにはありません: デベロッパーモードで .zip をインストールしてください（手順はページに記載）。自動では更新されません。"
    override val getTheZip = ".zip を入手"
    override val firefoxAndroidHint =
        "このスマホの Firefox でこれを開いてください。送ったリンクはこのアプリに直接届きます。"
    override val keepReadyForBrowser = "ブラウザー用に待機させる"
    override val keepReadyOn =
        "動画の画質が Firefox 内に直接表示されます。オンの間は控えめな通知が表示されます。"
    override val keepReadyOff =
        "オフ: Firefox が FetchJet を開き、ここで画質を選びます。オン: 画質が Firefox に表示されます（通知が残ります）。"
    override val popUpOverBrowser = "ブラウザーの上に表示"
    override val popUpOverBrowserOn = "オン · Firefox がリンクを送ると FetchJet が自動で開きます"
    override val popUpOverBrowserOff =
        "オフ · 代わりに通知が表示されます。タップして「他のアプリの上に重ねて表示」を許可"

    override val mediaToolsGroup = "ツール"
    override val ytDlpHint = "YouTube などのサイトで動画と音声を見つけます"
    override val settingsMissing = "未インストール"
    override val ffmpegHint = "動画と音声の結合（1080p 以上）、クリップの切り出し、MP3 作成"
    override val ffmpegMissing = "未インストール · 720p まで"
    override fun jsRuntimeTitle(name: String) = "JS ランタイム · ${name}"
    override val jsRuntimeHint = "YouTube のチェックを解いて、すべての画質をより速く表示します"
    override fun missingPress(button: String) = "未インストール · 「${button}」を押してください"
    override val mediaToolsFolder = "ツールのフォルダー"
    override val mediaUpdatingTools = "更新中…"
    override val mediaDownloadingTools = "ツールをダウンロード中…"
    override fun mediaDownloadingTool(name: String) = "${name} をダウンロード中…"
    override fun mediaDownloadingToolPercent(name: String, percent: Int) =
        "${name} をダウンロード中… ${percent}%"

    override val mediaUpdateTools = "ツールを更新"
    override val mediaDownloadTheTools = "ツールをダウンロード"

    override val updateNewVersionAvailable = "新しいバージョンがあります"
    override fun updateVersionLine(version: String, current: String) =
        "FetchJet ${version}  ·  現在 ${current}"

    override val updateWhatsNew = "新機能"
    override val updateNoReleaseNotes = "リリースノートはありません。"
    override fun downloadingUpdate(percent: Int) = "アップデートをダウンロード中… ${percent}%"
    override val updateDownloadedAndroid =
        "アップデートをダウンロードしました。Android でインストールの確認を求められます。ダウンロードはリストに残ります。"
    override val updateDownloadedDesktop =
        "アップデートをダウンロードしました。FetchJet は終了してインストールし、再び開きます。実行中のダウンロードは一時停止され、後で再開できます。"
    override val storeInstallsItselfOpenNow =
        "Microsoft Store が自動でインストールします。今すぐ更新するにはストアを開いてください。"
    override val updateInstall = "インストール"
    override val updateRestartAndUpdate = "再起動して更新"
    override val updateDownloading = "ダウンロード中…"
    override val updateDownloadAndInstall = "ダウンロードしてインストール"
    override val updateOpenMicrosoftStore = "Microsoft Store を開く"
    override fun updateDownloadWithSize(size: String) = "ダウンロード（${size}）"
    override val updateOpenReleasePage = "リリースページを開く"
    override val updateSkipThisVersion = "このバージョンをスキップ"
    override val updateLater = "後で"
    override fun versionIsAvailable(version: String) = "バージョン ${version} が利用可能です"
    override val youHaveLatest = "最新のバージョンがインストールされています"
    override val updateSeeWhatsNew = "新機能を見る"
    override val updateChecking = "確認中…"
    override val updateCheckNow = "今すぐ確認"
    override val checkUpdatesAutomatically = "アップデートを自動で確認"
    override val checkUpdatesAutomaticallyHint =
        "FetchJet の起動時に新しいバージョンを確認します（1 日最大 2 回）。"
    override val downloadUpdatesAutomatically = "アップデートを自動でダウンロード"
    override val downloadUpdatesAutomaticallyHintAndroid =
        "新しいバージョンをバックグラウンドでダウンロードします。インストールの確認だけで済みます。"
    override val downloadUpdatesAutomaticallyHint =
        "新しいバージョンをバックグラウンドでダウンロードします。再起動の確認だけで済みます。"
    override val installingUpdates = "アップデートのインストール"
    override val installingUpdatesStore =
        "Microsoft Store が新しいバージョンを自動でインストールします。公開されると FetchJet がお知らせします。"
    override val installingUpdatesManual =
        "このデバイスでは、FetchJet が新しいバージョンをお知らせしてリンクを表示します。インストールはご自身で行ってください。"
    override val updateReleaseNotes = "リリースノート"
    override val updateReleaseNotesHint = "各バージョンと変更点"
    override val installHintReleasePage =
        "リリースページを開いて新しいバージョンをダウンロードしてください。"
    override val installHintApk =
        "APK をダウンロードして開くと、このバージョンに上書きでインストールされます。"
    override val installHintIpa =
        ".ipa をダウンロードし、FetchJet を入れたときと同じく AltStore / Sideloadly でインストールしてください。"
    override val installHintDmg =
        ".dmg をダウンロードして開き、FetchJet を「アプリケーション」にドラッグしてください（古いものと置き換え）。"
    override val installHintDeb =
        ".deb をダウンロードしてインストールしてください（ダブルクリック、または sudo apt install ./file.deb）。"
    override val installHintOther =
        "新しいバージョンをダウンロードし、このバージョンに上書きでインストールしてください。"

    override val startupGroup = "起動"
    override val startupStoreFooter =
        "ストア版は Windows が起動します: 設定 > アプリ > スタートアップ で FetchJet をオンにしてください。"
    override val startupStartAtLogin = "ログイン時に FetchJet を起動"
    override val startupManagedByWindows = "Windows が管理"
    override val startupOpenWindowsSettings = "Windows の設定を開く"
    override val startupStartHidden = "トレイに隠して起動"
    override val startupStartHiddenHint =
        "ログイン時に起動したときは、ウィンドウを開かずトレイに常駐します。"
    override val startupStartAtLoginHint =
        "予約やキューのダウンロードが続行され、ブラウザー拡張機能が常にアプリを見つけられます。"

    override fun startupCouldNotChange(message: String) = "設定を変更できませんでした: ${message}"
    override val whenDoneGroup = "ダウンロード完了後"
    override val whenDoneHint = "このセッションのみ。実行前に 60 秒間キャンセルできます。"
    override val whenDoneNothing = "何もしない"
    override val whenDoneExit = "FetchJet を終了"
    override val whenDoneSleep = "スリープ"
    override val whenDoneShutDown = "シャットダウン"
    override val whenDoneTitle = "すべてのダウンロードが完了しました"
    override fun whenDoneExitIn(seconds: Int) = "${seconds} 秒後に FetchJet を終了します。"
    override fun whenDoneSleepIn(seconds: Int) = "${seconds} 秒後にスリープします。"
    override fun whenDoneShutDownIn(seconds: Int) = "${seconds} 秒後にシャットダウンします。"
    override val whenDoneNow = "今すぐ"
    override fun whenDoneBanner(action: String) = "ダウンロード完了後：${action}"
    override fun whenDoneFailed(message: String) = "実行できません：${message}"
    override val sectionNetwork = "ネットワーク"
    override val sectionNetworkBlurb = "プロキシとサイトのログイン"
    override val netProxyGroup = "プロキシ"
    override val netProxyOff = "オフ"
    override val netProxyHost = "サーバー"
    override val netProxyPort = "ポート"
    override val netProxyUser = "ユーザー名（任意）"
    override val netProxyPassword = "パスワード"
    override val netUser = "ユーザー名"
    override val netSave = "保存"
    override val netSaved = "保存しました"
    override val netProxyHint =
        "ダウンロード、動画ダウンロード（yt-dlp）、トレントで使われます。保存後に開始したダウンロードに適用されます。"
    override val netProxyHintIos =
        "ダウンロードと動画ダウンロードで使われます。iOS のトレントは直接接続します。保存後に開始したダウンロードに適用されます。"
    override val netProxyBadPort = "サーバーと 1〜65535 のポートを入力してください"
    override val netLoginsGroup = "サイトのログイン"
    override val netLoginsHint =
        "ユーザー名とパスワードを求めるダウンロード用（HTTP basic auth）。サイトにはサブドメインも含まれます。"
    override val netLoginSite = "サイト（example.com）"
    override val netLoginAdd = "追加"
    override val netLoginsEmpty = "保存されたログインはありません"
    override val netRemove = "削除"
    override fun netLoginsCount(n: Int) = "ログイン ${n} 件"
    override val sysTabAllDownloads = "すべてのダウンロード"
    override val sysTabActive = "進行中"
    override val sysTabCompleted = "完了"
    override val sysTabMediaExtractor = "メディア抽出"
    override val sysTabSettings = "設定"
    override val sysSortNewestFirst = "新しい順"
    override val sysSortOldestFirst = "古い順"
    override val sysSortLargestFirst = "大きい順"
    override val sysSortSmallestFirst = "小さい順"
    override val sysSortNameAz = "名前 A–Z"
    override val sysEnterValidUrlOrMagnet = "有効な URL またはマグネットリンクを入力してください"
    override val sysCannotStartVideoInspectionFailed =
        "ダウンロードを開始できません: 動画の解析に失敗しました。"
    override val sysCannotStartTorrentInspectionFailed =
        "ダウンロードを開始できません: トレントの解析に失敗したか、ファイルが選択されていません。"
    override val sysClipEndExceedsDuration = "クリップの終了時間が動画の長さを超えています。"
    override val sysClipStartAfterEnd = "クリップの開始時間は終了時間より前にしてください。"
    override fun sysStartedDownload(name: String) = "ダウンロードを開始しました: ${name}"
    override val sysFailedToQueueDownload = "ダウンロードをキューに追加できませんでした"
    override fun sysQualitiesNotAdded(count: Int) = "${count} 件の画質を追加できませんでした。"
    override fun sysAddedQualityDownloads(count: Int, title: String) =
        "「${title}」のダウンロードを ${count} 件追加しました"

    override val sysSelectAtLeastOnePlaylistVideo = "プレイリストの動画を 1 本以上選んでください。"
    override fun sysVideosNotAdded(count: Int) = "${count} 本の動画を追加できませんでした。"
    override fun sysAddedPlaylistVideos(count: Int, playlistTitle: String) =
        "「${playlistTitle}」から動画を ${count} 本追加しました"

    override val sysSelectAtLeastOneLink = "リンクを 1 つ以上選んでください。"
    override fun sysAddedLinkDownloads(added: Int, skipped: Int) =
        "ダウンロードを ${added} 件追加しました" + (if (skipped > 0) "（${skipped} 件はリストに既にあるか失敗）" else "")

    override val sysCouldNotReadTorrent = "トレントを読み込めませんでした"
    override fun sysCouldNotStartDownload(reason: String) =
        "ダウンロードを開始できませんでした: ${reason}"

    override val sysUnknownError = "不明なエラー"
    override fun sysQueuedFromBrowser(added: Int, skipped: Int) =
        "ブラウザーからダウンロードを ${added} 件キューに追加しました" + (if (skipped > 0) "（${skipped} 件スキップ）" else "")

    override val sysEnterTorrentOrMagnetFirst =
        "先にトレントまたはマグネットリンクを入力してください"
    override val sysFailedToParseTorrentMetadata = "トレントのメタデータを解析できませんでした"
    override val sysEnterMediaUrlFirst = "先にメディアの URL を入力してください"
    override fun sysUnableToFetchFormatsWithReason(reason: String) =
        "動画の形式を取得できません: ${reason}"

    override val sysUnableToFetchFormats =
        "動画の形式を取得できません。URL かインターネット接続を確認してください。"
    override val sysFailedToInspectMediaQualities = "メディアの画質を解析できませんでした"
    override fun sysTorrentEngineError(message: String) = "BitTorrent エンジンのエラー: ${message}"
    override fun sysTorrentEngineInstallFailed(message: String) =
        "BitTorrent エンジンのインストールに失敗しました: ${message}"

    override val sysMediaToolsDownloaded = "メディアツールをダウンロードしました！"
    override val sysMediaToolsDownloadFailed = "メディアツールのダウンロードに失敗しました"
    override val sysMediaToolsUpdated = "メディアツールを更新しました"
    override fun sysSavedLinks(count: Int, path: String) =
        "${count} 件のリンクを ${path} に保存しました"

    override fun sysCouldNotSaveLinks(reason: String) = "リンクを保存できませんでした: ${reason}"
    override val sysErrorFallback = "エラー"
    override val sysDownloadFailed = "ダウンロードに失敗しました"
    override val sysDownloadFailedNetwork = "ネットワークエラーでダウンロードに失敗しました"
    override val sysCouldNotStart = "開始できませんでした"
    override val sysUnknownErrorCapital = "不明なエラー"
    override fun sysCouldNotSaveFileTo(dir: String) = "${dir} にファイルを保存できませんでした"
    override val sysPlaylistFallbackTitle = "プレイリスト"
    override val sysPlaylistQualityBest = "最高"
    override val sysPlaylistQualityAudioOnly = "音声のみ"
    override val sysToolsReadyYtDlpFfmpeg =
        "準備完了: yt-dlp と ffmpeg をインストール済み（1080p 以上に完全対応）"
    override val sysToolsReadyYtDlpNoFfmpeg =
        "準備完了: yt-dlp をインストール済み（ffmpeg なし・720p まで）"
    override val sysToolsNotInstalled =
        "未インストールです。下の「ツールをダウンロード」をクリックしてください。"
    override val sysToolsInstalledBuiltIn = "インストール済み・準備完了（内蔵）"
    override fun sysYtDlpVersionReady(version: String) = "yt-dlp ${version}（準備完了）"
    override val sysMediaEngineBridgeNotRegistered =
        "メディアエンジンのブリッジが登録されていません"
    override val sysWebKitBuiltIn = "WebKit（内蔵）"
    override val sysTorrentEngineBuiltIn = "準備完了: BitTorrent エンジン内蔵"
    override fun sysTorrentEngineReadyVersion(version: String) =
        "準備完了: BitTorrent エンジン（libtorrent ${version}）"

    override fun sysTorrentEngineFailedToLoad(message: String) =
        "BitTorrent エンジンを読み込めませんでした: ${message}"

    override val sysTorrentEngineAvailable =
        "準備完了: BitTorrent エンジン利用可能（最初のトレントで読み込み）"
    override val sysTorrentEngineMissing =
        "BitTorrent エンジンがありません。下の「ツールをダウンロード」をクリックしてください。"
    override val sysTorrentEngineNotAvailable = "このビルドでは BitTorrent エンジンを利用できません"
    override fun sysCouldNotStartInstaller(message: String) =
        "インストーラーを起動できませんでした: ${message}"

    override val sysCheckingForUpdates = "アップデートを確認中…"
    override val sysNoPublishedRelease = "公開されたリリースはまだありません。"
    override fun sysVersionNoChangesForDevice(version: String, current: String) =
        "バージョン ${version} にはこのデバイス向けの変更はありません。このデバイスの最新ビルド（${current}）を使用中です。"

    override fun sysVersionAvailable(version: String) = "バージョン ${version} が利用可能です。"
    override fun sysUpToDate(current: String) = "最新の状態です（${current}）。"
    override fun sysCouldNotCheckForUpdates(reason: String) =
        "アップデートを確認できませんでした: ${reason}"

    override val sysNetworkError = "ネットワークエラー"
    override val sysUpdateNoInstallerForSystem =
        "アップデートのダウンロードに失敗しました: このリリースにはこのシステム用のインストーラーがありません。"

    override fun sysDownloadingAsset(name: String) = "${name} をダウンロード中…"
    override fun sysVersionReadyToInstall(version: String) =
        "バージョン ${version} をインストールする準備ができました。"

    override fun sysUpdateDownloadFailed(message: String) =
        "アップデートのダウンロードに失敗しました: ${message}"

    override val sysAllowUnknownAppsThenInstall =
        "FetchJet に「不明なアプリのインストール」を許可してから、もう一度「インストール」をタップしてください。"
    override val sysDownloadsChannelName = "FetchJet のダウンロード"
    override val sysDownloadsChannelDescription = "ダウンロードの進行状況をリアルタイムで表示します"
    override val sysNotificationAppTitle = "FetchJet ダウンローダー"
    override val sysAppRunningInBackground = "アプリはバックグラウンドで実行中です"
    override val sysNotificationDownloadsFallback = "ダウンロード"
    override fun sysFilesDownloading(count: Int) = "${count} 件のファイルをダウンロード中"
    override fun sysPercentActive(percent: Int, count: Int) = "${percent}% • 進行中 ${count}"
    override fun sysActiveCount(count: Int) = "進行中 ${count}"
    override fun sysDownloadCompleteSize(size: String) = "ダウンロード完了 • ${size}"
    override fun sysMoreDownloading(count: Int) = "+ ほか ${count} 件をダウンロード中"
    override val sysComplete = "完了"
    override fun sysNotificationDownloadsTitle(speed: String) = "FetchJet のダウンロード（${speed}）"
    override fun sysDownloadingPercent(count: Int, percent: Int) =
        "${count} 件ダウンロード中 • ${percent}%"

    override val sysDownloadTaskFallback = "ダウンロードタスク"
    override val sysBrowserReadyTitle = "FetchJet はブラウザー用に待機中です"
    override val sysBrowserReadyText = "動画の画質が Firefox 内に直接表示されます"
    override val sysTurnOff = "オフにする"
    override val sysBrowserReadyChannelName = "ブラウザー拡張機能"
    override val sysBrowserReadyChannelDescription =
        "FetchJet がブラウザー拡張機能用に待機している間に表示されます"
    override val sysDownloadingFileFallback = "ファイルをダウンロード中"
    override val sysUnknownSize = "不明"
    override val sysStatusCompleted = "完了"
    override val sysStatusDownloading = "ダウンロード中"
    override val sysDownloadCompleteTitle = "ダウンロード完了"
    override fun sysFinishedDownloading(size: String) = "ダウンロードが完了しました • ${size}"
    override val sysSentToFetchJet = "FetchJet に送信しました"
    override val sysLinksChannelName = "ブラウザーからのリンク"
    override val sysLinksChannelDescription = "タップすると FetchJet が受け取ったリンクを開きます"
    override val sysLinkReceivedTitle = "リンクを受信しました"
    override val sysLinkReceivedText = "タップして FetchJet で開く"

    override val stepCompleted = "完了"
    override val stepSeeding = "シード中"
    override val stepFetchingMetadata = "メタデータを取得中"
    override val stepConnectingToPeers = "ピアに接続中"
    override val stepCheckingFiles = "ファイルを確認中"
    override val stepCheckingResumeData = "再開データを確認中"
    override val stepDownloading = "ダウンロード中"
    override val stepFinished = "終了"
    override val stepConnecting = "接続中"
    override val stepPaused = "一時停止"
    override val stepError = "エラー"
    override val stepResolvingStreams = "ストリームを解析中"
    override val stepDownloadingParts = "パーツをダウンロード中"
    override val stepPreparingDownload = "ダウンロードを準備中"
    override val stepClippingVideo = "動画を切り抜き中"
    override val stepDownloadingMedia = "メディアをダウンロード中"
    override val stepAlreadyDownloaded = "ダウンロード済み"
    override val stepDownloadingVideo = "動画をダウンロード中"
    override val stepDownloadingAudio = "音声をダウンロード中"
    override val stepMergingAudioVideo = "音声と動画を結合中"
    override val stepConvertingToMp3 = "MP3 に変換中"
    override val stepCuttingClip = "クリップを切り出し中"
    override val stepSavingToFolder = "フォルダーに保存中"
    override val stepFinalizingVideo = "動画を仕上げ中"
    override val stepConvertingVideo = "動画を変換中"
    override val stepRepackagingVideo = "動画を再パッケージ中"
    override val stepYtDlpFallback = "yt-dlp に切り替え"
    override val stepNotPlainStream = "通常のストリームではありません"
    override val stepSingleConnection = "単一接続"
    override val stepServerNoRanges = "サーバーが範囲指定に対応していません"
    override val stepAgeRestrictedExpiredCookies =
        "年齢制限: 「ファイル > FetchJet」の Cookie が期限切れです"
    override val stepAgeRestrictedPlaceCookies =
        "年齢制限: cookies.txt を「ファイル > FetchJet」に置いてください"
    override val stepPreparingMediaEngine =
        "アプリ内メディアエンジンを準備中（Python を起動、yt-dlp を読み込み中）"
    override val stepMediaEngineNotStarted =
        "メディアエンジンが起動していません。「ツールをダウンロード」をタップしてください。"

    override fun stepNumber(step: Int, total: Int) = "ステップ ${step}/${total}"
    override fun stepSavingAs(ext: String) = ".${ext} として保存中"
    override fun stepJoiningParts(count: Int) = "${count} 個のパーツを MP4 に結合中"
    override fun stepNoDataFor(seconds: Int) = "${seconds} 秒間データなし"
    override fun stepPeers(peers: Int, seeds: Int) = "ピア ${peers}（シード ${seeds}）"
    override fun stepDownloadingFrom(host: String) = "${host} からダウンロード中"
    override fun stepMediaEngineNotReady(error: String) =
        "メディアエンジンの準備ができていません: ${error}"

    override fun stepTorrentEngineFailed(error: String) =
        "BitTorrent エンジンを起動できませんでした: ${error}"

    override fun stepReadyWith(details: String) = "準備完了: ${details}"

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
    override val visibleToDevices = "他のデバイスに表示"
    override val visibleToDevicesHint = "このネットワーク上の他の FetchJet デバイスがこのデバイスを見つけて、ダウンロードを送れます。オフでも、こちらからは送信できます。"
    override val sysDeviceSearch = "デバイスを検索"
    override val sysDeviceOthers = "その他のデバイス"
    override val sysDeviceNoMatch = "一致するデバイスはありません"
    override val sectionDevices = "デバイス"
    override val sectionDevicesBlurb = "デバイス間でダウンロードを送信"
    override val pairedDevicesTitle = "ペアリング済みのデバイス"
    override val noPairedDevices = "ペアリング済みのデバイスはまだありません。ダウンロードの「デバイスに送信」からペアリングしてください。"
    override val unpairDevice = "ペアリング解除"
    override val summaryVisible = "表示"
    override val summaryHidden = "非表示"
    override val blockDevice = "ブロック"
    override val unblockDevice = "ブロック解除"
    override val blockedDevicesTitle = "ブロック中のデバイス"
    override val noBlockedDevices = "ブロック中のデバイスはありません"
    override val pairRefusedBlocked = "相手のデバイスがこのデバイスをブロックしました"
    override val pairRefusedWait = "このデバイスに再度リクエストするまで {seconds} 秒お待ちください"
    override val pairRefusedBusy = "相手のデバイスは別のペアリング要求に応答中です。少し待ってからもう一度お試しください。"
    override val pairDeclined = "相手のデバイスがペアリングを拒否しました。もう一度送信すると再試行できます。"
    override val sysDevicePairedLabel = "Paired"
    override val sysDeviceThisDevice = "This device"
    override val sysDeviceManualConnect = "Connect by IP"
    override val sysDeviceConnect = "Connect"
}
