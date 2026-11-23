package com.michelle.raouf.fetch_jet.feature.hidden

import com.michelle.raouf.fetch_jet.feature.device_sync.JvmSyncLock
import com.michelle.raouf.fetch_jet.feature.device_sync.SyncLock

internal actual fun newStoreLock(): SyncLock = JvmSyncLock()
