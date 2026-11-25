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
const UUID_A = "2f1e4c7a-9b3d-4e21-8a6f-0c5d7e9b1a24";
const UUID_B = "7c0d2e5f-1a4b-4c3d-9e8f-6b2a1c0d9e8f";
const UUID_C = "b4a3c2d1-e0f9-4a8b-8c7d-6e5f4a3b2c1d";
const NAME_A = "How to digitise film stock _ BFI Film on Film Festival - 8-11 June (360p, youtube).mp4";
const NAME_B = "Interview, part 2.mov";
const NAME_C = "Narration.m4a";
const ifs = (kind, uuid, file) => `https://media.canva.com/v2/files/uri:ifs%3A%2F%2F${kind}%2F${uuid}/${file}?csig=AAAA&exp=1&osig=BBBB`;
const A_VIDEO = ifs("V", UUID_A, "video.mp4");
const A_AUDIO = ifs("V", UUID_A, "audio.m4a");
const A_PREVIEW = ifs("V", UUID_A, "preview.mp4");
const B_VIDEO = ifs("V", UUID_B, "video.mp4");
const B_AUDIO = ifs("V", UUID_B, "audio.m4a");
const C_AUDIO = ifs("A", UUID_C, "audio.m4a");

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

async function loadBackground(title, merges) {
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
  chrome.webRequest = { onHeadersReceived: { addListener(fn) { log.headers.push(fn); } }, onBeforeRequest: { addListener() {} } };
  const appFetch = async (u, opts) => {
    const s = String(u);
    if (/\/add$/.test(s) && opts && opts.body) log.adds.push(JSON.parse(opts.body));
    if (/\/links$/.test(s) && opts && opts.body) log.links.push(JSON.parse(opts.body));
    if (/\/inspect/.test(s)) log.inspects++;
    const body = /\/config$/.test(s) ? { pairedMerge: merges } : { ok: true };
    return { ok: true, status: 200, json: async () => body, text: async () => "ok" };
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
  await sandbox.fetchAppConfig();
  const settle = async () => { for (let i = 0; i < 5; i++) { await sandbox.serial(() => {}); await new Promise((r) => setImmediate(r)); } };
  const response = async (url, status, headers) => {
    const responseHeaders = Object.entries(headers).map(([name, value]) => ({ name, value: String(value) }));
    for (const fn of log.headers) fn({ tabId: 7, frameId: 0, url, statusCode: status, responseHeaders, initiator: "https://www.canva.com", type: "xmlhttprequest" });
    await settle();
  };
  const ask = async (request) => {
    const r = await new Promise((resolve) => {
      const x = log.handlers[0](request, { tab }, resolve);
      if (x && typeof x.then === "function") x.then(resolve);
    });
    await settle();
    return r;
  };
  return { sandbox, log, store, tab, response, ask, settle, setClock: (t) => { clock = t; } };
}

const track = (mime, total) => ({ "content-type": mime, "content-range": `bytes 0-65535/${total}`, "content-length": 65536 });

async function preload(bg) {
  await bg.response(A_VIDEO, 206, track("video/mp4", 15414067));
  await bg.response(A_VIDEO, 206, { "content-type": "video/mp4", "content-range": "bytes 65536-4000000/15414067" });
  await bg.response(A_AUDIO, 206, track("audio/mp4", 1258291));
  await bg.response(A_PREVIEW, 200, { "content-type": "video/mp4", "content-length": 900000 });
  await bg.response(`https://media.canva.com/v2/image-resize/format:JPG/height:200/uri:ifs%3A%2F%2FM%2F${UUID_A}/p.jpg?csig=x`, 200, { "content-type": "image/jpeg", "content-length": 40000 });
  await bg.response(ifs("V", UUID_B, "poster.mp4"), 200, { "content-type": "video/mp4", "content-length": 100000 });
  await bg.response(B_VIDEO, 206, track("video/mp4", 3250585));
  await bg.response(B_AUDIO, 206, track("audio/mp4", 1572864));
  await bg.response(C_AUDIO, 206, track("audio/mp4", 2097152));
}

const FIGURES = [
  { name: NAME_A, page: 1, ids: [UUID_A], thumb: `https://media.canva.com/v2/image-resize/format:JPG/uri:ifs%3A%2F%2FM%2F${UUID_A}/p.jpg?csig=x` },
  { name: NAME_B, page: 7, ids: [] },
  { name: NAME_C, page: 2, ids: [UUID_C] }
];

async function backgroundTests() {
  const bg = await loadBackground("الوسيط الرقمي DI - Canva", true);
  await preload(bg);
  let items = (await bg.sandbox.itemsForTab(7)).items;
  check("bg: one row per clip (2 video+audio pairs, 1 lone audio)", items.length === 3, items.map((i) => i.label));
  check("bg: unnamed rows are told apart by number", items.map((i) => i.label).join("|") === "Video 1|Video 2|Audio 1", items.map((i) => i.label));
  check("bg: nothing in the general media list", !bg.store.fetchjet_media_by_tab || !bg.store.fetchjet_media_by_tab[7]);

  await bg.ask({ action: "canvaFigures", figures: FIGURES });
  items = (await bg.sandbox.itemsForTab(7)).items;
  const byUrl = (u) => items.find((i) => i.url === u) || {};
  const a = byUrl(A_VIDEO), b = byUrl(B_VIDEO), c = byUrl(C_AUDIO);
  check("bg: 3 rows after the figures", items.length === 3 && items.every((i) => i.kind === "media" && i.canva && !i.disabled), items.map((i) => i.label));
  check("bg: clip A = its video + its audio (paired by the shared id), not the preview", a.audioUrl === A_AUDIO && a.url === A_VIDEO, a);
  check("bg: clip A named after its figure (id of the poster)", a.fileName === NAME_A && a.label === NAME_A, a.fileName);
  check("bg: clip A size = video + audio", a.size === 15414067 + 1258291, a.size);
  check("bg: clip B paired and named by the one figure left, as .mp4", b.audioUrl === B_AUDIO && b.fileName === "Interview, part 2.mp4", b);
  check("bg: clip B shows its page", /Page 7/.test(b.note || "") && /Page 7/.test(b.sub || ""), b.note);
  check("bg: lone audio stays one audio file", c.url === C_AUDIO && !c.audioUrl && c.fileName === NAME_C && c.ext === "m4a", c);
  check("bg: figure thumbnail travels with the item", a.thumb === FIGURES[0].thumb, a.thumb);
  check("bg: referer is the design page", items.every((i) => i.referer === PAGE));

  const one = await bg.sandbox.downloadItem(a, bg.tab);
  const sent = bg.log.adds[0] || {};
  check("bg: Download one row: one request", one && one.ok && bg.log.adds.length === 1 && bg.log.links.length === 0, bg.log.adds.length);
  check("bg: the request is the pair for the app to merge", sent.url === A_VIDEO && sent.audioUrl === A_AUDIO && sent.fileName === NAME_A && sent.referer === PAGE && sent.direct === true, sent);

  await bg.sandbox.sendDetectedMedia(7);
  const batch = bg.log.links[0] || [];
  check("bg: Download all: 3 requests, pairs keep their audio", batch.length === 3 && batch.filter((x) => x.audioUrl).length === 2, batch.map((x) => [x.fileName, !!x.audioUrl]));

  bg.setClock(50000);
  await bg.ask({ action: "canvaFigures", figures: [], play: { name: NAME_B, page: 7, ids: [], since: 49800, at: 50000 } });
  bg.setClock(50300);
  await bg.response(A_VIDEO, 206, { "content-type": "video/mp4", "content-range": "bytes 4000001-5000000/15414067" });
  await bg.response(B_VIDEO, 206, { "content-type": "video/mp4", "content-range": "bytes 65536-99999/3250585" });
  items = (await bg.sandbox.itemsForTab(7)).items;
  check("bg: two clips fetched after one Play: none marked playing", !items.some((i) => i.playing), items.map((i) => [i.label, i.playing]));

  const report = await bg.ask({ action: "canvaReport", tabId: 7 });
  check("bg: report has the captures without query values", report && report.captures.length >= 9 && report.captures.every((x) => !/csig=AAAA|osig=BBBB/.test(x.url)) && report.captures.some((x) => /\?csig&exp&osig$/.test(x.url)), report && report.captures.slice(0, 2));
  check("bg: report has mime / range / initiator / type / frame", report.captures.some((x) => x.mime === "audio/mp4" && /bytes 0-65535/.test(x.range) && x.initiator === "https://www.canva.com" && x.type === "xmlhttprequest" && x.frameId === 0));
  check("bg: report has the figures with page and stripped thumbnail", report.figures.length === 3 && report.figures[1].page === 7 && report.figures[0].thumb.endsWith("p.jpg?csig"), report.figures);

  await bg.ask({ action: "videoSeen", page: PAGE });
  await bg.sandbox.resolveTab(7, true);
  check("bg: no yt-dlp inspect of the design page", bg.log.inspects === 0, bg.log.inspects);

  const old = await loadBackground("Talk - Canva", false);
  await preload(old);
  const sep = (await old.sandbox.itemsForTab(7)).items;
  check("bg: app without pairedMerge: video and audio as two rows", sep.some((i) => i.url === A_VIDEO && !i.audioUrl) && sep.some((i) => i.url === A_AUDIO), sep.map((i) => i.label));
}

async function playTests() {
  const bg = await loadBackground("Trip video - Canva", true);
  const u = (n) => `https://media.canva.com/v2/files/uri:ifs%3A%2F%2FV%2F${n.replace(".", "_x.")}`;
  bg.setClock(1000);
  for (const n of ["v1.mp4", "a1.m4a", "v2.mp4", "a2.m4a", "v3.mp4"]) await bg.response(u(n), 206, track(/\.m4a$/.test(n) ? "audio/mp4" : "video/mp4", 3000000));
  await bg.ask({ action: "canvaFigures", figures: [{ name: "Beach.mp4", page: 1 }, { name: "Hotel.mp4", page: 2 }, { name: "Dinner.mp4", page: 3 }] });
  let items = (await bg.sandbox.itemsForTab(7)).items;
  check("play: tracks with nothing in common are not paired by guess", items.length === 5 && !items.some((i) => i.audioUrl), items.map((i) => i.label));

  bg.setClock(30000);
  await bg.ask({ action: "canvaFigures", figures: [], play: { name: "Hotel.mp4", page: 2, ids: [], duration: 49.09, since: 29600, at: 30000 } });
  bg.setClock(30400);
  await bg.response(u("v2.mp4"), 206, { "content-type": "video/mp4", "content-range": "bytes 2-500000/3000000" });
  await bg.response(u("a2.m4a"), 206, { "content-type": "audio/mp4", "content-range": "bytes 2-90000/3000000" });
  items = (await bg.sandbox.itemsForTab(7)).items;
  const top = items[0] || {};
  check("play: the video + audio fetched after Play are one clip", top.url === u("v2.mp4") && top.audioUrl === u("a2.m4a") && items.length === 4, items.map((i) => [i.url, i.audioUrl]));
  check("play: that clip gets the figure's name, page and duration", top.fileName === "Hotel.mp4" && /Page 2/.test(top.note) && /0:49/.test(top.note), top);
  check("play: marked playing and listed first", top.playing === true && /Playing now/.test(top.note) && items.filter((i) => i.playing).length === 1, top.note);

  bg.setClock(60000);
  await bg.ask({ action: "canvaFigures", figures: [], play: { name: "Beach.mp4", page: 1, ids: [], since: 59800, at: 60000 } });
  bg.setClock(60300);
  for (const n of ["v1.mp4", "a1.m4a", "v3.mp4"]) await bg.response(u(n), 206, { "content-type": /m4a/.test(n) ? "audio/mp4" : "video/mp4", "content-range": "bytes 2-9/3000000" });
  items = (await bg.sandbox.itemsForTab(7)).items;
  check("play: two clips started in one window: no guess", !items.some((i) => i.fileName === "Beach.mp4") && !items.some((i) => i.playing), items.map((i) => i.fileName));
  check("play: the earlier match is kept", items.some((i) => i.url === u("v2.mp4") && i.fileName === "Hotel.mp4"));

  await bg.ask({ action: "canvaFigures", figures: [], bind: { key: new URL(u("v3.mp4")).hostname + new URL(u("v3.mp4")).pathname, name: "Dinner.mp4", page: 3 } });
  items = (await bg.sandbox.itemsForTab(7)).items;
  check("play: a clip picked from a figure's button keeps that figure's name", items.some((i) => i.url === u("v3.mp4") && i.fileName === "Dinner.mp4"), items.map((i) => i.fileName));
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
  get parentElement() { return this.parentNode && this.parentNode.tagName ? this.parentNode : null; }
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
  const winListeners = {};
  const html = new Node("html");
  const body = new Node("body");
  html.appendChild(body);
  const canvas = new Node("div");
  canvas.setAttribute("role", "application"); canvas.setAttribute("aria-label", "Canvas content");
  body.appendChild(canvas);
  const figures = [];
  const rects = [[100, 100, 300, 460], [600, 100, 800, 460], [1000, 100, 1240, 300]];
  [[NAME_A, 1, UUID_A], [NAME_B, 7, ""], [NAME_C, 2, ""]].forEach(([name, n, id], i) => {
    const page = new Node("div"); page.setAttribute("role", "group"); page.setAttribute("aria-label", `Page ${n}, Talk`);
    const fig = new Node("figure"); fig.setAttribute("aria-label", name); fig.setAttribute("class", "_x1Ab2Cd3Ef4Gh5Ij6Kl");
    const [l, t, r, b] = rects[i];
    fig.rect = { left: l, top: t, right: r, bottom: b, width: r - l, height: b - t };
    const img = new Node("img");
    if (id) img.setAttribute("src", `https://media.canva.com/v2/image-resize/format:JPG/height:200/uri:ifs%3A%2F%2FM%2F${id}/p.jpg?csig=x`);
    const inner = new Node("div");
    fig.appendChild(img); fig.appendChild(inner);
    page.appendChild(fig); canvas.appendChild(page);
    figures.push({ fig, inner });
  });
  const video = new Node("video");
  video.rect = { left: 600, top: 100, right: 800, bottom: 460, width: 200, height: 360 };
  video.paused = false; video.ended = false; video.duration = 49.09; video.currentSrc = "blob:https://www.canva.com/0f0e"; video.src = video.currentSrc;
  body.appendChild(video);
  const document = {
    createElement: (t) => new Node(t), createElementNS: (ns, t) => new Node(t),
    querySelectorAll: (s) => html.querySelectorAll(s), querySelector: (s) => html.querySelector(s),
    getElementById: (id) => html.getElementById(id),
    addEventListener(type, fn) { (docListeners[type] = docListeners[type] || []).push(fn); },
    elementsFromPoint: () => [], documentElement: html, body, title: "Talk - Canva", contentType: "text/html"
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
    URL, Map, Set, WeakMap, WeakSet, Promise, Array, Object, String, Number, RegExp, Math, JSON, Date, Error, isFinite,
    setTimeout, clearTimeout, setInterval: () => 1, clearInterval() {},
    addEventListener(type, fn) { (winListeners[type] = winListeners[type] || []).push(fn); },
    MouseEvent: function () {}, PerformanceObserver: function () { this.observe = () => {}; },
    performance: { getEntriesByType: () => [] },
    fjT: (k) => k, fjIsRtl: () => false, fjOnLanguage() {}, fjReason: (r) => r, fjTheme: () => "dark", fjOnTheme() {},
    getComputedStyle: () => ({}), MutationObserver: function () { this.observe = () => {}; },
    requestAnimationFrame: (fn) => setTimeout(fn, 16)
  };
  context.window.window = context.window; win.top = win;
  context.self = context.window;
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(path.join(__dirname, "..", "content.js"), "utf8"), context, { filename: "content.js" });
  const fireDoc = (type, e) => { for (const fn of docListeners[type] || []) fn(e); };
  const fireWin = (type, e) => { for (const fn of winListeners[type] || []) fn(Object.assign({ type, isTrusted: true }, e)); };
  const hover = (i) => { const { fig, inner } = figures[i]; fireWin("mouseover", { target: inner, composedPath: () => [inner, fig, fig.parentNode, canvas, body, html] }); };
  return { sent, handlers, html, video, figures, canvas, fireDoc, fireWin, hover };
}

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

