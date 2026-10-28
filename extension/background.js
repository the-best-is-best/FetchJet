// FetchJet browser extension - background (Chrome/Edge service worker, Firefox event page)
//
// Works unchanged in Chromium (MV3 service worker) and Firefox (MV3 event page):
// every API call goes through `api` (browser.* in Firefox, chrome.* elsewhere) and
// uses promises only.

// Firefox loads yt-core.js from the manifest; Chromium service workers import it here.
// (No try/catch on purpose: a missing file must fail loudly at install, not silently later.)
if (typeof importScripts === "function" && typeof ytParseStream === "undefined") importScripts("yt-core.js");

const api = typeof browser !== "undefined" ? browser : chrome;
const IS_FIREFOX = typeof browser !== "undefined" && /firefox|thunderbird/i.test(navigator.userAgent);

const FETCHJET_PORT = 28450;
const FETCHJET_BASE = `http://127.0.0.1:${FETCHJET_PORT}`;
const NATIVE_HOST_NAME = "com.michelle.raouf.fetch_jet_downloader";
const PROTOCOL = "fetchjet://download?url=";

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

const DEFAULT_EXTENSIONS = [
  "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts",
  "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma",
  "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst", "cab",
  "pdf", "epub", "mobi", "djvu",
  "exe", "msi", "msix", "dmg", "pkg", "deb", "rpm", "appimage", "iso", "img", "bin",
  "apk", "xapk", "ipa", "jar",
  "torrent"
];

const DEFAULT_SETTINGS = {
  enabled: true,              // master switch
  captureDownloads: true,     // take over browser downloads (downloads.onCreated)
  captureLinks: true,         // intercept clicks on file links (content script)
  captureMedia: true,         // sniff video/audio responses and list them in the popup
  sendCookies: true,          // send the site's cookies with the link (needed for logins)
  minSizeKB: 0,               // ignore browser downloads smaller than this (0 = all)
  extensions: DEFAULT_EXTENSIONS,
  excludedHosts: [],          // never capture on these hosts (suffix match)
  holdKey: "alt",             // hold this key while clicking to download with the browser
  notifications: true,
  autoLaunch: true,           // start FetchJet when it is not running (off = browser downloads it)
  autoResolve: true,          // ask the app (yt-dlp) for qualities when a page plays a video
  pagePanel: true             // floating "Download this video" button on pages
};

let settingsCache = null;

async function getSettings() {
  if (settingsCache) return settingsCache;
  let stored = {};
  try { stored = (await api.storage.local.get("settings")).settings || {}; } catch (_) {}
  settingsCache = { ...DEFAULT_SETTINGS, ...stored };
  return settingsCache;
}

async function saveSettings(patch) {
  const current = await getSettings();
  settingsCache = { ...current, ...patch };
  await api.storage.local.set({ settings: settingsCache });
  return settingsCache;
}

api.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes.settings) {
    settingsCache = { ...DEFAULT_SETTINGS, ...(changes.settings.newValue || {}) };
    updateActionBadgeForAll();
  }
});

// ---------------------------------------------------------------------------
// Small session store (survives the worker being suspended)
// ---------------------------------------------------------------------------

const session = api.storage.session || api.storage.local;

// Read-modify-write cycles on session storage (media per tab, bypass marks, YouTube streams)
// fire concurrently from webRequest; run them one at a time so none is lost.
let writeChain = Promise.resolve();
function serial(fn) {
  const run = () => fn();
  const p = writeChain.then(run, run);
  writeChain = p.catch(() => {});
  return p;
}

async function sessionGet(key) {
  try { return (await session.get(key))[key] || {}; } catch (_) { return {}; }
}
async function sessionSet(key, value) {
  try { await session.set({ [key]: value }); } catch (_) {}
}

const BYPASS_KEY = "fetchjet_bypass_urls";   // downloads the user wants to keep in the browser
const NOTIFY_KEY = "fetchjet_notify_urls";   // notification id -> url ("Download in browser")
const MEDIA_KEY = "fetchjet_media_by_tab";    // tabId -> { url -> media item }
const HOLD_KEY = "fetchjet_hold_until";       // timestamp: the user held the bypass key on a click

const takeBypass = (...urls) => serial(() => takeBypassNow(...urls));
const addBypass = (url) => serial(() => addBypassNow(url));
async function takeBypassNow(...urls) {
  const map = await sessionGet(BYPASS_KEY);
  const now = Date.now();
  let hit = false;
  for (const [u, ts] of Object.entries(map)) {
    if (now - ts > 120000) delete map[u];
    else if (urls.includes(u)) { hit = true; delete map[u]; }
  }
  await sessionSet(BYPASS_KEY, map);
  return hit;
}
async function addBypassNow(url) {
  const map = await sessionGet(BYPASS_KEY);
  map[url] = Date.now();
  await sessionSet(BYPASS_KEY, map);
}

// ---------------------------------------------------------------------------
// URL helpers
// ---------------------------------------------------------------------------

function hostOf(url) {
  try { return new URL(url).hostname.toLowerCase(); } catch (_) { return ""; }
}

function isArchiveOrgDirectoryOrDetails(url) {
  try {
    const parsed = new URL(url);
    if (!parsed.hostname.includes("archive.org")) return false;
    const parts = parsed.pathname.split("/").filter(Boolean);
    return (parts[0] === "download" || parts[0] === "details") && parts.length <= 2;
  } catch (_) { return false; }
}

function isValidDownloadUrl(url) {
  if (!url || typeof url !== "string") return false;
  if (url.includes("fetchjet_fallback=1") || url.includes("kortex_fallback=1")) return false;
  if (url.includes(`localhost:${FETCHJET_PORT}`) || url.includes(`127.0.0.1:${FETCHJET_PORT}`)) return false;
  const lower = url.toLowerCase();
  if (!(lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("magnet:"))) return false;
  if (isArchiveOrgDirectoryOrDetails(url)) return false;
  return true;
}

