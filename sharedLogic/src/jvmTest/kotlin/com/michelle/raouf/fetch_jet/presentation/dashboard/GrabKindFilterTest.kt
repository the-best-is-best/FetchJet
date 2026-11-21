package com.michelle.raouf.fetch_jet.presentation.dashboard

import com.michelle.raouf.fetch_jet.domain.model.EngineType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrabKindFilterTest {
    private fun file(name: String, folder: String, before: Boolean = false) = LinkListEntry(
        url = "https://example.com/$name", engine = EngineType.HTTP_DIRECT, isSelected = !before,
        fileName = name, folder = folder, downloadedBefore = before
    )

    private fun state() = DashboardUiState(
        linkList = listOf(
            file("a.mp4", "Video"), file("b.zip", "Archives"), file("c.zip", "Archives", before = true),
            file("d.png", "Images"), file("e.png", "Images")
        )
    )

    @Test
    fun hidingATypeUnchecksItsRowsAndLeavesThemOutOfTheVisibleList() {
        val s = state().withKindShown("Archives", false)
        assertEquals(setOf("Archives"), s.siteGrab.hiddenKinds)
        assertTrue(s.linkList.filter { it.folder == "Archives" }.none { it.isSelected })
        assertEquals(listOf(0, 3, 4), s.visibleLinks.map { it.index })
        assertFalse(s.grabGroups.first { it.folder == "Archives" }.selected)
        assertTrue(s.grabGroups.first { it.folder == "Video" }.selected)
        assertEquals(2, s.grabGroups.first { it.folder == "Archives" }.count)
    }

    @Test
    fun showingATypeAgainChecksItsRowsExceptThoseDownloadedBefore() {
        val s = state().withKindShown("Archives", false).withKindShown("Archives", true)
        assertTrue(s.siteGrab.hiddenKinds.isEmpty())
        assertEquals(5, s.visibleLinks.size)
        assertTrue(s.linkList[1].isSelected)
        assertFalse(s.linkList[2].isSelected)
    }

    @Test
    fun selectAllAndASingleToggleIgnoreHiddenAndLockedRows() {
        val s = state().withKindShown("Images", false).withAllLinksChecked(false)
        assertEquals(listOf(false, false, false, false, false), s.linkList.map { it.isSelected })
        val all = s.withAllLinksChecked(true)
        assertEquals(listOf(true, true, false, false, false), all.linkList.map { it.isSelected })
        assertFalse(all.withLinkChecked(3, true).linkList[3].isSelected)
        assertFalse(all.withLinkChecked(2, true).linkList[2].isSelected)
        assertTrue(all.withLinkChecked(0, false).linkList[0].isSelected.not())
    }

    @Test
    fun everyTypeOffLeavesNoVisibleRowButKeepsTheChips() {
        val s = listOf("Video", "Archives", "Images").fold(state()) { st, k -> st.withKindShown(k, false) }
        assertTrue(s.visibleLinks.isEmpty())
        assertEquals(3, s.grabGroups.size)
        assertTrue(s.grabGroups.none { it.selected })
        assertTrue(s.linkList.none { it.isSelected })
    }

    @Test
    fun aFileFoundLaterOfAHiddenTypeArrivesHiddenAndUnchecked() {
        val s = state().withKindShown("Video", false).withGrabbedFiles(listOf(file("f.mp4", "Video"), file("g.png", "Images")))
        assertEquals(7, s.linkList.size)
        assertFalse(s.linkList[5].isSelected)
        assertTrue(s.linkList[6].isSelected)
        assertEquals(listOf(1, 2, 3, 4, 6), s.visibleLinks.map { it.index })
    }
}
