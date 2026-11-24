import SwiftUI
import WebKit
import SharedLogic

final class FJSiteSignInModel: NSObject, ObservableObject, WKNavigationDelegate {
    @Published var loading = true
    let webView: WKWebView

    override init() {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        webView = WKWebView(frame: .zero, configuration: config)
        super.init()
        webView.navigationDelegate = self
        webView.allowsBackForwardNavigationGestures = true
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        loading = true
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        loading = false
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        loading = false
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        loading = false
    }

    func cookieHeader(for address: URL, done: @escaping (String) -> Void) {
        let target = webView.url ?? address
        webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { cookies in
            let header = cookies.filter { FJPageBrowserModel.matches($0, target) }
                .map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
            done(header)
        }
    }
}

private struct FJSiteSignInWebView: UIViewRepresentable {
    let model: FJSiteSignInModel
    let url: URL

    func makeUIView(context: Context) -> WKWebView {
        model.webView.load(URLRequest(url: url))
        return model.webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

struct FJSignInTarget: Identifiable {
    let id = UUID()
    let url: URL
    let site: String

    static func make(from text: String) -> FJSignInTarget? {
        let typed = text.trimmingCharacters(in: .whitespaces)
        let site = SiteLogin.companion.cleanSite(text: typed)
        guard site.contains("."), let url = URL(string: typed.contains("://") ? typed : "https://" + typed) else { return nil }
        return FJSignInTarget(url: url, site: site)
    }
}

struct FJSiteSignInSheet: View {
    @EnvironmentObject var store: FJStore
    @StateObject private var model = FJSiteSignInModel()
    let target: FJSignInTarget
    let finished: (String?) -> Void

    var body: some View {
        let s = store.strings
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                Text(s.netLoginBrowserHint)
                    .font(.fj(12))
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                HStack(spacing: 12) {
                    Button(s.cancel) { finished(nil) }
                        .font(.fj(14, .semibold))
                        .foregroundStyle(FJTheme.color.primary)
                    Spacer(minLength: 0)
                    if model.loading { ProgressView() }
                    Button {
                        model.cookieHeader(for: target.url) { header in
                            finished(header.isEmpty ? nil : header)
                        }
                    } label: {
                        Text(s.netLoginBrowserDone)
                            .font(.fj(14, .semibold))
                            .lineLimit(1)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .foregroundStyle(.white)
                            .background(FJTheme.color.primary, in: Capsule())
                    }
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(FJTheme.color.surface)
            Divider()
            FJSiteSignInWebView(model: model, url: target.url)
        }
        .background(FJTheme.color.background.ignoresSafeArea())
    }
}
