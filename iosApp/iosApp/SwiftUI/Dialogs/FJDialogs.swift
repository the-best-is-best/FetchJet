import SwiftUI
import SharedLogic

private extension Text {
    func fjBody() -> some View {
        font(FJTheme.font.body).foregroundStyle(FJTheme.color.onSurface)
    }
    func fjMuted(_ font: Font = FJTheme.font.caption) -> some View {
        self.font(font).foregroundStyle(FJTheme.color.onSurfaceVariant)
    }
}

struct FJPairingDialog: View {
    @EnvironmentObject var store: FJStore
    @EnvironmentObject var model: FJDialogsModel
    let pairing: PairingState

    var body: some View {
        let s = store.strings
        let offer = pairing as? PairingStateOffer
        FJDialogFrame(title: offer != nil ? s.sysDeviceOfferTitle : s.sysDevicePairTitle) {
            VStack(spacing: 12) {
                if pairing is PairingStateConnecting {
                    ProgressView().controlSize(.large).tint(FJTheme.color.primary)
                    Text(s.sysDevicePairConnecting).fjBody()
                } else if let c = pairing as? PairingStateConfirm {
                    Text(s.sysDevicePairBody).fjBody().multilineTextAlignment(.center)
                    Text(c.code)
                        .font(.system(size: 34, weight: .bold, design: .monospaced))
                        .kerning(8)
                        .foregroundStyle(FJTheme.color.primary)
                        .environment(\.layoutDirection, .leftToRight)
                        .textSelection(.enabled)
                    Text(c.peerName).fjMuted()
                } else if let f = pairing as? PairingStateFailed {
                    Text(s.sysDevicePairFailed + ": " + fjDeviceSyncReason(f.reason, s))
                        .font(FJTheme.font.body)
                        .foregroundStyle(FJTheme.color.error)
                } else if let o = offer {
                    Text(store.host.decodeUnicodeText(input: o.fileName))
                        .font(FJTheme.font.headline)
                        .foregroundStyle(FJTheme.color.onSurface)
                        .multilineTextAlignment(.center)
                        .lineLimit(3)
                    if o.size > 0 {
                        Text(store.host.ltr(text: store.host.formatBytes(bytes: o.size))).fjMuted()
                    }
                    Text(s.sysDeviceOfferFrom.replacingOccurrences(of: "{device}", with: o.peerName))
                        .fjMuted(FJTheme.font.body)
                        .multilineTextAlignment(.center)
                    TimelineView(.periodic(from: .now, by: 1)) { ctx in
                        let left = max(0, 60 - ctx.date.timeIntervalSince(model.offerStart))
                        ProgressView(value: left / 60).tint(FJTheme.color.primary.opacity(0.6))
                    }
                }
            }
            .frame(maxWidth: .infinity)
        } buttons: {
            if pairing is PairingStateConfirm {
                FJDialogButton(title: s.blockDevice, kind: .dangerText) {
                    store.send(DashboardUiEventOnBlockPairingClicked.shared)
                }
            }
            FJDialogButton(title: offer != nil ? s.sysDeviceOfferReject : s.cancel, kind: .outlined) {
                store.send(DashboardUiEventOnCancelPairingClicked.shared)
            }
            if pairing is PairingStateConfirm {
                FJDialogButton(title: s.sysDevicePairConfirm, kind: .filled) {
                    store.send(DashboardUiEventOnConfirmPairingClicked.shared)
                }
            }
            if offer != nil {
                FJDialogButton(title: s.sysDeviceOfferAccept, kind: .filled) {
                    store.send(DashboardUiEventOnConfirmPairingClicked.shared)
                }
            }
        }
    }
}

struct FJReceiveDialog: View {
    @EnvironmentObject var store: FJStore
    let receiving: ReceiveState

    var body: some View {
        let s = store.strings
        FJDialogFrame(title: s.sysDeviceReceivingTitle) {
            VStack(spacing: 12) {
                Text(store.host.decodeUnicodeText(input: receiving.fileName))
                    .font(FJTheme.font.headline)
                    .foregroundStyle(FJTheme.color.onSurface)
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
                Text(s.sysDeviceOfferFrom.replacingOccurrences(of: "{device}", with: receiving.deviceName))
                    .fjMuted(FJTheme.font.body)
                if let r = receiving as? ReceiveStateReceiving {
                    if r.fraction < 0 {
                        ProgressView().progressViewStyle(.linear).tint(FJTheme.color.primary)
                    } else {
                        let f = Double(min(max(r.fraction, 0), 1))
                        ProgressView(value: f).tint(FJTheme.color.primary)
                        Text("\(Int(f * 100))%").fjMuted()
                    }
                } else if let f = receiving as? ReceiveStateFailed {
                    Text(s.sysDeviceReceiveFailed + ": " + fjDeviceSyncReason(f.reason, s))
                        .font(FJTheme.font.body)
                        .foregroundStyle(FJTheme.color.error)
                }
            }
            .frame(maxWidth: .infinity)
        } buttons: {
            FJDialogButton(title: s.close, kind: .outlined) {
                store.send(DashboardUiEventOnDismissReceiveClicked.shared)
            }
        }
    }
}

