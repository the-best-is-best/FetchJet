# FetchJet iOS SwiftUI contract (foundation, 2026-11-11)

The iOS app no longer links Compose:
Swift does `import SharedLogic` (the `:sharedLogic` static framework). `:sharedUI` is untouched and
is NOT in the iOS app; nothing in it is reachable from Swift (read it only as the reference for
behaviour / layout of each page).

## Where files go
- Your pages: `iosApp/iosApp/SwiftUI/<Area>/*.swift` (e.g. `SwiftUI/TaskList/`, `SwiftUI/Details/`,
  `SwiftUI/AddDownload/`, `SwiftUI/Settings/`, `SwiftUI/Dialogs/`). The whole `iosApp/iosApp`
  folder is a PBXFileSystemSynchronizedRootGroup: new files compile automatically. Never edit
  `project.pbxproj`.
- Placeholders live in `iosApp/iosApp/SwiftUI/Placeholders.swift`. To ship your page, DELETE only
  your struct from that file (keep the others) and add your real file with the SAME name and
  initializer:
  - `TaskListScreen()`
  - `TaskDetailsScreen(taskId: String)`
  - `AddDownloadSheet()`
  - `SettingsScreen()`
  - `DeviceSendSheet(taskId: String)`
  - `AppDialogsHost()`
- Do not edit `SwiftUI/Core/*` (FJStore, FJTheme, FJRoot). If you need a store helper, add an
  `extension FJStore { ... }` in your own folder (unique names, prefixed by your area).
- Do not edit Kotlin in sharedLogic unless unavoidable; if you must, only add (never rename) and say
  so in your report. Kotlin rebuild = `bash scripts/ios-dev-build.command all`; Swift only =
  `bash scripts/ios-dev-build.command xcode` (log `build/ios-dev-build.log`). Only one build at a
  time in this worktree: wait with `while pgrep -f ios-dev-build.command >/dev/null; do sleep 15; done`.
- No code comments unless essential.

## Store (`SwiftUI/Core/FJStore.swift`)
Every view gets it with `@EnvironmentObject var store: FJStore` (injected at the root; sheets
inherit it).

```swift
@MainActor final class FJStore: ObservableObject {
    let host: IosAppHost                       // Kotlin host (below)
    @Published private(set) var state: DashboardUiState   // whole Kotlin UI state, replaced on every change
    @Published private(set) var language: String          // "en", "ar", ...
    @Published private(set) var isRtl: Bool
    @Published var toast: FJToast?             // bottom toast; set via showToast
    @Published var selectedTaskId: String?     // open details: set it to push TaskDetailsScreen
    @Published var missingFileTaskId: String?  // set by openFile when the file is gone (dialogs show it)
    var strings: AppLocalization               // current language's texts (always read fresh)
    var onEffect: ((DashboardUiEffect) -> Void)?
    private(set) var lastEffect: DashboardUiEffect?
    func send(_ event: DashboardUiEvent)
    func showToast(_ text: String)
    func task(_ id: String?) -> DownloadTask?  // lookup in state.allTasks
    func openFile(_ task: DownloadTask)        // opens the finished file, or sets missingFileTaskId
    var tabItems: [FJTabItem]                  // (FJRoot.swift) tabs shown for this edition
    func selectTab(_ tab: DashboardTab)        // clears selectedTaskId + sends OnTabSelected
}
```
`DashboardUiEffectShowToast` is already turned into a toast by the store; you do not need to
handle effects.

## Kotlin host (`sharedLogic/src/iosMain/.../ios/IosAppHost.kt`, Swift: `IosAppHost`)
Created once by FJStore (runs LocalizationManager.start(), creates DashboardViewModel = loads
settings, language, theme, auto tool update; clipboard watcher like Compose). Use via `store.host`:
- `strings()`, `language`, `isRtl`, `languageChoice` ("system" or code), `languages()` ->
  `[AppLocalization]` (each has `localeCode`, see AppLocalization for its display name members)
- `torrentEnabled`, `mediaEnabled` (AppFeatures; hide torrent / media UI when false)
- `localizeStep(text:)` for engine step / status texts (`task.currentStepText`, `postProcessLabel`,
  `state.ytDlpStatus`, torrent states) - same as Compose
- Formatters (copies of sharedUI utils/Formatters.kt): `formatBytes(bytes:)`,
  `formatSizeDisplay(bytes:)`, `formatSpeed(bytesPerSec:unit:)` (unit = `state.speedDisplayUnit`),
  `ltr(text:)`, `decodeUnicodeText(input:)`, `formatSecondsToHhMm(totalSeconds:)`,
  `parseHhMmToSeconds(hhMm:)`, `formatSecondsToDisplayFriendly(totalSeconds:)`.
  Int64 arguments: `store.host.formatBytes(bytes: task.totalBytes)`.
