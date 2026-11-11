import SwiftUI
import SharedLogic

struct ContentView: View {
    @StateObject private var store = FJStore()

    var body: some View {
        FJRoot()
            .environmentObject(store)
    }
}
