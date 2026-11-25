import AVFoundation
import SharedLogic
import SwiftUI
import UIKit

enum FJTrimEdge { case start, end }

final class FJTrimModel: ObservableObject {
    let asset: AVURLAsset
    let player: AVPlayer
    let durationMs: Int64
    let audio: Bool
    @Published var startMs: Int64
    @Published var endMs: Int64
    @Published var edge: FJTrimEdge = .start
    @Published var playheadMs: Int64
    @Published var playingRange = false
    @Published var frames: [CGImage] = []
    @Published var peaks: [Float] = []
    private var waveJob: Kotlinx_coroutines_coreJob?
    private var waveBars = 0
    private(set) var frameMs: Double = 1000.0 / 30.0
    private var seeking = false
    private var pendingSeekMs: Int64?
    private var timeObserver: Any?
    private var boundaryObserver: Any?
    private var generator: AVAssetImageGenerator?
    private var framesKey = ""

    init(path: String, durationMs: Int64, audio: Bool, startMs: Int64, endMs: Int64) {
        self.audio = audio
        if audio { frameMs = 10 }
        asset = AVURLAsset(url: URL(fileURLWithPath: path), options: [AVURLAssetPreferPreciseDurationAndTimingKey: true])
        player = AVPlayer(playerItem: AVPlayerItem(asset: asset))
        player.actionAtItemEnd = .pause
        let total = max(durationMs, 1)
        self.durationMs = total
        let s = min(max(startMs, 0), total)
        var e = endMs > 0 ? min(endMs, total) : total
        if e <= s { e = total }
        self.startMs = s
        self.endMs = e
        self.playheadMs = s
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(value: 1, timescale: 30), queue: .main
        ) { [weak self] time in
            guard let self, self.playingRange, time.seconds.isFinite else { return }
            self.playheadMs = Int64(time.seconds * 1000)
            if self.playheadMs >= self.endMs { self.stopRange() }
        }
        if !audio { Task { await self.readFrameRate() } }
        seek(to: s)
    }

    deinit {
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        if let boundaryObserver { player.removeTimeObserver(boundaryObserver) }
        generator?.cancelAllCGImageGeneration()
        waveJob?.cancel(cause: nil)
        player.pause()
    }

    func loadWave(host: IosAppHost, bars: Int) {
        guard audio, bars > 0, bars != waveBars else { return }
        waveBars = bars
        waveJob?.cancel(cause: nil)
        waveJob = host.audioWaveform(path: asset.url.path, bars: Int32(bars), durationMs: durationMs) { [weak self] values in
            guard let self, self.waveBars == bars else { return }
            self.peaks = values.map { $0.floatValue }
            self.waveJob = nil
        }
    }

    var minGapMs: Int64 { max(Int64(frameMs.rounded(.up)), 1) }

    @MainActor
    private func setFrameMs(_ value: Double) { frameMs = value }

    private func readFrameRate() async {
        guard let track = try? await asset.loadTracks(withMediaType: .video).first,
              let rate = try? await track.load(.nominalFrameRate), rate > 0 else { return }
        await setFrameMs(1000.0 / Double(rate))
    }

    func time(_ ms: Int64) -> CMTime { CMTime(value: ms, timescale: 1000) }

    func seek(to ms: Int64) {
        playheadMs = ms
        if seeking {
            pendingSeekMs = ms
            return
        }
        seeking = true
        player.seek(to: time(ms), toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] _ in
            DispatchQueue.main.async {
                guard let self else { return }
                self.seeking = false
                if let next = self.pendingSeekMs {
                    self.pendingSeekMs = nil
                    self.seek(to: next)
                }
            }
        }
    }

    func setStart(_ ms: Int64) {
        stopRange()
        startMs = min(max(ms, 0), endMs - minGapMs)
        seek(to: startMs)
    }

    func setEnd(_ ms: Int64) {
        stopRange()
        endMs = max(min(ms, durationMs), startMs + minGapMs)
        seek(to: endMs)
    }

    func nudge(_ frames: Int) {
        edge == .start
            ? setStart(snap(Double(startMs) + Double(frames) * frameMs))
            : setEnd(snap(Double(endMs) + Double(frames) * frameMs))
    }

    private func snap(_ ms: Double) -> Int64 {
        let index = (ms / frameMs).rounded()
        return Int64((index * frameMs).rounded())
    }

    func toggleRange() {
        if playingRange {
            stopRange()
            return
        }
        playingRange = true
        player.seek(to: time(startMs), toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] _ in
            DispatchQueue.main.async {
                guard let self, self.playingRange else { return }
                if let old = self.boundaryObserver { self.player.removeTimeObserver(old) }
                self.boundaryObserver = self.player.addBoundaryTimeObserver(
                    forTimes: [NSValue(time: self.time(self.endMs))], queue: .main
                ) { [weak self] in self?.stopRange() }
                self.player.play()
            }
        }
    }

    func stopRange() {
        guard playingRange else { return }
        playingRange = false
        player.pause()
        if let old = boundaryObserver {
            player.removeTimeObserver(old)
            boundaryObserver = nil
        }
    }

    func loadFrames(count: Int, height: CGFloat) {
        let key = "\(count)x\(Int(height))"
        guard count > 0, key != framesKey else { return }
        framesKey = key
        if let cached = FJTrimFrameCache.shared.get(asset.url.path, key) {
            frames = cached
            return
        }
        generator?.cancelAllCGImageGeneration()
        let gen = AVAssetImageGenerator(asset: asset)
        gen.appliesPreferredTrackTransform = true
        gen.maximumSize = CGSize(width: height * 3 * UIScreen.main.scale, height: height * UIScreen.main.scale)
        gen.requestedTimeToleranceBefore = CMTime(value: 1, timescale: 10)
        gen.requestedTimeToleranceAfter = CMTime(value: 1, timescale: 10)
        generator = gen
        let step = Double(durationMs) / Double(count)
        let times = (0..<count).map { NSValue(time: time(Int64(step * (Double($0) + 0.5)))) }
        var images = [CGImage?](repeating: nil, count: count)
        var left = count
        let path = asset.url.path
        gen.generateCGImagesAsynchronously(forTimes: times) { [weak self] requested, image, _, _, _ in
            DispatchQueue.main.async {
                guard let self, self.framesKey == key else { return }
                if let i = times.firstIndex(where: { CMTimeCompare($0.timeValue, requested) == 0 }) {
                    images[i] = image
                }
                left -= 1
                let ready = images.compactMap { $0 }
                if ready.count == images.count || left == 0 {
                    self.frames = Self.filled(images)
                    if left == 0 { FJTrimFrameCache.shared.put(path, key, self.frames) }
                } else if ready.count % 4 == 0 {
                    self.frames = Self.filled(images)
                }
            }
        }
    }

    private static func filled(_ images: [CGImage?]) -> [CGImage] {
        var last: CGImage? = images.first { $0 != nil } ?? nil
        return images.compactMap { img -> CGImage? in
            if let img { last = img }
            return last
        }
    }
}

