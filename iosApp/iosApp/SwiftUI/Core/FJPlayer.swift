import AVFoundation
import MediaPlayer
import SharedLogic
import SwiftUI
import UIKit

final class FJPlayer: ObservableObject {
    static let shared = FJPlayer()
    @Published var queue: PlaybackQueue?
    weak var host: IosAppHost?

    var hasHidden: Bool { queue?.items.contains { $0.hidden } ?? false }
}

struct FJPlayerTarget: Identifiable {
    let queue: PlaybackQueue
    var id: String { queue.items.map { $0.taskId }.joined(separator: ",") }
}

struct FJLevel: Equatable {
    enum Kind { case volume, brightness }
    let kind: Kind
    let value: Double
}

struct FJSeekPreview: Equatable {
    let target: Double
    let delta: Double
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
    @Published var preparing = false
    @Published var videoSize: CGSize = .zero
    @Published var autoplay: Bool
    @Published var level: FJLevel?
    @Published var seekPreview: FJSeekPreview?
    @Published var artwork: UIImage?
    var scrubbing = false {
        didSet {
            if scrubbing { hideTask?.cancel() } else { scheduleHide() }
        }
    }
    let subtitles = FJSubtitles()
    private var levelClear: Task<Void, Never>?
    private var prepareJob: Kotlinx_coroutines_coreJob?
    private var preparedId: String?
    private var loadToken = 0

    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var statusObserver: NSKeyValueObservation?
    private var rateObserver: NSKeyValueObservation?
    private var sizeObserver: NSKeyValueObservation?
    private var rewindTimer: Timer?
    private var hideTask: Task<Void, Never>?
    private var wasPlayingBeforeHold = false
    private var rewindTarget: Double = 0
    private var rewindSeeking = false
    private var dragActive = false
    private var dragStart: Double = 0
    private var dragTarget: Double = 0
    private var dragSeeking = false
    private var wasPlayingBeforeDrag = false
    private var pendingResume = false

    init(queue: PlaybackQueue) {
        self.queue = queue
        self.index = Int(queue.index)
        self.autoplay = (BehaviorSettings.shared.playerAutoplay.value as? KotlinBoolean)?.boolValue ?? true
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
        prepareJob?.cancel(cause: nil)
        hideTask?.cancel()
        levelClear?.cancel()
        if queue.items.contains(where: { $0.hidden }) { FJPlayer.shared.host?.releaseAllHidden() }
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
        loadToken += 1
        pendingResume = false
        if dragActive {
            dragActive = false
            seekPreview = nil
            scrubbing = false
        }
        prepareJob?.cancel(cause: nil)
        prepareJob = nil
        if let old = preparedId, old != entry.taskId {
            player.replaceCurrentItem(with: nil)
            FJPlayer.shared.host?.releaseHidden(taskId: old)
            preparedId = nil
        }
        guard HiddenPlayback.shared.needsPrepare(item: entry), let host = FJPlayer.shared.host else {
            preparing = false
            start(path: entry.path, autoplay: autoplay)
            return
        }
        player.replaceCurrentItem(with: nil)
        preparing = true
        let token = loadToken
        prepareJob = host.prepareHidden(taskId: entry.taskId, onProgress: { _ in }, onDone: { [weak self] path in
            guard let self, self.loadToken == token else { return }
            self.preparing = false
            self.prepareJob = nil
            guard let path, !path.isEmpty else {
                self.controls = true
                self.failed = true
                return
            }
            self.preparedId = entry.taskId
            self.start(path: path, autoplay: autoplay)
        })
    }

