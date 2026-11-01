#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, FJTorrentState) {
    FJTorrentStateConnecting,
            FJTorrentStateCheckingFiles,
            FJTorrentStateCheckingResumeData,
            FJTorrentStateDownloadingMetadata,
            FJTorrentStateDownloading,
            FJTorrentStateFinished,
            FJTorrentStateSeeding,
};

typedef NS_ENUM(uint8_t, FJFilePriority
) {
FJFilePriorityDontDownload = 0,
        FJFilePriorityLow = 1,
        FJFilePriorityDefault = 4,
        FJFilePriorityTop = 7,
};

@interface FJTorrentFile : NSObject
@property(nonatomic, readonly) NSInteger index;
@property(nonatomic, readonly, copy) NSString *path;
@property(nonatomic, readonly) int64_t size;
@end

@interface FJTorrentInfo : NSObject
@property(nonatomic, readonly, copy) NSString *name;
@property(nonatomic, readonly, copy) NSString *infoHash;
@property(nonatomic, readonly) NSInteger fileCount;
@property(nonatomic, readonly, copy) NSArray<FJTorrentFile *> *files;
@property(nonatomic, readonly) int64_t totalSize;
@property(nonatomic, readonly, copy) NSData *torrentData;
@end

@interface FJTorrentStatus : NSObject
@property(nonatomic, readonly) FJTorrentState state;
@property(nonatomic, readonly) BOOL isPaused;
@property(nonatomic, readonly) BOOL isFinished;
@property(nonatomic, readonly) BOOL isSeeding;
@property(nonatomic, readonly) BOOL hasMetadata;
@property(nonatomic, readonly, copy, nullable) NSString *errorMessage;
@property(nonatomic, readonly) int64_t totalWanted;
@property(nonatomic, readonly) int64_t totalWantedDone;
@property(nonatomic, readonly) int64_t downloadRate;
@property(nonatomic, readonly) int64_t uploadRate;
@property(nonatomic, readonly) int32_t peers;
@property(nonatomic, readonly) int32_t seeds;
@property(nonatomic, readonly, copy) NSArray<NSNumber *> *fileProgress;
@end

NS_SWIFT_NAME(TorrentSession)

@interface FJTorrentSession : NSObject

@property(class, nonatomic, readonly) NSString *libtorrentVersion;

- (instancetype)init

NS_UNAVAILABLE;

- (nullable instancetype)initWithListenPort:(NSInteger)port
                                  userAgent:(NSString *)userAgent
                            peerFingerprint:(NSString *)fingerprint
                                      error:(NSError **)error NS_DESIGNATED_INITIALIZER;

+ (nullable FJTorrentInfo *)infoFromTorrentData:(NSData *)data error:(NSError **)error
NS_SWIFT_NAME

(
info(torrentData
:));

- (nullable NSString *)findMagnet:(NSString *)uri NS_SWIFT_NAME

(
find(magnet
:));

- (nullable NSString *)findTorrentData:(NSData *)data NS_SWIFT_NAME

(
find(torrentData
:));

- (nullable NSString *)addMagnet:(NSString *)uri savePath:(NSString *)savePath error:(NSError **)error
NS_SWIFT_NAME

(
add(magnet
:savePath:));

- (nullable NSString *)addTorrentData:(NSData *)data
                             savePath:(NSString *)savePath
                       filePriorities:(NSArray

<NSNumber *> *)
priorities
        error
:(NSError **)
error
        NS_SWIFT_NAME(add(torrentData
:savePath:filePriorities:));

- (nullable FJTorrentStatus *)statusFor:(NSString *)key NS_SWIFT_NAME

(
status(_
:));

- (nullable FJTorrentInfo *)infoFor:(NSString *)key NS_SWIFT_NAME

(
info(_
:));

- (void)pause:(NSString *)key;

- (void)resume:(NSString *)key;

- (void)remove:(NSString *)key deleteFiles:(BOOL)deleteFiles NS_SWIFT_NAME

(
remove(_
:deleteFiles:));

- (void)setFilePriorities:(NSArray

<NSNumber *> *)priorities for:(NSString *)
key
        NS_SWIFT_NAME(setFilePriorities(_
:for:));

- (void)setDownloadLimit:(int64_t)download uploadLimit:(int64_t)upload for:(NSString *)key
NS_SWIFT_NAME

(
setLimits(download
:upload:for:));

- (void)shutdown;

@end

NS_ASSUME_NONNULL_END
