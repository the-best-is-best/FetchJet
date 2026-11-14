"use strict";
const fs = require("fs");
const path = require("path");
const vm = require("vm");

let failed = 0;
function check(name, ok, detail) {
  if (!ok) failed++;
  console.log((ok ? "  ok    " : "  FAIL  ") + name + (ok || detail === undefined ? "" : " : " + JSON.stringify(detail)));
}

function makePage(options) {
  const listeners = [];
  const sent = [];
  const timers = [];
  const anchor = (href, attrs) => {
    const a = {
      tagName: "A", href, parentElement: null,
      hasAttribute: (n) => !!(attrs && n in attrs),
      getAttribute: (n) => (attrs && n in attrs ? attrs[n] : null),
      querySelectorAll: () => []
    };
    return a;
  };
  const node = () => ({
    style: {}, children: [], appendChild() {}, append() {}, remove() {}, setAttribute() {}, addEventListener() {},
    attachShadow() { return { append() {}, getElementById: () => node(), querySelector: () => node(), querySelectorAll: () => [], contains: () => false }; },
    getBoundingClientRect: () => ({ left: 0, top: 0, right: 0, bottom: 0, width: 0, height: 0 }),
    querySelectorAll: () => [], classList: { toggle() {}, add() {}, remove() {} }
  });
  const document = {
    addEventListener(type, fn, capture) { listeners.push({ target: "document", type, fn, capture: !!capture }); },
    createElement: () => node(),
    createElementNS: () => node(),
    querySelectorAll: () => [],
    documentElement: node(),
    body: node(),
    elementsFromPoint: () => [],
    title: "",
    contentType: "text/html"
  };
  const win = {
    addEventListener(type, fn, capture) { listeners.push({ target: "window", type, fn, capture: !!capture }); },
    location: { href: "https://example.com/page", hostname: "example.com", ancestorOrigins: [] },
    innerWidth: 1280, innerHeight: 800,
    getSelection: () => null
  };
  const api = {
    runtime: {
      sendMessage(msg) {
        sent.push(msg);
        if (msg.action === "getSettings") return Promise.resolve(options.settings);
        if (msg.action === "sendToFetchJet") return Promise.resolve({ ok: true });
        return Promise.resolve({});
      },
      onMessage: { addListener() {} }
    },
    storage: { onChanged: { addListener() {} }, local: { get: () => Promise.resolve({}), set: () => Promise.resolve() } }
  };
  const context = {
    window: win, document, chrome: api, navigator: { userAgent: "Mozilla/5.0 Windows", maxTouchPoints: 0 },
    location: win.location, innerWidth: 1280, innerHeight: 800, console,
    URL, Map, Set, Promise, Array, Object, String, Number, RegExp, Math, JSON, Date, Error,
    setTimeout: (fn) => { timers.push(fn); return timers.length; }, clearTimeout() {},
    setInterval: () => 1, clearInterval() {},
    addEventListener: win.addEventListener, MouseEvent: function () {}, PerformanceObserver: function () { this.observe = () => {}; },
    performance: { getEntriesByType: () => [] },
    fjT: (k) => k, fjIsRtl: () => false, fjOnLanguage() {}, fjReason: (r) => r, fjTheme: () => "dark", fjOnTheme() {},
    getComputedStyle: () => ({}), MutationObserver: function () { this.observe = () => {}; },
    requestAnimationFrame: (fn) => fn()
  };
  context.window.window = context.window;
  context.self = context.window;
  vm.createContext(context);
  const code = fs.readFileSync(path.join(__dirname, "..", "content.js"), "utf8");
  vm.runInContext(code, context, { filename: "content.js" });
  return { listeners, sent, anchor, win };
}