struct FJRemoveDialog: View {
    @EnvironmentObject var store: FJStore
    @State private var deleteFile = false

    var body: some View {
        let s = store.strings
        let count = store.state.removeConfirmationTaskId != nil ? 1 : store.state.removeConfirmationTaskIds.count
        FJDialogFrame(title: s.removeDialogTitle(count: Int32(count))) {
            VStack(alignment: .leading, spacing: 12) {
                Text(s.removeDialogMessage(count: Int32(count))).fjBody()
                FJDialogCheckRow(title: s.removeDialogAlsoDeleteFiles, checked: $deleteFile)
            }
        } buttons: {
            FJDialogButton(title: s.cancel, kind: .outlined) {
                store.send(DashboardUiEventOnDismissRemoveDialog.shared)
            }
            FJDialogButton(title: s.remove, kind: .danger) {
                store.send(DashboardUiEventOnConfirmRemoveClicked(deleteFile: deleteFile))
            }
        }
    }
}

struct FJMissingFileDialog: View {
    @EnvironmentObject var store: FJStore
    let task: DownloadTask

    var body: some View {
        let s = store.strings
        FJDialogFrame(title: s.fileMissingTitle) {
            Text(s.fileMissingMessage(name: store.host.decodeUnicodeText(input: task.fileName))).fjBody()
        } buttons: {
            FJDialogButton(title: s.close, kind: .outlined) { store.missingFileTaskId = nil }
            FJDialogButton(title: s.detailsRemoveFromList, kind: .danger) {
                let id = task.id
                store.missingFileTaskId = nil
                store.send(DashboardUiEventOnRemoveClicked(taskId: id))
                store.send(DashboardUiEventOnConfirmRemoveClicked(deleteFile: false))
            }
        }
    }
}

struct FJClipboardOfferDialog: View {
    @EnvironmentObject var store: FJStore
    let url: String

    var body: some View {
        let s = store.strings
        let shownUrl = store.host.displayUrl(url: url)
        let name: String = {
            if url.lowercased().hasPrefix("magnet:") { return s.homeMagnetLink }
            let last = shownUrl.components(separatedBy: "?").first?.components(separatedBy: "/").last ?? ""
            return last.isEmpty ? shownUrl : (last.removingPercentEncoding ?? last)
        }()
        FJDialogFrame(title: s.homeClipboardOfferTitle) {
            VStack(alignment: .leading, spacing: 6) {
                Text(name)
                    .font(FJTheme.font.body.weight(.semibold))
                    .foregroundStyle(FJTheme.color.onSurface)
                    .lineLimit(2)
                Text(shownUrl).fjMuted().lineLimit(2).truncationMode(.middle)
                Text(s.homeClipboardOfferHint).fjMuted().padding(.top, 2)
            }
        } buttons: {
            FJDialogButton(title: s.homeNotNow, kind: .outlined) {
                store.send(DashboardUiEventOnClipboardOfferDismissed.shared)
            }
            FJDialogButton(title: s.homeDownload, kind: .filled) {
                store.send(DashboardUiEventOnClipboardOfferAccepted.shared)
            }
        }
    }
}

struct FJBrowserFallbackDialog: View {
    @EnvironmentObject var store: FJStore
    let url: String

    var body: some View {
        let s = store.strings
        let last = url.components(separatedBy: "/").last?.components(separatedBy: "?").first ?? ""
        FJDialogFrame(title: s.homeBrowserFallbackTitle) {
            VStack(alignment: .leading, spacing: 8) {
                Text(s.homeBrowserFallbackText).fjBody()
                Text(last.isEmpty ? url : last).fjMuted().lineLimit(2)
            }
        } buttons: {
            FJDialogButton(title: s.homeNoDiscard, kind: .outlined) {
                store.send(DashboardUiEventOnDismissBrowserFallback.shared)
            }
            FJDialogButton(title: s.homeDownloadInBrowser, kind: .filled) {
                store.send(DashboardUiEventOnConfirmBrowserFallback.shared)
            }
        }
    }
}

struct FJDuplicateDialog: View {
    @EnvironmentObject var store: FJStore
    let task: DownloadTask

