import AVFoundation
import SharedLogic
import SwiftUI
import UIKit

final class FJPlayer: ObservableObject {
    static let shared = FJPlayer()
    @Published var queue: PlaybackQueue?
}

struct FJPlayerTarget: Identifiable {
    let queue: PlaybackQueue
    var id: String { queue.items.map { $0.taskId }.joined(separator: ",") }
}

final class FJPlayerModel: ObservableObject {
    let queue: PlaybackQueue
    let player = AVPlayer()
    @Published var index: Int
    @Published var playing = false
    @Published var position: Double = 0
    @Published var duration: Double = 0
    @Published var speed: Float = 1
    @Published var controls = true
    @Published var hold = 0
    @Published var failed = false
    @Published var finished = false

    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var statusObserver: NSKeyValueObservation?
    private var rateObserver: NSKeyValueObservation?
    private var rewindTimer: Timer?
    private var hideTask: Task<Void, Never>?
    private var wasPlayingBeforeHold = false
    private var rewindTarget: Double = 0
    private var rewindSeeking = false

    init(queue: PlaybackQueue) {
        self.queue = queue
        self.index = Int(queue.index)
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        try? AVAudioSession.sharedInstance().setActive(true)
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600), queue: .main
        ) { [weak self] time in
            guard let self else { return }
            self.position = time.seconds.isFinite ? time.seconds : 0
            if let d = self.player.currentItem?.duration.seconds, d.isFinite, d > 0 { self.duration = d }
        }
        rateObserver = player.observe(\.timeControlStatus, options: [.new]) { [weak self] p, _ in
            DispatchQueue.main.async {
                guard let self else { return }
                let now = p.timeControlStatus != .paused
                guard now != self.playing else { return }
                self.playing = now
                if now { self.scheduleHide() }
            }
        }
        load(index, autoplay: true)
    }

    deinit {
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        rewindTimer?.invalidate()
        player.pause()
    }

    var item: PlayerItem { queue.items[index] }
    var hasNext: Bool { index < queue.items.count - 1 }
    var hasPrevious: Bool { index > 0 }

    func load(_ i: Int, autoplay: Bool) {
        index = max(0, min(i, queue.items.count - 1))
        failed = false
        finished = false
        position = 0
        duration = 0
        let entry = queue.items[index]
        PlayerProgress.shared.remember(groupKey: queue.groupKey, taskId: entry.taskId)
        let playerItem = AVPlayerItem(url: URL(fileURLWithPath: entry.path))
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime, object: playerItem, queue: .main
        ) { [weak self] _ in
            guard let self else { return }
            if self.hasNext {
                self.load(self.index + 1, autoplay: false)
            } else {
                self.controls = true
                self.finished = true
                PlayerProgress.shared.clear(groupKey: self.queue.groupKey)
            }
        }
        statusObserver = playerItem.observe(\.status, options: [.new]) { [weak self] it, _ in
            DispatchQueue.main.async {
                guard let self else { return }
                if it.status == .failed {
                    self.player.pause()
                    self.hideTask?.cancel()
                    self.controls = true
                    if self.hasNext { self.load(self.index + 1, autoplay: false) } else { self.failed = true }
                }
            }
        }
        player.replaceCurrentItem(with: playerItem)
        if autoplay {
            player.playImmediately(atRate: speed)
        } else {
            player.pause()
            controls = true
        }
        scheduleHide()
    }

    func togglePlay() {
        if playing {
            player.pause()
        } else {
            if duration > 0, position >= duration - 0.3 { seek(0) }
            player.playImmediately(atRate: speed)
        }
        scheduleHide()
    }

    func next() { if hasNext { load(index + 1, autoplay: false) } }

    func startOver() { load(0, autoplay: true) }

    func previous() {
        if position > 3 || !hasPrevious { seek(0) } else { load(index - 1, autoplay: false) }
    }

    func seek(_ seconds: Double) {
        let target = max(0, duration > 0 ? min(seconds, duration) : seconds)
        player.seek(to: CMTime(seconds: target, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        position = target
        scheduleHide()
    }

    func skip(_ ms: Int64) { seek(position + Double(ms) / 1000) }

    func setSpeed(_ value: Float) {
        speed = value
        if playing { player.rate = value }
        scheduleHide()
    }

    func toggleControls() {
        controls.toggle()
        scheduleHide()
    }

    func scheduleHide() {
        hideTask?.cancel()
        guard controls, playing else { return }
        hideTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 3_500_000_000)
            await MainActor.run {
                guard let self, !Task.isCancelled, self.playing, self.hold == 0 else { return }
                self.controls = false
            }
        }
    }

    func beginHold(forward: Bool) {
        if forward {
            hold = 1
            if playing { player.rate = MediaPlayback.shared.HOLD_SPEED } else { player.playImmediately(atRate: MediaPlayback.shared.HOLD_SPEED) }
        } else {
            hold = -1
            wasPlayingBeforeHold = playing
            player.pause()
            let now = player.currentTime().seconds
            rewindTarget = now.isFinite ? now : position
            rewindTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
                guard let self else { return }
                self.rewindTarget = max(0, self.rewindTarget - 0.2)
                self.position = self.rewindTarget
                guard !self.rewindSeeking else { return }
                self.rewindSeeking = true
                self.player.seek(
                    to: CMTime(seconds: self.rewindTarget, preferredTimescale: 600),
                    toleranceBefore: .zero, toleranceAfter: .zero
                ) { [weak self] _ in
                    DispatchQueue.main.async { self?.rewindSeeking = false }
                }
            }
        }
    }

    func endHold() {
        if hold == 1 {
            player.rate = speed
        } else if hold == -1 {
            rewindTimer?.invalidate()
            rewindTimer = nil
            if wasPlayingBeforeHold { player.playImmediately(atRate: speed) }
        }
        hold = 0
        scheduleHide()
    }
}

