package com.michelle.raouf.fetch_jet.core.setup

/**
 * Windows rules shared by the JVM and the native app (the registry access is per platform).
 * Windows never lets an app set itself as the default: FetchJet registers itself as a candidate
 * (ProgIds + Capabilities + RegisteredApplications under HKCU, like the MSI) and opens the
 * Default apps page, where the user picks it.
 */
object WindowsDefaultApps {
    enum class Root { CURRENT_USER, LOCAL_MACHINE }

    class Value(val path: String, val name: String?, val data: String)

    const val APP_NAME = "FetchJet"
    const val TORRENT_PROGID = "FetchJet.Torrent"
    const val MAGNET_PROGID = "FetchJet.Magnet"
    const val CAPABILITIES = "Software\\Michelle Raouf\\FetchJet\\Capabilities"
    private const val STORE_PACKAGE_NAME = "MichelleRaouf.FetchJet"
    private const val STORE_APP_ID = "FetchJet"
    private const val TORRENT_CHOICE = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.torrent\\UserChoice"
    private const val MAGNET_CHOICE = "Software\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\magnet\\UserChoice"

    fun isDefault(read: (Root, String, String?) -> String?): Boolean? {
        val torrent = read(Root.CURRENT_USER, TORRENT_CHOICE, "ProgId")
            ?: readClass(read, ".torrent", null)
        val magnet = read(Root.CURRENT_USER, MAGNET_CHOICE, "ProgId")
            ?: if (readClass(read, "magnet", "URL Protocol") != null) "magnet" else null
        if (torrent == null && magnet == null) return null
        return torrent != null && magnet != null && isOurs(read, torrent) && isOurs(read, magnet)
    }

    private fun readClass(read: (Root, String, String?) -> String?, key: String, name: String?): String? =
        read(Root.CURRENT_USER, "Software\\Classes\\$key", name)
            ?: read(Root.LOCAL_MACHINE, "Software\\Classes\\$key", name)

    private fun isOurs(read: (Root, String, String?) -> String?, progId: String): Boolean {
        if (progId.equals(TORRENT_PROGID, true) || progId.equals(MAGNET_PROGID, true)) return true
        val command = readClass(read, "$progId\\shell\\open\\command", null)
        if (command != null && command.contains("fetchjet.exe", ignoreCase = true)) return true
        val aumid = readClass(read, "$progId\\Application", "AppUserModelId")
        return aumid != null && aumid.startsWith("${STORE_PACKAGE_NAME}_", ignoreCase = true)
    }

    /** What the installed (MSI / portable) app writes before it opens the Default apps page. */
    fun registration(exe: String, magnetKeyMissing: Boolean): List<Value> {
        val icon = "\"$exe\",0"
        val command = "\"$exe\" \"%1\""
        val classes = "Software\\Classes"
        return buildList {
            add(Value("$classes\\$TORRENT_PROGID", null, "BitTorrent file"))
            add(Value("$classes\\$TORRENT_PROGID\\DefaultIcon", null, icon))
            add(Value("$classes\\$TORRENT_PROGID\\shell\\open\\command", null, command))
            add(Value("$classes\\$MAGNET_PROGID", null, "URL:Magnet link"))
            add(Value("$classes\\$MAGNET_PROGID", "URL Protocol", ""))
            add(Value("$classes\\$MAGNET_PROGID\\DefaultIcon", null, icon))
            add(Value("$classes\\$MAGNET_PROGID\\shell\\open\\command", null, command))
            add(Value("$classes\\.torrent\\OpenWithProgids", TORRENT_PROGID, ""))
            if (magnetKeyMissing) {
                add(Value("$classes\\magnet", null, "URL:Magnet link"))
                add(Value("$classes\\magnet", "URL Protocol", ""))
                add(Value("$classes\\magnet\\DefaultIcon", null, icon))
                add(Value("$classes\\magnet\\shell\\open\\command", null, command))
            }
            add(Value(CAPABILITIES, "ApplicationName", APP_NAME))
            add(Value(CAPABILITIES, "ApplicationDescription", "FetchJet download manager"))
            add(Value(CAPABILITIES, "ApplicationIcon", icon))
            add(Value("$CAPABILITIES\\FileAssociations", ".torrent", TORRENT_PROGID))
            add(Value("$CAPABILITIES\\URLAssociations", "magnet", MAGNET_PROGID))
            add(Value("Software\\RegisteredApplications", APP_NAME, CAPABILITIES))
        }
    }

    fun magnetKeyMissing(read: (Root, String, String?) -> String?): Boolean =
        readClass(read, "magnet", "URL Protocol") == null

    /**
     * The Default apps page opened on FetchJet's own entry (Windows 11; older versions open
     * the general page). [exePath] decides the Store (MSIX) form.
     */
    fun settingsUri(exePath: String): String {
        val folder = exePath.replace('/', '\\').split('\\').firstOrNull { it.startsWith("${STORE_PACKAGE_NAME}_", true) }
        if (folder != null) {
            val parts = folder.split('_')
            if (parts.size >= 5) return "ms-settings:defaultapps?registeredAUMID=${parts[0]}_${parts.last()}!$STORE_APP_ID"
            return "ms-settings:defaultapps"
        }
        return "ms-settings:defaultapps?registeredAppUser=$APP_NAME"
    }

    fun isStorePath(exePath: String): Boolean = exePath.contains("\\WindowsApps\\", ignoreCase = true)
}
