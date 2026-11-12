// FetchJet - popup
const api = typeof browser !== "undefined" ? browser : chrome;

const $ = (id) => document.getElementById(id);
const send = (msg) => api.runtime.sendMessage(msg);

let currentTab = null;
let settings = null;
let online = false;

function human(bytes) {
  if (!bytes || bytes <= 0) return "";
  const u = ["B", "KB", "MB", "GB", "TB"];
  let v = bytes, i = 0;
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
  return `${v.toFixed(v >= 100 || i === 0 ? 0 : 1)} ${u[i]}`;
}

function flash(text, isError = false, ms = 3000) {
  const el = $("msg");
  el.textContent = text;
  el.classList.toggle("err", isError);
  clearTimeout(flash._t);
  flash._t = setTimeout(() => { el.textContent = ""; }, ms);
}

function setStatus(state, text) {
  const el = $("status");
  el.classList.remove("online", "busy");
  if (state) el.classList.add(state);
  $("statusText").textContent = text;
}

async function checkStatus() {
  setStatus("busy", fjT("popupChecking"));
  try {
    const r = await send({ action: "checkStatus" });
    online = !!(r && r.online);
    if (r && r.config && r.config.language) fjUseLanguage(r.config.language);
    if (r && r.ios) {
      setStatus("online", fjT("popupIosLinks"));
    } else if (online) {
      const v = r.config && r.config.version ? ` v${r.config.version}` : "";
      setStatus("online", `${fjT("popupConnected")}${v}`);
    } else {
      setStatus("", fjT("popupNotRunning"));
    }
  } catch (_) {
    online = false;
    setStatus("", fjT("popupNotRunning"));
  }
}

async function launch() {
  setStatus("busy", fjT("popupStarting"));
  const r = await send({ action: "launchApp" });
  if (r && r.online) { online = true; setStatus("online", fjT("popupConnected")); }
  else { setStatus("", fjT("popupCouldNotStart")); flash(fjT("popupInstalledHint"), true, 5000); }
}

async function sendUrl(url, extra = {}) {
  if (!url) return;
  flash(fjT("popupSending"));
  const r = await send({ action: "sendToFetchJet", url, referer: currentTab ? currentTab.url : "", ...extra });
  if (r && r.ok) flash(fjT("popupSent"));
  else flash(fjT("couldNotSendParen", { reason: fjReason(r && r.reason) }), true, 4000);
  checkStatus();
}

function isWebPage(url) {
  return !!url && /^(https?|ftp):/i.test(url);
}

/** A short, plain name for the kind of item (no "HLS", "yt-dlp"...). */
function badgeOf(it) {
  if (it.kind === "playlist" || it.kind === "course") return { cls: "list", text: fjT("badgeList") };
  if ((it.kind === "ytdlp" || it.kind === "captured") && /^audio/i.test(it.label || "")) return { cls: "audio", text: fjT("badgeAudio") };
  if (it.kind === "ytdlp" || it.kind === "captured") return { cls: "video", text: fjT("badgeVideo") };
  if (it.stream) return { cls: "stream", text: fjT("badgeStream") };
  if (/^(mp3|m4a|aac|flac|wav|ogg|opus|wma)$/i.test(it.ext || "") || /^Audio/.test(it.label || "")) return { cls: "audio", text: fjT("badgeAudio") };
  if (/^(mp4|mkv|webm|mov|avi|m4v|ts|flv)$/i.test(it.ext || "") || /^Video/.test(it.label || "")) return { cls: "video", text: fjT("badgeVideo") };
  return { cls: "file", text: fjT("badgeFile") };
}

async function downloadFromPopup(btn, it, extra = {}, okText = "popupSent") {
  btn.disabled = true; flash(fjT(extra.openClip ? "popupOpening" : "popupSending"));
  const res = await send({ action: "downloadItem", item: { ...it, ...extra }, tabId: currentTab.id });
  if (res && res.ok) flash(fjT(okText)); else flash(fjT("couldNotSendParen", { reason: fjReason(res && res.reason) }), true, 4000);
  btn.disabled = false;
}

