// FetchJet browser extension - background (Chrome/Edge service worker, Firefox event page)
//
// Works unchanged in Chromium (MV3 service worker) and Firefox (MV3 event page):
// every API call goes through `api` (browser.* in Firefox, chrome.* elsewhere) and
// uses promises only.

// Firefox loads yt-core.js from the manifest; Chromium service workers import it here.
// (No try/catch on purpose: a missing file must fail loudly at install, not silently later.)
if (typeof importScripts === "function" && typeof fjT === "undefined") importScripts("i18n/i18n.js", "i18n/en.js", "i18n/ar.js", "i18n/fr.js", "i18n/de.js", "i18n/es.js", "i18n/pt.js", "i18n/it.js", "i18n/ru.js", "i18n/tr.js", "i18n/id.js", "i18n/zh.js", "i18n/ja.js", "i18n/ko.js", "i18n/hi.js", "i18n/fa.js");
if (typeof importScripts === "function" && typeof ytParseStream === "undefined") importScripts("yt-core.js");

const api = typeof browser !== "undefined" ? browser : chrome;
const IS_FIREFOX = typeof browser !== "undefined" && /firefox|thunderbird/i.test(navigator.userAgent);

const FETCHJET_PORT = 28450;
const FETCHJET_BASE = `http://127.0.0.1:${FETCHJET_PORT}`;
const NATIVE_HOST_NAME = "com.michelle.raouf.fetch_jet_downloader";
const PROTOCOL = "fetchjet://download?url=";
// Android freezes FetchJet while Firefox is in front, so asking it for qualities hangs until the
// app is opened. If the app answers a quick ping (its "Keep ready for the browser" setting is on)
// qualities are fetched as on desktop; otherwise the page button opens the app with the link and
// the app lists the qualities itself.
const ON_ANDROID = /android/i.test(navigator.userAgent);
// Safari on iPhone / iPad: iOS suspends FetchJet while Safari is in front and the app has no
// local server, so it is always "asleep": every link goes to the app through fetchjet://add
// (with its cookies and referer) and the app lists the qualities itself.
const ON_IOS = /iPhone|iPad|iPod/i.test(navigator.userAgent) ||
  (/Macintosh/i.test(navigator.userAgent) && (navigator.maxTouchPoints || 0) > 1);
const APP_SLEEPS = ON_ANDROID || ON_IOS;
const IOS_ADD = "fetchjet://add?json=";
async function appAwake() { return !ON_IOS && ON_ANDROID && await pingApp(1200); }

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
  if (ON_IOS) return false;
  try {
    const controller = new AbortController();
    const t = setTimeout(() => controller.abort(), timeoutMs);
    const res = await fetch(`${FETCHJET_BASE}/ping`, { method: "GET", signal: controller.signal });
    clearTimeout(t);
    return res.ok;
  } catch (_) { return false; }
}

// ---- pairing --------------------------------------------------------------------------
// The app answers only requests that carry its secret token (X-FetchJet-Token). The first
// time, POST /pair makes the app ask the user "Allow this browser extension?"; the token is
// kept in storage.local. X-FetchJet-Pairing tells the app this extension knows how to pair.

let appToken = null;
let pairing = null;

async function loadAppToken() {
  if (appToken !== null) return appToken;
  try {
    const got = await api.storage.local.get("appToken");
    appToken = (got && got.appToken) || "";
  } catch (_) { appToken = ""; }
  return appToken;
}

async function appHeaders(extra = {}) {
  const headers = { "X-FetchJet": "1", "X-FetchJet-Pairing": "1", ...extra };
  const token = await loadAppToken();
  if (token) headers["X-FetchJet-Token"] = token;
  return headers;
}

