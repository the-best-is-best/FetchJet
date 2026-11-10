package com.michelle.raouf.fetch_jet.platform.proxy

import com.michelle.raouf.fetch_jet.platform.win.winLoopSockets

actual fun loopSockets(): LoopSockets = winLoopSockets()
