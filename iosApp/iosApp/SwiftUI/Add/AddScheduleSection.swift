import SwiftUI
import SharedLogic

struct AddScheduleSection: View {
    @EnvironmentObject var store: FJStore
    @State private var showTime = false
    @State private var dayOffset: Int32 = 0
    @State private var time = Date()
    @State private var timeError: String?

    var body: some View {
        let strings = store.strings
        let start = store.state.scheduleStartMs
        VStack(alignment: .leading, spacing: 8) {
            AddLabel(text: strings.whenToStart)
            AddChipRow {
                AddChoiceChip(label: strings.scheduleNow, selected: start == 0 && !showTime) {
                    showTime = false
                    store.send(DashboardUiEventOnScheduleChanged(epochMs: 0))
                }
                AddChoiceChip(label: strings.scheduleIn30Min, selected: false) {
                    showTime = false
                    store.send(DashboardUiEventOnScheduleChanged(epochMs: LocalClockKt.currentEpochMs() + 30 * 60_000))
                }
                AddChoiceChip(label: strings.scheduleIn1Hour, selected: false) {
                    showTime = false
                    store.send(DashboardUiEventOnScheduleChanged(epochMs: LocalClockKt.currentEpochMs() + 60 * 60_000))
                }
                AddChoiceChip(label: strings.scheduleAtTime, selected: showTime) {
                    if !showTime {
                        time = Date()
                        dayOffset = 0
                        timeError = nil
                    }
                    showTime = true
                }
            }
            if showTime {
                Text(strings.scheduleDayLabel)
                    .font(FJTheme.font.small)
                    .foregroundStyle(FJTheme.color.onSurfaceVariant)
                AddChipRow {
                    let days: [(Int32, String)] = [
                        (0, strings.scheduleToday), (1, strings.scheduleTomorrow), (2, strings.scheduleIn2Days),
                        (3, strings.scheduleIn3Days), (7, strings.scheduleInAWeek)
                    ]
                    ForEach(days, id: \.0) { d, label in
                        AddChoiceChip(label: label, selected: dayOffset == d) { dayOffset = d }
                    }
                }
                HStack(spacing: 10) {
                    DatePicker("", selection: $time, displayedComponents: .hourAndMinute)
                        .labelsHidden()
                    Spacer()
                    Button(strings.scheduleSetTime) { setTime() }
                        .font(.system(size: 12, weight: .bold))
                        .buttonStyle(.borderedProminent)
                }
                if let timeError {
                    Text(timeError).font(FJTheme.font.small).foregroundStyle(FJTheme.color.error)
                }
            }
            if start > 0 {
                Text("🕑 \(strings.scheduleStartsAt(time: LocalClockKt.formatLocalSchedule(epochMs: start, nowMs: LocalClockKt.currentEpochMs())))")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(FJTheme.color.primary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(FJTheme.color.primaryContainer.opacity(0.45), in: RoundedRectangle(cornerRadius: 8))
            }
        }
    }

    private func setTime() {
        let c = Calendar.current.dateComponents([.hour, .minute], from: time)
        let now = LocalClockKt.currentEpochMs()
        let at = LocalClockKt.localTimeOnDayEpochMs(dayOffset: dayOffset, hour: Int32(c.hour ?? 0), minute: Int32(c.minute ?? 0), nowMs: now)
        if at <= now {
            timeError = store.strings.scheduleTimePassed
        } else {
            timeError = nil
            store.send(DashboardUiEventOnScheduleChanged(epochMs: at))
        }
    }
}

struct AddConnectionsSection: View {
    @EnvironmentObject var store: FJStore
    var body: some View {
        let s = store.state
        let strings = store.strings
        VStack(alignment: .leading, spacing: 8) {
            AddLabel(text: strings.addConnectionsTitle)
            AddChipRow {
                AddChoiceChip(
                    label: s.defaultConnections > 0 ? strings.connectionsAutoWithDefault(n: s.defaultConnections) : strings.connectionsAuto,
                    selected: s.selectedConnections == 0
                ) { store.send(DashboardUiEventOnConnectionsChanged(connections: 0)) }
                ForEach([Int32(1), 4, 8, 16, 32], id: \.self) { n in
                    AddChoiceChip(label: "\(n)", selected: s.selectedConnections == n) {
                        store.send(DashboardUiEventOnConnectionsChanged(connections: n))
                    }
                }
            }
        }
    }
}
