import AVFoundation
import SwiftUI
import UIKit

final class FJTrimViewport: ObservableObject {
    static let defaultSpanMs: Double = 120_000
    static let finestSpanMs: Double = 5_000

    let durationMs: Double
    @Published private(set) var startMs: Double = 0
    @Published private(set) var msPerPoint: Double = 1
    @Published private(set) var width: CGFloat = 0
    private(set) var interacting = false
    private var zoomed = false
    private var flingTimer: Timer?
    private var flingVelocity: Double = 0
    private var flingLast: CFTimeInterval = 0
    private var edgeTimer: Timer?
    private var edgeSpeed: Double = 0
    private var edgeLast: CFTimeInterval = 0
    private var edgeTick: (() -> Void)?

    init(durationMs: Int64) {
        self.durationMs = Double(max(durationMs, 1))
    }

    deinit {
        flingTimer?.invalidate()
        edgeTimer?.invalidate()
    }

    var spanMs: Double { Double(width) * msPerPoint }
    var endMs: Double { startMs + spanMs }

    private var fullScale: Double { durationMs / Double(max(width, 1)) }
    private var finestScale: Double { min(Self.finestSpanMs / Double(max(width, 1)), fullScale) }

    func x(_ ms: Double) -> CGFloat { CGFloat((ms - startMs) / msPerPoint) }

    func ms(at x: CGFloat) -> Double { startMs + Double(x) * msPerPoint }

    @discardableResult
    func setWidth(_ value: CGFloat) -> Bool {
        guard value > 1, abs(value - width) >= 0.5 else { return false }
        let first = width <= 1
        let center = startMs + spanMs / 2
        width = value
        msPerPoint = zoomed ? clampScale(msPerPoint) : min(Self.defaultSpanMs / Double(value), fullScale)
        startMs = first ? 0 : clampStart(center - spanMs / 2)
        return first
    }

    private func clampScale(_ value: Double) -> Double { min(max(value, finestScale), fullScale) }

    private func clampStart(_ value: Double) -> Double { min(max(value, 0), max(durationMs - spanMs, 0)) }

    func scroll(to value: Double) {
        let next = clampStart(value)
        if next != startMs { startMs = next }
    }

    func pan(by dx: CGFloat) {
        scroll(to: startMs - Double(dx) * msPerPoint)
    }

    func beginInteraction() {
        stopFling()
        interacting = true
    }

    func endInteraction() {
        interacting = false
    }

    func zoom(from base: Double, by scale: CGFloat, anchorX: CGFloat, anchorMs: Double) {
        guard width > 1, scale > 0 else { return }
        zoomed = true
        let next = clampScale(base / Double(scale))
        if next != msPerPoint { msPerPoint = next }
        scroll(to: anchorMs - Double(anchorX) * msPerPoint)
    }

    func reveal(_ ms: Double) {
        guard width > 1 else { return }
        let margin = Double(min(36, width / 4)) * msPerPoint
        if ms < startMs + margin {
            stopFling()
            scroll(to: ms - margin)
        } else if ms > endMs - margin {
            stopFling()
            scroll(to: ms - spanMs + margin)
        }
    }

    func center(on ms: Double) {
        stopFling()
        scroll(to: ms - spanMs / 2)
    }

    func follow(_ ms: Double) {
        guard !interacting, edgeTimer == nil, flingTimer == nil, width > 1 else { return }
        if ms > endMs - spanMs * 0.04 || ms < startMs {
            scroll(to: ms - spanMs * 0.1)
        }
    }

    func fling(_ velocity: CGFloat) {
        stopFling()
        guard abs(velocity) > 80 else { return }
        flingVelocity = Double(velocity)
        flingLast = CACurrentMediaTime()
        let timer = Timer(timeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            self?.flingStep()
        }
        RunLoop.main.add(timer, forMode: .common)
        flingTimer = timer
    }

    private func flingStep() {
        let now = CACurrentMediaTime()
        let dt = min(now - flingLast, 0.05)
        flingLast = now
        let before = startMs
        pan(by: CGFloat(flingVelocity * dt))
        flingVelocity *= pow(0.998, dt * 1000)
        if abs(flingVelocity) < 20 || startMs == before { stopFling() }
    }

    func stopFling() {
        flingTimer?.invalidate()
        flingTimer = nil
    }