function isExcludedHost(url, settings) {
  const host = hostOf(url);
  if (!host) return false;
  return (settings.excludedHosts || []).some(h => {
    const x = String(h).trim().toLowerCase();
    return x && (host === x || host.endsWith("." + x));
  });
}

function extensionOf(urlOrName) {
  try {
    const path = urlOrName.includes("://") ? new URL(urlOrName).pathname : urlOrName;
    const clean = path.replace(/\/+$/, "");
    const m = /\.([a-z0-9]{1,8})$/i.exec(clean);
    return m ? m[1].toLowerCase() : "";
  } catch (_) { return ""; }
}

function hasWantedExtension(url, fileName, settings) {
  const set = new Set((settings.extensions || []).map(e => String(e).toLowerCase().replace(/^\./, "")));
  const e1 = extensionOf(fileName || "");
  if (e1 && set.has(e1)) return true;
  const e2 = extensionOf(url);
  return !!(e2 && set.has(e2));
}

function baseName(path) {
  if (!path) return "";
  const parts = path.split(/[\\/]/);
  return parts[parts.length - 1] || "";
}

// ---------------------------------------------------------------------------
// Talking to the desktop app
// ---------------------------------------------------------------------------

async function pingApp(timeoutMs = 800) {
  try {
    const controller = new AbortController();
    const t = setTimeout(() => controller.abort(), timeoutMs);
    const res = await fetch(`${FETCHJET_BASE}/ping`, { method: "GET", signal: controller.signal });
    clearTimeout(t);
    return res.ok;
  } catch (_) { return false; }
}

async function fetchAppConfig() {
  try {
    const controller = new AbortController();
    const t = setTimeout(() => controller.abort(), 1500);
    const res = await fetch(`${FETCHJET_BASE}/config`, { method: "GET", signal: controller.signal });
    clearTimeout(t);
    if (!res.ok) return null;
    return await res.json();
  } catch (_) { return null; }
}

async function cookieHeaderFor(url) {
  const settings = await getSettings();
  if (!settings.sendCookies || !api.cookies) return "";
  try {
    const cookies = await api.cookies.getAll({ url });
    if (!cookies || cookies.length === 0) return "";
    return cookies.map(c => `${c.name}=${c.value}`).join("; ");
  } catch (_) { return ""; }
}

/** Builds the JSON the app's /add and /links endpoints accept. */
async function buildRequest({ url, referer = "", fileName = "", formatId = "", direct = false }) {
  const body = {
    url,
    cookie: await cookieHeaderFor(url),
    referer: referer || "",
    userAgent: navigator.userAgent,
    fileName: fileName || ""
  };
  if (formatId) body.formatId = formatId; // quality chosen in the browser (yt-dlp format)
  if (direct) body.direct = true;         // start now, no Add dialog
  return body;
}

