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
  "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "mpg", "mpeg", "3gp", "ogv",
  "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "aiff", "mka",
  "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "zst", "lz", "lzma", "cab",
  "pdf", "epub", "mobi", "djvu", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "csv",
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
  limitToListed: false,
  excludedHosts: [],          // never capture on these hosts (suffix match)
  holdKey: "alt",             // hold this key while clicking to download with the browser
  notifications: true,
  autoLaunch: true,           // start FetchJet when it is not running (off = browser downloads it)
  openDirectly: false,        // open the app with fetchjet://download instead of the silent local send
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
  if (!(lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("magnet:") || /^ftps?:\/\//.test(lower))) return false;
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

/** Sent with every request so the app knows this browser has the extension, and which version. */
const EXT_VERSION = (() => { try { return api.runtime.getManifest().version || ""; } catch (_) { return ""; } })();

async function appHeaders(extra = {}) {
  const headers = { "X-FetchJet": "1", "X-FetchJet-Pairing": "1", "X-FetchJet-Ext": EXT_VERSION, ...extra };
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
        headers: { "X-FetchJet": "1", "X-FetchJet-Pairing": "1", "X-FetchJet-Ext": EXT_VERSION },
        signal: controller.signal
      });
      if (!res.ok) return false;
      const body = await res.json();
      if (!body || !body.token) return false;
      appToken = body.token;
      try { await api.storage.local.set({ appToken }); } catch (_) {}
      retryAfterPairing().catch(() => {});
      return true;
    } catch (_) { return false; }
    finally { clearTimeout(t); pairing = null; }
  })();
  return pairing;
}

/** The user just allowed this browser: every tab that was waiting for it looks its qualities up again. */
async function retryAfterPairing() {
  const youtube = await sessionGet(YT_KEY);
  for (const id of Object.keys(youtube)) {
    const state = youtube[id];
    if (!state || !state.needsPairing || !state.videoId) continue;
    await setTabState(YT_KEY, Number(id), { ...state, needsPairing: false, resolveError: null, resolved: null, resolving: false });
    resolveYoutube(Number(id), `https://www.youtube.com/watch?v=${state.videoId}`, true).catch(() => {});
  }
  const pages = await sessionGet(RES_KEY);
  for (const id of Object.keys(pages)) {
    const state = pages[id];
    if (!state || !state.needsPairing || !state.page) continue;
    await setTabState(RES_KEY, Number(id), { ...state, needsPairing: false, resolveError: null, resolved: null, resolving: false });
    resolvePage(Number(id), state.page, true).catch(() => {});
  }
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
  pairWithApp();
  return res;
}

/** The app downloads a captured video-only stream together with its audio and merges them. */
let appMerges = false;
/** The app lists a course the extension read (`playlist` in the request) without yt-dlp. */
let appCourses = false;

async function fetchAppConfig() {
  try {
    const controller = new AbortController();
    const t = setTimeout(() => controller.abort(), 1500);
    const token = await loadAppToken();
    const res = await fetch(`${FETCHJET_BASE}/config`, {
      method: "GET",
      headers: token ? { "X-FetchJet-Token": token, "X-FetchJet-Ext": EXT_VERSION } : { "X-FetchJet-Ext": EXT_VERSION },
      signal: controller.signal
    });
    clearTimeout(t);
    if (!res.ok) return null;
    const cfg = await res.json();
    if (cfg && cfg.language) rememberAppLanguage(cfg.language);
    if (cfg && cfg.theme) rememberAppTheme(cfg.theme);
    appMerges = !!(cfg && cfg.pairedMerge);
    const courses = !!(cfg && cfg.coursePlaylist);
    if (courses !== appCourses) {
      appCourses = courses;
      api.tabs.query({ active: true, currentWindow: true }).then(([t]) => t && refreshMenus(t.id)).catch(() => {});
    }
    return cfg;
  } catch (_) { return null; }
}

function rememberAppTheme(theme) {
  try { Promise.resolve(api.storage.local.set({ appTheme: theme === "light" ? "light" : "dark" })).catch(() => {}); } catch (_) {}
}

function rememberAppLanguage(code) {
  fjUseLanguage(code);
  try { Promise.resolve(api.storage.local.set({ appLanguage: code })).catch(() => {}); } catch (_) {}
}

/**
 * The cookie store of who asked (`from`: a tab, a tab id, several tab ids or a browser download).
 * A private window and a Firefox container have their own store. undefined = the browser's
 * normal store; null = a private one that cannot be read here: no cookies at all, never the
 * normal window's.
 */
async function cookieStoreOf(from) {
  if (from == null || !api.cookies) return undefined;
  if (Array.isArray(from)) {
    const stores = [];
    for (const one of from) stores.push(await cookieStoreOf(one));
    return stores.every(s => s === stores[0]) ? stores[0] : null;
  }
  if (typeof from === "number") { try { from = await api.tabs.get(from); } catch (_) { return undefined; } }
  if (!from) return undefined;
  if (from.cookieStoreId) return from.cookieStoreId;
  if (!from.incognito) return undefined;
  if (IS_FIREFOX) return "firefox-private";
  try {
    const tabIds = from.windowId != null ? [from.id]
      : (await api.tabs.query({})).filter(t => t.incognito).map(t => t.id);
    const stores = await api.cookies.getAllCookieStores();
    const store = (stores || []).find(s => (s.tabIds || []).some(id => tabIds.includes(id)));
    return store ? store.id : null;
  } catch (_) { return null; }
}

const siteOf = (host) => String(host || "").toLowerCase().split(".").slice(-2).join(".");

async function pageCookieFallback(url, from) {
  if (!ON_IOS) return "";
  try {
    const one = Array.isArray(from) ? from[0] : from;
    const tabId = typeof one === "number" ? one : one && one.id;
    if (tabId == null) return "";
    const res = await api.tabs.sendMessage(tabId, { action: "pageCookie" }, { frameId: 0 });
    if (!res || !res.cookie || !res.page) return "";
    return siteOf(new URL(res.page).hostname) === siteOf(new URL(url).hostname) ? res.cookie : "";
  } catch (_) { return ""; }
}

async function cookieHeaderFor(url, from = null) {
  const settings = await getSettings();
  if (!settings.sendCookies) return "";
  if (!api.cookies) return pageCookieFallback(url, from);
  try {
    const storeId = await cookieStoreOf(from);
    if (storeId === null) return "";
    const cookies = await api.cookies.getAll(storeId ? { url, storeId } : { url });
    if (!cookies || cookies.length === 0) return pageCookieFallback(url, from);
    return cookies.map(c => `${c.name}=${c.value}`).join("; ");
  } catch (_) { return pageCookieFallback(url, from); }
}

