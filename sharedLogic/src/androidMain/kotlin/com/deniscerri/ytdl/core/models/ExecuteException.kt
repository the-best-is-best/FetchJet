/*
 * This file is derived from YTDLnis (https://github.com/deniscerri/ytdlnis),
 * Copyright (C) Denis Çerri and contributors, licensed under the GNU General Public License v3.0.
 * Modified for FetchJet (Copyright (C) 2026 Michelle Raouf). FetchJet as a whole is distributed
 * under the GNU General Public License v3.0 or later; see the LICENSE file in the repository root.
 */
package com.deniscerri.ytdl.core.models

class ExecuteException : Exception {
    constructor(message: String?) : super(message)
    constructor(message: String?, e: Throwable?) : super(message, e)
    constructor(e: Throwable?) : super(e)
}