    private func start(path: String, autoplay: Bool) {
        let playerItem = AVPlayerItem(url: URL(fileURLWithPath: path))
        if item.isVideo {
            subtitles.attach(player: player, item: playerItem, entry: item, path: path)
            artwork = nil
        } else {
            controls = true
            loadArtwork(playerItem.asset)
        }
        videoSize = .zero
        sizeObserver = playerItem.observe(\.presentationSize, options: [.new]) { [weak self] it, _ in
            let size = it.presentationSize
            DispatchQueue.main.async {
                guard let self, size != self.videoSize else { return }
                self.videoSize = size
            }
        }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime, object: playerItem, queue: .main
        ) { [weak self] _ in
            guard let self else { return }
            if self.hasNext {
                self.load(self.index + 1, autoplay: self.autoplay)
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
                    if self.hasNext { self.load(self.index + 1, autoplay: self.autoplay) } else { self.failed = true }
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

    func next() { if hasNext { load(index + 1, autoplay: autoplay) } }

    func startOver() { load(0, autoplay: true) }

    func previous() {
        if position > 3 || !hasPrevious { seek(0) } else { load(index - 1, autoplay: autoplay) }
    }

    func setAutoplay(_ on: Bool) {
        autoplay = on
        BehaviorSettings.shared.setPlayerAutoplay(on: on)
        scheduleHide()
    }

    func showLevel(_ next: FJLevel?) {
        levelClear?.cancel()
        if let next {
            hideTask?.cancel()
            level = next
            return
        }
        levelClear = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 600_000_000)
            guard let self, !Task.isCancelled else { return }
            self.level = nil
            self.scheduleHide()
        }
    }

    private func loadArtwork(_ asset: AVAsset) {
        artwork = nil
        let token = loadToken
        Task { @MainActor [weak self] in
            guard let metadata = try? await asset.load(.commonMetadata),
                  let art = AVMetadataItem.metadataItems(from: metadata, filteredByIdentifier: .commonIdentifierArtwork).first,
                  let data = try? await art.load(.dataValue),
                  let image = UIImage(data: data) else { return }
            guard let self, self.loadToken == token else { return }
            self.artwork = image
        }
    }

