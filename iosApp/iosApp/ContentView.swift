import SwiftUI
import SharedUI

struct ComposeView: UIViewControllerRepresentable {
    var onForcedDarkChanged: (Bool?) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController { forced in
            onForcedDarkChanged(forced?.boolValue)
        }
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    /// nil = the app's theme is System: force nothing, so the window (and Compose's
    /// isSystemInDarkTheme) follows the device's appearance live.
    @State private var forcedDark: Bool? = nil

    var body: some View {
        ComposeView { forced in
            self.forcedDark = forced
        }
        .ignoresSafeArea()
        .ignoresSafeArea(.keyboard)
        .preferredColorScheme(forcedDark.map { $0 ? .dark : .light })
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