    var body: some View {
        let s = store.strings
        let statusColor: Color = {
            switch task.status {
            case .completed: return FJTheme.color.statusCompleted
            case .downloading, .connecting: return FJTheme.color.statusConnecting
            case .paused: return FJTheme.color.statusPaused
            case .error: return FJTheme.color.statusError
            default: return FJTheme.color.onSurfaceVariant
            }
        }()
        FJDialogFrame(title: s.duplicateDialogTitle, icon: "🔄", subtitle: s.duplicateDialogSubtitle, stackButtons: true) {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(task.fileName.isEmpty ? store.host.displayUrl(url: task.url) : store.host.decodeUnicodeText(input: task.fileName))
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(FJTheme.color.onSurface)
                        .lineLimit(2)
                    HStack {
                        Text(s.duplicateDialogStatus(status: task.status.name))
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(statusColor)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(statusColor.opacity(0.16), in: RoundedRectangle(cornerRadius: 6))
                        Spacer()
                        if task.progress > 0 {
                            Text("\(Int(task.progress * 100))%")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(FJTheme.color.statusConnecting)
                        }
                    }
                    if task.progress > 0 && task.status != .completed {
                        ProgressView(value: Double(min(max(task.progress, 0), 1))).tint(FJTheme.color.primary)
                    }
                }
                .padding(14)
                .background(FJTheme.color.surfaceVariant.opacity(0.35), in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(FJTheme.color.outlineVariant.opacity(0.6)))
                Text(s.duplicateDialogQuestion)
                    .font(.system(size: 12))
                    .foregroundStyle(FJTheme.color.onSurface)
            }
        } buttons: {
            FJDialogButton(title: "▶️ " + s.duplicateDialogResumeExisting, kind: .filled, fill: true) {
                store.send(DashboardUiEventOnResumeExistingDuplicateClicked.shared)
            }
            FJDialogButton(title: "🔄 " + s.duplicateDialogRedownload, kind: .outlined, fill: true) {
                store.send(DashboardUiEventOnRestartDuplicateClicked.shared)
            }
            FJDialogButton(title: s.cancel, kind: .text, fill: true) {
                store.send(DashboardUiEventOnDismissDuplicateDialog.shared)
            }
        }
    }
}

struct FJFileConflictDialog: View {
    @EnvironmentObject var store: FJStore
    let data: FileConflictData

    var body: some View {
        let s = store.strings
        FJDialogFrame(title: s.overwriteDialogTitle, icon: "⚠️", subtitle: s.overwriteDialogSubtitle, stackButtons: true) {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 8) {
                    row(s.overwriteDialogFileNameLabel, data.fileName, FJTheme.color.onSurfaceVariant, FJTheme.color.onSurface)
                    row(s.overwriteDialogFolderLabel, data.destinationPath, FJTheme.color.onSurfaceVariant, FJTheme.color.onSurface)
                    row(s.overwriteDialogNewCopyLabel, data.suggestedNewName, FJTheme.color.primary, FJTheme.color.primary)
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(FJTheme.color.surfaceVariant.opacity(0.35), in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(FJTheme.color.outlineVariant.opacity(0.6)))
                Text(s.overwriteDialogQuestion)
                    .font(.system(size: 12))
                    .foregroundStyle(FJTheme.color.onSurface)
            }
        } buttons: {
            FJDialogButton(title: "📝 " + s.overwriteDialogSaveAsCopy(name: data.suggestedNewName), kind: .filled, fill: true) {
                store.send(DashboardUiEventOnSaveAsCopyClicked(newFileName: data.suggestedNewName))
            }
            FJDialogButton(title: "⚠️ " + s.overwriteDialogOverwriteExisting, kind: .dangerText, fill: true) {
                store.send(DashboardUiEventOnConfirmOverwriteClicked.shared)
            }
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.error))
            FJDialogButton(title: s.cancel, kind: .text, fill: true) {
                store.send(DashboardUiEventOnDismissFileConflictDialog.shared)
            }
        }
    }

    private func row(_ label: String, _ value: String, _ labelColor: Color, _ valueColor: Color) -> some View {
        (Text(label).font(.system(size: 12, weight: .bold)).foregroundColor(labelColor)
            + Text(" ")
            + Text(value).font(.system(size: 12, weight: .semibold)).foregroundColor(valueColor))
            .lineLimit(2)
    }
}

struct FJUpdateDialog: View {
    @EnvironmentObject var store: FJStore
    @Environment(\.openURL) private var openURL
    let state: UpdateState
    let release: AppRelease