/** Asks the app for its token (the user answers in the app). One request at a time. */
function pairWithApp() {
  if (ON_IOS) return Promise.resolve(false);
  if (pairing) return pairing;
  pairing = (async () => {
    const controller = new AbortController();
    const t = setTimeout(() => controller.abort(), 125000);
    try {
      const res = await fetch(`${FETCHJET_BASE}/pair`, {
        method: "POST",
        headers: { "X-FetchJet": "1", "X-FetchJet-Pairing": "1" },
        signal: controller.signal
      });
      if (!res.ok) return false;
      const body = await res.json();
      if (!body || !body.token) return false;
      appToken = body.token;
      try { await api.storage.local.set({ appToken }); } catch (_) {}
      return true;
    } catch (_) { return false; }
    finally { clearTimeout(t); pairing = null; }
  })();
  return pairing;
}

/** fetch() to the app with the token; on 401 pairs once and tries again. */
async function appFetch(path, init = {}, timeoutMs = 0) {
  const send = async () => {
    const controller = new AbortController();
    const t = timeoutMs ? setTimeout(() => controller.abort(), timeoutMs) : null;
    if (init.signal) init.signal.addEventListener("abort", () => controller.abort());
    try {
      return await fetch(`${FETCHJET_BASE}${path}`, {
        ...init,
        headers: await appHeaders(init.headers || {}),
        signal: controller.signal
      });
    } finally { if (t) clearTimeout(t); }
  };
  const res = await send();
  if (res.status !== 401) return res;
  appToken = "";
  try { await api.storage.local.remove("appToken"); } catch (_) {}
  return (await pairWithApp()) ? await send() : res;
}

/** The app downloads a captured video-only stream together with its audio and merges them. */
let appMerges = false;

async function fetchAppConfig() {
  try {
    const controller = new AbortController();
    const t = setTimeout(() => controller.abort(), 1500);
    const res = await fetch(`${FETCHJET_BASE}/config`, { method: "GET", signal: controller.signal });
    clearTimeout(t);
    if (!res.ok) return null;
    const cfg = await res.json();
    if (cfg && cfg.language) rememberAppLanguage(cfg.language);
    appMerges = !!(cfg && cfg.pairedMerge);
    return cfg;
  } catch (_) { return null; }
}

function rememberAppLanguage(code) {
  fjUseLanguage(code);
  try { Promise.resolve(api.storage.local.set({ appLanguage: code })).catch(() => {}); } catch (_) {}
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
async function buildRequest({ url, referer = "", fileName = "", formatId = "", direct = false, openClip = false, audioUrl = "" }) {
  const body = {
    url,
    cookie: await cookieHeaderFor(url),
    referer: referer || "",
    userAgent: navigator.userAgent,
    fileName: fileName || ""
  };
  if (formatId) body.formatId = formatId; // quality chosen in the browser (yt-dlp format)
  if (direct) body.direct = true;         // start now, no Add dialog
  if (openClip) body.openClip = true;     // open the Add dialog with the clip range on
  if (audioUrl && isValidDownloadUrl(audioUrl)) body.audioUrl = audioUrl; // video-only + its audio: merged in the app
  return body;
}

async function postJson(path, body, timeoutMs = 2500) {
  return appFetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  }, timeoutMs);
}

