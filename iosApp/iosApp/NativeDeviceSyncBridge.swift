import Foundation
import CryptoKit
import SharedUI
import Darwin

/// Native half of iOS device sync (ROADMAP-1.3.0 item 4): the crypto and the raw LAN sockets.
/// The protocol, pairing and state live in Kotlin (`IosDeviceSync` in sharedLogic); this class only
/// does what must be native and byte-matches the JVM/Android side:
///   * ECDH on P-256 with DER (X.509 SubjectPublicKeyInfo) public keys and `SHA-256(sharedSecret)`,
///   * AES-256-GCM whose combined output is `nonce(12) ++ ciphertext ++ tag(16)` (CryptoKit
///     `SealedBox.combined`), the exact layout Kotlin `CryptoBox` uses,
///   * UDP broadcast discovery on port 28461 and a TCP server/clients on 28462 (POSIX sockets, so
///     the wire is identical to the desktop/Android implementation — no Bonjour).
///
/// Registered at startup: `IosDeviceSyncNativeRegistry.shared.native = NativeDeviceSyncBridge()`.
/// Needs `NSLocalNetworkUsageDescription` (and `NSBonjourServices`) in Info.plist, else iOS blocks
/// every local-network socket.
public final class NativeDeviceSyncBridge: NSObject, IosDeviceSyncNative {

    private let lock = NSLock()

    // MARK: crypto

    private var keyPairs: [Int64: P256.KeyAgreement.PrivateKey] = [:]
    private var keyPairSeq: Int64 = 0

    public func generateEcKeyPair() -> IosEcKeyPair? {
        let pk = P256.KeyAgreement.PrivateKey()
        lock.lock(); keyPairSeq += 1; let id = keyPairSeq; keyPairs[id] = pk; lock.unlock()
        return IosEcKeyPair(keyId: id, publicX509: pk.publicKey.derRepresentation)
    }

    public func deriveKey(keyId: Int64, peerPublicX509: Data) -> Data? {
        lock.lock(); let pk = keyPairs[keyId]; lock.unlock()
        guard let pk = pk,
              let peer = try? P256.KeyAgreement.PublicKey(derRepresentation: peerPublicX509),
              let secret = try? pk.sharedSecretFromKeyAgreement(with: peer) else { return nil }
        let raw = secret.withUnsafeBytes { Data($0) }
        return Data(SHA256.hash(data: raw))
    }

    public func releaseKeyPair(keyId: Int64) {
        lock.lock(); keyPairs[keyId] = nil; lock.unlock()
    }

    public func aesSeal(key: Data, plain: Data) -> Data? {
        guard let box = try? AES.GCM.seal(plain, using: SymmetricKey(data: key)) else { return nil }
        return box.combined
    }

    public func aesOpen(key: Data, sealed: Data) -> Data? {
        guard let box = try? AES.GCM.SealedBox(combined: sealed) else { return nil }
        return try? AES.GCM.open(box, using: SymmetricKey(data: key))
    }

    public func sha256(data: Data) -> Data {
        return Data(SHA256.hash(data: data))
    }

    // MARK: UDP discovery

    private var udpListenFd: Int32 = -1
    private var udpListening = false

