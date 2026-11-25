"use strict";
const fs = require("fs");
const path = require("path");
const vm = require("vm");

let failed = 0;
function check(name, ok, detail) {
  if (!ok) failed++;
  console.log((ok ? "  ok    " : "  FAIL  ") + name + (ok || detail === undefined ? "" : " : " + JSON.stringify(detail)));
}

const PAGE = "https://www.canva.com/design/DAGtest1234/AbCdEfGh/edit";
const NAME_A = "How to digitise film stock _ BFI Film on Film Festival - 8-11 June (360p, youtube).mp4";
const NAME_B = "Interview, part 2.mp4";
const NAME_C = "Intro clip.mov";
const URL_A = "https://media.canva.com/VAFaaaaaaaa/v/1a2b3c4d.mp4?Expires=1&Signature=s1&Key-Pair-Id=k";
const URL_A_PREVIEW = "https://media.canva.com/VAFaaaaaaaa/preview/low.mp4?Expires=1&Signature=s0";
const URL_B = "https://media.canva.com/VAFbbbbbbbb/v/9f8e7d6c.mp4?Expires=1&Signature=s2";
const URL_C = "https://video-cdn.canva-media.example/u/abc/clip.webm?t=1";

function deep() {
  const fn = function () {};
  return new Proxy(fn, {
    get(_, key) {
      if (key === "then") return undefined;
      if (Object.prototype.hasOwnProperty.call(_, key)) return _[key];
      if (key === Symbol.toPrimitive) return () => "";
      return deep();
    },
    apply() { return Promise.resolve(undefined); },
  });
}

function loadBackground(title) {
  const log = { adds: [], links: [], inspects: 0, headers: [], handlers: [] };
  const store = {};
  let clock = 1000;
  const chrome = deep();
  const session = {
    get: async (k) => (k in store ? { [k]: JSON.parse(JSON.stringify(store[k])) } : {}),
    set: async (o) => { for (const [k, v] of Object.entries(o)) store[k] = JSON.parse(JSON.stringify(v)); },
    remove: async () => {}
  };
  chrome.storage = { local: { get: async () => ({ settings: { notifications: false } }), set: async () => {} }, session, onChanged: { addListener() {} } };
  chrome.permissions = { contains: async () => true };
  chrome.runtime = { getManifest: () => ({ version: "9.9.9" }), onMessage: { addListener(fn) { log.handlers.push(fn); } }, onInstalled: { addListener() {} }, onStartup: { addListener() {} }, lastError: null, getURL: (p) => p };
  const tab = { id: 7, url: PAGE, title };
  chrome.tabs = {
    get: async (id) => (id === 7 ? tab : null),
    create: async () => ({ id: 1 }), update: async () => ({}), remove: async () => {}, query: async () => [tab],
    sendMessage: async () => ({}),
    onUpdated: { addListener() {} }, onRemoved: { addListener() {} }, onActivated: { addListener() {} }, onCreated: { addListener() {} }
  };
  chrome.webRequest = {
    onHeadersReceived: { addListener(fn) { log.headers.push(fn); } },
    onBeforeRequest: { addListener() {} }
  };
  const appFetch = async (u, opts) => {
    const s = String(u);
    if (/\/add$/.test(s) && opts && opts.body) log.adds.push(JSON.parse(opts.body));
    if (/\/links$/.test(s) && opts && opts.body) log.links.push(JSON.parse(opts.body));
    if (/\/inspect/.test(s)) log.inspects++;
    return { ok: true, status: 200, json: async () => ({ ok: true }), text: async () => "ok" };
  };
  class FakeDate extends Date { static now() { return clock; } }
  const sandbox = { chrome, console, URL, URLSearchParams, Promise, setTimeout, clearTimeout, Date: FakeDate, Set, Map, JSON, Math, fetch: appFetch, self: {}, navigator: { userAgent: "Mozilla/5.0 Windows" }, AbortController, TextEncoder, TextDecoder };
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  const root = path.join(__dirname, "..");
  for (const f of ["i18n/i18n.js", "i18n/en.js", "yt-core.js"]) {
    try { vm.runInContext(fs.readFileSync(path.join(root, f), "utf8"), sandbox, { filename: f }); } catch (_) {}
  }
  vm.runInContext(fs.readFileSync(path.join(root, "background.js"), "utf8"), sandbox, { filename: "background.js" });
  const settle = async () => { for (let i = 0; i < 5; i++) { await sandbox.serial(() => {}); await new Promise((r) => setImmediate(r)); } };
  const response = async (url, status, headers, extra) => {
    const responseHeaders = Object.entries(headers).map(([name, value]) => ({ name, value: String(value) }));
    for (const fn of log.headers) fn({ tabId: 7, url, statusCode: status, responseHeaders, initiator: "https://www.canva.com", type: "xmlhttprequest", ...(extra || {}) });
    await settle();
  };
  const ask = (request) => new Promise((resolve) => {
    const r = log.handlers[0](request, { tab }, resolve);
    if (r && typeof r.then === "function") r.then(resolve);
  });
  return { sandbox, log, store, tab, response, ask, settle, setClock: (t) => { clock = t; } };
}

