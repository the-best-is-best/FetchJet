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

  function refreshSettings() {
    try {
      api.runtime.sendMessage({ action: "getSettings" }).then((s) => { if (s) settings = s; }).catch(() => {});
    } catch (_) {}
  }
  refreshSettings();
  try {
    api.storage.onChanged.addListener((changes, area) => {
      if (area === "local" && changes.settings && changes.settings.newValue) settings = { ...settings, ...changes.settings.newValue };
    });
  } catch (_) {}

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

  document.addEventListener("click", (event) => {
    if (!settings.enabled || !settings.captureLinks) return;
    if (event.button !== 0) return;
    let target = event.target;
    while (target && target.tagName !== "A") target = target.parentElement;
    if (!target || !target.href) return;

    const href = target.href;
    if (isArchiveOrgDirectoryOrDetails(href)) return;
    if (isExcludedHost(window.location.hostname)) return;

    const direct = isDownloadable(href);
    const hasDownloadAttr = target.hasAttribute("download") && /^https?:|^magnet:/i.test(href);
    if (!direct && !hasDownloadAttr) return;

    if (holdKeyPressed(event)) {
      // Let the browser download this one: tell the background to skip the next capture.
      try { api.runtime.sendMessage({ action: "holdBypass" }).catch(() => {}); } catch (_) {}
      return;
    }

    event.preventDefault();
    event.stopPropagation();
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
  }, true);

  // ---- link collection ("Download all / selected links") ------------------

  function collectLinks(selectionOnly) {
    const out = new Map();
    const push = (url, name) => {
      try {
        const abs = new URL(url, window.location.href).href;
        if (!out.has(abs) && (isDownloadable(abs) || /^magnet:/i.test(abs))) out.set(abs, { url: abs, fileName: name || "" });
      } catch (_) {}
    };

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
            el.querySelectorAll("a[href]").forEach(a => { if (sel.containsNode(a, true)) push(a.href, a.getAttribute("download")); });
          }
        }
      }
    }
    for (const root of roots) {
      root.querySelectorAll && root.querySelectorAll("a[href]").forEach(a => push(a.href, a.getAttribute("download")));
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
  function allVideos() {
    const list = [...document.querySelectorAll("video")];
    if (deepRound++ % 4 === 0) { const found = []; try { collectShadow(document, found, 0); } catch (_) {} shadowVideos = found; }
    for (const v of shadowVideos) if (v.isConnected && !list.includes(v)) list.push(v);
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
    /snapchat\.com\/(spotlight|add)\//i, /linkedin\.com\/(posts|feed\/update)\//i, /streamable\.com\//i, /rumble\.com\//i, /odysee\.com\//i
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
    if (v.paused && !(v.duration > 3) && !v.currentSrc) return;
    const page = TOP ? linkFor(v) : (isVideoPage(location.href) ? location.href : "");
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
    .count { position: absolute; top: -5px; right: -5px; min-width: 16px; height: 16px; padding: 0 4px; border-radius: 8px; background: #0f172a; color: #fff; font-size: 10px; line-height: 16px; text-align: center; border: 1.5px solid #a855f7; display: none; }
    .count.on { display: block; }
    .side { position: absolute; top: -7px; left: -7px; width: 16px; height: 16px; border-radius: 50%; background: #0f172a; color: #e2e8f0; font-size: 9px; line-height: 16px; text-align: center; cursor: pointer; display: none; border: 1px solid rgba(255,255,255,.3); }
    .btn:hover .side { display: block; }
    .side:hover { background: #ef4444; }
    .spin { width: 14px; height: 14px; border: 2px solid rgba(255,255,255,.4); border-top-color: #fff; border-radius: 50%; animation: r .8s linear infinite; }
    @keyframes r { to { transform: rotate(360deg); } }
    .wrap { position: relative; }
    .list { position: absolute; right: 0; top: 40px; width: 340px; background: #0f172a; color: #f1f5f9; border: 1px solid rgba(255,255,255,.12); border-radius: 10px; box-shadow: 0 12px 40px rgba(0,0,0,.55); max-height: 380px; overflow-y: auto; }
    .head { padding: 8px 12px; background: #141a2e; border-bottom: 1px solid rgba(255,255,255,.08); font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .item { padding: 8px 12px; cursor: pointer; border-bottom: 1px solid rgba(255,255,255,.05); display: flex; gap: 8px; }
    .item:last-child { border-bottom: 0; }
    .item:hover { background: #1e293b; }
    .item.off { cursor: default; opacity: .65; }
    .item.off:hover { background: transparent; }
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

  function makeBubble(video) {
    const host = document.createElement("fetchjet-button");
    host.style.cssText = "all:initial;position:fixed;z-index:2147483647;";
    const root = host.attachShadow({ mode: "closed" });
    const list = el("div", { class: "list", id: "list" }); list.hidden = true;
    const spin = el("span", { class: "spin", id: "spin" }); spin.hidden = true;
    root.append(
      el("style", { text: STYLE }),
      el("div", { class: "wrap" },
        el("div", { class: "btn", id: "main", title: "Download this video with FetchJet" },
          el("span", { class: "part", id: "grab" }, icon(), spin, el("span", { id: "label", text: "Download this video" })),
          el("span", { class: "count", id: "count" }),
          el("span", { class: "side", id: "close", title: "Hide this button", text: "✕" })),
        list));
    const b = { host, root, video, open: false, own: null, loading: false, dx: null, dy: null, dismissed: false };
    let down = null;
    root.getElementById("grab").addEventListener("mousedown", (e) => {
      if (e.button !== 0) return;
      const hr = host.getBoundingClientRect();
      down = { x: e.clientX, y: e.clientY, ox: e.clientX - hr.left, oy: e.clientY - hr.top, moved: false };
      e.preventDefault();
    });
    addEventListener("mousemove", (e) => {
      if (!down) return;
      if (!down.moved && Math.abs(e.clientX - down.x) + Math.abs(e.clientY - down.y) < 5) return;
      down.moved = true;
      root.getElementById("main").classList.add("drag");
      const r = video.getBoundingClientRect();
      b.dx = e.clientX - down.ox - r.left; b.dy = e.clientY - down.oy - r.top;
      placeOne(b);
    });
    addEventListener("mouseup", () => {
      if (!down) return;
      const wasDrag = down.moved; down = null;
      root.getElementById("main").classList.remove("drag");
      if (!wasDrag) clicked(b);
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
    renderOne(b, "Looking for the video qualities…");
    const msg = { action: "resolveVideo", page: ownLink(b.video) || (directSrc(b.video) || !TOP ? "" : location.href), src: directSrc(b.video), frame: !TOP };
    api.runtime.sendMessage(msg).then((res) => {
      b.loading = false;
      if (!res) return notRunning(b, () => lookUp(b));
      if (res.error) return /not running/i.test(res.error) ? notRunning(b, () => lookUp(b)) : renderOne(b, res.error, true);
      b.own = res.items || [];
      renderOne(b);
    }).catch(() => { b.loading = false; renderOne(b, "FetchJet extension was reloaded - refresh the page", true); });
  }

  function clicked(b) {
    if (!itemsOf(b).length && !b.loading) return lookUp(b);
    b.open = !b.open;
    renderOne(b);
  }

  function notRunning(b, retry) {
    b.open = true;
    renderOne(b, "FetchJet is not running.", true, {
      text: "Start FetchJet", run: () => {
        renderOne(b, "Starting FetchJet…");
        api.runtime.sendMessage({ action: "launchApp" }).then((r) => { if (r && r.online) retry(); else renderOne(b, "FetchJet did not start. Open it from the Start menu and try again.", true); }).catch(() => {});
      }
    });
  }

  const heightOf = (q) => { const m = /^(\d{3,4})p/.exec(q || ""); return m ? Number(m[1]) : 0; };
  const tagOf = (h) => (!h ? "" : h >= 4320 ? "8K" : h >= 2160 ? "4K" : h >= 1440 ? "2K" : h >= 1080 ? "Full HD" : h >= 720 ? "HD" : "SD");

  function renderOne(b, note, isError, action) {
    const list = itemsOf(b);
    const usable = list.filter(i => !i.disabled);
    const busy = b.loading || (!usable.length && list.some(i => i.disabled && /Getting/.test(i.label)));
    b.root.getElementById("label").textContent = usable.length > 1 ? `Download · ${usable.length} options` : "Download this video";
    const count = b.root.getElementById("count");
    count.textContent = String(usable.length);
    count.classList.toggle("on", usable.length > 1 && !b.open);
    b.root.getElementById("spin").hidden = !busy;
    b.root.getElementById("ico").style.display = busy ? "none" : "";
    b.root.getElementById("main").classList.toggle("open", b.open);
    const box = b.root.getElementById("list");
    box.hidden = !b.open;
    box.replaceChildren();
    if (note) {
      const d = el("div", { class: "msg" + (isError ? " err" : ""), text: note });
      if (action) { const a = el("div", { class: "act", text: action.text }); a.addEventListener("click", action.run); d.append(el("br"), a); }
      box.appendChild(d); return;
    }
    if (!list.length) { box.appendChild(el("div", { class: "msg", text: "Looking for the video qualities…" })); return; }
    const title = (list.find(m => m.title) || {}).title || cleanTitle();
    box.appendChild(el("div", { class: "head", title, text: title }));
    const files = usable.filter(m => m.kind === "media");
    if (files.length >= 2) {
      const row = el("div", { class: "item" }, el("span", { class: "num", text: "⇊" }), el("div", { class: "body" }, el("div", { class: "t all", text: `Download all (${files.length} files)` })));
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
        el("span", { class: "num", text: m.disabled ? "" : `${++n}.` }),
        el("div", { class: "body" }, t, el("div", { class: "s", text: subText.length > 110 ? subText.slice(0, 110) + "…" : subText })));
      if (!m.disabled) row.addEventListener("click", () => download(b, m));
      if (!m.disabled && m.kind === "ytdlp") {
        // Scissors: open FetchJet on this quality with the clip range on (download a part only).
        const cut = el("button", { class: "cut", text: "✂", title: "Download a part of it (clip) in FetchJet" });
        cut.addEventListener("click", (e) => { e.stopPropagation(); download(b, { ...m, openClip: true }); });
        row.appendChild(cut);
      }
      box.appendChild(row);
    }
  }

  function download(b, m) {
    api.runtime.sendMessage({ action: "downloadItem", item: { ...m, title: m.title || cleanTitle() } }).then((res) => {
      if (!res || !res.ok) {
        if (res && /not running|did not start/i.test(res.reason || "")) return notRunning(b, () => download(b, m));
        return flashOne(b, `Could not send: ${(res && res.reason) || "unknown error"}`, true);
      }
      flashOne(b, "Sent to FetchJet ✔");
    }).catch(() => flashOne(b, "Could not reach the extension", true));
  }
  function flashOne(b, text, isError) {
    b.open = true; renderOne(b, text, isError);
    setTimeout(() => { b.open = false; renderOne(b); }, 2500);
  }

  function placeOne(b) {
    const fs = document.fullscreenElement || document.webkitFullscreenElement;
    const r = b.video.getBoundingClientRect();
    const onScreen = r.bottom > 0 && r.top < innerHeight && r.width >= 200 && r.height >= 100 && b.video.isConnected &&
      !(fs && fs.tagName === "VIDEO") && (!fs || fs.contains(b.video) || fs.shadowRoot);
    b.host.style.display = onScreen ? "block" : "none";
    if (!onScreen) return;
    if (b.host.parentNode !== layer()) layer().appendChild(b.host);
    const w = b.open ? 340 : 34; // keep the anchor stable while the label expands on hover
    const h = 34;
    let y = b.dy == null ? r.top + 10 : r.top + b.dy;
    y = Math.max(r.top, Math.min(y, r.bottom - h));
    b.host.style.top = `${Math.max(4, Math.min(y, innerHeight - h - 4))}px`;
    if (b.dx == null) {
      // Anchored to the video's top-right corner: the label grows to the left on hover,
      // so it never spills outside the video.
      b.host.style.left = "auto";
      b.host.style.right = `${Math.max(4, innerWidth - r.right + 10)}px`;
      b.host.style.flexDirection = "row-reverse";
    } else {
      let x = r.left + b.dx;
      x = Math.max(r.left, Math.min(x, r.right - 34));
      b.host.style.right = "auto";
      b.host.style.left = `${Math.max(4, Math.min(x, innerWidth - w))}px`;
    }
  }

  function place() {
    if (!settings.enabled || settings.pagePanel === false) return;
    const vids = allVideos().filter(v => { const r = v.getBoundingClientRect(); return r.width >= 200 && r.height >= 100; });
    for (const [v, b] of bubbles) if (!v.isConnected) { b.host.remove(); bubbles.delete(v); }
    for (const v of vids.slice(0, 12)) {
      let b = bubbles.get(v);
      if (!b) {
        // main video: once the background found something or it plays; others: as soon as they are real players
        const show = isMain(v) ? (items.length || playing(v)) : !(v.paused && !(v.duration > 3) && !directSrc(v));
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
    if (!settings.enabled || settings.pagePanel === false) return;
    place();
    for (const b of bubbles.values()) if (!b.own && !b.loading) renderOne(b);
  }

  // click elsewhere / Escape closes an open menu
  addEventListener("mousedown", (e) => {
    const path = e.composedPath ? e.composedPath() : [];
    for (const b of bubbles.values()) if (b.open && !path.includes(b.host)) { b.open = false; renderOne(b); }
  }, true);
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
      if (area === "local" && changes.settings && changes.settings.newValue) {
        const s = changes.settings.newValue;
        if (s.enabled === false || s.pagePanel === false) removeAll();
      }
    });
  } catch (_) {}
  if (TOP) {
    // media / qualities the background found before this script loaded
    try { api.runtime.sendMessage({ action: "getItems" }).then((r) => { if (r && r.items) update(r.items); }).catch(() => {}); } catch (_) {}
  }

  api.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request && request.action === "fetchjetItems") {
      update(request.items);
      return false;
    }
    if (request && request.action === "openPanel") {
      const v = biggestVideo();
      if (!v) return false;
      let b = bubbles.get(v) || makeBubble(v);
      b.dismissed = false; layer().appendChild(b.host); b.open = true;
      if (!itemsOf(b).length) lookUp(b); else renderOne(b);
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
