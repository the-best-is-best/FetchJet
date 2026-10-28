import SwiftUI
import SharedUI

struct ComposeView: UIViewControllerRepresentable {
    var onDarkModeChanged: (Bool) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController { isDark in
            onDarkModeChanged(isDark.boolValue)
        }
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @State private var isDarkMode: Bool? = nil

    var body: some View {
        ComposeView { isDark in
            self.isDarkMode = isDark
        }
        .ignoresSafeArea()
        .ignoresSafeArea(.keyboard)
        .preferredColorScheme(isDarkMode == nil ? nil : (isDarkMode! ? .dark : .light))
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