async function preload(bg) {
  await bg.response(URL_B, 206, { "content-type": "video/mp4", "content-range": "bytes 0-65535/20971520", "content-length": 65536 });
  await bg.response(URL_B, 206, { "content-type": "video/mp4", "content-range": "bytes 65536-1048575/20971520", "content-length": 983040 });
  await bg.response(URL_A_PREVIEW, 200, { "content-type": "video/mp4", "content-length": 900000 });
  await bg.response("https://media.canva.com/v2/image-resize/VAFaaaaaaaa/thumb.jpg", 200, { "content-type": "image/jpeg", "content-length": 40000 });
  await bg.response("https://media.canva.com/VAFdddddddd/v/poster.mp4", 200, { "content-type": "video/mp4", "content-length": 100000 });
  await bg.response(URL_A, 206, { "content-type": "video/mp4", "content-range": "bytes 0-1/8000000", "content-length": 2 });
  await bg.response(URL_A, 206, { "content-type": "video/mp4", "content-range": "bytes 2-4000000/8000000", "content-length": 3999999 });
  await bg.response(URL_C, 206, { "content-type": "video/webm", "content-range": "bytes 0-99/5242880", "content-length": 100 });
}

async function backgroundTests() {
  const bg = loadBackground("BFI talk - Canva");
  await preload(bg);
  let r = await bg.sandbox.itemsForTab(7);
  check("bg: 3 files before the figures are known", r.items.length === 3, r.items.map((i) => i.label));
  check("bg: unnamed files get the design title + number", r.items.map((i) => i.fileName).join("|") === "BFI talk - 1.mp4|BFI talk - 2.mp4|BFI talk - 3.webm", r.items.map((i) => i.fileName));
  check("bg: nothing in the general media list", !bg.store.fetchjet_media_by_tab || !bg.store.fetchjet_media_by_tab[7]);

  await bg.ask({ action: "canvaFigures", figures: [{ name: NAME_A, ids: ["VAFaaaaaaaa"] }, { name: NAME_B, ids: ["VAFbbbbbbbb"] }, { name: NAME_C, ids: [] }] });
  await bg.settle();
  r = await bg.sandbox.itemsForTab(7);
  const items = r.items;
  check("bg: 3 single files", items.length === 3 && items.every((i) => i.kind === "media" && i.canva && !i.stream && !i.disabled), items);
  const byUrl = (u) => items.find((i) => i.url === u) || {};
  check("bg: B named by its media id", byUrl(URL_B).fileName === NAME_B, byUrl(URL_B));
  check("bg: A named by its media id, main file (not the preview)", byUrl(URL_A).fileName === NAME_A, items.map((i) => [i.url, i.fileName]));
  check("bg: C (no id) gets the one name left, with the served type", byUrl(URL_C).fileName === "Intro clip.webm", byUrl(URL_C));
  check("bg: no preview / thumbnail / poster item", !items.some((i) => /preview|thumb|poster/.test(i.url)), items.map((i) => i.url));
  check("bg: sizes are the whole files", byUrl(URL_A).size === 8000000 && byUrl(URL_B).size === 20971520 && byUrl(URL_C).size === 5242880, items.map((i) => i.size));
  check("bg: referer is the design page", items.every((i) => i.referer === PAGE));

  const one = await bg.sandbox.downloadItem(byUrl(URL_A), bg.tab);
  check("bg: Download one item: ok", one && one.ok === true, one);
  check("bg: Download one item: exactly one file posted", bg.log.adds.length === 1 && bg.log.links.length === 0, { adds: bg.log.adds.length, links: bg.log.links.length });
  const sent = bg.log.adds[0] || {};
  check("bg: posted file is A, with its name, page referer, started directly", sent.url === URL_A && sent.fileName === NAME_A && sent.referer === PAGE && sent.direct === true, sent);

  const all = await bg.sandbox.sendDetectedMedia(7);
  const batch = bg.log.links[0] || [];
  check("bg: Download all sends the 3 files with names", all && all.ok && batch.length === 3 && batch.every((b) => b.fileName && b.referer === PAGE), batch.map((b) => b.fileName));

  await bg.ask({ action: "videoSeen", page: PAGE });
  await bg.sandbox.resolveTab(7, true);
  check("bg: no yt-dlp inspect of the design page", bg.log.inspects === 0, bg.log.inspects);
}