const REAL = {
  video: "https://media.canva.com/v2/files/uri:ifs%3A%2F%2FV%2FYrub1ASKEXvFPkWzszkkr13eOoRw_S9iWKULLU0jMso.MP4?csig=AAE&exp=1764000000&signer=media-rpc&token=TKN&bandwidth=420000&resolution=360x640",
  audio: "https://media.canva.com/v2/files/uri:ifs%3A%2F%2FV%2Fxf93c-QBFMx0mZKZIgsLbTlyuwymHyilVHHLqLVxMxM.M4A?csig=AAF&exp=1764000000&signer=media-rpc&token=TKN&bandwidth=128000",
  videoTotal: 2837360,
  audioTotal: 1208095,
  t0: 1764000008952,
  junk: [
    ["https://www.google.com.eg/pagead/1p-conversion/1234567890/?random=1&cv=11&fst=1764000000000&label=01a2144e-5b6c-4d7e-8f90-a1b2c3d4e5f6", ""],
    ["https://ct.canva.com/collect?id=01a2144e-5b6c-4d7e-8f90-a1b2c3d4e5f6&ev=video_play", "application/octet-stream"]
  ]
};

async function realReport(bg) {
  const t = REAL.t0;
  const ranges = (total, step, count) => Array.from({ length: count }, (_, i) => [i * step, Math.min(total - 1, (i + 1) * step - 1)]).filter(([a]) => a < total);
  const events = [];
  ranges(REAL.videoTotal, 300000, 10).forEach(([a, b], i) => events.push([t + i * 5000, REAL.video, "video/mp4", a, b, REAL.videoTotal]));
  ranges(REAL.audioTotal, 130000, 10).forEach(([a, b], i) => events.push([t + i * 5000 + 6, REAL.audio, "application/octet-stream", a, b, REAL.audioTotal]));
  events.push([t + 3, REAL.junk[0][0], REAL.junk[0][1], -1]);
  events.push([t + 4, REAL.junk[1][0], REAL.junk[1][1], -1]);
  events.sort((x, y) => x[0] - y[0]);
  for (const [when, url, mime, a, b, total] of events) {
    bg.setClock(when);
    if (a < 0) await bg.response(url, 200, { "content-type": mime, "content-length": 0 });
    else await bg.response(url, 206, { "content-type": mime, "content-range": `bytes ${a}-${b}/${total}`, "content-length": b - a + 1 });
  }
}

