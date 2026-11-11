import SwiftUI
import SharedLogic

struct AudioCompressButton: View {
    @EnvironmentObject var store: FJStore
    let task: DownloadTask

    var body: some View {
        let count = store.state.audioCopyCounts[task.id]?.intValue ?? 0
        let title = store.strings.audioCompressTitle + (count > 0 ? " (\(count))" : "")
        TaskDetailButton(text: title, primary: false) {
            store.send(DashboardUiEventOnAudioCompressClicked(taskId: task.id))
        }
        .sheet(isPresented: Binding(
            get: { store.state.audioCompress?.taskId == task.id },
            set: { if !$0 { store.send(DashboardUiEventOnDismissAudioCompress.shared) } })) {
            if let page = store.state.audioCompress {
                AudioCompressSheet(page: page).environmentObject(store)
            }
        }
    }
}

struct AudioCompressSheet: View {
    @EnvironmentObject var store: FJStore
    let page: AudioCompressUi

    private var settings: AudioCompressSettings { page.settings }
    private var simple: Bool { settings.mode == .simple }

    private var hasEstimate: Bool {
        !page.reading && page.estimatedBytes > 0 && page.originalBytes > 0
    }

    private var savingPercent: Int {
        guard hasEstimate else { return 0 }
        return max(0, Int(100 - page.estimatedBytes * 100 / page.originalBytes))
    }

    private var sizeFraction: CGFloat {
        guard hasEstimate else { return 0 }
        let value = CGFloat(Double(page.estimatedBytes) / Double(page.originalBytes))
        return min(max(value, 0.02), 1)
    }

    private var levelText: String {
        let list = AudioCompress.shared.LEVELS
        let level = Int(settings.level)
        guard !list.isEmpty else { return "\(level)" }
        let index = min(max(level - 1, 0), list.count - 1)
        return "\(level) · \(list[index].bitrateKbps) kbps"
    }

    private func change(mode: AudioCompressMode? = nil, level: Int32? = nil, bitrate: Int32? = nil,
                        sampleRate: Int32? = nil, channels: Int32? = nil) {
        let next = AudioCompressSettings(
            mode: mode ?? settings.mode,
            level: level ?? settings.level,
            bitrateKbps: bitrate ?? settings.bitrateKbps,
            sampleRate: sampleRate ?? settings.sampleRate,
            channels: channels ?? settings.channels)
        store.send(DashboardUiEventOnAudioCompressSettingsChanged(settings: next))
    }

    private func rateText(_ rate: Int32, _ s: AppLocalization) -> String {
        rate == 0 ? s.audioCompressAuto : "\(rate)Hz"
    }

    private func channelsText(_ value: Int32, _ s: AppLocalization) -> String {
        switch value {
        case 1: return s.audioCompressMono
        case 2: return s.audioCompressStereo
        default: return s.audioCompressAuto
        }
    }

