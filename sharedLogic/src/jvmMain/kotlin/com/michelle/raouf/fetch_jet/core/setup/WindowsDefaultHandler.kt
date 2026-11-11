package com.michelle.raouf.fetch_jet.core.setup

import com.michelle.raouf.fetch_jet.core.platform.WindowsStorePackage
import com.michelle.raouf.fetch_jet.core.setup.WindowsDefaultApps.Root

/** Windows (JVM app): `reg.exe` for the registry, the Default apps page for the choice. */
internal object WindowsDefaultHandler {
    private fun hive(root: Root) = if (root == Root.CURRENT_USER) "HKCU" else "HKLM"

    private fun read(root: Root, path: String, name: String?): String? {
        val args = if (name == null) arrayOf("reg", "query", "${hive(root)}\\$path", "/ve")
        else arrayOf("reg", "query", "${hive(root)}\\$path", "/v", name)
        val result = jvmRun(*args) ?: return null
        if (result.code != 0) return null
        val wanted = name ?: "(Default)"
        for (line in result.out.lines()) {
            val trimmed = line.trim()
            val match = Regex("^(.+?)\\s{2,}(REG_\\w+)(?:\\s{2,}(.*))?$").find(trimmed) ?: continue
            val (valueName, _, data) = match.destructured
            if (valueName.equals(wanted, true) || (name == null && valueName.startsWith("("))) return data
        }
        return null
    }

    fun isDefault(): Boolean? = WindowsDefaultApps.isDefault(::read)

    fun makeDefault(launcher: String?): Boolean {
        val exe = launcher ?: System.getProperty("jpackage.app-path").orEmpty()
        if (!WindowsStorePackage.isInstalled && exe.isNotBlank()) {
            for (value in WindowsDefaultApps.registration(exe, WindowsDefaultApps.magnetKeyMissing(::read))) {
                val args = mutableListOf("reg", "add", "HKCU\\${value.path}")
                if (value.name == null) args += "/ve" else args += listOf("/v", value.name)
                args += listOf("/t", "REG_SZ", "/d", value.data, "/f")
                jvmRun(*args.toTypedArray())
            }
        }
        val uri = WindowsDefaultApps.settingsUri(exe)
        return jvmRun("cmd", "/c", "start", "", uri)?.code == 0 ||
            jvmRun("explorer.exe", uri) != null
    }
}
