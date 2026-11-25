import AVFoundation
import SharedLogic
import SwiftUI
import UniformTypeIdentifiers

struct FJSubtitleTrack: Identifiable {
    let id: String
    let label: String
    let option: AVMediaSelectionOption
}

final class FJSubtitles: ObservableObject {
    @Published private(set) var tracks: [FJSubtitleTrack] = []
    @Published private(set) var files: [SubtitleFile] = []
    @Published private(set) var choice: SubtitleChoice = SubtitleChoiceOff.shared
    @Published private(set) var text = ""
    @Published var failed = false

    private weak var player: AVPlayer?
    private var item: AVPlayerItem?
    private var group: AVMediaSelectionGroup?
    private var entry: PlayerItem?
    private var cues: [SubtitleCue] = []
    private var observer: Any?
    private var token = 0

    static let fileTypes: [UTType] = ["srt", "vtt", "ass", "ssa"].compactMap { UTType(filenameExtension: $0) } + [.plainText]

    deinit {
        if let observer { player?.removeTimeObserver(observer) }
    }

    func attach(player: AVPlayer, item: AVPlayerItem, entry: PlayerItem, path: String) {
        self.player = player
        self.item = item
        self.entry = entry
        token += 1
        let current = token
        player.appliesMediaSelectionCriteriaAutomatically = false
        tracks = []
        group = nil
        cues = []
        text = ""
        failed = false
        let language = PlayerSubtitles.shared.appLanguage()
        files = entry.hidden ? [] : PlayerSubtitles.shared.sideFiles(videoPath: path, preferredLanguage: language)
        if observer == nil {
            observer = player.addPeriodicTimeObserver(
                forInterval: CMTime(value: 1, timescale: 10), queue: .main
            ) { [weak self] time in
                guard let self, !self.cues.isEmpty, time.seconds.isFinite else { return }
                let line = PlayerSubtitles.shared.cueAt(cues: self.cues, positionMs: Int64(time.seconds * 1000))
                if line != self.text { self.text = line }
            }
        }
        let start = PlayerSubtitles.shared.initialChoice(
            taskId: entry.taskId, sideFiles: files, preferredLanguage: language, remember: !entry.hidden
        ) ?? SubtitleChoiceOff.shared
        Task { @MainActor in
            let legible = try? await item.asset.loadMediaSelectionGroup(for: .legible)
            guard current == self.token else { return }
            self.group = legible
            self.tracks = (legible?.options ?? []).enumerated().filter { _, option in
                !option.hasMediaCharacteristic(.containsOnlyForcedSubtitles)
            }.map { index, option in
                FJSubtitleTrack(id: "\(index)", label: option.displayName.trimmingCharacters(in: .whitespaces), option: option)
            }
            self.apply(start, save: false)
        }
    }

    func pick(_ next: SubtitleChoice) {
        apply(next, save: true)
    }

    func pickFile(_ url: URL) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let folder = URL(fileURLWithPath: NSHomeDirectory()).appendingPathComponent("Library/Application Support/picked_subtitles", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let copy = folder.appendingPathComponent(url.lastPathComponent)
        try? FileManager.default.removeItem(at: copy)
        guard (try? FileManager.default.copyItem(at: url, to: copy)) != nil else {
            failed = true
            return
        }
        apply(SubtitleChoiceFile(path: copy.path), save: true)
    }

    func isChosen(_ other: SubtitleChoice) -> Bool { choice.encode() == other.encode() }

    var pickedFileName: String? {
        guard let file = choice as? SubtitleChoiceFile, !files.contains(where: { $0.path == file.path }) else { return nil }
        return (file.path as NSString).lastPathComponent
    }

    private func apply(_ next: SubtitleChoice, save: Bool) {
        guard let item else { return }
        var applied = next
        if let track = next as? SubtitleChoiceTrack {
            if let group, let option = tracks.first(where: { $0.id == track.id })?.option {
                item.select(option, in: group)
            } else {
                applied = SubtitleChoiceOff.shared
                if let group { item.select(nil, in: group) }
            }
            cues = []
        } else {
            if let group { item.select(nil, in: group) }
            if let file = next as? SubtitleChoiceFile {
                let read = PlayerSubtitles.shared.readCues(path: file.path)
                if read.isEmpty {
                    failed = true
                    applied = SubtitleChoiceOff.shared
                    cues = []
                } else {
                    cues = read
                }
            } else {
                cues = []
            }
        }
        text = ""
        if let time = player?.currentTime().seconds, time.isFinite, !cues.isEmpty {
            text = PlayerSubtitles.shared.cueAt(cues: cues, positionMs: Int64(time * 1000))
        }
        choice = applied
        if save, let entry, !entry.hidden {
            PlayerSubtitles.shared.remember(taskId: entry.taskId, choice: applied)
        }
    }
}

struct FJSubtitleOverlay: View {
    @ObservedObject var subtitles: FJSubtitles
    let raised: Bool

    var body: some View {
        VStack {
            Spacer()
            if !subtitles.text.isEmpty {
                Text(subtitles.text)
                    .font(.system(size: 19, weight: .semibold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .shadow(color: .black, radius: 1, x: 1, y: 1)
                    .shadow(color: .black, radius: 2)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Color.black.opacity(0.35), in: RoundedRectangle(cornerRadius: 6))
                    .padding(.horizontal, 24)
                    .padding(.bottom, raised ? 150 : 36)
            }
        }
        .allowsHitTesting(false)
        .animation(.easeOut(duration: 0.15), value: raised)
    }
}

struct FJSubtitleMenu: View {
    @EnvironmentObject var store: FJStore
    @ObservedObject var subtitles: FJSubtitles
    @Binding var picking: Bool

    var body: some View {
        let s = store.strings
        Menu {
            Button {
                subtitles.pick(SubtitleChoiceOff.shared)
            } label: {
                mark(s.playerSubtitlesOff, subtitles.isChosen(SubtitleChoiceOff.shared))
            }
            if !subtitles.tracks.isEmpty {
                Section(s.playerSubtitlesInVideo) {
                    ForEach(subtitles.tracks) { track in
                        let choice = SubtitleChoiceTrack(id: track.id)
                        let title = track.label.isEmpty ? "\(s.playerSubtitlesTrack) \((Int(track.id) ?? 0) + 1)" : track.label
                        Button { subtitles.pick(choice) } label: { mark(title, subtitles.isChosen(choice)) }
                    }
                }
            }
            if !subtitles.files.isEmpty || subtitles.pickedFileName != nil {
                Section(s.playerSubtitlesFiles) {
                    ForEach(subtitles.files, id: \.path) { file in
                        let choice = SubtitleChoiceFile(path: file.path)
                        Button { subtitles.pick(choice) } label: { mark(file.label, subtitles.isChosen(choice)) }
                    }
                    if let picked = subtitles.pickedFileName {
                        Button {} label: { mark(picked, true) }
                    }
                }
            }
            Button {
                picking = true
            } label: {
                Label(s.playerSubtitlesOpenFile, systemImage: "doc.badge.plus")
            }
        } label: {
            Image(systemName: subtitles.isChosen(SubtitleChoiceOff.shared) ? "captions.bubble" : "captions.bubble.fill")
                .font(.title3)
                .foregroundStyle(.white)
                .frame(width: 64, height: 44, alignment: .trailing)
        }
        .accessibilityLabel(s.playerSubtitles)
    }

    @ViewBuilder
    private func mark(_ title: String, _ on: Bool) -> some View {
        if on {
            Label(title, systemImage: "checkmark")
        } else {
            Text(title)
        }
    }
}