- `taskFilePath(task:)` (full path of a task's file), `openTaskFile(task:) -> Bool`,
  `openDownloadedFile(fullPath:) -> Bool` (prefer `store.openFile(task)`).
- `isDark(themeMode:systemDark:)`.
Anything else in sharedLogic is directly callable too (e.g. `PathResolver()`, `AppFeatures.shared`,
`AppLanguage.shared`, `TorrentFilePicker.shared`, top-level funcs as `<File>Kt.fn(...)`).

## Kotlin types as seen from Swift (Kotlin 2.4 ObjC export, no SKIE)
- Data classes = Swift classes with the same property names: `store.state.allTasks`,
  `store.state.filteredTasks` (already filtered + sorted for `state.selectedTab`),
  `task.fileName`, `task.id`. Look up names in `DashboardContract.kt`, `domain/model/*.kt`,
  `feature/device_sync/*.kt`, or the generated header
  `sharedLogic/build/bin/iosSimulatorArm64/debugFramework/SharedLogic.framework/Headers/SharedLogic.h`
  (grep `swift_name("X")`).
- Primitive mapping: `Long` -> `Int64`, `Int` -> `Int32`, `Float` -> `Float`, `Double` -> `Double`,
  `Boolean` -> `Bool`. Nullable primitives are boxed: `Float?` -> `KotlinFloat?` (`.floatValue`),
  `Int?` -> `KotlinInt?`, `Boolean?` -> `KotlinBoolean?` (`.boolValue`), `Long?` -> `KotlinLong?`.
  `Pair<Int,Int>?` -> `KotlinPair<KotlinInt, KotlinInt>?` (`.first?.intValue`).
  `List<T>` -> `[T]`, `Map<K,V>` -> `[K: V]`, `String?` -> `String?`.
  Inside lambdas Kotlin primitives are boxed too (`KotlinBoolean`).
- Enums = classes with lowerCamel static members: `DashboardTab.all/.active/.completed/.torrent/
  .media/.settings`, `DownloadStatus.idle/.queued/.connecting/.downloading/.paused/.completed/.error`,
  `EngineType.httpDirect/.bittorrent/.mediaStream`, `TaskSortOption.dateDesc...`,
  `SpeedPreset.low...`. Compare with `==`; `switch` needs a `default:`. `.name`, `.ordinal` (Int32),
  `DashboardTab.entries` / `TaskSortOption.entries` give all values. Kotlin enum computed props
  (`tab.title`, `sort.label`, `engine.displayName`) work.
- Kotlin `sealed interface X` = Swift protocol `X`; its cases are classes named
  `X` + case name: `DashboardUiEventOnUrlChanged(url:)`. `data object` cases are singletons:
  `DashboardUiEventOnPauseAllClicked.shared`. Match with `if let e = x as? DashboardUiEffectShowToast`.
  Same for device sync states (`PairingState`, `SendState`, `ReceiveState`: `as?` their case classes,
  check names in the header).
- Kotlin functions on models (e.g. `task.canResume()`, `entry.isBeingInspected`) are callable.
  Extension functions in a file become `<FileName>Kt.fn(receiver, ...)`.

## Sending events (examples)
```swift
store.send(DashboardUiEventOnUrlChanged(url: text))
store.send(DashboardUiEventOnPauseClicked(taskId: task.id))
store.send(DashboardUiEventOnResumeAllClicked.shared)
store.send(DashboardUiEventOnTabSelected(tab: .completed))   // or store.selectTab(.completed)
store.send(DashboardUiEventOnSortChanged(sort: .sizeDesc))
store.send(DashboardUiEventOnConfirmRemoveClicked(deleteFile: true))
store.send(DashboardUiEventOnMonitorClipboardChanged(on: true))
store.send(DashboardUiEventOnGlobalDownloadSpeedLimitChanged(limitBytesPerSec: 1_048_576))
store.send(DashboardUiEventOnTaskPriorityChanged(taskId: id, priority: 1))  // Int -> Int32 literal ok
store.send(DashboardUiEventOnMediaQualitySelectionToggled(qualityIndex: Int32(i), isSelected: true))
```
Labels are the Kotlin parameter names. Int params need `Int32(...)` when coming from a Swift `Int`.
For text fields bind with a `Binding(get: { store.state.inputUrl }, set: { store.send(DashboardUiEventOnUrlChanged(url: $0)) })`
(state comes back synchronously on the main thread).

## Strings
`store.strings.homeTabAll`, `store.strings.browserMissingMessage(name: x)`, ... (all members of
`core/i18n/data_provider/AppLocalization.kt`; same names as Compose's `LocalizationManager.Strings.x`).
Never hard-code English UI text that has a key. Root re-creates the view tree when the language
changes (`.id(store.language)`), and sets `layoutDirection` for ar / fa.

## Theme (`SwiftUI/Core/FJTheme.swift`)
- `FJTheme.color.X` = dynamic colors (follow light / dark automatically), same names as the Compose
  Material scheme: `primary` (dark: neon cyan #00E5FF, light: cobalt #0284C7), `onPrimary`,
  `primaryContainer`, `onPrimaryContainer`, `secondary`, `onSecondary`, `secondaryContainer`,
  `onSecondaryContainer`, `tertiary`, `background`, `surface`, `surfaceVariant`, `onBackground`,
  `onSurface`, `onSurfaceVariant`, `outline`, `outlineVariant`, `error`, `onError`,
  `errorContainer`, `onErrorContainer`; plus fixed `engineTorrent`, `engineMedia`,
  `statusCompleted`, `statusPaused`, `statusConnecting`, `statusError`.
- `FJTheme.font.largeTitle/title/headline/body/callout/caption/small/mono`,
  `FJTheme.corner` (14), `FJTheme.cornerSmall` (8).
- The theme mode (`state.themeMode` = "system"/"light"/"dark") is applied by FJRoot on the window
  (`overrideUserInterfaceStyle`); just use FJTheme colors. Change it with
  `DashboardUiEventOnThemeModeChanged(mode:)`.
- Lists / Forms: use `.scrollContentBackground(.hidden)` + `FJTheme.color.background` (the
  default List background is system black/gray, not the app palette).

## Shell (`SwiftUI/Core/FJRoot.swift`), what is already done for you
- Compact width: `NavigationStack { FJListPage }` + custom bottom bar `FJBottomBar` (All, Active,
  Done, Torrent, Media, Settings with counts; a custom bar, not TabView, so 6 tabs fit without
  "More"). Regular width: `NavigationSplitView` with a sidebar of the same tabs.
- `FJListPage` shows `SettingsScreen()` when `state.selectedTab == .settings`, else
  `TaskListScreen()` with the floating "+" button (sends `OnToggleAddDialog(show: true)`). The list
  page must NOT add its own "+" FAB; it owns its header (title, total speed, pause/resume all,
  sort, search, selection mode).
- Details: set `store.selectedTaskId = task.id` -> FJRoot pushes `TaskDetailsScreen(taskId:)` via
  `navigationDestination(item:)`. Back = clear it. Details must handle the task disappearing
  (`store.task(taskId) == nil` -> show nothing / dismiss).
- `AddDownloadSheet` is presented by FJRoot as a `.sheet` while `state.isAddDialogVisible`;
  swipe-down sends `OnToggleAddDialog(show: false)`. The sheet itself sends
  `OnAddDownloadClicked` / `OnToggleAddDialog(show: false)`.
- `DeviceSendSheet(taskId:)` is presented while `state.deviceShareTaskId != nil`; dismiss sends
  `OnDismissShareSheet`. Open it from list/details with `OnShareTaskClicked(taskId:)`.
- `AppDialogsHost()` is attached once at the root (`.background`). It owns every state-driven
  dialog: remove confirmation (`removeConfirmationTaskId(s)`), duplicate (`duplicateTaskAlert`),
  file conflict (`fileConflictAlert`), clipboard offer (`clipboardOfferUrl`), browser fallback
  (`browserFallbackUrl`), pairing (`devicePairing`), receive (`deviceReceiving`), missing file
  (`store.missingFileTaskId`). Caveat: an `.alert` on the root does not show while a sheet is up;
  dialogs that can fire during the Add sheet (duplicate, file conflict) must also be presentable
  from inside `AddDownloadSheet` (dialogs agent: expose e.g. `AppDialogsHost(insideSheet: true)`
  or modifiers the Add sheet attaches; coordinate so each dialog shows once).
- Toasts: `store.showToast(text)`; Kotlin ShowToast effects already appear.

## Current numbers
Foundation build (placeholders): phys footprint ~81-92 MB on the iPhone 17 simulator (was
95-107 MB with Compose); no Skia / Compose in the binary.
