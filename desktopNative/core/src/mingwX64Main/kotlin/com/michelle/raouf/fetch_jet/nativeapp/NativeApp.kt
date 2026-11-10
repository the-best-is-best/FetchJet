package com.michelle.raouf.fetch_jet.nativeapp

import com.michelle.raouf.fetch_jet.core.browser.BrowserIntegration
import com.michelle.raouf.fetch_jet.core.i18n.start.start
import com.michelle.raouf.fetch_jet.core.log.AppLog
import com.michelle.raouf.fetch_jet.core.log.DiagnosticsLog
import com.michelle.raouf.fetch_jet.core.network.ExternalDownloadBridge
import com.michelle.raouf.fetch_jet.core.notification.SystemNotice
import com.michelle.raouf.fetch_jet.core.power.WhenDone
import com.michelle.raouf.fetch_jet.core.startup.StartupPreferences
import com.michelle.raouf.fetch_jet.core.update.APP_VERSION
import com.michelle.raouf.fetch_jet.nativeapp.actions.UiAction
import com.michelle.raouf.fetch_jet.nativeapp.bridge.BRIDGE_PORT
import com.michelle.raouf.fetch_jet.nativeapp.bridge.BridgeServer
import com.michelle.raouf.fetch_jet.nativeapp.channels.UiChannel
import com.michelle.raouf.fetch_jet.nativeapp.channels.UiJson
import com.michelle.raouf.fetch_jet.nativeapp.dto.Channels
import com.michelle.raouf.fetch_jet.nativeapp.dto.EffectKind
import com.michelle.raouf.fetch_jet.nativeapp.host.HostFreeFn
import com.michelle.raouf.fetch_jet.nativeapp.host.HostServiceFn
import com.michelle.raouf.fetch_jet.nativeapp.host.HostServices
import com.michelle.raouf.fetch_jet.nativeapp.presenter.Presenter
import com.michelle.raouf.fetch_jet.nativeapp.presenter.dispatch
import com.michelle.raouf.fetch_jet.nativeapp.runtime.ChangeNotifier
import com.michelle.raouf.fetch_jet.nativeapp.runtime.CrashLog
import com.michelle.raouf.fetch_jet.nativeapp.runtime.HostMainDispatcher
import com.michelle.raouf.fetch_jet.nativeapp.runtime.HostNotifyFn
import com.michelle.raouf.fetch_jet.nativeapp.runtime.HostPostFn
import com.michelle.raouf.fetch_jet.nativeapp.runtime.installMainDispatcher
import com.michelle.raouf.fetch_jet.platform.win.HostHooks
import com.michelle.raouf.fetch_jet.platform.win.WinApp
import com.michelle.raouf.fetch_jet.platform.win.WinHttpNative
import com.michelle.raouf.fetch_jet.platform.win.WinInstallKind
import com.michelle.raouf.fetch_jet.platform.win.winCreateDirectories
import com.michelle.raouf.fetch_jet.platform.win.winSetEnv
import com.michelle.raouf.fetch_jet.platform.win.winShellOpen
import com.tbib.kmp_localization.LocalizationManager
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.Volatile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.put

@Serializable
internal data class StartConfig(
    val home: String = "",
    val downloads: String = "",
    val args: List<String> = emptyList(),
    val loginStart: Boolean = false,
    val headless: Boolean = false,
    val bridge: Boolean = true,
    val debug: Boolean = false,
    val verbose: Boolean = false,
    val kind: String = "",
    val automaticDownloads: Boolean = true,
    val bridgePort: Int = 0,
    val secondInstance: Boolean = false,
)

internal object NativeApp {
    private val state = AtomicInt(0)

    @Volatile
    var config = StartConfig()
        private set

    lateinit var main: HostMainDispatcher
        private set
    lateinit var notifier: ChangeNotifier
        private set
    lateinit var presenter: Presenter
        private set

    @Volatile
    var host: HostServices? = null
        private set

    private var channels = arrayOfNulls<UiChannel>(Channels.COUNT)
    private var bridge: BridgeServer? = null
    private var overridesApplied = false

    private fun parse(configJson: String): StartConfig = try {
        UiJson.decodeFromString(StartConfig.serializer(), configJson.ifBlank { "{}" })
    } catch (_: Throwable) {
        StartConfig()
    }

    private fun applyOverrides(parsed: StartConfig) {
        if (overridesApplied) return
        overridesApplied = true
        if (parsed.home.isNotBlank()) WinApp.homeOverride = parsed.home.replace('/', '\\').trimEnd('\\')
        when (parsed.kind) {
            "development" -> WinApp.kindOverride = WinInstallKind.DEVELOPMENT
            "installed" -> WinApp.kindOverride = WinInstallKind.INSTALLED
            "store" -> WinApp.kindOverride = WinInstallKind.STORE
        }
        if (!parsed.automaticDownloads) WinApp.automaticDownloads = false
        if (parsed.downloads.isNotBlank()) winSetEnv("FETCHJET_DOWNLOADS", parsed.downloads.replace('/', '\\').trimEnd('\\'))
    }