    func edgeScroll(speed: Double, tick: @escaping () -> Void) {
        edgeSpeed = speed
        edgeTick = tick
        guard edgeTimer == nil else { return }
        stopFling()
        edgeLast = CACurrentMediaTime()
        let timer = Timer(timeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            self?.edgeStep()
        }
        RunLoop.main.add(timer, forMode: .common)
        edgeTimer = timer
    }

    private func edgeStep() {
        let now = CACurrentMediaTime()
        let dt = min(now - edgeLast, 0.05)
        edgeLast = now
        let before = startMs
        scroll(to: startMs + edgeSpeed * dt * msPerPoint)
        if startMs != before { edgeTick?() }
    }

    func stopEdgeScroll() {
        edgeTimer?.invalidate()
        edgeTimer = nil
        edgeTick = nil
        edgeSpeed = 0
    }
}

final class FJTrimThumbs: ObservableObject {
    static let tilePoints: Double = 64
    private static let limit = 120

    let asset: AVAsset
    let durationMs: Double
    private let maxSize: CGSize
    @Published private(set) var images: [String: CGImage] = [:]
    private var times: [String: Double] = [:]
    private var failed: Set<String> = []
    private var inFlight: Set<String> = []
    private var generator: AVAssetImageGenerator?
    private var bucket: Double = 0
    private var batch = 0
    private var pending: DispatchWorkItem?

    init(asset: AVAsset, durationMs: Int64, height: CGFloat) {
        self.asset = asset
        self.durationMs = Double(max(durationMs, 1))
        let scale = UIScreen.main.scale
        maxSize = CGSize(width: height * 2.3 * scale, height: height * scale)
    }

    deinit {
        pending?.cancel()
        generator?.cancelAllCGImageGeneration()
    }

    static func bucketMs(msPerPoint: Double) -> Double {
        let want = msPerPoint * tilePoints
        var value = 100.0
        while value < want { value *= 2 }
        return value
    }

    func count(_ bucket: Double) -> Int { Int((durationMs / bucket).rounded(.up)) }

    private static func key(_ bucket: Double, _ index: Int) -> String { "\(Int(bucket)):\(index)" }

    private func mid(_ bucket: Double, _ index: Int) -> Double {
        min((Double(index) + 0.5) * bucket, max(durationMs - 40, 0))
    }

    func image(bucket: Double, index: Int) -> CGImage? {
        if let image = images[Self.key(bucket, index)] { return image }
        var coarse = bucket
        var at = index
        for _ in 0..<5 {
            coarse *= 2
            at /= 2
            if let image = images[Self.key(coarse, at)] { return image }
        }
        if bucket >= 200, let image = images[Self.key(bucket / 2, index * 2)] { return image }
        return nil
    }

    func update(startMs: Double, spanMs: Double, msPerPoint: Double) {
        guard spanMs > 0 else { return }
        pending?.cancel()
        let work = DispatchWorkItem { [weak self] in
            self?.load(startMs: startMs, spanMs: spanMs, msPerPoint: msPerPoint)
        }
        pending = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05, execute: work)
    }

    private func load(startMs: Double, spanMs: Double, msPerPoint: Double) {
        let b = Self.bucketMs(msPerPoint: msPerPoint)
        let total = count(b)
        guard total > 0 else { return }
        let first = max(Int(((startMs - spanMs) / b).rounded(.down)), 0)
        let last = min(Int(((startMs + spanMs * 2) / b).rounded(.down)), total - 1)
        guard first <= last else { return }
        let wanted = Set((first...last).map { Self.key(b, $0) })
        if b != bucket || !inFlight.isSubset(of: wanted) {
            generator?.cancelAllCGImageGeneration()
            generator = nil
            inFlight.removeAll()
            batch += 1
            bucket = b
        }
        let center = startMs + spanMs / 2
        prune(keep: wanted, center: center)
        let missing = (first...last)
            .filter { index in
                let k = Self.key(b, index)
                return images[k] == nil && !inFlight.contains(k) && !failed.contains(k)
            }
            .sorted { abs(mid(b, $0) - center) < abs(mid(b, $1) - center) }
        guard !missing.isEmpty else { return }
        let gen = generator ?? makeGenerator(bucket: b)
        generator = gen
        var keys: [Int64: String] = [:]
        var requested: [NSValue] = []
        for index in missing {
            let ms = Int64(mid(b, index).rounded())
            let k = Self.key(b, index)
            keys[ms] = k
            inFlight.insert(k)
            requested.append(NSValue(time: CMTime(value: ms, timescale: 1000)))
        }
        let keyMap = keys
        let token = batch
        gen.generateCGImagesAsynchronously(forTimes: requested) { [weak self] time, image, _, result, _ in
            let ms = CMTimeConvertScale(time, timescale: 1000, method: .roundHalfAwayFromZero).value
            DispatchQueue.main.async {
                guard let self, self.batch == token, let k = keyMap[ms] else { return }
                self.inFlight.remove(k)
                if result == .succeeded, let image {
                    self.images[k] = image
                    self.times[k] = Double(ms)
                } else if result == .failed {
                    self.failed.insert(k)
                }
            }
        }
    }

    private func makeGenerator(bucket: Double) -> AVAssetImageGenerator {
        let gen = AVAssetImageGenerator(asset: asset)
        gen.appliesPreferredTrackTransform = true
        gen.maximumSize = maxSize
        let tolerance = CMTime(value: Int64(bucket / 2), timescale: 1000)
        gen.requestedTimeToleranceBefore = tolerance
        gen.requestedTimeToleranceAfter = tolerance
        return gen
    }

    private func prune(keep: Set<String>, center: Double) {
        guard images.count > Self.limit else { return }
        let drop = images.keys
            .filter { !keep.contains($0) }
            .sorted { abs((times[$0] ?? 0) - center) > abs((times[$1] ?? 0) - center) }
            .prefix(images.count - Self.limit)
        guard !drop.isEmpty else { return }
        var next = images
        for k in drop {
            next[k] = nil
            times[k] = nil
        }
        images = next
    }
}

