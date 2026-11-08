package com.michelle.raouf.fetch_jet.core.notification

/** A one-off system notification (not a download's progress), e.g. a download sent / received. */
expect object SystemNotice {
    fun show(id: String, title: String, text: String)
}