/** Builds the JSON the app's /add and /links endpoints accept. */
async function buildRequest({ url, referer = "", fileName = "", formatId = "", direct = false, openClip = false, audioUrl = "", playlist = null, group = "", from = null }) {
  const body = {
    url,
    cookie: await cookieHeaderFor(url, from),
    referer: referer || "",
    userAgent: navigator.userAgent,
    fileName: fileName || ""
  };
  if (formatId) body.formatId = formatId; // quality chosen in the browser (yt-dlp format)
  if (direct) body.direct = true;         // start now, no Add dialog
  if (openClip) body.openClip = true;     // open the Add dialog with the clip range on
  if (audioUrl && isValidDownloadUrl(audioUrl)) body.audioUrl = audioUrl; // video-only + its audio: merged in the app
  if (playlist) body.playlist = playlist; // the videos, already read here (Udemy course)
  if (group) body.group = group.slice(0, 150); // several links of one page: their folder in the app
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
let lastDeliverPairing = false;

async function deliverOne(request) {
  lastDeliverPairing = false;
  try {
    // With a chosen quality the app may have to re-run yt-dlp when its cache expired.
    const res = await postJson("/add", request, request.formatId ? 90000 : 2500);
    if (res.status === 409) return false; // quality gone: the caller re-inspects
    if (res.ok) return true;
    if (res.status === 401) { lastDeliverPairing = true; return false; }
    if (res.status === 404) {
      const legacy = `/download?url=${encodeURIComponent(request.url)}&cookie=${encodeURIComponent(request.cookie || "")}`;
      const r2 = await appFetch(legacy, { method: "GET" });
      if (r2.status === 401) lastDeliverPairing = true;
      return r2.ok;
    }
    return false;
  } catch (_) { return false; }
}

async function deliverBatch(requests) {
  lastDeliverPairing = false;
  try {
    const res = await postJson("/links", requests, 5000);
    if (res.ok) return true;
    if (res.status === 401) { lastDeliverPairing = true; return false; }
    if (res.status === 404) { // old app: one by one
      for (const r of requests) await deliverOne(r);
      return true;
    }
    return false;
  } catch (_) { return false; }
}

// The browser asks "Open FetchJet?" the first time an extension opens fetchjet:// (the answer
// belongs to the extension, not to a site). That question lives in the tab that opened the link:
// closing the tab early (or leaving it in the background) hides it and the app never starts.
// So the tab stays until the app answers, and is brought to the front when it is still not up
// after a moment, so the question is seen. It is closed as soon as the app is online.
async function keepLaunchTab(tabId, alreadyFront) {
  const close = () => api.tabs.remove(tabId).catch(() => {});
  const start = Date.now();
  let shown = !!alreadyFront;
  while (Date.now() - start < 25000) {
    if (await pingApp(500)) { setTimeout(close, 800); return; }
    if (!shown && Date.now() - start > 1500) {
      shown = true;
      try { await api.tabs.update(tabId, { active: true }); } catch (_) {}
    }
    await new Promise(r => setTimeout(r, 500));
  }
  close();
}

async function launchApp(firstUrl) {
  const settings = await getSettings();
  if (!settings.autoLaunch) return false;

  try {
    // Firefox for Android only hands a link to another app from the visible tab.
    const onAndroid = /android/i.test(navigator.userAgent);
    const tab = await openProtocolTab(PROTOCOL + encodeURIComponent(firstUrl || ""), onAndroid, 0);
    if (tab && tab.id != null) keepLaunchTab(tab.id, onAndroid);
    return true;
  } catch (_) { return false; }
}

/**
 * iOS: hands one request (or a list) to the app through fetchjet://add. Safari asks "Open in
 * FetchJet?" and the app opens with the link(s), so nothing waits for an answer here.
 */
const IOS_SESSION_SITES = /(^|\.)zoom\.us$/i;

function iosSessionPage(request) {
  try {
    const target = new URL(request.url);
    if (!IOS_SESSION_SITES.test(target.hostname)) return "";
    if (/^\/rec\//i.test(target.pathname)) return request.url;
    const page = new URL(request.referer || "");
    return /^https?:$/.test(page.protocol) && IOS_SESSION_SITES.test(page.hostname) ? page.href : "";
  } catch (_) { return ""; }
}

function openAppWith(payload, tabId = null) {
  return openAppAt(IOS_ADD + encodeURIComponent(JSON.stringify(payload)), tabId);
}

async function openAppAt(url, tabId = null) {
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

async function openProtocolTab(url, active, removeAfterMs = 6000) {
  const tab = await api.tabs.create({ url, active });
  if (removeAfterMs && tab && tab.id != null) setTimeout(() => api.tabs.remove(tab.id).catch(() => {}), removeAfterMs);
  return tab;
}

const DIRECT_BATCH_MAX = 3;

function isDirectOpenLink(request) {
  return isPlainLink(request) && !request.fileName && !request.referer;
}

function isPlainLink(request) {
  return !request.formatId && !request.audioUrl && !request.playlist && !request.openClip && !request.direct;
}

async function waitForApp(maxMs = 12000) {
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
async function sendToFetchJet({ url, referer = "", fileName = "", formatId = "", direct = false, openClip = false, audioUrl = "", playlist = null, from = null, quiet = false }, source = "") {
  if (!isValidDownloadUrl(url)) return { ok: false, reason: "unsupported url" };
  const settings = await getSettings();
  const request = await buildRequest({ url, referer, fileName, formatId, direct, openClip, audioUrl, playlist, from });
  if (ON_IOS) {
    const page = iosSessionPage(request);
    if (page) return (await openAppAt("fetchjet://browse?url=" + encodeURIComponent(page))) ? { ok: true } : { ok: false, reason: "could not open FetchJet" };
    return (await openAppWith(request)) ? { ok: true } : { ok: false, reason: "could not open FetchJet" };
  }

  if (settings.openDirectly && isDirectOpenLink(request)) {
    try {
      await openProtocolTab(PROTOCOL + encodeURIComponent(request.url), true);
      return { ok: true };
    } catch (_) { return { ok: false, reason: "could not open FetchJet" }; }
  }

  if (await deliverOne(request)) return { ok: true };
  if (lastDeliverPairing) {
    if (!quiet) await notify(fjT("errNeedsPairing"));
    return { ok: false, reason: "pairing required" };
  }

  const launched = await launchApp(url);
  if (!launched) {
    if (!quiet) await notify(fjT("notifyNotRunning"));
    return { ok: false, reason: "app not running" };
  }
  await notify(fjT("notifyLaunching"));
  if (await waitForApp()) {
    // The launcher may already have passed the URL on the command line; /add is idempotent
    // enough (duplicate check in the app) that re-sending with cookies is the safer choice.
    const ok = await deliverOne(request);
    return ok ? { ok } : { ok, reason: lastDeliverPairing ? "pairing required" : "" };
  }
  return { ok: false, reason: "app did not start" };
}

async function sendBatchToFetchJet(items, referer = "", group = "", from = null) {
  const unique = new Map();
  for (const it of items) {
    const url = typeof it === "string" ? it : it.url;
    if (isValidDownloadUrl(url) && !unique.has(url)) {
      unique.set(url, { url, referer: (it && it.referer) || referer, fileName: (it && it.fileName) || "", audioUrl: (it && it.audioUrl) || "" });
    }
  }
  if (unique.size === 0) return { ok: false, reason: "no links", count: 0 };
  const requests = [];
  for (const it of unique.values()) requests.push(await buildRequest({ ...it, group, from }));
  if (ON_IOS) return { ok: await openAppWith(requests), count: requests.length };

  if (requests.length <= DIRECT_BATCH_MAX && !requests.some(r => r.fileName) && (await getSettings()).openDirectly) {
    let opened = 0;
    for (const r of requests) {
      try {
        await openProtocolTab(PROTOCOL + encodeURIComponent(r.url), opened === 0);
        opened++;
      } catch (_) {}
      await new Promise(res => setTimeout(res, 400));
    }
    return { ok: opened > 0, count: opened };
  }

  if (!(await pingApp())) {
    if (!(await launchApp(requests[0].url)) || !(await waitForApp())) {
      await notify(fjT("notifyNotRunning"));
      return { ok: false, reason: "app not running", count: 0 };
    }
  }
  const ok = await deliverBatch(requests);
  if (!ok && lastDeliverPairing) {
    await notify(fjT("errNeedsPairing"));
    return { ok: false, reason: "pairing required", count: 0 };
  }
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
  if (await wasPosted(targetUrl)) return false;                  // a form's answer: only the browser can ask for it again
  if (await takeBypass(targetUrl, item.url)) return false;      // "Download in browser instead"
  if (await consumeHold()) return false;                         // user held the bypass key
  if (isExcludedHost(targetUrl, settings) || isExcludedHost(item.referrer || "", settings)) return false;
  if (!(await hasSiteAccess(item.referrer || targetUrl))) return false;
  const size = item.totalBytes > 0 ? item.totalBytes : (item.fileSize > 0 ? item.fileSize : -1);
  if (settings.minSizeKB > 0 && size >= 0 && size < settings.minSizeKB * 1024) return false;
  const fileName = baseName(item.filename);
  // A browser download is already "a file": the extension list only filters when the user
  // narrowed it and the name has an extension we can judge.
  if (settings.limitToListed && (settings.extensions || []).length > 0 && fileName && extensionOf(fileName) &&
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

const HAND_OVER_MS = 20000;

async function downloadNow(id) {
  try { return (await api.downloads.search({ id }))[0] || null; } catch (_) { return null; }
}

/**
 * The browser keeps its download, paused, until the app really has the link: only then it is
 * cancelled and removed here. When the app did not take it the browser's own download goes on.
 * Answers true when the app took it.
 */
async function takeOver(item) {
  if (!HAS_DOWNLOADS) return false;
  const targetUrl = item.finalUrl || item.url;
  const before = await downloadNow(item.id);
  if (!before || before.state !== "in_progress") return false; // finished, failed or removed meanwhile
  let held = false;
  try { await api.downloads.pause(item.id); held = true; } catch (_) {}
  const fileName = baseName(item.filename);
  const result = await Promise.race([
    sendToFetchJet({ url: targetUrl, referer: item.referrer || "", fileName, from: item, quiet: true }, "download")
      .catch(() => ({ ok: false, reason: "" })),
    new Promise(r => setTimeout(() => r({ ok: false, reason: "no answer" }), HAND_OVER_MS))
  ]);
  if (!result.ok) {
    await giveBack(item, held);
    await notify(fjT("notifyKeptInBrowser", { reason: fjReason(result.reason || "refused") }));
    return false;
  }
  const after = await downloadNow(item.id);
  if (after && after.state !== "complete") {
    try { await api.downloads.cancel(item.id); } catch (_) {}
    try { await api.downloads.erase({ id: item.id }); } catch (_) {}
  }
  const nid = await notify(fjT("notifyCaptured"), {
    buttons: [{ title: fjT("btnDownloadInBrowser") }]
  });
  if (nid) {
    const map = await sessionGet(NOTIFY_KEY);
    map[nid] = targetUrl;
    await sessionSet(NOTIFY_KEY, map);
  }
  return true;
}

/** The app did not take it: the browser's download continues, or starts again when it cannot. */
async function giveBack(item, held) {
  const targetUrl = item.finalUrl || item.url;
  const now = await downloadNow(item.id);
  if (!now || now.state === "complete") return;
  if (now.state === "in_progress" && !now.paused) return;
  if (now.paused || now.canResume) {
    try { await api.downloads.resume(item.id); return; } catch (_) {}
  }
  // Firefox reports its own pause as USER_CANCELED when the server gave nothing to resume from.
  if (/USER_CANCELED/i.test(now.error || "") && !(IS_FIREFOX && held)) return;
  if (item.incognito && !IS_FIREFOX) return; // Chromium would start it again in a normal window
  await addBypass(targetUrl);
  try { await api.downloads.download(item.incognito ? { url: targetUrl, incognito: true } : { url: targetUrl }); } catch (_) {}
}

// Chromium (Chrome, Edge, Brave, Opera, Vivaldi): decide before the browser asks where to
// save, so its "Save as" window never appears for files FetchJet takes. Firefox has no such
// moment and decides when the download is created.
// Firefox for Android has no downloads API at all: nothing to capture there.
const HAS_DOWNLOADS = !!(api.downloads && api.downloads.onCreated);
const DECIDE_EARLY = HAS_DOWNLOADS && !!(api.downloads.onDeterminingFilename && api.downloads.onDeterminingFilename.addListener);

const firstLook = new Map();

function knownAtStart(item) {
  const settings = settingsCache;
  if (!settings || !settings.enabled || !settings.captureDownloads || settings.minSizeKB > 0) return false;
  const targetUrl = item.finalUrl || item.url;
  if (!targetUrl || !/^https?:/i.test(targetUrl)) return false;
  return hasWantedExtension(targetUrl, baseName(item.filename), settings);
}

function takeAtStart(item) {
  if (!knownAtStart(item)) return null;
  const look = { taken: Promise.resolve(false) };
  look.decision = shouldTake(item).then((take) => {
    if (take) look.taken = takeOver(item).catch(() => false);
    return take;
  }).catch(() => false);
  firstLook.set(item.id, look);
  setTimeout(() => firstLook.delete(item.id), 60000);
  return look;
}

// A download answered to a form's POST cannot be asked again by the app with a plain GET, and
// sending the form again could repeat a purchase or an action: it stays in the browser.
const POST_KEY = "fetchjet_post_urls";
const POST_MEMORY_MS = 120000;
const postedUrls = new Map();
const withoutHash = (url) => String(url || "").split("#")[0];

async function wasPosted(url) {
  const key = withoutHash(url);
  const fresh = (t) => !!t && Date.now() - t < POST_MEMORY_MS;
  if (postedUrls.has(key)) return fresh(postedUrls.get(key));
  return fresh((await sessionGet(POST_KEY))[key]);
}

function notePost(url, posted) {
  const key = withoutHash(url);
  if (!posted && !postedUrls.has(key)) return;
  postedUrls.set(key, posted ? Date.now() : 0);
  serial(async () => {
    const map = await sessionGet(POST_KEY);
    const now = Date.now();
    for (const [u, t] of Object.entries(map)) if (now - t > POST_MEMORY_MS) delete map[u];
    for (const [u, t] of postedUrls) if (now - t > POST_MEMORY_MS) postedUrls.delete(u);
    if (posted) map[key] = now; else delete map[key];
    await sessionSet(POST_KEY, map);
  }).catch(() => {});
}

if (HAS_DOWNLOADS && api.webRequest && api.webRequest.onBeforeRequest) {
  api.webRequest.onBeforeRequest.addListener((d) => {
    const method = String(d.method || "GET").toUpperCase();
    notePost(d.url, method !== "GET" && method !== "HEAD");
  }, { urls: ["http://*/*", "https://*/*"], types: ["main_frame", "sub_frame"] });
}

if (DECIDE_EARLY) {
  api.downloads.onDeterminingFilename.addListener((item, suggest) => {
    let answered = false;
    const keep = () => { if (!answered) { answered = true; try { suggest(); } catch (_) {} } };
    const guard = setTimeout(keep, 4000); // never leave the browser waiting for the decision
    const early = firstLook.get(item.id);
    (early ? early.decision : shouldTake(item)).then((take) => {
      clearTimeout(guard);
      if (!take || (answered && !early)) return keep();
      // No "Save as" window while the app is asked; when it did not take the file the browser goes on.
      return (early ? early.taken : takeOver(item)).then(keep, keep);
    }).catch(() => { clearTimeout(guard); keep(); });
    return true; // answered asynchronously
  });
}

if (HAS_DOWNLOADS) api.downloads.onCreated.addListener((downloadItem) => {
  if (takeAtStart(downloadItem)) return;
  if (DECIDE_EARLY) return;
  // Firefox: give it a moment to resolve the final URL / filename / size.
  setTimeout(async () => {
    let item;
    try { [item] = await api.downloads.search({ id: downloadItem.id }); } catch (_) { return; }
    if (item && await shouldTake(item)) await takeOver(item);
  }, 400);
});

getSettings().catch(() => {});

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
  if (CANVA_ORIGIN.test(`${details.initiator || ""} ${details.originUrl || ""} ${details.documentUrl || ""} ${url}`) &&
    !/^(image|text|font)\/|json|javascript|css|html/.test(header(details.responseHeaders, "content-type").toLowerCase())) {
    let tabUrl = "";
    try { tabUrl = ((await api.tabs.get(details.tabId)) || {}).url || ""; } catch (_) {}
    if (canvaDesign(tabUrl)) return addCanvaMedia(details, tabUrl);
  }

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

/**
 * The badge changes only when its number does: every captured video piece used to set it again
 * (twice a second on a playing course), and Firefox for Android redraws the extension's menu
 * entry each time, so a tap on it could be lost and the popup did not open.
 */
const shownBadges = new Map();
function updateBadge(tabId, count) {
  if (!api.action) return;
  const text = count > 0 ? String(count) : "";
  if (shownBadges.get(tabId) === text) return;
  const first = !shownBadges.has(tabId);
  shownBadges.set(tabId, text);
  try {
    api.action.setBadgeText({ tabId, text }).catch(() => {});
    if (first) api.action.setBadgeBackgroundColor({ tabId, color: "#6366f1" }).catch(() => {});
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
const DRM_KEY = "fetchjet_drm_by_tab";
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
async function inspectWithApp(pageUrl, from = null) {
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), 90000);
  try {
    // The page's cookies and referer travel with the request: sites that need a login (Udemy,
    // Vimeo private videos...) then answer yt-dlp exactly like they answer the browser.
    const headers = { "X-FetchJet-Referer": pageUrl };
    try { const c = await cookieHeaderFor(pageUrl, from || tabsResolving(pageUrl)); if (c) headers["X-FetchJet-Cookie"] = c; } catch (_) {}
    const res = await appFetch(`/inspect?url=${encodeURIComponent(pageUrl)}`, { signal: controller.signal, headers });
    if (res.status === 401) return { resolveError: fjT("errNeedsPairing"), needsPairing: true };
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

function tabsResolving(pageUrl) {
  const videoId = ytVideoId(pageUrl);
  const tabs = [];
  for (const [tabId, what] of resolving) if (what === pageUrl || (videoId && what === videoId)) tabs.push(tabId);
  return tabs;
}

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
  if (UDEMY_COURSE_PAGE.test(page) || canvaDesign(page)) return;
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
  const canva = await canvaForTab(tabId);
  if (canva) return canva;
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
      kind: "media", label, sub: m.url, quality, stream: isPl,
      url: m.url, fileName: m.name, referer: m.pageUrl, size: isPl ? 0 : m.size, ext: extensionOf(m.name)
    });
  }
  // A player loads its main playlist and then one rendition of it: both are the same video.
  // The main one (no quality in its address) lets FetchJet offer every quality: keep only it.
  const mainStream = items.find(i => i.stream && !i.quality);
  if (mainStream) for (let i = items.length - 1; i >= 0; i--) if (items[i].stream && items[i] !== mainStream) items.splice(i, 1);
  const list = await listUrlForTab(tabId);
  if (list) items.unshift({ kind: "playlist", label: fjT("itemPlaylist"), sub: fjT("itemPlaylistSub"), url: list.url });
  const course = appCourses && !ON_IOS ? await udemyCourseForTab(tabId) : null;
  if (course) items.unshift({ kind: "course", label: fjT("itemCourse"), sub: fjT("itemCourseSub"), url: course.url });
  const drm = await tabState(DRM_KEY, tabId);
  if (drm && !items.some(i => i.kind === "ytdlp")) {
    const keep = items.filter(i => i.kind === "playlist" || i.kind === "course");
    return { items: [...keep, { kind: "drm", label: fjT("itemDrm"), sub: fjT("itemDrmSub"), disabled: true }], title: pageTitle };
  }
  return { items, title: pageTitle };
}

/** A Udemy course page (`udemy.com/course/<name>/...`, business sub-domains too). */
const UDEMY_COURSE_PAGE = /^https:\/\/([\w-]+\.)?udemy\.com\/course\/[^/?#]+/i;

async function udemyCourseForTab(tabId) {
  let pageUrl = "";
  try { pageUrl = ((await api.tabs.get(tabId)) || {}).url || ""; } catch (_) { return null; }
  const m = /^https:\/\/([\w-]+\.)?udemy\.com\/course\/([^/?#]+)/i.exec(pageUrl);
  if (!m) return null;
  return { url: `https://${m[1] || ""}udemy.com/course/${m[2]}/`, slug: m[2] };
}

/**
 * Runs in the Udemy page (its own origin and login, like Udemy's player). Without `part`: the
 * course id, title and lectures. With `part` ({ id, lectures }): each lecture's stream link
 * (null when it has none; `drm` when it is protected).
 */
async function readUdemyCourseInPage(slug, part) {
  const headers = { "Accept": "application/json", "X-Requested-With": "XMLHttpRequest" };
  const get = async (path) => {
    const r = await fetch(path.startsWith("http") ? path : location.origin + path, { credentials: "include", headers });
    if (!r.ok) throw new Error("HTTP " + r.status);
    return r.json();
  };
  const courseIdInPage = () => {
    const html = document.documentElement.innerHTML;
    const found = /data-course-id="(\d+)"/.exec(html) || /&quot;courseId&quot;:\s*(\d+)/.exec(html) || /"courseId":\s*(\d+)/.exec(html);
    return found ? found[1] : "";
  };
  try {
    if (part) {
      if (!part.id) {
        part.id = courseIdInPage();
        if (!part.id) part.id = String((await get(`/api-2.0/courses/${encodeURIComponent(slug)}/?fields[course]=id`)).id || "");
        if (!part.id) throw new Error("no course id");
      }
      const pick = (asset) => {
        const all = [].concat(asset.media_sources || [],
          (asset.stream_urls && asset.stream_urls.Video) || [],
          (asset.download_urls && asset.download_urls.Video) || []);
        const srcOf = (x) => (x && (x.src || x.file)) || "";
        const hls = all.find(x => /mpegurl/i.test(x.type || "") || /\.m3u8(\?|$)/i.test(srcOf(x)));
        if (hls) return srcOf(hls);
        const files = all.filter(x => /^https?:/i.test(srcOf(x)) && !/dash/i.test(x.type || "") && !/\.mpd(\?|$)/i.test(srcOf(x)))
          .sort((a, b) => (parseInt(b.label, 10) || 0) - (parseInt(a.label, 10) || 0));
        return files.length ? srcOf(files[0]) : "";
      };
      const typeOf = (a) => (a && (a.asset_type || a.assetType)) || "";
      const fileOf = (a) => {
        const urls = a && a.download_urls;
        if (!urls || typeof urls !== "object") return "";
        const type = typeOf(a);
        const lists = [urls[type]].concat(Object.keys(urls).filter(k => k !== type && k !== "Video").map(k => urls[k]));
        for (const list of lists) {
          for (const x of [].concat(list || [])) {
            const u = (x && (x.file || x.src)) || "";
            if (/^https?:/i.test(u)) return u;
          }
        }
        return "";
      };
      const external = (a) => typeOf(a) === "ExternalLink" || !!(a && a.external_url);
      return await Promise.all(part.lectures.map(async (lec) => {
        try {
          const lecturePath = `/api-2.0/users/me/subscribed-courses/${part.id}/lectures/${lec.id}/`;
          let d;
          try { d = await get(`${lecturePath}?fields[lecture]=title,asset,supplementary_assets&fields[asset]=asset_type,media_sources,stream_urls,download_urls,course_is_drmed,title,filename,external_url`); }
          catch (_) { d = await get(`${lecturePath}?fields[lecture]=title,asset&fields[asset]=asset_type,media_sources,stream_urls,download_urls,course_is_drmed`); }
          const out = { title: d.title || lec.title || "" };
          const kind = lec.kind || "video";
          if (kind === "video") {
            const url = d.asset ? pick(d.asset) : "";
            if (url) out.url = url;
            else out.drm = !!(d.asset && (d.asset.course_is_drmed || (d.asset.media_sources || []).length));
          } else if (kind === "file" && d.asset) {
            const url = fileOf(d.asset);
            if (url) out.file = { url, name: d.asset.filename || d.asset.title || "" };
          }
          const resources = [];
          for (const a of [].concat(d.supplementary_assets || [])) {
            if (!a || external(a)) continue;
            let url = fileOf(a);
            if (!url && a.id) {
              try { url = fileOf(await get(`${lecturePath}supplementary-assets/${a.id}/?fields[asset]=download_urls,filename,title,asset_type`)); } catch (_) {}
            }
            if (url) resources.push({ url, name: a.filename || a.title || "" });
          }
          if (resources.length) out.resources = resources;
          return out;
        } catch (_) { return {}; }
      }));
    }
    let id = courseIdInPage();
    let title = "";
    try {
      const c = await get(`/api-2.0/courses/${id || encodeURIComponent(slug)}/?fields[course]=id,title`);
      id = String(c.id || id); title = c.title || "";
    } catch (e) { if (!id) throw e; }
    const query = "?page_size=1000&fields[lecture]=title,object_index,asset,supplementary_assets&fields[chapter]=title,object_index&fields[asset]=asset_type,external_url";
    const plainQuery = "?page_size=1000&fields[lecture]=title,object_index,asset&fields[chapter]=title,object_index&fields[asset]=asset_type";
    let page;
    try { page = await get(`/api-2.0/courses/${id}/subscriber-curriculum-items/${query}`); }
    catch (_) {
      try { page = await get(`/api-2.0/courses/${id}/cached-subscriber-curriculum-items/${query}`); }
      catch (_) { page = await get(`/api-2.0/courses/${id}/subscriber-curriculum-items/${plainQuery}`); }
    }
    let items = page.results || [];
    while (page.next) { page = await get(page.next); items = items.concat(page.results || []); }
    const FILE_TYPES = ["E-Book", "File", "Presentation", "Audio", "SourceCode"];
    const lectures = [];
    for (const e of items) {
      if (e._class !== "lecture") continue;
      const type = e.asset ? (e.asset.asset_type || e.asset.assetType || "") : "Video";
      const kind = type === "Video" ? "video" : FILE_TYPES.includes(type) ? "file" : "";
      const resources = [].concat(e.supplementary_assets || []).filter(a => a &&
        (a.asset_type || a.assetType) !== "ExternalLink" && !a.external_url).length;
      if (kind || resources) lectures.push({ id: e.id, title: e.title || "", kind: kind || "none" });
    }
    return { id, title, lectures };
  } catch (e) {
    return { error: (e && e.message) || "error" };
  }
}

function udemyFileName(number, name, fileName, url) {
  const extOf = (s) => {
    const m = /\.([a-z0-9]{1,8})$/i.exec(String(s || "").trim());
    return m && /[a-z]/i.test(m[1]) ? m[1].toLowerCase() : "";
  };
  let ext = extOf(fileName);
  if (!ext) {
    try {
      const u = new URL(url);
      const disposition = u.searchParams.get("response-content-disposition") || "";
      const m = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
      ext = extOf(m ? decodeURIComponent(m[1].replace(/\+/g, " ")) : "") || extOf(decodeURIComponent(u.pathname.split("/").pop() || ""));
    } catch (_) {}
  }
  const base = String(name || fileName || "").replace(/[\\/:*?"<>|\u0000-\u001f]+/g, " ").replace(/\s+/g, " ").trim() || "file";
  const named = ext && !base.toLowerCase().endsWith("." + ext);
  return `${number} - ${base}${named ? "." + ext : ""}`;
}

/** Tells the app how far the course reading is (it shows "12 / 80"), or why it failed. */
async function courseProgressToApp(courseUrl, referer, extra, from = null) {
  try { await postJson("/add", { ...(await buildRequest({ url: courseUrl, referer, from })), ...extra }, 2500); } catch (_) {}
}

const CANVA_KEY = "fetchjet_canva_by_tab";
const CANVA_PAGE = /^https:\/\/([\w-]+\.)?canva\.(com|cn)\/design\/([\w-]+)/i;
const CANVA_ORIGIN = /canva\.(com|cn)|canva-apps\.com|canvausercontent\.com/i;
const CANVA_PREVIEW = /thumb|poster|preview|sprite|storyboard|filmstrip|waveform|placeholder|image-resize|document-image/i;
const CANVA_PLAY_WINDOW = 8000;
const CANVA_PAIR_MS = 1500;
const CANVA_FILE_EXTS = new Set(["mp4", "m4v", "mov", "webm", "mkv", "mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav", "weba"]);
const CANVA_AUDIO_EXTS = new Set(["mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav", "weba"]);
const CANVA_TYPE_EXT = { "video/mp4": "mp4", "video/webm": "webm", "video/quicktime": "mov", "video/x-matroska": "mkv", "video/x-m4v": "m4v", "audio/mpeg": "mp3", "audio/mp4": "m4a", "audio/x-m4a": "m4a", "audio/wav": "wav", "audio/x-wav": "wav", "audio/ogg": "ogg", "audio/aac": "aac", "audio/webm": "weba" };

const canvaDesign = (url) => { const m = CANVA_PAGE.exec(String(url || "")); return m ? m[3] : ""; };
const canvaTitle = (t) => String(t || "").replace(/\s*[-–|·]\s*Canva\s*$/i, "").trim();
const stripExt = (n) => String(n || "").replace(/\.[a-z0-9]{2,5}$/i, "");
const nameExt = (n) => { const m = /\.([a-z0-9]{2,5})$/i.exec(String(n || "")); return m ? m[1].toLowerCase() : ""; };
const safeBase = (n) => String(n || "").replace(/[<>:"/\\|?*\u0000-\u001f]/g, "_").replace(/\s+/g, " ").trim().slice(0, 150).replace(/[. ]+$/, "");
const fmtClock = (s) => { s = Math.round(s); return s >= 3600 ? `${Math.floor(s / 3600)}:${String(Math.floor(s / 60) % 60).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}` : `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`; };

function stripQuery(url) {
  try {
    const u = new URL(url);
    const keys = [...new Set([...u.searchParams.keys()])];
    return u.origin + u.pathname + (keys.length ? "?" + keys.join("&") : "");
  } catch (_) { return String(url || "").split("?")[0]; }
}

function canvaTokens(url, design = "") {
  let text = String(url || "");
  for (let i = 0; i < 2; i++) { try { text = decodeURIComponent(text); } catch (_) { break; } }
  const out = new Set();
  for (const m of text.matchAll(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi)) out.add(m[0].toLowerCase());
  for (const m of text.matchAll(/(?:^|[^A-Za-z0-9_-])([VM][A-Z][A-Za-z0-9_-]{9})(?=$|[^A-Za-z0-9_-])/g)) out.add(m[1]);
  let path = "";
  try { path = decodeURIComponent(new URL(url).pathname); } catch (_) {}
  for (const part of path.split(/[/:]/)) {
    const seg = part.replace(/\.[A-Za-z0-9]{2,5}$/, "");
    if (/^[A-Za-z0-9_-]{16,}$/.test(seg) && /\d/.test(seg) && /[a-z]/i.test(seg)) out.add(seg);
  }
  if (design) out.delete(design);
  return [...out].slice(0, 12);
}

function canvaServerName(url, headers) {
  const fromDisposition = (v) => {
    const m = /filename\*\s*=\s*(?:UTF-8'')?([^;]+)|filename\s*=\s*"?([^";]+)"?/i.exec(v || "");
    if (!m) return "";
    const raw = (m[1] || m[2]).trim();
    try { return decodeURIComponent(raw); } catch (_) { return raw; }
  };
  let name = fromDisposition(header(headers, "content-disposition"));
  try {
    const u = new URL(url);
    name = name || fromDisposition(u.searchParams.get("response-content-disposition")) || u.searchParams.get("filename") || "";
  } catch (_) {}
  return /\.[a-z0-9]{2,5}$/i.test(name) ? baseName(name) : "";
}

function canvaState(all, tabId, design, page, memo = null) {
  const state = all[tabId];
  if (state && state.design === design) return state;
  const kept = memo && memo.design === design ? memo : null;
  return { design, page, files: {}, figures: [], plays: [], pairs: { ...((kept && kept.pairs) || {}) }, binds: {}, remembered: { ...((kept && kept.names) || {}) }, log: [], next: 0 };
}

const CANVA_MEMO_KEY = "fetchjet_canva_memo_by_tab";

async function rememberCanva(tabId, state, memos) {
  const old = memos[tabId];
  const memo = old && old.design === state.design ? { design: state.design, names: { ...old.names }, pairs: { ...old.pairs } } : { design: state.design, names: {}, pairs: {} };
  const { clips, named } = canvaNaming(state);
  for (const c of clips) {
    const fig = named.get(c.key);
    if (fig && fig.name) for (const k of c.keys) memo.names[k] = { name: fig.name, page: fig.page || 0 };
    if (c.video && c.audio) memo.pairs[c.video.key] = c.audio.key;
  }
  for (const part of ["names", "pairs"]) {
    const keys = Object.keys(memo[part]);
    if (keys.length > 200) for (const k of keys.slice(0, keys.length - 200)) delete memo[part][k];
  }
  if (JSON.stringify(old) === JSON.stringify(memo)) return;
  memos[tabId] = memo;
  await sessionSet(CANVA_MEMO_KEY, memos);
}

function canvaClips(state) {
  const files = Object.entries(state.files || {}).map(([key, f]) => ({ key, ...f }));
  const parent = new Map(files.map(f => [f.key, f.key]));
  const find = (k) => { while (parent.get(k) !== k) k = parent.get(k); return k; };
  const members = (root) => files.filter(f => find(f.key) === root);
  const fits = (keys) => {
    const all = files.filter(f => keys.includes(find(f.key)));
    return all.filter(f => !f.audio && !f.preview).length <= 1 && all.filter(f => f.audio && !f.preview).length <= 1;
  };
  const join = (a, b) => {
    const ra = find(a), rb = find(b);
    if (ra === rb || !fits([ra, rb])) return;
    parent.set(rb, ra);
  };
  for (const [v, a] of Object.entries(state.pairs || {})) if (parent.has(v) && parent.has(a)) join(v, a);
  const groupBy = (sig) => {
    const by = new Map();
    for (const f of files) for (const t of sig(f)) { if (!by.has(t)) by.set(t, []); by.get(t).push(f.key); }
    for (const keys of by.values()) {
      if (keys.length < 2 || keys.length > 4) continue;
      const roots = [...new Set(keys.map(find))];
      if (roots.length > 1 && fits(roots)) for (const k of keys) join(keys[0], k);
    }
  };
  groupBy(f => f.tokens || []);
  const firsts = [...new Set(files.map(f => find(f.key)))].map(root => {
    const mine = members(root);
    return { root, v: mine.some(f => !f.audio), a: mine.some(f => f.audio), first: Math.min(...mine.map(f => f.first || Infinity)) };
  }).filter(g => isFinite(g.first));
  for (const g of firsts.filter(x => x.v && !x.a)) {
    const near = firsts.filter(x => x !== g && Math.abs(x.first - g.first) <= CANVA_PAIR_MS);
    if (near.length !== 1 || !near[0].a || near[0].v) continue;
    if (firsts.some(x => x !== g && x !== near[0] && Math.abs(x.first - near[0].first) <= CANVA_PAIR_MS)) continue;
    join(g.root, near[0].root);
  }
  groupBy(f => { const last = (f.dir || "").split("/").pop(); return last.length >= 8 && /\d/.test(last) ? [f.dir] : []; });
  const clips = [];
  for (const root of new Set(files.map(f => find(f.key)))) {
    const mine = members(root);
    const best = (audio) => mine.filter(f => !!f.audio === audio).sort((a, b) => (a.preview - b.preview) || ((b.size || 0) - (a.size || 0)))[0] || null;
    const video = best(false), audio = best(true);
    clips.push({
      key: (video || audio).key, keys: mine.map(f => f.key), video, audio,
      tokens: [...new Set(mine.flatMap(f => f.tokens || []))], order: Math.min(...mine.map(f => f.order)),
      hits: mine.flatMap(f => f.hits || []), serverName: (video && video.serverName) || (audio && audio.serverName) || ""
    });
  }
  return clips.sort((a, b) => a.order - b.order);
}

function bindCanvaPlays(state, now = Date.now()) {
  for (const p of state.plays || []) {
    if (p.done) continue;
    const clips = canvaClips(state);
    const byToken = clips.filter(c => (p.ids || []).some(t => c.tokens.includes(t)));
    if (byToken.length === 1) { p.key = byToken[0].key; p.done = true; continue; }
    const from = p.since - 500, to = p.at + CANVA_PLAY_WINDOW;
    const hit = clips.filter(c => c.hits.some(t => t >= from && t <= to));
    p.key = "";
    if (hit.length === 1) p.key = hit[0].key;
    else if (hit.length === 2) {
      const v = hit.find(c => c.video && !c.audio), a = hit.find(c => c.audio && !c.video);
      if (v && a) { state.pairs = { ...(state.pairs || {}), [v.video.key]: a.audio.key }; p.key = v.key; }
    }
    if (now > to) p.done = true;
  }
}

function logCanva(state, details, note) {
  const h = (n) => header(details.responseHeaders, n);
  state.log = [...(state.log || []).slice(-119), {
    t: Date.now(), url: stripQuery(details.url), status: details.statusCode, mime: h("content-type"),
    length: h("content-length"), range: h("content-range"), initiator: details.initiator || details.originUrl || "",
    type: details.type || "", frameId: details.frameId, note
  }];
}

async function addCanvaMedia(details, pageUrl) {
  const url = String(details.url).split("#")[0];
  const headers = details.responseHeaders;
  const ctype = header(headers, "content-type").toLowerCase().split(";")[0].trim();
  const all = await sessionGet(CANVA_KEY);
  const memos = await sessionGet(CANVA_MEMO_KEY);
  const design = canvaDesign(pageUrl);
  const state = canvaState(all, details.tabId, design, pageUrl, memos[details.tabId]);
  state.page = pageUrl;
  const save = async (note) => { logCanva(state, details, note); all[details.tabId] = state; await sessionSet(CANVA_KEY, all); };
  let host = "", path = "";
  try { const u = new URL(url); host = u.hostname; path = u.pathname; } catch (_) { return; }
  const ext = extensionOf(url);
  if (!/^media\.canva\.(com|cn)$/i.test(host) || !/^\/v2\/files\//i.test(path) || !CANVA_FILE_EXTS.has(ext) || /^(image|text|font)\//.test(ctype)) {
    if ((state.log || []).some(x => x.url === stripQuery(url))) return;
    return save("ignored");
  }
  const tokens = canvaTokens(url, design);
  const stream = false;
  const range = /\/(\d+)\s*$/.exec(header(headers, "content-range"));
  const size = range ? parseInt(range[1], 10) : details.statusCode === 200 ? (parseInt(header(headers, "content-length"), 10) || 0) : 0;
  if (!stream && size > 0 && size < 512 * 1024) return save("small");
  const key = host + path;
  const now = Date.now();
  const file = {
    url, size, stream, tokens, dir: path.replace(/\/[^/]*$/, ""),
    audio: /^audio\//.test(ctype) || CANVA_AUDIO_EXTS.has(ext),
    preview: CANVA_PREVIEW.test(path),
    ext: MEDIA_EXTS.has(ext) ? ext : (CANVA_TYPE_EXT[ctype] || ""), serverName: canvaServerName(url, headers)
  };
  const old = state.files[key];
  let shown = true;
  if (!old) {
    const keys = Object.keys(state.files);
    if (keys.length >= 80) delete state.files[keys[0]];
    state.next = (state.next || 0) + 1;
    state.files[key] = { ...file, order: state.next, hits: [now], first: now };
  } else {
    shown = false;
    old.hits = [...(old.hits || []).filter(t => now - t < 60000).slice(-29), now];
    old.url = url;
    if (size > (old.size || 0)) { old.size = size; shown = true; }
    if (!old.serverName && file.serverName) { old.serverName = file.serverName; shown = true; }
  }
  const before = (state.plays || []).map(p => p.key || "").join("|");
  bindCanvaPlays(state, now);
  if ((state.plays || []).map(p => p.key || "").join("|") !== before) shown = true;
  await save(old ? "again" : "file");
  await rememberCanva(details.tabId, state, memos);
  if (shown) pushToTab(details.tabId);
}

async function addCanvaFigures(tab, figures, play, bind) {
  const design = canvaDesign(tab && tab.url);
  if (!design) return { ok: false };
  await serial(async () => {
    const all = await sessionGet(CANVA_KEY);
    const memos = await sessionGet(CANVA_MEMO_KEY);
    const state = canvaState(all, tab.id, design, tab.url, memos[tab.id]);
    const cleanIds = (ids) => (Array.isArray(ids) ? ids : []).map(String).filter(i => i.length >= 8 && i.length <= 64 && i !== design).slice(0, 12);
    const list = Array.isArray(figures) ? figures : [];
    if (list.length) state.figures.forEach(g => { g.visible = false; });
    for (const f of list.slice(0, 200)) {
      const name = String((f && f.name) || "").trim().slice(0, 200);
      if (!name) continue;
      const page = Number(f.page) || 0;
      const fig = { name, page, ids: cleanIds(f.ids), thumb: /^https:/i.test(f.thumb || "") ? String(f.thumb).slice(0, 2000) : "", duration: Number(f.duration) || 0, box: f.box || null, boxFrom: String(f.boxFrom || "").slice(0, 20), visible: !!f.visible };
      const same = state.figures.find(g => g.name === name && (!page || !g.page || g.page === page));
      if (same) {
        same.page = same.page || page;
        same.ids = [...new Set([...same.ids, ...fig.ids])].slice(0, 12);
        if (fig.thumb) same.thumb = fig.thumb;
        if (fig.duration) same.duration = fig.duration;
        if (fig.box) { same.box = fig.box; same.boxFrom = fig.boxFrom; }
        same.visible = fig.visible;
      } else state.figures.push(fig);
    }
    if (state.figures.length > 300) state.figures = state.figures.slice(-300);
    if (play && (play.name || play.at)) {
      const at = Number(play.at) || Date.now();
      const since = Math.min(at, Number(play.since) || at - 3000);
      state.plays = [...(state.plays || []).slice(-19), { name: String(play.name || "").slice(0, 200), page: Number(play.page) || 0, ids: cleanIds(play.ids), since, at, key: "" }];
      const fig = play.name ? state.figures.find(g => g.name === play.name && (!play.page || !g.page || g.page === Number(play.page))) : null;
      if (fig && play.duration) fig.duration = Number(play.duration) || fig.duration;
    }
    if (bind && bind.key && bind.name && state.files[bind.key]) {
      state.binds = { ...(state.binds || {}), [bind.key]: { name: String(bind.name).slice(0, 200), page: Number(bind.page) || 0 } };
    }
    bindCanvaPlays(state);
    all[tab.id] = state;
    await sessionSet(CANVA_KEY, all);
    await rememberCanva(tab.id, state, memos);
  });
  pushToTab(tab.id);
  return { ok: true };
}

function canvaNaming(state) {
  const clips = canvaClips(state);
  const figures = state.figures || [];
  const figKey = (g) => `${g.page || 0}|${g.name}`;
  const used = new Set();
  const named = new Map();
  const take = (c, fig) => {
    if (!fig || named.has(c.key)) return;
    named.set(c.key, fig);
    figures.forEach(g => { if (g.name === fig.name && (!fig.page || !g.page || g.page === fig.page)) used.add(figKey(g)); });
  };
  const figureOf = (name, page) => figures.find(g => g.name === name && (!page || !g.page || g.page === page)) || { name, page: page || 0, ids: [] };
  for (const c of clips) for (const k of c.keys) if (state.binds && state.binds[k]) take(c, figureOf(state.binds[k].name, state.binds[k].page));
  for (const c of clips) {
    if (named.has(c.key)) continue;
    const hits = figures.filter(g => g.ids.some(t => c.tokens.includes(t)));
    const names = [...new Set(hits.map(figKey))];
    if (names.length !== 1) continue;
    const shared = hits[0].ids.filter(t => c.tokens.includes(t));
    if (shared.some(t => clips.filter(o => o.tokens.includes(t)).length === 1)) take(c, hits[0]);
  }
  const plays = (state.plays || []).filter(p => p.key).sort((a, b) => a.at - b.at);
  const clipOf = (key) => clips.find(c => c.keys.includes(key));
  for (const p of plays) { const c = clipOf(p.key); if (c && p.name) take(c, figureOf(p.name, p.page)); }
  for (const c of clips) for (const k of c.keys) { const r = state.remembered && state.remembered[k]; if (r) take(c, figureOf(r.name, r.page)); }
  for (const c of clips) {
    if (named.has(c.key) || !c.serverName) continue;
    const base = stripExt(c.serverName).toLowerCase();
    take(c, figures.find(g => stripExt(g.name).toLowerCase() === base) || { name: c.serverName, page: 0, ids: [] });
  }
  const shownFree = () => figures.filter(g => g.visible && !used.has(figKey(g)) && !CANVA_AUDIO_EXTS.has(nameExt(g.name)));
  const lastPlay = (state.plays || []).slice(-1)[0];
  const lastClip = lastPlay && lastPlay.key ? clipOf(lastPlay.key) : null;
  if (lastClip && lastClip.video && !named.has(lastClip.key)) {
    const shown = shownFree();
    if (new Set(shown.map(figKey)).size === 1) take(lastClip, shown[0]);
  }
  const leftVideos = clips.filter(c => !named.has(c.key) && c.video);
  if (leftVideos.length === 1) {
    const shown = shownFree();
    if (new Set(shown.map(figKey)).size === 1) take(leftVideos[0], shown[0]);
  }
  const left = clips.filter(c => !named.has(c.key));
  if (left.length === 1) {
    const free = figures.filter(g => !used.has(figKey(g)));
    const fits = [...new Set(free.filter(g => !g.ids.length || !left[0].tokens.length).map(figKey))];
    if (fits.length === 1 && free.length === free.filter(g => figKey(g) === fits[0]).length) take(left[0], free[0]);
  }
  return { clips, named, clipOf };
}

function canvaItems(state, tabTitle, pageUrl) {
  const { clips, named, clipOf } = canvaNaming(state);
  const last = (state.plays || []).slice(-1)[0];
  const playingClip = last && last.key ? clipOf(last.key) : null;
  const title = safeBase(canvaTitle(tabTitle)) || "Canva";
  const merge = appMerges && !ON_IOS;
  const taken = new Set();
  const unique = (base, ext) => {
    let name = `${base}.${ext}`;
    for (let k = 2; taken.has(name.toLowerCase()); k++) name = `${base} (${k}).${ext}`;
    taken.add(name.toLowerCase());
    return name;
  };
  const items = [];
  let videos = 0, audios = 0;
  for (const c of clips) {
    const fig = named.get(c.key) || null;
    const isAudio = !c.video;
    const n = isAudio ? ++audios : ++videos;
    const playing = !!playingClip && playingClip.key === c.key;
    const base = safeBase(fig ? stripExt(fig.name) : "") || `${title} - ${isAudio ? "audio " : ""}${n}`;
    const label = fig ? fig.name : fjT(isAudio ? "itemCanvaAudio" : "itemCanvaVideo", { n });
    const duration = fig && fig.duration ? fig.duration : 0;
    const parts = [playing ? fjT("itemCanvaPlaying") : "", fig && fig.page ? fjT("itemCanvaPage", { n: fig.page }) : "", duration ? fmtClock(duration) : ""].filter(Boolean);
    const common = { kind: "media", canva: true, clipKey: c.key, referer: pageUrl, figure: fig ? fig.name : "", page: fig ? fig.page || 0 : 0, thumb: fig ? fig.thumb || "" : "", duration, playing };
    const make = (main, audioUrl, size, ext, extraLabel) => {
      const fileName = unique(base, ext);
      const note = parts.join(" · ");
      return {
        ...common, label: extraLabel ? `${label} · ${extraLabel}` : (fig ? fileName : label), url: main.url, audioUrl, size, ext,
        stream: !!main.stream, fileName, note, sub: [note, size ? fmtBytes(size) : ""].filter(Boolean).join(" · ")
      };
    };
    if (c.video && c.audio && merge) items.push(make(c.video, c.audio.url, (c.video.size || 0) + (c.audio.size || 0), "mp4"));
    else {
      if (c.video) items.push(make(c.video, "", c.video.stream ? 0 : (c.video.size || 0), c.video.stream ? "mp4" : (c.video.ext || nameExt(fig && fig.name) || "mp4"), c.audio ? fjT("badgeVideo") : ""));
      if (c.audio) items.push(make(c.audio, "", c.audio.size || 0, c.audio.ext || "m4a", c.video ? fjT("badgeAudio") : ""));
    }
    if (playing) items.forEach(i => { if (i.clipKey === c.key) i.note = i.note || fjT("itemCanvaPlaying"); });
  }
  return items.sort((a, b) => (b.playing ? 1 : 0) - (a.playing ? 1 : 0));
}

async function canvaForTab(tabId) {
  let tab = null;
  try { tab = await api.tabs.get(tabId); } catch (_) { return null; }
  const design = canvaDesign(tab && tab.url);
  if (!design) return null;
  const state = await tabState(CANVA_KEY, tabId);
  const mine = state && state.design === design ? state : canvaState({}, tabId, design, tab.url);
  const items = canvaItems(mine, tab.title, tab.url);
  if (!items.length && mine.figures.length) items.push({ label: fjT("itemCanvaWait"), sub: fjT("itemCanvaWaitSub"), disabled: true });
  return { items, title: canvaTitle(tab.title) };
}

async function canvaReport(tabId) {
  let tab = null;
  try { tab = await api.tabs.get(tabId); } catch (_) {}
  const state = (await tabState(CANVA_KEY, tabId)) || {};
  let live = null;
  try { live = await api.tabs.sendMessage(tabId, { action: "canvaDebug" }); } catch (_) {}
  return {
    extension: EXT_VERSION, page: stripQuery((tab && tab.url) || ""), appMerges, live,
    remembered: (await tabState(CANVA_MEMO_KEY, tabId)) || null,
    captures: state.log || [],
    files: canvaClips(state.files ? state : { files: {} }).map(c => ({
      key: c.key, keys: c.keys, tokens: c.tokens, first: Math.min(...c.keys.map(k => (state.files[k] || {}).first || Infinity)), video: c.video ? { url: stripQuery(c.video.url), size: c.video.size, ext: c.video.ext, preview: c.video.preview } : null,
      audio: c.audio ? { url: stripQuery(c.audio.url), size: c.audio.size, ext: c.audio.ext, preview: c.audio.preview } : null
    })),
    figures: (state.figures || []).map(g => ({ label: g.name, page: g.page, thumb: stripQuery(g.thumb), ids: g.ids, duration: g.duration, box: g.box, boxFrom: g.boxFrom || "", visible: !!g.visible })),
    plays: (state.plays || []).map(p => ({ name: p.name, page: p.page, since: p.since, at: p.at, key: p.key })),
    pairs: state.pairs || {}, binds: state.binds || {}
  };
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
  refreshMenus(tabId).catch(() => {});
  api.tabs.sendMessage(tabId, { action: "fetchjetItems", items: settings.pagePanel ? items : [] }).catch(() => {});
}

async function clearTab(tabId) {
  seenStreams.delete(tabId);
  videoPages.delete(tabId);
  await Promise.all([clearMediaForTab(tabId), setTabState(YT_KEY, tabId, null), setTabState(RES_KEY, tabId, null), setTabState(DRM_KEY, tabId, null), setTabState(CANVA_KEY, tabId, null)]);
  updateBadge(tabId, 0);
  api.tabs.sendMessage(tabId, { action: "fetchjetItems", items: [] }).catch(() => {});
  refreshMenus(tabId).catch(() => {});
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
    return sendToFetchJet({ url: item.url, referer: pageUrl, from: tab }, "playlist");
  }
  if (item.kind === "course") {
    const course = tab && tab.id != null ? await udemyCourseForTab(tab.id) : null;
    if (!course) return { ok: false, reason: fjT("courseFailed", { reason: "" }) };
    const inPage = async (part) => {
      try {
        const [res] = await api.scripting.executeScript({ target: { tabId: tab.id }, world: "MAIN", func: readUdemyCourseInPage, args: [course.slug, part || null] });
        return res && res.result;
      } catch (e) { return { error: (e && e.message) || "" }; }
    };
    const fail = async (reason) => {
      await courseProgressToApp(course.url, pageUrl, { readingFailed: reason }, tab);
      await notify(reason);
      return { ok: false, reason };
    };
    await courseProgressToApp(course.url, pageUrl, { reading: { done: 0, total: 0 } }, tab);
    const head = await inPage(null);
    if (!head || head.error || !head.lectures || !head.lectures.length) {
      return fail(fjT("courseFailed", { reason: head && head.error ? `(${head.error})` : "" }));
    }
    const total = head.lectures.length;
    const entries = [];
    let drm = 0;
    for (let i = 0; i < total; i += 12) {
      await courseProgressToApp(course.url, pageUrl, { reading: { done: i, total } }, tab);
      const part = await inPage({ id: head.id, lectures: head.lectures.slice(i, i + 12) });
      if (!Array.isArray(part)) return fail(fjT("courseFailed", { reason: part && part.error ? `(${part.error})` : "" }));
      part.forEach((r, k) => {
        if (!r) return;
        const number = String(i + k + 1).padStart(3, "0");
        if (r.url) entries.push({ title: `${number} - ${r.title}`, url: r.url });
        else if (r.drm) drm++;
        if (r.file && r.file.url) {
          const fileName = udemyFileName(number, r.title, r.file.name, r.file.url);
          entries.push({ title: fileName, url: r.file.url, fileName });
        }
        for (const res of r.resources || []) {
          const fileName = udemyFileName(number, res.name, res.name, res.url);
          entries.push({ title: fileName, url: res.url, fileName });
        }
      });
    }
    if (!entries.length) return fail(drm ? fjT("courseAllDrm") : fjT("courseFailed", { reason: "" }));
    if (drm) await notify(fjT("courseDrm", { n: drm }));
    return sendToFetchJet({ url: course.url, referer: pageUrl, playlist: { title: head.title || clean, entries }, from: tab }, "course");
  }
  if (item.kind === "openApp") {
    // Android / iOS: the app opens with the link and shows the qualities itself.
    if (ON_IOS) {
      const request = await buildRequest({ url: item.url, referer: pageUrl, from: tab });
      const page = iosSessionPage(request);
      const ok = page ? await openAppAt("fetchjet://browse?url=" + encodeURIComponent(page), tab && tab.id) : await openAppWith(request, tab && tab.id);
      return ok ? { ok: true } : { ok: false, reason: "could not open FetchJet" };
    }
    try {
      await openProtocolTab(PROTOCOL + encodeURIComponent(item.url), true, 1500);
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
    let r = await sendToFetchJet({ url: item.url, formatId: item.formatId, fileName, referer: pageUrl, openClip: !!item.openClip, from: tab }, "quality");
    if (!r.ok && tab) {
      // The app's cache expired: inspect again and retry once with the same label.
      const change = await inspectWithApp(item.url, tab);
      const again = change.resolved && change.resolved.options.find(o => o.label === item.label);
      if (again) r = await sendToFetchJet({ url: again.url, formatId: again.formatId, fileName, referer: pageUrl, from: tab }, "quality");
    }
    return r;
  }
  if (item.kind === "captured") {
    const fileName = `${clean}${item.quality ? " [" + item.quality + "]" : ""}${item.videoOnly ? " (video only)" : ""}.${item.ext || "mp4"}`;
    return sendToFetchJet({ url: item.url, audioUrl: item.audioUrl || "", referer: "https://www.youtube.com/", fileName, direct: true, from: tab }, "captured");
  }
  // A captured HLS/DASH playlist: the app merges it into one video; name it after the page
  // (the lecture / video title), not after the playlist's hash.
  let fileName = item.fileName || "";
  if (item.kind === "media" && !item.canva && (item.stream || /\.(m3u8|mpd)$/i.test(fileName))) {
    // The app lists the playlist's renditions and the user picks one there: no quality in the name.
    fileName = `${clean}.mp4`;
  }
  return sendToFetchJet({ url: item.url, audioUrl: item.audioUrl || "", referer: item.referer || pageUrl, fileName, direct: true, from: tab }, "media");
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
    const canva = canvaDesign(info.url) ? await tabState(CANVA_KEY, tabId) : null;
    const sameDesign = !!canva && canva.design === canvaDesign(info.url);
    if (!(yt && newId && yt.videoId === newId) && !sameDesign) await clearTab(tabId); // not the same video
  } else if (info.status === "loading") {
    // Plain reload: same URL, new page - forget what the old page played.
    const yt = await tabState(YT_KEY, tabId);
    if (!yt || !ytVideoId(url)) await clearTab(tabId);
    else if (yt.resolveError || (yt.resolving && !resolving.has(tabId))) {
      await setTabState(YT_KEY, tabId, { ...yt, resolveError: null, resolved: null, resolving: false });
    }
    const res = await tabState(RES_KEY, tabId);
    if (res && (res.resolveError || (res.resolving && !resolving.has(tabId)))) await setTabState(RES_KEY, tabId, null);
  }
  if (info.url || info.status === "loading" || info.status === "complete") ensureYoutubeResolved(tabId, url);
});
api.tabs.onRemoved.addListener((tabId) => { clearTab(tabId).catch(() => {}).then(() => { shownBadges.delete(tabId); return setTabState(CANVA_MEMO_KEY, tabId, null); }).catch(() => {}); });

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
  pageMedia: "fetchjet-page-media",
  course: "fetchjet-course",
  playlist: "fetchjet-playlist",
  canvaAll: "fetchjet-canva-all"
};

function installMenus() {
  if (!api.contextMenus) return;
  Promise.resolve(api.contextMenus.removeAll()).catch(() => {}).then(() => {
    shownMenus.clear();
    // contextMenus.create is callback-style in every browser (also browser.* in Firefox).
    const create = (props) => { try { api.contextMenus.create(props, () => void api.runtime.lastError); } catch (_) {} };
    create({ id: MENU.link, title: fjT("menuLink"), contexts: ["link"] });
    create({ id: MENU.media, title: fjT("menuMedia"), contexts: ["video", "audio"] });
    create({ id: MENU.image, title: fjT("menuImage"), contexts: ["image"] });
    create({ id: MENU.selectionLinks, title: fjT("menuSelection"), contexts: ["selection"] });
    create({ id: MENU.course, title: fjT("itemCourse"), contexts: ["page", "frame", "video"], visible: false });
    create({ id: MENU.playlist, title: fjT("itemPlaylist"), contexts: ["page", "frame"], visible: false });
    create({ id: MENU.page, title: fjT("menuPage"), contexts: ["page", "frame"], visible: false });
    create({ id: MENU.canvaAll, title: fjT("menuCanvaAll"), contexts: ["page", "frame", "image", "video"], visible: false });
    create({ id: MENU.allLinks, title: fjT("menuAllLinks"), contexts: ["page", "frame"] });
    create({ id: MENU.pageMedia, title: fjT("menuPageMedia"), contexts: ["action"] });
    api.tabs.query({ active: true, currentWindow: true }).then(([t]) => t && refreshMenus(t.id)).catch(() => {});
  });
}

/** Tabs whose content script said the page is one video (content.js VIDEO_PAGE). */
const videoPages = new Map();
const shownMenus = new Map();

/**
 * The right-click menu shows only what fits the page: a Udemy course -> the whole course; a
 * YouTube list -> the list and the video; a video page (or one that played media) -> the
 * video; any other page -> all its links.
 */
async function refreshMenus(tabId) {
  if (!api.contextMenus || tabId == null) return;
  try {
    const [active] = await api.tabs.query({ active: true, currentWindow: true });
    if (!active || active.id !== tabId) return;
  } catch (_) { return; }
  const course = appCourses && !ON_IOS ? await udemyCourseForTab(tabId) : null;
  const list = course ? null : await listUrlForTab(tabId);
  const canva = course || list ? null : await canvaForTab(tabId);
  const canvaFiles = canva ? canva.items.filter(i => i.canva).length : 0;
  let video = false;
  if (!course && !canva) {
    let url = "";
    try { url = ((await api.tabs.get(tabId)) || {}).url || ""; } catch (_) {}
    const all = await sessionGet(MEDIA_KEY);
    video = !!ytVideoId(url) || videoPages.get(tabId) === true || Object.keys(all[tabId] || {}).length > 0;
  }
  const want = { [MENU.course]: !!course, [MENU.playlist]: !!list, [MENU.page]: video, [MENU.allLinks]: !course && !list && !video && !canva, [MENU.canvaAll]: canvaFiles > 0 };
  for (const [id, visible] of Object.entries(want)) {
    if (shownMenus.get(id) === visible) continue;
    shownMenus.set(id, visible);
    try { api.contextMenus.update(id, { visible }, () => void api.runtime.lastError); } catch (_) {}
  }
}

api.tabs.onActivated.addListener(({ tabId }) => { refreshMenus(tabId).catch(() => {}); });

api.runtime.onInstalled.addListener(() => { installMenus(); updateActionBadgeForAll(); });
api.runtime.onStartup.addListener(() => { installMenus(); updateActionBadgeForAll(); });
fjOnLanguage(() => installMenus());
fetchAppConfig().catch(() => {});

if (api.contextMenus) {
  api.contextMenus.onClicked.addListener(async (info, tab) => {
    const pageUrl = (tab && tab.url) || info.pageUrl || "";
    switch (info.menuItemId) {
      case MENU.link:
        return sendToFetchJet({ url: info.linkUrl, referer: pageUrl, from: tab }, "menu");
      case MENU.media:
      case MENU.image:
        return sendToFetchJet({ url: info.srcUrl, referer: pageUrl, from: tab }, "menu");
      case MENU.page:
        return sendToFetchJet({ url: pageUrl, from: tab }, "menu");
      case MENU.course:
        return downloadItem({ kind: "course" }, tab);
      case MENU.playlist: {
        const list = tab && tab.id != null ? await listUrlForTab(tab.id) : null;
        return list ? downloadItem({ kind: "playlist", url: list.url }, tab) : null;
      }
      case MENU.allLinks:
        return collectAndSendLinks(tab, { selectionOnly: false, frameId: info.frameId });
      case MENU.selectionLinks:
        return collectAndSendLinks(tab, { selectionOnly: true, frameId: info.frameId });
      case MENU.pageMedia:
      case MENU.canvaAll:
        return sendDetectedMedia(tab && tab.id);
    }
  });
}

if (api.commands && api.commands.onCommand) {
  api.commands.onCommand.addListener(async (command, tab) => {
    if (!tab) { try { [tab] = await api.tabs.query({ active: true, currentWindow: true }); } catch (_) {} }
    if (!tab) return;
    if (command === "send-page") await sendToFetchJet({ url: tab.url, from: tab }, "command");
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
  return sendBatchToFetchJet(links, tab.url || "", await tabGroupName(tab), tab);
}

/**
 * The folder name for a page's links in the app: the page's own name from its title without
 * the site's name and the description ("GitHub - owner/speed-test-files: Collection of..." ->
 * "speed-test-files"), else the last part of its address, else the site.
 */
async function tabGroupName(tabOrId) {
  let tab = tabOrId;
  if (typeof tabOrId === "number") { try { tab = await api.tabs.get(tabOrId); } catch (_) { tab = null; } }
  if (!tab) return "";
  let url = null;
  try { url = new URL(tab.url); } catch (_) {}
  const host = url ? url.hostname.replace(/^www\./, "") : "";
  const site = host.split(".").slice(-2, -1)[0] || host;
  const title = (tab.title || "").trim();
  if (title && !/^https?:\/\//i.test(title)) {
    const parts = title.split(/\s+[-|·—–]\s+/).map(p => p.trim()).filter(Boolean)
      .filter(p => p.toLowerCase().replace(/\s+/g, "") !== site.toLowerCase() && p.toLowerCase() !== host.toLowerCase());
    let name = (parts[0] || "").split(/:\s/)[0].trim();
    if (/^[\w.-]+\/[\w.-]+$/.test(name)) name = name.split("/")[1]; // owner/repo
    if (name) return name.slice(0, 80);
  }
  const last = url ? url.pathname.split("/").filter(Boolean).pop() : "";
  if (last) { try { return decodeURIComponent(last).slice(0, 80); } catch (_) { return last.slice(0, 80); } }
  return host;
}

async function sendDetectedMedia(tabId) {
  if (tabId == null) return { ok: false, count: 0 };
  const canva = await canvaForTab(tabId);
  if (canva) {
    const files = canva.items.filter(i => i.canva);
    if (!files.length) { await notify(fjT("notifyNoMedia")); return { ok: false, count: 0 }; }
    return sendBatchToFetchJet(files.map(i => ({ url: i.url, referer: i.referer, fileName: i.fileName, audioUrl: i.audioUrl || "" })), "", await tabGroupName(tabId), tabId);
  }
  const all = await sessionGet(MEDIA_KEY);
  const items = Object.values(all[tabId] || {});
  if (items.length === 0) { await notify(fjT("notifyNoMedia")); return { ok: false, count: 0 }; }
  return sendBatchToFetchJet(items.map(i => ({ url: i.url, referer: i.pageUrl, fileName: i.name })), "", await tabGroupName(tabId), tabId);
}

/**
 * A bubble on a video that is not the page's main one was clicked: its own post / video
 * link (feeds), its own file, or the page when neither is known.
 */
async function resolveVideo(msg, tab) {
  if (tab && (await tabState(DRM_KEY, tab.id))) return { items: [{ kind: "drm", label: fjT("itemDrm"), sub: fjT("itemDrmSub"), disabled: true }] };
  const items = [];
  if (msg.src && /^https?:/i.test(msg.src)) {
    items.push({ kind: "media", label: fjT("itemVideoFile", { name: baseName((() => { try { return new URL(msg.src).pathname; } catch (_) { return msg.src; } })()) || "video" }), sub: msg.src, url: msg.src, referer: tab && tab.url, fileName: "", ext: extensionOf(msg.src) || "mp4" });
  }
  const udemy = !!tab && UDEMY_COURSE_PAGE.test(msg.page || tab.url || "");
  if (tab && (udemy || (msg.frame && !msg.page))) {
    // a player in a frame: what was found for the whole page is what it plays
    const found = (await itemsForTab(tab.id)).items.filter(i => !i.disabled);
    if (found.length) return { items: found };
    if (msg.poll || udemy) return { pending: true };
  }
  const page = msg.page || (msg.src && !msg.frame ? "" : (tab && tab.url));
  if (page && /^https?:/i.test(page)) {
    if (!(await appCanInspect())) return items.length ? { items } : { error: fjT("errNotRunning"), notRunning: true };
    const r = await inspectWithApp(ytVideoId(page) ? `https://www.youtube.com/watch?v=${ytVideoId(page)}` : page, tab);
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

/** The tab a message speaks for: the page that sent it, or the tab the popup is open on. */
async function askingTab(request, sender) {
  if (sender && sender.tab) return sender.tab;
  try {
    if (request && request.tabId != null) return await api.tabs.get(request.tabId);
    const [tab] = await api.tabs.query({ active: true, lastFocusedWindow: true });
    return tab || null;
  } catch (_) { return null; }
}

api.runtime.onMessage.addListener((request, sender, sendResponse) => {
  (async () => {
    switch (request && request.action) {
      case "sendToFetchJet":
      case "sendToKortex": {
        const referer = request.referer || (sender.tab && sender.tab.url) || "";
        return sendToFetchJet({ url: request.url, referer, fileName: request.fileName || "", from: await askingTab(request, sender) }, "message");
      }
      case "sendBatch":
        return sendBatchToFetchJet(request.items || [], request.referer || (sender.tab && sender.tab.url) || "", sender.tab ? await tabGroupName(sender.tab) : "", await askingTab(request, sender));
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
      case "openPageInApp": {
        if (!ON_IOS) return { ok: false };
        const tab = sender.tab || null;
        let page = request.page || (tab && tab.url) || "";
        try { if (!/^https?:$/.test(new URL(page).protocol)) page = ""; } catch (_) { page = ""; }
        if (!page) return { ok: false };
        return { ok: await openAppAt("fetchjet://browse?url=" + encodeURIComponent(page), tab && tab.id) };
      }
      case "openPageDialog": {
        if (ON_IOS) return { ok: false, reason: "unsupported url" };
        let page = request.page || (sender.tab && sender.tab.url) || "";
        try { if (!/^https?:$/.test(new URL(page).protocol)) page = ""; } catch (_) { page = ""; }
        if (!page) return { ok: false, reason: "unsupported url" };
        try {
          await openProtocolTab(PROTOCOL + encodeURIComponent(page), true);
          return { ok: true };
        } catch (_) { return { ok: false, reason: "could not open FetchJet" }; }
      }
      case "launchApp": {
        const launched = await launchApp(request.url || "");
        const online = launched ? await waitForApp(12000) : false;
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
        if (tabId != null && typeof request.videoPage === "boolean" && sender.tab) {
          videoPages.set(tabId, request.videoPage);
          refreshMenus(tabId).catch(() => {});
        }
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
      case "drmSeen": {
        if (!sender.tab || sender.tab.id == null) return { ok: false };
        await setTabState(DRM_KEY, sender.tab.id, { page: sender.tab.url || "" });
        pushToTab(sender.tab.id);
        return { ok: true };
      }
      case "resolveVideo":
        return resolveVideo(request, sender.tab || null);
      case "canvaFigures":
        if (!sender.tab || sender.tab.id == null) return { ok: false };
        return addCanvaFigures(sender.tab, request.figures, request.play || null, request.bind || null);
      case "canvaReport":
        return canvaReport(request.tabId != null ? request.tabId : (sender.tab && sender.tab.id));
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