async function playTests() {
  const bg = loadBackground("Trip video - Canva");
  const idless = ["https://cdn.canva-media.example/a/one.mp4", "https://cdn.canva-media.example/a/two.mp4", "https://cdn.canva-media.example/a/three.mp4"];
  bg.setClock(1000);
  for (const u of idless) await bg.response(u, 206, { "content-type": "video/mp4", "content-range": "bytes 0-1/3000000" });
  await bg.ask({ action: "canvaFigures", figures: [{ name: "Beach.mp4", ids: [] }, { name: "Hotel.mp4", ids: [] }, { name: "Dinner.mp4", ids: [] }] });
  let items = (await bg.sandbox.itemsForTab(7)).items;
  check("play: preloaded files without ids keep neutral names", items.map((i) => i.fileName).join("|") === "Trip video - 1.mp4|Trip video - 2.mp4|Trip video - 3.mp4", items.map((i) => i.fileName));

  bg.setClock(30000);
  await bg.ask({ action: "canvaFigures", figures: [], play: { name: "Hotel.mp4", ids: [], since: 29600, at: 30000 } });
  bg.setClock(30400);
  await bg.response(idless[1], 206, { "content-type": "video/mp4", "content-range": "bytes 2-500000/3000000" });
  items = (await bg.sandbox.itemsForTab(7)).items;
  check("play: the file fetched right after Play gets the figure's name", items[0].url === idless[1] && items[0].fileName === "Hotel.mp4", items.map((i) => [i.url, i.fileName]));
  check("play: it is marked playing and listed first", items[0].playing === true && items[0].note === "Playing now" && items.filter((i) => i.playing).length === 1, items[0]);

  bg.setClock(60000);
  await bg.ask({ action: "canvaFigures", figures: [], play: { name: "Beach.mp4", ids: [], since: 59800, at: 60000 } });
  bg.setClock(60300);
  await bg.response(idless[0], 206, { "content-type": "video/mp4", "content-range": "bytes 2-9/3000000" });
  await bg.response(idless[2], 206, { "content-type": "video/mp4", "content-range": "bytes 2-9/3000000" });
  items = (await bg.sandbox.itemsForTab(7)).items;
  const named = items.filter((i) => i.fileName === "Beach.mp4");
  check("play: two files fetched after Play: no guess", named.length === 0, items.map((i) => i.fileName));
  check("play: nothing marked playing when the last Play is not known", !items.some((i) => i.playing), items.map((i) => i.playing));
  check("play: the earlier match is kept", items.some((i) => i.url === idless[1] && i.fileName === "Hotel.mp4"));
}

