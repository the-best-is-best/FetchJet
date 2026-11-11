package com.michelle.raouf.fetch_jet.platform.proxy

import com.michelle.raouf.fetch_jet.platform.macos.macLoopSockets

actual fun loopSockets(): LoopSockets = macLoopSockets()