private struct FJTrimPinch {
    let scale: Double
    let anchorX: CGFloat
    let anchorMs: Double
}

private enum FJTrimGesture {
    case pending
    case pan
    case ignored
    case handle(FJTrimEdge, CGFloat)
}

struct FJTrimTimeline: View {
    @EnvironmentObject var store: FJStore
    @ObservedObject private var model: FJTrimModel
    @StateObject private var viewport: FJTrimViewport
    @StateObject private var thumbs: FJTrimThumbs
    @State private var touch: FJTrimGesture?
    @State private var lastPan: CGFloat = 0
    @State private var pinch: FJTrimPinch?
    private let handle: CGFloat = 18
    private let stripHeight: CGFloat = 56
    private let inset: CGFloat = 4

    init(model: FJTrimModel) {
        _model = ObservedObject(wrappedValue: model)
        _viewport = StateObject(wrappedValue: FJTrimViewport(durationMs: model.durationMs))
        _thumbs = StateObject(wrappedValue: FJTrimThumbs(asset: model.asset, durationMs: model.durationMs, height: 56))
    }

    private var draggingHandle: Bool {
        if case .handle(_, _)? = touch { return true }
        return false
    }

    var body: some View {
        VStack(spacing: 10) {
            GeometryReader { geo in
                track(total: geo.size.width)
                    .onAppear {
                        setWidth(geo.size.width)
                        if model.audio { model.loadWave(host: store.host) }
                        refreshThumbs()
                    }
                    .onChange(of: geo.size.width) { _, w in setWidth(w) }
            }
            .frame(height: stripHeight + inset * 2)
        }
        .environment(\.layoutDirection, .leftToRight)
        .onChange(of: viewport.startMs) { _, _ in refreshThumbs() }
        .onChange(of: viewport.msPerPoint) { _, _ in refreshThumbs() }
        .onChange(of: viewport.width) { _, _ in refreshThumbs() }
        .onChange(of: model.startMs) { _, value in revealIfIdle(value) }
        .onChange(of: model.endMs) { _, value in revealIfIdle(value) }
        .onChange(of: model.focusTick) { _, _ in
            viewport.reveal(Double(model.edge == .start ? model.startMs : model.endMs))
        }
        .onChange(of: model.playheadMs) { _, value in
            if model.playingRange { viewport.follow(Double(value)) }
        }
        .onDisappear {
            viewport.stopFling()
            viewport.stopEdgeScroll()
        }
    }

    private func setWidth(_ total: CGFloat) {
        if viewport.setWidth(max(total - handle * 2, 1)) {
            viewport.reveal(Double(model.startMs))
        }
    }

    private func revealIfIdle(_ ms: Int64) {
        guard !draggingHandle else { return }
        viewport.reveal(Double(ms))
    }

