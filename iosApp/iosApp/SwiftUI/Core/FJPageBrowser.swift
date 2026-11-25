import SwiftUI
import WebKit
import SharedLogic

enum FJBrowserSession {
    static func clear(site: String) {
        let store = WKWebsiteDataStore.default()
        store.fetchDataRecords(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes()) { records in
            let matching = records.filter {
                let name = $0.displayName.lowercased()
                return name == site || site.hasSuffix("." + name) || name.hasSuffix("." + site)
            }
            if matching.isEmpty { return }
            store.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), for: matching) {}
        }
    }
}

final class FJPageBrowser: ObservableObject {
    static let shared = FJPageBrowser()
    @Published var page: URL?

    func handle(_ url: URL) -> Bool {
        guard url.scheme?.lowercased() == "fetchjet", url.host?.lowercased() == "browse" else { return false }
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        guard let raw = items.first(where: { $0.name == "url" })?.value,
              let target = URL(string: raw),
              let scheme = target.scheme?.lowercased(), scheme == "https" || scheme == "http" else { return true }
        page = target
        return true
    }
}

private final class FJWeakScriptHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?

    init(_ target: WKScriptMessageHandler) {
        self.target = target
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        target?.userContentController(userContentController, didReceive: message)
    }
}

final class FJPageBrowserModel: NSObject, ObservableObject, WKScriptMessageHandler, WKNavigationDelegate, WKUIDelegate {
    @Published var mediaUrl: String = ""
    @Published var title: String = ""
    @Published var loading = true
    @Published var address: String = ""
    @Published var canBack = false
    @Published var canForward = false
    var onSent: (() -> Void)?
    var loginTyped = false
    let webView: WKWebView

    static func address(from typed: String) -> URL? {
        let text = typed.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { return nil }
        if text.contains("://") { return URL(string: text) }
        let looksLikeAddress = !text.contains(" ") && (text.contains(".") || text.hasPrefix("localhost"))
        if looksLikeAddress { return URL(string: "https://" + text) }
        var query = URLComponents(string: "https://www.google.com/search")
        query?.queryItems = [URLQueryItem(name: "q", value: text)]
        return query?.url
    }

    private func refreshNavigation() {
        address = webView.url?.absoluteString ?? address
        canBack = webView.canGoBack
        canForward = webView.canGoForward
    }

    private static let finder = """
    (function () {
      var last = "";
      function look() {
        var found = "";
        var list = document.querySelectorAll("video, audio");
        for (var i = 0; i < list.length; i++) {
          var s = list[i].currentSrc || list[i].src || "";
          if (/^https?:/i.test(s) && !/\\.m3u8|\\.mpd/i.test(s)) { found = s; break; }
        }
        if (found !== last) {
          last = found;
          try { window.webkit.messageHandlers.fjMedia.postMessage(found); } catch (e) {}
        }
      }
      setInterval(look, 700);
      look();
      if (!window.__fjLoginWatch) {
        window.__fjLoginWatch = true;
        document.addEventListener("input", function (e) {
          var t = e.target;
          if (t && t.type === "password" && t.value) { try { window.webkit.messageHandlers.fjLogin.postMessage("typed"); } catch (x) {} }
        }, true);
      }
    })();
    """

