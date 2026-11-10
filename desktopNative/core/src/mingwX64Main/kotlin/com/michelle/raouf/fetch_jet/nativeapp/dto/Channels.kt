package com.michelle.raouf.fetch_jet.nativeapp.dto

enum class ChannelKind { STRINGS, STRUCT, LIST, QUEUE }

class ChannelSpec(val id: Int, val name: String, val kind: ChannelKind, val dto: String)

object Channels {
    val TR = ChannelSpec(0, "tr", ChannelKind.STRINGS, "")
    val SHELL = ChannelSpec(1, "shell", ChannelKind.STRUCT, "ShellDto")
    val CHROME = ChannelSpec(2, "chrome", ChannelKind.STRUCT, "ChromeDto")
    val STATUS = ChannelSpec(3, "status", ChannelKind.STRUCT, "StatusDto")
    val TASKS = ChannelSpec(4, "tasks", ChannelKind.LIST, "TaskRowDto")
    val EFFECTS = ChannelSpec(5, "effects", ChannelKind.QUEUE, "EffectDto")
    val DETAILS = ChannelSpec(6, "details", ChannelKind.STRUCT, "DetailsDto")
    val DETAIL_PIECES = ChannelSpec(7, "detailPieces", ChannelKind.LIST, "PieceDto")
    val DETAIL_CONNECTIONS = ChannelSpec(8, "detailConnections", ChannelKind.LIST, "ConnectionDto")
    val DETAIL_FILES = ChannelSpec(9, "detailFiles", ChannelKind.LIST, "FileRowDto")
    val ALERTS = ChannelSpec(10, "alerts", ChannelKind.LIST, "AlertDto")
    val ADD = ChannelSpec(11, "add", ChannelKind.STRUCT, "AddDto")
    val ADD_MEDIA = ChannelSpec(12, "addMedia", ChannelKind.STRUCT, "AddMediaDto")
    val ADD_QUALITIES = ChannelSpec(13, "addQualities", ChannelKind.LIST, "QualityRowDto")
    val ADD_SUBTITLES = ChannelSpec(14, "addSubtitles", ChannelKind.LIST, "SubtitleRowDto")
    val ADD_PLAYLIST = ChannelSpec(15, "addPlaylist", ChannelKind.LIST, "PlaylistRowDto")
    val CLIP_PICKER = ChannelSpec(16, "clipPicker", ChannelKind.STRUCT, "ClipPickerDto")
    val CHOICES = ChannelSpec(17, "choices", ChannelKind.LIST, "ChoiceDialogDto")

    val SETTINGS_NAV = ChannelSpec(18, "settingsNav", ChannelKind.STRUCT, "SettingsNavDto")
    val SETTINGS_ROWS = ChannelSpec(19, "settingsRows", ChannelKind.LIST, "SettingRowDto")

    val UPDATE_DIALOG = ChannelSpec(20, "updateDialog", ChannelKind.STRUCT, "UpdateDialogDto")

    val ADD_LINKS = ChannelSpec(21, "addLinks", ChannelKind.LIST, "LinkRowDto")

    val DEVICE_SHEET = ChannelSpec(22, "deviceSheet", ChannelKind.STRUCT, "DeviceSheetDto")
    val DEVICE_ROWS = ChannelSpec(23, "deviceRows", ChannelKind.LIST, "DeviceRowDto")
    val DEVICE_PAIRING = ChannelSpec(24, "devicePairing", ChannelKind.STRUCT, "DevicePairingDto")

    val ADD_TORRENT = ChannelSpec(25, "addTorrent", ChannelKind.STRUCT, "AddTorrentDto")
    val ADD_TORRENT_FILES = ChannelSpec(26, "addTorrentFiles", ChannelKind.LIST, "TorrentFileRowDto")

    const val COUNT = 27
    const val FULL_FLAG = 0x100
}