final class FJTrimFrameCache {
    static let shared = FJTrimFrameCache()
    private var store: [String: [CGImage]] = [:]
    private var order: [String] = []

    func get(_ path: String, _ key: String) -> [CGImage]? { store["\(path)|\(key)"] }

    func put(_ path: String, _ key: String, _ frames: [CGImage]) {
        let k = "\(path)|\(key)"
        if store[k] == nil { order.append(k) }
        store[k] = frames
        while order.count > 6 { store[order.removeFirst()] = nil }
    }
}

struct FJTrimPlayerSurface: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> FJPlayerView {
        let view = FJPlayerView()
        view.backgroundColor = .black
        view.playerLayer.player = player
        view.playerLayer.videoGravity = .resizeAspect
        return view
    }

    func updateUIView(_ view: FJPlayerView, context: Context) {
        view.playerLayer.player = player
    }
}

enum FJTrimFormat {
    static func text(_ ms: Int64) -> String {
        let v = max(ms, 0)
        let h = v / 3_600_000
        let m = (v / 60_000) % 60
        let s = (v / 1000) % 60
        let f = v % 1000
        return h > 0
            ? String(format: "%d:%02d:%02d.%03d", h, m, s, f)
            : String(format: "%02d:%02d.%03d", m, s, f)
    }
}

struct FJTrimView: View {
    @EnvironmentObject var store: FJStore
    @StateObject private var model: FJTrimModel
    let message: String?
    let failed: Bool
    let onCut: (Int64, Int64) -> Void
    let onClose: () -> Void

    let audio: Bool
    let title: String

    init(path: String, durationMs: Int64, audio: Bool = false, title: String = "", startMs: Int64, endMs: Int64,
         message: String? = nil, failed: Bool = false,
         onCut: @escaping (Int64, Int64) -> Void, onClose: @escaping () -> Void) {
        _model = StateObject(wrappedValue: FJTrimModel(path: path, durationMs: durationMs, audio: audio, startMs: startMs, endMs: endMs))
        self.audio = audio
        self.title = title
        self.message = message
        self.failed = failed
        self.onCut = onCut
        self.onClose = onClose
    }

