package com.michelle.raouf.fetch_jet

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController(onDarkModeChanged: ((Boolean) -> Unit)? = null) = ComposeUIViewController(
    configure = {
        onFocusBehavior = OnFocusBehavior.DoNothing
    }
) {
    App(onDarkModeChanged = onDarkModeChanged)
}