package com.michelle.raouf.fetch_jet.feature.media_extractor

import kotlin.concurrent.Volatile

/**
 * Progress of a download that yt-dlp fetches as two files (video, then audio) and merges.
 *
 * The platform layers report each file on its own (percent of *that* file, its bytes, its size)
 * with a step label "Step 1 of 3" / "Step 2 of 3" / "Step 3 of 3". The task card shows the file
 * of the current step (video, then audio) with the bar at exactly that file's downloaded / size,
 * and the combined size of both once they are complete.
 *
 * Before: the bar filled 0..100 % once per file, the total stayed frozen at the video's size while
 * the audio was counted against it, and the first update of step 2 still carried the video's
 * numbers - so the audio looked as big as the video.
 */
internal class MultiStreamProgress {
    class View(
        val fraction: Float,
        val downloaded: Long,
        val total: Long,
        val totalIsEstimate: Boolean
    )

    private val total = LongArray(2)
    private val done = LongArray(2)

    @Volatile
    private var lastIndex = -1

    /** null = not a multi-file download (single file, clip...): keep the plain handling. */
    fun update(progress: Float, downloaded: Long, reportedTotal: Long, step: String?): View? {
        val s = step ?: return null
        val idx = when {
            s.contains("Step 1 of 3") -> 0
            s.contains("Step 2 of 3") -> 1
            s.contains("Step 3 of 3") -> 2
            else -> return null
        }
        if (idx == 2) {
            // Both files are complete; the merge has its own line under the bar.
            val all = sizeOf(0) + sizeOf(1)
            return View(1f, all, all, false)
        }

        val switched = idx != lastIndex
        lastIndex = idx
        // The update that announces a new file still carries the previous file's numbers
        // (sent on yt-dlp's "Destination:" line): do not adopt them for the new file.
        if (!switched) {
            if (reportedTotal > 0L) total[idx] = reportedTotal
            val p = progress.coerceIn(0f, 1f)
            val d = when {
                downloaded > 0L -> downloaded
                total[idx] > 0L -> (total[idx] * p).toLong()
                else -> done[idx]
            }
            done[idx] = if (total[idx] > 0L) minOf(d, total[idx]) else d
        }
        if (idx == 1 && done[0] < total[0]) done[0] = total[0] // the video is finished

        // Each step shows its own file: the video while the video downloads, the audio while
        // the audio downloads; the bar is that file's own share, so it always matches the text.
        // The combined size appears once both files are complete (merge step / completed).
        val fileTotal = total[idx]
        val fileDone = done[idx]
        val fraction = if (fileTotal > 0L) {
            (fileDone.toDouble() / fileTotal.toDouble()).toFloat().coerceIn(0f, 1f)
        } else if (switched) 0f else progress.coerceIn(0f, 1f)
        return View(fraction, fileDone, fileTotal, totalIsEstimate = false)
    }

    private fun sizeOf(i: Int): Long = maxOf(total[i], done[i])
}
