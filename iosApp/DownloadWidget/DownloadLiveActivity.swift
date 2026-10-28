import ActivityKit
import WidgetKit
import SwiftUI

public struct DownloadLiveActivityWidget: Widget {
    public init() {
    }

    public var body: some WidgetConfiguration {
        ActivityConfiguration(for: DownloadActivityAttributes.self) { context in
            // Lock Screen Live Activity Banner View
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Image(systemName: "arrow.down.circle.fill")
                        .foregroundColor(.blue)
                        .font(.title3)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(context.attributes.fileName)
                            .font(.headline)
                            .lineLimit(1)
                        Text(context.attributes.engineType)
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }

                    Spacer()

                    Text(context.state.speedText)
                        .font(.caption.bold())
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.blue.opacity(0.15))
                        .cornerRadius(8)
                }

                ProgressView(value: context.state.progress)
                    .tint(.blue)

                HStack {
                    Text("\(context.state.downloadedBytesStr) / \(context.state.totalBytesStr)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Spacer()
                    Text("\(context.state.progressPercent)%")
                        .font(.caption.bold())
                        .foregroundColor(.blue)
                }
            }
            .padding()
            .activityBackgroundTint(Color(UIColor.secondarySystemBackground))
            .activitySystemActionForegroundColor(Color.primary)

        } dynamicIsland: { context in
            DynamicIsland {
                // Dynamic Island Expanded View (Long Press)
                DynamicIslandExpandedRegion(.leading) {
                    HStack {
                        Image(systemName: "arrow.down.circle.fill")
                            .foregroundColor(.blue)
                        Text(context.attributes.fileName)
                            .font(.subheadline.bold())
                            .lineLimit(1)
                    }
                }

                DynamicIslandExpandedRegion(.trailing) {
                    Text(context.state.speedText)
                        .font(.caption.bold())
                        .foregroundColor(.blue)
                }

                DynamicIslandExpandedRegion(.center) {
                    VStack(spacing: 4) {
                        ProgressView(value: context.state.progress)
                            .tint(.blue)
                        Text("\(context.state.progressPercent)%")
                            .font(.caption2.bold())
                    }
                }

                DynamicIslandExpandedRegion(.bottom) {
                    HStack {
                        Text("\(context.state.downloadedBytesStr) / \(context.state.totalBytesStr)")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(context.state.statusText)
                            .font(.caption2.bold())
                    }
                }
            } compactLeading: {
                Image(systemName: "arrow.down.circle.fill")
                    .foregroundColor(.blue)
            } compactTrailing: {
                Text("\(context.state.progressPercent)%")
                    .font(.caption2.bold())
                    .foregroundColor(.blue)
            } minimal: {
                Image(systemName: "arrow.down.circle.fill")
                    .foregroundColor(.blue)
            }
        }
    }
}