async function realReportTests() {
  for (const [label, withPlay] of [["no Play seen, one video figure on the page", false], ["Play reported under the figure", true]]) {
    const bg = await loadBackground("Copy of الوسيط DI - Canva", true);
    if (withPlay) {
      bg.setClock(REAL.t0 - 300);
      await bg.ask({ action: "canvaFigures", figures: [], play: { name: NAME_A, page: 7, ids: [], duration: 49.09, since: REAL.t0 - 400, at: REAL.t0 - 300 } });
    }
    await realReport(bg);
    bg.setClock(REAL.t0 + 60000);
    await bg.ask({ action: "canvaFigures", figures: [{ name: NAME_A, page: 7, ids: [], thumb: "", box: [406, 112, 200, 360], visible: true }] });
    const items = (await bg.sandbox.itemsForTab(7)).items;
    const it = items[0] || {};
    check(`report (${label}): exactly 1 item`, items.length === 1, items.map((i) => [i.label, i.url]));
    check(`report (${label}): video + audio paired by their first requests`, it.url === REAL.video && it.audioUrl === REAL.audio, it);
    check(`report (${label}): total 4045455 bytes`, it.size === 4045455, it.size);
    check(`report (${label}): named after the figure, page 7`, it.fileName === NAME_A && it.page === 7 && /Page 7/.test(it.note || ""), [it.fileName, it.page, it.note]);
    check(`report (${label}): junk requests ignored`, !items.some((i) => /google|ct\.canva/.test(i.url)), items.map((i) => i.url));
    if (withPlay) check("report (Play): marked playing", it.playing === true, it.playing);
  }
  const bg = await loadBackground("x - Canva", true);
  bg.setClock(1000);
  await bg.response(REAL.video, 206, track("video/mp4", REAL.videoTotal));
  bg.setClock(1003);
  await bg.response(ifs("V", "Other4hashOther4hashOther4hash1234567890abc", "x.MP4"), 206, track("video/mp4", 5000000));
  bg.setClock(1006);
  await bg.response(REAL.audio, 206, track("application/octet-stream", REAL.audioTotal));
  const items = (await bg.sandbox.itemsForTab(7)).items;
  check("report: two videos starting with one audio: not paired by guess", items.length === 3 && !items.some((i) => i.audioUrl), items.map((i) => i.label));
}

