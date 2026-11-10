package com.michelle.raouf.fetch_jet.core.filesystem

import com.michelle.raouf.fetch_jet.platform.desktop.DesktopFileSystem
import okio.FileSystem

actual val AppFileSystem: FileSystem = DesktopFileSystem