    var body: some View {
        let s = store.strings
        let asset = PlatformUpdater.shared.pickAsset(assets: release.assets)
        let downloading = state.downloadProgress >= 0
        FJDialogFrame(title: "🎉 " + s.updateNewVersionAvailable) {
            VStack(alignment: .leading, spacing: 8) {
                Text(s.updateVersionLine(version: release.version, current: state.currentVersion))
                    .font(FJTheme.font.body.weight(.semibold))
                    .foregroundStyle(FJTheme.color.primary)
                Text(s.updateWhatsNew)
                    .font(FJTheme.font.callout.weight(.semibold))
                    .foregroundStyle(FJTheme.color.onSurface)
                    .padding(.top, 4)
                ScrollView {
                    let notes = Self.plainNotes(release.notesForDisplay)
                    Text(notes.isEmpty ? s.updateNoReleaseNotes : notes)
                        .font(FJTheme.font.caption)
                        .foregroundStyle(FJTheme.color.onSurface)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                }
                .frame(maxHeight: 260)
                .fixedSize(horizontal: false, vertical: true)
                .background(FJTheme.color.surfaceVariant.opacity(0.4), in: RoundedRectangle(cornerRadius: 10))
                if !state.canInstallInApp {
                    Text(Self.installHint(asset?.name, s)).fjMuted().padding(.top, 4)
                }
                if state.messageIsInstallError, let m = state.message {
                    Text(m).font(FJTheme.font.caption).foregroundStyle(FJTheme.color.error)
                }
            }
        } buttons: {
            if !downloading {
                FJDialogButton(title: s.updateSkipThisVersion, kind: .text) { UpdateManager.shared.skip() }
            }
            FJDialogButton(title: s.updateLater, kind: .outlined) { UpdateManager.shared.dismiss() }
            FJDialogButton(
                title: asset.map { s.updateDownloadWithSize(size: $0.size > 0 ? "(\(store.host.formatBytes(bytes: $0.size)))" : "") }
                    ?? s.updateOpenReleasePage,
                kind: .filled
            ) {
                if let url = URL(string: asset?.url ?? release.pageUrl) { openURL(url) }
                UpdateManager.shared.dismiss()
            }
        }
    }

    static func installHint(_ name: String?, _ s: AppLocalization) -> String {
        guard let n = name?.lowercased() else { return s.installHintReleasePage }
        if n.hasSuffix(".apk") { return s.installHintApk }
        if n.hasSuffix(".ipa") { return s.installHintIpa }
        if n.hasSuffix(".dmg") { return s.installHintDmg }
        if n.hasSuffix(".deb") { return s.installHintDeb }
        return s.installHintOther
    }

    static func plainNotes(_ md: String) -> String {
        let lines = md.components(separatedBy: "\n").map { raw -> String in
            var l = raw.replacingOccurrences(of: "\\s+$", with: "", options: .regularExpression)
            l = l.replacingOccurrences(of: "^#{1,6}\\s*", with: "", options: .regularExpression)
            l = l.replacingOccurrences(of: "^\\s*[-*]\\s+", with: "• ", options: .regularExpression)
            l = l.replacingOccurrences(of: "**", with: "")
            l = l.replacingOccurrences(of: "\\[([^\\]]+)\\]\\((https?://[^)]+)\\)", with: "$1", options: .regularExpression)
            l = l.replacingOccurrences(of: "<!--.*?-->", with: "", options: .regularExpression)
            return l
        }
        return lines.joined(separator: "\n")
            .replacingOccurrences(of: "\n{3,}", with: "\n\n", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

struct FJWhenDoneDialog: View {
    @EnvironmentObject var store: FJStore
    @EnvironmentObject var model: FJDialogsModel

    var body: some View {
        let s = store.strings
        let secs = model.whenDoneSeconds ?? 0
        let text: String = {
            if model.whenDonePending == .sleep { return s.whenDoneSleepIn(seconds: Int32(secs)) }
            if model.whenDonePending == .shutDown { return s.whenDoneShutDownIn(seconds: Int32(secs)) }
            return s.whenDoneExitIn(seconds: Int32(secs))
        }()
        FJDialogFrame(title: s.whenDoneTitle) {
            VStack(alignment: .leading, spacing: 12) {
                Text(text).fjBody()
                ProgressView(value: Double(secs) / Double(max(WhenDone.shared.COUNTDOWN_SECONDS, 1)))
                    .tint(FJTheme.color.primary)
            }
        } buttons: {
            FJDialogButton(title: s.whenDoneNow, kind: .outlined) { WhenDone.shared.runNow() }
            FJDialogButton(title: s.cancel, kind: .filled) { WhenDone.shared.cancel() }
        }
    }
}

struct FJWhenDoneErrorDialog: View {
    @EnvironmentObject var store: FJStore
    @EnvironmentObject var model: FJDialogsModel

    var body: some View {
        let s = store.strings
        FJDialogFrame(title: s.whenDoneGroup) {
            Text(s.whenDoneFailed(message: model.whenDoneError ?? "")).fjBody()
        } buttons: {
            FJDialogButton(title: s.ok, kind: .filled) { WhenDone.shared.dismissError() }
        }
    }
}
