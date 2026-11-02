package com.michelle.raouf.fetch_jet.core.update

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UpdateWebPageTest {

    @Test
    fun readsTheLatestReleaseWithoutTheApi() = runBlocking {
        val release = UpdateManager.fetchLatestFromWebPage()
        assertNotNull(release)
        assertTrue(release.tag.startsWith("v"), release.tag)
        assertTrue(release.pageUrl.contains("/releases/tag/${release.tag}"), release.pageUrl)
        assertTrue(release.assets.any { it.name.endsWith(".apk") }, release.assets.toString())
        assertTrue(release.assets.all { it.url.startsWith("https://github.com/") })
        println("web page: ${release.tag}, ${release.assets.size} files: ${release.assets.joinToString { it.name }}")
    }
}
