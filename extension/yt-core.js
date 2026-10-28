// FetchJet - YouTube stream helpers. No browser APIs here (unit-testable with Node).
//
// The YouTube player loads video and audio from *.googlevideo.com/videoplayback URLs that
// are already unlocked (signature, n-param, PO token). Each URL says which format it is
// (itag), its full size (clen) and duration (dur). Removing the player's piece parameters
// (range, rn, ...) gives a URL for the whole stream.
//
// FetchJet prefers the app's own yt-dlp inspection (every format, proper merging). The
// captured streams are the fallback when yt-dlp is not installed: progressive formats
// (video + audio in one file) and single video-only / audio-only streams can be
// downloaded as plain files.

const YT_ITAGS = {
  18: { kind: "av", h: 360, ext: "mp4" },
  22: { kind: "av", h: 720, ext: "mp4" },
  59: { kind: "av", h: 480, ext: "mp4" },
  160: { kind: "v", h: 144, ext: "mp4" }, 133: { kind: "v", h: 240, ext: "mp4" }, 134: { kind: "v", h: 360, ext: "mp4" },
  135: { kind: "v", h: 480, ext: "mp4" }, 136: { kind: "v", h: 720, ext: "mp4" }, 137: { kind: "v", h: 1080, ext: "mp4" },
  298: { kind: "v", h: 720, fps: 60, ext: "mp4" }, 299: { kind: "v", h: 1080, fps: 60, ext: "mp4" },
  264: { kind: "v", h: 1440, ext: "mp4" }, 266: { kind: "v", h: 2160, ext: "mp4" },
  394: { kind: "v", h: 144, ext: "mp4", codec: "AV1" }, 395: { kind: "v", h: 240, ext: "mp4", codec: "AV1" },
  396: { kind: "v", h: 360, ext: "mp4", codec: "AV1" }, 397: { kind: "v", h: 480, ext: "mp4", codec: "AV1" },
  398: { kind: "v", h: 720, ext: "mp4", codec: "AV1" }, 399: { kind: "v", h: 1080, ext: "mp4", codec: "AV1" },
  400: { kind: "v", h: 1440, ext: "mp4", codec: "AV1" }, 401: { kind: "v", h: 2160, ext: "mp4", codec: "AV1" },
  278: { kind: "v", h: 144, ext: "webm" }, 242: { kind: "v", h: 240, ext: "webm" }, 243: { kind: "v", h: 360, ext: "webm" },
  244: { kind: "v", h: 480, ext: "webm" }, 247: { kind: "v", h: 720, ext: "webm" }, 248: { kind: "v", h: 1080, ext: "webm" },
  271: { kind: "v", h: 1440, ext: "webm" }, 313: { kind: "v", h: 2160, ext: "webm" },
  302: { kind: "v", h: 720, fps: 60, ext: "webm" }, 303: { kind: "v", h: 1080, fps: 60, ext: "webm" },
  308: { kind: "v", h: 1440, fps: 60, ext: "webm" }, 315: { kind: "v", h: 2160, fps: 60, ext: "webm" },
  139: { kind: "a", abr: 48, ext: "m4a" }, 140: { kind: "a", abr: 128, ext: "m4a" }, 141: { kind: "a", abr: 256, ext: "m4a" },
  249: { kind: "a", abr: 50, ext: "webm" }, 250: { kind: "a", abr: 70, ext: "webm" }, 251: { kind: "a", abr: 160, ext: "webm" }
};

const YT_PIECE_PARAMS = ["range", "rn", "rbuf", "ump", "srfvp", "alr"];

function ytIsYoutubeHost(hostname) {
  return /(^|\.)youtube\.com$|(^|\.)youtube-nocookie\.com$|^youtu\.be$/i.test(hostname || "");
}

/** Video id of a watch / shorts / embed / live / youtu.be URL, or null. */
function ytVideoId(pageUrl) {
  try {
    const u = new URL(pageUrl);
    if (!ytIsYoutubeHost(u.hostname)) return null;
    if (u.hostname === "youtu.be") return u.pathname.slice(1).split("/")[0] || null;
    const v = u.searchParams.get("v");
    if (v) return v;
    const m = /^\/(shorts|embed|live|v)\/([\w-]{6,})/.exec(u.pathname);
    return m ? m[2] : null;
  } catch (_) { return null; }
}

function ytIsPlaylistPage(pageUrl) {
  try { const u = new URL(pageUrl); return ytIsYoutubeHost(u.hostname) && !!u.searchParams.get("list"); } catch (_) { return false; }
}

