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

async function loadMedia() {
  if (!currentTab) return;
  const r = await send({ action: "getItems", tabId: currentTab.id });
  const items = (r && r.items) || [];
  const list = $("mediaList");
  list.replaceChildren();
  const files = items.filter(i => i.kind === "media");
  $("allMediaBtn").style.display = files.length > 1 ? "" : "none";
  if (items.length === 0) {
    const empty = document.createElement("div");
    empty.className = "empty";
    empty.textContent = !FJ_PLATFORM.qualitiesInPopup ? fjT("popupEmptyNoQualities") : fjT("popupEmpty");
    list.appendChild(empty);
    return;
  }
  for (const it of items) {
    const row = document.createElement("div");
    row.className = "media" + (it.disabled ? " off" : "");
    const kind = it.kind === "ytdlp" ? "yt-dlp" : it.kind === "captured" ? "stream" : it.kind === "playlist" ? "list" : (it.label.split(" ")[0] || "file");
    const badge = document.createElement("span"); badge.className = `kind ${it.kind || ""} ${kind}`; badge.textContent = it.disabled ? "…" : kind;
    const name = document.createElement("span"); name.className = "name"; name.title = it.sub || it.url || "";
    name.textContent = it.label;
    if (it.sub && it.kind !== "media") { const sub = document.createElement("span"); sub.className = "sub"; sub.textContent = it.sub; name.appendChild(sub); }
    const size = document.createElement("span"); size.className = "size"; size.dir = "ltr"; size.textContent = it.kind === "media" ? human(it.size) : "";
    row.append(badge, name, size);
    if (!it.disabled) {
      const btn = document.createElement("button");
      btn.className = "small primary"; btn.style.flex = "0 0 auto"; btn.textContent = "⬇"; btn.title = fjT("popupDownloadWith");
      btn.addEventListener("click", async () => {
        btn.disabled = true; flash(fjT("popupSending"));
        const res = await send({ action: "downloadItem", item: it, tabId: currentTab.id });
        if (res && res.ok) flash(fjT("popupSent")); else flash(fjT("couldNotSendParen", { reason: fjReason(res && res.reason) }), true, 4000);
        btn.disabled = false;
      });
      row.appendChild(btn);
      if (it.kind === "ytdlp") {
        const cut = document.createElement("button");
        cut.className = "small"; cut.style.flex = "0 0 auto"; cut.textContent = "✂"; cut.title = fjT("clipTitle");
        cut.addEventListener("click", async () => {
          cut.disabled = true; flash(fjT("popupOpening"));
          const res = await send({ action: "downloadItem", item: { ...it, openClip: true }, tabId: currentTab.id });
          if (res && res.ok) flash(fjT("popupOpened")); else flash(fjT("couldNotSendParen", { reason: fjReason(res && res.reason) }), true, 4000);
          cut.disabled = false;
        });
        row.appendChild(cut);
      }
    }
    list.appendChild(row);
  }
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
  $("optionsLink").addEventListener("click", () => api.runtime.openOptionsPage());
  $("grantBtn").addEventListener("click", async () => {
    try {
      const granted = await api.permissions.request({ origins: ["<all_urls>"] });
      if (granted) $("permBanner").style.display = "none";
    } catch (e) { flash(fjT("popupPermFailed"), true); }
  });
});
