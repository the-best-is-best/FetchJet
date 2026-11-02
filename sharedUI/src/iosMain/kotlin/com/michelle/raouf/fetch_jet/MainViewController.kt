package com.michelle.raouf.fetch_jet

import com.michelle.raouf.fetch_jet.core.i18n.start.start
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController(onDarkModeChanged: ((Boolean) -> Unit)? = null) = ComposeUIViewController(
    configure = {
        com.tbib.kmp_localization.LocalizationManager.start()
        onFocusBehavior = OnFocusBehavior.DoNothing
    }
) {
    App(onDarkModeChanged = onDarkModeChanged)
}