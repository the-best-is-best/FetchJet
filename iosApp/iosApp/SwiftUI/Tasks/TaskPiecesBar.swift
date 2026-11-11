import SwiftUI
import SharedLogic

private struct PieceInfo: Identifiable {
    let index: Int32
    let start: Int64
    let end: Int64
    let downloaded: Int64
    let completed: Bool
    let active: Bool
    let speed: Int64
    let note: String?
    var id: Int32 { index }
    var fill: Double { Double(downloaded) / Double(max(1, end - start + 1)) }
}

struct TaskPiecesBar: View {
    @EnvironmentObject var store: FJStore
    let task: DownloadTask

    private static let activeB = FJTheme.hex(0x7DF9FF)

    var body: some View {
        let running = TaskUi.isRunning(task)
        let pieces: [PieceInfo] = task.chunks.sorted { $0.startByte < $1.startByte }.map { c in
            PieceInfo(index: c.chunkIndex, start: c.startByte, end: c.endByte, downloaded: c.downloadedBytes,
                      completed: c.isCompleted, active: running && c.isActive,
                      speed: running ? c.speedBytesPerSec : 0, note: running ? c.note : nil)
        }
        let total = pieces.map { $0.end + 1 }.max() ?? 0
        if total > 0 {
            content(pieces, total: total)
        }
    }

    @ViewBuilder
    private func content(_ pieces: [PieceInfo], total: Int64) -> some View {
        let s = store.strings
        let many = pieces.count > 40
        let active = pieces.filter { $0.active }
        let stalled = pieces.filter { !$0.active && !$0.completed && $0.note != nil }
        let finished = pieces.filter { $0.completed }.count
        let waiting = FJTheme.color.statusPaused
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                Text(s.homePiecesLabel)
                    .font(.system(size: 11))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                pill(many ? s.homeSegmentsCount(count: Int32(pieces.count)) : s.homePiecesCount(count: Int32(pieces.count)),
                     FJTheme.color.onSurfaceVariant, FJTheme.color.surfaceVariant.opacity(0.5))
                if !active.isEmpty {
                    pill(s.homeActiveCount(count: Int32(active.count)), Self.activeB, Self.activeB.opacity(0.14), dot: true)
                }
                if !stalled.isEmpty {
                    pill(s.homeWaitingCount(count: Int32(stalled.count)), waiting, waiting.opacity(0.14))
                }
                if finished > 0 {
                    pill(s.homeDoneCount(count: Int32(finished)), FJTheme.color.statusCompleted, FJTheme.color.statusCompleted.opacity(0.14))
                }
            }
            bar(pieces, total: total, animate: !active.isEmpty)
                .frame(height: 22)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .padding(.top, 8)
            let shown = (active + stalled).sorted { $0.index < $1.index }
            if many {
                Text(s.homeSegmentsProgress(
                    finished: Int32(finished), total: Int32(pieces.count),
                    downloading: active.isEmpty ? nil : active.map { "#\($0.index + 1)" }.joined(separator: ", ")))
                    .font(.system(size: 11))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .lineLimit(2)
                    .padding(.top, 6)
            } else if !shown.isEmpty {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 6) {
                    ForEach(shown) { p in
                        connectionCard(p, tint: p.active ? Self.activeB : waiting)
                    }
                }
                .padding(.top, 10)
            }
        }
    }

    private func pill(_ text: String, _ fg: Color, _ bg: Color, dot: Bool = false) -> some View {
        HStack(spacing: 5) {
            if dot { Circle().fill(fg).frame(width: 6, height: 6) }
            Text(text).font(.system(size: 10, weight: .semibold)).foregroundStyle(fg).lineLimit(1)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(bg, in: Capsule())
    }

    private func bar(_ pieces: [PieceInfo], total: Int64, animate: Bool) -> some View {
        let done = FJTheme.color.primary
        let activeA = FJTheme.color.primary
        let activeB = Self.activeB
        let waiting = FJTheme.color.statusPaused
        let track = FJTheme.color.surfaceVariant.opacity(0.55)
        let gapColor = FJTheme.color.surface
        return TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !animate)) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate
            let sheen = CGFloat(-0.4 + 1.8 * (t.truncatingRemainder(dividingBy: 1.6) / 1.6))
            Canvas { ctx, size in
                let w = size.width, h = size.height
                let perPiece = w / CGFloat(pieces.count)
                let gap: CGFloat = perPiece > 60 ? 2 : (perPiece > 6 ? 1 : 0)
                for p in pieces {
                    let x0 = w * CGFloat(Double(p.start) / Double(total)) + gap / 2
                    let x1 = w * CGFloat(Double(p.end + 1) / Double(total)) - gap / 2
                    let segW = max(1, x1 - x0)
                    let fill = min(max(p.fill, 0), 1)
                    let full = Path(roundedRect: CGRect(x: x0, y: 0, width: segW, height: h), cornerRadius: 1.5)
                    ctx.fill(full, with: .color(track))
                    if fill > 0 {
                        let fw = segW * CGFloat(fill)
                        let part = Path(roundedRect: CGRect(x: x0, y: 0, width: fw, height: h), cornerRadius: 1.5)
                        if p.completed {
                            ctx.fill(full, with: .color(done))
                        } else if p.active {
                            ctx.fill(part, with: .linearGradient(Gradient(colors: [activeA, activeB]),
                                                                 startPoint: CGPoint(x: x0, y: 0), endPoint: CGPoint(x: x0 + fw, y: 0)))
                            let sx = x0 + fw * sheen
                            ctx.fill(part, with: .linearGradient(
                                Gradient(colors: [.clear, .white.opacity(0.45), .clear]),
                                startPoint: CGPoint(x: sx - fw * 0.18, y: 0), endPoint: CGPoint(x: sx + fw * 0.18, y: 0)))
                        } else {
                            ctx.fill(part, with: .color(p.note != nil ? waiting.opacity(0.85) : done.opacity(0.6)))
                        }
                    }
                    if fill == 0 && p.note != nil {
                        ctx.fill(Path(CGRect(x: x0, y: h - 3, width: segW, height: 3)), with: .color(waiting.opacity(0.5)))
                    }
                }
                if gap > 0 {
                    for p in pieces.dropFirst() {
                        let x = w * CGFloat(Double(p.start) / Double(total))
                        ctx.fill(Path(CGRect(x: x - gap / 2, y: 0, width: gap, height: h)), with: .color(gapColor))
                    }
                }
            }
        }
    }

    private func connectionCard(_ p: PieceInfo, tint: Color) -> some View {
        let frac = min(max(p.fill, 0), 1)
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Text("#\(p.index + 1)")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(tint)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 1)
                    .background(tint.opacity(0.18), in: RoundedRectangle(cornerRadius: 5))
                Text(p.active ? store.host.formatSpeed(bytesPerSec: p.speed, unit: store.state.speedDisplayUnit)
                     : store.strings.homeRetryingNote(note: p.note))
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(p.active ? FJTheme.color.onSurface : tint)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text("\(Int(frac * 100))%")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(FJTheme.color.onSurface.opacity(0.85))
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 2).fill(FJTheme.color.surfaceVariant)
                    RoundedRectangle(cornerRadius: 2).fill(tint).frame(width: geo.size.width * CGFloat(frac))
                }
            }
            .frame(height: 4)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(FJTheme.color.surfaceVariant.opacity(0.35), in: RoundedRectangle(cornerRadius: 10))
    }
}
