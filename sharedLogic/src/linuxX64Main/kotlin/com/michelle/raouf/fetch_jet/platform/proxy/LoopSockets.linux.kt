package com.michelle.raouf.fetch_jet.platform.proxy

import com.michelle.raouf.fetch_jet.platform.linux.linuxLoopSockets

actual fun loopSockets(): LoopSockets = linuxLoopSockets()