    private func refreshThumbs() {
        guard !model.audio, viewport.width > 1 else { return }
        thumbs.update(startMs: viewport.startMs, spanMs: viewport.spanMs, msPerPoint: viewport.msPerPoint)
    }

    private func track(total: CGFloat) -> some View {
        Canvas { context, size in
            draw(context, size)
        }
        .contentShape(Rectangle())
        .gesture(dragGesture(total: total))
        .simultaneousGesture(pinchGesture(total: total))
    }

    private func draw(_ context: GraphicsContext, _ size: CGSize) {
        let strip = CGRect(x: handle, y: inset, width: max(size.width - handle * 2, 1), height: max(size.height - inset * 2, 1))
        let primary = FJTheme.color.primary
        let shape = Path(roundedRect: strip, cornerRadius: 4)
        context.fill(shape, with: .color(Color.white.opacity(0.08)))
        var layer = context
        layer.clip(to: shape)
        if model.audio {
            drawWave(layer, strip)
        } else {
            drawThumbs(layer, strip)
        }
        let rawStart = strip.minX + viewport.x(Double(model.startMs))
        let rawEnd = strip.minX + viewport.x(Double(model.endMs))
        let sx = min(max(rawStart, -40), size.width + 40)
        let ex = min(max(rawEnd, -40), size.width + 40)
        let dim = Color.black.opacity(0.6)
        if sx > strip.minX {
            layer.fill(Path(CGRect(x: strip.minX, y: strip.minY, width: sx - strip.minX, height: strip.height)), with: .color(dim))
        }
        if ex < strip.maxX {
            layer.fill(Path(CGRect(x: ex, y: strip.minY, width: strip.maxX - ex, height: strip.height)), with: .color(dim))
        }
        if ex > -20 && sx < size.width + 20 {
            let box = CGRect(x: sx, y: strip.minY + 1.5, width: max(ex - sx, 2), height: strip.height - 3)
            context.stroke(Path(roundedRect: box, cornerRadius: 4), with: .color(primary), lineWidth: 3)
        }
        drawHandle(context, .start, CGRect(x: rawStart - handle, y: strip.minY, width: handle, height: strip.height), size, primary)
        drawHandle(context, .end, CGRect(x: rawEnd, y: strip.minY, width: handle, height: strip.height), size, primary)
        let px = strip.minX + viewport.x(Double(model.playheadMs))
        if px > -2 && px < size.width + 2 {
            let alpha = model.playingRange || draggingHandle ? 1.0 : 0.5
            context.fill(Path(CGRect(x: px - 1, y: 0, width: 2, height: size.height)), with: .color(Color.white.opacity(alpha)))
        }
    }