function mediaRow(it, hero) {
  const row = document.createElement("div");
  row.className = "media" + (it.disabled ? " off" : "") + (hero ? " hero" : "");
  const b = badgeOf(it);
  const badge = document.createElement("span"); badge.className = `kind ${b.cls}`; badge.textContent = it.disabled ? "…" : b.text;
  const name = document.createElement("span"); name.className = "name"; name.title = it.sub || it.url || "";
  name.textContent = it.label;
  if (it.sub && it.kind !== "media") { const sub = document.createElement("span"); sub.className = "sub"; sub.textContent = it.sub; name.appendChild(sub); }
  const size = document.createElement("span"); size.className = "size"; size.dir = "ltr";
  size.textContent = it.kind === "media" && !it.stream ? human(it.size) : "";
  row.append(badge, name, size);
  if (!it.disabled) {
    const btn = document.createElement("button");
    btn.className = "small primary"; btn.style.flex = "0 0 auto"; btn.textContent = "⬇"; btn.title = fjT("popupDownloadWith");
    btn.addEventListener("click", () => downloadFromPopup(btn, it));
    row.appendChild(btn);
    if (it.kind === "ytdlp") {
      const cut = document.createElement("button");
      cut.className = "small"; cut.style.flex = "0 0 auto"; cut.textContent = "↗"; cut.title = fjT("clipTitle");
      cut.addEventListener("click", () => downloadFromPopup(cut, it, { openClip: true }, "popupOpened"));
      row.appendChild(cut);
    }
  }
  return row;
}

async function loadMedia() {
  if (!currentTab) return;
  const r = await send({ action: "getItems", tabId: currentTab.id });
  const items = (r && r.items) || [];
  const list = $("mediaList");
  list.replaceChildren();
  // "Download all" only for real files: stream playlists of one video are not several files.
  const files = items.filter(i => i.kind === "media" && !i.stream);
  $("allMediaBtn").style.display = files.length > 1 ? "" : "none";
  if (items.length === 0) {
    const empty = document.createElement("div");
    empty.className = "empty";
    empty.textContent = !FJ_PLATFORM.qualitiesInPopup ? fjT("popupEmptyNoQualities") : fjT("popupEmpty");
    list.appendChild(empty);
    $("sendPageBtn").classList.add("primary");
    return;
  }
  // What the user most likely wants goes first, on its own: the whole course / list, else the
  // best quality FetchJet found. Everything else sits under "More options".
  const usable = items.filter(i => !i.disabled);
  const hero = usable.find(i => i.kind === "course" || i.kind === "playlist") ||
    usable.find(i => i.kind === "ytdlp") || (usable.length === 1 ? usable[0] : null);
  $("sendPageBtn").classList.toggle("primary", !hero);
  if (hero) list.appendChild(mediaRow(hero, true));
  const rest = items.filter(i => i !== hero);
  if (hero && rest.length) {
    const more = document.createElement("div");
    more.className = "more"; more.textContent = fjT("popupMoreOptions");
    list.appendChild(more);
  }
  for (const it of rest) list.appendChild(mediaRow(it, false));
}

function renderSwitches() {
  $("enabledSwitch").classList.toggle("on", !!settings.enabled);
  $("mediaSwitch").classList.toggle("on", !!settings.captureMedia);
  if (FJ_PLATFORM.hint != null) {
    $("hint").textContent = FJ_PLATFORM.hint && fjT(FJ_PLATFORM.hint);
    return;
  }
  const key = (settings.holdKey || "alt");
  $("hint").textContent = key === "none" ? fjT("popupMenuHint")
    : fjT("popupHoldHint", { key: key[0].toUpperCase() + key.slice(1) });
}

function tabSite() {
  try { return currentTab && isWebPage(currentTab.url) ? new URL(currentTab.url).hostname : ""; } catch (_) { return ""; }
}

function renderSite() {
  const site = tabSite();
  $("siteOffRow").style.display = site && settings && (settings.hiddenSites || []).includes(site) ? "" : "none";
}

async function turnSiteOn() {
  const site = tabSite();
  settings = await send({ action: "saveSettings", settings: { hiddenSites: (settings.hiddenSites || []).filter(h => h !== site) } });
  renderSite();
}

async function toggle(key) {
  settings = await send({ action: "saveSettings", settings: { [key]: !settings[key] } });
  renderSwitches();
}

async function checkHostPermission() {
  // Firefox MV3 does not grant host permissions at install time.
  if (!api.permissions || !api.permissions.contains) return;
  try {
    const ok = await api.permissions.contains({ origins: ["<all_urls>"] });
    $("permBanner").style.display = ok ? "none" : "block";
  } catch (_) {}
}

