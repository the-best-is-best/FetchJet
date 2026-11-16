"use strict";
const fs = require("fs");
const os = require("os");
const path = require("path");
const http = require("http");
const { spawn } = require("child_process");

const root = path.join(__dirname, "..", "..");
const built = path.join(root, "build", "extension", "chrome");
const chrome = process.env.CHROME || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const SITE_PORT = 18460;
const DEBUG_PORT = 9334;
const profile = fs.mkdtempSync(path.join(os.tmpdir(), "fj-chrome-"));

let failed = 0;
function check(name, ok, detail) {
  if (!ok) failed++;
  console.log((ok ? "  ok    " : "  FAIL  ") + name + (ok || detail === undefined ? "" : " : " + JSON.stringify(detail)));
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const zoomPage = '<!doctype html><title>zoom test</title><body style="margin:0"><div id="hero" style="width:600px;height:340px;margin:60px;background:#223">' +
  '<audio controls preload="none" src="/rec/play/audio.m4a"></audio></div></body>';
const plainPage = '<!doctype html><title>plain</title><body><video controls src="/test/files/none.mp4" width="640" height="360"></video></body>';

const site = http.createServer((req, res) => {
  const url = new URL(req.url, "http://x");
  if (url.pathname.startsWith("/rec/play/") && url.pathname.endsWith(".html")) {
    res.writeHead(200, { "Content-Type": "text/html" });
    return res.end(zoomPage);
  }
  if (url.pathname === "/rec/play/audio.m4a") {
    res.writeHead(200, { "Content-Type": "audio/mp4" });
    return res.end(Buffer.alloc(2048));
  }
  if (url.pathname === "/plain.html") {
    res.writeHead(200, { "Content-Type": "text/html" });
    return res.end(plainPage);
  }
  const file = path.join(root, "extension", decodeURIComponent(url.pathname));
  if (file.startsWith(path.join(root, "extension")) && fs.existsSync(file) && fs.statSync(file).isFile()) {
    res.writeHead(200, { "Content-Type": file.endsWith(".html") ? "text/html" : file.endsWith(".js") ? "text/javascript" : "application/octet-stream" });
    return res.end(fs.readFileSync(file));
  }
  res.writeHead(404);
  res.end("no");
});

class Cdp {
  constructor(url) {
    this.ws = new WebSocket(url);
    this.id = 0;
    this.waiting = new Map();
    this.events = [];
    this.ready = new Promise((resolve) => (this.ws.onopen = resolve));
    this.ws.onmessage = (e) => {
      const m = JSON.parse(e.data);
      if (m.id && this.waiting.has(m.id)) {
        const { resolve, reject } = this.waiting.get(m.id);
        this.waiting.delete(m.id);
        m.error ? reject(new Error(JSON.stringify(m.error))) : resolve(m.result);
      } else if (m.method) {
        this.events.push(m);
      }
    };
  }

  send(method, params = {}, sessionId) {
    const id = ++this.id;
    this.ws.send(JSON.stringify({ id, method, params, sessionId }));
    return new Promise((resolve, reject) => this.waiting.set(id, { resolve, reject }));
  }
}

async function evaluate(cdp, sessionId, expression) {
  const r = await cdp.send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true }, sessionId);
  if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails).slice(0, 300));
  return r.result.value;
}

async function openTab(cdp, url) {
  const { targetId } = await cdp.send("Target.createTarget", { url });
  const { sessionId } = await cdp.send("Target.attachToTarget", { targetId, flatten: true });
  await cdp.send("Runtime.enable", {}, sessionId);
  return { targetId, sessionId };
}

