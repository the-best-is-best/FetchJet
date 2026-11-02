import SafariServices

/// Native side of the Safari Web Extension. The extension's JavaScript (the same files as the
/// Chrome / Firefox extension, copied from extension/ at build time) hands links to the app
/// through fetchjet://add, so there is nothing to answer here beyond an acknowledgement.
final class SafariWebExtensionHandler: NSObject, NSExtensionRequestHandling {
    func beginRequest(with context: NSExtensionContext) {
        let response = NSExtensionItem()
        response.userInfo = [SFExtensionMessageKey: ["ok": true]]
        context.completeRequest(returningItems: [response], completionHandler: nil)
    }
}