function click(page, target, extra) {
  const state = { prevented: false, stopped: false };
  const event = Object.assign({
    isTrusted: true, button: 0, target, defaultPrevented: false,
    altKey: false, ctrlKey: false, shiftKey: false, metaKey: false,
    composedPath: () => [target],
    preventDefault() { state.prevented = true; },
    stopPropagation() { state.stopped = true; },
    stopImmediatePropagation() { state.stopped = true; }
  }, extra || {});
  const handlers = page.listeners.filter((l) => l.type === "click");
  return { state, handlers, event };
}

const settings = { enabled: true, captureLinks: true, extensions: ["zip", "pdf", "exe"], excludedHosts: [], holdKey: "alt" };

async function run() {
  const page = makePage({ settings });
  await new Promise((r) => setImmediate(r));
  await new Promise((r) => setImmediate(r));

  const clickHandlers = page.listeners.filter((l) => l.type === "click");
  check("one click listener on the window", clickHandlers.length >= 1 && clickHandlers.some((l) => l.target === "window"), clickHandlers.map((l) => l.target + ":" + l.capture));
  const taker = clickHandlers.find((l) => l.target === "window" && !l.capture);
  check("it runs after the page's own code (bubble phase)", !!taker, clickHandlers.map((l) => l.target + ":" + l.capture));
  if (!taker) return;
  const fire = (target, extra) => {
    const before = page.sent.filter((m) => m.action === "sendToFetchJet").length;
    const c = click(page, target, extra);
    taker.fn(c.event);
    return { state: c.state, sent: page.sent.filter((m) => m.action === "sendToFetchJet").length - before };
  };

  let r = fire(page.anchor("https://example.com/a.zip", {}));
  check("a zip link click is sent to the app, the default is prevented", r.sent === 1 && r.state.prevented, r);
  check("the click is not stopped for the page", !r.state.stopped, r);

  r = fire(page.anchor("https://example.com/a.zip", {}), { defaultPrevented: true });
  check("a click the page already handled stays with the page", r.sent === 0 && !r.state.prevented, r);

  r = fire(page.anchor("https://example.com/a.zip", {}), { isTrusted: false });
  check("a click made by a script is ignored", r.sent === 0, r);

  r = fire(page.anchor("https://example.com/a.zip", {}), { button: 2 });
  check("a right click is ignored", r.sent === 0, r);

  r = fire(page.anchor("https://example.com/a.zip", {}), { altKey: true });
  check("the hold key (Alt) leaves the click to the browser", r.sent === 0 && !r.state.prevented, r);

  r = fire(page.anchor("https://example.com/book.pdf", {}));
  check("a plain PDF link is left to the browser's viewer", r.sent === 0 && !r.state.prevented, r);

  r = fire(page.anchor("https://example.com/book.pdf", { download: "" }));
  check("a PDF link marked as a download is taken", r.sent === 1, r);

  r = fire(page.anchor("https://example.com/get?id=7", { download: "" }));
  check("a nameless link marked with download is taken", r.sent === 1, r);

  r = fire(page.anchor("https://example.com/page2", {}));
  check("an ordinary page link is not touched", r.sent === 0 && !r.state.prevented, r);

  const inner = { tagName: "SPAN", parentElement: null };
  const link = page.anchor("https://example.com/b.exe", {});
  r = fire(inner, { composedPath: () => [inner, link] });
  check("the link is found through the event path (shadow DOM)", r.sent === 1, r);

  const off = makePage({ settings: Object.assign({}, settings, { captureLinks: false }) });
  await new Promise((r2) => setImmediate(r2));
  await new Promise((r2) => setImmediate(r2));
  const t2 = off.listeners.find((l) => l.type === "click" && l.target === "window");
  const c2 = click(off, off.anchor("https://example.com/a.zip", {}));
  if (t2) t2.fn(c2.event);
  check("nothing is taken when link capture is off", off.sent.filter((m) => m.action === "sendToFetchJet").length === 0);

  console.log(failed ? "FAILED: " + failed : "all passed");
  process.exit(failed ? 1 : 0);
}

run().catch((e) => { console.log("  FAIL  harness error: " + (e && e.stack || e)); process.exit(1); });