/** Describes a captured videoplayback URL, or null when it is not one. */
function ytParseStream(rawUrl) {
  let u;
  try { u = new URL(rawUrl); } catch (_) { return null; }
  if (!/(^|\.)googlevideo\.com$/i.test(u.hostname) || u.pathname !== "/videoplayback") return null;
  const p = u.searchParams;
  if (p.get("sabr") === "1") return { sabr: true }; // new protocol: no reusable URLs
  const itag = Number(p.get("itag"));
  if (!itag) return null;
  const mime = (p.get("mime") || "").toLowerCase();
  let info = YT_ITAGS[itag];
  if (!info) {
    if (mime.startsWith("audio/")) info = { kind: "a", abr: 0, ext: mime.includes("webm") ? "webm" : "m4a" };
    else if (mime.startsWith("video/")) info = { kind: "v", h: 0, ext: mime.includes("webm") ? "webm" : "mp4" };
    else return null;
  }
  for (const k of YT_PIECE_PARAMS) p.delete(k);
  const xtags = (p.get("xtags") || "").toLowerCase();
  const lang = (/(?:^|:)lang=([\w-]+)/.exec(xtags) || [])[1] || "";
  const acont = (/(?:^|:)acont=([\w-]+)/.exec(xtags) || [])[1] || "";
  return {
    itag, url: u.toString(), mime, lang,
    original: acont === "original",
    dubbed: acont.startsWith("dubbed") || acont === "descriptive",
    size: Number(p.get("clen")) || 0,
    dur: Math.round(Number(p.get("dur")) || 0),
    ...info
  };
}

function ytQualityTag(h) {
  if (!h) return "";
  if (h >= 4320) return "8K";
  if (h >= 2160) return "4K";
  if (h >= 1440) return "2K";
  if (h >= 1080) return "Full HD";
  if (h >= 720) return "HD";
  return "SD";
}

function ytFmtSize(n) {
  if (!n) return "";
  const u = ["B", "KB", "MB", "GB"];
  let i = 0;
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
  return `${n.toFixed(i ? 1 : 0)} ${u[i]}`;
}

/**
 * Download choices from the streams captured in one tab. Ads are short separate streams,
 * so the group with the longest duration is the real video. Without an in-app merger,
 * only progressive (video+audio) streams give a complete file; video-only and audio-only
 * streams are offered as what they are.
 */
function ytBuildOptions(streams) {
  const list = Object.values(streams || {}).filter(s => !s.sabr);
  if (!list.length) return [];
  const groups = new Map();
  for (const s of list) { const k = s.dur || 0; if (!groups.has(k)) groups.set(k, []); groups.get(k).push(s); }
  const mainDur = [...groups.keys()].sort((a, b) => b - a || groups.get(b).length - groups.get(a).length)[0];
  const main = groups.get(mainDur);
  const options = [];
  const seen = new Set();
  const byQuality = (a, b) => (b.h || 0) - (a.h || 0) || (b.fps || 30) - (a.fps || 30) || b.size - a.size;

  for (const v of main.filter(s => s.kind === "av").sort(byQuality)) {
    const q = `${v.h}p`;
    if (seen.has("av" + q)) continue;
    seen.add("av" + q);
    options.push({
      kind: "captured", label: `${q} ${ytQualityTag(v.h)} · ${v.ext.toUpperCase()} · video with sound`,
      sub: ytFmtSize(v.size), quality: q, ext: v.ext, size: v.size, duration: v.dur, url: v.url
    });
  }
  for (const v of main.filter(s => s.kind === "v").sort(byQuality)) {
    const q = `${v.h ? v.h + "p" : "video"}${v.fps && v.fps > 30 ? v.fps : ""}`;
    const fmt = v.codec ? `${v.ext.toUpperCase()} ${v.codec}` : v.ext.toUpperCase();
    const key = `v${q}|${fmt}`;
    if (seen.has(key)) continue;
    seen.add(key);
    options.push({
      kind: "captured", label: `${q} ${ytQualityTag(v.h)} · ${fmt} · video only (no sound)`,
      sub: ytFmtSize(v.size), quality: q, ext: v.ext, size: v.size, duration: v.dur, url: v.url, videoOnly: true
    });
  }
  const trackRank = (s) => (s.original ? 2 : s.dubbed ? 0 : 1);
  const audios = main.filter(s => s.kind === "a").sort((a, b) => trackRank(b) - trackRank(a) || (b.abr || 0) - (a.abr || 0) || b.size - a.size);
  for (const a of audios.slice(0, 3)) {
    const key = `a${a.ext}${a.abr}${a.lang}`;
    if (seen.has(key)) continue;
    seen.add(key);
    options.push({
      kind: "captured", label: `Audio only · ${a.ext.toUpperCase()}${a.abr ? " " + a.abr + "k" : ""}${a.lang ? " · " + a.lang : ""}`,
      sub: ytFmtSize(a.size), quality: "audio", ext: a.ext, size: a.size, duration: a.dur, url: a.url, audioOnly: true
    });
  }
  return options;
}

/** "(3) Some title - YouTube" -> "Some title", safe for file names on every OS. */
function ytCleanTitle(title) {
  return (title || "video")
    .replace(/^\(\d+\)\s*/, "")
    .replace(/\s*-\s*YouTube\s*$/i, "")
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, "_")
    .trim()
    .slice(0, 120) || "video";
}

if (typeof module !== "undefined") {
  module.exports = { YT_ITAGS, ytParseStream, ytVideoId, ytIsPlaylistPage, ytBuildOptions, ytCleanTitle, ytFmtSize, ytQualityTag, ytIsYoutubeHost };
}