async function main() {
  await new Promise((r) => site.listen(SITE_PORT, "127.0.0.1", r));
  const child = spawn(chrome, [
    "--headless=new", "--remote-debugging-port=" + DEBUG_PORT, "--remote-allow-origins=*", "--user-data-dir=" + profile,
    "--enable-unsafe-extension-debugging", "--no-first-run", "--disable-gpu", "--disable-background-networking",
    "--disable-component-update", "--no-pings", "--disable-sync", "--metrics-recording-only",
    "--host-resolver-rules=MAP us06web.zoom.us 127.0.0.1", "about:blank"
  ], { stdio: "ignore" });
  let version;
  for (let i = 0; i < 40 && !version; i++) {
    try { version = await (await fetch("http://127.0.0.1:" + DEBUG_PORT + "/json/version")).json(); } catch (_) { await sleep(250); }
  }
  if (!version) throw new Error("Chrome did not start");
  console.log("Chrome " + version.Browser);
  const cdp = new Cdp(version.webSocketDebuggerUrl);
  await cdp.ready;
  try {
    const loaded = await cdp.send("Extensions.loadUnpacked", { path: built });
    const id = loaded.id;
    check("the built extension loads", !!id, loaded);
    await sleep(2500);

    const targets = (await cdp.send("Target.getTargets")).targetInfos;
    const worker = targets.find((t) => t.type === "service_worker" && t.url.includes(id));
    check("its service worker runs", !!worker, targets.map((t) => t.type + " " + t.url));
    if (worker) {
      const { sessionId } = await cdp.send("Target.attachToTarget", { targetId: worker.targetId, flatten: true });
      await cdp.send("Runtime.enable", {}, sessionId);
      const state = await evaluate(cdp, sessionId, "JSON.stringify({ios: typeof ON_IOS !== 'undefined' ? ON_IOS : 'missing', version: chrome.runtime.getManifest().version, open: typeof openPageInApp, session: typeof iosSessionPage})");
      const s = JSON.parse(state);
      check("the service worker is not in the iOS mode and reports 3.5.0", s.ios === false && s.version === "3.5.0", s);
    }

    const plain = await openTab(cdp, "http://127.0.0.1:" + SITE_PORT + "/test/real.html");
    await sleep(3500);
    const plainButtons = await evaluate(cdp, plain.sessionId, "document.querySelectorAll('fetchjet-button').length");
    check("the real.html test page gets page buttons", plainButtons >= 1, plainButtons);

    const zoom = await openTab(cdp, "http://us06web.zoom.us:" + SITE_PORT + "/rec/play/x.html");
    await sleep(3500);
    const zoomInfo = JSON.parse(await evaluate(cdp, zoom.sessionId, `(() => {
      const hero = document.getElementById('hero').getBoundingClientRect();
      const buttons = [...document.querySelectorAll('fetchjet-button')].map((b) => { const r = b.getBoundingClientRect(); return { x: r.x, y: r.y, w: r.width, h: r.height }; });
      return JSON.stringify({ host: location.hostname, buttons, hero: { x: hero.x, y: hero.y, w: hero.width, h: hero.height }, src: document.querySelector('audio').currentSrc });
    })()`));
    check("a Zoom recording page with only audio gets one page button", zoomInfo.buttons.length === 1, zoomInfo);
    const zb = zoomInfo.buttons[0];
    check("the button sits on the 600x340 block that holds the audio", !!zb && zb.x >= zoomInfo.hero.x - 5 && zb.y >= zoomInfo.hero.y - 5 &&
      zb.x <= zoomInfo.hero.x + zoomInfo.hero.w && zb.y <= zoomInfo.hero.y + zoomInfo.hero.h, zoomInfo);
    const rows = await evaluate(cdp, zoom.sessionId, "document.querySelector('fetchjet-button') ? 'present' : 'none'");
    check("the iOS-only row is not built outside iOS (no page-in-app text in the button list)", rows === "present", rows);

    const popup = await openTab(cdp, "chrome-extension://" + id + "/popup.html");
    await sleep(2500);
    const popupInfo = JSON.parse(await evaluate(cdp, popup.sessionId, `(() => {
      const visible = (e) => !!e && getComputedStyle(e).display !== 'none' && e.getBoundingClientRect().height > 0;
      const banner = document.getElementById('permBanner');
      return JSON.stringify({ title: document.title, text: document.body.innerText.slice(0, 200), banner: !!banner, bannerVisible: banner ? visible(banner) : null });
    })()`));
    check("the popup opens with text", popupInfo.text.length > 20, popupInfo);
    check("with full site access the access banner does not show", popupInfo.banner === true && popupInfo.bannerVisible === false, popupInfo);
    const onClick = JSON.parse(await evaluate(cdp, popup.sessionId, `(async () => {
      chrome.permissions.contains = async () => false;
      chrome.permissions.request = async () => true;
      await checkHostPermission();
      const shown = getComputedStyle(document.getElementById('permBanner')).display;
      document.getElementById('grantBtn').click();
      await new Promise((r) => setTimeout(r, 300));
      const after = getComputedStyle(document.getElementById('permBanner')).display;
      chrome.permissions.contains = async (o) => (o.origins || []).includes('*://*/*');
      await checkHostPermission();
      const withPattern = getComputedStyle(document.getElementById('permBanner')).display;
      return JSON.stringify({ shown, after, withPattern });
    })()`));
    check("with site access 'On click' the banner shows", onClick.shown !== "none", onClick);
    check("'Allow access to all sites' hides it", onClick.after === "none", onClick);
    check("access to *://*/* alone also hides it", onClick.withPattern === "none", onClick);

    const options = await openTab(cdp, "chrome-extension://" + id + "/options.html");
    await sleep(2500);
    const optionsText = await evaluate(cdp, options.sessionId, "document.body.innerText");
    check("the options page opens", optionsText.length > 200, optionsText.length);
    const hasOption = await evaluate(cdp, options.sessionId, "!!document.getElementById('limitToListed')");
    check("it has the new 'only the listed file types' option", hasOption === true, hasOption);
    check("and no page-in-app text on a desktop browser", !/Open this page in FetchJet/.test(optionsText));

    const errors = cdp.events.filter((e) => e.method === "Runtime.exceptionThrown").map((e) => JSON.stringify(e.params.exceptionDetails).slice(0, 160));
    check("no uncaught exception in any page", errors.length === 0, errors.slice(0, 3));
  } finally {
    try { await cdp.send("Browser.close"); } catch (_) {}
    child.kill();
    site.close();
    await sleep(1500);
    try { fs.rmSync(profile, { recursive: true, force: true }); } catch (_) {}
  }
  console.log(failed ? failed + " failed" : "all passed");
  process.exit(failed ? 1 : 0);
}

main().catch((e) => { console.log("  FAIL  " + e.message); process.exit(2); });
