// FetchJet - content script: intercepts clicks on file links, collects links for
// "Download all links", and lets the user bypass capture by holding a key.
(function () {
  if (window.__fetchjetContentLoaded) return;
  window.__fetchjetContentLoaded = true;

  const api = typeof browser !== "undefined" ? browser : chrome;

  let settings = {
    enabled: true,
    captureLinks: true,
    extensions: [],
    excludedHosts: [],
    holdKey: "alt"
  };

  let settingsReady = false;
  function refreshSettings() {
    try {
      api.runtime.sendMessage({ action: "getSettings" }).then((s) => { if (s) settings = s; settingsReady = true; }).catch(() => { settingsReady = true; });
    } catch (_) { settingsReady = true; }
  }
  refreshSettings();
  try {
    api.storage.onChanged.addListener((changes, area) => {
      if (area === "local" && changes.settings && changes.settings.newValue) settings = { ...settings, ...changes.settings.newValue };
    });
  } catch (_) {}

  // Safari on iPhone / iPad has no webRequest to see the media a page loads: read the same
  // URLs (HLS/DASH playlists, mp4, audio) from the Performance API and hand them to the
  // background, which lists them in the popup and the page button like on desktop.
  const ON_IOS = /iPhone|iPad|iPod/i.test(navigator.userAgent) ||
    (/Macintosh/i.test(navigator.userAgent) && (navigator.maxTouchPoints || 0) > 1);
  if (ON_IOS) {
    const MEDIA_PATH = /\.(m3u8|mpd|mp4|m4v|webm|mov|mkv|mp3|m4a|aac|ogg|opus|flac|wav)$/i;
    const seenMedia = new Set();
    const reportMedia = (entries) => {
      const urls = [];
      for (const e of entries) {
        const u = e && e.name;
        if (!u || seenMedia.has(u) || !/^https?:/i.test(u)) continue;
        let path = "";
        try { path = new URL(u).pathname; } catch (_) { continue; }
        if (!MEDIA_PATH.test(path)) continue;
        seenMedia.add(u);
        urls.push(u);
      }
      if (urls.length) {
        try { api.runtime.sendMessage({ action: "mediaSeen", urls }).catch(() => {}); } catch (_) {}
      }
    };
    try {
      reportMedia(performance.getEntriesByType("resource"));
      new PerformanceObserver((list) => reportMedia(list.getEntries())).observe({ type: "resource", buffered: true });
    } catch (_) {}
  }

  // ---- helpers ------------------------------------------------------------

  function isArchiveOrgDirectoryOrDetails(url) {
    try {
      const parsed = new URL(url, window.location.href);
      if (!parsed.hostname.includes("archive.org")) return false;
      const parts = parsed.pathname.split("/").filter(Boolean);
      return (parts[0] === "download" || parts[0] === "details") && parts.length <= 2;
    } catch (_) { return false; }
  }

  function extensionSet() {
    return new Set((settings.extensions || []).map(e => String(e).toLowerCase().replace(/^\./, "")));
  }

  function extOf(path) {
    const m = /\.([a-z0-9]{1,8})$/i.exec(path.replace(/\/+$/, ""));
    return m ? m[1].toLowerCase() : "";
  }

  function isExcludedHost(hostname) {
    const host = (hostname || "").toLowerCase();
    return (settings.excludedHosts || []).some(h => {
      const x = String(h).trim().toLowerCase();
      return x && (host === x || host.endsWith("." + x));
    });
  }

  function isDownloadable(url) {
    if (!url) return false;
    const lower = url.toLowerCase();
    if (lower.startsWith("blob:") || lower.startsWith("data:") || lower.startsWith("javascript:") || lower.startsWith("mailto:")) return false;
    if (lower.startsWith("magnet:")) return true;
    if (isArchiveOrgDirectoryOrDetails(url)) return false;
    try {
      const parsed = new URL(url, window.location.href);
      if (!/^https?:$/.test(parsed.protocol)) return false;
      if (isExcludedHost(parsed.hostname)) return false;
      const exts = extensionSet();
      if (exts.size === 0) return false;
      const e = extOf(parsed.pathname);
      if (e && exts.has(e)) return true;
      // ?file=name.zip / &download=name.rar
      for (const [, v] of parsed.searchParams) {
        const ve = extOf(v);
        if (ve && exts.has(ve) && v.includes(".")) return true;
      }
    } catch (_) {}
    return false;
  }

  function holdKeyPressed(event) {
    switch ((settings.holdKey || "alt").toLowerCase()) {
      case "alt": return event.altKey;
      case "ctrl": return event.ctrlKey;
      case "shift": return event.shiftKey;
      case "meta": return event.metaKey;
      case "none": return false;
      default: return event.altKey;
    }
  }

  // ---- click interception -------------------------------------------------

  // Taken after the page's own click code has run (bubble phase, nothing stopped): a site whose
  // click code makes the link work (sets a cookie, builds the address) no longer answers 403.
  // A click the page already handled (defaultPrevented) or stopped stays with the page; the
  // browser's download is then handed over by the background.
  window.addEventListener("click", (event) => {
    if (!settings.enabled || !settings.captureLinks) return;
    // Only the user's own clicks: a page script's a.click() must not send links with cookies.
    if (!event.isTrusted) return;
    if (event.button !== 0) return;
    if (event.defaultPrevented) return;
    let target = null;
    const path = event.composedPath ? event.composedPath() : [];
    for (const n of path) { if (n && n.tagName === "A" && n.href) { target = n; break; } }
    if (!target) {
      target = event.target;
      while (target && target.tagName !== "A") target = target.parentElement;
    }
    if (!target || !target.href) return;

    const href = target.href;
    if (isArchiveOrgDirectoryOrDetails(href)) return;
    if (isExcludedHost(window.location.hostname)) return;

    const direct = isDownloadable(href);
    const hasDownloadAttr = target.hasAttribute("download") && /^https?:|^magnet:/i.test(href);
    if (!direct && !hasDownloadAttr) return;
    // A PDF link the page does not mark as a download is meant to be read in the browser.
    if (!hasDownloadAttr) { try { if (/\.pdf$/i.test(new URL(href).pathname)) return; } catch (_) {} }

    if (holdKeyPressed(event)) {
      // Let the browser download this one: tell the background to skip the next capture.
      try { api.runtime.sendMessage({ action: "holdBypass" }).catch(() => {}); } catch (_) {}
      return;
    }

    event.preventDefault();
    const fileName = target.getAttribute("download") || "";
    // If FetchJet cannot take it (not running, launch disabled), give the click back to the
    // browser so the file still downloads.
    const browserFallback = () => {
      try { api.runtime.sendMessage({ action: "bypassOnce", url: href }).catch(() => {}); } catch (_) {}
      const a = document.createElement("a");
      a.href = href; if (fileName) a.download = fileName; a.rel = "noopener"; a.style.display = "none";
      document.documentElement.appendChild(a); a.click(); a.remove();
    };
    try {
      api.runtime.sendMessage({ action: "sendToFetchJet", url: href, referer: window.location.href, fileName })
        .then((r) => { if (!r || !r.ok) browserFallback(); })
        .catch(browserFallback);
    } catch (_) { browserFallback(); }
  });

  // ---- link collection ("Download all / selected links") ------------------

  function collectLinks(selectionOnly) {
    const out = new Map();
    const marked = (abs) => {
      try { const u = new URL(abs); return /^https?:$/.test(u.protocol) && !isExcludedHost(u.hostname) && !isArchiveOrgDirectoryOrDetails(abs); } catch (_) { return false; }
    };
    const push = (url, name, asked) => {
      try {
        const abs = new URL(url, window.location.href).href;
        if (!out.has(abs) && (isDownloadable(abs) || (asked && marked(abs)) || /^magnet:|^ftps?:\/\/.*[^\/]$/i.test(abs))) out.set(abs, { url: abs, fileName: name || "" });
      } catch (_) {}
    };
    const DATA_LINKS = "[data-href],[data-url],[data-download],[data-file],[data-link]";

    let roots = [document];
    if (selectionOnly) {
      const sel = window.getSelection();
      roots = [];
      if (sel && sel.rangeCount > 0) {
        for (let i = 0; i < sel.rangeCount; i++) {
          const frag = sel.getRangeAt(i).cloneContents();
          roots.push(frag);
          // Anchors partially inside the selection
          const container = sel.getRangeAt(i).commonAncestorContainer;
          const el = container.nodeType === 1 ? container : container.parentElement;
          if (el) {
            el.querySelectorAll("a[href]").forEach(a => { if (sel.containsNode(a, true)) push(a.href, a.getAttribute("download"), a.hasAttribute("download")); });
          }
        }
      }
    }
    const onSoundCloud = /(^|\.)soundcloud\.com$/i.test(location.hostname);
    const pushTrack = (href) => {
      try {
        const u = new URL(href, window.location.href);
        if (!/(^|\.)soundcloud\.com$/i.test(u.hostname)) return;
        const parts = u.pathname.replace(/^\/n\//, "/").split("/").filter(Boolean);
        if (parts.length !== 2 || parts[1] === "sets") return;
        const clean = "https://soundcloud.com/" + parts.join("/");
        if (!out.has(clean) && isVideoPage(clean)) out.set(clean, { url: clean, fileName: "" });
      } catch (_) {}
    };
    for (const root of roots) {
      root.querySelectorAll && root.querySelectorAll("a[href]").forEach(a => {
        push(a.href, a.getAttribute("download"), a.hasAttribute("download"));
        if (onSoundCloud) pushTrack(a.getAttribute("href"));
      });
      root.querySelectorAll && root.querySelectorAll(DATA_LINKS).forEach(n => {
        for (const key of ["href", "url", "download", "file", "link"]) {
          const v = n.getAttribute("data-" + key);
          if (v && v.length < 2048 && !/\s/.test(v)) push(v, "");
        }
      });
      if (!selectionOnly) {
        root.querySelectorAll("video[src], audio[src], video source[src], audio source[src]").forEach(m => {
          const src = m.getAttribute("src");
          if (src && !src.startsWith("blob:")) push(src, "");
        });
      }
    }
    return Array.from(out.values()).map(l => ({ ...l, referer: window.location.href }));
  }

  // ---- "Download this video" bubbles ---------------------------------------
  // One button on every video of the page (also inside frames and shadow DOM),
  // placed over the video's top-right corner, draggable, with the quality menu.

  const TOP = window.top === window;
  let items = [];            // what the background found for this tab (main video)
  const bubbles = new Map(); // video -> bubble
  let lastPage = "";

  // The site the user is on: a frame takes the tab's site, so "Hide on this site" and the
  // remembered button position follow the page, not the embedded player.
  function ancestorHost(top) {
    if (TOP) return location.hostname;
    try { const a = location.ancestorOrigins; if (a && a.length) return new URL(a[top ? a.length - 1 : 0]).hostname; } catch (_) {}
    try { if (document.referrer) return new URL(document.referrer).hostname; } catch (_) {}
    return top ? location.hostname : "";
  }
  const SITE = ancestorHost(true);
  // Google Drive covers its player frame with its own layer: the Drive page draws the button.
  const DRIVE_PLAYER = !TOP && ancestorHost(false) === "drive.google.com";
  const siteHidden = () => !!SITE && (settings.hiddenSites || []).includes(SITE);
  const buttonOff = () => !settingsReady || !settings.enabled || settings.pagePanel === false || siteHidden() || DRIVE_PLAYER;
  function hideSite() {
    if (!SITE) return;
    const hiddenSites = [...new Set([...(settings.hiddenSites || []), SITE])];
    settings = { ...settings, hiddenSites };
    removeAll();
    try { api.runtime.sendMessage({ action: "saveSettings", settings: { hiddenSites } }).catch(() => {}); } catch (_) {}
  }

  // Where the user dragged the button, per site: [x, y] as parts of the video's width / height.
  let spot = null;
  const readSpot = (all) => { const p = all && all[SITE]; spot = Array.isArray(p) && p.length === 2 ? p : null; };
  try { api.storage.local.get("buttonSpots").then((r) => { readSpot(r && r.buttonSpots); bubbles.forEach(b => placeOne(b)); }).catch(() => {}); } catch (_) {}
  function saveSpot() {
    if (!SITE || !spot) return;
    const mine = spot;
    try {
      api.storage.local.get("buttonSpots").then((r) => api.storage.local.set({ buttonSpots: { ...((r && r.buttonSpots) || {}), [SITE]: mine } })).catch(() => {});
    } catch (_) {}
  }

  // A Google Drive file page: the file itself can be handed to the app as a plain download.
  function driveId() {
    try {
      const u = new URL(location.href);
      if (!/^(drive|docs)\.google\.com$/i.test(u.hostname)) return "";
      const m = /\/file\/d\/([\w-]{10,})/.exec(u.pathname);
      if (m) return m[1];
      const id = /(^|\/)(uc|open)$/.test(u.pathname) ? (u.searchParams.get("id") || "") : "";
      return /^[\w-]{10,}$/.test(id) ? id : "";
    } catch (_) { return ""; }
  }
  let fileHero = null;
  function driveHero() {
    if (!driveId()) return (fileHero = null);
    const shown = (e) => {
      if (!e.isConnected) return false;
      const r = e.getBoundingClientRect();
      return r.width >= 200 && r.height >= 100 && r.bottom > 60 && r.top < innerHeight - 60 &&
        (!e.checkVisibility || e.checkVisibility({ visibilityProperty: true, checkVisibilityCSS: true }));
    };
    if (fileHero && shown(fileHero)) return fileHero;
    let best = null, area = 0;
    for (const sel of ["video, iframe", "img, canvas, embed, object"]) {
      for (const e of document.querySelectorAll(sel)) {
        if (!shown(e)) continue;
        const r = e.getBoundingClientRect();
        if (r.width * r.height > area) { best = e; area = r.width * r.height; }
      }
      if (best) break;
    }
    return (fileHero = best);
  }
  const isFileAnchor = (v) => v === fileHero && !/^(VIDEO|IFRAME)$/.test(v.tagName);

  function fmtSize(n) {
    if (!n) return "";
    const u = ["B", "KB", "MB", "GB"]; let i = 0;
    while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
    return `${n.toFixed(i ? 1 : 0)} ${u[i]}`;
  }
  const sanitizeTitle = (t) => (t || "").replace(/^\(\d+\)\s*/, "").replace(/\s*-\s*YouTube\s*$/i, "").replace(/^Course[:_]\s*/i, "")
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, "_").replace(/\s+/g, " ").trim().slice(0, 120);
  const textOf = (sel) => { try { const e = document.querySelector(sel); return e ? (e.textContent || "").trim() : ""; } catch (_) { return ""; } };
  // The name of what is playing, not of the whole page: course sites show the course in the
  // tab title and the current lesson only in the playlist / heading.
  const mediaTitle = () => {
    const h = location.hostname;
    let t = "";
    if (/udemy\.com$/.test(h)) {
      t = textOf('[class*="curriculum-item-link--is-current"] [data-purpose="item-title"]') ||
          textOf('[aria-current="true"] [data-purpose="item-title"]') ||
          textOf('[data-purpose="curriculum-item-title"]');
    } else if (/coursera\.org$/.test(h) || /linkedin\.com$/.test(h) || /skillshare\.com$/.test(h) || /pluralsight\.com$/.test(h)) {
      t = textOf("h1");
    }
    if (!t) {
      // YouTube and most sites: the tab title is the video's; an <h1> may be an ad or a header.
      const og = /youtube\.com$|youtu\.be$/.test(h) ? null : document.querySelector('meta[property="og:title"]');
      t = document.title || (og && og.content) || "";
    }
    return sanitizeTitle(t) || "video";
  };
  const cleanTitle = mediaTitle;

  // Videos of the page, also those inside shadow DOM (players built as web components).
  let shadowVideos = [], deepRound = 0;
  function collectShadow(root, out, depth) {
    if (depth > 6) return;
    for (const el of root.querySelectorAll("*")) {
      const sr = el.shadowRoot;
      if (!sr) continue;
      out.push(...sr.querySelectorAll("video"));
      collectShadow(sr, out, depth + 1);
    }
  }
  const AUDIO_HERO = [[/soundcloud\.com\//i, () => {
    const h1 = document.querySelector("main h1");
    return document.querySelector(".fullListenHero") || (h1 && h1.closest("section"));
  }]];
  function audioPageUrl() {
    try {
      const u = new URL(location.href);
      if (/(^|\.)soundcloud\.com$/i.test(u.hostname)) return "https://soundcloud.com" + u.pathname.replace(/^\/n\//, "/");
    } catch (_) {}
    return location.href;
  }
  function audioHero() {
    const page = audioPageUrl();
    if (!isVideoPage(page)) return null;
    for (const [re, find] of AUDIO_HERO) if (re.test(page)) return find();
    return null;
  }
  function allVideos() {
    const list = [...document.querySelectorAll("video")];
    if (deepRound++ % 4 === 0) { const found = []; try { collectShadow(document, found, 0); } catch (_) {} shadowVideos = found; }
    for (const v of shadowVideos) if (v.isConnected && !list.includes(v)) list.push(v);
    for (const hero of [audioHero(), driveHero()]) if (hero && !list.includes(hero)) list.push(hero);
    return list;
  }
  // The video the user is watching: a playing one first, else the biggest on screen.
  function biggestVideo() {
    let best = null, score = 0;
    for (const v of allVideos()) {
      const r = v.getBoundingClientRect();
      if (r.width < 200 || r.height < 100) continue;
      const visible = Math.max(0, Math.min(r.bottom, innerHeight) - Math.max(r.top, 0));
      if (visible < 60) continue;
      const sc = r.width * visible * (!v.paused && !v.ended ? 4 : 1);
      if (sc > score) { best = v; score = sc; }
    }
    return best;
  }
  const playing = (v) => !v.paused && !v.ended && v.duration > 3;
  const isMain = (v) => TOP && v === biggestVideo();
  function directSrc(video) {
    const src = video.currentSrc || video.src || "";
    return /^https?:/i.test(src) && !/\.m3u8|\.mpd/i.test(src) ? src : "";
  }

  // Pages that are one video: their own address is the best link for it.
  const VIDEO_PAGE = [
    /youtube\.com\/(watch|shorts\/|live\/|embed\/)/i, /youtube-nocookie\.com\/embed\//i, /youtu\.be\//i,
    /tiktok\.com\/@[^/]+\/(video|photo)\//i, /instagram\.com\/(reels?|p|tv)\//i,
    /facebook\.com\/(.+\/videos\/|watch|reel\/|share\/[vr]\/|plugins\/video)/i, /fb\.watch\//i,
    /(twitter|x)\.com\/[^/]+\/status\//i, /vimeo\.com\/\d+/i, /player\.vimeo\.com\/video\//i,
    /dailymotion\.com\/(video\/|embed\/video\/)/i, /dai\.ly\//i, /reddit\.com\/r\/[^/]+\/comments\//i,
    /twitch\.tv\/(videos\/|[^/]+\/clip\/)/i, /clips\.twitch\.tv\//i, /pinterest\.[a-z.]+\/pin\//i,
    /snapchat\.com\/(spotlight|add)\//i, /linkedin\.com\/(posts|feed\/update)\//i, /streamable\.com\//i, /rumble\.com\//i, /odysee\.com\//i,
    /9gag\.com\/gag\//i, /kick\.com\/[^/]+\/(videos|clips)\//i,
    /soundcloud\.com\/(?!(you|discover|stream|search|upload|charts|pages|settings|messages|notifications|tags|getstarted|company|jobs|imprint|terms-of-use|creators|artists|people|popular|feed|library|n)\/)[^/?#]+\/(?!(tracks|albums|reposts|likes|followers|following|popular-tracks|comments|spotlight|toptracks)(?:[/?#]|$))[^/?#]+/i
  ];
  const isVideoPage = (u) => VIDEO_PAGE.some(r => r.test(u));
  // In feeds (Facebook, Instagram, X...) many videos share a page: the post link next to the video.
  function ownLink(video) {
    let el = video;
    for (let depth = 0; el && depth < 25; depth++) {
      const links = el.querySelectorAll ? el.querySelectorAll("a[href]") : [];
      for (const a of links) if (isVideoPage(a.href)) return a.href.split("#")[0];
      if (el.tagName === "ARTICLE") break;
      el = el.parentElement || (el.parentNode && el.parentNode.host) || null;
    }
    if (!TOP && isVideoPage(location.href)) return location.href;
    return "";
  }
  const linkFor = (video) => isVideoPage(location.href) ? location.href : (ownLink(video) || location.href);

  // Tell the background which video page is being watched, so it asks yt-dlp for the qualities.
  function watchVideos() {
    if (!settings.enabled) return;
    const v = biggestVideo();
    if (!v) return;
    if (isFileAnchor(v) || (v.paused && !(v.duration > 3) && !v.currentSrc)) return;
    const page = v.tagName !== "VIDEO" ? audioPageUrl() : TOP ? linkFor(v) : (isVideoPage(location.href) ? location.href : "");
    const key = (TOP ? "" : "frame:") + page;
    if (key === lastPage) return;
    lastPage = key;
    try { api.runtime.sendMessage({ action: "videoSeen", page, frame: !TOP }).catch(() => {}); } catch (_) {}
  }

  const STYLE = `
    :host { all: initial; }
    .wrap { font: 12px/1.3 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color: #e2e8f0; display: inline-block; }
    /* Compact round icon in the app's colours; the label only appears on hover so it
       covers as little of the video as possible. */
    .btn { position: relative; display: inline-flex; align-items: center; color: #fff; background: linear-gradient(135deg, #6366f1, #a855f7); border-radius: 999px; box-shadow: 0 2px 10px rgba(0,0,0,.5), 0 0 0 1.5px rgba(255,255,255,.35); font-weight: 700; opacity: .82; user-select: none; white-space: nowrap; transition: opacity .15s, padding .15s; }
    .btn:hover, .btn.open, .btn.drag { opacity: 1; }
    .part { display: inline-flex; align-items: center; gap: 0; padding: 0; width: 34px; height: 34px; justify-content: center; cursor: grab; border-radius: 999px; }
    .btn.drag .part { cursor: grabbing; }
    .ico { width: 18px; height: 18px; flex: none; }
    #label { display: none; font-size: 12px; padding-right: 10px; }
    .btn:hover #label, .btn.open #label { display: inline; }
    .btn:hover .part, .btn.open .part { width: auto; padding: 0 6px 0 9px; gap: 6px; }
    .btn:focus-visible { opacity: 1; outline: 2px solid #fff; outline-offset: 2px; }
    .btn:focus-visible #label { display: inline; }
    .btn:focus-visible .part { width: auto; padding: 0 6px 0 9px; gap: 6px; }
    .item:focus-visible, .act:focus-visible, .cut:focus-visible { outline: 2px solid #38bdf8; outline-offset: -2px; }
    .count { position: absolute; top: -5px; right: -5px; min-width: 16px; height: 16px; padding: 0 4px; border-radius: 8px; background: #0f172a; color: #fff; font-size: 10px; line-height: 16px; text-align: center; border: 1.5px solid #a855f7; display: none; }
    .count.on { display: block; }
    .side { position: absolute; top: -7px; left: -7px; width: 16px; height: 16px; border-radius: 50%; background: #0f172a; color: #e2e8f0; font-size: 9px; line-height: 16px; text-align: center; cursor: pointer; display: none; border: 1px solid rgba(255,255,255,.3); }
    .btn:hover .side { display: block; }
    .side:hover { background: #ef4444; }
    .spin { width: 14px; height: 14px; border: 2px solid rgba(255,255,255,.4); border-top-color: #fff; border-radius: 50%; animation: r .8s linear infinite; }
    @keyframes r { to { transform: rotate(360deg); } }
    .wrap { position: relative; }
    .wrap.start .list { right: auto; left: 0; }
    .list { position: absolute; right: 0; top: 40px; width: 340px; background: #0f172a; color: #f1f5f9; border: 1px solid rgba(255,255,255,.12); border-radius: 10px; box-shadow: 0 12px 40px rgba(0,0,0,.55); max-height: 380px; overflow-y: auto; }
    .head { padding: 8px 12px; background: #141a2e; border-bottom: 1px solid rgba(255,255,255,.08); font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .item { padding: 8px 12px; cursor: pointer; border-bottom: 1px solid rgba(255,255,255,.05); display: flex; gap: 8px; }
    .item:last-child { border-bottom: 0; }
    .item:hover { background: #1e293b; }
    .item.off { cursor: default; opacity: .65; }
    .item.off:hover { background: transparent; }
    .foot { justify-content: center; color: #94a3b8; font-size: 11px; padding: 6px 12px; }
    .num { color: #94a3b8; min-width: 18px; text-align: right; }
    .cut { margin-left: auto; align-self: center; flex: 0 0 auto; width: 28px; height: 28px; border-radius: 8px; border: 1px solid rgba(255,255,255,.12); background: transparent; color: #cbd5e1; cursor: pointer; font-size: 14px; line-height: 26px; text-align: center; }
    .cut:hover { background: #334155; color: #fff; }
    .body { flex: 1; min-width: 0; }
    .t { font-weight: 600; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
    .s { color: #94a3b8; font-size: 11px; overflow-wrap: anywhere; }
    .tag { font-size: 10px; font-weight: 800; padding: 0 5px; border-radius: 4px; background: #334155; color: #fff; line-height: 15px; }
    .tag.HD { background: #15803d; } .tag.FullHD { background: #1d4ed8; } .tag.K2 { background: #6d28d9; } .tag.K4 { background: #c2410c; } .tag.K8 { background: #b91c1c; }
    .all { color: #a5b4fc; font-weight: 700; }
    .msg { padding: 10px 12px; color: #4ade80; }
    .msg.err { color: #f87171; }
    .wrap[dir="rtl"] .s { text-align: right; }
    .wrap[data-theme="light"] { color: #0f172a; }
    .wrap[data-theme="light"] .btn { color: #3730a3; background: linear-gradient(135deg, #e0e7ff, #f5e8ff); box-shadow: 0 2px 10px rgba(15,23,42,.28), 0 0 0 1.5px rgba(99,102,241,.55); }
    .wrap[data-theme="light"] .ico path { fill: #3730a3; }
    .wrap[data-theme="light"] .spin { border-color: rgba(67,56,202,.3); border-top-color: #4338ca; }
    .wrap[data-theme="light"] .btn:focus-visible { outline-color: #4338ca; }
    .wrap[data-theme="light"] .list { background: #ffffff; color: #0f172a; border-color: rgba(15,23,42,.14); box-shadow: 0 12px 40px rgba(15,23,42,.25); }
    .wrap[data-theme="light"] .head { background: #f1f5f9; border-bottom-color: rgba(15,23,42,.10); }
    .wrap[data-theme="light"] .item { border-bottom-color: rgba(15,23,42,.08); }
    .wrap[data-theme="light"] .item:hover { background: #eef2ff; }
    .wrap[data-theme="light"] .item.off:hover { background: transparent; }
    .wrap[data-theme="light"] .foot, .wrap[data-theme="light"] .num, .wrap[data-theme="light"] .s { color: #64748b; }
    .wrap[data-theme="light"] .cut { border-color: rgba(15,23,42,.18); color: #334155; }
    .wrap[data-theme="light"] .cut:hover { background: #e2e8f0; color: #0f172a; }
    .wrap[data-theme="light"] .tag { background: #e2e8f0; color: #0f172a; }
    .wrap[data-theme="light"] .all { color: #4338ca; }
    .wrap[data-theme="light"] .msg { color: #15803d; }
    .wrap[data-theme="light"] .msg.err { color: #b91c1c; }
    .wrap[data-theme="light"] .count { background: #ffffff; color: #0f172a; }
    .wrap[data-theme="light"] .side { background: #ffffff; color: #0f172a; border-color: rgba(15,23,42,.25); }
    .wrap[data-theme="light"] .side:hover { background: #ef4444; color: #fff; }
    .act { margin-top: 6px; display: inline-block; background: #6366f1; color: #fff; border-radius: 6px; padding: 4px 10px; cursor: pointer; }`;

  function layer() {
    const f = document.fullscreenElement || document.webkitFullscreenElement;
    return f && f.tagName !== "VIDEO" ? f : document.documentElement;
  }
  const el = (tag, props = {}, ...kids) => {
    const e = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) { if (k === "text") e.textContent = v; else e.setAttribute(k, v); }
    e.append(...kids);
    return e;
  };

  // FetchJet mark: a download arrow onto a tray, drawn in SVG (no text, no emoji).
  function icon() {
    const ns = "http://www.w3.org/2000/svg";
    const svg = document.createElementNS(ns, "svg");
    svg.setAttribute("class", "ico"); svg.setAttribute("id", "ico"); svg.setAttribute("viewBox", "0 0 24 24");
    const path = document.createElementNS(ns, "path");
    path.setAttribute("d", "M12 3v10.2l3.6-3.6 1.4 1.4-6 6-6-6 1.4-1.4 3.6 3.6V3h2zM4 17h16v2H4z");
    path.setAttribute("fill", "#fff");
    svg.appendChild(path);
    return svg;
  }

  const isAudioAnchor = (v) => v.tagName !== "VIDEO" && v !== fileHero;
  const byKind = (v, key) => fjT(key + (isFileAnchor(v) ? "File" : isAudioAnchor(v) ? "Audio" : "Video"));

  let drag = null;

  function makeBubble(video) {
    const host = document.createElement("fetchjet-button");
    host.style.cssText = "all:initial;position:fixed;z-index:2147483647;";
    const root = host.attachShadow({ mode: "closed" });
    const list = el("div", { class: "list", id: "list" }); list.hidden = true;
    const spin = el("span", { class: "spin", id: "spin" }); spin.hidden = true;
    root.append(
      el("style", { text: STYLE }),
      el("div", { class: "wrap" },
        el("div", { class: "btn", id: "main", title: byKind(video, "btnTitle") },
          el("span", { class: "part", id: "grab" }, icon(), spin, el("span", { id: "label", text: byKind(video, "btnDownload") })),
          el("span", { class: "count", id: "count" }),
          el("span", { class: "side", id: "close", title: fjT("hideButton"), text: "✕" })),
        list));
    const b = { host, root, video, open: false, own: null, loading: false, dismissed: false, lifts: 0 };
    fjOnTheme((theme) => { const w = root.querySelector(".wrap"); if (w) w.setAttribute("data-theme", theme); });
    root.querySelector(".wrap").setAttribute("data-theme", fjTheme());
    const mainEl = root.getElementById("main");
    mainEl.tabIndex = 0;
    mainEl.setAttribute("role", "button");
    mainEl.addEventListener("keydown", (e) => {
      if (!e.isTrusted) return;
      if (e.key === "Enter" || e.key === " ") { e.preventDefault(); e.stopPropagation(); clicked(b); }
    });
    root.getElementById("grab").addEventListener("mousedown", (e) => {
      if (e.button !== 0) return;
      const hr = host.getBoundingClientRect();
      drag = { b, x: e.clientX, y: e.clientY, ox: e.clientX - hr.left, oy: e.clientY - hr.top, moved: false };
      e.preventDefault();
    });
    root.getElementById("close").addEventListener("click", () => { b.dismissed = true; host.remove(); });
    layer().appendChild(host);
    bubbles.set(video, b);
    renderOne(b); placeOne(b);
    return b;
  }

  const itemsOf = (b) => b.own ? b.own : (isMain(b.video) ? items : []);

  // Another video on the page (or nothing found yet): look it up now.
  function lookUp(b) {
    b.loading = true; b.open = true;
    renderOne(b, byKind(b.video, "looking"));
    const anchor = b.video.tagName !== "VIDEO";
    const page = anchor ? audioPageUrl() : ownLink(b.video) || (directSrc(b.video) || !TOP ? "" : location.href);
    const msg = { action: "resolveVideo", page, src: anchor ? "" : directSrc(b.video), frame: !TOP };
    api.runtime.sendMessage(msg).then((res) => {
      b.loading = false;
      if (!res) return notRunning(b, () => lookUp(b));
      if (res.error) return res.notRunning || /not running/i.test(res.error) ? notRunning(b, () => lookUp(b)) : renderOne(b, res.error, true);
      b.own = res.items || [];
      if (!b.own.length && /(^|\.)telegram\.org$/i.test(SITE)) return renderOne(b, fjT("telegramNote"), true);
      renderOne(b);
    }).catch(() => { b.loading = false; renderOne(b, fjT("extReloaded"), true); });
  }

  function clicked(b) {
    if (!itemsOf(b).length && !b.loading && !isFileAnchor(b.video)) return lookUp(b);
    b.open = !b.open;
    renderOne(b);
  }

  function notRunning(b, retry) {
    b.open = true;
    renderOne(b, fjT("appNotRunning"), true, {
      text: fjT("startApp"), run: () => {
        renderOne(b, fjT("startingApp"));
        api.runtime.sendMessage({ action: "launchApp" }).then((r) => { if (r && r.online) retry(); else renderOne(b, fjT("appDidNotStart"), true); }).catch(() => {});
      }
    });
  }

  const heightOf = (q) => { const m = /^(\d{3,4})p/.exec(q || ""); return m ? Number(m[1]) : 0; };
  const tagOf = (h) => (!h ? "" : h >= 4320 ? "8K" : h >= 2160 ? "4K" : h >= 1440 ? "2K" : h >= 1080 ? "Full HD" : h >= 720 ? "HD" : "SD");

  function fileRow(b) {
    const id = driveId();
    if (!id) return null;
    const row = el("div", { class: "item" }, el("span", { class: "num", text: "↓" }),
      el("div", { class: "body" }, el("div", { class: "t all", text: fjT("originalFile") }), el("div", { class: "s", text: fjT("originalFileSub") })));
    row.addEventListener("click", () => {
      const name = sanitizeTitle(document.title.replace(/\s+[-–]\s+Google\s+\S+\s*$/, ""));
      sendOne(b, { action: "sendToFetchJet", url: `https://drive.google.com/uc?export=download&id=${id}`, referer: location.href, fileName: /\.[a-z0-9]{1,8}$/i.test(name) ? name : "" });
    });
    return row;
  }
  function footRow() {
    const row = el("div", { class: "item foot", text: fjT("hideOnSite") });
    row.addEventListener("click", hideSite);
    return row;
  }

  function renderOne(b, note, isError, action) {
    const list = itemsOf(b);
    const usable = list.filter(i => !i.disabled);
    const busy = b.loading || (!usable.length && list.some(i => i.disabled && /Getting/.test(i.label)));
    b.root.getElementById("label").textContent = usable.length > 1 ? fjT("downloadOptions", { n: usable.length }) : byKind(b.video, "btnDownload");
    b.root.getElementById("main").title = byKind(b.video, "btnTitle");
    b.root.getElementById("close").title = fjT("hideButton");
    b.root.querySelector(".wrap").dir = fjIsRtl() ? "rtl" : "ltr";
    b.root.querySelector(".wrap").setAttribute("data-theme", fjTheme());
    const count = b.root.getElementById("count");
    count.textContent = String(usable.length);
    count.classList.toggle("on", usable.length > 1 && !b.open);
    b.root.getElementById("spin").hidden = !busy;
    b.root.getElementById("ico").style.display = busy ? "none" : "";
    b.root.getElementById("main").classList.toggle("open", b.open);
    const box = b.root.getElementById("list");
    box.hidden = !b.open;
    box.replaceChildren();
    const file = fileRow(b);
    const tail = () => { if (file) box.appendChild(file); box.appendChild(footRow()); pressable(box); };
    if (note) {
      const d = el("div", { class: "msg" + (isError ? " err" : ""), text: note });
      if (action) { const a = el("div", { class: "act", text: action.text }); a.addEventListener("click", action.run); d.append(el("br"), a); }
      box.appendChild(d); tail(); return;
    }
    if (!list.length) {
      if (!isFileAnchor(b.video)) box.appendChild(el("div", { class: "msg", text: byKind(b.video, "looking") }));
      tail(); return;
    }
    const title = (list.find(m => m.title) || {}).title || cleanTitle();
    box.appendChild(el("div", { class: "head", title, text: title }));
    if (file) box.appendChild(file);
    const files = usable.filter(m => m.kind === "media" && !m.stream);
    if (files.length >= 2) {
      const row = el("div", { class: "item" }, el("span", { class: "num", text: "⇊" }), el("div", { class: "body" }, el("div", { class: "t all", text: fjT("downloadAllFiles", { n: files.length }) })));
      row.addEventListener("click", () => files.forEach((m, i) => setTimeout(() => download(b, m), i * 300)));
      box.appendChild(row);
    }
    let n = 0;
    for (const m of list) {
      const h = heightOf(m.quality);
      const tag = m.kind === "ytdlp" || m.kind === "captured" ? tagOf(h) : "";
      const t = el("div", { class: "t" }, el("span", { text: m.label }));
      if (tag && !/HD|SD|K/.test(m.label)) t.append(el("span", { class: "tag " + tag.replace(/\s/g, "").replace(/^(\d)K$/, "K$1"), text: tag }));
      const subText = m.sub || (m.size ? fmtSize(m.size) : "");
      const row = el("div", { class: "item" + (m.disabled ? " off" : "") },
        el("span", { class: "num", dir: "ltr", text: m.disabled ? "" : `${++n}.` }),
        el("div", { class: "body" }, t, el("div", { class: "s", dir: "ltr", text: subText.length > 110 ? subText.slice(0, 110) + "…" : subText })));
      if (!m.disabled) row.addEventListener("click", () => download(b, m));
      if (!m.disabled && m.kind === "ytdlp") {
        // Open FetchJet on this quality with the Add dialog, instead of downloading at once.
        const cut = el("button", { class: "cut", text: "↗", title: fjT("clipTitle") });
        cut.addEventListener("click", (e) => { e.stopPropagation(); download(b, { ...m, openClip: true }); });
        row.appendChild(cut);
      }
      box.appendChild(row);
    }
    box.appendChild(footRow());
    pressable(box);
  }

  function pressable(box) {
    box.querySelectorAll(".item:not(.off), .act, .cut").forEach((n) => {
      n.tabIndex = 0;
      n.addEventListener("keydown", (e) => {
        if (!e.isTrusted) return;
        if (e.key === "Enter" || e.key === " ") { e.preventDefault(); e.stopPropagation(); n.click(); }
      });
    });
  }

  const download = (b, m) => sendOne(b, { action: "downloadItem", item: { ...m, title: m.title || cleanTitle() } });
  function sendOne(b, msg) {
    api.runtime.sendMessage(msg).then((res) => {
      if (!res || !res.ok) {
        if (res && /not running|did not start/i.test(res.reason || "")) return notRunning(b, () => sendOne(b, msg));
        return flashOne(b, fjT("couldNotSendReason", { reason: fjReason(res && res.reason) }), true);
      }
      flashOne(b, fjT("sentToApp"));
    }).catch(() => flashOne(b, fjT("cantReachExtension"), true));
  }
  function flashOne(b, text, isError) {
    b.open = true; renderOne(b, text, isError);
    setTimeout(() => { b.open = false; renderOne(b); }, 2500);
  }

  function snapSpot(b) {
    const r = b.video.getBoundingClientRect();
    if (!spot || !r.width || !r.height) return;
    const near = 24;
    spot = [
      spot[0] * r.width < near ? 0 : (1 - spot[0]) * r.width < near ? 1 : spot[0],
      spot[1] * r.height < near ? 0 : (1 - spot[1]) * r.height < near ? 1 : spot[1]
    ];
  }

  function placeOne(b) {
    if (b.dismissed) return;
    const fs = document.fullscreenElement || document.webkitFullscreenElement;
    const r = b.video.getBoundingClientRect();
    const onScreen = r.bottom > 0 && r.top < innerHeight && r.width >= 200 && r.height >= 100 && b.video.isConnected &&
      !(fs && fs.tagName === "VIDEO") && (!fs || fs.contains(b.video) || fs.shadowRoot);
    b.host.style.display = onScreen ? "block" : "none";
    if (!onScreen) return;
    const top = layer(), next = b.host.nextElementSibling;
    if (b.host.parentNode !== top) top.appendChild(b.host);
    else if (next && next.localName !== "fetchjet-button" && b.lifts < 6) { b.lifts++; top.appendChild(b.host); }
    const w = b.open ? 340 : 34; // keep the anchor stable while the label expands on hover
    const h = 34;
    const sp = spot;
    let y = sp ? r.top + sp[1] * r.height : r.top + 10;
    y = Math.max(r.top, Math.min(y, r.bottom - h));
    b.host.style.top = `${Math.max(4, Math.min(y, innerHeight - h - 4))}px`;
    if (!sp) {
      // Anchored to the video's top-right corner: the label grows to the left on hover,
      // so it never spills outside the video.
      b.host.style.left = "auto";
      b.host.style.right = `${Math.max(4, innerWidth - r.right + 10)}px`;
      b.host.style.flexDirection = "row-reverse";
    } else {
      let x = r.left + sp[0] * r.width;
      x = Math.max(r.left, Math.min(x, r.right - 34));
      b.host.style.right = "auto";
      b.host.style.left = `${Math.max(4, Math.min(x, innerWidth - w))}px`;
    }
    // Near the left edge the menu opens to the right of the button instead of being cut off.
    if (!b.open) {
      const hr = b.host.getBoundingClientRect();
      const wrapEl = b.root.querySelector(".wrap");
      if (wrapEl) wrapEl.classList.toggle("start", hr.right - 340 < 4 && hr.left + 340 <= innerWidth - 4);
    }
  }

  // A video that is "on screen" but hidden under the page's own content (a fixed background video
  // that the sections scroll over): nothing of it can be seen, so it gets no button.
  function pinned(v) {
    for (let e = v; e && e.nodeType === 1; e = e.parentElement) {
      let pos = "";
      try { pos = getComputedStyle(e).position; } catch (_) {}
      if (pos === "fixed" || pos === "sticky") return true;
    }
    return false;
  }
  // Does this element (or one of its wrappers up to the video's own) hide what is behind it: a
  // solid background or a picture, not a clear layer or a gradient?
  function paintsOver(el, v) {
    for (let n = el; n && n.nodeType === 1 && !n.contains(v); n = n.parentElement) {
      let cs = null;
      try { cs = getComputedStyle(n); } catch (_) { return false; }
      const m = /rgba?\(([^)]+)\)/.exec(cs.backgroundColor || "");
      if (m) { const a = m[1].split(",")[3]; if (a === undefined || parseFloat(a) >= 0.9) return true; }
      if (cs.backgroundImage && cs.backgroundImage !== "none" && !/gradient/.test(cs.backgroundImage)) return true;
      if (/^(IMG|VIDEO|CANVAS|IFRAME)$/.test(n.tagName)) return true;
    }
    return false;
  }
  function covered(v) {
    if (v.tagName !== "VIDEO" || !pinned(v)) return false;
    const r = v.getBoundingClientRect();
    // The player: the video's own box plus the wrappers that are about its size (a site draws its
    // controls and gradients over the video inside them).
    let box = v;
    for (let e = v.parentElement; e && e !== document.body && e !== document.documentElement; e = e.parentElement) {
      const er = e.getBoundingClientRect();
      if (er.width > r.width * 1.25 || er.height > r.height * 1.25) break;
      box = e;
    }
    const pts = [[0.12, 0.12], [0.88, 0.12], [0.12, 0.88], [0.88, 0.88]];
    let seen = 0, tested = 0;
    for (const [fx, fy] of pts) {
      const x = r.left + fx * r.width, y = r.top + fy * r.height;
      if (x < 0 || y < 0 || x >= innerWidth || y >= innerHeight) continue;
      let stack = [];
      try { stack = document.elementsFromPoint(x, y); } catch (_) { return false; }
      const top = stack.find(e => e.localName !== "fetchjet-button");
      if (!top) continue;
      tested++;
      if (top === v || box.contains(top) || top.contains(v) || !paintsOver(top, v)) seen++;
    }
    return tested > 0 && seen === 0;
  }

  // Pages stack several videos in one place (a hero that cross-fades clips, a clip inside a
  // laptop picture): one button for the group, on the main or playing one, then the biggest.
  function distinct(vids) {
    const rects = new Map(vids.map(v => [v, v.getBoundingClientRect()]));
    const area = (r) => r.width * r.height;
    const common = (a, b) => Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left)) * Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
    const rank = (v) => (isMain(v) ? 2 : 0) + (v.tagName === "VIDEO" && playing(v) ? 1 : 0);
    const ranked = vids.slice().sort((a, b) => (rank(b) - rank(a)) || (area(rects.get(b)) - area(rects.get(a))));
    const kept = [];
    for (const v of ranked) {
      const r = rects.get(v);
      if (kept.some(k => common(rects.get(k), r) > 0.5 * Math.min(area(rects.get(k)), area(r)))) continue;
      kept.push(v);
    }
    return kept;
  }

  function place() {
    if (buttonOff()) return;
    const all = allVideos();
    const seen = all.filter(v => { const r = v.getBoundingClientRect(); return r.width >= 200 && r.height >= 100 && !covered(v); });
    const vids = distinct(seen);
    for (const [v, b] of bubbles) if (!v.isConnected || (v.tagName !== "VIDEO" && !all.includes(v))) { b.host.remove(); bubbles.delete(v); }
    for (const [v, b] of bubbles) if (!b.dismissed && !vids.includes(v)) { b.host.remove(); bubbles.delete(v); }
    const oneVideoSite = /(^|\.)youtube\.com$/i.test(location.hostname);
    for (const v of vids.slice(0, 12)) {
      if (oneVideoSite && !isMain(v)) {
        const old = bubbles.get(v);
        if (old) { old.host.remove(); bubbles.delete(v); }
        continue;
      }
      let b = bubbles.get(v);
      if (!b) {
        // main video: once the background found something or it plays; others: as soon as they are real players
        const show = v.tagName !== "VIDEO" || v === fileHero || (isMain(v) ? (items.length || playing(v)) : !(v.paused && !(v.duration > 3) && !directSrc(v)));
        if (!show) continue;
        b = makeBubble(v);
      }
      if (!b.dismissed) placeOne(b);
    }
  }

  function removeAll() { for (const b of bubbles.values()) b.host.remove(); bubbles.clear(); }

  function update(newItems) {
    if (!TOP) return; // frames look up their own videos
    items = newItems || [];
    if (!items.length) { removeAll(); return; }
    if (buttonOff()) return;
    place();
    for (const b of bubbles.values()) if (!b.own && !b.loading) renderOne(b);
  }

  // click elsewhere / Escape closes an open menu
  addEventListener("mousedown", (e) => {
    const path = e.composedPath ? e.composedPath() : [];
    for (const b of bubbles.values()) if (b.open && !path.includes(b.host)) { b.open = false; renderOne(b); }
  }, true);
  addEventListener("mousemove", (e) => {
    if (!drag) return;
    if (!drag.moved && Math.abs(e.clientX - drag.x) + Math.abs(e.clientY - drag.y) < 5) return;
    drag.moved = true;
    drag.b.root.getElementById("main").classList.add("drag");
    const r = drag.b.video.getBoundingClientRect();
    if (!r.width || !r.height) return;
    spot = [(e.clientX - drag.ox - r.left) / r.width, (e.clientY - drag.oy - r.top) / r.height].map(n => Math.round(Math.max(0, Math.min(1, n)) * 1000) / 1000);
    bubbles.forEach(b => placeOne(b));
  }, true);
  addEventListener("mouseup", () => {
    if (!drag) return;
    const d = drag; drag = null;
    d.b.root.getElementById("main").classList.remove("drag");
    if (d.moved) { snapSpot(d.b); bubbles.forEach(b => placeOne(b)); saveSpot(); } else clicked(d.b);
  }, true);
  // Presses on the button or its menu stay inside it: players that listen for every press in
  // their area (Google Drive) would take them. Our own handlers get a copy that never leaves
  // the shadow tree.
  for (const type of ["pointerdown", "pointerup", "mousedown", "mouseup", "click", "dblclick", "touchstart", "touchend"]) {
    addEventListener(type, (e) => {
      if (!e.isTrusted) return;
      const path = e.composedPath ? e.composedPath() : [];
      let b = null;
      for (const one of bubbles.values()) if (path.includes(one.host)) { b = one; break; }
      if (!b) return;
      e.stopImmediatePropagation();
      if (type !== "mousedown" && type !== "click") return;
      let inner = b.root.elementFromPoint ? b.root.elementFromPoint(e.clientX, e.clientY) : null;
      if (!inner || !b.root.contains(inner)) inner = b.root.getElementById("main");
      const copy = new MouseEvent(type, { bubbles: true, cancelable: true, composed: false, clientX: e.clientX, clientY: e.clientY, button: e.button });
      if (!inner.dispatchEvent(copy)) e.preventDefault();
    }, true);
  }
  addEventListener("keydown", (e) => { if (e.key === "Escape") for (const b of bubbles.values()) if (b.open) { b.open = false; renderOne(b); } });
  for (const ev of ["fullscreenchange", "webkitfullscreenchange"]) document.addEventListener(ev, () => setTimeout(() => bubbles.forEach(b => placeOne(b)), 50));
  addEventListener("scroll", place, { passive: true, capture: true });
  addEventListener("resize", place, { passive: true });
  setInterval(place, 700);
  setInterval(watchVideos, 1500);
  document.addEventListener("play", () => setTimeout(() => { watchVideos(); place(); }, 300), true);
  addEventListener("popstate", () => setTimeout(watchVideos, 500));
  try {
    api.storage.onChanged.addListener((changes, area) => {
      if (area !== "local") return;
      if (changes.settings && changes.settings.newValue) {
        if (buttonOff()) removeAll(); else place();
      }
      if (changes.buttonSpots && !drag) { readSpot(changes.buttonSpots.newValue); bubbles.forEach(b => placeOne(b)); }
    });
  } catch (_) {}
  if (TOP) {
    // media / qualities the background found before this script loaded
    try { api.runtime.sendMessage({ action: "getItems", videoPage: isVideoPage(location.href) }).then((r) => { if (r && r.items) update(r.items); }).catch(() => {}); } catch (_) {}
  }

  fjOnLanguage(() => { for (const b of bubbles.values()) renderOne(b); });

  api.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request && request.action === "fetchjetItems") {
      update(request.items);
      return false;
    }
    if (request && request.action === "openPanel") {
      const v = buttonOff() ? null : biggestVideo();
      if (!v) return false;
      let b = bubbles.get(v) || makeBubble(v);
      b.dismissed = false; layer().appendChild(b.host); b.open = true;
      if (!itemsOf(b).length && !isFileAnchor(v)) lookUp(b); else renderOne(b);
      placeOne(b);
      return false;
    }
    if (request && request.action === "collectLinks") {
      sendResponse({ links: collectLinks(!!request.selectionOnly) });
      return false;
    }
    if (request && request.action === "pageInfo") {
      sendResponse({ title: mediaTitle(), url: window.location.href });
      return false;
    }
    return false;
  });
})();