function makeReportPage(mode, videoRect) {
  const sent = [];
  const handlers = [];
  const intervals = [];
  const html = new Node("html");
  const body = new Node("body");
  html.appendChild(body);
  const canvas = new Node("div");
  canvas.setAttribute("role", "application"); canvas.setAttribute("aria-label", "Canvas content");
  body.appendChild(canvas);
  const page = new Node("div"); page.setAttribute("role", "group"); page.setAttribute("aria-label", "Page 7, Copy of DI");
  const wrap = new Node("div");
  const fig = new Node("div"); fig.setAttribute("role", "figure"); fig.setAttribute("aria-label", NAME_A);
  fig.rect = { left: 406, top: 112, right: 406, bottom: 112, width: 0, height: 0 };
  const poster = new Node("img");
  poster.setAttribute("src", "https://media.canva.com/v2/image-resize/format:JPG/height:640/quality:92/uri:ifs%3A%2F%2FM%2F5e1f0c2a-7b3d-4e9f-a1c2-d3e4f5a6b7c8/p.jpg?osig=Q");
  if (mode === "inside") {
    const visual = new Node("div");
    visual.rect = { left: 406, top: 112, right: 606, bottom: 472, width: 200, height: 360 };
    fig.appendChild(visual);
  }
  wrap.appendChild(fig); wrap.appendChild(poster);
  page.appendChild(wrap); canvas.appendChild(page);
  const host = new Node("div");
  const shadow = new Node("#shadow-root");
  host.shadowRoot = shadow;
  body.appendChild(host);
  const video = new Node("video");
  video.rect = videoRect || { left: 406, top: 112, right: 606, bottom: 472, width: 200, height: 360 };
  video.paused = false; video.ended = false; video.duration = 49.09; video.currentSrc = "blob:https://www.canva.com/9c1d"; video.src = video.currentSrc;
  const document = {
    createElement: (t) => new Node(t), createElementNS: (ns, t) => new Node(t),
    querySelectorAll: (s) => html.querySelectorAll(s), querySelector: (s) => html.querySelector(s),
    getElementById: (id) => html.getElementById(id),
    addEventListener() {},
    elementsFromPoint: () => [], documentElement: html, body, title: "Copy of DI - Canva", contentType: "text/html"
  };
  const win = { top: null, addEventListener() {}, location: { href: PAGE, hostname: "www.canva.com", pathname: "/design/DAGtest1234/AbCdEfGh/edit", ancestorOrigins: [] }, innerWidth: 1280, innerHeight: 800, getSelection: () => null };
  const api = {
    runtime: {
      sendMessage(msg) {
        sent.push(msg);
        if (msg.action === "getSettings") return Promise.resolve({ enabled: true, pagePanel: true });
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
    URL, Map, Set, WeakMap, WeakSet, Promise, Array, Object, String, Number, RegExp, Math, JSON, Date, Error, isFinite,
    setTimeout, clearTimeout, setInterval: (fn) => { intervals.push(fn); return intervals.length; }, clearInterval() {},
    addEventListener() {}, MouseEvent: function () {}, PerformanceObserver: function () { this.observe = () => {}; },
    performance: { getEntriesByType: () => [] },
    fjT: (k) => k, fjIsRtl: () => false, fjOnLanguage() {}, fjReason: (r) => r, fjTheme: () => "dark", fjOnTheme() {},
    getComputedStyle: () => ({}), MutationObserver: function () { this.observe = () => {}; },
    requestAnimationFrame: (fn) => setTimeout(fn, 16)
  };
  context.window.window = context.window; win.top = win;
  context.self = context.window;
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(path.join(__dirname, "..", "content.js"), "utf8"), context, { filename: "content.js" });
  const tick = () => intervals.forEach((fn) => fn());
  return { sent, handlers, html, video, shadow, fig, tick };
}

async function clearKeepsNameTests() {
  const bg = await loadBackground("Copy of DI - Canva", true);
  bg.setClock(REAL.t0 - 300);
  await bg.ask({ action: "canvaFigures", figures: [{ name: NAME_A, page: 7, ids: [], visible: true, box: [406, 112, 0, 0] }], play: { name: NAME_A, page: 7, ids: [], duration: 49.09, since: REAL.t0 - 400, at: REAL.t0 - 300 } });
  await realReport(bg);
  let it = ((await bg.sandbox.itemsForTab(7)).items)[0] || {};
  check("clear: named before Clear", it.fileName === NAME_A && it.audioUrl === REAL.audio, it.fileName);
  await bg.ask({ action: "clearDetectedMedia", tabId: 7 });
  check("clear: the list is empty after Clear", (await bg.sandbox.itemsForTab(7)).items.length === 0);
  bg.setClock(REAL.t0 + 70000);
  await bg.response(REAL.video, 206, { "content-type": "video/mp4", "content-range": "bytes 1500000-1799999/2837360" });
  bg.setClock(REAL.t0 + 73000);
  await bg.response(REAL.audio, 206, { "content-type": "application/octet-stream", "content-range": "bytes 650000-779999/1208095" });
  const items = (await bg.sandbox.itemsForTab(7)).items;
  it = items[0] || {};
  check("clear: captured again, still one paired item", items.length === 1 && it.url === REAL.video && it.audioUrl === REAL.audio, items.map((i) => [i.label, i.audioUrl]));
  check("clear: captured again, keeps the figure name and page", it.fileName === NAME_A && it.page === 7 && /Page 7/.test(it.note || ""), [it.fileName, it.page, it.note]);
  check("clear: and the full size", it.size === 4045455, it.size);
}

async function playingVideoBoxTests() {
  const p = makeReportPage("video-only", { left: 310, top: 114, right: 596, bottom: 622, width: 286, height: 508 });
  await wait(20);
  p.shadow.appendChild(p.video);
  for (let i = 0; i < 6; i++) p.tick();
  await wait(20);
  p.handlers[0]({ action: "fetchjetItems", items: [{ kind: "media", canva: true, clipKey: "k", label: NAME_A, fileName: NAME_A, url: REAL.video, audioUrl: REAL.audio, size: 4045455, ext: "mp4", figure: NAME_A, page: 7, playing: true, sub: "Playing now · Page 7" }] }, {}, () => {});
  await wait(40);
  let bs = p.html.find((n) => n.localName === "fetchjet-button" && n.parentNode);
  const b = bs[0];
  check("video box: one button, fixed, on top, in the page root", bs.length === 1 && /position:fixed/.test(b.style.cssText) && /2147483647/.test(b.style.cssText) && b.parentNode === p.html, bs.length && [b.style.cssText, b.parentNode && b.parentNode.localName]);
  check("video box: placed by the playing video's rect (286x508 at 310,114)", !!b && b.style.display === "block" && b.style.top === "124px" && b.style.right === `${1280 - 596 + 10}px`, b && [b.style.top, b.style.right]);
  const fresh = new Node("div"); fresh.setAttribute("role", "figure"); fresh.setAttribute("aria-label", NAME_A);
  fresh.rect = { left: 406, top: 112, right: 406, bottom: 112, width: 0, height: 0 };
  const parent = p.fig.parentNode;
  p.fig.remove(); parent.appendChild(fresh);
  p.video.rect = { left: 310, top: 214, right: 596, bottom: 722, width: 286, height: 508 };
  p.tick();
  await wait(60);
  bs = p.html.find((n) => n.localName === "fetchjet-button" && n.parentNode);
  check("video box: figure re-rendered and the video moved: the button follows", bs.length === 1 && bs[0].style.display === "block" && bs[0].style.top === "224px", bs.map((x) => [x.style.display, x.style.top]));
  let debug = null;
  p.handlers[0]({ action: "canvaDebug" }, {}, (r) => { debug = r; });
  check("video box: debug lists the videos with rect / paused / src", !!debug && debug.videos.some((v) => v.rect.join() === "310,214,286,508" && v.paused === false && /^blob:/.test(v.src)), debug && debug.videos);
  check("video box: debug tells which box was used and what was tried", !!debug && debug.figures[0].from === "video" && debug.figures[0].tried.length > 0 && debug.buttons.length === 1, debug && debug.figures);
}

async function reportContentTests() {
  for (const mode of ["inside", "video-only"]) {
    const p = makeReportPage(mode);
    await wait(20);
    p.shadow.appendChild(p.video);
    for (let i = 0; i < 6; i++) p.tick();
    await wait(20);
    const reports = p.sent.filter((m) => m.action === "canvaFigures");
    const play = (reports.find((m) => m.play) || {}).play;
    check(`page (${mode}): Play seen without a document event (video in a shadow root)`, !!play && play.name === NAME_A && play.page === 7 && play.duration === 49.09, play);
    const figure = ((reports.find((m) => m.play) || { figures: [] }).figures[0]) || {};
    check(`page (${mode}): the 0x0 figure gets a real box`, !!figure.box && figure.box[2] > 0 && figure.box[3] > 0, figure.box);
    check(`page (${mode}): the poster next to the figure gives the thumbnail and id`, /image-resize/.test(figure.thumb || "") && (figure.ids || []).includes("5e1f0c2a-7b3d-4e9f-a1c2-d3e4f5a6b7c8"), [figure.thumb, figure.ids]);
    p.handlers[0]({ action: "fetchjetItems", items: [{ kind: "media", canva: true, clipKey: "k", label: NAME_A, fileName: NAME_A, url: REAL.video, audioUrl: REAL.audio, size: 4045455, ext: "mp4", figure: NAME_A, page: 7, playing: true, sub: "Playing now · Page 7" }] }, {}, () => {});
    await wait(20);
    const bs = p.html.find((n) => n.localName === "fetchjet-button" && n.parentNode);
    check(`page (${mode}): button shown on the playing figure, inside its box`, bs.length === 1 && bs[0].style.display === "block" && parseInt(bs[0].style.top, 10) >= 112, bs.map((b) => [b.style.display, b.style.top, b.style.right]));
  }
}

async function contentTests() {
  const p = makeCanvaPage();
  await wait(20);
  const buttons = () => p.html.find((n) => n.localName === "fetchjet-button" && n.parentNode);
  const items = [
    { kind: "media", canva: true, clipKey: "k/a", label: NAME_A, fileName: NAME_A, url: A_VIDEO, audioUrl: A_AUDIO, size: 16672358, ext: "mp4", figure: NAME_A, page: 1, sub: "Page 1 · 15.9 MB" },
    { kind: "media", canva: true, clipKey: "k/b", label: "Video 2", fileName: "Talk - 2.mp4", url: B_VIDEO, audioUrl: B_AUDIO, size: 4823449, ext: "mp4", figure: "", page: 0, sub: "4.6 MB" },
    { kind: "media", canva: true, clipKey: "k/c", label: NAME_C, fileName: NAME_C, url: C_AUDIO, size: 2097152, ext: "m4a", figure: NAME_C, page: 2, sub: "Page 2 · 2 MB" }
  ];
  p.handlers[0]({ action: "fetchjetItems", items }, {}, () => {});
  p.handlers[0]({ action: "openPanel" }, {}, () => {});
  await wait(20);
  check("content: no button on the canvas <video> or on idle figures", buttons().length === 0, buttons().length);

  p.hover(0);
  await wait(20);
  let bs = buttons();
  check("content: hovering a video figure shows one button", bs.length === 1, bs.length);
  if (bs.length) {
    const root = bs[0].shadow;
    check("content: placed on the figure's box", parseInt(bs[0].style.top, 10) >= 100 && bs[0].style.right === `${1280 - 300 + 10}px`, [bs[0].style.top, bs[0].style.right]);
    check("content: same Open-in-app arrow as every site", !!root.getElementById("go"));
    const rows = root.find((n) => n.classList.contains("item") && !n.classList.contains("foot"));
    check("content: figure A's button lists only clip A (plus the usual open-in-app row)", rows.length === 2 && rows[0].textContent.includes(NAME_A) && rows[1].textContent.includes("itemOpenApp"), rows.map((r) => r.textContent));
    p.sent.length = 0;
    if (rows[0]) rows[0].fire("click");
    await wait(20);
    const dl = p.sent.filter((m) => m.action === "downloadItem");
    check("content: clicking it sends clip A with its audio", dl.length === 1 && dl[0].item.url === A_VIDEO && dl[0].item.audioUrl === A_AUDIO && dl[0].item.fileName === NAME_A, dl);
  }

  await wait(2600);
  p.hover(1);
  await wait(20);
  bs = buttons();
  check("content: moving to figure B moves the button", bs.length === 1, bs.length);
  if (bs.length) {
    const rows = bs[0].shadow.find((n) => n.classList.contains("item") && !n.classList.contains("foot"));
    check("content: B (no match yet) offers the clips without a figure (plus the usual open-in-app row)", rows.length === 2 && rows[0].textContent.includes("Video 2") && rows[1].textContent.includes("itemOpenApp"), rows.map((r) => r.textContent));
    p.sent.length = 0;
    if (rows[0]) rows[0].fire("click");
    await wait(20);
    const dl = p.sent.find((m) => m.action === "downloadItem");
    const bind = p.sent.find((m) => m.action === "canvaFigures" && m.bind);
    check("content: picked from B's button, it is saved with B's name", !!dl && dl.item.fileName === "Interview, part 2.mp4" && dl.item.url === B_VIDEO, dl);
    check("content: and the pick is remembered", !!bind && bind.bind.key === "k/b" && bind.bind.name === NAME_B && bind.bind.page === 7, bind);
  }

  p.sent.length = 0;
  p.fireDoc("play", { target: p.video, type: "play" });
  await wait(400);
  const withPlay = p.sent.find((m) => m.action === "canvaFigures" && m.play);
  check("content: Play names the figure under the video, with page and duration", !!withPlay && withPlay.play.name === NAME_B && withPlay.play.page === 7 && withPlay.play.duration === 49.09, withPlay && withPlay.play);
  const figs = withPlay ? withPlay.figures : [];
  check("content: figures carry name, page, poster id, thumbnail, box", figs.length === 3 && figs.map((f) => f.page).join() === "1,7,2" && figs[0].ids.includes(UUID_A) && /image-resize/.test(figs[0].thumb) && figs[0].box.join() === "100,100,200,360", figs.map((f) => [f.name, f.page, f.ids]));
  check("content: hashed class names are not taken as ids", !figs.some((f) => f.ids.includes("_x1Ab2Cd3Ef4Gh5Ij6Kl")));
  check("content: no yt-dlp lookup asked for the design", !p.sent.some((m) => m.action === "videoSeen" || m.action === "resolveVideo"), p.sent.map((m) => m.action));
}

(async () => {
  await backgroundTests();
  await playTests();
  await contentTests();
  await realReportTests();
  await reportContentTests();
  await clearKeepsNameTests();
  await playingVideoBoxTests();
  console.log(failed ? failed + " FAILED" : "all ok");
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.log("  FAIL  harness error: " + (e && e.stack || e)); process.exit(1); });