    private func drawHandle(_ context: GraphicsContext, _ edge: FJTrimEdge, _ rect: CGRect, _ size: CGSize, _ primary: Color) {
        guard rect.maxX > 0, rect.minX < size.width else { return }
        let active = model.edge == edge
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: edge == .start ? 6 : 0, bottomLeadingRadius: edge == .start ? 6 : 0,
            bottomTrailingRadius: edge == .end ? 6 : 0, topTrailingRadius: edge == .end ? 6 : 0
        ).path(in: rect)
        context.fill(shape, with: .color(primary.opacity(active ? 1 : 0.8)))
        let grip = CGRect(x: rect.midX - 1.5, y: rect.midY - rect.height * 0.2, width: 3, height: rect.height * 0.4)
        context.fill(Path(roundedRect: grip, cornerRadius: 1.5), with: .color(.white))
    }

    private func drawThumbs(_ context: GraphicsContext, _ strip: CGRect) {
        let bucket = FJTrimThumbs.bucketMs(msPerPoint: viewport.msPerPoint)
        let count = thumbs.count(bucket)
        guard count > 0 else { return }
        let first = max(Int(viewport.startMs / bucket), 0)
        let last = min(Int(viewport.endMs / bucket), count - 1)
        guard first <= last else { return }
        let line = Color.black.opacity(0.35)
        for index in first...last {
            let x0 = strip.minX + viewport.x(Double(index) * bucket)
            let x1 = strip.minX + viewport.x(min(Double(index + 1) * bucket, viewport.durationMs))
            let rect = CGRect(x: x0, y: strip.minY, width: max(x1 - x0, 1), height: strip.height)
            if let image = thumbs.image(bucket: bucket, index: index), image.width > 0, image.height > 0 {
                var tile = context
                tile.clip(to: Path(rect))
                let iw = CGFloat(image.width)
                let ih = CGFloat(image.height)
                let scale = max(rect.width / iw, rect.height / ih)
                let w = iw * scale
                let h = ih * scale
                tile.draw(Image(decorative: image, scale: 1), in: CGRect(x: rect.midX - w / 2, y: rect.midY - h / 2, width: w, height: h))
            }
            context.fill(Path(CGRect(x: x1 - 0.5, y: strip.minY, width: 0.5, height: strip.height)), with: .color(line))
        }
    }

    private func drawWave(_ context: GraphicsContext, _ strip: CGRect) {
        let peaks = model.peaks
        guard !peaks.isEmpty else { return }
        let barMs = viewport.durationMs / Double(peaks.count)
        let barPoints = barMs / viewport.msPerPoint
        let group = max(Int((3.0 / barPoints).rounded(.up)), 1)
        let groupMs = barMs * Double(group)
        let groups = (peaks.count + group - 1) / group
        let first = max(Int(viewport.startMs / groupMs) - 1, 0)
        let last = min(Int(viewport.endMs / groupMs) + 1, groups - 1)
        guard first <= last else { return }
        let step = CGFloat(groupMs / viewport.msPerPoint)
        let mid = strip.midY
        let color = Color.white.opacity(0.75)
        var heights: [CGFloat] = []
        var xs: [CGFloat] = []
        for g in first...last {
            let lo = g * group
            let hi = min(lo + group, peaks.count)
            var peak: Float = 0
            for i in lo..<hi where peaks[i] > peak { peak = peaks[i] }
            heights.append(max(CGFloat(peak) * (strip.height - 8), 2))
            xs.append(strip.minX + viewport.x(Double(lo) * barMs))
        }
        if step < 8 {
            let bar = max(step * 0.6, 1)
            for (i, h) in heights.enumerated() {
                let rect = CGRect(x: xs[i] + (step - bar) / 2, y: mid - h / 2, width: bar, height: h)
                context.fill(Path(roundedRect: rect, cornerRadius: bar / 2), with: .color(color))
            }
        } else {
            var path = Path()
            for (i, h) in heights.enumerated() {
                let point = CGPoint(x: xs[i] + step / 2, y: mid - h / 2)
                if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
            }
            for (i, h) in heights.enumerated().reversed() {
                path.addLine(to: CGPoint(x: xs[i] + step / 2, y: mid + h / 2))
            }
            path.closeSubpath()
            context.fill(path, with: .color(color))
        }
    }

    private func hitHandle(_ x: CGFloat) -> FJTrimEdge? {
        let s = handle + viewport.x(Double(model.startMs)) - handle / 2
        let e = handle + viewport.x(Double(model.endMs)) + handle / 2
        let reach = handle / 2 + 14
        let ds = abs(x - s)
        let de = abs(x - e)
        if ds <= reach && de <= reach { return x <= (s + e) / 2 ? .start : .end }
        if ds <= reach { return .start }
        if de <= reach { return .end }
        return nil
    }

    private func applyHandle(_ edge: FJTrimEdge, grab: CGFloat, x: CGFloat) {
        let target = Int64(viewport.ms(at: x - handle - grab).rounded())
        if edge == .start {
            model.setStart(target)
        } else {
            model.setEnd(target)
        }
    }

    private func moveHandle(_ edge: FJTrimEdge, grab: CGFloat, x: CGFloat, total: CGFloat) {
        applyHandle(edge, grab: grab, x: x)
        let zone: CGFloat = 44
        var speed: Double = 0
        if x < zone {
            speed = -Double(min((zone - x) / zone, 1.5)) * 700
        } else if x > total - zone {
            speed = Double(min((x - (total - zone)) / zone, 1.5)) * 700
        }
        if speed == 0 {
            viewport.stopEdgeScroll()
        } else {
            viewport.edgeScroll(speed: speed) {
                applyHandle(edge, grab: grab, x: x)
            }
        }
    }

    private func seek(at x: CGFloat) {
        model.stopRange()
        let ms = min(max(viewport.ms(at: x - handle), 0), viewport.durationMs)
        model.seek(to: Int64(ms.rounded()))
    }

    private func dragGesture(total: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if pinch != nil { return }
                if touch == nil {
                    viewport.stopFling()
                    lastPan = 0
                    let moved = hypot(value.translation.width, value.translation.height)
                    if moved > 12 {
                        touch = .ignored
                    } else if let edge = hitHandle(value.startLocation.x) {
                        model.stopRange()
                        model.edge = edge
                        let edgeX = handle + viewport.x(Double(edge == .start ? model.startMs : model.endMs))
                        touch = .handle(edge, value.startLocation.x - edgeX)
                    } else {
                        touch = .pending
                    }
                }
                switch touch {
                case .handle(let edge, let grab)?:
                    if value.translation != .zero {
                        moveHandle(edge, grab: grab, x: value.location.x, total: total)
                    }
                case .pending?:
                    if abs(value.translation.width) > 6 {
                        touch = .pan
                        viewport.beginInteraction()
                        viewport.pan(by: value.translation.width)
                        lastPan = value.translation.width
                    }
                case .pan?:
                    viewport.pan(by: value.translation.width - lastPan)
                    lastPan = value.translation.width
                default:
                    break
                }
            }
            .onEnded { value in
                switch touch {
                case .handle(let edge, _)?:
                    viewport.stopEdgeScroll()
                    if value.translation == .zero {
                        model.seek(to: edge == .start ? model.startMs : model.endMs)
                    }
                case .pending?:
                    if pinch == nil && hypot(value.translation.width, value.translation.height) < 10 {
                        seek(at: value.location.x)
                    }
                case .pan?:
                    viewport.endInteraction()
                    viewport.fling(value.velocity.width)
                default:
                    break
                }
                touch = nil
            }
    }

    private func pinchGesture(total: CGFloat) -> some Gesture {
        MagnifyGesture()
            .onChanged { value in
                if draggingHandle { return }
                if pinch == nil {
                    viewport.beginInteraction()
                    if touch != nil { touch = .ignored }
                    let ax = min(max(value.startAnchor.x * total - handle, 0), viewport.width)
                    pinch = FJTrimPinch(scale: viewport.msPerPoint, anchorX: ax, anchorMs: viewport.ms(at: ax))
                }
                if let p = pinch {
                    viewport.zoom(from: p.scale, by: value.magnification, anchorX: p.anchorX, anchorMs: p.anchorMs)
                }
            }
            .onEnded { _ in
                pinch = nil
                viewport.endInteraction()
            }
    }
}