    var body: some View {
        let s = store.strings
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header(s)
                sizeCard(s)
                Picker(s.audioCompressMode, selection: Binding(
                    get: { simple },
                    set: { change(mode: $0 ? .simple : .advanced) })) {
                    Text(s.audioCompressSimple).tag(true)
                    Text(s.audioCompressAdvanced).tag(false)
                }
                .pickerStyle(.segmented)
                .disabled(page.running)
                if simple {
                    simpleSection(s)
                } else {
                    advancedSection(s)
                }
                if page.running {
                    runningSection(s)
                }
                if page.failed {
                    Text(s.audioCompressFailed)
                        .font(FJTheme.font.callout)
                        .foregroundStyle(FJTheme.color.error)
                }
                if !page.copies.isEmpty {
                    copiesSection(s)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 22)
            .padding(.bottom, 12)
        }
        .safeAreaInset(edge: .bottom) {
            startButton(s)
        }
        .background(FJTheme.color.surface.ignoresSafeArea())
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(page.running)
    }

    private func header(_ s: AppLocalization) -> some View {
        HStack(alignment: .top, spacing: 8) {
            VStack(alignment: .leading, spacing: 4) {
                Text(s.audioCompressTitle)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(FJTheme.color.onSurface)
                Text(store.host.decodeUnicodeText(input: page.fileName))
                    .font(FJTheme.font.callout)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }
            Spacer(minLength: 0)
            Button {
                store.send(DashboardUiEventOnDismissAudioCompress.shared)
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(s.cancel)
            .disabled(page.running)
            .opacity(page.running ? 0.45 : 1)
        }
    }

    private func sizeCard(_ s: AppLocalization) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(store.host.formatBytes(bytes: page.originalBytes))
                    .font(FJTheme.font.callout)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .lineLimit(1)
                    .accessibilityLabel(s.audioCompressOriginalSize + " " + store.host.formatBytes(bytes: page.originalBytes))
                Image(systemName: "arrow.forward")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .accessibilityHidden(true)
                if page.reading {
                    Text(s.audioCompressReading)
                        .font(FJTheme.font.callout)
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                } else {
                    Text(page.estimatedBytes > 0 ? store.host.formatBytes(bytes: page.estimatedBytes) : "—")
                        .font(.system(size: 28, weight: .bold).monospacedDigit())
                        .foregroundStyle(FJTheme.color.primary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                        .accessibilityLabel(s.audioCompressNewSize + " " + store.host.formatBytes(bytes: page.estimatedBytes))
                }
                Spacer(minLength: 4)
                if hasEstimate {
                    Text("−\(savingPercent)%")
                        .font(FJTheme.font.callout.weight(.semibold).monospacedDigit())
                        .foregroundStyle(FJTheme.color.statusCompleted)
                        .lineLimit(1)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(FJTheme.color.statusCompleted.opacity(0.16), in: Capsule())
                }
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(FJTheme.color.outlineVariant)
                    Capsule()
                        .fill(FJTheme.color.primary)
                        .frame(width: geo.size.width * sizeFraction)
                }
            }
            .frame(height: 8)
            .accessibilityHidden(true)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: FJTheme.corner))
        .overlay(RoundedRectangle(cornerRadius: FJTheme.corner).stroke(FJTheme.color.outlineVariant, lineWidth: 1))
    }

    private func simpleSection(_ s: AppLocalization) -> some View {
        let levels = Int(AudioCompress.shared.LEVELS.count)
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text(s.audioCompressLevel)
                    .font(FJTheme.font.callout)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                Spacer(minLength: 4)
                Text(levelText)
                    .font(FJTheme.font.callout.weight(.semibold).monospacedDigit())
                    .foregroundStyle(FJTheme.color.onSurface)
                    .lineLimit(1)
            }
            Slider(value: Binding(
                get: { Double(settings.level) },
                set: { change(level: Int32($0.rounded())) }),
                   in: 1...Double(max(levels, 2)), step: 1)
                .tint(FJTheme.color.primary)
                .disabled(page.running)
                .accessibilityLabel(s.audioCompressLevel)
                .accessibilityValue(levelText)
            HStack(spacing: 8) {
                Text(s.audioCompressHigherQuality)
                Spacer(minLength: 4)
                Text(s.audioCompressSmallerFile)
            }
            .font(FJTheme.font.caption)
            .foregroundStyle(FJTheme.color.onSurfaceVariant)
        }
    }

    private func advancedSection(_ s: AppLocalization) -> some View {
        HStack(alignment: .top, spacing: 8) {
            pickField(s.audioCompressBitrate, "\(settings.bitrateKbps)k",
                      AudioCompress.shared.BITRATES.map { $0.int32Value },
                      label: { "\($0)k" }) { change(bitrate: $0) }
            pickField(s.audioCompressSampleRate, rateText(settings.sampleRate, s),
                      AudioCompress.shared.SAMPLE_RATES.map { $0.int32Value },
                      label: { rateText($0, s) }) { change(sampleRate: $0) }
            pickField(s.audioCompressChannels, channelsText(settings.channels, s),
                      AudioCompress.shared.CHANNELS.map { $0.int32Value },
                      label: { channelsText($0, s) }) { change(channels: $0) }
        }
    }

    private func pickField(_ title: String, _ current: String, _ values: [Int32],
                           label: @escaping (Int32) -> String, pick: @escaping (Int32) -> Void) -> some View {
        Menu {
            ForEach(values, id: \.self) { value in
                Button(label(value)) { pick(value) }
            }
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(FJTheme.font.caption)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                HStack(spacing: 4) {
                    Text(current)
                        .font(FJTheme.font.body.weight(.semibold))
                        .foregroundStyle(FJTheme.color.onSurface)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                    Spacer(minLength: 2)
                    Image(systemName: "chevron.down")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                        .accessibilityHidden(true)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .background(FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.outlineVariant, lineWidth: 1))
            .contentShape(RoundedRectangle(cornerRadius: 10))
        }
        .frame(maxWidth: .infinity)
        .disabled(page.running)
    }

    private func runningSection(_ s: AppLocalization) -> some View {
        let fraction = Double(min(max(page.progress, 0), 1))
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Text(s.audioCompressRunning)
                Spacer(minLength: 4)
                Text("\(Int(fraction * 100))%").monospacedDigit()
            }
            .font(FJTheme.font.caption)
            .foregroundStyle(FJTheme.color.onSurfaceVariant)
            ProgressView(value: fraction)
                .tint(FJTheme.color.primary)
        }
    }

    private func copiesSection(_ s: AppLocalization) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(s.audioCompressCopies)
                .font(FJTheme.font.callout.weight(.semibold))
                .foregroundStyle(FJTheme.color.onSurface)
            ForEach(page.copies, id: \.path) { copy in
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(copy.name)
                            .font(FJTheme.font.body)
                            .foregroundStyle(FJTheme.color.onSurface)
                            .lineLimit(1)
                            .truncationMode(.middle)
                        Text(store.host.formatBytes(bytes: copy.bytes))
                            .font(FJTheme.font.caption)
                            .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    }
                    Spacer(minLength: 4)
                    Button {
                        store.send(DashboardUiEventOnAudioCompressDeleteCopy(path: copy.path))
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundStyle(FJTheme.color.error)
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(s.audioCompressDelete)
                    .disabled(page.running)
                    .opacity(page.running ? 0.45 : 1)
                }
                .padding(.leading, 12)
                .padding(.trailing, 4)
                .padding(.vertical, 4)
                .background(FJTheme.color.surfaceVariant.opacity(0.55), in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(FJTheme.color.outlineVariant, lineWidth: 1))
            }
        }
    }

    private func startButton(_ s: AppLocalization) -> some View {
        let enabled = !(page.running || page.reading)
        return Button {
            store.send(DashboardUiEventOnAudioCompressStartClicked.shared)
        } label: {
            Text(s.audioCompressStart)
                .font(FJTheme.font.headline)
                .foregroundStyle(FJTheme.color.onPrimary)
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(FJTheme.color.primary, in: RoundedRectangle(cornerRadius: 12))
                .contentShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.45)
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(FJTheme.color.surface)
    }
}
