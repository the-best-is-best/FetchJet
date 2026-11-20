import SwiftUI
import SharedLogic

extension AppLocalization {
    func grabGroupLabel(_ folder: String) -> String {
        switch folder {
        case "Video": return grabGroupVideo
        case "Music": return grabGroupMusic
        case "Archives": return grabGroupArchives
        case "Documents": return grabGroupDocuments
        case "Programs": return grabGroupPrograms
        case "Images": return grabGroupImages
        default: return grabGroupOther
        }
    }

    func grabDepthLabel(_ depth: Int32) -> String {
        switch depth {
        case 2: return grabDepth2
        case 3: return grabDepth3
        default: return grabDepth1
        }
    }

    func grabDepthShortLabel(_ depth: Int32) -> String {
        switch depth {
        case 2: return grabDepthShort2
        case 3: return grabDepthShort3
        default: return grabDepthShort1
        }
    }
}

struct AddGrabNote: View {
    let note: String
    var body: some View {
        if !note.isEmpty {
            Text(note)
                .font(.fj(11))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

struct AddGrabCard: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        let strings = store.strings
        let grab = store.state.siteGrab
        AddCard {
            Text(strings.grabFindFiles)
                .font(.fj(13, .bold))
                .foregroundStyle(FJTheme.color.onSurface)
            if grab.running {
                HStack(spacing: 8) {
                    ProgressView().controlSize(.small)
                    Text(strings.grabReading.replacingOccurrences(of: "%1", with: String(grab.pagesRead)))
                        .font(.fj(12))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    Spacer(minLength: 0)
                    Button(strings.grabStop) { store.send(DashboardUiEventOnGrabCancelClicked.shared) }
                        .font(.fj(12, .bold))
                        .foregroundStyle(FJTheme.color.error)
                }
            } else {
                Text(strings.grabHow)
                    .font(.fj(11))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                let depths: [Int32] = [1, 2, 3]
                AddSegmentedRow(
                    labels: depths.map { strings.grabDepthShortLabel($0) },
                    selectedIndex: depths.firstIndex(of: grab.depth) ?? 0
                ) { index in
                    store.send(DashboardUiEventOnGrabDepthChanged(depth: depths[index]))
                }
                Text(strings.grabDepthLabel(grab.depth))
                    .font(.fj(11))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                AddOutlineButton(label: strings.grabFindFiles) { store.send(DashboardUiEventOnGrabPageClicked.shared) }
                AddGrabNote(note: grab.note)
            }
        }
    }
}

struct AddGrabGroupRow: View {
    @EnvironmentObject var store: FJStore

    var body: some View {
        let groups = store.state.grabGroups
        let strings = store.strings
        if !groups.isEmpty {
            AddChipRow {
                ForEach(groups, id: \.folder) { g in
                    AddChoiceChip(label: "\(strings.grabGroupLabel(g.folder)) \(g.count)", selected: g.selected) {
                        store.send(DashboardUiEventOnGrabGroupToggled(folder: g.folder, selected: !g.selected))
                    }
                }
            }
        }
    }
}
