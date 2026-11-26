import PDFKit
import SharedLogic
import SwiftUI

struct FJPdfTarget: Identifiable {
    let id = UUID()
    let path: String
    let title: String
    let task: DownloadTask?
    let hidden: Bool
}

final class FJPdfModel: ObservableObject {
    let document: PDFDocument?
    let startPage: Int
    @Published var page = 0
    @Published var jump: Int?
    @Published var fitTick = 0
    let count: Int
    let locked: Bool
    let failed: Bool
    private let task: DownloadTask?
    private let hidden: Bool

    init(target: FJPdfTarget) {
        task = target.task
        hidden = target.hidden
        let doc = PDFDocument(url: URL(fileURLWithPath: target.path))
        document = doc
        failed = doc == nil || (doc?.isLocked == false && (doc?.pageCount ?? 0) == 0)
        locked = doc?.isLocked == true
        count = doc?.pageCount ?? 0
        var first = 0
        if let task = target.task, !target.hidden {
            first = Int(PdfReader.shared.clampPage(page: PdfReader.shared.lastPage(task: task), pageCount: Int32(count)))
        }
        startPage = first
        page = first
    }

    func pageChanged(_ index: Int) {
        guard index != page else { return }
        page = index
        if let task, !hidden {
            PdfReader.shared.rememberPage(task: task, page: Int32(index))
        }
    }

    var label: String { PdfReader.shared.pageLabel(page: Int32(page), pageCount: Int32(count)) }
}

struct FJPdfView: UIViewRepresentable {
    let document: PDFDocument
    let startPage: Int
    @ObservedObject var model: FJPdfModel

    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        view.displaysPageBreaks = true
        view.backgroundColor = .black
        view.autoScales = true
        view.document = document
        context.coordinator.view = view
        context.coordinator.observer = NotificationCenter.default.addObserver(
            forName: .PDFViewPageChanged, object: view, queue: .main
        ) { [weak view] _ in
            guard let view, let page = view.currentPage, let doc = view.document else { return }
            model.pageChanged(doc.index(for: page))
        }
        DispatchQueue.main.async {
            if startPage > 0, let page = document.page(at: startPage) { view.go(to: page) }
        }
        return view
    }

    func updateUIView(_ view: PDFView, context: Context) {
        if let target = model.jump {
            if let page = document.page(at: target) { view.go(to: page) }
            DispatchQueue.main.async { model.jump = nil }
        }
        if context.coordinator.fitTick != model.fitTick {
            context.coordinator.fitTick = model.fitTick
            let current = view.currentPage
            view.autoScales = true
            view.scaleFactor = view.scaleFactorForSizeToFit
            if let current { view.go(to: current) }
        }
    }

    static func dismantleUIView(_ view: PDFView, coordinator: Coordinator) {
        if let observer = coordinator.observer { NotificationCenter.default.removeObserver(observer) }
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator {
        weak var view: PDFView?
        var observer: NSObjectProtocol?
        var fitTick = 0
    }
}

struct FJPdfReaderScreen: View {
    let target: FJPdfTarget
    let onClose: () -> Void
    @EnvironmentObject private var store: FJStore
    @StateObject private var model: FJPdfModel
    @Environment(\.scenePhase) private var phase
    @State private var asking = false
    @State private var pageText = ""

    init(target: FJPdfTarget, onClose: @escaping () -> Void) {
        self.target = target
        self.onClose = onClose
        _model = StateObject(wrappedValue: FJPdfModel(target: target))
    }

    var body: some View {
        let s = store.strings
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Button(action: onClose) {
                    Image(systemName: "xmark").font(.title3.weight(.semibold)).frame(width: 44, height: 44)
                }
                .accessibilityLabel(s.cancel)
                Text(target.title)
                    .lineLimit(1)
                    .truncationMode(.middle)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let document = model.document, !model.locked, document.pageCount > 0 {
                    Button {
                        pageText = ""
                        asking = true
                    } label: {
                        Text("\u{2066}\(model.label)\u{2069}")
                            .font(.subheadline.monospacedDigit())
                            .padding(.horizontal, 10)
                            .frame(height: 32)
                            .background(Color.white.opacity(0.14), in: Capsule())
                    }
                    .accessibilityLabel(s.pdfGoToPage)
                    Button { model.fitTick += 1 } label: {
                        Image(systemName: "arrow.left.and.right").font(.body.weight(.semibold)).frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(s.pdfFitWidth)
                }
            }
            .padding(.horizontal, 8)
            .foregroundStyle(.white)
            .background(Color(white: 0.08))

            if let document = model.document, !model.locked, !model.failed {
                FJPdfView(document: document, startPage: model.startPage, model: model)
                    .environment(\.layoutDirection, .leftToRight)
                    .ignoresSafeArea(edges: .bottom)
            } else {
                VStack(spacing: 16) {
                    Image(systemName: model.locked ? "lock.doc" : "exclamationmark.triangle")
                        .font(.system(size: 44, weight: .semibold))
                        .foregroundStyle(FJTheme.color.primary)
                    Text(model.locked ? s.pdfPasswordProtected : s.pdfOpenFailed)
                        .font(.body)
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                    if model.locked {
                        Button {
                            let path = target.path
                            onClose()
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                                _ = store.host.openDownloadedFile(fullPath: path)
                            }
                        } label: {
                            Text(s.playerModeOff)
                                .font(.body.weight(.semibold))
                                .foregroundStyle(FJTheme.color.onPrimary)
                                .padding(.horizontal, 20)
                                .frame(height: 44)
                                .background(FJTheme.color.primary, in: Capsule())
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Color.black.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .alert(s.pdfGoToPage, isPresented: $asking) {
            TextField("\u{2066}1 – \(model.count)\u{2069}", text: $pageText)
                .keyboardType(.numberPad)
            Button(s.cancel, role: .cancel) {}
            Button("OK") {
                if let index = PdfReader.shared.parsePage(text: pageText, pageCount: Int32(model.count)) {
                    model.jump = Int(truncating: index)
                }
            }
        } message: {
            Text("\u{2066}\(model.label)\u{2069}")
        }
        .overlay {
            if target.hidden && phase != .active { Color.black.ignoresSafeArea() }
        }
        .onChange(of: store.state.hidden.unlocked) { _, unlocked in
            if target.hidden && !unlocked { onClose() }
        }
        .onChange(of: store.state.hidden.covered) { _, covered in
            if target.hidden && covered { onClose() }
        }
        .onDisappear {
            if target.hidden { store.send(DashboardUiEventOnHiddenClosePlayer.shared) }
        }
    }
}
