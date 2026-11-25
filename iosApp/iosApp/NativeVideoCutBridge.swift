import AVFoundation
import Foundation
import SharedLogic

final class NativeVideoCutBridge: NSObject, IosVideoCutBridge {
    private let lock = NSLock()
    private var sessions: [String: AVAssetExportSession] = [:]

    private static let extensions: Set<String> = ["mp4", "m4v", "mov"]

    func canCut(inputPath: String) -> Bool {
        let ext = (inputPath as NSString).pathExtension.lowercased()
        return Self.extensions.contains(ext)
    }

    func durationMs(inputPath: String) -> Int64 {
        let asset = AVURLAsset(url: URL(fileURLWithPath: inputPath),
                               options: [AVURLAssetPreferPreciseDurationAndTimingKey: true])
        let ready = DispatchSemaphore(value: 0)
        var value: Int64 = -1
        Task {
            if let d = try? await asset.load(.duration), d.isNumeric, d.seconds > 0 {
                value = Int64((d.seconds * 1000).rounded())
            }
            ready.signal()
        }
        ready.wait()
        return value
    }

    func cut(runId: String, inputPath: String, outputPath: String, startMs: Int64, endMs: Int64,
             progress: any IosVideoCutProgressCallback) -> Int32 {
        guard canCut(inputPath: inputPath), endMs > startMs else { return -1 }
        let asset = AVURLAsset(url: URL(fileURLWithPath: inputPath),
                               options: [AVURLAssetPreferPreciseDurationAndTimingKey: true])
        let ready = DispatchSemaphore(value: 0)
        var exportable = false
        Task {
            exportable = (try? await asset.load(.isExportable)) ?? false
            ready.signal()
        }
        ready.wait()
        guard exportable,
              let session = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetHighestQuality) else {
            return -1
        }
        let output = URL(fileURLWithPath: outputPath)
        try? FileManager.default.removeItem(at: output)
        session.outputURL = output
        session.outputFileType = output.pathExtension.lowercased() == "mov" ? .mov : .mp4
        session.shouldOptimizeForNetworkUse = true
        session.timeRange = CMTimeRange(
            start: Self.frameShownAt(asset: asset, time: CMTime(value: startMs, timescale: 1000)),
            end: CMTime(value: endMs, timescale: 1000))
        lock.lock(); sessions[runId] = session; lock.unlock()

        let started = Date()
        let done = DispatchSemaphore(value: 0)
        session.exportAsynchronously { done.signal() }
        while done.wait(timeout: .now() + 0.1) == .timedOut {
            let fraction = Double(session.progress)
            let elapsed = Int64(Date().timeIntervalSince(started) * 1000)
            let eta: Int64 = fraction > 0.02 ? Int64(Double(elapsed) * (1 - fraction) / fraction) : -1
            progress.onProgress(fraction: Float(fraction), elapsedMs: elapsed, etaMs: eta)
        }
        lock.lock(); sessions[runId] = nil; lock.unlock()

        switch session.status {
        case .completed:
            progress.onProgress(fraction: 1, elapsedMs: Int64(Date().timeIntervalSince(started) * 1000), etaMs: 0)
            return 1
        case .cancelled:
            try? FileManager.default.removeItem(at: output)
            return 0
        default:
            NSLog("[cut] export failed, ffmpeg takes over: %@", session.error?.localizedDescription ?? "unknown")
            try? FileManager.default.removeItem(at: output)
            return -1
        }
    }

    private static func frameShownAt(asset: AVURLAsset, time: CMTime) -> CMTime {
        let ready = DispatchSemaphore(value: 0)
        var track: AVAssetTrack?
        Task {
            track = try? await asset.loadTracks(withMediaType: .video).first
            ready.signal()
        }
        ready.wait()
        guard let track, let reader = try? AVAssetReader(asset: asset) else { return time }
        let look = CMTime(value: 3, timescale: 1)
        let from = CMTimeMaximum(.zero, CMTimeSubtract(time, look))
        reader.timeRange = CMTimeRange(start: from, end: CMTimeAdd(time, CMTime(value: 1, timescale: 10)))
        let out = AVAssetReaderTrackOutput(track: track, outputSettings: nil)
        out.alwaysCopiesSampleData = false
        guard reader.canAdd(out) else { return time }
        reader.add(out)
        guard reader.startReading() else { return time }
        var best: CMTime?
        while let sample = out.copyNextSampleBuffer() {
            let pts = CMSampleBufferGetPresentationTimeStamp(sample)
            guard pts.isNumeric, CMSampleBufferGetNumSamples(sample) > 0 else { continue }
            if CMTimeCompare(pts, time) <= 0, best.map({ CMTimeCompare(pts, $0) > 0 }) ?? true {
                best = pts
            }
        }
        reader.cancelReading()
        return best ?? time
    }

    func cancel(runId: String) {
        lock.lock(); let session = sessions[runId]; lock.unlock()
        session?.cancelExport()
    }
}
