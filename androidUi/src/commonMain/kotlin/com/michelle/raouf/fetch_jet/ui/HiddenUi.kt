package com.michelle.raouf.fetch_jet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.michelle.raouf.fetch_jet.HiddenWorkBanner
import com.michelle.raouf.fetch_jet.SelectionBar
import com.michelle.raouf.fetch_jet.TaskDetailsPane
import com.michelle.raouf.fetch_jet.core.i18n.data_provider.Strings
import com.michelle.raouf.fetch_jet.domain.model.canResume
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenLockType
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenOpening
import com.michelle.raouf.fetch_jet.feature.hidden.HiddenStore
import com.michelle.raouf.fetch_jet.feature.row_menu.RowMenuOptions
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiEvent
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardUiState
import com.michelle.raouf.fetch_jet.presentation.dashboard.DashboardViewModel
import com.michelle.raouf.fetch_jet.presentation.dashboard.HiddenError
import com.michelle.raouf.fetch_jet.presentation.dashboard.HiddenLockTexts
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenLockTexts
import com.michelle.raouf.fetch_jet.presentation.dashboard.TaskListItem
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenItems
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenLockedText
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenOpeningText
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenWrongPinText
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenOpenFolder
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenOpenPlaylist
import com.michelle.raouf.fetch_jet.presentation.dashboard.hiddenTaskIdsOfRows
import com.tbib.kmp_localization.LocalizationManager
import kotlinx.coroutines.delay

@Composable
internal expect fun SecureWindowEffect(enabled: Boolean)

internal object HiddenFlow {
    var playerOut = false
    var leftAt: kotlin.time.TimeSource.Monotonic.ValueTimeMark? = null
}

