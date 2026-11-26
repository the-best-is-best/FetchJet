package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.core.i18n.start.start
import com.michelle.raouf.fetch_jet.core.security.DeviceAuth
import com.michelle.raouf.fetch_jet.core.settings.AppSettings
import com.michelle.raouf.fetch_jet.core.settings.AppSettingsRepository
import com.michelle.raouf.fetch_jet.core.settings.BehaviorSettings
import com.michelle.raouf.fetch_jet.domain.model.DownloadStatus
import com.michelle.raouf.fetch_jet.domain.model.DownloadTask
import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.backup.AppBackup
import com.michelle.raouf.fetch_jet.feature.backup.BackupExport
import com.michelle.raouf.fetch_jet.feature.backup.BackupFailure
import com.michelle.raouf.fetch_jet.feature.backup.devicePlatformId
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenStore
import com.michelle.raouf.fetch_jet.feature.queue_manager.LearnedMediaHosts
import com.michelle.raouf.fetch_jet.feature.queue_manager.QueueManagerRepository
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardRecoveryBackupTest {
    private lateinit var home: File
    private lateinit var home2: File
    private lateinit var repo: QueueManagerRepository
    private var repo2: QueueManagerRepository? = null
    private var vm: DashboardViewModel? = null
    private var oldHome: String? = null

    @BeforeTest
    fun setUp() {
        oldHome = System.getProperty("user.home")
        home = File.createTempFile("fjhome", "").also { it.delete(); it.mkdirs() }
        home2 = File.createTempFile("fjhome2", "").also { it.delete(); it.mkdirs() }
        System.setProperty("user.home", home.absolutePath)
        HiddenStore.resetForTest(File(home, "hidden-test.json").absolutePath)
        LearnedMediaHosts.resetForTest(File(home, "learned.txt").absolutePath)
        LocalizationManager.start()
        repo = QueueManagerRepository()
    }

    @AfterTest
    fun tearDown() {
        DeviceAuth.handler = null
        runCatching { vm?.deviceSync?.stop() }
        vm?.scope?.cancel()
        repo.scope.cancel()
        (repo.httpEngine as? com.michelle.raouf.fetch_jet.feature.http_downloader.HttpChunkDownloader)?.scope?.cancel()
        repo2?.scope?.cancel()
        (repo2?.httpEngine as? com.michelle.raouf.fetch_jet.feature.http_downloader.HttpChunkDownloader)?.scope?.cancel()
        HiddenStore.resetForTest(File(home, "hidden-gone.json").absolutePath)
        LearnedMediaHosts.resetForTest(null)
        BehaviorSettings.setSortIntoFolders(false)
        BehaviorSettings.setCategoryFolder("Video", "")
        oldHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
        home2.deleteRecursively()
    }

    private fun waitFor(what: String, check: () -> Boolean) = runBlocking {
        try {
            withTimeout(30_000) { while (!check()) delay(50) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("timed out waiting for $what")
        }
    }

    private fun viewModel(): DashboardViewModel = DashboardViewModel(repo).also { vm = it }

    private fun task(id: String, status: DownloadStatus, dest: String, extra: DownloadTask.() -> DownloadTask = { this }) =
        DownloadTask(
            id = id,
            url = "https://example.invalid/$id.bin?sig=abc",
            fileName = "$id.bin",
            destinationPath = dest,
            engineType = EngineType.HTTP_DIRECT,
            status = status,
            totalBytes = 1000,
            downloadedBytes = if (status == DownloadStatus.COMPLETED) 1000 else 400,
            progress = if (status == DownloadStatus.COMPLETED) 1f else 0.4f,
            createdAtEpochMs = 5
        ).extra()

    private fun seed(repository: QueueManagerRepository) {
        val dest = File(home, "Downloads").absolutePath
        repository.httpEngine.enqueueTask(task("done1", DownloadStatus.COMPLETED, dest))
        repository.httpEngine.enqueueTask(
            task("part1", DownloadStatus.PAUSED, dest) { copy(requestHeaders = mapOf("Cookie" to "SECRETCOOKIE=1", "Referer" to "https://example.invalid/")) }
        )
        waitFor("seeded") { repository.allTasksFlow.value.map { it.id }.containsAll(listOf("done1", "part1")) }
    }

    private fun switchToSecondHome(): QueueManagerRepository {
        System.setProperty("user.home", home2.absolutePath)
        return QueueManagerRepository().also { repo2 = it }
    }

    private fun ready(repository: QueueManagerRepository): BackupExport.Ready =
        assertNotNull(AppBackup.create(repository) as? BackupExport.Ready)

    @Test
    fun forgotPinOpensTheResetOnlyAfterTheDeviceConfirms() {
        assertTrue(HiddenStore.setPin("1234"))
        val model = viewModel()
        waitFor("lock type") { model.uiState.value.hidden.hasLock }
        assertFalse(model.uiState.value.hidden.canResetPin)
        DeviceAuth.handler = { true }
        assertTrue(model.uiState.value.hidden.canResetPin)
        model.onEvent(DashboardUiEvent.OnHiddenResetPin("5678"))
        assertEquals(HiddenNotice.RESET_EXPIRED, model.uiState.value.hidden.notice)
        assertFalse(model.uiState.value.hidden.unlocked)
        model.onEvent(DashboardUiEvent.OnHiddenNoticeShown)

        model.onEvent(DashboardUiEvent.OnHiddenForgotPin)
        waitFor("allowed") { model.uiState.value.hidden.pinResetAllowed }
        model.onEvent(DashboardUiEvent.OnHiddenResetPin("12"))
        assertEquals(HiddenError.INVALID_PIN, model.uiState.value.hidden.error)
        assertTrue(model.uiState.value.hidden.pinResetAllowed)
        model.onEvent(DashboardUiEvent.OnHiddenResetPin("5678"))
        val h = model.uiState.value.hidden
        assertTrue(h.unlocked)
        assertFalse(h.pinResetAllowed)
        assertNull(h.error)
        assertEquals(HiddenNotice.RESET_DONE, h.notice)
        model.onEvent(DashboardUiEvent.OnHiddenLock)
        model.onEvent(DashboardUiEvent.OnHiddenUnlock("1234"))
        assertFalse(model.uiState.value.hidden.unlocked)
        model.onEvent(DashboardUiEvent.OnHiddenUnlock("5678"))
        assertTrue(model.uiState.value.hidden.unlocked)
    }

    @Test
    fun aRefusedOrFailedDeviceAuthDoesNotAllowTheReset() = runBlocking {
        assertTrue(HiddenStore.setPin("1234"))
        val model = viewModel()
        DeviceAuth.handler = { false }
        model.onEvent(DashboardUiEvent.OnHiddenForgotPin)
        delay(300)
        assertFalse(model.uiState.value.hidden.pinResetAllowed)
        assertNull(model.uiState.value.hidden.notice)
        DeviceAuth.handler = { error("no lock screen") }
        model.onEvent(DashboardUiEvent.OnHiddenForgotPin)
        waitFor("auth failed") { model.uiState.value.hidden.notice == HiddenNotice.AUTH_FAILED }
        assertFalse(model.uiState.value.hidden.pinResetAllowed)
        model.onEvent(DashboardUiEvent.OnHiddenResetPin("5678"))
        assertEquals(HiddenNotice.RESET_EXPIRED, model.uiState.value.hidden.notice)
        DeviceAuth.handler = null
        model.onEvent(DashboardUiEvent.OnHiddenForgotPin)
        assertFalse(model.uiState.value.hidden.canResetPin)
    }

    @Test
    fun lockingEndsTheAllowance() {
        assertTrue(HiddenStore.setPin("1234"))
        val model = viewModel()
        DeviceAuth.handler = { true }
        model.onEvent(DashboardUiEvent.OnHiddenForgotPin)
        waitFor("allowed") { model.uiState.value.hidden.pinResetAllowed }
        model.onEvent(DashboardUiEvent.OnHiddenLock)
        assertFalse(model.uiState.value.hidden.pinResetAllowed)
        model.onEvent(DashboardUiEvent.OnHiddenResetPin("5678"))
        assertEquals(HiddenNotice.RESET_EXPIRED, model.uiState.value.hidden.notice)
        assertFalse(model.uiState.value.hidden.unlocked)
    }

    @Test
    fun theRecoveryCodeNeedsAnUnlockedAreaAndIsClearedWhenShown() {
        assertTrue(HiddenStore.setPin("1234"))
        assertTrue(HiddenStore.setEncrypted("t1", ByteArray(32) { 1 }, "a.mp4", "x.fjh"))
        val model = viewModel()
        model.onEvent(DashboardUiEvent.OnHiddenLock)
        model.onEvent(DashboardUiEvent.OnHiddenShowRecoveryCode)
        assertNull(model.uiState.value.hidden.recoveryCode)
        model.onEvent(DashboardUiEvent.OnHiddenUnlock("1234"))
        model.onEvent(DashboardUiEvent.OnHiddenShowRecoveryCode)
        val code = assertNotNull(model.uiState.value.hidden.recoveryCode)
        assertEquals(code, HiddenStore.recoveryCode())
        model.onEvent(DashboardUiEvent.OnHiddenRecoveryCodeShown)
        assertNull(model.uiState.value.hidden.recoveryCode)
        model.onEvent(DashboardUiEvent.OnHiddenShowRecoveryCode)
        model.onEvent(DashboardUiEvent.OnHiddenLock)
        assertEquals(code, model.uiState.value.hidden.recoveryCode)
        model.onEvent(DashboardUiEvent.OnHiddenRecoveryCodeShown)
        assertNull(model.uiState.value.hidden.recoveryCode)
    }

    @Test
    fun theCodeIsShownAfterTheNoticeEvenWhileTheAreaIsLocked() {
        assertTrue(HiddenStore.setPin("1234"))
        val model = viewModel()
        model.onEvent(DashboardUiEvent.OnHiddenLock)
        assertFalse(model.uiState.value.hidden.unlocked)
        model.onEvent(DashboardUiEvent.OnHiddenEncryptNoticeAccepted)
        val code = assertNotNull(model.uiState.value.hidden.recoveryCode)
        assertEquals(code, HiddenStore.recoveryCode())
        model.onEvent(DashboardUiEvent.OnHiddenLock)
        assertEquals(code, model.uiState.value.hidden.recoveryCode)
        model.onEvent(DashboardUiEvent.OnHiddenRecoveryCodeShown)
        assertNull(model.uiState.value.hidden.recoveryCode)
        model.onEvent(DashboardUiEvent.OnHiddenEncryptNoticeAccepted)
        assertNull(model.uiState.value.hidden.recoveryCode)
    }

    @Test
    fun cancellingTheResetEndsTheAllowance() {
        assertTrue(HiddenStore.setPin("1234"))
        val model = viewModel()
        DeviceAuth.handler = { true }
        model.onEvent(DashboardUiEvent.OnHiddenForgotPin)
        waitFor("allowed") { model.uiState.value.hidden.pinResetAllowed }
        assertTrue(HiddenStore.pinResetAllowed())
        model.onEvent(DashboardUiEvent.OnHiddenResetCancel)
        assertFalse(model.uiState.value.hidden.pinResetAllowed)
        assertFalse(HiddenStore.pinResetAllowed())
        model.onEvent(DashboardUiEvent.OnHiddenResetPin("5678"))
        assertEquals(HiddenNotice.RESET_EXPIRED, model.uiState.value.hidden.notice)
        assertFalse(model.uiState.value.hidden.unlocked)
    }

    @Test
    fun theAllowanceIsGoneAfterASuccessfulReset() {
        assertTrue(HiddenStore.setPin("1234"))
        val model = viewModel()
        DeviceAuth.handler = { true }
        model.onEvent(DashboardUiEvent.OnHiddenForgotPin)
        waitFor("allowed") { model.uiState.value.hidden.pinResetAllowed }
        model.onEvent(DashboardUiEvent.OnHiddenResetPin("5678"))
        assertEquals(HiddenNotice.RESET_DONE, model.uiState.value.hidden.notice)
        assertFalse(HiddenStore.pinResetAllowed())
        model.onEvent(DashboardUiEvent.OnHiddenNoticeShown)
        model.onEvent(DashboardUiEvent.OnHiddenResetPin("9999"))
        assertEquals(HiddenNotice.RESET_EXPIRED, model.uiState.value.hidden.notice)
        model.onEvent(DashboardUiEvent.OnHiddenLock)
        model.onEvent(DashboardUiEvent.OnHiddenUnlock("5678"))
        assertTrue(model.uiState.value.hidden.unlocked)
    }

    @Test
    fun aKeyThatCannotBeWrappedUnderTheCodeTellsTheUser() {
        assertTrue(HiddenStore.setPin("1234"))
        assertTrue(HiddenStore.setEncrypted("t1", ByteArray(32) { 1 }, "a.mp4", "x.fjh"))
        assertFalse(HiddenStore.takeRecoveryGap())
        val path = File(home, "hidden-test.json")
        path.writeText(path.readText().replace(Regex("\"rk\":\"[^\"]*\""), "\"rk\":\"fjenc1:AAAA\""))
        File(path.path + ".bak").delete()
        HiddenStore.resetForTest(path.absolutePath)
        assertTrue(HiddenStore.setEncrypted("t2", ByteArray(32) { 2 }, "b.mp4", "y.fjh"))
        val model = viewModel()
        model.noteRecoveryGap()
        assertEquals(HiddenNotice.RECOVERY_UNAVAILABLE, model.uiState.value.hidden.notice)
        model.onEvent(DashboardUiEvent.OnHiddenNoticeShown)
        model.noteRecoveryGap()
        assertNull(model.uiState.value.hidden.notice)
    }

    private fun mutate(text: String, change: (MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit): String {
        val map = Json.parseToJsonElement(text).jsonObject.toMutableMap()
        change(map)
        return JsonObject(map).toString()
    }

    private fun mutateTasks(text: String, change: (Int, Map<String, kotlinx.serialization.json.JsonElement>) -> Map<String, kotlinx.serialization.json.JsonElement>): String =
        mutate(text) { map ->
            val tasks = map.getValue("tasks").jsonArray.mapIndexed { i, t -> JsonObject(change(i, t.jsonObject)) }
            map["tasks"] = JsonArray(tasks)
        }

    @Test
    fun aHandMadeBackupCannotEscapeTheDownloadsFolderOrForgeResults() {
        seed(repo)
        val made = ready(repo)
        val startup = File(home2, "AppData/Startup").also { it.mkdirs() }
        val allowed = File(home2, "Downloads/sub")
        val evil = mutateTasks(made.text) { i, t ->
            if (i == 0) t + mapOf(
                "id" to JsonPrimitive("../evil"),
                "fileName" to JsonPrimitive("..\\..\\x.bin"),
                "destinationPath" to JsonPrimitive(startup.absolutePath)
            ) else t + mapOf(
                "id" to JsonPrimitive("good_1"),
                "fileName" to JsonPrimitive("ok.bin"),
                "destinationPath" to JsonPrimitive(allowed.absolutePath),
                "checksumVerified" to JsonPrimitive("true"),
                "scanResult" to JsonPrimitive("clean"),
                "expectedChecksum" to JsonPrimitive("abc"),
                "errorMessage" to JsonPrimitive("boom"),
                "status" to JsonPrimitive("ERROR"),
                "maxDownloadSpeedBytesPerSec" to JsonPrimitive(-5)
            )
        }
        val other = switchToSecondHome()
        val result = AppBackup.restore(evil, other)
        assertEquals(2, result.tasksAdded)
        waitFor("tasks back") { other.allTasksFlow.value.size == 2 }
        val downloads = other.pathResolver.getDownloadsDirectory()
        val first = other.allTasksFlow.value.first { it.id != "good_1" }
        assertTrue(Regex("[A-Za-z0-9_-]+").matches(first.id))
        assertFalse(first.id.contains(".."))
        assertFalse(first.fileName.contains('/') || first.fileName.contains('\\'))
        assertEquals(downloads, first.destinationPath)
        val second = other.allTasksFlow.value.first { it.id == "good_1" }
        assertEquals(allowed.absolutePath, second.destinationPath)
        assertNull(second.checksumVerified)
        assertNull(second.scanResult)
        assertNull(second.expectedChecksum)
        assertNull(second.errorMessage)
        assertEquals(0L, second.maxDownloadSpeedBytesPerSec)
    }

    @Test
    fun aBackupFromAnotherPlatformKeepsNoPaths() {
        seed(repo)
        val destination = File(home2, "Downloads/keep")
        BehaviorSettings.setCategoryFolder("Video", "")
        val made = ready(repo)
        val foreign = mutate(mutateTasks(made.text) { _, t -> t + ("destinationPath" to JsonPrimitive(destination.absolutePath)) }) { map ->
            map["platform"] = JsonPrimitive("plan9")
            map["behavior"] = JsonObject(map.getValue("behavior").jsonObject + ("folder.Video" to JsonPrimitive(File(home2, "Downloads/Vids").absolutePath)))
        }
        val other = switchToSecondHome()
        val result = AppBackup.restore(foreign, other)
        assertTrue(result.wrongPlatform)
        assertEquals(2, result.tasksAdded, result.toString())
        waitFor("tasks back") { other.allTasksFlow.value.size == 2 }
        val downloads = other.pathResolver.getDownloadsDirectory()
        assertTrue(other.allTasksFlow.value.all { it.destinationPath == downloads })
        assertNull(BehaviorSettings.categoryFolder("Video"))

        val samePlatform = mutate(foreign) { it["platform"] = JsonPrimitive(devicePlatformId()) }
        val third = AppBackup.restore(samePlatform, other)
        assertFalse(third.wrongPlatform)
        assertEquals(File(home2, "Downloads/Vids").absolutePath, BehaviorSettings.categoryFolder("Video"))
    }

    @Test
    fun theBackupNamesItsPlatformAndHidesLoginsInLinks() {
        val dest = File(home, "Downloads").absolutePath
        repo.httpEngine.enqueueTask(
            task("auth1", DownloadStatus.PAUSED, dest) {
                copy(
                    url = "https://user:pw@example.invalid/a.bin?x=1",
                    requestHeaders = mapOf("Referer" to "https://example.invalid/page")
                )
            }
        )
        waitFor("seeded") { repo.allTasksFlow.value.any { it.id == "auth1" } }
        val made = ready(repo)
        assertFalse(made.text.contains("user:pw"))
        assertTrue(made.text.contains("https://example.invalid/a.bin?x=1"))
        assertFalse(made.text.contains("https://example.invalid/page"))
        assertEquals(devicePlatformId(), Json.parseToJsonElement(made.text).jsonObject.getValue("platform").let { (it as JsonPrimitive).content })
        assertEquals("https://example.invalid/x", AppBackup.stripUserInfo("https://a:b@example.invalid/x"))
        assertEquals("magnet:?xt=urn:btih:1@2", AppBackup.stripUserInfo("magnet:?xt=urn:btih:1@2"))
        assertEquals("https://example.invalid/p@q", AppBackup.stripUserInfo("https://example.invalid/p@q"))
    }

    @Test
    fun unknownLanguagesAndUnitsAreIgnoredAndLimitsNeverGoNegative() {
        val made = ready(repo)
        val bad = mutate(made.text) { map ->
            map["settings"] = JsonObject(
                map.getValue("settings").jsonObject + mapOf(
                    "language" to JsonPrimitive("xx"),
                    "speedDisplayUnit" to JsonPrimitive("parsecs"),
                    "speedPresetLowUnit" to JsonPrimitive("<b>"),
                    "globalDownloadSpeedLimitBytes" to JsonPrimitive("-5"),
                    "speedPresetHighBytes" to JsonPrimitive("-1"),
                    "maxConcurrentDownloads" to JsonPrimitive("-3")
                )
            )
        }
        val before = AppSettingsRepository(repo.pathResolver).loadSettings()
        AppBackup.restore(bad, repo)
        val after = AppSettingsRepository(repo.pathResolver).loadSettings()
        assertEquals(before.language, after.language)
        assertEquals(before.speedDisplayUnit, after.speedDisplayUnit)
        assertEquals(before.speedPresetLowUnit, after.speedPresetLowUnit)
        assertEquals(0L, after.globalDownloadSpeedLimitBytes)
        assertEquals(0L, after.speedPresetHighBytes)
        assertTrue(repo.maxConcurrentDownloads.value >= 0)

        val good = mutate(made.text) { map ->
            map["settings"] = JsonObject(
                map.getValue("settings").jsonObject + mapOf(
                    "language" to JsonPrimitive("fr"),
                    "speedDisplayUnit" to JsonPrimitive("Mbps")
                )
            )
        }
        AppBackup.restore(good, repo)
        val applied = AppSettingsRepository(repo.pathResolver).loadSettings()
        assertEquals("fr", applied.language)
        assertEquals("Mbps", applied.speedDisplayUnit)
    }

    @Test
    fun theNoticeFollowsWhatWasReallyRestored() {
        seed(repo)
        val model = viewModel()
        model.onEvent(DashboardUiEvent.OnBackupExportRequested)
        waitFor("export") { model.uiState.value.backup.text != null }
        val text = model.uiState.value.backup.text!!
        AppSettingsRepository(repo.pathResolver).saveSettings(AppSettingsRepository(repo.pathResolver).loadSettings().copy(themeMode = "light"))
        model.onEvent(DashboardUiEvent.OnBackupRestoreText(text))
        waitFor("notice") { model.uiState.value.backup.notice != null }
        val settingsOnly = model.uiState.value.backup.notice!!
        assertEquals(BackupNoticeKind.RESTORED, settingsOnly.kind)
        assertEquals(0, settingsOnly.tasksAdded)
        assertEquals(2, settingsOnly.tasksSkipped)
        assertTrue(backupNoticeText(settingsOnly).contains(LocalizationManager.Strings.backupSettingsDone))
        model.onEvent(DashboardUiEvent.OnBackupNoticeShown)

        model.onEvent(DashboardUiEvent.OnBackupRestoreText(text))
        waitFor("nothing") { model.uiState.value.backup.notice != null }
        assertEquals(BackupNoticeKind.NOTHING_NEW, model.uiState.value.backup.notice!!.kind)

        val shown = backupNoticeText(BackupNotice(BackupNoticeKind.RESTORED, tasksAdded = 3, tasksSkipped = 4, needRestart = 2, wrongPlatform = true))
        assertTrue(shown.contains(LocalizationManager.Strings.backupWrongPlatform))
        assertTrue(shown.contains(LocalizationManager.Strings.backupNeedRestart.replace("{n}", "2")))
        assertTrue(shown.contains(LocalizationManager.Strings.backupSkipped.replace("{n}", "4")))
    }

    @Test
    fun restoreWithCodeReportsTheOutcome() {
        assertTrue(HiddenStore.setPin("1234"))
        assertTrue(HiddenStore.setEncrypted("t1", ByteArray(32) { 1 }, "a.mp4", "x.fjh"))
        val code = assertNotNull(HiddenStore.recoveryCode())
        val model = viewModel()
        model.onEvent(DashboardUiEvent.OnHiddenLock)
        model.onEvent(DashboardUiEvent.OnHiddenRestoreWithCode(code))
        assertNull(model.uiState.value.hidden.notice)
        model.onEvent(DashboardUiEvent.OnHiddenUnlock("1234"))
        model.onEvent(DashboardUiEvent.OnHiddenRestoreWithCode("ABCD-EFGH"))
        assertEquals(HiddenNotice.RESTORE_WRONG_CODE, model.uiState.value.hidden.notice)
        model.onEvent(DashboardUiEvent.OnHiddenNoticeShown)
        model.onEvent(DashboardUiEvent.OnHiddenRestoreWithCode(code))
        assertEquals(HiddenNotice.RESTORE_DONE, model.uiState.value.hidden.notice)
    }

    @Test
    fun aBackupRoundTripsAndKeepsSecretsOut() {
        seed(repo)
        AppSettingsRepository(repo.pathResolver).saveSettings(AppSettings(destinationPath = File(home, "Dest").absolutePath, themeMode = "dark", language = "fr"))
        BehaviorSettings.setSortIntoFolders(true)
        BehaviorSettings.setCategoryFolder("Video", File(home2, "Downloads/Vids").absolutePath)
        LearnedMediaHosts.learn("https://videos.example.org/watch")
        val made = ready(repo)
        assertTrue(Regex("""FetchJet-backup-\d{4}-\d{2}-\d{2}\.fjbackup""").matches(made.fileName))
        assertEquals(2, made.tasks)
        assertFalse(made.text.contains("SECRETCOOKIE"))
        assertFalse(made.text.contains("fjenc1"))
        assertTrue(made.text.contains("\"hdrCookie\":\"\""))
        assertTrue(made.text.contains("https://example.invalid/part1.bin?sig=abc"))
        assertTrue(made.text.contains("videos.example.org"))

        BehaviorSettings.setSortIntoFolders(false)
        BehaviorSettings.setCategoryFolder("Video", "")
        LearnedMediaHosts.forget("videos.example.org")
        val other = switchToSecondHome()
        val first = AppBackup.restore(made.text, other)
        assertTrue(first.valid)
        assertEquals(2, first.tasksAdded)
        assertEquals(0, first.tasksSkipped)
        assertEquals(1, first.needRestart)
        assertEquals(0, first.errors)
        assertTrue(first.settingsApplied > 0)
        waitFor("tasks back") { other.allTasksFlow.value.size == 2 }
        val done = other.allTasksFlow.value.first { it.id == "done1" }
        val part = other.allTasksFlow.value.first { it.id == "part1" }
        assertEquals(DownloadStatus.COMPLETED, done.status)
        assertEquals(File(home2, "Downloads").absolutePath, done.destinationPath)
        assertEquals(DownloadStatus.PAUSED, part.status)
        assertEquals(0L, part.downloadedBytes)
        assertEquals("https://example.invalid/part1.bin?sig=abc", part.url)
        assertNull(part.requestHeaders["Cookie"])
        assertNull(part.requestHeaders["Referer"])
        assertEquals("dark", AppSettingsRepository(other.pathResolver).loadSettings().themeMode)
        assertEquals("fr", AppSettingsRepository(other.pathResolver).loadSettings().language)
        assertTrue(BehaviorSettings.sortIntoFolders.value)
        assertEquals(File(home2, "Downloads/Vids").absolutePath, BehaviorSettings.categoryFolder("Video"))
        assertTrue(LearnedMediaHosts.knows("videos.example.org"))

        val again = AppBackup.restore(made.text, other)
        assertEquals(0, again.tasksAdded)
        assertEquals(2, again.tasksSkipped)
        assertEquals(0, again.needRestart)
    }

    @Test
    fun aBrokenEntryAndUnknownFieldsAreTolerated() {
        seed(repo)
        val made = ready(repo)
        val damaged = made.text
            .replaceFirst("\"tasks\":[", "\"tasks\":[{\"id\":\"bad1\",\"totalBytes\":\"zzz\"},{\"url\":\"no id\"},")
            .replaceFirst("\"settings\"", "\"futureThing\":{\"a\":1},\"settings\"")
        val other = switchToSecondHome()
        val result = AppBackup.restore(damaged, other)
        assertTrue(result.valid)
        assertEquals(2, result.tasksAdded)
        assertEquals(1, result.tasksSkipped)
        waitFor("tasks back") { other.allTasksFlow.value.size == 2 }
    }

    @Test
    fun somethingThatIsNotABackupIsRefusedAndChangesNothing() {
        val before = repo.allTasksFlow.value.size
        for (text in listOf("", "hello", "[]", "{}", "{\"format\":\"other\",\"version\":1}", "{\"format\":\"fetchjet-backup\"}")) {
            val r = AppBackup.restore(text, repo)
            assertFalse(r.valid, text)
            assertEquals(0, r.tasksAdded)
        }
        assertEquals(before, repo.allTasksFlow.value.size)
    }

    @Test
    fun aBackupIsRefusedWhileAnythingIsHidden() {
        seed(repo)
        assertTrue(HiddenStore.setPin("1234"))
        assertTrue(HiddenStore.hide(listOf("done1")))
        waitFor("hidden") { repo.allTasksFlow.value.any { it.hidden } }
        val made = AppBackup.create(repo)
        assertEquals(BackupExport.Failed(BackupFailure.BACKUP_HIDDEN_BLOCKED), made)
    }

    @Test
    fun theEventsFillTheStateAndTheUiClearsIt() {
        seed(repo)
        val model = viewModel()
        model.onEvent(DashboardUiEvent.OnBackupExportRequested)
        waitFor("export") { model.uiState.value.backup.text != null }
        val text = model.uiState.value.backup.text!!
        assertTrue(model.uiState.value.backup.fileName.endsWith(".fjbackup"))
        model.onEvent(DashboardUiEvent.OnBackupWritten)
        assertNull(model.uiState.value.backup.text)
        assertEquals("", model.uiState.value.backup.fileName)

        model.onEvent(DashboardUiEvent.OnBackupRestoreText(text))
        waitFor("notice") { model.uiState.value.backup.notice != null }
        assertEquals(BackupNoticeKind.NOTHING_NEW, model.uiState.value.backup.notice!!.kind)
        assertEquals(2, model.uiState.value.backup.notice!!.tasksSkipped)
        model.onEvent(DashboardUiEvent.OnBackupNoticeShown)
        assertNull(model.uiState.value.backup.notice)

        model.onEvent(DashboardUiEvent.OnBackupRestoreText("nonsense"))
        waitFor("invalid") { model.uiState.value.backup.notice?.kind == BackupNoticeKind.INVALID }
        assertTrue(backupNoticeText(model.uiState.value.backup.notice!!).isNotBlank())
    }

    @Test
    fun theEventsReportHiddenBlockAndRestoredCounts() {
        seed(repo)
        val model = viewModel()
        model.onEvent(DashboardUiEvent.OnBackupExportRequested)
        waitFor("export") { model.uiState.value.backup.text != null }
        val text = model.uiState.value.backup.text!!
        val other = switchToSecondHome()
        runCatching { vm?.deviceSync?.stop() }
        vm?.scope?.cancel()
        val model2 = DashboardViewModel(other).also { vm = it }
        model2.onEvent(DashboardUiEvent.OnBackupRestoreText(text))
        waitFor("restored") { model2.uiState.value.backup.notice?.kind == BackupNoticeKind.RESTORED }
        val notice = model2.uiState.value.backup.notice!!
        assertEquals(2, notice.tasksAdded)
        assertEquals(1, notice.needRestart)
        assertTrue(backupNoticeText(notice).contains("2"))
        waitFor("list") { model2.uiState.value.allTasks.size == 2 }

        assertTrue(HiddenStore.setPin("1234"))
        assertTrue(HiddenStore.hide(listOf("done1")))
        waitFor("hidden") { other.allTasksFlow.value.any { it.hidden } }
        model2.onEvent(DashboardUiEvent.OnBackupExportRequested)
        waitFor("blocked") { model2.uiState.value.backup.notice?.kind == BackupNoticeKind.HIDDEN_BLOCKED }
        assertNull(model2.uiState.value.backup.text)
    }

    @Test
    fun theFileNameUsesTheUtcDate() {
        assertEquals("FetchJet-backup-1970-01-01.fjbackup", AppBackup.fileName(0L))
        assertEquals("FetchJet-backup-2026-11-23.fjbackup", AppBackup.fileName(1_795_392_000_000L + 3_600_000L))
        assertEquals("FetchJet-backup-2024-02-29.fjbackup", AppBackup.fileName(1_709_164_800_000L))
    }
}