class Node {
  constructor(tag) {
    this.tagName = String(tag).toUpperCase();
    this.localName = String(tag).toLowerCase();
    this.attrs = {}; this.children = []; this.parentNode = null; this.listeners = {}; this.style = {}; this._text = "";
    this.isConnected = true; this.shadow = null;
    const self = this;
    this.classList = {
      toggle(c, on) { const l = (self.attrs.class || "").split(/\s+/).filter(Boolean).filter((x) => x !== c); if (on) l.push(c); self.attrs.class = l.join(" "); },
      add(c) { this.toggle(c, true); },
      remove(c) { this.toggle(c, false); },
      contains(c) { return (self.attrs.class || "").split(/\s+/).includes(c); }
    };
  }
  get parentElement() { return this.parentNode; }
  get nextElementSibling() { return null; }
  get attributes() { return Object.entries(this.attrs).map(([name, value]) => ({ name, value })); }
  set textContent(v) { this._text = String(v); this.children = []; }
  get textContent() { return this._text + this.children.map((c) => c.textContent).join(""); }
  get title() { return this.attrs.title || ""; }
  set title(v) { this.attrs.title = String(v); }
  setAttribute(k, v) { this.attrs[k] = String(v); }
  getAttribute(k) { return k in this.attrs ? this.attrs[k] : null; }
  hasAttribute(k) { return k in this.attrs; }
  append(...kids) { for (const k of kids) this.appendChild(typeof k === "string" ? Object.assign(new Node("#text"), { _text: k }) : k); }
  appendChild(k) { if (k.parentNode) k.remove(); k.parentNode = this; this.children.push(k); return k; }
  replaceChildren(...kids) { this.children = []; this.append(...kids); }
  remove() { if (this.parentNode) this.parentNode.children = this.parentNode.children.filter((c) => c !== this); this.parentNode = null; }
  addEventListener(type, fn) { (this.listeners[type] = this.listeners[type] || []).push(fn); }
  fire(type, extra) {
    const e = Object.assign({ type, isTrusted: true, key: "", preventDefault() {}, stopPropagation() {}, stopImmediatePropagation() {} }, extra);
    for (const fn of this.listeners[type] || []) fn(e);
  }
  attachShadow() { this.shadow = new Node("#shadow"); return this.shadow; }
  find(pred, out = []) { for (const c of this.children) { if (pred(c)) out.push(c); c.find(pred, out); } return out; }
  getElementById(id) { return this.find((n) => n.attrs.id === id)[0] || null; }
  matches(sel) {
    if (sel === "*") return this.localName[0] !== "#";
    if (sel === 'figure, [role="figure"]') return this.localName === "figure" || this.attrs.role === "figure";
    if (sel[0] === ".") return this.classList.contains(sel.slice(1));
    if (sel[0] === "#") return this.attrs.id === sel.slice(1);
    return this.localName === sel.toLowerCase();
  }
  querySelectorAll(sel) {
    if (/:not\(/.test(sel)) return this.find((n) => n.classList.contains("item") && !n.classList.contains("off"));
    return this.find((n) => n.matches(sel));
  }
  querySelector(sel) { return this.querySelectorAll(sel)[0] || null; }
  contains(n) { return n === this || this.find((c) => c === n).length > 0; }
  closest() { return null; }
  click() { this.fire("click"); }
  getBoundingClientRect() { return this.rect || { left: 0, top: 0, right: 0, bottom: 0, width: 0, height: 0 }; }
}

function makeCanvaPage() {
  const sent = [];
  const handlers = [];
  const docListeners = {};
  const html = new Node("html");
  const body = new Node("body");
  html.appendChild(body);
  const canvas = new Node("div");
  canvas.setAttribute("role", "application"); canvas.setAttribute("aria-label", "Canvas content");
  body.appendChild(canvas);
  const figures = [];
  const rects = [[100, 100, 460, 740], [600, 100, 960, 740], [1000, 100, 1240, 400]];
  [[NAME_A, "VAFaaaaaaaa"], [NAME_B, ""], [NAME_C, ""]].forEach(([name, id], i) => {
    const page = new Node("div"); page.setAttribute("role", "group"); page.setAttribute("aria-label", `Page ${i + 1}, BFI talk`);
    const fig = new Node("figure"); fig.setAttribute("aria-label", name);
    const [l, t, r, b] = rects[i];
    fig.rect = { left: l, top: t, right: r, bottom: b, width: r - l, height: b - t };
    const img = new Node("img");
    if (id) img.setAttribute("src", `https://media.canva.com/v2/image-resize/format:JPG/height:200/${id}/thumb.jpg`);
    fig.appendChild(img);
    page.appendChild(fig); canvas.appendChild(page);
    figures.push(fig);
  });
  const video = new Node("video");
  video.rect = { left: 600, top: 100, right: 960, bottom: 740, width: 360, height: 640 };
  video.paused = false; video.ended = false; video.duration = 49.09; video.currentSrc = "blob:https://www.canva.com/0f0e"; video.src = video.currentSrc;
  body.appendChild(video);
  const document = {
    createElement: (t) => new Node(t), createElementNS: (ns, t) => new Node(t),
    querySelectorAll: (s) => html.querySelectorAll(s), querySelector: (s) => html.querySelector(s),
    getElementById: (id) => html.getElementById(id),
    addEventListener(type, fn) { (docListeners[type] = docListeners[type] || []).push(fn); },
    elementsFromPoint: () => [], documentElement: html, body, title: "BFI talk - Canva", contentType: "text/html"
  };
  const win = { top: null, addEventListener() {}, location: { href: PAGE, hostname: "www.canva.com", pathname: "/design/DAGtest1234/AbCdEfGh/edit", ancestorOrigins: [] }, innerWidth: 1280, innerHeight: 800, getSelection: () => null };
  const api = {
    runtime: {
      sendMessage(msg) {
        sent.push(msg);
        if (msg.action === "getSettings") return Promise.resolve({ enabled: true, pagePanel: true });
        if (msg.action === "downloadItem") return Promise.resolve({ ok: true });
        if (msg.action === "getItems") return Promise.resolve({ items: [] });
        return Promise.resolve({});
      },
      onMessage: { addListener(fn) { handlers.push(fn); } }
    },
    storage: { onChanged: { addListener() {} }, local: { get: () => Promise.resolve({}), set: () => Promise.resolve() } }
  };
  const context = {
    window: win, document, chrome: api, navigator: { userAgent: "Mozilla/5.0 Windows", maxTouchPoints: 0 },
    location: win.location, innerWidth: 1280, innerHeight: 800, console,
    URL, Map, Set, Promise, Array, Object, String, Number, RegExp, Math, JSON, Date, Error,
    setTimeout, clearTimeout, setInterval: () => 1, clearInterval() {},
    addEventListener() {}, MouseEvent: function () {}, PerformanceObserver: function () { this.observe = () => {}; },
    performance: { getEntriesByType: () => [] },
    fjT: (k) => k, fjIsRtl: () => false, fjOnLanguage() {}, fjReason: (r) => r, fjTheme: () => "dark", fjOnTheme() {},
    getComputedStyle: () => ({}), MutationObserver: function () { this.observe = () => {}; },
    requestAnimationFrame: (fn) => fn()
  };
  context.window.window = context.window; win.top = win;
  context.self = context.window;
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(path.join(__dirname, "..", "content.js"), "utf8"), context, { filename: "content.js" });
  const fireDoc = (type, e) => { for (const fn of docListeners[type] || []) fn(e); };
  return { sent, handlers, html, video, figures, fireDoc };
}

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

