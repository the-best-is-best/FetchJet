package com.michelle.raouf.fetch_jet

import com.michelle.raouf.fetch_jet.core.i18n.start.start
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController

/**
 * [onForcedDarkChanged]: the user's explicit Light (false) / Dark (true), or null for System, in
 * which case the host must not force a color scheme (that would freeze the system appearance).
 */
fun MainViewController(onForcedDarkChanged: ((Boolean?) -> Unit)? = null) = ComposeUIViewController(
    configure = {
        com.tbib.kmp_localization.LocalizationManager.start()
        onFocusBehavior = OnFocusBehavior.DoNothing
    }
) {
    App(onForcedDarkChanged = onForcedDarkChanged)
}