    func seek(_ seconds: Double) {
        let target = max(0, duration > 0 ? min(seconds, duration) : seconds)
        player.seek(to: CMTime(seconds: target, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        position = target
        scheduleHide()
    }

    func skip(_ ms: Int64) { seek(position + Double(ms) / 1000) }

    var canDragSeek: Bool {
        item.isVideo && duration > 0 && !preparing && !failed && hold == 0
    }

    func beginDragSeek() {
        guard canDragSeek, !dragActive else { return }
        dragActive = true
        wasPlayingBeforeDrag = playing || pendingResume
        pendingResume = false
        player.pause()
        let now = player.currentTime().seconds
        dragStart = now.isFinite ? min(max(now, 0), duration) : position
        dragTarget = dragStart
        scrubbing = true
        seekPreview = FJSeekPreview(target: dragStart, delta: 0)
    }

    func dragSeek(fraction: Double) {
        guard dragActive else { return }
        let span = min(90, duration)
        dragTarget = min(max(dragStart + fraction * span, 0), duration)
        position = dragTarget
        seekPreview = FJSeekPreview(target: dragTarget, delta: dragTarget - dragStart)
        pumpDragSeek()
    }

    private func pumpDragSeek() {
        guard dragActive, !dragSeeking else { return }
        dragSeeking = true
        let target = dragTarget
        let tolerance = CMTime(seconds: 0.5, preferredTimescale: 600)
        player.seek(
            to: CMTime(seconds: target, preferredTimescale: 600),
            toleranceBefore: tolerance, toleranceAfter: tolerance
        ) { [weak self] _ in
            DispatchQueue.main.async {
                guard let self else { return }
                self.dragSeeking = false
                if self.dragActive && self.dragTarget != target { self.pumpDragSeek() }
            }
        }
    }

    func endDragSeek() {
        guard dragActive else { return }
        dragActive = false
        let target = dragTarget
        pendingResume = wasPlayingBeforeDrag
        position = target
        seekPreview = nil
        player.seek(
            to: CMTime(seconds: target, preferredTimescale: 600),
            toleranceBefore: .zero, toleranceAfter: .zero
        ) { [weak self] _ in
            DispatchQueue.main.async {
                guard let self, self.pendingResume else { return }
                self.pendingResume = false
                self.player.playImmediately(atRate: self.speed)
            }
        }
        scrubbing = false
    }

    func setSpeed(_ value: Float) {
        speed = value
        if playing { player.rate = value }
        scheduleHide()
    }

    func toggleControls() {
        guard item.isVideo else { return }
        controls.toggle()
        scheduleHide()
    }

    private var canHide: Bool {
        item.isVideo && hold == 0 && level == nil && !scrubbing && !preparing && !failed && !finished
    }

    func scheduleHide() {
        hideTask?.cancel()
        guard controls, canHide else { return }
        hideTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            guard let self, !Task.isCancelled, self.canHide else { return }
            self.controls = false
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
    let seekable: Bool
    let onTap: () -> Void
    let onDoubleTap: (Bool) -> Void
    let onHoldStart: (Bool) -> Void
    let onHoldEnd: () -> Void
    let onLevel: (FJLevel?) -> Void
    let onSeekStart: () -> Void
    let onSeekMove: (Double) -> Void
    let onSeekEnd: () -> Void

    func makeUIView(context: Context) -> FJPlayerView {
        let view = FJPlayerView()
        view.backgroundColor = .clear
        view.addVolumeView()
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tap))
        let double = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.doubleTap(_:)))
        double.numberOfTapsRequired = 2
        tap.require(toFail: double)
        let hold = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.hold(_:)))
        hold.minimumPressDuration = 0.35
        let pan = UIPanGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.pan(_:)))
        pan.maximumNumberOfTouches = 1
        pan.delegate = context.coordinator
        view.addGestureRecognizer(tap)
        view.addGestureRecognizer(double)
        view.addGestureRecognizer(hold)
        view.addGestureRecognizer(pan)
        return view
    }

    func updateUIView(_ view: FJPlayerView, context: Context) {
        context.coordinator.parent = self
        view.playerLayer.player = showVideo ? player : nil
    }

    static func dismantleUIView(_ view: FJPlayerView, coordinator: Coordinator) {
        coordinator.restoreBrightness()
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: FJPlayerSurface
        private var kind: FJLevel.Kind?
        private var horizontal = false
        private var seeking = false
        private var startValue: Double = 0
        private var savedBrightness: CGFloat?
        private weak var screen: UIScreen?
        private var backgroundObserver: NSObjectProtocol?

        init(_ parent: FJPlayerSurface) {
            self.parent = parent
            super.init()
            backgroundObserver = NotificationCenter.default.addObserver(
                forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main
            ) { [weak self] _ in self?.restoreBrightness() }
        }

        deinit {
            if let backgroundObserver { NotificationCenter.default.removeObserver(backgroundObserver) }
        }

        func restoreBrightness() {
            if let savedBrightness, let screen { screen.brightness = savedBrightness }
            savedBrightness = nil
        }

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

        func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
            guard let pan = g as? UIPanGestureRecognizer, let view = pan.view else { return true }
            let velocity = pan.velocity(in: view)
            let y = pan.location(in: view).y
            let edge: CGFloat = 44
            if parent.seekable && abs(velocity.x) > abs(velocity.y) * 1.5 {
                horizontal = true
                return true
            }
            horizontal = false
            return abs(velocity.y) > abs(velocity.x) * 1.5 && y > edge && y < view.bounds.height - edge
        }

        @objc func pan(_ g: UIPanGestureRecognizer) {
            guard let view = g.view as? FJPlayerView else { return }
            if horizontal || seeking {
                seekPan(g, in: view)
                return
            }
            switch g.state {
            case .began:
                if g.location(in: view).x > view.bounds.width / 2 {
                    kind = .volume
                    startValue = Double(view.volumeSlider == nil ? parent.player.volume : AVAudioSession.sharedInstance().outputVolume)
                } else {
                    guard let found = view.window?.windowScene?.screen else { return }
                    screen = found
                    if savedBrightness == nil { savedBrightness = found.brightness }
                    kind = .brightness
                    startValue = Double(found.brightness)
                }
                if let kind { parent.onLevel(FJLevel(kind: kind, value: startValue)) }
            case .changed:
                guard let kind else { return }
                let span = max(view.bounds.height * 0.7, 1)
                let value = min(1, max(0, startValue - Double(g.translation(in: view).y / span)))
                switch kind {
                case .volume:
                    if let slider = view.volumeSlider { slider.value = Float(value) } else { parent.player.volume = Float(value) }
                case .brightness:
                    screen?.brightness = CGFloat(value)
                }
                parent.onLevel(FJLevel(kind: kind, value: value))
            case .ended, .cancelled, .failed:
                if kind != nil { parent.onLevel(nil) }
                kind = nil
            default: break
            }
        }

        private func seekPan(_ g: UIPanGestureRecognizer, in view: UIView) {
            switch g.state {
            case .began:
                seeking = true
                parent.onSeekStart()
            case .changed:
                guard seeking else { return }
                let width = max(view.bounds.width, 1)
                parent.onSeekMove(Double(g.translation(in: view).x / width))
            case .ended, .cancelled, .failed:
                if seeking { parent.onSeekEnd() }
                seeking = false
                horizontal = false
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

    private var volumeView: MPVolumeView?

    func addVolumeView() {
        guard volumeView == nil else { return }
        let hidden = MPVolumeView(frame: CGRect(x: -2000, y: -2000, width: 1, height: 1))
        hidden.alpha = 0.01
        hidden.isUserInteractionEnabled = false
        addSubview(hidden)
        volumeView = hidden
    }

    var volumeSlider: UISlider? { volumeView?.subviews.compactMap { $0 as? UISlider }.first }
}

struct FJSeekBar: View {
    let value: Double
    let total: Double
    @Binding var dragging: Double?
    let onCommit: (Double) -> Void
    let onStep: (Bool) -> Void

    var body: some View {
        GeometryReader { geo in
            let width = max(geo.size.width, 1)
            let active = dragging != nil
            let fraction = total > 0 ? min(max((dragging ?? value) / total, 0), 1) : 0
            let knob: CGFloat = active ? 16 : 12
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.3)).frame(height: active ? 6 : 3)
                Capsule().fill(FJTheme.color.primary).frame(width: width * CGFloat(fraction), height: active ? 6 : 3)
                Circle().fill(FJTheme.color.primary).frame(width: knob, height: knob)
                    .offset(x: width * CGFloat(fraction) - knob / 2)
            }
            .frame(width: width, height: geo.size.height)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { g in
                        guard total > 0 else { return }
                        dragging = Double(min(max(g.location.x / width, 0), 1)) * total
                    }
                    .onEnded { g in
                        guard total > 0 else { return }
                        onCommit(Double(min(max(g.location.x / width, 0), 1)) * total)
                        dragging = nil
                    }
            )
            .animation(.easeOut(duration: 0.15), value: active)
        }
        .frame(height: 24)
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityElement()
        .accessibilityValue(MediaPlayback.shared.formatTime(ms: Int64(value * 1000)))
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: onStep(true)
            case .decrement: onStep(false)
            @unknown default: break
            }
        }
    }
}


