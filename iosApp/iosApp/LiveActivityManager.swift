import Foundation
import ActivityKit
import SwiftUI

@MainActor
class LiveActivityManager: ObservableObject {
    static let shared = LiveActivityManager()

    private var currentActivity: Activity<DownloadActivityAttributes>?
    /// Downloads currently represented by the (single) activity; it ends when this empties.
    private var activeTaskIds: Set<String> = []

    func startOrUpdateLiveActivity(
        taskId: String,
        fileName: String,
        engineType: String,
        progress: Double,
        progressPercent: Int,
        speedText: String,
        downloadedBytesStr: String,
        totalBytesStr: String,
        statusText: String
    ) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            print("LiveActivityManager: Live Activities are not enabled.")
            return
        }

        activeTaskIds.insert(taskId)
        let state = DownloadActivityAttributes.ContentState(
            progress: progress,
            progressPercent: progressPercent,
            speedText: speedText,
            downloadedBytesStr: downloadedBytesStr,
            totalBytesStr: totalBytesStr,
            statusText: statusText
        )

        if let activity = currentActivity, activity.activityState == .active {
            Task {
                await activity.update(
                    ActivityContent(state: state, staleDate: Date().addingTimeInterval(30))
                )
            }
        } else {
            currentActivity = nil
            let attributes = DownloadActivityAttributes(fileName: fileName, engineType: engineType)
            let content = ActivityContent(state: state, staleDate: Date().addingTimeInterval(30))
            do {
                currentActivity = try Activity.request(
                    attributes: attributes,
                    content: content,
                    pushType: nil
                )
                print("LiveActivityManager: Live Activity requested successfully for \(fileName)")
            } catch {
                print("Failed to start Live Activity: \(error.localizedDescription)")
            }
        }
    }

    /// Called per task. The activity is dismissed only once no tracked download is left:
    /// right away for pause/cancel, after a few seconds for a completed download so the
    /// "Completed" state is actually seen on the Lock Screen / Dynamic Island.
    func endLiveActivity(taskId: String, completed: Bool) {
        activeTaskIds.remove(taskId)
        guard activeTaskIds.isEmpty, let activity = currentActivity else {
            return
        }
        currentActivity = nil
        Task {
            let policy: ActivityUIDismissalPolicy = completed
                ? .after(Date().addingTimeInterval(4))
                : .immediate
            await activity.end(activity.content, dismissalPolicy: policy)
        }
    }

    /// Safety net at launch: activities left over from a previous process (crash, force quit).
    func endStaleActivities() {
        for activity in Activity<DownloadActivityAttributes>.activities where activity.id != currentActivity?.id {
            Task {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
    }
}
