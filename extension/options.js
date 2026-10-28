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

async function load() {
  const s = await send({ action: "getSettings" });
  for (const k of BOOLS) $(k).checked = !!s[k];
  $("holdKey").value = s.holdKey || "alt";
  $("minSizeKB").value = s.minSizeKB || 0;
  $("extensions").value = (s.extensions || []).join(", ");
  $("excludedHosts").value = (s.excludedHosts || []).join("\n");

  const r = await send({ action: "checkStatus" });
  if (r && r.online) {
    const c = r.config || {};
    $("connStatus").innerHTML = `<b>Connected</b> to FetchJet ${c.version ? "v" + c.version : ""} on 127.0.0.1:28450` +
      (c.mediaExtractor === false ? " · media extractor disabled in this edition" : "");
  } else {
    $("connStatus").innerHTML = `<b>FetchJet is not running.</b> Start the desktop app, then reload this page.`;
  }

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
  $("saved").textContent = "Saved";
  setTimeout(() => { $("saved").textContent = ""; }, 2000);
}

document.addEventListener("DOMContentLoaded", () => {
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
