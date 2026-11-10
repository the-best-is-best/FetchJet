package com.michelle.raouf.fetch_jet.nativeapp.actions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface UiAction {

    @Serializable
    @SerialName("SystemTheme")
    data class SystemTheme(val dark: Boolean = false) : UiAction

    @Serializable
    @SerialName("SetFilter")
    data class SetFilter(val filter: Int = 0) : UiAction

    @Serializable
    @SerialName("OpenSettings")
    data object OpenSettings : UiAction

    @Serializable
    @SerialName("SetSearch")
    data class SetSearch(val text: String = "") : UiAction

    @Serializable
    @SerialName("ToggleTurtle")
    data object ToggleTurtle : UiAction

    @Serializable
    @SerialName("PickSpeedOption")
    data class PickSpeedOption(val index: Int = 0) : UiAction

    @Serializable
    @SerialName("ToggleTheme")
    data object ToggleTheme : UiAction

    @Serializable
    @SerialName("SetSort")
    data class SetSort(val index: Int = 0) : UiAction

    @Serializable
    @SerialName("PauseAll")
    data object PauseAll : UiAction

    @Serializable
    @SerialName("ResumeAll")
    data object ResumeAll : UiAction

    @Serializable
    @SerialName("RowClick")
    data class RowClick(val id: String = "", val control: Boolean = false, val shift: Boolean = false) : UiAction

    @Serializable
    @SerialName("RowCheck")
    data class RowCheck(val id: String = "", val checked: Boolean = false) : UiAction

    @Serializable
    @SerialName("RowQuickAction")
    data class RowQuickAction(val id: String = "") : UiAction

    @Serializable
    @SerialName("CyclePriority")
    data class CyclePriority(val id: String = "") : UiAction

    @Serializable
    @SerialName("CheckAll")
    data class CheckAll(val checked: Boolean = false) : UiAction

    @Serializable
    @SerialName("ClearChecks")
    data object ClearChecks : UiAction

    @Serializable
    @SerialName("RemoveChecked")
    data object RemoveChecked : UiAction

    @Serializable
    @SerialName("ExportChecked")
    data object ExportChecked : UiAction

    @Serializable
    @SerialName("ToggleGroup")
    data class ToggleGroup(val key: String = "") : UiAction

    @Serializable
    @SerialName("ListKey")
    data class ListKey(val key: Int = 0) : UiAction

    @Serializable
    @SerialName("CloseDetails")
    data object CloseDetails : UiAction

    @Serializable
    @SerialName("CancelWhenDone")
    data object CancelWhenDone : UiAction

    @Serializable
    @SerialName("SetWhenDone")
    data class SetWhenDone(val index: Int = 0) : UiAction

    @Serializable
    @SerialName("SetUrl")
    data class SetUrl(val text: String = "") : UiAction

    @Serializable
    @SerialName("ShowAddDialog")
    data class ShowAddDialog(val show: Boolean = false) : UiAction

    @Serializable
    @SerialName("StartDownload")
    data object StartDownload : UiAction

    @Serializable
    @SerialName("SetDestination")
    data class SetDestination(val path: String = "") : UiAction

    @Serializable
    @SerialName("SetFileName")
    data class SetFileName(val name: String = "") : UiAction

    @Serializable
    @SerialName("Pause")
    data class Pause(val id: String = "") : UiAction

    @Serializable
    @SerialName("Resume")
    data class Resume(val id: String = "") : UiAction

    @Serializable
    @SerialName("Remove")
    data class Remove(val id: String = "") : UiAction

    @Serializable
    @SerialName("ConfirmRemove")
    data class ConfirmRemove(val deleteFiles: Boolean = false) : UiAction

    @Serializable
    @SerialName("DismissRemove")
    data object DismissRemove : UiAction

    @Serializable
    @SerialName("SetLanguage")
    data class SetLanguage(val choice: String = "") : UiAction

    @Serializable
    @SerialName("DetailsAction")
    data class DetailsAction(val code: Int = 0, val primary: Boolean = false) : UiAction

    @Serializable
    @SerialName("DetailsRemove")
    data object DetailsRemove : UiAction

    @Serializable
    @SerialName("DetailsShare")
    data object DetailsShare : UiAction

    @Serializable
    @SerialName("ToggleDetailFile")
    data class ToggleDetailFile(val index: Int = 0, val selected: Boolean = false) : UiAction

    @Serializable
    @SerialName("CycleDetailFilePriority")
    data class CycleDetailFilePriority(val index: Int = 0) : UiAction

    @Serializable
    @SerialName("CopyText")
    data class CopyText(val text: String = "") : UiAction

    @Serializable
    @SerialName("AlertConfirm")
    data class AlertConfirm(val kind: Int = 0, val checked: Boolean = false) : UiAction

    @Serializable
    @SerialName("AlertDismiss")
    data class AlertDismiss(val kind: Int = 0, val fromScrim: Boolean = false) : UiAction

    @Serializable
    @SerialName("AlertExtra")
    data class AlertExtra(val kind: Int = 0) : UiAction

    @Serializable
    @SerialName("SetSettingsSection")
    data class SetSettingsSection(val index: Int = 0) : UiAction

    @Serializable
    @SerialName("SettingToggle")
    data class SettingToggle(val id: String = "", val on: Boolean = false) : UiAction

    @Serializable
    @SerialName("SettingPick")
    data class SettingPick(val id: String = "", val index: Int = 0) : UiAction

    @Serializable
    @SerialName("SettingText")
    data class SettingText(val id: String = "", val text: String = "") : UiAction

    @Serializable
    @SerialName("SettingClick")
    data class SettingClick(val id: String = "") : UiAction

    @Serializable
    @SerialName("UpdateConfirm")
    data object UpdateConfirm : UiAction

    @Serializable
    @SerialName("UpdateSkip")
    data object UpdateSkip : UiAction

    @Serializable
    @SerialName("UpdateLater")
    data class UpdateLater(val fromScrim: Boolean = false) : UiAction

    @Serializable
    @SerialName("ToggleLink")
    data class ToggleLink(val index: Int = 0, val selected: Boolean = false) : UiAction

    @Serializable
    @SerialName("SelectAllLinks")
    data class SelectAllLinks(val selected: Boolean = false) : UiAction

    @Serializable
    @SerialName("SetLinkFolder")
    data class SetLinkFolder(val name: String = "") : UiAction

    @Serializable
    @SerialName("SetLinkQuality")
    data class SetLinkQuality(val index: Int = 0, val quality: Int = 0) : UiAction

    @Serializable
    @SerialName("RetryLinkInspect")
    data class RetryLinkInspect(val index: Int = 0) : UiAction

    @Serializable
    @SerialName("DeviceSelect")
    data class DeviceSelect(val id: String = "") : UiAction

    @Serializable
    @SerialName("DeviceSetMode")
    data class DeviceSetMode(val transfer: Boolean = false) : UiAction

    @Serializable
    @SerialName("DeviceSetDeleteAfter")
    data class DeviceSetDeleteAfter(val on: Boolean = false) : UiAction

    @Serializable
    @SerialName("DeviceSearch")
    data class DeviceSearch(val text: String = "") : UiAction

    @Serializable
    @SerialName("DeviceToggleManual")
    data object DeviceToggleManual : UiAction

    @Serializable
    @SerialName("DeviceManualIp")
    data class DeviceManualIp(val text: String = "") : UiAction

    @Serializable
    @SerialName("DeviceManualConnect")
    data object DeviceManualConnect : UiAction

    @Serializable
    @SerialName("DeviceSend")
    data object DeviceSend : UiAction

    @Serializable
    @SerialName("DeviceDismiss")
    data object DeviceDismiss : UiAction

    @Serializable
    @SerialName("PairingConfirm")
    data object PairingConfirm : UiAction

    @Serializable
    @SerialName("PairingCancel")
    data object PairingCancel : UiAction

    @Serializable
    @SerialName("PairingBlock")
    data object PairingBlock : UiAction

    @Serializable
    @SerialName("InspectTorrent")
    data object InspectTorrent : UiAction

    @Serializable
    @SerialName("ToggleTorrentFile")
    data class ToggleTorrentFile(val index: Int = 0, val selected: Boolean = false) : UiAction

    @Serializable
    @SerialName("CycleTorrentFilePriority")
    data class CycleTorrentFilePriority(val index: Int = 0) : UiAction

    @Serializable
    @SerialName("SelectAllTorrentFiles")
    data class SelectAllTorrentFiles(val selected: Boolean = false) : UiAction

    @Serializable
    @SerialName("ToggleLinkTorrentFile")
    data class ToggleLinkTorrentFile(val link: Int = 0, val index: Int = 0, val selected: Boolean = false) : UiAction

    @Serializable
    @SerialName("CycleLinkTorrentFilePriority")
    data class CycleLinkTorrentFilePriority(val link: Int = 0, val index: Int = 0) : UiAction

    @Serializable
    @SerialName("SelectAllLinkTorrentFiles")
    data class SelectAllLinkTorrentFiles(val link: Int = 0, val selected: Boolean = false) : UiAction

    @Serializable
    @SerialName("PasteUrl")
    data object PasteUrl : UiAction

    @Serializable
    @SerialName("ImportLinksFile")
    data object ImportLinksFile : UiAction

    @Serializable
    @SerialName("OpenTorrentFile")
    data object OpenTorrentFile : UiAction

    @Serializable
    @SerialName("SetEngine")
    data class SetEngine(val engine: Int = 0) : UiAction

    @Serializable
    @SerialName("ScheduleQuick")
    data class ScheduleQuick(val minutes: Int = 0) : UiAction

    @Serializable
    @SerialName("ScheduleAtTime")
    data object ScheduleAtTime : UiAction

    @Serializable
    @SerialName("SetScheduleDay")
    data class SetScheduleDay(val offset: Int = 0) : UiAction

    @Serializable
    @SerialName("SetScheduleHour")
    data class SetScheduleHour(val text: String = "") : UiAction

    @Serializable
    @SerialName("SetScheduleMinute")
    data class SetScheduleMinute(val text: String = "") : UiAction

    @Serializable
    @SerialName("SetSchedulePm")
    data class SetSchedulePm(val pm: Boolean = false) : UiAction

    @Serializable
    @SerialName("ApplyScheduleTime")
    data object ApplyScheduleTime : UiAction

    @Serializable
    @SerialName("SetConnections")
    data class SetConnections(val count: Int = 0) : UiAction

    @Serializable
    @SerialName("ResetFileName")
    data object ResetFileName : UiAction

    @Serializable
    @SerialName("SetChecksum")
    data class SetChecksum(val value: String = "") : UiAction

    @Serializable
    @SerialName("PickDestination")
    data object PickDestination : UiAction

    @Serializable
    @SerialName("SetQuickFolder")
    data class SetQuickFolder(val path: String = "") : UiAction

    @Serializable
    @SerialName("SetLeech")
    data class SetLeech(val on: Boolean = false) : UiAction

    @Serializable
    @SerialName("RetryInspect")
    data object RetryInspect : UiAction

    @Serializable
    @SerialName("InspectMedia")
    data object InspectMedia : UiAction

    @Serializable
    @SerialName("DownloadTools")
    data object DownloadTools : UiAction

    @Serializable
    @SerialName("ToggleQuality")
    data class ToggleQuality(val index: Int = 0, val selected: Boolean = false) : UiAction

    @Serializable
    @SerialName("SelectAllQualities")
    data class SelectAllQualities(val selected: Boolean = false) : UiAction

    @Serializable
    @SerialName("ToggleSubtitle")
    data class ToggleSubtitle(val code: String = "", val selected: Boolean = false) : UiAction

    @Serializable
    @SerialName("SetEmbedSubtitles")
    data class SetEmbedSubtitles(val embed: Boolean = false) : UiAction

    @Serializable
    @SerialName("SetClipEnabled")
    data class SetClipEnabled(val on: Boolean = false) : UiAction

    @Serializable
    @SerialName("SetClipRange")
    data class SetClipRange(val start: String = "", val end: String = "") : UiAction

    @Serializable
    @SerialName("SetClipFastMode")
    data class SetClipFastMode(val on: Boolean = false) : UiAction

    @Serializable
    @SerialName("OpenClipPicker")
    data class OpenClipPicker(val end: Boolean = false) : UiAction

    @Serializable
    @SerialName("ClipPickerType")
    data class ClipPickerType(val field: Int = 0, val text: String = "") : UiAction

    @Serializable
    @SerialName("ClipPickerStep")
    data class ClipPickerStep(val field: Int = 0, val up: Boolean = false) : UiAction

    @Serializable
    @SerialName("ClipPickerQuick")
    data class ClipPickerQuick(val code: Int = 0) : UiAction

    @Serializable
    @SerialName("ClipPickerApply")
    data object ClipPickerApply : UiAction

    @Serializable
    @SerialName("ClipPickerClose")
    data object ClipPickerClose : UiAction

    @Serializable
    @SerialName("SetAudioFormat")
    data class SetAudioFormat(val format: String = "") : UiAction

    @Serializable
    @SerialName("TogglePlaylistEntry")
    data class TogglePlaylistEntry(val index: Int = 0, val selected: Boolean = false) : UiAction

    @Serializable
    @SerialName("SelectAllPlaylist")
    data class SelectAllPlaylist(val selected: Boolean = false) : UiAction

    @Serializable
    @SerialName("SetPlaylistFolder")
    data class SetPlaylistFolder(val name: String = "") : UiAction

    @Serializable
    @SerialName("SetPlaylistQuality")
    data class SetPlaylistQuality(val quality: Int = 0) : UiAction

    @Serializable
    @SerialName("UseSingleVideo")
    data object UseSingleVideo : UiAction

    @Serializable
    @SerialName("ChoicePrimary")
    data class ChoicePrimary(val kind: Int = 0) : UiAction

    @Serializable
    @SerialName("ChoiceSecondary")
    data class ChoiceSecondary(val kind: Int = 0) : UiAction

    @Serializable
    @SerialName("ChoiceDismiss")
    data class ChoiceDismiss(val kind: Int = 0) : UiAction
}
