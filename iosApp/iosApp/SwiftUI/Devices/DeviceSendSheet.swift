import SwiftUI
import SharedLogic

func fjDeviceSyncReason(_ reason: String, _ s: AppLocalization) -> String {
    switch reason {
    case "blocked": return s.pairRefusedBlocked
    case "busy": return s.pairRefusedBusy
    case "declined": return s.pairDeclined
    case "expired": return s.pairExpired
    case "no-answer": return s.deviceNoAnswer
    default:
        if reason.hasPrefix("wait:") {
            return s.pairRefusedWait.replacingOccurrences(of: "{seconds}", with: String(reason.dropFirst(5)))
        }
        return reason
    }
}

func fjDeviceEmoji(_ label: String) -> String {
    let l = label.lowercased()
    return ["android", "iphone", "ipad", "ios"].contains { l.contains($0) } ? "📱" : "💻"
}

struct DeviceSendSheet: View {
    @EnvironmentObject var store: FJStore
    let taskId: String

    @State private var selectedId: String?
    @State private var mode: SendMode = .linkOnly
    @State private var modeInitialized = false
    @State private var deleteAfter = false
    @State private var query = ""
    @State private var showManualIp = false
    @State private var manualIp = ""
    @FocusState private var ipFocused: Bool

    private var task: DownloadTask? { store.task(taskId) }
    private var canTransfer: Bool { task?.canTransferToDevice() == true }
    private var devices: [DiscoveredDevice] { store.state.discoveredDevices }
    private var sending: SendState? { store.state.deviceSending }
    private var streaming: Bool { sending is SendStateSending }
    private var isBusy: Bool { sending is SendStateSending || sending is SendStateWaiting }

    private func sorted(_ list: [DiscoveredDevice]) -> [DiscoveredDevice] {
        list.sorted {
            if $0.paired != $1.paired { return $0.paired }
            return $0.name.lowercased() < $1.name.lowercased()
        }
    }

