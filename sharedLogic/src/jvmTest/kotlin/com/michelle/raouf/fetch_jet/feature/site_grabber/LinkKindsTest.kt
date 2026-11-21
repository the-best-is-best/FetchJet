package com.michelle.raouf.fetch_jet.feature.site_grabber

import com.michelle.raouf.fetch_jet.feature.http_downloader.LinkProbeCode
import com.michelle.raouf.fetch_jet.feature.http_downloader.LinkProbeResult
import com.michelle.raouf.fetch_jet.feature.http_downloader.classifyHttpProbe
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkKindsTest {
    private fun page(head: String) = LinkProbeResult.Failed(LinkProbeCode.WEB_PAGE, 200, head)

    @Test
    fun noAnswerYetIsUnknown() = assertEquals(LinkKind.UNKNOWN, LinkKinds.of(null))

    @Test
    fun aFileAnswerIsAFile() {
        assertEquals(LinkKind.FILE, LinkKinds.of(LinkProbeResult.Ok(10L, true, "a.zip")))
        assertEquals(LinkKind.FILE, LinkKinds.of(classifyHttpProbe(200, "application/zip", 100L, true)))
    }

    @Test
    fun plainHtmlIsAPage() {
        assertEquals(LinkKind.PAGE, LinkKinds.of(page("<html><body><a href=\"/x.zip\">x</a></body></html>")))
        assertEquals(LinkKind.PAGE, LinkKinds.of(classifyHttpProbe(200, "text/html", 100L, false, pageText = "<p>Hi</p>", pageHead = "<p>Hi</p>")))
    }

    @Test
    fun htmlWithAVideoIsAVideoPage() {
        assertEquals(LinkKind.VIDEO_PAGE, LinkKinds.of(page("<head><meta property=\"og:video\" content=\"x.mp4\">")))
        assertEquals(LinkKind.VIDEO_PAGE, LinkKinds.of(page("<meta name='twitter:player' content='y'>")))
        assertEquals(LinkKind.VIDEO_PAGE, LinkKinds.of(page("<div><video src=a.mp4></video>")))
        assertEquals(LinkKind.VIDEO_PAGE, LinkKinds.of(page("<iframe width=1 src=\"https://www.youtube.com/embed/x\">")))
        assertEquals(LinkKind.VIDEO_PAGE, LinkKinds.of(page("<iframe src=\"//player.vimeo.com/video/1\">")))
    }

    @Test
    fun aFailedProbeIsUnsure() {
        for (code in listOf(LinkProbeCode.ROBOT, LinkProbeCode.REFUSED, LinkProbeCode.TIMEOUT, LinkProbeCode.NO_CONNECTION, LinkProbeCode.NOT_FOUND)) {
            assertEquals(LinkKind.UNSURE,LinkKinds.of(LinkProbeResult.Failed(code, 403)))
        }
    }

    @Test
    fun aVideoMentionedOnlyInTextIsNotAVideoPage() {
        assertEquals(LinkKind.PAGE, LinkKinds.of(page("<p>my video collection and og:video notes</p>")))
    }
}
