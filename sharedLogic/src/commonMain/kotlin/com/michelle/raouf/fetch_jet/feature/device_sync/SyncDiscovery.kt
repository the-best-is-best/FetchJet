package com.michelle.raouf.fetch_jet.feature.device_sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

internal class SyncDiscovery(private val engine: DeviceSyncEngine) {
    private val seen = LinkedHashMap<String, DiscoveredDevice>()
    private val lastSeenAt = HashMap<String, Long>()
    private val sockets = ArrayList<SyncUdpSocket>()

    fun find(deviceId: String): DiscoveredDevice? = engine.lock.withLock { seen[deviceId] }

    fun remember(device: DiscoveredDevice) {
        engine.lock.withLock {
            seen[device.id] = device
            lastSeenAt[device.id] = engine.now()
        }
        publish()
    }

    fun publish() {
        val store = engine.store
        engine.lock.withLock {
            engine.discoveredFlow.value = seen.values.filterNot { store.isBlocked(it.id) }
                .map { it.copy(paired = store.isPaired(it.id)) }
        }
    }

    fun closeSockets() {
        val open = engine.lock.withLock { sockets.toList().also { sockets.clear() } }
        open.forEach { it.closeQuietly() }
    }

    private suspend fun track(socket: SyncUdpSocket): Boolean {
        engine.lock.withLock { sockets.add(socket) }
        if (currentCoroutineContext().isActive) return true
        release(socket)
        return false
    }

    private fun release(socket: SyncUdpSocket) {
        engine.lock.withLock { sockets.remove(socket) }
        socket.closeQuietly()
    }

    private fun announceBytes(): ByteArray {
        val port = engine.serverPort
        val shown = engine.visible && port > 0
        return Announce(
            t = if (shown) ANNOUNCE else PROBE,
            id = engine.selfId,
            name = engine.selfName,
            plat = engine.config.platformName,
            port = if (shown) port else 0
        ).toBytes()
    }

    suspend fun announceLoop() {
        val config = engine.config
        val network = engine.platform.network
        val socket = try {
            network.openAnnouncer(config.bindHost)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            engine.log("announce socket failed: ${t.message}")
            return
        }
        if (!track(socket)) return
        var loggedTargets: String? = null
        var lastSweep = 0L
        try {
            while (true) {
                val body = announceBytes()
                if (engine.visible && engine.serverPort > 0 && config.sendBroadcast) {
                    val targets = network.broadcast(socket, config.discoveryPort, body)
                    val signature = targets.joinToString()
                    if (targets.isNotEmpty() && signature != loggedTargets) {
                        loggedTargets = signature
                        engine.log("announce -> [$signature]:${config.discoveryPort}")
                    }
                }
                val hosts = engine.lock.withLock {
                    seen.values.filterNot { engine.store.isBlocked(it.id) }.map { it.host }
                }.distinct()
                for (host in hosts) socket.sendQuietly(host, config.discoveryPort, body)
                val sweepEvery = engine.timing.sweepEveryMs
                if (sweepEvery > 0) {
                    val now = engine.now()
                    if (now - lastSweep >= sweepEvery) {
                        lastSweep = now
                        network.sweepSubnet(config.discoveryPort, body)
                    }
                }
                delay(engine.timing.announceEveryMs)
            }
        } finally {
            release(socket)
        }
    }

    suspend fun listenLoop() {
        val config = engine.config
        while (true) {
            val socket = try {
                engine.platform.network.bindDiscovery(config.bindHost, config.discoveryPort)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                engine.log("listen bind failed: ${t.message}")
                return
            }
            if (!track(socket)) return
            try {
                while (true) {
                    val datagram = socket.receive()
                    try {
                        onDatagram(socket, datagram)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                currentCoroutineContext().ensureActive()
                engine.log("listen stopped: ${t.message}")
            } finally {
                release(socket)
            }
            delay(engine.timing.rebindMs)
        }
    }

    private suspend fun onDatagram(socket: SyncUdpSocket, datagram: SyncDatagram) {
        val sender = datagram.host
        if (sender.isEmpty() || datagram.bytes.size > SYNC_MAX_DATAGRAM) return
        val ann = announceOf(datagram.bytes) ?: return
        if (ann.id == engine.selfId) return
        val store = engine.store
        if (ann.t == PROBE) {
            if (engine.visible && engine.serverPort > 0 && !store.isBlocked(ann.id)) {
                socket.sendQuietly(sender, engine.config.discoveryPort, announceBytes())
            }
            return
        }
        if (ann.t != ANNOUNCE || store.isBlocked(ann.id)) return
        val known = find(ann.id)
        if (known == null || known.host != sender || known.port != ann.port) {
            engine.log("heard ${ann.name} @ $sender:${ann.port}")
        }
        remember(
            DiscoveredDevice(
                id = ann.id, name = ann.name, platform = ann.plat,
                host = sender, port = ann.port, paired = store.isPaired(ann.id)
            )
        )
    }

    suspend fun expireLoop() {
        while (true) {
            delay(engine.timing.announceEveryMs)
            val now = engine.now()
            val changed = engine.lock.withLock {
                val expired = lastSeenAt.filterValues { now - it > engine.timing.deviceExpireMs }.keys.toList()
                expired.forEach {
                    seen.remove(it)
                    lastSeenAt.remove(it)
                }
                expired.isNotEmpty()
            }
            if (changed) publish()
        }
    }
}