    var body: some View {
        let s = store.strings
        VStack(spacing: 0) {
            header(s)
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let name = task?.fileName, !name.isEmpty {
                        Text(store.host.decodeUnicodeText(input: name))
                            .font(FJTheme.font.body.weight(.semibold))
                            .foregroundStyle(FJTheme.color.onSurface)
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                    if devices.isEmpty {
                        searching(s)
                    } else {
                        deviceList(s)
                        options(s)
                    }
                    Divider().overlay(FJTheme.color.outlineVariant)
                    manualConnect(s)
                    status(s)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
            .scrollDismissesKeyboard(.interactively)
            footer(s)
        }
        .background(FJTheme.color.surface.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(streaming)
        .onAppear {
            if !modeInitialized {
                modeInitialized = true
                mode = canTransfer ? .transferAndContinue : .linkOnly
            }
        }
        .onChange(of: devices.map(\.id), initial: true) { _, ids in
            if selectedId == nil || !ids.contains(selectedId!) {
                selectedId = sorted(devices).first?.id
            }
        }
    }

    private func header(_ s: AppLocalization) -> some View {
        HStack {
            Image(systemName: "laptopcomputer.and.iphone")
                .foregroundStyle(FJTheme.color.primary)
            Text(s.sysDeviceSendTitle)
                .font(FJTheme.font.title)
                .foregroundStyle(FJTheme.color.onSurface)
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.top, 22)
        .padding(.bottom, 4)
    }

    private func searching(_ s: AppLocalization) -> some View {
        VStack(spacing: 10) {
            ZStack {
                Circle().fill(FJTheme.color.primary.opacity(0.12)).frame(width: 56, height: 56)
                ProgressView().tint(FJTheme.color.primary)
            }
            Text(s.sysDeviceSearching)
                .font(FJTheme.font.callout)
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 18)
    }

    @ViewBuilder
    private func deviceList(_ s: AppLocalization) -> some View {
        if devices.count > 5 {
            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(FJTheme.color.onSurfaceVariant)
                TextField(s.sysDeviceSearch, text: $query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
            .padding(10)
            .background(FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: 10))
        }
        let q = query.trimmingCharacters(in: .whitespaces)
        let shown = sorted(devices.filter { q.isEmpty || $0.name.localizedCaseInsensitiveContains(q) })
        let paired = shown.filter(\.paired)
        let others = shown.filter { !$0.paired }
        VStack(alignment: .leading, spacing: 0) {
            if shown.isEmpty {
                Text(s.sysDeviceNoMatch)
                    .font(FJTheme.font.caption)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .padding(14)
            }
            if !paired.isEmpty { group(s.sysDevicePairedLabel, paired, s) }
            if !others.isEmpty { group(s.sysDeviceOthers, others, s) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(FJTheme.color.surfaceVariant.opacity(0.55), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(FJTheme.color.outline.opacity(0.5)))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func group(_ title: String, _ list: [DiscoveredDevice], _ s: AppLocalization) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                Text(title.uppercased())
                    .font(FJTheme.font.small.weight(.semibold))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                if !list.first!.paired && isSearchingMore {
                    ProgressView().controlSize(.mini)
                }
            }
            .padding(.leading, 12)
            .padding(.top, 8)
            .padding(.bottom, 2)
            ForEach(Array(list.enumerated()), id: \.element.id) { index, device in
                if index > 0 {
                    Divider().overlay(FJTheme.color.outline.opacity(0.25)).padding(.leading, 52)
                }
                deviceRow(device, s)
            }
        }
    }

    private var isSearchingMore: Bool { !isBusy }

    private func deviceRow(_ device: DiscoveredDevice, _ s: AppLocalization) -> some View {
        let selected = device.id == selectedId
        let parts = device.name.components(separatedBy: " · ")
        let hostName = parts.first ?? device.name
        let plat = parts.count > 1 && !parts[1].isEmpty ? parts.dropFirst().joined(separator: " · ") : device.platform
        let showPlat = !plat.isEmpty && plat.lowercased() != hostName.lowercased()
        return Button {
            selectedId = device.id
        } label: {
            HStack(spacing: 10) {
                Text(fjDeviceEmoji(device.name + " " + device.platform))
                    .font(.system(size: 15))
                    .frame(width: 30, height: 30)
                    .background(FJTheme.color.primary.opacity(selected ? 0.22 : 0.12),
                                in: RoundedRectangle(cornerRadius: 9))
                HStack(spacing: 0) {
                    Text(hostName)
                        .font(FJTheme.font.body.weight(.semibold))
                        .foregroundStyle(FJTheme.color.onSurface)
                        .lineLimit(1)
                    if showPlat {
                        Text(" · \(plat)")
                            .font(FJTheme.font.caption)
                            .foregroundStyle(FJTheme.color.onSurfaceVariant)
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 4)
                if device.paired {
                    Text(s.sysDevicePairedLabel)
                        .font(FJTheme.font.small)
                        .foregroundStyle(FJTheme.color.primary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(FJTheme.color.primary.opacity(0.16), in: Capsule())
                }
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .font(.system(size: 20))
                    .foregroundStyle(selected ? FJTheme.color.primary : FJTheme.color.onSurfaceVariant)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(selected ? FJTheme.color.primary.opacity(0.14) : .clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func options(_ s: AppLocalization) -> some View {
        Divider().overlay(FJTheme.color.outlineVariant)
        modeRow(s.sysDeviceModeLink, s.sysDeviceModeLinkDesc, .linkOnly)
        if canTransfer {
            modeRow(s.sysDeviceModeTransfer, s.sysDeviceModeTransferDesc, .transferAndContinue)
        }
        Button { deleteAfter.toggle() } label: {
            HStack(spacing: 10) {
                Image(systemName: deleteAfter ? "checkmark.square.fill" : "square")
                    .font(.system(size: 20))
                    .foregroundStyle(deleteAfter ? FJTheme.color.primary : FJTheme.color.onSurfaceVariant)
                Text(s.sysDeviceDeleteAfter)
                    .font(FJTheme.font.body.weight(.medium))
                    .foregroundStyle(FJTheme.color.onSurface)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func modeRow(_ title: String, _ desc: String, _ value: SendMode) -> some View {
        let selected = mode == value
        return Button { mode = value } label: {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .font(.system(size: 20))
                    .foregroundStyle(selected ? FJTheme.color.primary : FJTheme.color.onSurfaceVariant)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(FJTheme.font.body.weight(.medium))
                        .foregroundStyle(FJTheme.color.onSurface)
                    Text(desc)
                        .font(FJTheme.font.caption)
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                }
                .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func manualConnect(_ s: AppLocalization) -> some View {
        Button {
            withAnimation(.easeInOut(duration: 0.15)) { showManualIp.toggle() }
            if showManualIp { ipFocused = true }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: showManualIp ? "chevron.down" : "chevron.forward")
                    .font(.system(size: 11, weight: .semibold))
                Text(s.sysDeviceManualConnect).font(FJTheme.font.caption)
            }
            .foregroundStyle(FJTheme.color.primary)
        }
        .buttonStyle(.plain)
        if showManualIp {
            HStack(spacing: 8) {
                TextField("192.168.1.5", text: $manualIp)
                    .keyboardType(.numbersAndPunctuation)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($ipFocused)
                    .padding(10)
                    .background(FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10)
                        .stroke(ipFocused ? FJTheme.color.primary : FJTheme.color.outlineVariant))
                    .environment(\.layoutDirection, .leftToRight)
                let ip = manualIp.trimmingCharacters(in: .whitespaces)
                FJDialogButton(title: s.sysDeviceConnect, kind: .outlined, enabled: !ip.isEmpty && !isBusy) {
                    ipFocused = false
                    store.send(DashboardUiEventOnManualConnectClicked(ip: ip))
                }
            }
        }
    }

    @ViewBuilder
    private func status(_ s: AppLocalization) -> some View {
        if sending is SendStateWaiting {
            HStack(spacing: 10) {
                ProgressView().controlSize(.small)
                Text(s.sysDeviceWaitingAccept)
                    .font(FJTheme.font.caption)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
        } else if let st = sending as? SendStateSending {
            VStack(alignment: .leading, spacing: 6) {
                ProgressView(value: Double(min(max(st.fraction, 0), 1)))
                    .tint(FJTheme.color.primary)
                HStack {
                    Text(s.sysDeviceSending).font(FJTheme.font.caption)
                    Spacer()
                    Text("\(Int(min(max(st.fraction, 0), 1) * 100))%")
                        .font(FJTheme.font.caption.monospacedDigit())
                }
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
        } else if let f = sending as? SendStateFailed {
            Text(s.sysDeviceSendFailed + ": " + fjDeviceSyncReason(f.reason, s))
                .font(FJTheme.font.caption)
                .foregroundStyle(FJTheme.color.error)
        } else if let d = sending as? SendStateDone {
            Text(s.sysDeviceSent.replacingOccurrences(of: "{name}", with: d.deviceName))
                .font(FJTheme.font.caption)
                .foregroundStyle(FJTheme.color.primary)
        }
    }

    private func footer(_ s: AppLocalization) -> some View {
        HStack(spacing: 10) {
            FJDialogButton(title: s.cancel, kind: .outlined, enabled: !streaming, fill: true) {
                store.send(DashboardUiEventOnDismissShareSheet.shared)
            }
            FJDialogButton(title: s.sysDeviceSend, kind: .filled, enabled: selectedId != nil && !isBusy, fill: true) {
                guard let id = selectedId else { return }
                ipFocused = false
                store.send(DashboardUiEventOnSendToDeviceClicked(
                    taskId: taskId, deviceId: id, mode: mode, deleteAfterSuccess: deleteAfter))
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(FJTheme.color.surface)
        .overlay(alignment: .top) { Divider().overlay(FJTheme.color.outlineVariant) }
    }
}