    fun prepare(configJson: String): String {
        val parsed = parse(configJson)
        applyOverrides(parsed)
        return UiJson.encodeToString(LaunchResult.serializer(), Launch.prepare(parsed))
    }

    val isRunning: Boolean get() = state.value == 2

    fun start(configJson: String, post: HostPostFn, notify: HostNotifyFn, hostFn: HostServiceFn?, hostFree: HostFreeFn?): Int {
        if (!state.compareAndSet(0, 1)) return 2
        val parsed = parse(configJson)
        config = parsed
        applyOverrides(parsed)
        val logs = WinApp.appHome + "\\logs"
        winCreateDirectories(logs)
        CrashLog.install("$logs\\native-crash.log", null, terminateOnUnhandled = true, quietAbort = true)
        DiagnosticsLog.install()
        if (parsed.verbose) AppLog.verbose = true
        println("FetchJet native core $APP_VERSION (${WinApp.kind.name.lowercase()}) home=${WinApp.appHome}")
        WinHttpNative.registerAsDefault()
        val dispatcher = HostMainDispatcher(post)
        installMainDispatcher(dispatcher)
        main = dispatcher
        notifier = ChangeNotifier(post, notify)
        LocalizationManager.start()
        StartupPreferences.launchedAtLogin = parsed.loginStart || Launch.loginStart
        val services = hostFn?.let { HostServices(it, hostFree) }
        host = services
        val created = Presenter(notifier, parsed.headless)
        presenter = created
        for (channel in created.channels) channels[channel.id] = channel
        wireHost(created, services, parsed)
        created.start()
        if (parsed.bridge) {
            val port = if (parsed.bridgePort > 0) parsed.bridgePort else BRIDGE_PORT
            val server = BridgeServer(created.scope, port) { ExternalDownloadBridge.showAndFocus() }
            bridge = server
            created.scope.launch { server.start() }
        }
        if (!parsed.headless) {
            created.scope.launch(Dispatchers.IO) {
                try {
                    BrowserIntegration.setUpOnFirstRun()
                } catch (_: Throwable) {
                }
            }
        }
        val startUrl = Launch.pendingUrl
        if (startUrl.isNotBlank()) {
            Launch.pendingUrl = ""
            created.scope.launch {
                delay(600)
                ExternalDownloadBridge.dispatch(startUrl, "")
                ExternalDownloadBridge.showAndFocus()
            }
        }
        state.value = 2
        return 0
    }

    private fun wireHost(presenter: Presenter, services: HostServices?, config: StartConfig) {
        ExternalDownloadBridge.onShowAndFocus = {
            presenter.effect(EffectKind.SHOW_WINDOW)
        }
        HostHooks.showWindow = { presenter.effect(EffectKind.SHOW_WINDOW) }
        ExternalDownloadBridge.openInBrowserFallback = { url ->
            println("opening the browser fallback link")
            if (!config.headless) winShellOpen(url)
        }
        HostHooks.requestExit = { presenter.effect(EffectKind.EXIT) }
        WhenDone.onExitApp = { presenter.effect(EffectKind.EXIT) }
        presenter.scope.launch {
            SystemNotice.notices.collect { (title, text) ->
                presenter.effect(EffectKind.NOTIFY, text = text, title = title)
            }
        }
        if (services != null && !config.headless) {
            HostHooks.pickFile = { title, filterName, pattern ->
                services.call("pickFile") {
                    put("title", title)
                    put("filterName", filterName)
                    put("pattern", pattern)
                }.takeIf { it.isNotEmpty() }
            }
            HostHooks.pickFolder = { title, initialPath ->
                services.call("pickFolder") {
                    put("title", title)
                    put("initialPath", initialPath.orEmpty())
                }.takeIf { it.isNotEmpty() }
            }
        }
    }

    fun snapshot(channelAndFlags: Int): String {
        if (!isRunning) return "{}"
        val id = channelAndFlags and 0xFF
        val full = channelAndFlags and Channels.FULL_FLAG != 0
        val channel = channels.getOrNull(id) ?: return "{}"
        return channel.snapshot(full)
    }

    fun action(json: String) {
        if (!isRunning) return
        val action = try {
            UiJson.decodeFromString(UiAction.serializer(), json)
        } catch (e: Throwable) {
            AppLog.e("fj_action: cannot read ${json.take(200)}: ${e.message}")
            return
        }
        if (main.isUiThread()) {
            presenter.dispatch(action)
        } else {
            main.dispatch(main, Runnable { presenter.dispatch(action) })
        }
    }

    fun call(name: String, args: String): String {
        if (!isRunning) return ""
        return when {
            name == "ping" -> "pong"
            name == "version" -> APP_VERSION
            name == "home" -> WinApp.appHome
            name.startsWith("debug.") && config.debug -> DebugCalls.call(name.removePrefix("debug."), args)
            else -> ""
        }
    }

    fun shutdown() {
        if (!state.compareAndSet(2, 3)) return
        bridge?.stop()
        bridge = null
        notifier.stop()
        presenter.stop()
        main.close()
    }
}