/** Sends one link; compatible with older app builds that only know GET /download. */
async function deliverOne(request) {
  try {
    // With a chosen quality the app may have to re-run yt-dlp when its cache expired.
    const res = await postJson("/add", request, request.formatId ? 90000 : 2500);
    if (res.status === 409) return false; // quality gone: the caller re-inspects
    if (res.ok) return true;
    if (res.status === 404) {
      const legacy = `/download?url=${encodeURIComponent(request.url)}&cookie=${encodeURIComponent(request.cookie || "")}`;
      const r2 = await appFetch(legacy, { method: "GET" });
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
    // Firefox for Android only hands a link to another app from the visible tab.
    const onAndroid = /android/i.test(navigator.userAgent);
    const tab = await api.tabs.create({ url: PROTOCOL + encodeURIComponent(firstUrl || ""), active: onAndroid });
    if (tab && tab.id != null) setTimeout(() => api.tabs.remove(tab.id).catch(() => {}), 1500);
    return true;
  } catch (_) { return false; }
}

/**
 * iOS: hands one request (or a list) to the app through fetchjet://add. Safari asks "Open in
 * FetchJet?" and the app opens with the link(s), so nothing waits for an answer here.
 */
async function openAppWith(payload, tabId = null) {
  const url = IOS_ADD + encodeURIComponent(JSON.stringify(payload));
  try {
    let id = tabId;
    if (id == null) {
      const [tab] = await api.tabs.query({ active: true, currentWindow: true });
      id = tab && tab.id;
    }
    if (id != null) await api.tabs.update(id, { url });
    else await api.tabs.create({ url, active: true });
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
async function sendToFetchJet({ url, referer = "", fileName = "", formatId = "", direct = false, openClip = false, audioUrl = "" }, source = "") {
  if (!isValidDownloadUrl(url)) return { ok: false, reason: "unsupported url" };
  const request = await buildRequest({ url, referer, fileName, formatId, direct, openClip, audioUrl });
  if (ON_IOS) {
    return (await openAppWith(request)) ? { ok: true } : { ok: false, reason: "could not open FetchJet" };
  }

  if (await deliverOne(request)) return { ok: true };

  const launched = await launchApp(url);
  if (!launched) {
    await notify(fjT("notifyNotRunning"));
    return { ok: false, reason: "app not running" };
  }
  await notify(fjT("notifyLaunching"));
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
  if (ON_IOS) return { ok: await openAppWith(requests), count: requests.length };

  if (!(await pingApp())) {
    if (!(await launchApp(requests[0].url)) || !(await waitForApp())) {
      await notify(fjT("notifyNotRunning"));
      return { ok: false, reason: "app not running", count: 0 };
    }
  }
  const ok = await deliverBatch(requests);
  if (ok) await notify(requests.length === 1 ? fjT("notifyOneLinkSent") : fjT("notifyLinksSent", { n: requests.length }));
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
  if (!(await hasSiteAccess(item.referrer || targetUrl))) return false;
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

async function hasSiteAccess(url) {
  if (!api.permissions || !api.permissions.contains) return true;
  let origin;
  try { origin = new URL(url).origin; } catch (_) { return true; }
  if (!/^https?:/i.test(origin)) return true;
  try { return await api.permissions.contains({ origins: [origin + "/*"] }); } catch (_) { return true; }
}

async function takeOver(item) {
  const targetUrl = item.finalUrl || item.url;
  if (!HAS_DOWNLOADS) return;
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
  const nid = await notify(fjT("notifyCaptured"), {
    buttons: [{ title: fjT("btnDownloadInBrowser") }]
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
// Firefox for Android has no downloads API at all: nothing to capture there.
const HAS_DOWNLOADS = !!(api.downloads && api.downloads.onCreated);
const DECIDE_EARLY = HAS_DOWNLOADS && !!(api.downloads.onDeterminingFilename && api.downloads.onDeterminingFilename.addListener);
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

if (HAS_DOWNLOADS) api.downloads.onCreated.addListener((downloadItem) => {
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
    // The page's cookies and referer travel with the request: sites that need a login (Udemy,
    // Vimeo private videos...) then answer yt-dlp exactly like they answer the browser.
    const headers = { "X-FetchJet-Referer": pageUrl };
    try { const c = await cookieHeaderFor(pageUrl); if (c) headers["X-FetchJet-Cookie"] = c; } catch (_) {}
    const res = await appFetch(`/inspect?url=${encodeURIComponent(pageUrl)}`, { signal: controller.signal, headers });
    // The app answers errors as plain text ("missing X-FetchJet header"): never parse those as JSON.
    const text = await res.text();
    let data = null;
    try { data = JSON.parse(text); } catch (_) { data = { error: text.trim() }; }
    if (res.status === 403) return { resolveError: fjT("errRefused") };
    if (!res.ok || !Array.isArray(data)) return { resolveError: (data && data.error) || fjT("errHttp", { status: res.status }) };
    const title = (data.find(q => q.title) || {}).title || "";
    const options = [];
    const seen = new Set();
    for (const q of data) {
      if (!q.formatId) continue;
      const fmt = (q.format || "mp4").toUpperCase();
      // "1080p (Full HD - MP4)" already names the container: do not add "· MP4" again.
      const label = (q.quality || "").toUpperCase().includes(fmt) ? q.quality : `${q.quality || "video"} · ${fmt}`;
      if (seen.has(label)) continue;
      seen.add(label);
      options.push({
        kind: "ytdlp", label, sub: q.size > 0 ? fmtBytes(q.size) : "",
        url: pageUrl, formatId: String(q.formatId), quality: q.quality || "", ext: q.format || "mp4",
        size: q.size > 0 ? q.size : 0, duration: q.duration || 0, title
      });
    }
    if (!options.length) return { resolveError: fjT("errNoFormats") };
    return { resolved: { title, options } };
  } catch (e) {
    return { resolveError: (await pingApp()) ? String(e && e.message || e) : fjT("errNotRunning") };
  } finally { clearTimeout(t); }
}

/** A piece of a stream rather than a whole file: tiny, or a byte-range / segment URL. */
function isStreamFragment(m) {
  const u = String(m.url || "");
  if (/[?&](bytestart|byteend|range)=/i.test(u)) return true;
  if (/\.(m4s|ts)(\?|$)/i.test(u)) return true;
  if (/fbcdn\.net\/.*\/m\d{2,3}\//i.test(u) && (!m.size || m.size < 8 * 1048576)) return true;
  if (/googlevideo\.com\//i.test(u) && /[?&]range=/i.test(u)) return true;
  return !!m.size && m.size < 512 * 1024 && !/\.(mp4|mkv|webm|mp3|m4a)(\?|$)/i.test(u);
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
  if (APP_SLEEPS && !(await appAwake())) {
    if (!(current && current.videoId === videoId && current.openApp)) {
      await setTabState(YT_KEY, tabId, { ...((current && current.videoId === videoId) ? current : { videoId, sabr: false, streams: {} }), openApp: true });
      pushToTab(tabId);
    }
    return;
  }
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
  if (APP_SLEEPS && !(await appAwake())) {
    if (!(current && current.page === page && current.openApp)) {
      await setTabState(RES_KEY, tabId, { page, openApp: true });
      pushToTab(tabId);
    }
    return;
  }
  if (!force && current && current.page === page && (current.resolved || current.resolving || current.resolveError)) return;
  if (resolving.get(tabId) === page) return;
  if (!force) {
    const settings = await getSettings();
    if (!settings.autoResolve) return;
  }
  if (!(await appCanInspect())) {
    if (force) { await setTabState(RES_KEY, tabId, { page, resolving: false, resolveError: fjT("errNoExtractor") }); pushToTab(tabId); }
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
  if (APP_SLEEPS) {
    // A course / player page whose HLS or DASH playlist was captured (Udemy previews): the
    // playlist downloads, the page itself usually does not (yt-dlp gets 403 there).
    const hasPlaylist = media.some(m => m.kind === "HLS" || m.kind === "DASH");
    const page = (yt && yt.openApp) ? `https://www.youtube.com/watch?v=${yt.videoId}`
      : (res && res.openApp && !hasPlaylist ? res.page : "");
    if (page) items.push({ kind: "openApp", label: fjT("itemOpenApp"), sub: fjT("itemOpenAppSub"), url: page });
  }
  if (yt) {
    const captured = typeof ytBuildOptions === "function" ? ytBuildOptions(yt.streams, appMerges && !ON_IOS) : [];
    if (yt.resolved && yt.resolved.options.length) {
      pageTitle = yt.resolved.title;
      items.push(...yt.resolved.options);
    } else if (captured.length) {
      items.push(...captured.map(o => ({ ...o, referer: "https://www.youtube.com/" })));
      if (yt.resolving) items.push({ label: fjT("itemGettingAll"), sub: fjT("itemGettingAllSub"), disabled: true });
    } else if (yt.resolving) {
      items.push({ label: fjT("itemGetting"), sub: /android/i.test(navigator.userAgent) ? fjT("itemGettingPhone") : fjT("itemGettingDesktop"), disabled: true });
    } else if (yt.resolveError) {
      items.push({ label: fjT("errCantGet"), sub: yt.resolveError, disabled: true });
    } else if (yt.sabr && !(ON_IOS && yt.openApp)) {
      items.push({
        label: fjT("itemFromApp"),
        sub: /android/i.test(navigator.userAgent) ? fjT("itemFromAppPhone") : fjT("itemFromAppDesktop"),
        disabled: true
      });
    }
  }
  if (res && !yt) {
    if (res.resolved && res.resolved.options.length) { pageTitle = res.resolved.title; items.push(...res.resolved.options); }
    else if (res.resolving) items.push({ label: fjT("itemGetting"), sub: fjT("itemGettingDesktop"), disabled: true });
    else if (res.resolveError && !media.length) items.push({ label: fjT("errCantGet"), sub: res.resolveError, disabled: true });
    // Captured streams are shown, but say why the page itself gave no qualities (login, geo...)
    else if (res.resolveError) items.push({ label: fjT("itemPageUnavailable"), sub: res.resolveError, disabled: true });
  }
  const resolvedFound = items.some(i => i.kind === "ytdlp");
  const resolving = !!(res && res.resolving && !yt);
  for (const m of media) {
    // when yt-dlp already found the real qualities, raw streams are noise
    if (resolvedFound && (m.kind === "HLS" || m.kind === "DASH" || m.kind === "Video")) continue;
    // while FetchJet is still working out the page's qualities, the raw streams only confuse
    if (resolving && (m.kind === "HLS" || m.kind === "DASH" || m.kind === "Video")) continue;
    // DASH/HLS *fragments* the player fetched (Facebook: fbcdn ".../m311/..." pieces of a few
    // hundred KB, "bytestart=" ranges) are not files anyone wants
    if (m.kind === "Video" && isStreamFragment(m)) continue;
    // HLS/DASH: show the rendition (AVC_192 -> "192", 1080p, 720p...) read from the URL instead of
    // the playlist's hash; the size of a playlist file means nothing to the user.
    const isPl = m.kind === "HLS" || m.kind === "DASH";
    const qm = isPl ? /(\d{3,4})p|[_\/-](AVC|HEVC|VP9)[_-]?(\d{2,4})|[_\/-](\d{3,4})k/i.exec(m.url) : null;
    const quality = qm ? (qm[1] ? qm[1] + "p" : (qm[3] || qm[4])) : "";
    const label = isPl
      ? fjT("itemStreamPick", { kind: m.kind, quality: quality ? " · " + quality : "" })
      : `${m.kind}${m.size ? " · " + fmtBytes(m.size) : ""} · ${m.name}`;
    items.push({
      kind: "media", label, sub: m.url, quality,
      url: m.url, fileName: m.name, referer: m.pageUrl, size: m.size, ext: extensionOf(m.name)
    });
  }
  const list = await listUrlForTab(tabId);
  if (list) items.unshift({ kind: "playlist", label: fjT("itemPlaylist"), sub: fjT("itemPlaylistSub"), url: list.url });
  return { items, title: pageTitle };
}

/**
 * The tab's YouTube list (`list=`) as a link the app lists video by video. Not for a mix
 * (RD...): YouTube makes it endless (yt-dlp listed 762 videos for one).
 */
async function listUrlForTab(tabId) {
  let pageUrl = "";
  try { pageUrl = ((await api.tabs.get(tabId)) || {}).url || ""; } catch (_) { return null; }
  if (typeof ytIsPlaylistPage !== "function" || !ytIsPlaylistPage(pageUrl)) return null;
  const list = new URL(pageUrl).searchParams.get("list");
  if (!list || /^RD/.test(list)) return null;
  return { url: `https://www.youtube.com/playlist?list=${encodeURIComponent(list)}` };
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
// One tap can reach us several times (touch + click, two badges, a retry): the same item within
// a few seconds is sent once.
const recentSends = new Map();
function alreadySent(key) {
  const now = Date.now();
  for (const [k, t] of recentSends) if (now - t > 8000) recentSends.delete(k);
  if (recentSends.has(key)) return true;
  recentSends.set(key, now);
  return false;
}

async function downloadItem(item, tab) {
  const pageUrl = (tab && tab.url) || item.referer || "";
  if (alreadySent(`${item.url}|${item.formatId || ""}|${item.openClip ? "clip" : ""}`)) return { ok: true };
  // Prefer the page's own idea of what is playing (lesson title on course sites) over the tab
  // title, which is the course / site name there.
  let pageTitle = "";
  if (!item.title && tab && tab.id != null) {
    try { const info = await api.tabs.sendMessage(tab.id, { action: "pageInfo" }); pageTitle = (info && info.title) || ""; } catch (_) {}
  }
  const title = item.title || pageTitle || (tab && tab.title) || "video";
  const clean = typeof ytCleanTitle === "function" ? ytCleanTitle(title) : title;
  if (item.kind === "playlist") {
    // The app's Add dialog lists the videos; the user picks them and the quality there.
    return sendToFetchJet({ url: item.url, referer: pageUrl }, "playlist");
  }
  if (item.kind === "openApp") {
    // Android / iOS: the app opens with the link and shows the qualities itself.
    if (ON_IOS) {
      const ok = await openAppWith(await buildRequest({ url: item.url, referer: pageUrl }), tab && tab.id);
      return ok ? { ok: true } : { ok: false, reason: "could not open FetchJet" };
    }
    try {
      const t = await api.tabs.create({ url: PROTOCOL + encodeURIComponent(item.url), active: true });
      if (t && t.id != null) setTimeout(() => api.tabs.remove(t.id).catch(() => {}), 1500);
      return { ok: true };
    } catch (_) { return { ok: false, reason: "could not open FetchJet" }; }
  }
  if (item.kind === "ytdlp") {
    // "1080p · Full HD · H.264" -> only "1080p" goes into the file name.
    // "1080p (Full HD - MP4)" -> "1080p"; "Audio Only (M4A)" -> "Audio"; otherwise the first word
    const q = item.quality || "";
    const qm = /(\d{3,4}p|4K|8K)/i.exec(q);
    const qTag = qm ? qm[1] : (/audio/i.test(q) ? "Audio" : q.split(/[\s(·]/)[0].trim());
    const fileName = `${clean}${qTag ? " [" + qTag + "]" : ""}.${item.ext || "mp4"}`;
    let r = await sendToFetchJet({ url: item.url, formatId: item.formatId, fileName, referer: pageUrl, openClip: !!item.openClip }, "quality");
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
    return sendToFetchJet({ url: item.url, audioUrl: item.audioUrl || "", referer: "https://www.youtube.com/", fileName, direct: true }, "captured");
  }
  // A captured HLS/DASH playlist: the app merges it into one video; name it after the page
  // (the lecture / video title), not after the playlist's hash.
  let fileName = item.fileName || "";
  if (item.kind === "media" && /\.(m3u8|mpd)$/i.test(fileName)) {
    // The app lists the playlist's renditions and the user picks one there: no quality in the name.
    fileName = `${clean}.mp4`;
  }
  return sendToFetchJet({ url: item.url, referer: item.referer || pageUrl, fileName, direct: true }, "media");
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
    create({ id: MENU.link, title: fjT("menuLink"), contexts: ["link"] });
    create({ id: MENU.media, title: fjT("menuMedia"), contexts: ["video", "audio"] });
    create({ id: MENU.image, title: fjT("menuImage"), contexts: ["image"] });
    create({ id: MENU.selectionLinks, title: fjT("menuSelection"), contexts: ["selection"] });
    create({ id: MENU.allLinks, title: fjT("menuAllLinks"), contexts: ["page", "frame"] });
    create({ id: MENU.page, title: fjT("menuPage"), contexts: ["page", "frame"] });
    create({ id: MENU.pageMedia, title: fjT("menuPageMedia"), contexts: ["action"] });
  });
}

api.runtime.onInstalled.addListener(() => { installMenus(); updateActionBadgeForAll(); });
api.runtime.onStartup.addListener(() => { installMenus(); updateActionBadgeForAll(); });
fjOnLanguage(() => installMenus());
fetchAppConfig().catch(() => {});

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
      await notify(!s.enabled ? fjT("notifyCaptureOn") : fjT("notifyCaptureOff"));
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
      await api.scripting.executeScript({ target: { tabId: tab.id, allFrames: false }, files: ["i18n/i18n.js", "i18n/en.js", "i18n/ar.js", "i18n/fr.js", "i18n/de.js", "i18n/es.js", "i18n/pt.js", "i18n/it.js", "i18n/ru.js", "i18n/tr.js", "i18n/id.js", "i18n/zh.js", "i18n/ja.js", "i18n/ko.js", "i18n/hi.js", "i18n/fa.js", "content.js"] });
      const res = await api.tabs.sendMessage(tab.id, { action: "collectLinks", selectionOnly });
      links = (res && res.links) || [];
    } catch (_) {}
  }
  if (links.length === 0) {
    await notify(selectionOnly ? fjT("notifyNoLinksSelection") : fjT("notifyNoLinksPage"));
    return { ok: false, count: 0 };
  }
  return sendBatchToFetchJet(links, tab.url || "");
}

async function sendDetectedMedia(tabId) {
  if (tabId == null) return { ok: false, count: 0 };
  const all = await sessionGet(MEDIA_KEY);
  const items = Object.values(all[tabId] || {});
  if (items.length === 0) { await notify(fjT("notifyNoMedia")); return { ok: false, count: 0 }; }
  return sendBatchToFetchJet(items.map(i => ({ url: i.url, referer: i.pageUrl, fileName: i.name })));
}

/**
 * A bubble on a video that is not the page's main one was clicked: its own post / video
 * link (feeds), its own file, or the page when neither is known.
 */
async function resolveVideo(msg, tab) {
  const items = [];
  if (msg.src && /^https?:/i.test(msg.src)) {
    items.push({ kind: "media", label: fjT("itemVideoFile", { name: baseName((() => { try { return new URL(msg.src).pathname; } catch (_) { return msg.src; } })()) || "video" }), sub: msg.src, url: msg.src, referer: tab && tab.url, fileName: "", ext: extensionOf(msg.src) || "mp4" });
  }
  if (msg.frame && tab && !msg.page) {
    // a player in a frame: what was found for the whole page is what it plays
    const found = (await itemsForTab(tab.id)).items.filter(i => !i.disabled);
    if (found.length) return { items: found };
  }
  const page = msg.page || (msg.src && !msg.frame ? "" : (tab && tab.url));
  if (page && /^https?:/i.test(page)) {
    if (!(await appCanInspect())) return items.length ? { items } : { error: fjT("errNotRunning"), notRunning: true };
    const r = await inspectWithApp(ytVideoId(page) ? `https://www.youtube.com/watch?v=${ytVideoId(page)}` : page);
    if (r.resolved && r.resolved.options.length) items.unshift(...r.resolved.options);
    else if (!items.length) return { error: r.resolveError || fjT("errCantGet") };
  }
  return items.length ? { items } : { error: fjT("errCantGet") };
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
        if (ON_IOS) return { online: true, ios: true, config: null };
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
      case "mediaSeen": {
        // iOS: media URLs the page loaded, read by the content script (no webRequest there).
        if (!ON_IOS || !sender.tab || sender.tab.id == null) return { ok: false };
        for (const url of (request.urls || []).slice(0, 20)) {
          await sniffMedia({ tabId: sender.tab.id, statusCode: 200, url, responseHeaders: [], documentUrl: sender.url || "" });
        }
        return { ok: true };
      }
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
