"use strict";
const fs = require("fs");
const path = require("path");
const vm = require("vm");

let failed = 0;
function check(name, ok, detail) {
  if (!ok) failed++;
  console.log((ok ? "  ok    " : "  FAIL  ") + name + (ok || detail === undefined ? "" : " : " + JSON.stringify(detail)));
}

class Node {
  constructor(tag) {
    this.tagName = String(tag).toUpperCase();
    this.localName = String(tag).toLowerCase();
    this.attrs = {}; this.children = []; this.parentNode = null; this.listeners = {}; this.style = {}; this._text = "";
    this.isConnected = true; this.hidden = false; this.shadow = null;
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
    if (sel[0] === ".") return this.classList.contains(sel.slice(1));
    if (sel[0] === "#") return this.attrs.id === sel.slice(1);
    return this.localName === sel.toLowerCase();
  }
  querySelectorAll(sel) { return /^[.#]?[\w-]+$/.test(sel) ? this.find((n) => n.matches(sel)) : []; }
  querySelector(sel) { return this.querySelectorAll(sel)[0] || null; }
  contains(n) { return n === this || this.find((c) => c === n).length > 0; }
  closest() { return null; }
  getBoundingClientRect() { return this.rect || { left: 0, top: 0, right: 0, bottom: 0, width: 0, height: 0 }; }
}

function makePage(userAgent, reply) {
  const sent = [];
  const handlers = [];
  const video = new Node("video");
  video.rect = { left: 100, top: 100, right: 740, bottom: 460, width: 640, height: 360 };
  video.paused = false; video.ended = false; video.duration = 600; video.currentSrc = "blob:x"; video.src = "";
  const html = new Node("html");
  const document = {
    createElement: (t) => new Node(t), createElementNS: (ns, t) => new Node(t),
    querySelectorAll: (s) => (s === "video" ? [video] : []), querySelector: () => null,
    addEventListener() {}, elementsFromPoint: () => [], documentElement: html, body: html, title: "Lecture", contentType: "text/html"
  };
  const win = { top: null, addEventListener() {}, location: { href: "https://www.udemy.com/course/x/learn/lecture/1", hostname: "www.udemy.com", ancestorOrigins: [] }, innerWidth: 1280, innerHeight: 800, getSelection: () => null };
  const api = {
    runtime: {
      sendMessage(msg) {
        sent.push(msg);
        if (msg.action === "getSettings") return Promise.resolve({ enabled: true });
        if (msg.action === "resolveVideo") return Promise.resolve(reply);
        if (msg.action === "openPageDialog") return Promise.resolve({ ok: true });
        return Promise.resolve({});
      },
      onMessage: { addListener(fn) { handlers.push(fn); } }
    },
    storage: { onChanged: { addListener() {} }, local: { get: () => Promise.resolve({}), set: () => Promise.resolve() } }
  };
  const context = {
    window: win, document, chrome: api, navigator: { userAgent, maxTouchPoints: 0 },
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
  return { sent, handlers, html, video };
}

const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const ANDROID = "Mozilla/5.0 (Linux; Android 14) Chrome/130 Mobile";
const IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Safari/605.1";

async function scenario(ua, reply) {
  const page = makePage(ua, reply);
  await wait(20);
  page.handlers[0]({ action: "openPanel" }, {}, () => {});
  await wait(60);
  const host = page.html.children.find((c) => c.localName === "fetchjet-button");
  return { page, host, root: host && host.shadow };
}

const rowsOf = (root) => root.find((n) => n.attrs.class && n.attrs.class.split(" ").includes("item"));

(async () => {
  const stream = { items: [{ kind: "media", label: "Video stream", sub: "https://v.example/x.m3u8", url: "https://v.example/x.m3u8", stream: true }] };
  const lecture = "https://www.udemy.com/course/x/learn/lecture/1";
  for (const [label, reply] of [["captured stream", stream], ["empty list", { items: [] }], ["error", { error: "boom" }]]) {
    const s = await scenario(ANDROID, reply);
    check(label + ": button exists", !!s.root);
    if (!s.root) continue;
    const go = s.root.getElementById("go");
    const main = s.root.getElementById("main");
    check(label + ": arrow is next to the pill, inside the button", !!go && main.contains(go));
    check(label + ": arrow has a tooltip, is focusable, is a button", !!go && go.title === "openInApp" && go.tabIndex === 0 && go.attrs.role === "button");
    const rows = rowsOf(s.root);
    check(label + ": list has the open-in-FetchJet row", rows.some((r) => r.textContent.includes("itemOpenApp")), rows.map((r) => r.textContent));
    if (label === "captured stream") check("captured stream: its row has its own arrow", s.root.find((n) => n.attrs.class === "cut").length === 1);
    s.page.sent.length = 0;
    go.fire("click");
    await wait(20);
    const m = s.page.sent.find((x) => x.action === "openPageDialog");
    check(label + ": arrow click sends openPageDialog with the page link", !!m && m.page === lecture, s.page.sent);
    check(label + ": nothing else is sent (no download)", s.page.sent.every((x) => x.action === "openPageDialog"), s.page.sent);
    s.page.sent.length = 0;
    go.fire("keydown", { key: "Enter" });
    await wait(20);
    check(label + ": Enter on the arrow sends it", s.page.sent.some((x) => x.action === "openPageDialog"));
    s.page.sent.length = 0;
    go.fire("keydown", { key: " " });
    await wait(20);
    check(label + ": Space on the arrow sends it", s.page.sent.some((x) => x.action === "openPageDialog"));
    s.page.sent.length = 0;
    await wait(2700);
    const row = rowsOf(s.root).find((r) => r.textContent.includes("itemOpenApp"));
    if (row) { row.fire("click"); await wait(20); }
    check(label + ": list row sends openPageDialog", s.page.sent.some((x) => x.action === "openPageDialog"), s.page.sent);
    if (label === "captured stream") {
      await wait(2700);
      s.page.sent.length = 0;
      const cut = s.root.find((n) => n.attrs.class === "cut")[0];
      if (cut) { cut.fire("click"); await wait(20); }
      check("captured stream: row arrow sends openPageDialog, not a download", s.page.sent.length === 1 && s.page.sent[0].action === "openPageDialog", s.page.sent);
    }
  }
  const ios = await scenario(IPHONE, stream);
  check("iOS: button exists", !!ios.root);
  if (ios.root) {
    check("iOS: no second arrow", !ios.root.getElementById("go"));
    const iosRow = rowsOf(ios.root).find((r) => r.textContent.includes("openPageInApp"));
    ios.page.sent.length = 0;
    if (iosRow) { iosRow.fire("click"); await wait(20); }
    check("iOS: list row still sends openPageInApp", ios.page.sent.some((x) => x.action === "openPageInApp"), ios.page.sent);
  }
  await require("./arrow.bg.js").run(check);
  console.log(failed ? failed + " FAILED" : "all ok");
  process.exit(failed ? 1 : 0);
})();