    var body: some View {
        let s = store.strings
        VStack(spacing: 0) {
            HStack {
                Button(action: { model.stopRange(); onClose() }) {
                    Image(systemName: "xmark")
                        .font(.fj(16, .semibold))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(s.cancel)
                Spacer()
                Text(s.videoCutOnTimeline)
                    .font(.fj(15, .semibold))
                Spacer()
                Color.clear.frame(width: 44, height: 44)
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 8)

            if audio {
                VStack(spacing: 16) {
                    Image(systemName: "music.note")
                        .font(.system(size: 72, weight: .semibold))
                        .foregroundStyle(FJTheme.color.primary)
                    Text(title)
                        .font(.fj(15, .medium))
                        .foregroundStyle(.white.opacity(0.85))
                        .multilineTextAlignment(.center)
                        .lineLimit(3)
                        .truncationMode(.middle)
                        .padding(.horizontal, 24)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                FJTrimPlayerSurface(player: model.player)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            VStack(spacing: 14) {
                if failed || !(message ?? "").isEmpty {
                    Label((message ?? "").isEmpty ? store.strings.videoCutFailed : (message ?? ""), systemImage: failed ? "exclamationmark.triangle" : "checkmark.circle")
                        .font(.fj(13, .medium))
                        .foregroundStyle(failed ? FJTheme.color.error : FJTheme.color.primary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                labels(s)
                FJTrimTrack(model: model)
                    .frame(height: 56)
                controls(s)
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 10)
            .frame(maxWidth: 820)
            .environment(\.layoutDirection, .leftToRight)
        }
        .background(Color.black.ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func labels(_ s: AppLocalization) -> some View {
        HStack(spacing: 10) {
            timeBox(s.videoCutFrom, model.startMs, model.edge == .start) { model.edge = .start; model.seek(to: model.startMs) }
            timeBox(s.videoCutDuration, model.endMs - model.startMs, false, nil)
            timeBox(s.videoCutTo, model.endMs, model.edge == .end) { model.edge = .end; model.seek(to: model.endMs) }
        }
    }

    private func timeBox(_ title: String, _ ms: Int64, _ active: Bool, _ action: (() -> Void)?) -> some View {
        let content = VStack(spacing: 2) {
            Text(title)
                .font(.fj(11, .medium))
                .foregroundStyle(.white.opacity(0.65))
            Text(FJTrimFormat.text(ms))
                .font(.system(size: 15, weight: .semibold, design: .monospaced))
                .foregroundStyle(active ? FJTheme.color.primary : .white)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(.white.opacity(active ? 0.12 : 0.06), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(active ? FJTheme.color.primary : .clear, lineWidth: 1.5))
        return Group {
            if let action {
                Button(action: action) { content }.buttonStyle(.plain)
            } else {
                content
            }
        }
    }

    private func controls(_ s: AppLocalization) -> some View {
        HStack(spacing: 12) {
            FJRepeatButton(systemImage: "chevron.backward.2", label: "-1") { model.nudge(-1) }
            FJRepeatButton(systemImage: "chevron.forward.2", label: "+1") { model.nudge(1) }
            Button(action: model.toggleRange) {
                Label(s.videoCutPlayRange, systemImage: model.playingRange ? "pause.fill" : "play.fill")
                    .font(.fj(14, .semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(.white.opacity(0.10), in: RoundedRectangle(cornerRadius: 10))
            }
            .buttonStyle(.plain)
            .foregroundStyle(.white)
            Button {
                model.stopRange()
                onCut(model.startMs, model.endMs)
            } label: {
                Label(s.videoCutApply, systemImage: "scissors")
                    .font(.fj(14, .bold))
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .foregroundStyle(FJTheme.color.onPrimary)
                    .background(FJTheme.color.primary, in: RoundedRectangle(cornerRadius: 10))
            }
            .buttonStyle(.plain)
        }
    }
}

struct FJRepeatButton: View {
    let systemImage: String
    let label: String
    let action: () -> Void
    @State private var pressed = false
    @State private var repeated = false
    @State private var delay: DispatchWorkItem?
    @State private var timer: Timer?

    var body: some View {
        Image(systemName: systemImage)
            .font(.fj(15, .semibold))
            .foregroundStyle(.white)
            .frame(width: 48, height: 44)
            .background(.white.opacity(pressed ? 0.22 : 0.10), in: RoundedRectangle(cornerRadius: 10))
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        guard !pressed else { return }
                        pressed = true
                        repeated = false
                        let work = DispatchWorkItem {
                            repeated = true
                            action()
                            timer = Timer.scheduledTimer(withTimeInterval: 0.08, repeats: true) { _ in action() }
                        }
                        delay = work
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35, execute: work)
                    }
                    .onEnded { _ in
                        delay?.cancel()
                        timer?.invalidate()
                        timer = nil
                        if !repeated { action() }
                        pressed = false
                    }
            )
            .onDisappear {
                delay?.cancel()
                timer?.invalidate()
            }
            .accessibilityLabel(label)
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { action() }
    }
}

struct FJTrimTrack: View {
    @EnvironmentObject var store: FJStore
    @ObservedObject var model: FJTrimModel
    @State private var dragStartMs: Int64?
    private let handle: CGFloat = 18

    var body: some View {
        GeometryReader { geo in
            let width = max(geo.size.width - handle * 2, 1)
            let height = geo.size.height
            let x: (Int64) -> CGFloat = { handle + CGFloat(Double($0) / Double(model.durationMs)) * width }
            let startX = x(model.startMs)
            let endX = x(model.endMs)
            ZStack(alignment: .topLeading) {
                strip(width: width, height: height)
                    .offset(x: handle)
                Rectangle()
                    .fill(Color.black.opacity(0.6))
                    .frame(width: max(startX - handle, 0), height: height)
                    .offset(x: handle)
                Rectangle()
                    .fill(Color.black.opacity(0.6))
                    .frame(width: max(handle + width - endX, 0), height: height)
                    .offset(x: endX)
                RoundedRectangle(cornerRadius: 4)
                    .stroke(FJTheme.color.primary, lineWidth: 3)
                    .frame(width: max(endX - startX, 2), height: height)
                    .offset(x: startX)
                Rectangle()
                    .fill(Color.white)
                    .frame(width: 2, height: height + 8)
                    .offset(x: x(model.playheadMs) - 1, y: -4)
                    .opacity(model.playingRange || dragStartMs != nil ? 1 : 0.5)
                handleView(.start, height: height)
                    .offset(x: startX - handle)
                    .gesture(drag(.start, width: width))
                handleView(.end, height: height)
                    .offset(x: endX)
                    .gesture(drag(.end, width: width))
            }
            .frame(width: geo.size.width, height: height, alignment: .topLeading)
            .contentShape(Rectangle())
            .onAppear { load(width: width, height: height) }
            .onChange(of: Int(width)) { _, w in load(width: CGFloat(w), height: height) }
        }
    }

    private func load(width: CGFloat, height: CGFloat) {
        if model.audio {
            model.loadWave(host: store.host, bars: max(Int(width / 4), 16))
        } else {
            model.loadFrames(count: max(Int(width / 40), 6), height: height)
        }
    }

    @ViewBuilder
    private func strip(width: CGFloat, height: CGFloat) -> some View {
        if model.audio {
            wave(width: width, height: height)
        } else {
            frames(width: width, height: height)
        }
    }

    private func wave(width: CGFloat, height: CGFloat) -> some View {
        let peaks = model.peaks
        return Canvas { context, size in
            guard !peaks.isEmpty else { return }
            let step = size.width / CGFloat(peaks.count)
            let bar = max(step * 0.6, 1)
            let mid = size.height / 2
            for (i, p) in peaks.enumerated() {
                let h = max(CGFloat(p) * (size.height - 8), 2)
                let rect = CGRect(x: CGFloat(i) * step + (step - bar) / 2, y: mid - h / 2, width: bar, height: h)
                context.fill(Path(roundedRect: rect, cornerRadius: bar / 2), with: .color(.white.opacity(0.75)))
            }
        }
        .frame(width: width, height: height)
        .background(Color.white.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 4))
    }

    private func frames(width: CGFloat, height: CGFloat) -> some View {
        HStack(spacing: 0) {
            if model.frames.isEmpty {
                Rectangle().fill(Color.white.opacity(0.08))
            } else {
                ForEach(Array(model.frames.enumerated()), id: \.offset) { _, image in
                    Image(decorative: image, scale: 1)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: width / CGFloat(model.frames.count), height: height)
                        .clipped()
                }
            }
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: 4))
    }

    private func handleView(_ edge: FJTrimEdge, height: CGFloat) -> some View {
        let active = model.edge == edge
        return UnevenRoundedRectangle(
            topLeadingRadius: edge == .start ? 6 : 0, bottomLeadingRadius: edge == .start ? 6 : 0,
            bottomTrailingRadius: edge == .end ? 6 : 0, topTrailingRadius: edge == .end ? 6 : 0
        )
        .fill(FJTheme.color.primary.opacity(active ? 1 : 0.8))
        .frame(width: handle, height: height)
        .overlay(
            Capsule().fill(Color.white).frame(width: 3, height: height * 0.4)
        )
        .contentShape(Rectangle().inset(by: -12))
    }

    private func drag(_ edge: FJTrimEdge, width: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if dragStartMs == nil {
                    model.stopRange()
                    model.edge = edge
                    dragStartMs = edge == .start ? model.startMs : model.endMs
                }
                let delta = Int64(Double(value.translation.width / width) * Double(model.durationMs))
                let target = (dragStartMs ?? 0) + delta
                edge == .start ? model.setStart(target) : model.setEnd(target)
            }
            .onEnded { _ in dragStartMs = nil }
    }
}
