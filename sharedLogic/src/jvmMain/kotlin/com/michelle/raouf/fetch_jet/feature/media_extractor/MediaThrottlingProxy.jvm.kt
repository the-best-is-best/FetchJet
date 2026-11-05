package com.michelle.raouf.fetch_jet.feature.media_extractor

private class BlockingRateLimiter(private val speedLimitProvider: () -> Long) {
    private val lock = Any()
    private var availableTokens: Double = 0.0
    private var lastRefillTime = System.nanoTime()
    private var lastLimit = 0L

    fun acquire(bytes: Int) {
        if (bytes <= 0) return
        var waitMs = 0L
        var waitNanos = 0

        synchronized(lock) {
            val currentLimit = speedLimitProvider()
            if (currentLimit <= 0L) {
                availableTokens = 0.0
                return
            }

            val now = System.nanoTime()
            if (currentLimit != lastLimit) {
                lastLimit = currentLimit
                availableTokens = 0.0
                lastRefillTime = now
            }

            val elapsedSec = (now - lastRefillTime) / 1_000_000_000.0
            lastRefillTime = now

            val maxTokens = maxOf(16 * 1024.0, currentLimit * 0.20)
            availableTokens =
                minOf(maxTokens, availableTokens + elapsedSec * currentLimit.toDouble())
            availableTokens -= bytes.toDouble()

            if (availableTokens < 0.0) {
                val deficit = -availableTokens
                val waitSec = deficit / currentLimit.toDouble()
                waitMs = (waitSec * 1000.0).toLong()
                waitNanos = ((waitSec * 1_000_000_000.0) % 1_000_000).toInt()
            }
        }

        if (waitMs > 0L || waitNanos > 0) {
            try {
                Thread.sleep(waitMs, waitNanos)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}

internal class LocalThrottlingProxy(
    private val speedLimitProvider: (() -> Long)? = null
) : AutoCloseable {
    private val serverSocket =
        java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
    val port: Int = serverSocket.localPort

    @Volatile
    private var isRunning = true
    private val rateLimiter = speedLimitProvider?.let { BlockingRateLimiter(it) }
    private val openSockets = java.util.concurrent.ConcurrentHashMap.newKeySet<java.net.Socket>()

    val totalDownloadedBytes = java.util.concurrent.atomic.AtomicLong(0L)
    val totalUploadedBytes = java.util.concurrent.atomic.AtomicLong(0L)

    private var lastSpeedCalcTimeMs = System.currentTimeMillis()
    private var lastSpeedCalcBytes = 0L
    private var currentSpeedBytesPerSec = 0L

    @Synchronized
    fun getInstantaneousDownloadSpeedBytesPerSec(): Long {
        val now = System.currentTimeMillis()
        val elapsedMs = now - lastSpeedCalcTimeMs
        if (elapsedMs >= 350L) {
            val totalBytes = totalDownloadedBytes.get()
            val deltaBytes = totalBytes - lastSpeedCalcBytes
            if (deltaBytes > 0L && elapsedMs > 0L) {
                val instant = (deltaBytes * 1000L) / elapsedMs
                currentSpeedBytesPerSec =
                    if (currentSpeedBytesPerSec <= 0L) instant else (instant * 0.7 + currentSpeedBytesPerSec * 0.3).toLong()
                lastSpeedCalcTimeMs = now
                lastSpeedCalcBytes = totalBytes
            } else if (elapsedMs >= 1200L) {
                currentSpeedBytesPerSec = 0L
                lastSpeedCalcTimeMs = now
                lastSpeedCalcBytes = totalBytes
            }
        }
        return currentSpeedBytesPerSec
    }

    init {
        Thread {
            while (isRunning && !serverSocket.isClosed) {
                try {
                    val clientSocket = serverSocket.accept()
                    openSockets.add(clientSocket)
                    Thread {
                        handleConnection(clientSocket)
                    }.apply {
                        isDaemon = true
                        start()
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }.apply {
            isDaemon = true
            name = "LocalThrottlingProxy-Acceptor"
            start()
        }
    }

    private fun handleConnection(clientSocket: java.net.Socket) {
        var remoteSocket: java.net.Socket? = null
        try {
            clientSocket.tcpNoDelay = true
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val headerBuffer = ByteArray(8192)
            var totalRead = 0
            while (totalRead < headerBuffer.size) {
                val r = clientIn.read(headerBuffer, totalRead, headerBuffer.size - totalRead)
                if (r <= 0) break
                totalRead += r
                val text = String(headerBuffer, 0, totalRead, Charsets.ISO_8859_1)
                if (text.contains("\r\n\r\n")) break
            }

            if (totalRead <= 0) return
            val requestHeader = String(headerBuffer, 0, totalRead, Charsets.ISO_8859_1)
            val firstLine = requestHeader.substringBefore("\r\n")
            val parts = firstLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val target = parts[1]

            if (method.equals("CONNECT", ignoreCase = true)) {
                val host = target.substringBefore(":")
                val port = target.substringAfter(":", "443").toIntOrNull() ?: 443

                val remote = java.net.Socket(host, port)
                remote.tcpNoDelay = true
                remoteSocket = remote
                openSockets.add(remote)

                clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                clientOut.flush()

                val remoteIn = remote.getInputStream()
                val remoteOut = remote.getOutputStream()

                val uploadThread = Thread {
                    try {
                        val buf = ByteArray(16384)
                        while (isRunning) {
                            val r = clientIn.read(buf)
                            if (r <= 0) break
                            totalUploadedBytes.addAndGet(r.toLong())
                            remoteOut.write(buf, 0, r)
                            remoteOut.flush()
                        }
                    } catch (_: Exception) {
                    } finally {
                        try {
                            remote.close()
                        } catch (_: Exception) {
                        }
                        try {
                            clientSocket.close()
                        } catch (_: Exception) {
                        }
                    }
                }.apply { isDaemon = true; start() }

                try {
                    val buf = ByteArray(16384)
                    while (isRunning) {
                        val r = remoteIn.read(buf)
                        if (r <= 0) break
                        totalDownloadedBytes.addAndGet(r.toLong())
                        rateLimiter?.acquire(r)
                        clientOut.write(buf, 0, r)
                        clientOut.flush()
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        remote.close()
                    } catch (_: Exception) {
                    }
                    try {
                        clientSocket.close()
                    } catch (_: Exception) {
                    }
                }

                uploadThread.join(300)
            } else {
                val hostLine =
                    requestHeader.lines().firstOrNull { it.startsWith("Host:", ignoreCase = true) }
                val rawHost = hostLine?.substringAfter(":")?.trim() ?: target.substringAfter("://")
                    .substringBefore("/")
                val host = rawHost.substringBefore(":")
                val port = rawHost.substringAfter(":", "80").toIntOrNull() ?: 80

                val remote = java.net.Socket(host, port)
                remote.tcpNoDelay = true
                remoteSocket = remote
                openSockets.add(remote)

                val remoteOut = remote.getOutputStream()
                val remoteIn = remote.getInputStream()

                remoteOut.write(headerBuffer, 0, totalRead)
                remoteOut.flush()

                val uploadThread = Thread {
                    try {
                        val buf = ByteArray(16384)
                        while (isRunning) {
                            val r = clientIn.read(buf)
                            if (r <= 0) break
                            totalUploadedBytes.addAndGet(r.toLong())
                            remoteOut.write(buf, 0, r)
                            remoteOut.flush()
                        }
                    } catch (_: Exception) {
                    } finally {
                        try {
                            remote.close()
                        } catch (_: Exception) {
                        }
                        try {
                            clientSocket.close()
                        } catch (_: Exception) {
                        }
                    }
                }.apply { isDaemon = true; start() }

                try {
                    val buf = ByteArray(16384)
                    while (isRunning) {
                        val r = remoteIn.read(buf)
                        if (r <= 0) break
                        totalDownloadedBytes.addAndGet(r.toLong())
                        rateLimiter?.acquire(r)
                        clientOut.write(buf, 0, r)
                        clientOut.flush()
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        remote.close()
                    } catch (_: Exception) {
                    }
                    try {
                        clientSocket.close()
                    } catch (_: Exception) {
                    }
                }

                uploadThread.join(300)
            }
        } catch (_: Exception) {
        } finally {
            try {
                clientSocket.close()
            } catch (_: Exception) {
            }
            try {
                remoteSocket?.close()
            } catch (_: Exception) {
            }
            openSockets.remove(clientSocket)
            remoteSocket?.let { openSockets.remove(it) }
        }
    }

    override fun close() {
        isRunning = false
        try {
            serverSocket.close()
        } catch (_: Exception) {
        }
        for (s in openSockets) {
            try {
                s.close()
            } catch (_: Exception) {
            }
        }
        openSockets.clear()
    }
}