struct FJPlayerScreen: View {
    let queue: PlaybackQueue
    let onClose: () -> Void
    @EnvironmentObject private var store: FJStore
    @StateObject private var model: FJPlayerModel
    @Environment(\.scenePhase) private var phase
    @Environment(\.verticalSizeClass) private var verticalSizeClass
    @State private var showQueue = false
    @State private var pickingSubtitle = false
    @State private var dragging: Double?
    @State private var barTop: CGFloat = .infinity

    init(queue: PlaybackQueue, onClose: @escaping () -> Void) {
        self.queue = queue
        self.onClose = onClose
        _model = StateObject(wrappedValue: FJPlayerModel(queue: queue))
    }

    private var playback: MediaPlayback { MediaPlayback.shared }
    private var hasHidden: Bool { queue.items.contains { $0.hidden } }
    private var showsFinishedBar: Bool { model.finished && (queue.items.count > 1 || !queue.groupKey.isEmpty) }
    private var showsCenter: Bool {
        !model.preparing && !model.failed && model.hold == 0 && !overlayShown && !showsFinishedBar
    }
    private var overlayShown: Bool { model.level != nil || model.seekPreview != nil }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            FJPlayerSurface(
                player: model.player,
                showVideo: model.item.isVideo,
                seekable: model.canDragSeek,
                onTap: { model.toggleControls() },
                onDoubleTap: { forward in
                    model.skip(forward ? playback.SKIP_MS : -playback.SKIP_MS)
                },
                onHoldStart: { model.beginHold(forward: $0) },
                onHoldEnd: { model.endHold() },
                onLevel: { model.showLevel($0) },
                onSeekStart: { model.beginDragSeek() },
                onSeekMove: { model.dragSeek(fraction: $0) },
                onSeekEnd: { model.endDragSeek() }
            )
            .ignoresSafeArea()