struct FJTrimOverview: View {
    @ObservedObject var model: FJTrimModel
    @ObservedObject var viewport: FJTrimViewport

    var body: some View {
        GeometryReader { geo in
            let width = max(geo.size.width, 1)
            Canvas { context, size in
                draw(context, size)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        viewport.beginInteraction()
                        viewport.center(on: Double(value.location.x / width) * viewport.durationMs)
                    }
                    .onEnded { _ in viewport.endInteraction() }
            )
        }
        .accessibilityHidden(true)
    }

    private func draw(_ context: GraphicsContext, _ size: CGSize) {
        let d = viewport.durationMs
        let px: (Double) -> CGFloat = { CGFloat(min(max($0 / d, 0), 1)) * size.width }
        let barHeight: CGFloat = 6
        let barY = (size.height - barHeight) / 2
        context.fill(Path(roundedRect: CGRect(x: 0, y: barY, width: size.width, height: barHeight), cornerRadius: 3),
                     with: .color(Color.white.opacity(0.15)))
        let sx = px(Double(model.startMs))
        let ex = px(Double(model.endMs))
        context.fill(Path(roundedRect: CGRect(x: sx, y: barY, width: max(ex - sx, 2), height: barHeight), cornerRadius: 3),
                     with: .color(FJTheme.color.primary.opacity(0.85)))
        let vw = min(max(px(viewport.endMs) - px(viewport.startMs), 6), size.width)
        let vx = min(px(viewport.startMs), size.width - vw)
        let frame = CGRect(x: vx, y: 1, width: vw, height: size.height - 2)
        context.fill(Path(roundedRect: frame, cornerRadius: 4), with: .color(Color.white.opacity(0.08)))
        context.stroke(Path(roundedRect: frame, cornerRadius: 4), with: .color(.white.opacity(0.9)), lineWidth: 1.5)
        let hx = px(Double(model.playheadMs))
        context.fill(Path(CGRect(x: hx - 1, y: 0, width: 2, height: size.height)), with: .color(.white))
    }
}
