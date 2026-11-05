package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.core.power.SystemPower
import com.michelle.raouf.fetch_jet.core.power.WhenDone
import com.michelle.raouf.fetch_jet.core.power.WhenDoneAction
import com.tbib.kmp_localization.LocalizationManager

fun whenDoneLabel(action: WhenDoneAction): String = when (action) {
    WhenDoneAction.NOTHING -> LocalizationManager.Strings.whenDoneNothing
    WhenDoneAction.EXIT -> LocalizationManager.Strings.whenDoneExit
    WhenDoneAction.SLEEP -> LocalizationManager.Strings.whenDoneSleep
    WhenDoneAction.SHUT_DOWN -> LocalizationManager.Strings.whenDoneShutDown
}

/** Settings > General (desktop): what to do when all downloads finish. */
@Composable
fun WhenDoneSettingsCard() {
    if (!SystemPower.isSupported) return
    val action by WhenDone.action.collectAsState()
    SettingsGroup(
        title = LocalizationManager.Strings.whenDoneGroup,
        footer = LocalizationManager.Strings.whenDoneHint
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SegmentedPicker(
                options = WhenDoneAction.entries,
                selected = action,
                label = { whenDoneLabel(it) },
                onSelect = { WhenDone.choose(it) }
            )
        }
    }
}

/** Above the download list while an action is chosen, so it is never forgotten. */
@Composable
internal fun WhenDoneBanner() {
    val action by WhenDone.action.collectAsState()
    if (action == WhenDoneAction.NOTHING) return
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                LocalizationManager.Strings.whenDoneBanner(whenDoneLabel(action)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { WhenDone.cancel() }) {
                Text(LocalizationManager.Strings.cancel)
            }
        }
    }
}

/** The countdown (Cancel / Now) and, when the command fails, its error. */
@Composable
fun WhenDoneDialogHost() {
    val secondsLeft by WhenDone.secondsLeft.collectAsState()
    val pending by WhenDone.pending.collectAsState()
    val error by WhenDone.error.collectAsState()

    secondsLeft?.let { seconds ->
        val text = when (pending) {
            WhenDoneAction.SLEEP -> LocalizationManager.Strings.whenDoneSleepIn(seconds)
            WhenDoneAction.SHUT_DOWN -> LocalizationManager.Strings.whenDoneShutDownIn(seconds)
            else -> LocalizationManager.Strings.whenDoneExitIn(seconds)
        }
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    LocalizationManager.Strings.whenDoneTitle,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { seconds / WhenDone.COUNTDOWN_SECONDS.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { WhenDone.cancel() }, shape = RoundedCornerShape(10.dp)) {
                    Text(LocalizationManager.Strings.cancel)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { WhenDone.runNow() }, shape = RoundedCornerShape(10.dp)) {
                    Text(LocalizationManager.Strings.whenDoneNow)
                }
            }
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { WhenDone.dismissError() },
            shape = RoundedCornerShape(18.dp),
            title = {
                Text(
                    LocalizationManager.Strings.whenDoneGroup,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(LocalizationManager.Strings.whenDoneFailed(message)) },
            confirmButton = {
                Button(onClick = { WhenDone.dismissError() }, shape = RoundedCornerShape(10.dp)) {
                    Text(LocalizationManager.Strings.ok)
                }
            }
        )
    }
}