    override init() {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.websiteDataStore = .default()
        webView = WKWebView(frame: .zero, configuration: config)
        super.init()
        let handler = FJWeakScriptHandler(self)
        config.userContentController.add(handler, name: "fjMedia")
        config.userContentController.add(handler, name: "fjLogin")
        config.userContentController.addUserScript(
            WKUserScript(source: Self.finder, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
        )
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
    }

    deinit {
        webView.configuration.userContentController.removeAllScriptMessageHandlers()
    }

    func shutDown() {
        webView.stopLoading()
        webView.pauseAllMediaPlayback()
        webView.configuration.userContentController.removeAllScriptMessageHandlers()
        webView.navigationDelegate = nil
        webView.uiDelegate = nil
        webView.loadHTMLString("", baseURL: nil)
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.frameInfo.isMainFrame else { return }
        if message.name == "fjLogin" {
            loginTyped = true
            return
        }
        mediaUrl = (message.body as? String) ?? ""
    }

    private func rememberSession() {
        guard let page = webView.url, let host = page.host else { return }
        webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { cookies in
            let header = cookies.filter { Self.matches($0, page) }
                .map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
            if header.isEmpty { return }
            DispatchQueue.main.async {
                NetworkSettings.shared.saveCookie(site: SiteLogin.companion.cleanSite(text: host), cookie: header)
                self.loginTyped = false
            }
        }
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        loading = true
        mediaUrl = ""
        refreshNavigation()
    }

    func webView(_ webView: WKWebView, didCommit navigation: WKNavigation!) {
        refreshNavigation()
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        loading = false
        title = webView.title ?? ""
        refreshNavigation()
        if loginTyped {
            webView.evaluateJavaScript("document.querySelector('input[type=password]') === null") { [weak self] gone, _ in
                if (gone as? Bool) == true { self?.rememberSession() }
            }
        }
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationResponse: WKNavigationResponse,
        decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void
    ) {
        let response = navigationResponse.response as? HTTPURLResponse
        let disposition = response?.value(forHTTPHeaderField: "Content-Disposition")?.lowercased() ?? ""
        let succeeded = (200...299).contains(response?.statusCode ?? 200)
        let isFile = navigationResponse.isForMainFrame && succeeded && (!navigationResponse.canShowMIMEType || disposition.contains("attachment"))
        guard isFile, let link = navigationResponse.response.url?.absoluteString else {
            decisionHandler(.allow)
            return
        }
        decisionHandler(.cancel)
        let name = navigationResponse.response.suggestedFilename ?? ""
        send(link: link, fileName: name) { [weak self] ok in
            if ok { self?.onSent?() }
        }
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        let scheme = navigationAction.request.url?.scheme?.lowercased() ?? ""
        decisionHandler(scheme == "http" || scheme == "https" || scheme == "about" ? .allow : .cancel)
    }

    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if navigationAction.targetFrame == nil { webView.load(navigationAction.request) }
        return nil
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        loading = false
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        loading = false
    }

    static func matches(_ cookie: HTTPCookie, _ url: URL) -> Bool {
        guard let host = url.host?.lowercased() else { return false }
        let domain = cookie.domain.lowercased()
        let bare = domain.hasPrefix(".") ? String(domain.dropFirst()) : domain
        guard host == bare || host.hasSuffix("." + bare) else { return false }
        if cookie.isSecure && url.scheme?.lowercased() != "https" { return false }
        let path = url.path.isEmpty ? "/" : url.path
        return path.hasPrefix(cookie.path)
    }

    func download(done: @escaping (Bool) -> Void) {
        let name = URL(string: mediaUrl)?.lastPathComponent ?? ""
        send(link: mediaUrl, fileName: name.contains(".") ? name : "", done: done)
    }

    func sendPage(done: @escaping (Bool) -> Void) {
        guard let page = webView.url?.absoluteString else { return done(false) }
        send(link: page, fileName: "", done: done)
    }

    func send(link: String, fileName: String, done: @escaping (Bool) -> Void) {
        guard let target = URL(string: link), let page = webView.url else { return done(false) }
        let store = webView.configuration.websiteDataStore.httpCookieStore
        webView.evaluateJavaScript("navigator.userAgent") { [weak self] agent, _ in
            store.getAllCookies { cookies in
                guard let self else { return done(false) }
                let header = cookies.filter { Self.matches($0, target) }
                    .map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
                let body: [String: Any] = [
                    "url": link,
                    "cookie": header,
                    "referer": page.absoluteString,
                    "userAgent": (agent as? String) ?? "",
                    "fileName": fileName
                ]
                guard let data = try? JSONSerialization.data(withJSONObject: body),
                      let json = String(data: data, encoding: .utf8),
                      let packed = json.addingPercentEncoding(withAllowedCharacters: .alphanumerics) else { return done(false) }
                done(IosFetchJetLinks.shared.handle(link: "fetchjet://add?json=" + packed))
            }
        }
    }
}