async function contentTests() {
  const p = makeCanvaPage();
  await wait(20);
  const buttons = () => p.html.find((n) => n.localName === "fetchjet-button");
  const plain = [NAME_A, NAME_B, "Intro clip.webm"].map((n, i) => ({ kind: "media", canva: true, label: n, fileName: n, url: `https://media.canva.com/x${i}.mp4`, size: 1e6, figure: [NAME_A, NAME_B, NAME_C][i] }));
  p.handlers[0]({ action: "fetchjetItems", items: plain }, {}, () => {});
  p.handlers[0]({ action: "openPanel" }, {}, () => {});
  await wait(20);
  check("content: no floating button on the canvas videos", buttons().length === 0, buttons().length);

  p.fireDoc("play", { target: p.video, type: "play" });
  await wait(400);
  const report = p.sent.filter((m) => m.action === "canvaFigures");
  const withPlay = report.find((m) => m.play);
  check("content: figures reported with their file names", report.length > 0 && JSON.stringify(report[0].figures.map((f) => f.name)) === JSON.stringify([NAME_A, NAME_B, NAME_C]), report[0] && report[0].figures);
  check("content: media id read from the figure", report.length > 0 && report[0].figures[0].ids.includes("VAFaaaaaaaa"), report[0] && report[0].figures[0]);
  check("content: Play names the figure under the video", !!withPlay && withPlay.play.name === NAME_B && withPlay.play.duration === 49.09, withPlay && withPlay.play);
  check("content: no yt-dlp lookup asked for the design", !p.sent.some((m) => m.action === "videoSeen" || m.action === "resolveVideo"), p.sent.map((m) => m.action));
  check("content: still no button while the playing file is unknown", buttons().length === 0);

  const playing = { ...plain[1], playing: true, note: "Playing now" };
  p.handlers[0]({ action: "fetchjetItems", items: [playing, plain[0], plain[2]] }, {}, () => {});
  await wait(20);
  const bs = buttons();
  check("content: one button once the playing file is known", bs.length === 1, bs.length);
  if (!bs.length) return;
  const root = bs[0].shadow;
  check("content: no Open-in-app arrow on it", !root.getElementById("go"));
  const rows = root.find((n) => n.classList.contains("item") && !n.classList.contains("foot"));
  check("content: its list holds only the playing file", rows.length === 1 && rows[0].textContent.includes(NAME_B), rows.map((r) => r.textContent));
  p.sent.length = 0;
  if (rows[0]) rows[0].fire("click");
  await wait(20);
  const dl = p.sent.filter((m) => m.action === "downloadItem");
  check("content: clicking it sends that one file", dl.length === 1 && dl[0].item.url === playing.url && dl[0].item.fileName === NAME_B, p.sent);
}

(async () => {
  await backgroundTests();
  await playTests();
  await contentTests();
  console.log(failed ? failed + " FAILED" : "all ok");
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.log("  FAIL  harness error: " + (e && e.stack || e)); process.exit(1); });
