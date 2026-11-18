import SwiftUI
import SharedLogic

struct SettingsReportPage: View {
    @EnvironmentObject var store: FJStore
    @State private var period: ReportPeriod = .thisMonth
    @State private var fromDate = Date()
    @State private var toDate = Date()

    private static let periods: [ReportPeriod] = [.thisMonth, .lastMonth, .last7Days, .last30Days, .thisYear, .all, .custom]

    private func label(_ p: ReportPeriod, _ s: AppLocalization) -> String {
        switch p {
        case .thisMonth: return s.reportThisMonth
        case .lastMonth: return s.reportLastMonth
        case .last7Days: return s.reportLast7
        case .last30Days: return s.reportLast30
        case .thisYear: return s.reportThisYear
        case .all: return s.reportAllTime
        default: return s.reportCustom
        }
    }

    private func dayKey(_ date: Date) -> DayKey {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return DayKey(year: Int32(c.year ?? 1970), month: Int32(c.month ?? 1), day: Int32(c.day ?? 1))
    }

    private func date(_ day: DayKey) -> Date {
        Calendar.current.date(from: DateComponents(year: Int(day.year), month: Int(day.month), day: Int(day.day))) ?? Date()
    }

    private func dayText(_ day: DayKey, _ s: AppLocalization) -> String {
        let names = s.reportMonthNames.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
        let m = Int(day.month) - 1
        return "\(day.day) \(m >= 0 && m < names.count ? names[m] : "\(day.month)") \(day.year)"
    }

    var body: some View {
        let s = store.strings
        let today = dayKey(Date())
        let pair = DownloadReport.shared.rangeOf(period: period, today: today, customFrom: dayKey(fromDate), customTo: dayKey(toDate))
        let from = pair.first as? DayKey
        let to = pair.second as? DayKey
        let tasks = store.state.allTasks
        let report = DownloadReport.shared.forRange(tasks: tasks, from: from, to: to, utcOffsetMs: Int64(TimeZone.current.secondsFromGMT()) * 1000, topSites: 5)
        let rangeText: String? = {
            guard let from, let to else { return nil }
            return from == to ? dayText(from, s) : dayText(from, s) + "  –  " + dayText(to, s)
        }()

        VStack(alignment: .leading, spacing: 20) {
            SettingsGroupBox(title: s.reportPeriod, footer: rangeText) {
                SettingsFlow(spacing: 8) {
                    ForEach(Self.periods, id: \.self) { p in
                        Button {
                            if p == .custom, period != .custom {
                                if let from { fromDate = date(from) }
                                if let to { toDate = date(to) }
                            }
                            withAnimation(.easeInOut(duration: 0.18)) { period = p }
                        } label: {
                            Text(label(p, s))
                                .font(FJTheme.font.callout.weight(p == period ? .bold : .medium))
                                .foregroundStyle(p == period ? FJTheme.color.onPrimary : FJTheme.color.onSurfaceVariant)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(p == period ? FJTheme.color.primary : .clear, in: Capsule())
                                .overlay(Capsule().stroke(p == period ? .clear : FJTheme.color.outline.opacity(0.6)))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16)
                if period == .custom {
                    SettingsDivider()
                    DatePicker(s.reportFrom, selection: $fromDate, in: ...toDate, displayedComponents: .date)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                    SettingsDivider()
                    DatePicker(s.reportTo, selection: $toDate, in: fromDate..., displayedComponents: .date)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                }
            }

            HStack(spacing: 10) {
                tile("\(report.files)", s.reportFiles, highlight: true)
                tile(store.host.formatBytes(bytes: report.bytes), s.reportSize)
                tile("\(report.siteCount)", s.reportSites)
            }

            SettingsGroupBox(title: s.reportTopSites, footer: s.reportFooter) {
                if report.sites.isEmpty {
                    SettingsRow(title: tasks.isEmpty ? s.reportEmpty : s.reportNothingInPeriod)
                } else {
                    let top = max(Int64(1), report.sites.map { $0.bytes }.max() ?? 1)
                    VStack(spacing: 14) {
                        ForEach(Array(report.sites.enumerated()), id: \.offset) { i, site in
                            bar(i, site.site, store.host.formatBytes(bytes: site.bytes) + " · \(site.files)",
                                max(0.02, min(1, Double(site.bytes) / Double(top))))
                        }
                    }
                    .padding(16)
                }
            }
        }
    }

    private func tile(_ value: String, _ label: String, highlight: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(highlight ? FJTheme.color.primary : FJTheme.color.onSurface)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(FJTheme.font.caption.weight(.semibold))
                .foregroundStyle(FJTheme.color.onSurfaceVariant)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(
            LinearGradient(
                colors: [FJTheme.color.primary.opacity(highlight ? 0.16 : 0.07), FJTheme.color.surfaceVariant.opacity(0.45)],
                startPoint: .topLeading, endPoint: .bottomTrailing
            ),
            in: RoundedRectangle(cornerRadius: 14)
        )
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(FJTheme.color.primary.opacity(highlight ? 0.35 : 0.14)))
    }

    private func bar(_ index: Int, _ site: String, _ value: String, _ fraction: Double) -> some View {
        VStack(spacing: 6) {
            HStack(spacing: 10) {
                Text("\(index + 1)")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(index == 0 ? FJTheme.color.primary : FJTheme.color.onSurfaceVariant)
                    .frame(width: 22, height: 22)
                    .background(index == 0 ? FJTheme.color.primary.opacity(0.18) : FJTheme.color.surfaceVariant, in: Circle())
                Text(site)
                    .font(FJTheme.font.body.weight(.semibold))
                    .foregroundStyle(FJTheme.color.onSurface)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(value)
                    .font(FJTheme.font.caption)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(FJTheme.color.outlineVariant.opacity(0.45))
                    Capsule()
                        .fill(index == 0
                              ? AnyShapeStyle(LinearGradient(colors: [FJTheme.color.primary, FJTheme.color.secondary], startPoint: .leading, endPoint: .trailing))
                              : AnyShapeStyle(FJTheme.color.primary.opacity(0.55)))
                        .frame(width: max(8, geo.size.width * fraction))
                }
            }
            .frame(height: 8)
        }
    }
}
