package com.michelle.raouf.fetch_jet.feature.hidden

import com.michelle.raouf.fetch_jet.feature.device_sync.SyncLock
import com.michelle.raouf.fetch_jet.feature.device_sync.WinSyncLock

internal actual fun newStoreLock(): SyncLock = WinSyncLock()
