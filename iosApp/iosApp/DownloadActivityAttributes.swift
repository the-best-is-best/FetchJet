import ActivityKit
import Foundation

public struct DownloadActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public var progress: Double
        public var progressPercent: Int
        public var speedText: String
        public var downloadedBytesStr: String
        public var totalBytesStr: String
        public var statusText: String

        public init(
            progress: Double,
            progressPercent: Int,
            speedText: String,
            downloadedBytesStr: String,
            totalBytesStr: String,
            statusText: String
        ) {
            self.progress = progress
            self.progressPercent = progressPercent
            self.speedText = speedText
            self.downloadedBytesStr = downloadedBytesStr
            self.totalBytesStr = totalBytesStr
            self.statusText = statusText
        }
    }

    public var fileName: String
    public var engineType: String

    public init(fileName: String, engineType: String) {
        self.fileName = fileName
        self.engineType = engineType
    }
}
