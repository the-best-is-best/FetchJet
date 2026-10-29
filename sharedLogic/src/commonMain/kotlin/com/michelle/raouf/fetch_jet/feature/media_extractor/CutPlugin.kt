package com.michelle.raouf.fetch_jet.feature.media_extractor

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

/**
 * A tiny yt-dlp post-processor plugin that cuts the finished download to [start, end] with a
 * stream copy (no re-encoding, a few seconds). It replaces yt-dlp's own
 * `--remove-chapters "*a-b"`, which in yt-dlp 2026.x crashes before anything is downloaded
 * ("TypeError: unhashable type: 'list'" in ModifyChaptersPP).
 *
 * The source lives here so all three platforms ship the same plugin; [ensureCutPluginDir]
 * writes it next to the task folders and the platforms pass that folder with `--plugin-dirs`
 * and enable it with `--use-postprocessor FetchJetCut:start=<sec>;end=<sec|inf>`.
 *
 * Verified with yt-dlp 2026.10.21 + ffmpeg: a 60 s file cut to 10-25 came out at 15.02 s.
 */
const val FETCHJET_CUT_PLUGIN_PY =
    """# FetchJet: cut a finished download to [start, end] without re-encoding (stream copy).
# Used instead of yt-dlp's "--remove-chapters *a-b", which crashes at start-up in
# yt-dlp 2026.x ("unhashable type: 'list'"). Loaded with --plugin-dirs; invoked as
#   --use-postprocessor FetchJetCut:start=<seconds>;end=<seconds or inf>
import os

from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor
from yt_dlp.utils import PostProcessingError, prepend_extension


class FetchJetCutPP(FFmpegPostProcessor):
    def __init__(self, downloader=None, start='0', end='inf', **kwargs):
        super().__init__(downloader)
        self._start = max(0.0, float(start or 0))
        self._end = None if str(end) in ('', 'inf', 'none') else float(end)

    def run(self, info):
        path = info['filepath']
        if self._start <= 0 and self._end is None:
            return [], info
        out = prepend_extension(path, 'temp')
        self.to_screen(f'Cutting clip from {path}')
        in_opts = ['-ss', f'{self._start:.3f}'] if self._start > 0 else []
        out_opts = ['-map', '0', '-c', 'copy', '-avoid_negative_ts', 'make_zero', '-movflags', '+faststart']
        if self._end is not None:
            dur = self._end - self._start
            if dur <= 0:
                raise PostProcessingError('clip end must be after its start')
            out_opts += ['-t', f'{dur:.3f}']
        self.real_run_ffmpeg([(path, in_opts)], [(out, out_opts)])
        os.replace(out, path)
        # the file is now shorter: keep the metadata honest for later steps
        if self._end is not None:
            info['duration'] = self._end - self._start
        elif info.get('duration'):
            info['duration'] = max(0, info['duration'] - self._start)
        return [], info
"""

/**
 * Writes the plugin under [baseDir] (the downloads temp folder, shared by all tasks) and
 * returns the folder to pass to `--plugin-dirs`, or null when the file cannot be written
 * (the caller then falls back to `--download-sections`).
 */
fun ensureCutPluginDir(baseDir: String): String? = try {
    val root = baseDir.trimEnd('/', '\\').toPath() / "fetchjet-ytdlp-plugins"
    val file = root / "fetchjet" / "yt_dlp_plugins" / "postprocessor" / "fetchjetcut.py"
    val fs = FileSystem.SYSTEM
    val current = if (fs.exists(file)) fs.read(file) { readUtf8() } else null
    if (current != FETCHJET_CUT_PLUGIN_PY) {
        file.parent?.let { fs.createDirectories(it) }
        fs.write(file) { writeUtf8(FETCHJET_CUT_PLUGIN_PY) }
    }
    root.toString()
} catch (_: Throwable) {
    null
}

/** `--use-postprocessor` argument for a clip; seconds, "inf" for an open end. */
fun cutPluginArg(startSec: Double, endSec: Double?): String {
    val end =
        if (endSec == null || endSec.isInfinite() || endSec <= 0.0) "inf" else endSec.toString()
    return "FetchJetCut:start=$startSec;end=$end"
}

/** The folder that holds the task folders: `.../task_<id>` -> `...`. */
fun parentDirOf(dir: String): String {
    val t = dir.trimEnd('/', '\\')
    val i = maxOf(t.lastIndexOf('/'), t.lastIndexOf('\\'))
    return if (i > 0) t.substring(0, i) else t
}
