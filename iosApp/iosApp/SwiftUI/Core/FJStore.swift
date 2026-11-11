import SwiftUI
import SharedLogic

struct FJToast: Identifiable, Equatable {
    let id = UUID()
    let text: String
}

@MainActor
final class FJStore: ObservableObject {
    let host: IosAppHost
    @Published private(set) var state: DashboardUiState
    @Published private(set) var language: String
    @Published private(set) var isRtl: Bool
    @Published var toast: FJToast?
    @Published var selectedTaskId: String?
    @Published var missingFileTaskId: String?
    private(set) var lastEffect: DashboardUiEffect?
    var onEffect: ((DashboardUiEffect) -> Void)?
    private var watchers: [IosCancelable] = []

    init() {
        let host = IosAppHost()
        self.host = host
        self.state = host.state
        self.language = host.language
        self.isRtl = host.isRtl
        watchers.append(host.watch(
            onState: { [weak self] s in self?.state = s },
            onEffect: { [weak self] e in self?.handle(e) }
        ))
        watchers.append(host.watchLanguage { [weak self] lang, rtl in
            self?.language = lang
            self?.isRtl = rtl.boolValue
        })
    }

    var strings: AppLocalization { host.strings() }

    func send(_ event: DashboardUiEvent) {
        host.send(event: event)
    }

    func showToast(_ text: String) {
        toast = FJToast(text: text)
    }

    func task(_ id: String?) -> DownloadTask? {
        guard let id else { return nil }
        return state.allTasks.first { $0.id == id }
    }

    func openFile(_ task: DownloadTask) {
        if !host.openTaskFile(task: task) {
            missingFileTaskId = task.id
        }
    }

    private func handle(_ effect: DashboardUiEffect) {
        lastEffect = effect
        if let t = effect as? DashboardUiEffectShowToast {
            showToast(t.text)
        }
        onEffect?(effect)
    }
}
