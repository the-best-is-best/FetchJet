const api = typeof browser !== "undefined" ? browser : chrome;
const $ = (id) => document.getElementById(id);
const send = (msg) => api.runtime.sendMessage(msg);

const DEFAULT_EXTENSIONS = [
  "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts",
  "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma",
  "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst", "cab",
  "pdf", "epub", "mobi", "djvu",
  "exe", "msi", "msix", "dmg", "pkg", "deb", "rpm", "appimage", "iso", "img", "bin",
  "apk", "xapk", "ipa", "jar",
  "torrent"
];

const BOOLS = ["enabled", "captureDownloads", "captureLinks", "captureMedia", "sendCookies", "notifications", "autoLaunch", "autoResolve", "pagePanel"];

function parseList(text, lower = true) {
  return text.split(/[\s,;]+/).map(s => s.trim().replace(/^\./, "")).filter(Boolean).map(s => lower ? s.toLowerCase() : s);
}

function hide(id) { const el = $(id); if (el) el.style.display = "none"; }

function fitToBrowser() {
  const p = FJ_PLATFORM;
  fjApplyTexts(document);
  p.hidden.forEach(hide);
  if (p.hidden.includes("cardShortcuts")) $("masterHint").textContent = fjT("optMasterSwitch");
  if (p.shortcutsText) $("shortcutsHelp").textContent = fjT(p.shortcutsText);
  else if (p.shortcutsPage) {
    const [before, after] = fjT("optShortcutsChange").split("{page}");
    $("shortcutsHelp").replaceChildren(before || "", Object.assign(document.createElement("code"), { textContent: p.shortcutsPage }), after || "");
  }
  if (p.cookiesNote) $("cookiesNote").textContent = fjT(p.cookiesNote);
}

async function load() {
  const s = await send({ action: "getSettings" });
  for (const k of BOOLS) $(k).checked = !!s[k];
  $("holdKey").value = s.holdKey || "alt";
  $("minSizeKB").value = s.minSizeKB || 0;
  $("extensions").value = (s.extensions || []).join(", ");
  $("excludedHosts").value = (s.excludedHosts || []).join("\n");

  lastStatus = await send({ action: "checkStatus" });
  if (lastStatus && lastStatus.config && lastStatus.config.language) fjUseLanguage(lastStatus.config.language);
  renderStatus();

  if (api.permissions && api.permissions.contains) {
    try {
      const ok = await api.permissions.contains({ origins: ["<all_urls>"] });
      $("permRow").style.display = ok ? "none" : "flex";
    } catch (_) {}
  }
}

async function save() {
  const settings = {};
  for (const k of BOOLS) settings[k] = $(k).checked;
  settings.holdKey = $("holdKey").value;
  settings.minSizeKB = Math.max(0, parseInt($("minSizeKB").value, 10) || 0);
  settings.extensions = parseList($("extensions").value);
  settings.excludedHosts = parseList($("excludedHosts").value);
  await send({ action: "saveSettings", settings });
  $("saved").textContent = fjT("optSaved");
  setTimeout(() => { $("saved").textContent = ""; }, 2000);
}

let lastStatus = null;
function renderStatus() {
  const r = lastStatus;
  if (FJ_PLATFORM.status) {
    setStatus(fjT(FJ_PLATFORM.status), "");
  } else if (r && r.online) {
    const c = r.config || {};
    setStatus(fjT("optConnectedTo"), fjT("optConnectedDetail", { version: c.version ? "v" + c.version : "" }) +
      (c.mediaExtractor === false ? fjT("optNoExtractor") : ""));
  } else if (r) {
    setStatus(fjT("optNotRunningBold"), fjT("optNotRunningDetail"));
  }
}

document.addEventListener("DOMContentLoaded", () => {
  fjOnLanguage(() => { fitToBrowser(); renderStatus(); });
  fitToBrowser();
  load();
  $("save").addEventListener("click", save);
  $("resetExt").addEventListener("click", () => { $("extensions").value = DEFAULT_EXTENSIONS.join(", "); });
  $("grantBtn").addEventListener("click", async () => {
    try {
      const granted = await api.permissions.request({ origins: ["<all_urls>"] });
      if (granted) $("permRow").style.display = "none";
    } catch (_) {}
  });
});

// Bold label + plain text, built without innerHTML.
function setStatus(bold, rest) {
  const el = $("connStatus");
  el.textContent = "";
  const b = document.createElement("b");
  b.textContent = bold;
  el.append(b, document.createTextNode(rest));
}
