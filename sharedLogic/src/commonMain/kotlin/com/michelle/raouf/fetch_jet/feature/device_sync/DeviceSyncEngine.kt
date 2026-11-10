package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DeviceSyncEngine(
    internal val config: DeviceSyncConfig,
    internal val platform: DeviceSyncPlatform,
    internal val store: DeviceSyncStore,
    internal val onReceive: (SendPayload, List<String>) -> Unit,
) : DeviceSync {

    internal val timing: DeviceSyncTiming get() = config.timing
    internal val lock: SyncLock = platform.newLock()
    private val logClock = TimeSource.Monotonic.markNow()
    internal val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t -> log("error: ${t.message}") }
    )
    private val serverGate = Mutex()

    internal val discoveredFlow = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discovered: StateFlow<List<DiscoveredDevice>> = discoveredFlow.asStateFlow()
    internal val pairedFlow = MutableStateFlow(store.pairedDevices())
    override val paired: StateFlow<List<PairedDevice>> = pairedFlow.asStateFlow()
    internal val pairingFlow = MutableStateFlow<PairingState?>(null)
    override val pairing: StateFlow<PairingState?> = pairingFlow.asStateFlow()
    internal val sendingFlow = MutableStateFlow<SendState?>(null)
    override val sending: StateFlow<SendState?> = sendingFlow.asStateFlow()
    internal val receivingFlow = MutableStateFlow<ReceiveState?>(null)
    override val receiving: StateFlow<ReceiveState?> = receivingFlow.asStateFlow()
    internal val blockedFlow = MutableStateFlow(store.blockedDevices())
    override val blocked: StateFlow<List<PairedDevice>> = blockedFlow.asStateFlow()

    override val selfId: String = store.selfId
    override val selfName: String get() = store.selfName.ifBlank { config.platformName }

    override fun setSelfName(name: String) {
        store.selfName = name
    }

    internal val discovery = SyncDiscovery(this)
    internal val pairings = SyncPairing(this)
    internal val receiver = SyncReceiver(this)
    private val sender = SyncSender(this)

    @Volatile
    internal var started = false
        private set

    @Volatile
    internal var visible = false
        private set

    @Volatile
    private var active = false

    @Volatile
    private var session: Job? = null

    @Volatile
    private var server: SyncServerSocket? = null

    @Volatile
    internal var serverPort = 0
        private set

    @Volatile
    private var network: Job? = null

    @Volatile
    private var multicastToken: Any? = null

    private var inbound = 0

    internal val listeningTcpPort: Int get() = serverPort
    internal val discoveryRunning: Boolean get() = network?.isActive == true

    override fun start() {
        val first = lock.withLock {
            if (started) {
                false
            } else {
                started = true
                session = SupervisorJob(scope.coroutineContext[Job])
                true
            }
        }
        if (!first) return
        log("start selfId=$selfId name=$selfName visible=$visible")
        applyVisibility()
    }

    override fun stop() {
        val job = lock.withLock {
            started = false
            session.also { session = null }
        }
        job?.cancel()
        applyVisibility()
    }

    override fun setVisible(visible: Boolean) {
        this.visible = visible
        if (started) applyVisibility()
    }

    override fun setActive(active: Boolean) {
        this.active = active
        if (started) applyVisibility()
    }

    internal fun shutdown() {
        stop()
        val listener = server
        server = null
        serverPort = 0
        listener?.closeQuietly()
        discovery.closeSockets()
        scope.cancel()
        releaseMulticast()
        platform.network.close()
    }

    private fun applyVisibility() {
        scope.launch {
            serverGate.withLock {
                val wanted = visible && started
                val listener = server
                if (wanted && listener == null) {
                    val bound = bindServer()
                    if (bound != null) {
                        serverPort = bound.port
                        server = bound
                        log("visible tcpPort=$serverPort")
                        scope.launch { acceptLoop(bound) }
                    }
                } else if (!wanted && listener != null) {
                    server = null
                    serverPort = 0
                    listener.closeQuietly()
                    if (started) log("hidden")
                }
                applyNetwork()
            }
        }
    }

    private suspend fun applyNetwork() {
        val parent = session
        val wanted = started && parent != null && (config.alwaysDiscover || visible || active)
        val current = network
        if (current != null && (!wanted || !current.isActive)) {
            network = null
            current.cancel()
            discovery.closeSockets()
            current.join()
            releaseMulticast()
            if (started) log("discovery off")
        }
        if (wanted && network == null) {
            val job = SupervisorJob(parent)
            network = job
            multicastToken = MulticastLock.acquire()
            val running = CoroutineScope(scope.coroutineContext + job)
            running.launch { discovery.listenLoop() }
            running.launch { discovery.expireLoop() }
            running.launch { discovery.announceLoop() }
            log("discovery on visible=$visible active=$active")
        }
    }

    private fun releaseMulticast() {
        val token = multicastToken ?: return
        multicastToken = null
        MulticastLock.release(token)
    }

    private suspend fun bindServer(): SyncServerSocket? {
        try {
            return platform.network.listen(config.bindHost, config.tcpPort)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
        return try {
            platform.network.listen(config.bindHost, 0)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            log("tcp bind failed: ${t.message}")
            null
        }
    }

    private suspend fun acceptLoop(listener: SyncServerSocket) {
        while (server === listener) {
            val connection = try {
                listener.accept()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                if (server !== listener) break
                delay(200)
                continue
            }
            val admitted = lock.withLock { (inbound < SYNC_MAX_INBOUND).also { if (it) inbound++ } }
            if (!admitted) {
                connection.closeQuietly()
                continue
            }
            scope.launch {
                try {
                    receiver.handle(connection)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    log("connection failed: ${t.message}")
                } finally {
                    lock.withLock { inbound-- }
                    connection.closeQuietly()
                }
            }
        }
    }

    internal fun log(message: String) {
        val stamp = if (config.logTimestamps) "+${logClock.elapsedNow().inWholeMilliseconds}ms " else ""
        println("FJSYNC $stamp$message")
    }

    internal fun now(): Long = Clock.System.now().toEpochMilliseconds()

    internal fun publishPaired() {
        lock.withLock { pairedFlow.value = store.pairedDevices() }
    }

    internal fun forgetPairing(deviceId: String) {
        store.removePairing(deviceId)
        publishPaired()
        discovery.publish()
    }

    override fun startPairing(deviceId: String) = pairings.startPairing(deviceId)

    override fun startPairingWithHost(host: String) = pairings.connectAndPair(host, config.tcpPort, host)

    override fun confirmPairing() = pairings.confirm()

    override fun cancelPairing() = pairings.cancel()

    override fun blockPairing() = pairings.block()

    override fun unblock(deviceId: String) = pairings.unblock(deviceId)

    override fun unpair(deviceId: String) = pairings.unpair(deviceId)

    override fun clearReceiving() = receiver.clear()

    override fun send(request: SendRequest, onResult: (Boolean) -> Unit) = sender.send(request, onResult)

    override fun cancelSend() = sender.cancel()
}
