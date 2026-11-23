package com.michelle.raouf.fetch_jet.feature.media_extractor

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiteBusyTest {
    @Test
    fun aSiteThatSaid429IsNotAskedAgainForAWhile() {
        SiteBusy.mark("https://drive.google.com/file/d/abc/view")
        assertFailsWith<SiteBusyException> { SiteBusy.check("https://drive.google.com/file/d/other/view") }
        SiteBusy.check("https://www.youtube.com/watch?v=x")
    }

    @Test
    fun only429CountsAsBusy() {
        assertTrue(ytDlpSaysTooManyRequests("ERROR: [GoogleDrive] x: Unable to download JSON metadata: HTTP Error 429: Too Many Requests"))
        assertFalse(ytDlpSaysTooManyRequests("ERROR: [youtube] x: HTTP Error 403: Forbidden"))
    }
}