@Composable
internal fun HiddenNoticeDialog(viewModel: DashboardViewModel) {
    val s = LocalizationManager.Strings
    val dismiss: () -> Unit = { viewModel.onEvent(DashboardUiEvent.OnHiddenEncryptNoticeDismissed) }
    AlertDialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(s.hiddenNoticeTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = { Text(s.hiddenNoticeBody, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(onClick = {
                viewModel.onEvent(DashboardUiEvent.OnHiddenEncryptNoticeAccepted)
            }) { Text(s.hiddenNoticeAccept) }
        },
        dismissButton = { OutlinedButton(onClick = dismiss) { Text(s.cancel) } }
    )
}

@Composable
internal fun HiddenOpeningDialog(opening: HiddenOpening) {
    val s = LocalizationManager.Strings
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, securePolicy = SecureFlagPolicy.SecureOn)
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(20.dp).width(240.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    hiddenOpeningText(opening.fraction),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                androidx.compose.material3.LinearProgressIndicator(progress = { opening.fraction }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

internal class PinResult(val error: HiddenError?, val triesLeft: Int, val lockedSeconds: Int)

internal fun DashboardViewModel.hiddenResult(): PinResult {
    val h = uiState.value.hidden
    return PinResult(h.error, h.triesLeft, h.lockedSeconds)
}

private fun pinText(error: HiddenError, triesLeft: Int, lockedSeconds: Int, invalid: String): String =
    when (error) {
        HiddenError.WRONG_PIN -> hiddenWrongPinText(triesLeft)
        HiddenError.LOCKED -> hiddenLockedText(lockedSeconds)
        HiddenError.INVALID_PIN -> invalid
    }

internal class SecretField(val label: String, val type: HiddenLockType)

private fun HiddenLockType.other(): HiddenLockType =
    if (this == HiddenLockType.PIN) HiddenLockType.PATTERN else HiddenLockType.PIN

private fun DashboardUiState.storedLockType(): HiddenLockType = hidden.lockType ?: HiddenLockType.PIN

@Composable
private fun LockTypeSwitch(type: HiddenLockType, onChange: (HiddenLockType) -> Unit) {
    TextButton(onClick = { onChange(type.other()) }) { Text(hiddenLockTexts(type).switchTo) }
}

@Composable
internal fun PinDialog(
    title: String,
    hint: String? = null,
    fields: List<SecretField>,
    confirmLabel: String,
    checkLocal: (List<String>) -> String? = { null },
    submit: (List<String>) -> PinResult,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    footer: @Composable () -> Unit = {}
) {
    val s = LocalizationManager.Strings
    val types = fields.map { it.type }
    val values = remember(types) { mutableStateListOf<String>().also { list -> repeat(fields.size) { list.add("") } } }
    var message by remember(types) { mutableStateOf<String?>(null) }
    var lockLeft by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val firstPin = types.indexOf(HiddenLockType.PIN)
    val hasPin = firstPin >= 0
    val invalidText = hiddenLockTexts(types.last()).invalid
    LaunchedEffect(types) { if (firstPin == 0) runCatching { focus.requestFocus() } }
    LaunchedEffect(lockLeft > 0) {
        while (lockLeft > 0) {
            delay(1000)
            lockLeft -= 1
        }
    }
    val send = send@{
        if (lockLeft > 0 || values.any { it.isEmpty() }) return@send
        val local = checkLocal(values.toList())
        if (local != null) {
            message = local
            for (i in values.indices) if (types[i] == HiddenLockType.PATTERN) values[i] = ""
            return@send
        }
        val result = submit(values.toList())
        val error = result.error
        if (error == null) {
            onDone()
            return@send
        }
        for (i in values.indices) values[i] = ""
        if (firstPin == 0) runCatching { focus.requestFocus() }
        if (error == HiddenError.LOCKED) {
            lockLeft = result.lockedSeconds.coerceAtLeast(1)
            message = null
        } else {
            message = pinText(error, result.triesLeft, result.lockedSeconds, invalidText)
        }
    }
    val padIndex = types.indices.firstOrNull { types[it] == HiddenLockType.PATTERN && values[it].isEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().clearFocusOnTapOutside(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                fields.forEachIndexed { index, field ->
                    if (field.type == HiddenLockType.PIN) {
                        val last = index == types.lastIndexOf(HiddenLockType.PIN)
                        FetchJetTextField(
                            value = values[index],
                            onValueChange = { raw -> values[index] = raw.filter { it in '0'..'9' }.take(HiddenStore.PIN_MAX) },
                            label = field.label,
                            enabled = lockLeft == 0,
                            centered = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = if (last) ImeAction.Done else ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(onDone = { send() }),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = if (index == firstPin) Modifier.focusRequester(focus) else Modifier
                        )
                    } else if (index == padIndex) {
                        Text(field.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        PatternPad(
                            enabled = lockLeft == 0,
                            onDone = { drawn ->
                                if (drawn.length < HiddenStore.PATTERN_MIN) {
                                    message = hiddenLockTexts(HiddenLockType.PATTERN).invalid
                                } else {
                                    message = null
                                    values[index] = drawn
                                    if (values.none { it.isEmpty() }) send()
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
                val shown = if (lockLeft > 0) pinText(HiddenError.LOCKED, 0, lockLeft, invalidText) else message
                if (shown != null) {
                    Text(shown, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                footer()
            }
        },
        confirmButton = {
            if (hasPin) {
                Button(onClick = { send() }, enabled = lockLeft == 0 && values.none { it.isEmpty() }) { Text(confirmLabel) }
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
private fun NewPinDialog(
    title: (HiddenLockTexts) -> String,
    initialType: HiddenLockType,
    viewModel: DashboardViewModel,
    event: (String, HiddenLockType) -> DashboardUiEvent,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    footer: @Composable () -> Unit = {}
) {
    var type by remember { mutableStateOf(initialType) }
    val texts = hiddenLockTexts(type)
    PinDialog(
        title = title(texts),
        hint = texts.setHint,
        fields = listOf(SecretField(texts.enter, type), SecretField(texts.confirm, type)),
        confirmLabel = title(texts),
        checkLocal = { v ->
            when {
                !HiddenStore.isValidSecret(type, v[0]) -> texts.invalid
                v[0] != v[1] -> texts.mismatch
                else -> null
            }
        },
        submit = { v ->
            viewModel.onEvent(event(v[0], type))
            viewModel.hiddenResult()
        },
        onDismiss = onDismiss,
        onDone = onDone,
        footer = {
            LockTypeSwitch(type) { type = it }
            footer()
        }
    )
}

@Composable
private fun ChangeLockDialog(viewModel: DashboardViewModel, oldType: HiddenLockType, onClose: () -> Unit) {
    var newType by remember { mutableStateOf(oldType) }
    val oldTexts = hiddenLockTexts(oldType)
    val newTexts = hiddenLockTexts(newType)
    PinDialog(
        title = oldTexts.change,
        fields = listOf(
            SecretField(oldTexts.oldLabel, oldType),
            SecretField(newTexts.newLabel, newType),
            SecretField(newTexts.confirm, newType)
        ),
        confirmLabel = oldTexts.change,
        checkLocal = { v ->
            when {
                !HiddenStore.isValidSecret(newType, v[1]) -> newTexts.invalid
                v[1] != v[2] -> newTexts.mismatch
                else -> null
            }
        },
        submit = { v ->
            viewModel.onEvent(DashboardUiEvent.OnHiddenChangePin(v[0], v[1], newType))
            viewModel.hiddenResult()
        },
        onDismiss = onClose,
        onDone = onClose,
        footer = { LockTypeSwitch(newType) { newType = it } }
    )
}

@Composable
internal fun SetPinDialog(
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    val s = LocalizationManager.Strings
    var restoring by remember { mutableStateOf(false) }
    NewPinDialog(
        title = { it.setTitle },
        initialType = HiddenLockType.PIN,
        viewModel = viewModel,
        event = { pin, type -> DashboardUiEvent.OnHiddenSetPin(pin, type) },
        onDismiss = onDismiss,
        onDone = onDone,
        footer = {
            TextButton(onClick = { restoring = true }) { Text(s.hiddenRecoveryRestoreTitle) }
        }
    )
    if (restoring) RecoveryRestoreDialog(viewModel, onDismiss = { restoring = false })
}

@Composable
internal fun UnlockPinDialog(viewModel: DashboardViewModel, onDismiss: () -> Unit) {
    val s = LocalizationManager.Strings
    val state by viewModel.uiState.collectAsState()
    val dismiss: () -> Unit = {
        if (viewModel.uiState.value.hidden.pinResetAllowed) viewModel.onEvent(DashboardUiEvent.OnHiddenResetCancel)
        onDismiss()
    }
    val storedType = state.storedLockType()
    if (state.hidden.pinResetAllowed) {
        NewPinDialog(
            title = { it.resetTitle },
            initialType = storedType,
            viewModel = viewModel,
            event = { pin, type -> DashboardUiEvent.OnHiddenResetPin(pin, type) },
            onDismiss = dismiss,
            onDone = {}
        )
        return
    }
    val texts = hiddenLockTexts(storedType)
    PinDialog(
        title = s.hiddenTitle,
        fields = listOf(SecretField(texts.enter, storedType)),
        confirmLabel = s.hiddenTitle,
        submit = { v ->
            viewModel.onEvent(DashboardUiEvent.OnHiddenUnlock(v[0]))
            viewModel.hiddenResult()
        },
        onDismiss = dismiss,
        onDone = {},
        footer = {
            if (state.hidden.canResetPin) {
                TextButton(onClick = { viewModel.onEvent(DashboardUiEvent.OnHiddenForgotPin) }) { Text(texts.forgot) }
            }
        }
    )
}

@Composable
internal fun RecoveryRestoreDialog(viewModel: DashboardViewModel, onDismiss: () -> Unit) {
    val s = LocalizationManager.Strings
    var code by remember { mutableStateOf("") }
    val send = {
        if (code.isNotBlank()) {
            viewModel.onEvent(DashboardUiEvent.OnHiddenRestoreWithCode(code.trim()))
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(s.hiddenRecoveryRestoreTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().clearFocusOnTapOutside(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(s.hiddenRecoveryRestoreHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FetchJetTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = s.hiddenRecoveryEnter,
                    singleLine = false,
                    maxLines = 3,
                    height = 96.dp,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { send() })
                )
            }
        },
        confirmButton = { Button(onClick = { send() }, enabled = code.isNotBlank()) { Text(s.hiddenRecoveryRestoreTitle) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
internal fun RecoveryCodeDialog(code: String, onClose: () -> Unit) {
    val s = LocalizationManager.Strings
    val copySecret = rememberSecretCopier()
    val lines = remember(code) { code.split('-').chunked(4).joinToString("\n") { it.joinToString("-") } }
    AlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(s.hiddenRecoveryTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.hiddenRecoveryBody, style = MaterialTheme.typography.bodyMedium)
                Text(
                    lines,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { copySecret(code) }) { Text(s.hiddenRecoveryCopy) }
        },
        dismissButton = { OutlinedButton(onClick = onClose) { Text(s.close) } }
    )
}

@Composable
private fun HiddenGroupPageHeader(
    title: String,
    summary: String,
    path: String?,
    canPause: Boolean,
    canResume: Boolean,
    onBack: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onUnhide: () -> Unit
) {
    GroupPageHeader(title, summary, path, onBack) {
        PauseResumeAllButtons(canPause, canResume, onPauseAll, onResumeAll)
        OutlinedButton(onClick = onUnhide, shape = RoundedCornerShape(10.dp)) {
            Text(LocalizationManager.Strings.hiddenUnhide, fontSize = 12.sp)
        }
    }
}

private enum class HiddenDialog { NONE, CHANGE, REMOVE, RESTORE }

@Composable
internal fun HiddenScreen(
    state: DashboardUiState,
    viewModel: DashboardViewModel,
    onClose: () -> Unit
) {
    val s = LocalizationManager.Strings
    val openPlaylist = state.hiddenOpenPlaylist()
    val openFolder = state.hiddenOpenFolder()
    val pageTasks = openPlaylist?.tasks ?: openFolder?.tasks
    val inPage = pageTasks != null
    val tasks = pageTasks ?: state.hidden.tasks
    val items = remember(tasks, state.playlists, state.groupByFolder, inPage) {
        if (inPage) tasks.map { TaskListItem.Single(it) } else state.hiddenItems()
    }
    val rowKeys = items.map { it.key }
    var checked by remember { mutableStateOf(emptySet<String>()) }
    var detailsId by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuTaskId by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf(HiddenDialog.NONE) }
    val lockType = state.storedLockType()
    val lockTexts = hiddenLockTexts(lockType)
    SecureWindowEffect(true)
    DisposableEffect(Unit) { onDispose { viewModel.onEvent(DashboardUiEvent.OnHiddenLock) } }
    LaunchedEffect(rowKeys) {
        val ids = rowKeys.toHashSet()
        if (!ids.containsAll(checked)) checked = checked.filterTo(HashSet()) { it in ids }
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = {
            if (checked.isNotEmpty()) checked = emptySet()
            else if (inPage) viewModel.onEvent(DashboardUiEvent.OnHiddenCloseGroup)
            else onClose()
        }
    )
    val selecting = checked.isNotEmpty()
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                count = checked.size,
                allChecked = checked.size == rowKeys.size,
                onCheckAll = { all -> checked = if (all) rowKeys.toHashSet() else emptySet() },
                onClose = { checked = emptySet() },
                onUnhide = {
                    viewModel.onEvent(DashboardUiEvent.OnUnhideTasks(state.hiddenTaskIdsOfRows(checked)))
                    checked = emptySet()
                }
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onClose() }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (LocalLayoutDirection.current == LayoutDirection.Rtl) "→" else "←",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(s.hiddenTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { viewModel.onEvent(DashboardUiEvent.OnHiddenLock) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(34.dp)
                ) { Text("🔒 " + s.hiddenLock, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Text("⋮", fontSize = 18.sp) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(s.hiddenUnhideAll) },
                            enabled = state.hidden.tasks.isNotEmpty(),
                            onClick = {
                                menuOpen = false
                                viewModel.onEvent(DashboardUiEvent.OnUnhideAll)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(s.hiddenRecoveryTitle) },
                            onClick = {
                                menuOpen = false
                                viewModel.onEvent(DashboardUiEvent.OnHiddenShowRecoveryCode)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(s.hiddenRecoveryRestoreTitle) },
                            onClick = {
                                menuOpen = false
                                dialog = HiddenDialog.RESTORE
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(lockTexts.change) },
                            onClick = {
                                menuOpen = false
                                dialog = HiddenDialog.CHANGE
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(lockTexts.remove, color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                dialog = HiddenDialog.REMOVE
                            }
                        )
                    }
                }
            }
        }
        Text(
            s.hiddenNoteEncrypted,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
        if (!selecting) {
            if (openPlaylist != null) {
                HiddenGroupPageHeader(
                    title = openPlaylist.title,
                    summary = openPlaylist.summaryText,
                    path = null,
                    canPause = openPlaylist.canPause,
                    canResume = openPlaylist.canResume,
                    onBack = { viewModel.onEvent(DashboardUiEvent.OnHiddenCloseGroup) },
                    onPauseAll = { viewModel.onEvent(DashboardUiEvent.OnHiddenGroupPause(playlistId = openPlaylist.id)) },
                    onResumeAll = { viewModel.onEvent(DashboardUiEvent.OnHiddenGroupResume(playlistId = openPlaylist.id)) },
                    onUnhide = {
                        viewModel.onEvent(DashboardUiEvent.OnUnhideGroup(playlistId = openPlaylist.id))
                        viewModel.onEvent(DashboardUiEvent.OnHiddenCloseGroup)
                    }
                )
            } else if (openFolder != null) {
                HiddenGroupPageHeader(
                    title = "📁 " + openFolder.label,
                    summary = openFolder.summaryText,
                    path = openFolder.path,
                    canPause = openFolder.canPause,
                    canResume = openFolder.canResume,
                    onBack = { viewModel.onEvent(DashboardUiEvent.OnHiddenCloseGroup) },
                    onPauseAll = { viewModel.onEvent(DashboardUiEvent.OnHiddenGroupPause(folder = openFolder.folder)) },
                    onResumeAll = { viewModel.onEvent(DashboardUiEvent.OnHiddenGroupResume(folder = openFolder.folder)) },
                    onUnhide = {
                        viewModel.onEvent(DashboardUiEvent.OnUnhideGroup(folder = openFolder.folder))
                        viewModel.onEvent(DashboardUiEvent.OnHiddenCloseGroup)
                    }
                )
            }
        }
        if (state.hidden.working) HiddenWorkBanner(state.hidden)
        if (tasks.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(s.hiddenEmpty, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)
            ) {
                items(items, key = { it.key }) { item ->
                    if (item is TaskListItem.Group) {
                        val group = item.group
                        PlaylistGroupRow(
                            group = group,
                            speedDisplayUnit = state.speedDisplayUnit,
                            onClick = {
                                checked = emptySet()
                                viewModel.onEvent(DashboardUiEvent.OnHiddenOpenPlaylist(group.id))
                            },
                            onPause = { viewModel.onEvent(DashboardUiEvent.OnHiddenGroupPause(playlistId = group.id)) },
                            onResume = { viewModel.onEvent(DashboardUiEvent.OnHiddenGroupResume(playlistId = group.id)) },
                            checked = item.key in checked,
                            selectionMode = selecting,
                            onCheckedChange = { on -> checked = if (on) checked + item.key else checked - item.key },
                            onLongClick = { checked = checked + item.key }
                        )
                        return@items
                    }
                    if (item is TaskListItem.Folder) {
                        val group = item.group
                        FolderGroupRow(
                            group = group,
                            speedDisplayUnit = state.speedDisplayUnit,
                            onClick = {
                                checked = emptySet()
                                viewModel.onEvent(DashboardUiEvent.OnHiddenOpenFolder(group.folder))
                            },
                            onPause = { viewModel.onEvent(DashboardUiEvent.OnHiddenGroupPause(folder = group.folder)) },
                            onResume = { viewModel.onEvent(DashboardUiEvent.OnHiddenGroupResume(folder = group.folder)) },
                            checked = item.key in checked,
                            selectionMode = selecting,
                            onCheckedChange = { on -> checked = if (on) checked + item.key else checked - item.key },
                            onLongClick = { checked = checked + item.key }
                        )
                        return@items
                    }
                    val task = (item as TaskListItem.Single).task
                    TaskRow(
                        task = task,
                        selected = false,
                        speedDisplayUnit = state.speedDisplayUnit,
                        onClick = { if (selecting) checked = if (task.id in checked) checked - task.id else checked + task.id else detailsId = task.id },
                        checked = task.id in checked,
                        selectionMode = selecting,
                        onCheckedChange = { on -> checked = if (on) checked + task.id else checked - task.id },
                        onLongClick = { if (selecting) checked = checked + task.id else menuTaskId = task.id },
                        rowMenu = {
                            TaskRowMenu(
                                task = task,
                                expanded = menuTaskId == task.id,
                                viewModel = viewModel,
                                options = RowMenuOptions(selectEntry = true),
                                onDismiss = { menuTaskId = null },
                                onPicked = { menuTaskId = null },
                                onOpen = { viewModel.onEvent(DashboardUiEvent.OnHiddenOpenFile(task.id)) },
                                onOpenFolder = {},
                                onSelect = { checked = checked + task.id },
                                onDetails = { detailsId = task.id }
                            )
                        },
                        onQuickAction = { action ->
                            when (action) {
                                RowAction.PAUSE -> if (task.canResume() == false) detailsId = task.id else viewModel.onEvent(DashboardUiEvent.OnPauseClicked(task.id))
                                RowAction.RESUME, RowAction.START_NOW -> viewModel.onEvent(DashboardUiEvent.OnResumeClicked(task.id))
                                RowAction.OPEN -> viewModel.onEvent(DashboardUiEvent.OnHiddenOpenFile(task.id))
                                RowAction.NONE -> {}
                            }
                        },
                        onPriorityChanged = { priority -> viewModel.onEvent(DashboardUiEvent.OnTaskPriorityChanged(task.id, priority)) }
                    )
                }
            }
        }
    }
    if (!selecting) {
        AddFab(onClick = { viewModel.onEvent(DashboardUiEvent.OnHiddenAddDownload) }, modifier = Modifier.align(Alignment.BottomEnd))
    }
    }
    val detailsTask = state.hidden.tasks.firstOrNull { it.id == detailsId }
    if (detailsTask != null) {
        Dialog(
            onDismissRequest = { detailsId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)
        ) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                TaskDetailsPane(
                    task = detailsTask,
                    viewModel = viewModel,
                    speedDisplayUnit = state.speedDisplayUnit,
                    fullScreen = true,
                    onClose = { detailsId = null },
                    compressedCopies = state.audioCopyCounts[detailsTask.id] ?: 0
                )
            }
        }
    }
    when (dialog) {
        HiddenDialog.CHANGE -> ChangeLockDialog(viewModel, lockType, onClose = { dialog = HiddenDialog.NONE })

        HiddenDialog.REMOVE -> PinDialog(
            title = lockTexts.remove,
            hint = lockTexts.removeWarn,
            fields = listOf(SecretField(lockTexts.enter, lockType)),
            confirmLabel = lockTexts.remove,
            submit = { v ->
                viewModel.onEvent(DashboardUiEvent.OnHiddenRemoveLock(v[0]))
                viewModel.hiddenResult()
            },
            onDismiss = { dialog = HiddenDialog.NONE },
            onDone = { dialog = HiddenDialog.NONE }
        )

        HiddenDialog.RESTORE -> RecoveryRestoreDialog(viewModel, onDismiss = { dialog = HiddenDialog.NONE })

        HiddenDialog.NONE -> {}
    }
}
