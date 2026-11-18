import SwiftUI
import WebKit
import SharedLogic

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

final class FJPageBrowserModel: NSObject, ObservableObject, WKScriptMessageHandler, WKNavigationDelegate {
    @Published var mediaUrl: String = ""
    @Published var title: String = ""
    @Published var loading = true
    let webView: WKWebView

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
    })();
    """

    override init() {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.websiteDataStore = .default()
        webView = WKWebView(frame: .zero, configuration: config)
        super.init()
        config.userContentController.add(self, name: "fjMedia")
        config.userContentController.addUserScript(
            WKUserScript(source: Self.finder, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
        )
        webView.navigationDelegate = self
        webView.allowsBackForwardNavigationGestures = true
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        mediaUrl = (message.body as? String) ?? ""
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        loading = true
        mediaUrl = ""
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        loading = false
        title = webView.title ?? ""
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        loading = false
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        loading = false
    }

    private static func matches(_ cookie: HTTPCookie, _ url: URL) -> Bool {
        guard let host = url.host?.lowercased() else { return false }
        let domain = cookie.domain.lowercased()
        let bare = domain.hasPrefix(".") ? String(domain.dropFirst()) : domain
        guard host == bare || host.hasSuffix("." + bare) else { return false }
        if cookie.isSecure && url.scheme?.lowercased() != "https" { return false }
        let path = url.path.isEmpty ? "/" : url.path
        return path.hasPrefix(cookie.path)
    }

    func download(done: @escaping (Bool) -> Void) {
        guard let media = URL(string: mediaUrl), let page = webView.url else { return done(false) }
        let store = webView.configuration.websiteDataStore.httpCookieStore
        webView.evaluateJavaScript("navigator.userAgent") { [weak self] agent, _ in
            store.getAllCookies { cookies in
                guard let self else { return done(false) }
                let header = cookies.filter { Self.matches($0, media) }
                    .map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
                let name = media.lastPathComponent
                let body: [String: Any] = [
                    "url": self.mediaUrl,
                    "cookie": header,
                    "referer": page.absoluteString,
                    "userAgent": (agent as? String) ?? "",
                    "fileName": name.contains(".") ? name : ""
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
        model.webView.load(URLRequest(url: url))
        return model.webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

struct FJPageBrowserSheet: View {
    @EnvironmentObject var store: FJStore
    @StateObject private var model = FJPageBrowserModel()
    let url: URL
    let close: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Button(action: close) {
                    Image(systemName: "xmark")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(width: 36, height: 36)
                        .background(FJTheme.color.surfaceVariant, in: Circle())
                }
                .accessibilityLabel(store.strings.close)
                VStack(alignment: .leading, spacing: 2) {
                    Text(model.title.isEmpty ? (url.host ?? "") : model.title)
                        .font(.system(size: 15, weight: .semibold))
                        .lineLimit(1)
                    Text(model.webView.url?.host ?? url.host ?? "")
                        .font(.system(size: 12))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                if model.loading { ProgressView() }
                Button {
                    model.download { ok in if ok { close() } }
                } label: {
                    Label(store.strings.addStartDownload, systemImage: "arrow.down.circle.fill")
                        .font(.system(size: 14, weight: .semibold))
                        .lineLimit(1)
                        .fixedSize()
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .foregroundStyle(.white)
                        .background(FJTheme.color.primary.opacity(model.mediaUrl.isEmpty ? 0.35 : 1), in: Capsule())
                }
                .disabled(model.mediaUrl.isEmpty)
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(FJTheme.color.surface)
            Divider()
            FJPageWebView(model: model, url: url)
        }
        .background(FJTheme.color.background.ignoresSafeArea())
    }
}