async function postJson(path, body, timeoutMs = 2500) {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(`${FETCHJET_BASE}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-FetchJet": "1" },
      body: JSON.stringify(body),
      signal: controller.signal
    });
  } finally { clearTimeout(t); }
}

/** Sends one link; compatible with older app builds that only know GET /download. */
async function deliverOne(request) {
  try {
    // With a chosen quality the app may have to re-run yt-dlp when its cache expired.
    const res = await postJson("/add", request, request.formatId ? 90000 : 2500);
    if (res.status === 409) return false; // quality gone: the caller re-inspects
    if (res.ok) return true;
    if (res.status === 404) {
      const legacy = `${FETCHJET_BASE}/download?url=${encodeURIComponent(request.url)}&cookie=${encodeURIComponent(request.cookie || "")}`;
      const r2 = await fetch(legacy, { method: "GET", headers: { "X-FetchJet": "1" } });
      return r2.ok;
    }
    return false;
  } catch (_) { return false; }
}

async function deliverBatch(requests) {
  try {
    const res = await postJson("/links", requests, 5000);
    if (res.ok) return true;
    if (res.status === 404) { // old app: one by one
      for (const r of requests) await deliverOne(r);
      return true;
    }
    return false;
  } catch (_) { return false; }
}

async function launchApp(firstUrl) {
  const settings = await getSettings();
  if (!settings.autoLaunch) return false;

  // 1. Native messaging host (installed with the desktop app)
  try {
    await api.runtime.sendNativeMessage(NATIVE_HOST_NAME, { action: "download", url: firstUrl || "" });
    return true;
  } catch (_) { /* not registered */ }

  // 2. fetchjet:// protocol handler (registered by the installer)
  try {
    const tab = await api.tabs.create({ url: PROTOCOL + encodeURIComponent(firstUrl || ""), active: false });
    if (tab && tab.id != null) setTimeout(() => api.tabs.remove(tab.id).catch(() => {}), 1500);
    return true;
  } catch (_) { return false; }
}

async function waitForApp(maxMs = 8000) {
  const start = Date.now();
  while (Date.now() - start < maxMs) {
    if (await pingApp(500)) return true;
    await new Promise(r => setTimeout(r, 400));
  }
  return false;
}

async function notify(message, extra = {}) {
  const settings = await getSettings();
  if (!settings.notifications || !api.notifications) return null;
  try {
    const opts = { type: "basic", iconUrl: "icon48.png", title: "FetchJet", message, ...extra };
    if (IS_FIREFOX) delete opts.buttons; // Firefox does not support notification buttons
    return await api.notifications.create(opts);
  } catch (_) { return null; }
}

/**
 * Main entry: hand a link to FetchJet, launching it when needed.
 * `source` is a short tag for logging ("download", "link", "menu", "popup", "media").
 */
async function sendToFetchJet({ url, referer = "", fileName = "", formatId = "", direct = false }, source = "") {
  if (!isValidDownloadUrl(url)) return { ok: false, reason: "unsupported url" };
  const request = await buildRequest({ url, referer, fileName, formatId, direct });

  if (await deliverOne(request)) return { ok: true };

  const launched = await launchApp(url);
  if (!launched) {
    await notify("FetchJet is not running and could not be started.");
    return { ok: false, reason: "app not running" };
  }
  await notify("Launching FetchJet...");
  if (await waitForApp()) {
    // The launcher may already have passed the URL on the command line; /add is idempotent
    // enough (duplicate check in the app) that re-sending with cookies is the safer choice.
    const ok = await deliverOne(request);
    return { ok };
  }
  return { ok: false, reason: "app did not start" };
}

async function sendBatchToFetchJet(items, referer = "") {
  const unique = new Map();
  for (const it of items) {
    const url = typeof it === "string" ? it : it.url;
    if (isValidDownloadUrl(url) && !unique.has(url)) {
      unique.set(url, { url, referer: (it && it.referer) || referer, fileName: (it && it.fileName) || "" });
    }
  }
  if (unique.size === 0) return { ok: false, reason: "no links", count: 0 };
  const requests = [];
  for (const it of unique.values()) requests.push(await buildRequest(it));

  if (!(await pingApp())) {
    if (!(await launchApp(requests[0].url)) || !(await waitForApp())) {
      await notify("FetchJet is not running and could not be started.");
      return { ok: false, reason: "app not running", count: 0 };
    }
  }
  const ok = await deliverBatch(requests);
  if (ok) await notify(`${requests.length} link${requests.length === 1 ? "" : "s"} sent to FetchJet.`);
  return { ok, count: requests.length };
}

// ---------------------------------------------------------------------------
// 1. Browser download interception
// ---------------------------------------------------------------------------

/** Only downloads the user starts now, not ones the browser resumes at start-up. */
function isNewDownload(item) {
  if (item.state && item.state !== "in_progress") return false;
  if (item.byExtensionId) return false; // ours (handed back) or another extension's
  const started = Date.parse(item.startTime || "");
  if (!Number.isNaN(started) && Date.now() - started > 30000) return false;
  return true;
}

/** Should FetchJet take this browser download? */
async function shouldTake(item) {
  const settings = await getSettings();
  if (!settings.enabled || !settings.captureDownloads) return false;
  const targetUrl = item.finalUrl || item.url;
  if (!isValidDownloadUrl(targetUrl) || !/^https?:/i.test(targetUrl)) return false; // blob:, data: stay
  if (!isNewDownload(item)) return false;
  if (await takeBypass(targetUrl, item.url)) return false;      // "Download in browser instead"
  if (await consumeHold()) return false;                         // user held the bypass key
  if (isExcludedHost(targetUrl, settings) || isExcludedHost(item.referrer || "", settings)) return false;
  const size = item.totalBytes > 0 ? item.totalBytes : (item.fileSize > 0 ? item.fileSize : -1);
  if (settings.minSizeKB > 0 && size >= 0 && size < settings.minSizeKB * 1024) return false;
  const fileName = baseName(item.filename);
  // A browser download is already "a file": the extension list only filters when the user
  // narrowed it and the name has an extension we can judge.
  if ((settings.extensions || []).length > 0 && fileName && extensionOf(fileName) &&
      !hasWantedExtension(targetUrl, fileName, settings)) return false;
  // App closed and auto-launch off: leave it to the browser (Ezoo behaviour).
  if (!settings.autoLaunch && !(await pingApp())) return false;
  return true;
}

async function takeOver(item) {
  const targetUrl = item.finalUrl || item.url;
  try { await api.downloads.cancel(item.id); } catch (_) {}
  try { await api.downloads.erase({ id: item.id }); } catch (_) {}
  const fileName = baseName(item.filename);
  const result = await sendToFetchJet({ url: targetUrl, referer: item.referrer || "", fileName }, "download");
  if (!result.ok) {
    // The app could not be reached: give the download back to the browser, once.
    await addBypass(targetUrl);
    try { await api.downloads.download({ url: targetUrl }); } catch (_) {}
    return;
  }
  const nid = await notify("Download captured and sent to FetchJet.", {
    buttons: [{ title: "Download in browser instead" }]
  });
  if (nid) {
    const map = await sessionGet(NOTIFY_KEY);
    map[nid] = targetUrl;
    await sessionSet(NOTIFY_KEY, map);
  }
}

// Chromium (Chrome, Edge, Brave, Opera, Vivaldi): decide before the browser asks where to
// save, so its "Save as" window never appears for files FetchJet takes. Firefox has no such
// moment and decides when the download is created.
const DECIDE_EARLY = !!(api.downloads.onDeterminingFilename && api.downloads.onDeterminingFilename.addListener);
if (DECIDE_EARLY) {
  api.downloads.onDeterminingFilename.addListener((item, suggest) => {
    let answered = false;
    const keep = () => { if (!answered) { answered = true; try { suggest(); } catch (_) {} } };
    const guard = setTimeout(keep, 4000); // never leave the browser waiting
    shouldTake(item).then((take) => {
      clearTimeout(guard);
      if (take && !answered) { answered = true; return takeOver(item); }
      keep();
    }).catch(() => { clearTimeout(guard); keep(); });
    return true; // answered asynchronously
  });
}

api.downloads.onCreated.addListener((downloadItem) => {
  if (DECIDE_EARLY) return;
  // Firefox: give it a moment to resolve the final URL / filename / size.
  setTimeout(async () => {
    let item;
    try { [item] = await api.downloads.search({ id: downloadItem.id }); } catch (_) { return; }
    if (item && await shouldTake(item)) await takeOver(item);
  }, 400);
});

if (api.notifications && api.notifications.onButtonClicked) {
  api.notifications.onButtonClicked.addListener(async (notificationId, buttonIndex) => {
    if (buttonIndex !== 0) return;
    const map = await sessionGet(NOTIFY_KEY);
    const url = map[notificationId];
    if (!url) return;
    delete map[notificationId];
    await sessionSet(NOTIFY_KEY, map);
    await addBypass(url);
    try { await api.downloads.download({ url }); } catch (_) {}
  });
}

async function consumeHold() {
  const data = await sessionGet(HOLD_KEY);
  const until = data.until || 0;
  if (until && Date.now() < until) {
    await sessionSet(HOLD_KEY, {});
    return true;
  }
  return false;
}

// ---------------------------------------------------------------------------
// 2. Media sniffing (video / audio / HLS / DASH responses)
// ---------------------------------------------------------------------------

const MEDIA_TYPES = [
  "video/", "audio/",
  "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl",
  "application/dash+xml", "application/octet-stream"
];
const MEDIA_EXTS = new Set(["mp4", "webm", "mkv", "mov", "m4v", "flv", "avi", "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "m3u8", "mpd"]);
const SEGMENT_HINT = /\.(ts|m4s|aac|cmfv|cmfa)(\?|$)|\/seg(ment)?[-_]?\d+|\/chunk[-_]?\d+|[?&]range=|\/range\//i;
const SKIP_MEDIA_HOSTS = /(^|\.)(googlevideo\.com|youtube\.com|ytimg\.com|youtu\.be)$/i;

if (api.webRequest && api.webRequest.onHeadersReceived) {
  api.webRequest.onHeadersReceived.addListener(
    (details) => { serial(() => sniffMedia(details)).catch(() => {}); },
    { urls: ["http://*/*", "https://*/*"], types: ["media", "xmlhttprequest", "other", "object"] },
    ["responseHeaders"]
  );
}

function header(headers, name) {
  if (!headers) return "";
  const h = headers.find(x => x.name.toLowerCase() === name);
  return h ? String(h.value || "") : "";
}

async function sniffMedia(details) {
  if (details.tabId == null || details.tabId < 0) return;
  if (details.statusCode < 200 || details.statusCode >= 300) return;
  const settings = await getSettings();
  if (!settings.enabled || !settings.captureMedia) return;
  const url = details.url;
  if (!isValidDownloadUrl(url) || SKIP_MEDIA_HOSTS.test(hostOf(url))) return;

  const ctype = header(details.responseHeaders, "content-type").toLowerCase();
  const ext = extensionOf(url);
  const typeMatch = MEDIA_TYPES.some(t => ctype.startsWith(t));
  const extMatch = MEDIA_EXTS.has(ext);
  if (!typeMatch && !extMatch) return;
  if (ctype.startsWith("application/octet-stream") && !extMatch) return;
  if (ctype.startsWith("video/mp2t") || ctype.includes("iso.segment")) return; // HLS/DASH segments
  if (SEGMENT_HINT.test(url) && !["m3u8", "mpd"].includes(ext)) return;

  let size = 0;
  const range = header(details.responseHeaders, "content-range"); // "bytes 0-999/123456"
  const m = /\/(\d+)\s*$/.exec(range);
  if (m) size = parseInt(m[1], 10);
  else size = parseInt(header(details.responseHeaders, "content-length"), 10) || 0;
  const isPlaylist = ext === "m3u8" || ext === "mpd" || ctype.includes("mpegurl") || ctype.includes("dash+xml");
  if (!isPlaylist && size > 0 && size < 512 * 1024) return; // tiny blobs: previews, thumbnails, segments

  const key = isPlaylist ? url : url.split("#")[0];
  const all = await sessionGet(MEDIA_KEY);
  const perTab = all[details.tabId] || {};
  if (perTab[key]) {
    if (size > (perTab[key].size || 0)) { perTab[key].size = size; all[details.tabId] = perTab; await sessionSet(MEDIA_KEY, all); }
    return;
  }
  const kind = isPlaylist ? (ext === "mpd" || ctype.includes("dash") ? "DASH" : "HLS")
    : ctype.startsWith("audio/") || ["mp3", "m4a", "aac", "ogg", "opus", "flac", "wav"].includes(ext) ? "Audio" : "Video";
  let pageUrl = details.initiator || details.documentUrl || "";
  try { const t = await api.tabs.get(details.tabId); pageUrl = t.url || pageUrl; } catch (_) {}

  const entries = Object.keys(perTab);
  if (entries.length >= 30) delete perTab[entries[0]];
  perTab[key] = {
    url: key, kind, size, contentType: ctype.split(";")[0], pageUrl,
    name: baseName((() => { try { return new URL(key).pathname; } catch (_) { return key; } })()) || `${kind.toLowerCase()}.${ext || "bin"}`,
    time: Date.now()
  };
  all[details.tabId] = perTab;
  await sessionSet(MEDIA_KEY, all);
  pushToTab(details.tabId);
}

function clearMediaForTab(tabId) {
  return serial(async () => {
    const all = await sessionGet(MEDIA_KEY);
    if (all[tabId]) { delete all[tabId]; await sessionSet(MEDIA_KEY, all); }
  });
}

function updateBadge(tabId, count) {
  if (!api.action) return;
  try {
    api.action.setBadgeText({ tabId, text: count > 0 ? String(count) : "" }).catch(() => {});
    api.action.setBadgeBackgroundColor({ tabId, color: "#6366f1" }).catch(() => {});
  } catch (_) {}
}

async function updateActionBadgeForAll() {
  const settings = await getSettings();
  if (!api.action) return;
  try {
    if (!settings.enabled) {
      await api.action.setBadgeText({ text: "off" });
      await api.action.setBadgeBackgroundColor({ color: "#64748b" });
    } else {
      await api.action.setBadgeText({ text: "" });
    }
  } catch (_) {}
}


// ---------------------------------------------------------------------------
// 2b. YouTube streams, yt-dlp qualities and the per-tab item list
// ---------------------------------------------------------------------------
// yt_<tab>:  { videoId, sabr, streams: { "<itag>_<dur>[_lang]": stream }, resolving, resolved, resolveError }
// res_<tab>: { page, resolving, resolved: { title, options }, resolveError }   (any other video site)

const YT_KEY = "fetchjet_yt_by_tab";
const RES_KEY = "fetchjet_res_by_tab";
const seenStreams = new Map(); // tabId -> Set(streamKey), memory only (worker may restart)

async function tabState(key, tabId) { return (await sessionGet(key))[tabId] || null; }
function setTabState(key, tabId, value) {
  return serial(async () => {
    const all = await sessionGet(key);
    if (value == null) delete all[tabId]; else all[tabId] = value;
    await sessionSet(key, all);
  });
}

if (api.webRequest && api.webRequest.onBeforeRequest && typeof ytParseStream === "function") {
  api.webRequest.onBeforeRequest.addListener((d) => {
    if (d.tabId == null || d.tabId < 0) return;
    const stream = ytParseStream(d.url);
    if (!stream) return;
    const sk = stream.sabr ? "sabr" : `${stream.itag}_${stream.dur}${stream.lang ? "_" + stream.lang : ""}`;
    let seen = seenStreams.get(d.tabId);
    if (!seen) seenStreams.set(d.tabId, (seen = new Set()));
    if (seen.has(sk)) return;
    seen.add(sk);
    serial(() => addYoutubeStream(d.tabId, stream, sk)).catch(() => {});
  }, { urls: ["*://*.googlevideo.com/videoplayback*"] });
}

async function addYoutubeStream(tabId, stream, sk) {
  const settings = await getSettings();
  if (!settings.enabled || !settings.captureMedia) return;
  let tab = null;
  try { tab = await api.tabs.get(tabId); } catch (_) {}
  const videoId = tab ? ytVideoId(tab.url) : null;
  let state = (await tabState(YT_KEY, tabId)) || { videoId, sabr: false, streams: {} };
  if (videoId && state.videoId && state.videoId !== videoId) {
    state = { videoId, sabr: false, streams: {} }; // moved to another video
    seenStreams.delete(tabId);
  }
  state.videoId = state.videoId || videoId;
  if (stream.sabr) state.sabr = true;
  else {
    state.streams[sk] = stream; // newest URL of each stream (old ones expire)
    const ids = Object.keys(state.streams);
    if (ids.length > 60) delete state.streams[ids[0]];
  }
  // Runs inside serial(): write directly (setTabState would queue behind us and deadlock).
  const all = await sessionGet(YT_KEY);
  all[tabId] = state;
  await sessionSet(YT_KEY, all);
  pushToTab(tabId);
}

/** GET /inspect on the app: yt-dlp qualities of a page as popup items. */
async function inspectWithApp(pageUrl) {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), 90000);
  try {
    const res = await fetch(`${FETCHJET_BASE}/inspect?url=${encodeURIComponent(pageUrl)}`, { signal: controller.signal, headers: { "X-FetchJet": "1" } });
    const data = await res.json();
    if (!res.ok || !Array.isArray(data)) return { resolveError: (data && data.error) || `FetchJet answered HTTP ${res.status}` };
    const title = (data.find(q => q.title) || {}).title || "";
    const options = [];
    const seen = new Set();
    for (const q of data) {
      if (!q.formatId) continue;
      const label = `${q.quality || "video"} · ${(q.format || "mp4").toUpperCase()}`;
      if (seen.has(label)) continue;
      seen.add(label);
      options.push({
        kind: "ytdlp", label, sub: q.size > 0 ? fmtBytes(q.size) : "",
        url: pageUrl, formatId: String(q.formatId), quality: q.quality || "", ext: q.format || "mp4",
        size: q.size > 0 ? q.size : 0, duration: q.duration || 0, title
      });
    }
    if (!options.length) return { resolveError: "No downloadable formats found" };
    return { resolved: { title, options } };
  } catch (e) {
    return { resolveError: (await pingApp()) ? String(e && e.message || e) : "FetchJet is not running" };
  } finally { clearTimeout(t); }
}

function fmtBytes(n) { return typeof ytFmtSize === "function" ? ytFmtSize(n) : `${Math.round(n / 1048576)} MB`; }

async function appCanInspect() {
  if (!(await pingApp())) return false;
  const cfg = await fetchAppConfig();
  return !cfg || cfg.mediaExtractor !== false;
}

const resolving = new Map(); // tabId -> page / videoId being resolved

async function resolveYoutube(tabId, pageUrl, force = false) {
  const videoId = ytVideoId(pageUrl);
  if (!videoId) return;
  const current = await tabState(YT_KEY, tabId);
  if (!force && current && current.videoId === videoId && (current.resolved || current.resolving || current.resolveError)) return;
  if (resolving.get(tabId) === videoId) return;
  const settings = await getSettings();
  if (!force && !settings.autoResolve) return;
  if (!(await appCanInspect())) return; // captured streams remain the fallback
  resolving.set(tabId, videoId);
  const base = (current && current.videoId === videoId) ? current : { videoId, sabr: false, streams: {} };
  await setTabState(YT_KEY, tabId, { ...base, resolving: true, resolveError: null, resolved: null });
  pushToTab(tabId);
  const change = await inspectWithApp(`https://www.youtube.com/watch?v=${videoId}`);
  if (resolving.get(tabId) === videoId) resolving.delete(tabId);
  let now = null;
  try { now = await api.tabs.get(tabId); } catch (_) {}
  if (now && ytVideoId(now.url) === videoId) {
    const latest = (await tabState(YT_KEY, tabId)) || base;
    await setTabState(YT_KEY, tabId, { ...latest, resolving: false, ...change });
    pushToTab(tabId);
  }
}

/** Any other site (Facebook, Instagram, TikTok, X, Vimeo, ... ~1800 via yt-dlp). */
async function resolvePage(tabId, page, force = false, embedded = false) {
  // YouTube's own pages go through resolveYoutube; a YouTube player embedded elsewhere goes here.
  if (!page || !/^https?:/i.test(page) || (ytVideoId(page) && !embedded)) return;
  const current = await tabState(RES_KEY, tabId);
  if (!force && current && current.page === page && (current.resolved || current.resolving || current.resolveError)) return;
  if (resolving.get(tabId) === page) return;
  if (!force) {
    const settings = await getSettings();
    if (!settings.autoResolve) return;
  }
  if (!(await appCanInspect())) {
    if (force) { await setTabState(RES_KEY, tabId, { page, resolving: false, resolveError: "FetchJet is not running (or has no media extractor)" }); pushToTab(tabId); }
    return;
  }
  resolving.set(tabId, page);
  await setTabState(RES_KEY, tabId, { page, resolving: true });
  pushToTab(tabId);
  const change = await inspectWithApp(page);
  if (resolving.get(tabId) === page) resolving.delete(tabId);
  const now = await tabState(RES_KEY, tabId);
  if (now && now.page === page) {
    await setTabState(RES_KEY, tabId, { page, resolving: false, ...change });
    pushToTab(tabId);
  }
}

/** Everything the popup and the page button show for a tab, in one list. */
async function itemsForTab(tabId) {
  const [yt, res, mediaAll] = await Promise.all([tabState(YT_KEY, tabId), tabState(RES_KEY, tabId), sessionGet(MEDIA_KEY)]);
  const media = Object.values(mediaAll[tabId] || {}).sort((a, b) => (b.size || 0) - (a.size || 0));
  const items = [];
  let pageTitle = "";
  if (yt) {
    const captured = typeof ytBuildOptions === "function" ? ytBuildOptions(yt.streams) : [];
    if (yt.resolved && yt.resolved.options.length) {
      pageTitle = yt.resolved.title;
      items.push(...yt.resolved.options);
    } else if (captured.length) {
      items.push(...captured.map(o => ({ ...o, referer: "https://www.youtube.com/" })));
      if (yt.resolving) items.push({ label: "Getting all qualities from FetchJet (yt-dlp)…", sub: "Captured streams are shown meanwhile", disabled: true });
    } else if (yt.resolving) {
      items.push({ label: "Getting video qualities…", sub: "This takes a few seconds", disabled: true });
    } else if (yt.resolveError) {
      items.push({ label: "Can't get this video", sub: yt.resolveError, disabled: true });
    } else if (yt.sabr) {
      items.push({ label: "Qualities come from FetchJet (yt-dlp) for this video", sub: "Start FetchJet (Settings → Download Tools once), then click Get qualities or reload the page", disabled: true });
    }
  }
  if (res && !yt) {
    if (res.resolved && res.resolved.options.length) { pageTitle = res.resolved.title; items.push(...res.resolved.options); }
    else if (res.resolving) items.push({ label: "Getting video qualities…", sub: "This takes a few seconds", disabled: true });
    else if (res.resolveError && !media.length) items.push({ label: "Can't get this video", sub: res.resolveError, disabled: true });
  }
  const resolvedFound = items.some(i => i.kind === "ytdlp");
  for (const m of media) {
    // when yt-dlp already found the real qualities, raw streams are noise
    if (resolvedFound && (m.kind === "HLS" || m.kind === "DASH" || m.kind === "Video")) continue;
    items.push({
      kind: "media", label: `${m.kind}${m.size ? " · " + fmtBytes(m.size) : ""} · ${m.name}`, sub: m.url,
      url: m.url, fileName: m.name, referer: m.pageUrl, size: m.size, ext: extensionOf(m.name)
    });
  }
  return { items, title: pageTitle };
}

async function pushToTab(tabId) {
  const settings = await getSettings();
  if (!settings.enabled) {
    updateBadge(tabId, 0);
    api.tabs.sendMessage(tabId, { action: "fetchjetItems", items: [] }).catch(() => {});
    return;
  }
  const { items } = await itemsForTab(tabId);
  updateBadge(tabId, items.filter(i => !i.disabled).length);
  api.tabs.sendMessage(tabId, { action: "fetchjetItems", items: settings.pagePanel ? items : [] }).catch(() => {});
}

async function clearTab(tabId) {
  seenStreams.delete(tabId);
  await Promise.all([clearMediaForTab(tabId), setTabState(YT_KEY, tabId, null), setTabState(RES_KEY, tabId, null)]);
  updateBadge(tabId, 0);
  api.tabs.sendMessage(tabId, { action: "fetchjetItems", items: [] }).catch(() => {});
}

/** The user picked one item (popup or page button). */
async function downloadItem(item, tab) {
  const pageUrl = (tab && tab.url) || item.referer || "";
  const title = item.title || (tab && tab.title) || "video";
  const clean = typeof ytCleanTitle === "function" ? ytCleanTitle(title) : title;
  if (item.kind === "ytdlp") {
    const fileName = `${clean}${item.quality ? " [" + item.quality + "]" : ""}.${item.ext || "mp4"}`;
    let r = await sendToFetchJet({ url: item.url, formatId: item.formatId, fileName, referer: pageUrl }, "quality");
    if (!r.ok && tab) {
      // The app's cache expired: inspect again and retry once with the same label.
      const change = await inspectWithApp(item.url);
      const again = change.resolved && change.resolved.options.find(o => o.label === item.label);
      if (again) r = await sendToFetchJet({ url: again.url, formatId: again.formatId, fileName, referer: pageUrl }, "quality");
    }
    return r;
  }
  if (item.kind === "captured") {
    const fileName = `${clean}${item.quality ? " [" + item.quality + "]" : ""}${item.videoOnly ? " (video only)" : ""}.${item.ext || "mp4"}`;
    return sendToFetchJet({ url: item.url, referer: "https://www.youtube.com/", fileName, direct: true }, "captured");
  }
  return sendToFetchJet({ url: item.url, referer: item.referer || pageUrl, fileName: item.fileName || "", direct: true }, "media");
}

/** YouTube tab: make sure the qualities are (being) fetched, whatever brought us here. */
async function ensureYoutubeResolved(tabId, url) {
  const videoId = ytVideoId(url || "");
  if (!videoId) return;
  const yt = await tabState(YT_KEY, tabId);
  if (yt && yt.videoId === videoId && (yt.resolved || yt.resolving || yt.resolveError)) return;
  resolveYoutube(tabId, url).catch(() => {});
}

api.tabs.onUpdated.addListener(async (tabId, info, tab) => {
  const url = info.url || (tab && tab.url) || "";
  if (info.url) {
    const yt = await tabState(YT_KEY, tabId);
    const newId = ytVideoId(info.url);
    if (!(yt && newId && yt.videoId === newId)) await clearTab(tabId); // not the same video
  } else if (info.status === "loading") {
    // Plain reload: same URL, new page - forget what the old page played.
    const yt = await tabState(YT_KEY, tabId);
    if (!yt || !ytVideoId(url)) await clearTab(tabId);
  }
  if (info.url || info.status === "loading" || info.status === "complete") ensureYoutubeResolved(tabId, url);
});
api.tabs.onRemoved.addListener((tabId) => { clearTab(tabId).catch(() => {}); });

// ---------------------------------------------------------------------------
// 3. Context menus & keyboard commands
// ---------------------------------------------------------------------------

const MENU = {
  link: "fetchjet-link",
  media: "fetchjet-media",
  image: "fetchjet-image",
  page: "fetchjet-page",
  allLinks: "fetchjet-all-links",
  selectionLinks: "fetchjet-selection-links",
  pageMedia: "fetchjet-page-media"
};

function installMenus() {
  if (!api.contextMenus) return;
  Promise.resolve(api.contextMenus.removeAll()).catch(() => {}).then(() => {
    // contextMenus.create is callback-style in every browser (also browser.* in Firefox).
    const create = (props) => { try { api.contextMenus.create(props, () => void api.runtime.lastError); } catch (_) {} };
    create({ id: MENU.link, title: "Download link with FetchJet", contexts: ["link"] });
    create({ id: MENU.media, title: "Download video/audio with FetchJet", contexts: ["video", "audio"] });
    create({ id: MENU.image, title: "Download image with FetchJet", contexts: ["image"] });
    create({ id: MENU.selectionLinks, title: "Download selected links with FetchJet", contexts: ["selection"] });
    create({ id: MENU.allLinks, title: "Download all links on this page with FetchJet", contexts: ["page", "frame"] });
    create({ id: MENU.page, title: "Download this page's video with FetchJet (yt-dlp)", contexts: ["page", "frame"] });
    create({ id: MENU.pageMedia, title: "Download detected media on this page", contexts: ["action"] });
  });
}

api.runtime.onInstalled.addListener(() => { installMenus(); updateActionBadgeForAll(); });
api.runtime.onStartup.addListener(() => { installMenus(); updateActionBadgeForAll(); });

if (api.contextMenus) {
  api.contextMenus.onClicked.addListener(async (info, tab) => {
    const pageUrl = (tab && tab.url) || info.pageUrl || "";
    switch (info.menuItemId) {
      case MENU.link:
        return sendToFetchJet({ url: info.linkUrl, referer: pageUrl }, "menu");
      case MENU.media:
      case MENU.image:
        return sendToFetchJet({ url: info.srcUrl, referer: pageUrl }, "menu");
      case MENU.page:
        return sendToFetchJet({ url: pageUrl }, "menu");
      case MENU.allLinks:
        return collectAndSendLinks(tab, { selectionOnly: false, frameId: info.frameId });
      case MENU.selectionLinks:
        return collectAndSendLinks(tab, { selectionOnly: true, frameId: info.frameId });
      case MENU.pageMedia:
        return sendDetectedMedia(tab && tab.id);
    }
  });
}

if (api.commands && api.commands.onCommand) {
  api.commands.onCommand.addListener(async (command, tab) => {
    if (!tab) { try { [tab] = await api.tabs.query({ active: true, currentWindow: true }); } catch (_) {} }
    if (!tab) return;
    if (command === "send-page") await sendToFetchJet({ url: tab.url }, "command");
    else if (command === "send-all-links") await collectAndSendLinks(tab, { selectionOnly: false });
    else if (command === "toggle-capture") {
      const s = await getSettings();
      await saveSettings({ enabled: !s.enabled });
      await notify(`Download capture ${!s.enabled ? "enabled" : "disabled"}.`);
    }
  });
}

async function collectAndSendLinks(tab, { selectionOnly = false, frameId } = {}) {
  if (!tab || tab.id == null) return { ok: false, count: 0 };
  let links = [];
  try {
    const opts = frameId != null ? { frameId } : {};
    const res = await api.tabs.sendMessage(tab.id, { action: "collectLinks", selectionOnly }, opts);
    links = (res && res.links) || [];
  } catch (_) {
    // Content script not injected (page loaded before install, or no host access yet).
    try {
      await api.scripting.executeScript({ target: { tabId: tab.id, allFrames: false }, files: ["content.js"] });
      const res = await api.tabs.sendMessage(tab.id, { action: "collectLinks", selectionOnly });
      links = (res && res.links) || [];
    } catch (_) {}
  }
  if (links.length === 0) {
    await notify(selectionOnly ? "No downloadable links in the selection." : "No downloadable links found on this page.");
    return { ok: false, count: 0 };
  }
  return sendBatchToFetchJet(links, tab.url || "");
}

async function sendDetectedMedia(tabId) {
  if (tabId == null) return { ok: false, count: 0 };
  const all = await sessionGet(MEDIA_KEY);
  const items = Object.values(all[tabId] || {});
  if (items.length === 0) { await notify("No media detected on this page yet. Start playing the video first."); return { ok: false, count: 0 }; }
  return sendBatchToFetchJet(items.map(i => ({ url: i.url, referer: i.pageUrl, fileName: i.name })));
}

/**
 * A bubble on a video that is not the page's main one was clicked: its own post / video
 * link (feeds), its own file, or the page when neither is known.
 */
async function resolveVideo(msg, tab) {
  const items = [];
  if (msg.src && /^https?:/i.test(msg.src)) {
    items.push({ kind: "media", label: `Video file · ${baseName((() => { try { return new URL(msg.src).pathname; } catch (_) { return msg.src; } })()) || "video"}`, sub: msg.src, url: msg.src, referer: tab && tab.url, fileName: "", ext: extensionOf(msg.src) || "mp4" });
  }
  if (msg.frame && tab && !msg.page) {
    // a player in a frame: what was found for the whole page is what it plays
    const found = (await itemsForTab(tab.id)).items.filter(i => !i.disabled);
    if (found.length) return { items: found };
  }
  const page = msg.page || (msg.src && !msg.frame ? "" : (tab && tab.url));
  if (page && /^https?:/i.test(page)) {
    if (!(await appCanInspect())) return items.length ? { items } : { error: "FetchJet is not running" };
    const r = await inspectWithApp(ytVideoId(page) ? `https://www.youtube.com/watch?v=${ytVideoId(page)}` : page);
    if (r.resolved && r.resolved.options.length) items.unshift(...r.resolved.options);
    else if (!items.length) return { error: r.resolveError || "Can't get this video" };
  }
  return items.length ? { items } : { error: "Can't get this video" };
}

/** Popup / page button: start (or redo) the yt-dlp inspection of the tab's page. */
async function resolveTab(tabId, force = true) {
  let tab; try { tab = await api.tabs.get(tabId); } catch (_) { return { ok: false }; }
  if (!tab || !tab.url) return { ok: false };
  if (ytVideoId(tab.url)) await resolveYoutube(tabId, tab.url, force); else await resolvePage(tabId, tab.url, force);
  return itemsForTab(tabId);
}

// ---------------------------------------------------------------------------
// 4. Messages from popup / options / content scripts
// ---------------------------------------------------------------------------

api.runtime.onMessage.addListener((request, sender, sendResponse) => {
  (async () => {
    switch (request && request.action) {
      case "sendToFetchJet":
      case "sendToKortex": {
        const referer = request.referer || (sender.tab && sender.tab.url) || "";
        return sendToFetchJet({ url: request.url, referer, fileName: request.fileName || "" }, "message");
      }
      case "sendBatch":
        return sendBatchToFetchJet(request.items || [], request.referer || (sender.tab && sender.tab.url) || "");
      case "sendDetectedMedia":
        return sendDetectedMedia(request.tabId);
      case "collectAndSendLinks": {
        const tab = await api.tabs.get(request.tabId);
        return collectAndSendLinks(tab, { selectionOnly: !!request.selectionOnly });
      }
      case "checkStatus": {
        const online = await pingApp();
        const config = online ? await fetchAppConfig() : null;
        return { online, config };
      }
      case "launchApp": {
        const launched = await launchApp(request.url || "");
        const online = launched ? await waitForApp(6000) : false;
        return { online };
      }
      case "getSettings":
        return getSettings();
      case "saveSettings":
        return saveSettings(request.settings || {});
      case "getDetectedMedia": {
        const all = await sessionGet(MEDIA_KEY);
        return { items: Object.values(all[request.tabId] || {}).sort((a, b) => (b.size || 0) - (a.size || 0)) };
      }
      case "getItems": {
        const tabId = request.tabId != null ? request.tabId : (sender.tab && sender.tab.id);
        if (tabId != null) { try { const t = await api.tabs.get(tabId); ensureYoutubeResolved(tabId, t.url); } catch (_) {} }
        return itemsForTab(tabId);
      }
      case "downloadItem": {
        let tab = sender.tab || null;
        if (!tab && request.tabId != null) { try { tab = await api.tabs.get(request.tabId); } catch (_) {} }
        return downloadItem(request.item, tab);
      }
      case "resolveTab":
        return resolveTab(request.tabId != null ? request.tabId : (sender.tab && sender.tab.id), request.force !== false);
      case "videoSeen": {
        // A page plays a video: ask yt-dlp for the qualities of the video's own link (the post in a
        // feed, the embed's address in a frame) or of the page.
        if (!sender.tab) return { ok: false };
        const tabUrl = sender.tab.url || "";
        const page = (request.page && /^https?:/i.test(request.page)) ? request.page : tabUrl;
        if (ytVideoId(tabUrl)) ensureYoutubeResolved(sender.tab.id, tabUrl);
        else if (ytVideoId(page)) resolvePage(sender.tab.id, `https://www.youtube.com/watch?v=${ytVideoId(page)}`, false, true).catch(() => {});
        else resolvePage(sender.tab.id, page).catch(() => {});
        return { ok: true };
      }
      case "resolveVideo":
        return resolveVideo(request, sender.tab || null);
      case "openPanel": {
        try { await api.tabs.sendMessage(request.tabId, { action: "openPanel" }); } catch (_) {}
        return { ok: true };
      }
      case "clearDetectedMedia":
        await clearTab(request.tabId);
        return { ok: true };
      case "holdBypass":
        // The user clicked a link while holding the bypass key: let the browser handle the
        // download that is about to be created.
        await sessionSet(HOLD_KEY, { until: Date.now() + 4000 });
        return { ok: true };
      case "bypassOnce":
        if (request.url) await addBypass(request.url);
        return { ok: true };
      default:
        return { ok: false, reason: "unknown action" };
    }
  })().then(sendResponse, (e) => sendResponse({ ok: false, reason: String(e && e.message || e) }));
  return true; // async response
});

updateActionBadgeForAll();
