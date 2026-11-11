import SwiftUI

struct FJDialogButton: View {
    enum Kind { case filled, outlined, text, danger, dangerText }
    let title: String
    var kind: Kind = .filled
    var enabled: Bool = true
    var fill: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(FJTheme.font.callout.weight(kind == .text || kind == .dangerText ? .medium : .semibold))
                .lineLimit(2)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .frame(minHeight: 40)
                .frame(maxWidth: fill ? .infinity : nil)
                .foregroundStyle(foreground)
                .background(background, in: RoundedRectangle(cornerRadius: 10))
                .overlay {
                    if kind == .outlined {
                        RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.outline, lineWidth: 1)
                    }
                }
                .contentShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.45)
    }

    private var foreground: Color {
        switch kind {
        case .filled: return FJTheme.color.onPrimary
        case .danger: return FJTheme.color.onError
        case .dangerText: return FJTheme.color.error
        case .outlined, .text: return kind == .text ? FJTheme.color.primary : FJTheme.color.onSurface
        }
    }

    private var background: Color {
        switch kind {
        case .filled: return FJTheme.color.primary
        case .danger: return FJTheme.color.error
        default: return .clear
        }
    }
}

struct FJDialogFrame<Content: View, Buttons: View>: View {
    let title: String
    var icon: String? = nil
    var subtitle: String? = nil
    var stackButtons = false
    @ViewBuilder var content: () -> Content
    @ViewBuilder var buttons: () -> Buttons

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 12) {
                if let icon {
                    Text(icon)
                        .font(.system(size: 22))
                        .frame(width: 44, height: 44)
                        .background(FJTheme.color.primaryContainer.opacity(0.4), in: RoundedRectangle(cornerRadius: 12))
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(FJTheme.font.headline.weight(.bold))
                        .foregroundStyle(FJTheme.color.onSurface)
                    if let subtitle {
                        Text(subtitle)
                            .font(FJTheme.font.caption)
                            .foregroundStyle(FJTheme.color.onSurfaceVariant)
                    }
                }
            }
            content()
            if stackButtons {
                VStack(spacing: 8) { buttons() }
            } else {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 8) {
                        Spacer(minLength: 0)
                        buttons()
                    }
                    VStack(alignment: .trailing, spacing: 8) { buttons() }
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
            }
        }
        .padding(22)
        .frame(maxWidth: 440, alignment: .leading)
        .background(FJTheme.color.surface, in: RoundedRectangle(cornerRadius: 22))
        .overlay(RoundedRectangle(cornerRadius: 22).stroke(FJTheme.color.outlineVariant.opacity(0.6)))
        .shadow(color: .black.opacity(0.3), radius: 20, y: 8)
        .padding(.horizontal, 22)
    }
}

struct FJDialogCheckRow: View {
    let title: String
    @Binding var checked: Bool

    var body: some View {
        Button { checked.toggle() } label: {
            HStack(spacing: 10) {
                Image(systemName: checked ? "checkmark.square.fill" : "square")
                    .font(.system(size: 20))
                    .foregroundStyle(checked ? FJTheme.color.primary : FJTheme.color.onSurfaceVariant)
                Text(title)
                    .font(FJTheme.font.body)
                    .foregroundStyle(FJTheme.color.onSurface)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