struct FJPlayerSurface: UIViewRepresentable {
    let player: AVPlayer
    let showVideo: Bool
    let onTap: () -> Void
    let onDoubleTap: (Bool) -> Void
    let onHoldStart: (Bool) -> Void
    let onHoldEnd: () -> Void

    func makeUIView(context: Context) -> FJPlayerView {
        let view = FJPlayerView()
        view.backgroundColor = .clear
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tap))
        let double = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.doubleTap(_:)))
        double.numberOfTapsRequired = 2
        tap.require(toFail: double)
        let hold = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.hold(_:)))
        hold.minimumPressDuration = 0.35
        view.addGestureRecognizer(tap)
        view.addGestureRecognizer(double)
        view.addGestureRecognizer(hold)
        return view
    }

    func updateUIView(_ view: FJPlayerView, context: Context) {
        context.coordinator.parent = self
        view.playerLayer.player = showVideo ? player : nil
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject {
        var parent: FJPlayerSurface
        init(_ parent: FJPlayerSurface) { self.parent = parent }

        @objc func tap() { parent.onTap() }

        @objc func doubleTap(_ g: UITapGestureRecognizer) {
            guard let view = g.view else { return }
            parent.onDoubleTap(g.location(in: view).x > view.bounds.width / 2)
        }

        @objc func hold(_ g: UILongPressGestureRecognizer) {
            guard let view = g.view else { return }
            switch g.state {
            case .began: parent.onHoldStart(g.location(in: view).x > view.bounds.width / 2)
            case .ended, .cancelled, .failed: parent.onHoldEnd()
            default: break
            }
        }
    }
}

final class FJPlayerView: UIView {
    override static var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer {
        // swiftlint:disable:next force_cast
        layer as! AVPlayerLayer
    }
}

struct FJPlayerScreen: View {
    let queue: PlaybackQueue
    let onClose: () -> Void
    @EnvironmentObject private var store: FJStore
    @StateObject private var model: FJPlayerModel
    @State private var showQueue = false
    @State private var dragging: Double?

    init(queue: PlaybackQueue, onClose: @escaping () -> Void) {
        self.queue = queue
        self.onClose = onClose
        _model = StateObject(wrappedValue: FJPlayerModel(queue: queue))
    }

    private var playback: MediaPlayback { MediaPlayback.shared }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if !model.item.isVideo {
                VStack(spacing: 24) {
                    Image(systemName: "music.note")
                        .font(.system(size: 56, weight: .semibold))
                        .foregroundStyle(FJTheme.color.primary)
                        .frame(width: 140, height: 140)
                        .background(FJTheme.color.primary.opacity(0.25), in: Circle())
                    Text(model.item.title)
                        .font(.title3)
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(3)
                        .padding(.horizontal, 32)
                }
            }
            FJPlayerSurface(
                player: model.player,
                showVideo: model.item.isVideo,
                onTap: { model.toggleControls() },
                onDoubleTap: { forward in
                    model.skip(forward ? playback.SKIP_MS : -playback.SKIP_MS)
                },
                onHoldStart: { model.beginHold(forward: $0) },
                onHoldEnd: { model.endHold() }
            )
            .ignoresSafeArea()

