package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.EngineType
import com.michelle.raouf.fetch_jet.feature.media_extractor.MediaPlaylist
import com.michelle.raouf.fetch_jet.feature.media_extractor.PlaylistEntry
import com.michelle.raouf.fetch_jet.feature.media_extractor.allTicked
import com.michelle.raouf.fetch_jet.feature.media_extractor.anySelectable
import com.michelle.raouf.fetch_jet.feature.site_grabber.LinkKind
import com.michelle.raouf.fetch_jet.core.i18n.start.start
import com.tbib.kmp_localization.LocalizationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddDialogRulesTest {
    private fun entry(i: Int, before: Boolean = false, selected: Boolean = !before) =
        PlaylistEntry(index = i, title = "v$i", url = "https://e.com/$i", downloadedBefore = before, isSelected = selected)

    @Test
    fun selectAllIsHiddenWhenEveryVideoWasDownloadedBefore() {
        val all = MediaPlaylist("p", (0 until 7).map { entry(it, before = true) })
        assertFalse(all.anySelectable)
        assertFalse(all.allTicked)
        val some = MediaPlaylist("p", listOf(entry(0, before = true), entry(1)))
        assertTrue(some.anySelectable)
        assertTrue(some.allTicked)
    }

    @Test
    fun countLinesKeepTheDownloadedPartOnItsOwnLine() {
        LocalizationManager.start()
        val none = linkCountLines(3, 5, 0)
        assertEquals("", none.before)
        val some = linkCountLines(3, 5, 2)
        assertEquals(none.count, some.count)
        assertTrue(some.before.contains("2"))
        assertFalse(some.count.contains(some.before))
    }

    @Test
    fun aPageLinkNeedsNoFileNameAndShowsNoNameBox() {
        val url = "https://example.com/section/page"
        val page = DashboardUiState(
            isAddDialogVisible = true,
            inputUrl = url,
            detectedEngine = EngineType.HTTP_DIRECT,
            linkKind = LinkKind.PAGE,
            linkProbeUrl = url
        )
        assertTrue(page.canGrabPage)
        assertFalse(page.needsFileName)
        assertFalse(page.fileNameShown)
        val file = page.copy(inputUrl = "https://example.com/a.zip", linkProbeUrl = "https://example.com/a.zip", suggestedFileName = "a.zip")
        assertFalse(file.canGrabPage)
        assertTrue(file.fileNameShown)
    }
}