private struct FJPageWebView: UIViewRepresentable {
    let model: FJPageBrowserModel
    let url: URL

    func makeUIView(context: Context) -> WKWebView {
        model.webView.scrollView.contentInset.bottom = 80
        model.webView.scrollView.verticalScrollIndicatorInsets.bottom = 80
        model.webView.load(URLRequest(url: url))
        return model.webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

struct FJPageBrowserSheet: View {
    @EnvironmentObject var store: FJStore
    @StateObject private var model = FJPageBrowserModel()
    @State private var typed = ""
    let url: URL
    let close: () -> Void

    var body: some View {
        let s = store.strings
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                TextField(s.browserAddressHint, text: $typed)
                    .font(.fj(14))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(.URL)
                    .submitLabel(.go)
                    .onSubmit {
                        if let target = FJPageBrowserModel.address(from: typed) { model.webView.load(URLRequest(url: target)) }
                    }
                    .padding(.horizontal, 10)
                    .frame(height: 36)
                    .background(FJTheme.color.surfaceVariant, in: RoundedRectangle(cornerRadius: 10))
                    .onReceive(NotificationCenter.default.publisher(for: UITextField.textDidBeginEditingNotification)) { note in
                        let field = note.object as? UITextField
                        DispatchQueue.main.async { field?.selectAll(nil) }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                            if field?.isFirstResponder == true { field?.selectAll(nil) }
                        }
                    }
                Button(action: close) {
                    Image(systemName: "xmark")
                        .font(.fj(15, .semibold))
                        .frame(width: 36, height: 36)
                        .background(FJTheme.color.surfaceVariant, in: Circle())
                }
                .accessibilityLabel(s.close)
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(FJTheme.color.surface)
            HStack(spacing: 0) {
                Button { model.webView.goBack() } label: {
                    Image(systemName: "chevron.left").font(.fj(22, .semibold)).frame(maxWidth: .infinity, minHeight: 44)
                }
                .disabled(!model.canBack)
                Button { model.webView.goForward() } label: {
                    Image(systemName: "chevron.right").font(.fj(22, .semibold)).frame(maxWidth: .infinity, minHeight: 44)
                }
                .disabled(!model.canForward)
                if model.loading {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 44)
                } else {
                    Button { model.webView.reload() } label: {
                        Image(systemName: "arrow.clockwise").font(.fj(20, .semibold)).frame(maxWidth: .infinity, minHeight: 44)
                    }
                }
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 12)
            .background(FJTheme.color.surface)
            Divider()
            ZStack(alignment: .bottomTrailing) {
                FJPageWebView(model: model, url: url)
                Button {
                    if model.mediaUrl.isEmpty {
                        model.sendPage { ok in if ok { close() } }
                    } else {
                        model.download { ok in if ok { close() } }
                    }
                } label: {
                    Image(systemName: "arrow.down.to.line")
                        .font(.fj(22, .semibold))
                        .foregroundStyle(.white)
                        .frame(width: 56, height: 56)
                        .background(FJTheme.color.primary.opacity(model.mediaUrl.isEmpty ? 0.6 : 1), in: Circle())
                        .shadow(radius: 4, y: 2)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(s.browserDownloadMedia)
                .padding(16)
            }
        }
        .background(FJTheme.color.background.ignoresSafeArea())
        .onAppear {
            typed = url.absoluteString
            model.onSent = close
        }
        .onDisappear { model.shutDown() }
        .onChange(of: model.address) { _, value in
            if !value.isEmpty { typed = value }
        }
    }
}
