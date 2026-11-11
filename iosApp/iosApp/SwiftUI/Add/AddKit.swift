import SwiftUI
import SharedLogic

struct AddLabel: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(FJTheme.color.onSurface)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct AddCard<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 8) { content }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(FJTheme.color.surfaceVariant.opacity(0.35), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(FJTheme.color.outlineVariant.opacity(0.6), lineWidth: 1))
    }
}

struct AddField: View {
    let icon: String
    var placeholder: String = ""
    @Binding var text: String
    var multiline = false
    var enabled = true
    var keyboard: UIKeyboardType = .default
    var trailing: AnyView? = nil
    @FocusState private var focused: Bool

    var body: some View {
        HStack(alignment: multiline ? .top : .center, spacing: 8) {
            Text(icon).font(.system(size: 16)).padding(.top, multiline ? 2 : 0)
            Group {
                if multiline {
                    TextField(placeholder, text: $text, axis: .vertical).lineLimit(1...3)
                } else {
                    TextField(placeholder, text: $text)
                }
            }
            .font(FJTheme.font.callout)
            .foregroundStyle(FJTheme.color.onSurface)
            .keyboardType(keyboard)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .focused($focused)
            .disabled(!enabled)
            if let trailing { trailing }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(FJTheme.color.surfaceVariant.opacity(enabled ? 0.45 : 0.25), in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(focused ? FJTheme.color.primary : FJTheme.color.outlineVariant.opacity(0.7), lineWidth: focused ? 1.5 : 1)
        )
        .shadow(color: focused ? FJTheme.color.primary.opacity(0.25) : .clear, radius: 6)
    }
}

struct AddFieldIcon: View {
    let symbol: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(symbol)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
                .frame(width: 28, height: 28)
                .background(FJTheme.color.surfaceVariant, in: Circle())
        }
        .buttonStyle(.plain)
    }
}

struct AddFieldChip: View {
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(FJTheme.color.primary)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(FJTheme.color.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }
}

struct AddChoiceChip: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: selected ? .bold : .medium))
                .foregroundStyle(selected ? FJTheme.color.primary : FJTheme.color.onSurface)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(selected ? FJTheme.color.primary.opacity(0.18) : FJTheme.color.surface.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(selected ? FJTheme.color.primary : FJTheme.color.outlineVariant.opacity(0.6), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct AddChipRow<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) { content }.padding(.vertical, 1)
        }
    }
}

struct AddCheckbox: View {
    let checked: Bool
    var body: some View {
        Image(systemName: checked ? "checkmark.square.fill" : "square")
            .font(.system(size: 20))
            .foregroundStyle(checked ? FJTheme.hex(0x0284C7) : FJTheme.hex(0x64748B))
    }
}

struct AddLoadingBanner: View {
    let title: String
    let subtitle: String
    var body: some View {
        HStack(spacing: 12) {
            ProgressView().tint(FJTheme.color.primary)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 13, weight: .bold)).foregroundStyle(FJTheme.color.primary)
                Text(subtitle).font(FJTheme.font.small).foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(FJTheme.color.primaryContainer.opacity(0.3), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.primary.opacity(0.4), lineWidth: 1))
    }
}

struct AddInlineError: View {
    let message: String
    var body: some View {
        HStack(alignment: .top, spacing: 6) {
            Text("⚠️").font(.system(size: 14))
            Text(message).font(.system(size: 11, weight: .medium)).foregroundStyle(FJTheme.color.error)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(FJTheme.color.errorContainer.opacity(0.35), in: RoundedRectangle(cornerRadius: 8))
    }
}

struct AddFailureCard: View {
    let title: String
    let message: String
    let retryLabel: String
    let onRetry: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.system(size: 13, weight: .bold)).foregroundStyle(FJTheme.color.error)
            Text(message).font(FJTheme.font.small).foregroundStyle(FJTheme.color.onSurfaceVariant)
            HStack {
                Spacer()
                Button(action: onRetry) {
                    Text(retryLabel)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(FJTheme.color.onError)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(FJTheme.color.error, in: RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(FJTheme.color.errorContainer.opacity(0.25), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.color.error.opacity(0.6), lineWidth: 1))
    }
}

struct AddOutlineButton: View {
    let label: String
    var enabled = true
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(FJTheme.hex(0x38BDF8))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(FJTheme.hex(0x0284C7), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.5)
    }
}

struct AddOptionCard: View {
    let selected: Bool
    let title: String
    var lines: [String] = []
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(selected ? "◉" : "○")
                        .foregroundStyle(selected ? FJTheme.color.primary : FJTheme.color.onSurfaceVariant)
                    Text(title)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(selected ? FJTheme.color.primary : FJTheme.color.onSurface)
                        .multilineTextAlignment(.leading)
                }
                ForEach(lines, id: \.self) { line in
                    Text("• \(line)")
                        .font(.system(size: 10))
                        .foregroundStyle(FJTheme.color.onSurfaceVariant)
                        .multilineTextAlignment(.leading)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(selected ? FJTheme.color.primary.opacity(0.12) : FJTheme.color.surfaceVariant.opacity(0.25), in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(selected ? FJTheme.color.primary : FJTheme.color.outlineVariant.opacity(0.5), lineWidth: selected ? 2 : 1))
        }
        .buttonStyle(.plain)
    }
}

extension FJStore {
    func addClockText(_ total: Int64) -> String {
        let t = max(total, 0)
        let h = t / 3600, m = (t % 3600) / 60, s = t % 60
        return h > 0 ? String(format: "%lld:%02lld:%02lld", h, m, s) : String(format: "%lld:%02lld", m, s)
    }
}
