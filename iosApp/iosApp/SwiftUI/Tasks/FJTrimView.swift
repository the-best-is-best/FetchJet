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
    @Published var peaks: [Float] = []
    @Published var focusTick = 0
    private var waveJob: Kotlinx_coroutines_coreJob?
    private(set) var frameMs: Double = 1000.0 / 30.0
    private var seeking = false
    private var pendingSeekMs: Int64?
    private var timeObserver: Any?
    private var boundaryObserver: Any?

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
        waveJob?.cancel(cause: nil)
        player.pause()
    }

    func loadWave(host: IosAppHost) {
        guard audio, waveJob == nil, peaks.isEmpty else { return }
        let bars = Int(min(4000, max(Double(durationMs) / 1000 * 20, 32)))
        waveJob = host.audioWaveform(path: asset.url.path, bars: Int32(bars), durationMs: durationMs) { [weak self] values in
            guard let self else { return }
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

    func focus(_ which: FJTrimEdge) {
        stopRange()
        edge = which
        seek(to: which == .start ? startMs : endMs)
        focusTick += 1
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
                FJTrimTimeline(model: model)
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
            timeBox(s.videoCutFrom, model.startMs, model.edge == .start) { model.focus(.start) }
            timeBox(s.videoCutDuration, model.endMs - model.startMs, false, nil)
            timeBox(s.videoCutTo, model.endMs, model.edge == .end) { model.focus(.end) }
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