    public func startUdpListen(port: Int32, listener: IosUdpPacketListener) -> Bool {
        let fd = socket(AF_INET, SOCK_DGRAM, 0)
        if fd < 0 { return false }
        var yes: Int32 = 1
        setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &yes, socklen_t(MemoryLayout<Int32>.size))
        setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, &yes, socklen_t(MemoryLayout<Int32>.size))
        setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &yes, socklen_t(MemoryLayout<Int32>.size))
        var addr = sockaddr_in()
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_addr.s_addr = INADDR_ANY
        addr.sin_port = UInt16(truncatingIfNeeded: port).bigEndian
        let bound = withUnsafePointer(to: &addr) { p in
            p.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        if bound < 0 { close(fd); return false }
        udpListenFd = fd
        udpListening = true
        Thread.detachNewThread { [weak self] in
            var buf = [UInt8](repeating: 0, count: 2048)
            while self?.udpListening == true {
                var from = sockaddr_in()
                var fromLen = socklen_t(MemoryLayout<sockaddr_in>.size)
                let n = withUnsafeMutablePointer(to: &from) { fp in
                    fp.withMemoryRebound(to: sockaddr.self, capacity: 1) { sp in
                        recvfrom(fd, &buf, buf.count, 0, sp, &fromLen)
                    }
                }
                if n <= 0 { continue }
                let data = Data(bytes: buf, count: n)
                var ipBuf = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
                var sin = from.sin_addr
                inet_ntop(AF_INET, &sin, &ipBuf, socklen_t(INET_ADDRSTRLEN))
                let ip = String(cString: ipBuf)
                listener.onPacket(senderIp: ip, data: data)
            }
        }
        return true
    }

    public func sendUdpBroadcast(port: Int32, data: Data) {
        let fd = socket(AF_INET, SOCK_DGRAM, 0)
        if fd < 0 { return }
        var yes: Int32 = 1
        setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &yes, socklen_t(MemoryLayout<Int32>.size))
        defer { close(fd) }
        var targets = broadcastAddresses()
        targets.append("255.255.255.255")
        for ip in Set(targets) {
            var addr = sockaddr_in()
            addr.sin_family = sa_family_t(AF_INET)
            addr.sin_port = UInt16(truncatingIfNeeded: port).bigEndian
            if inet_pton(AF_INET, ip, &addr.sin_addr) != 1 { continue }
            _ = data.withUnsafeBytes { raw in
                withUnsafePointer(to: &addr) { p in
                    p.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                        sendto(fd, raw.baseAddress, data.count, 0, $0,
                               socklen_t(MemoryLayout<sockaddr_in>.size))
                    }
                }
            }
        }
    }

    public func stopUdp() {
        udpListening = false
        if udpListenFd >= 0 { close(udpListenFd); udpListenFd = -1 }
    }

    /// Subnet-directed broadcast address of every up, non-loopback IPv4 interface (e.g. 192.168.8.255).
    private func broadcastAddresses() -> [String] {
        var out: [String] = []
        var ifap: UnsafeMutablePointer<ifaddrs>?
        if getifaddrs(&ifap) != 0 { return out }
        defer { freeifaddrs(ifap) }
        var p = ifap
        while let cur = p {
            let flags = Int32(cur.pointee.ifa_flags)
            let addr = cur.pointee.ifa_addr
            if (flags & IFF_UP) != 0, (flags & IFF_LOOPBACK) == 0,
               let addr = addr, addr.pointee.sa_family == UInt8(AF_INET),
               let mask = cur.pointee.ifa_netmask {
                let ip = addr.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee.sin_addr.s_addr }
                let nm = mask.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee.sin_addr.s_addr }
                let bcast = ip | ~nm
                var a = in_addr(s_addr: bcast)
                var ipBuf = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
                inet_ntop(AF_INET, &a, &ipBuf, socklen_t(INET_ADDRSTRLEN))
                out.append(String(cString: ipBuf))
            }
            p = cur.pointee.ifa_next
        }
        return out
    }

    // MARK: TCP connections

    private var conns: [Int64: Int32] = [:]
    private var connSeq: Int64 = 0
    private var serverFd: Int32 = -1
    private var serverRunning = false

    private func registerConn(_ fd: Int32) -> Int64 {
        lock.lock(); connSeq += 1; let id = connSeq; conns[id] = fd; lock.unlock()
        return id
    }

    private func fdFor(_ id: Int64) -> Int32? {
        lock.lock(); let fd = conns[id]; lock.unlock(); return fd
    }

    public func startTcpServer(port: Int32, listener: IosTcpConnectionListener) -> Int32 {
        let fd = socket(AF_INET, SOCK_STREAM, 0)
        if fd < 0 { return -1 }
        var yes: Int32 = 1
        setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &yes, socklen_t(MemoryLayout<Int32>.size))
        var addr = sockaddr_in()
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_addr.s_addr = INADDR_ANY
        addr.sin_port = UInt16(truncatingIfNeeded: port).bigEndian
        let bound = withUnsafePointer(to: &addr) { p in
            p.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                bind(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        if bound < 0 { close(fd); return -1 }
        if listen(fd, 8) < 0 { close(fd); return -1 }
        // Read back the actually-bound port (when 0 was requested).
        var bound2 = sockaddr_in()
        var len = socklen_t(MemoryLayout<sockaddr_in>.size)
        _ = withUnsafeMutablePointer(to: &bound2) { p in
            p.withMemoryRebound(to: sockaddr.self, capacity: 1) { getsockname(fd, $0, &len) }
        }
        let actualPort = Int32(UInt16(bigEndian: bound2.sin_port))
        serverFd = fd
        serverRunning = true
        Thread.detachNewThread { [weak self] in
            while self?.serverRunning == true {
                let client = accept(fd, nil, nil)
                if client < 0 { continue }
                guard let self = self else { close(client); break }
                let id = self.registerConn(client)
                listener.onConnection(connId: id)
            }
        }
        return actualPort > 0 ? actualPort : port
    }

    public func stopTcpServer() {
        serverRunning = false
        if serverFd >= 0 { close(serverFd); serverFd = -1 }
    }

    public func tcpConnect(host: String, port: Int32, timeoutMs: Int32) -> Int64 {
        let fd = socket(AF_INET, SOCK_STREAM, 0)
        if fd < 0 { return -1 }
        var addr = sockaddr_in()
        addr.sin_family = sa_family_t(AF_INET)
        addr.sin_port = UInt16(truncatingIfNeeded: port).bigEndian
        if inet_pton(AF_INET, host, &addr.sin_addr) != 1 {
            // Not a numeric IPv4: resolve via getaddrinfo.
            if !resolveInto(&addr, host: host) { close(fd); return -1 }
        }
        // Non-blocking connect with a timeout.
        let flags = fcntl(fd, F_GETFL, 0)
        _ = fcntl(fd, F_SETFL, flags | O_NONBLOCK)
        let rc = withUnsafePointer(to: &addr) { p in
            p.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                connect(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        if rc != 0 && errno != EINPROGRESS { close(fd); return -1 }
        if rc != 0 {
            var wset = fd_set()
            fdZero(&wset); fdSet(fd, &wset)
            var tv = timeval(tv_sec: Int(timeoutMs / 1000), tv_usec: Int32((timeoutMs % 1000) * 1000))
            let sel = select(fd + 1, nil, &wset, nil, &tv)
            if sel <= 0 { close(fd); return -1 }
            var err: Int32 = 0
            var elen = socklen_t(MemoryLayout<Int32>.size)
            getsockopt(fd, SOL_SOCKET, SO_ERROR, &err, &elen)
            if err != 0 { close(fd); return -1 }
        }
        _ = fcntl(fd, F_SETFL, flags) // back to blocking
        return registerConn(fd)
    }

    private func resolveInto(_ addr: inout sockaddr_in, host: String) -> Bool {
        var hints = addrinfo()
        hints.ai_family = AF_INET
        hints.ai_socktype = SOCK_STREAM
        var res: UnsafeMutablePointer<addrinfo>?
        if getaddrinfo(host, nil, &hints, &res) != 0 { return false }
        defer { freeaddrinfo(res) }
        guard let first = res, let sa = first.pointee.ai_addr else { return false }
        let resolved = sa.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee.sin_addr }
        addr.sin_addr = resolved
        return true
    }

    public func tcpReadFully(connId: Int64, length: Int32) -> Data? {
        guard let fd = fdFor(connId) else { return nil }
        let want = Int(length)
        var buf = [UInt8](repeating: 0, count: want)
        var got = 0
        while got < want {
            let n = buf.withUnsafeMutableBytes { raw -> Int in
                recv(fd, raw.baseAddress!.advanced(by: got), want - got, 0)
            }
            if n <= 0 { return nil }
            got += n
        }
        return Data(bytes: buf, count: want)
    }

    public func tcpWrite(connId: Int64, data: Data) -> Bool {
        guard let fd = fdFor(connId) else { return false }
        return data.withUnsafeBytes { raw -> Bool in
            let base = raw.baseAddress!
            var off = 0
            let total = data.count
            while off < total {
                let n = send(fd, base.advanced(by: off), total - off, 0)
                if n <= 0 { return false }
                off += n
            }
            return true
        }
    }

    public func tcpClose(connId: Int64) {
        lock.lock(); let fd = conns[connId]; conns[connId] = nil; lock.unlock()
        if let fd = fd { close(fd) }
    }

    // fd_set helpers (not exposed as functions in Swift's Darwin).
    private func fdZero(_ set: inout fd_set) {
        set.fds_bits = (0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    }

    private func fdSet(_ fd: Int32, _ set: inout fd_set) {
        let intOffset = Int(fd) / 32
        let bitOffset = Int(fd) % 32
        let mask = Int32(1 << bitOffset)
        withUnsafeMutablePointer(to: &set.fds_bits) { p in
            p.withMemoryRebound(to: Int32.self, capacity: 32) { $0[intOffset] |= mask }
        }
    }
}
