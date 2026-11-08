package com.michelle.raouf.fetch_jet.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewestBuiltForTest {

    private val releases =
        javaClass.getResource("/releases-2026-11-08.json")!!.readText()

    private fun newest(platform: String, ext: String) =
        newestBuiltFor(releases, platform) { assets -> assets.any { it.name.endsWith(ext) } }?.version

    @Test
    fun skippedPlatformsGetTheirLastBuild() {
        assertEquals("1.3.0", newest("macos", ".dmg"))
        assertEquals("1.3.0", newest("android", ".apk"))
        assertEquals("1.3.0", newest("linux", ".deb"))
    }

    @Test
    fun rebuiltPlatformsGetTheLatest() {
        assertEquals("1.3.1", newest("windows", ".msi"))
        assertEquals("1.3.1", newest("ios", ".ipa"))
    }

    @Test
    fun draftsAndPreReleasesAreIgnored() {
        val json = """[
            {"tag_name":"v9.9.9","draft":true,"prerelease":false,"body":"","assets":[
              {"name":"FetchJet-9.9.9-arm64.dmg","browser_download_url":"$RELEASE_DOWNLOAD_PREFIX/v9.9.9/FetchJet-9.9.9-arm64.dmg"}]},
            {"tag_name":"v9.9.8","draft":false,"prerelease":true,"body":"","assets":[
              {"name":"FetchJet-9.9.8-arm64.dmg","browser_download_url":"$RELEASE_DOWNLOAD_PREFIX/v9.9.8/FetchJet-9.9.8-arm64.dmg"}]}
        ]"""
        assertNull(newestBuiltFor(json, "macos") { assets -> assets.isNotEmpty() })
    }
}