            if model.item.isVideo {
                FJSubtitleOverlay(subtitles: model.subtitles, videoSize: model.videoSize,
                                  barTop: model.controls && !overlayShown ? barTop : nil)
                if model.controls && !overlayShown {
                    videoControls.transition(.opacity)
                } else if !model.playing && showsCenter {
                    playButton.transition(.opacity)
                }
            } else {
                audioControls
            }
            if let level = model.level { levelBox(level) }
            if let preview = model.seekPreview { seekBox(preview) }
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
            if model.preparing {
                ProgressView()
                    .tint(.white)
                    .scaleEffect(1.4)
            }
            if model.failed {
                Text("⚠").font(.largeTitle).foregroundStyle(.white)
            }
            if showsFinishedBar { finishedBar }
            if showQueue { queueList }
        }
        .animation(.easeInOut(duration: 0.25), value: model.controls)
        .animation(.easeInOut(duration: 0.2), value: model.level == nil)
        .animation(.easeInOut(duration: 0.2), value: model.seekPreview == nil)
        .statusBarHidden(!model.controls)
        .overlay {
            if hasHidden && phase != .active { Color.black.ignoresSafeArea() }
        }
        .fileImporter(isPresented: $pickingSubtitle, allowedContentTypes: FJSubtitles.fileTypes) { result in
            if case .success(let url) = result { model.subtitles.pickFile(url) }
        }
        .onReceive(model.subtitles.$failed) { failed in
            if failed {
                store.showToast(store.strings.playerSubtitlesFailed)
                model.subtitles.failed = false
            }
        }
        .onChange(of: dragging == nil) { _, idle in model.scrubbing = !idle }
        .onChange(of: phase) { _, new in
            if hasHidden && new != .active { model.player.pause() }
        }
        .onChange(of: store.state.hidden.unlocked) { _, unlocked in
            if hasHidden && !unlocked { onClose() }
        }
        .onChange(of: store.state.hidden.covered) { _, covered in
            if hasHidden && covered { onClose() }
        }
        .onDisappear {
            model.player.pause()
            if hasHidden {
                store.host.releaseAllHidden()
                store.send(DashboardUiEventOnHiddenClosePlayer.shared)
            }
        }
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    private var videoControls: some View {
        ZStack {
            VStack(spacing: 0) {
                LinearGradient(colors: [Color.black.opacity(0.7), .clear], startPoint: .top, endPoint: .bottom)
                    .frame(height: 130)
                Spacer(minLength: 0)
                LinearGradient(colors: [.clear, Color.black.opacity(0.8)], startPoint: .top, endPoint: .bottom)
                    .frame(height: 170)
            }
            .ignoresSafeArea()
            .allowsHitTesting(false)
            VStack(spacing: 0) {
                topBar
                Spacer(minLength: 0)
                bottomBar
            }
            if showsCenter { transport }
        }
    }

    private var audioControls: some View {
        VStack(spacing: 0) {
            topBar
            Spacer(minLength: 8)
            if verticalSizeClass == .compact {
                HStack(spacing: 32) {
                    cover(150)
                    VStack(spacing: 16) {
                        audioTitle
                        if showsCenter { transport }
                    }
                }
                .padding(.horizontal, 24)
            } else {
                VStack(spacing: 20) {
                    cover(240)
                    audioTitle
                    if showsCenter { transport } else { Color.clear.frame(height: 72) }
                }
            }
            Spacer(minLength: 8)
            bottomBar
        }
    }

    private var audioTitle: some View {
        Text(model.item.title)
            .font(.headline)
            .foregroundStyle(.white)
            .multilineTextAlignment(.center)
            .lineLimit(2)
            .padding(.horizontal, 24)
            .allowsHitTesting(false)
    }

    private func cover(_ side: CGFloat) -> some View {
        Group {
            if let art = model.artwork {
                Image(uiImage: art).resizable().scaledToFill()
            } else {
                ZStack {
                    LinearGradient(
                        colors: [FJTheme.color.primary.opacity(0.9), FJTheme.color.primary.opacity(0.35)],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    )
                    Image(systemName: "music.note")
                        .font(.system(size: side * 0.36, weight: .semibold))
                        .foregroundStyle(Color.white.opacity(0.9))
                }
            }
        }
        .frame(width: side, height: side)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .allowsHitTesting(false)
    }

    private var topBar: some View {
        HStack(spacing: 4) {
            Button(action: onClose) {
                Image(systemName: "xmark").font(.body.weight(.semibold)).frame(width: 44, height: 44)
            }
            .accessibilityLabel(store.strings.close)
            Text(model.item.title)
                .font(.subheadline)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            if queue.items.count > 1 {
                Text("\(model.index + 1)/\(queue.items.count)").font(.caption.monospacedDigit())
                Button { showQueue = true } label: {
                    Image(systemName: "list.bullet").font(.body).frame(width: 44, height: 44)
                }
            }
        }
        .padding(.horizontal, 8)
        .foregroundStyle(.white)
    }

    private var transport: some View {
        let canGoBack = model.hasPrevious || model.position >= 3
        return HStack(spacing: 28) {
            Button { model.previous() } label: {
                Image(systemName: "backward.end.fill").font(.title2).frame(width: 48, height: 48)
            }
            .disabled(!canGoBack)
            .opacity(canGoBack ? 1 : 0.35)
            playButton
            Button { model.next() } label: {
                Image(systemName: "forward.end.fill").font(.title2).frame(width: 48, height: 48)
            }
            .disabled(!model.hasNext)
            .opacity(model.hasNext ? 1 : 0.35)
        }
        .foregroundStyle(.white)
        .environment(\.layoutDirection, .leftToRight)
    }

    private var playButton: some View {
        let size: CGFloat = model.playing ? 60 : 72
        return Button { model.togglePlay() } label: {
            Image(systemName: model.playing ? "pause.fill" : "play.fill")
                .font(.system(size: model.playing ? 24 : 30, weight: .semibold))
                .foregroundStyle(.white)
                .offset(x: model.playing ? 0 : 2)
                .frame(width: size, height: size)
                .background(Color.black.opacity(0.45), in: Circle())
                .overlay(Circle().stroke(Color.white.opacity(0.15), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .environment(\.layoutDirection, .leftToRight)
    }

    private func time(_ seconds: Double) -> String {
        playback.formatTime(ms: Int64((seconds.isFinite ? seconds : 0) * 1000))
    }

    private var bottomBar: some View {
        VStack(spacing: 0) {
            HStack {
                Text(time(dragging ?? model.position))
                Spacer(minLength: 8)
                Text(time(model.duration))
            }
            .font(.caption2.monospacedDigit())
            .foregroundStyle(Color.white.opacity(0.9))
            .environment(\.layoutDirection, .leftToRight)
            FJSeekBar(
                value: model.position,
                total: model.duration,
                dragging: $dragging,
                onCommit: { model.seek($0) },
                onStep: { forward in model.skip(forward ? playback.SKIP_MS : -playback.SKIP_MS) }
            )
            HStack(spacing: 14) {
                Menu {
                    ForEach(playback.SPEEDS.map { $0.floatValue }, id: \.self) { s in
                        Button(playback.speedLabel(speed: s)) { model.setSpeed(s) }
                    }
                } label: {
                    Text(playback.speedLabel(speed: model.speed))
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(minWidth: 36, minHeight: 36, alignment: .leading)
                }
                autoplayToggle
                Spacer(minLength: 0)
                if model.item.isVideo {
                    FJSubtitleMenu(subtitles: model.subtitles, picking: $pickingSubtitle)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 4)
        .onGeometryChange(for: CGFloat.self) { $0.frame(in: .global).minY } action: { barTop = $0 }
    }

    private var autoplayToggle: some View {
        let on = model.autoplay
        return Button { model.setAutoplay(!on) } label: {
            HStack(spacing: 6) {
                ZStack(alignment: on ? Alignment.trailing : Alignment.leading) {
                    Capsule().fill(on ? FJTheme.color.primary : Color.white.opacity(0.3))
                    Circle().fill(Color.white).padding(2)
                }
                .frame(width: 28, height: 16)
                Text(store.strings.playerAutoplay)
                    .font(.caption)
                    .foregroundStyle(.white)
                    .lineLimit(1)
            }
            .frame(minHeight: 36)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .animation(.easeOut(duration: 0.15), value: on)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(store.strings.playerAutoplay)
        .accessibilityValue(on ? "1" : "0")
        .accessibilityAddTraits(.isButton)
    }

    private func levelBox(_ level: FJLevel) -> some View {
        let s = store.strings
        let isVolume = level.kind == .volume
        let muted = isVolume && level.value < 0.005
        let symbol = isVolume ? (muted ? "speaker.slash.fill" : "speaker.wave.2.fill") : "sun.max.fill"
        let percent = "\(Int((level.value * 100).rounded()))%"
        return VStack(spacing: 10) {
            Image(systemName: symbol)
                .font(.system(size: 28, weight: .semibold))
                .foregroundStyle(isVolume ? Color.white : Color.yellow)
                .frame(height: 32)
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.25))
                Capsule().fill(Color.white).frame(width: 84 * CGFloat(level.value))
            }
            .frame(width: 84, height: 4)
            Text(percent)
                .font(.caption.monospacedDigit())
                .foregroundStyle(.white)
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 16)
        .background(Color.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .environment(\.layoutDirection, .leftToRight)
        .allowsHitTesting(false)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(isVolume ? (muted ? s.playerMute : s.playerVolume) : s.playerBrightness)
        .accessibilityValue(percent)
    }

    private func seekBox(_ preview: FJSeekPreview) -> some View {
        let seconds = Int(preview.delta.rounded())
        let delta = seconds < 0 ? "(-\(-seconds)s)" : "(+\(seconds)s)"
        let label = "\(time(preview.target)) / \(time(model.duration))"
        return VStack(spacing: 6) {
            Text(label)
                .font(.title3.weight(.semibold).monospacedDigit())
                .foregroundStyle(.white)
            Text(delta)
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(Color.white.opacity(0.85))
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 16)
        .background(Color.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .environment(\.layoutDirection, .leftToRight)
        .allowsHitTesting(false)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label) \(delta)")
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
            .padding(.bottom, model.controls ? 110 : 24)
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
                                model.load(i, autoplay: model.autoplay)
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
