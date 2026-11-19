import SwiftUI
import SharedLogic

enum FJFont {
    static func name(_ weight: Font.Weight) -> String {
        let style: AppFontWeight
        switch weight {
        case .medium: style = .medium
        case .semibold: style = .semiBold
        case .bold, .heavy, .black: style = .bold
        default: style = .regular
        }
        return AppFont.shared.current.postScriptName(weight: style)
    }

    static func uiFont(_ size: CGFloat, _ weight: Font.Weight) -> UIFont {
        UIFont(name: name(weight), size: size) ?? .systemFont(ofSize: size, weight: weight == .bold ? .bold : .semibold)
    }

    static func applyToNavigationBars() {
        let bar = UINavigationBar.appearance()
        bar.largeTitleTextAttributes = [.font: uiFont(34, .bold)]
        bar.titleTextAttributes = [.font: uiFont(17, .semibold)]
    }
}

extension Font {
    static func fj(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        Font.custom(FJFont.name(weight), fixedSize: size)
    }
}