            if model.hold != 0 {
                Text(model.hold > 0 ? "▶▶  2x" : "◀◀  2x")
                    .font(.subheadline)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(Color.black.opacity(0.65), in: Capsule())
                    .frame(maxHeight: .infinity, alignment: .top)
                    .padding(.top, 70)
            }
            if model.failed {
                Text("⚠").font(.largeTitle).foregroundStyle(.white)
            }
            if model.finished && (queue.items.count > 1 || !queue.groupKey.isEmpty) { finishedBar }
            if model.controls { controls }
            if showQueue { queueList }
        }
        .statusBarHidden(!model.controls)
        .onDisappear { model.player.pause() }
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    private var controls: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Button(action: onClose) {
                    Image(systemName: "xmark").font(.title3.weight(.semibold)).frame(width: 44, height: 44)
                }
                Text(model.item.title).foregroundStyle(.white).lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
                if queue.items.count > 1 {
                    Text("\(model.index + 1)/\(queue.items.count)").font(.footnote).foregroundStyle(.white)
                    Button { showQueue = true } label: {
                        Image(systemName: "list.bullet").font(.title3).frame(width: 44, height: 44)
                    }
                }
            }
            .padding(.horizontal, 8)
            .foregroundStyle(.white)
            .background(Color.black.opacity(0.55))
            Spacer()
            VStack(spacing: 4) {
                HStack(spacing: 8) {
                    Text(playback.formatTime(ms: Int64((dragging ?? model.position) * 1000)))
                    Slider(
                        value: Binding(get: { dragging ?? model.position }, set: { dragging = $0 }),
                        in: 0...max(model.duration, 1)
                    ) { editing in
                        if !editing, let value = dragging {
                            model.seek(value)
                            dragging = nil
                        }
                    }
                    Text(playback.formatTime(ms: Int64(model.duration * 1000)))
                }
                .font(.caption.monospacedDigit())
                .foregroundStyle(.white)
                HStack {
                    Menu {
                        ForEach(playback.SPEEDS.map { $0.floatValue }, id: \.self) { s in
                            Button(playback.speedLabel(speed: s)) { model.setSpeed(s) }
                        }
                    } label: {
                        Text(playback.speedLabel(speed: model.speed))
                            .font(.subheadline)
                            .foregroundStyle(.white)
                            .frame(width: 64, height: 44, alignment: .leading)
                    }
                    Spacer()
                    Button { model.previous() } label: {
                        Image(systemName: "backward.end.fill").font(.title2).frame(width: 52, height: 52)
                    }
                    .disabled(!model.hasPrevious && model.position < 3)
                    Button { model.togglePlay() } label: {
                        Image(systemName: model.playing ? "pause.fill" : "play.fill")
                            .font(.title)
                            .foregroundStyle(.white)
                            .frame(width: 60, height: 60)
                            .background(FJTheme.color.primary, in: Circle())
                    }
                    Button { model.next() } label: {
                        Image(systemName: "forward.end.fill").font(.title2).frame(width: 52, height: 52)
                    }
                    .disabled(!model.hasNext)
                    Spacer()
                    Color.clear.frame(width: 64, height: 44)
                }
                .foregroundStyle(.white)
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 6)
            .background(Color.black.opacity(0.55))
        }
    }

    private var finishedBar: some View {
        VStack {
            Spacer()
            HStack(spacing: 12) {
                Text(store.strings.playerAllFinished)
                    .font(.subheadline)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button { model.startOver() } label: {
                    Text(store.strings.playerStartOver)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(FJTheme.color.primary, in: RoundedRectangle(cornerRadius: 12))
                }
            }
            .padding(.leading, 16)
            .padding(.trailing, 8)
            .padding(.vertical, 8)
            .background(Color(white: 0.12).opacity(0.95), in: RoundedRectangle(cornerRadius: 16))
            .padding(.horizontal, 16)
            .padding(.bottom, model.controls ? 150 : 24)
        }
    }

    private var queueList: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(0.55).ignoresSafeArea().onTapGesture { showQueue = false }
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(Array(queue.items.enumerated()), id: \.offset) { i, item in
                            HStack {
                                Text("\(i + 1)").font(.footnote).foregroundStyle(.secondary).frame(width: 34, alignment: .leading)
                                Text(item.title)
                                    .lineLimit(2)
                                    .foregroundStyle(i == model.index ? FJTheme.color.primary : Color.primary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                Text(item.isVideo ? "VID" : "AUD").font(.caption2).foregroundStyle(.secondary)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 14)
                            .background(i == model.index ? FJTheme.color.primary.opacity(0.18) : Color.clear)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                model.load(i, autoplay: false)
                                showQueue = false
                            }
                            .id(i)
                        }
                    }
                }
                .onAppear { proxy.scrollTo(max(model.index - 2, 0), anchor: .top) }
            }
            .frame(height: 380)
            .background(FJTheme.color.surface, in: UnevenRoundedRectangle(topLeadingRadius: 20, topTrailingRadius: 20))
        }
    }
}