let pageInfo = null;
function renderTexts() {
  fjApplyTexts(document);
  if (pageInfo) { $("pageTitle").textContent = pageInfo.title; $("pageUrl").textContent = pageInfo.url; }
  if (settings) renderSwitches();
}

document.addEventListener("DOMContentLoaded", async () => {
  fjOnLanguage(() => { renderTexts(); checkStatus(); loadMedia(); });
  renderTexts();
  setStatus("busy", fjT("popupChecking"));
  settings = await send({ action: "getSettings" });
  if (!FJ_PLATFORM.qualitiesInPopup) $("resolveBtn").style.display = "none";
  renderSwitches();
  checkStatus();
  checkHostPermission();

  try { [currentTab] = await api.tabs.query({ active: true, currentWindow: true }); } catch (_) {}
  if (currentTab && isWebPage(currentTab.url)) {
    pageInfo = { title: currentTab.title || fjT("popupCurrentPage"), url: currentTab.url };
    $("urlInput").placeholder = "https://… or magnet:?…";
  } else {
    pageInfo = { title: fjT("popupBrowserPage"), url: fjT("popupOpenWebsite") };
    $("sendPageBtn").disabled = true;
    $("allLinksBtn").disabled = true;
  }
  renderTexts();
  renderSite();
  loadMedia();

  $("status").addEventListener("click", () => online ? checkStatus() : launch());
  $("sendPageBtn").addEventListener("click", () => sendUrl(currentTab.url));
  $("allLinksBtn").addEventListener("click", async () => {
    flash(fjT("popupCollecting"));
    const r = await send({ action: "collectAndSendLinks", tabId: currentTab.id });
    if (r && r.ok) flash(r.count === 1 ? fjT("popupLinksSentOne") : fjT("popupLinksSent", { n: r.count }));
    else flash(r && r.reason === "no links" ? fjT("popupNoLinks") : fjT("popupCouldNotSendLinks"), true);
  });
  $("allMediaBtn").addEventListener("click", async () => {
    const r = await send({ action: "sendDetectedMedia", tabId: currentTab.id });
    if (r && r.ok) flash(r.count === 1 ? fjT("popupItemsSentOne") : fjT("popupItemsSent", { n: r.count })); else flash(fjT("popupCouldNotSend"), true);
  });
  $("clearMediaBtn").addEventListener("click", async () => { await send({ action: "clearDetectedMedia", tabId: currentTab.id }); loadMedia(); });
  $("resolveBtn").addEventListener("click", async () => {
    if (!currentTab || !isWebPage(currentTab.url)) return;
    $("resolveBtn").textContent = fjT("popupWorking");
    const r = await send({ action: "resolveTab", tabId: currentTab.id, force: true });
    $("resolveBtn").textContent = fjT("popupGetQualities");
    await loadMedia();
    if (!r || !r.items || !r.items.length) flash(fjT("popupNoVideo"), true);
  });
  // Keep the list live while the page is still being resolved.
  const refresh = setInterval(loadMedia, 2000);
  window.addEventListener("unload", () => clearInterval(refresh));
  $("sendInputBtn").addEventListener("click", () => {
    const v = $("urlInput").value.trim();
    if (!v) { flash(fjT("popupPasteFirst"), true); return; }
    sendUrl(v);
    $("urlInput").value = "";
  });
  $("urlInput").addEventListener("keydown", (e) => { if (e.key === "Enter") $("sendInputBtn").click(); });
  $("enabledSwitch").addEventListener("click", () => toggle("enabled"));
  $("mediaSwitch").addEventListener("click", () => toggle("captureMedia"));
  $("siteOnBtn").addEventListener("click", turnSiteOn);
  api.storage.onChanged.addListener((changes, area) => {
    if (area === "local" && changes.settings && changes.settings.newValue) { settings = { ...settings, ...changes.settings.newValue }; renderSwitches(); renderSite(); }
  });
  $("optionsLink").addEventListener("click", () => api.runtime.openOptionsPage());
  $("grantBtn").addEventListener("click", async () => {
    try {
      const granted = await api.permissions.request({ origins: ["<all_urls>"] });
      if (granted) $("permBanner").style.display = "none";
    } catch (e) { flash(fjT("popupPermFailed"), true); }
  });
});
