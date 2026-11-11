import SwiftUI
import UIKit

enum FJTheme {
    struct Colors {
        let primary = FJTheme.dyn(0x00E5FF, 0x0284C7)
        let onPrimary = FJTheme.dyn(0x031625, 0xFFFFFF)
        let primaryContainer = FJTheme.dyn(0x0C3D60, 0xE0F2FE)
        let onPrimaryContainer = FJTheme.dyn(0xC7F1FF, 0x0369A1)
        let secondary = FJTheme.dyn(0x818CF8, 0x4F46E5)
        let onSecondary = FJTheme.dyn(0x1E1B4B, 0xFFFFFF)
        let secondaryContainer = FJTheme.dyn(0x2C2F63, 0xEEF2FF)
        let onSecondaryContainer = FJTheme.dyn(0xE0E7FF, 0x312E81)
        let tertiary = FJTheme.dyn(0xF43F5E, 0xE11D48)
        let background = FJTheme.dyn(0x080D1A, 0xF8FAFC)
        let surface = FJTheme.dyn(0x0F1728, 0xFFFFFF)
        let surfaceVariant = FJTheme.dyn(0x172238, 0xF1F5F9)
        let onBackground = FJTheme.dyn(0xF8FAFC, 0x0F172A)
        let onSurface = FJTheme.dyn(0xF1F5F9, 0x0F172A)
        let onSurfaceVariant = FJTheme.dyn(0x94A3B8, 0x475569)
        let outline = FJTheme.dyn(0x334155, 0x94A3B8)
        let outlineVariant = FJTheme.dyn(0x1E2B42, 0xCBD5E1)
        let error = FJTheme.dyn(0xF87171, 0xDC2626)
        let onError = FJTheme.dyn(0x450A0A, 0xFFFFFF)
        let errorContainer = FJTheme.dyn(0x451A1A, 0xFEE2E2)
        let onErrorContainer = FJTheme.dyn(0xFECACA, 0x991B1B)
        let engineTorrent = FJTheme.hex(0x10B981)
        let engineMedia = FJTheme.hex(0x818CF8)
        let statusCompleted = FJTheme.hex(0x10B981)
        let statusPaused = FJTheme.hex(0xF59E0B)
        let statusConnecting = FJTheme.hex(0x38BDF8)
        let statusError = FJTheme.hex(0xF87171)
    }

    struct Fonts {
        let largeTitle = Font.system(size: 26, weight: .bold)
        let title = Font.system(size: 20, weight: .semibold)
        let headline = Font.system(size: 16, weight: .semibold)
        let body = Font.system(size: 15)
        let callout = Font.system(size: 14)
        let caption = Font.system(size: 12)
        let small = Font.system(size: 11)
        let mono = Font.system(size: 12, design: .monospaced)
    }

    static let color = Colors()
    static let font = Fonts()
    static let corner: CGFloat = 14
    static let cornerSmall: CGFloat = 8

    static func colorScheme(themeMode: String) -> ColorScheme? {
        switch themeMode {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }

    static func hex(_ rgb: UInt32) -> Color {
        Color(uiColor: uiColor(rgb))
    }

    static func dyn(_ dark: UInt32, _ light: UInt32) -> Color {
        let d = uiColor(dark), l = uiColor(light)
        return Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? d : l })
    }

    private static func uiColor(_ rgb: UInt32) -> UIColor {
        UIColor(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}
