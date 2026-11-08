package com.michelle.raouf.fetch_jet.core.browser

import com.michelle.raouf.fetch_jet.core.update.isOlderExtension
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionStoreTest {
    @Test
    fun olderExtensionVersions() {
        assertTrue(isOlderExtension("3.2.63", "3.3.0"))
        assertTrue(isOlderExtension("3.3", "3.3.1"))
        assertFalse(isOlderExtension("3.3.0", "3.3.0"))
        assertFalse(isOlderExtension("3.3.70", "3.3.0"))
        assertFalse(isOlderExtension("3.3.70.1", "3.3.70"))
        assertTrue(isOlderExtension("3.3.70", "3.3.70.1"))
    }

    /** Network: run with FETCHJET_NET_TESTS=1. */
    @Test
    fun findsSignedXpiAndStoreVersion() {
        if (System.getenv("FETCHJET_NET_TESTS") != "1") return
        runBlocking {
            val xpi = ExtensionStore.githubXpi()
            println("github xpi: $xpi")
            assertTrue(xpi != null && xpi.url.endsWith(".xpi"))
            val bytes = ExtensionStore.download(xpi!!)
            println("downloaded: ${bytes?.size} bytes")
            assertTrue(bytes != null && bytes.size > 1024)
            println("firefox store: ${ExtensionStore.versionFor("firefox")}")
            println("edge store: ${ExtensionStore.versionFor("edge")}")
        }
    }
}
