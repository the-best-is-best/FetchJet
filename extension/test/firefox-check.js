"use strict";
const fs = require("fs");
const path = require("path");
const http = require("http");
const { spawn } = require("child_process");

const root = path.join(__dirname, "..", "..");
const built = path.join(root, "build", "extension", "firefox");
const GECKO = process.env.GECKODRIVER || "E:\\tools\\probes\\geckodriver\\geckodriver.exe";
const FIREFOX = process.env.FIREFOX_BIN ||
  "C:\\Program Files\\WindowsApps\\Mozilla.Firefox_160.0.0.0_x64__n80bbvh6b1yt2\\VFS\\ProgramFiles\\Firefox Package Root\\firefox.exe";
const SITE_PORT = 18461;
const GECKO_PORT = 4478;

let failed = 0;
function check(name, ok, detail) {
  if (!ok) failed++;
  console.log((ok ? "  ok    " : "  FAIL  ") + name + (ok || detail === undefined ? "" : " : " + JSON.stringify(detail)));
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const zoomPage = '<!doctype html><title>zoom test</title><body style="margin:0"><div id="hero" style="width:600px;height:340px;margin:60px;background:#223">' +
  '<audio controls preload="none" src="/rec/play/audio.m4a"></audio></div></body>';

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
  const file = path.join(root, "extension", decodeURIComponent(url.pathname));
  if (file.startsWith(path.join(root, "extension")) && fs.existsSync(file) && fs.statSync(file).isFile()) {
    res.writeHead(200, { "Content-Type": file.endsWith(".html") ? "text/html" : file.endsWith(".js") ? "text/javascript" : "application/octet-stream" });
    return res.end(fs.readFileSync(file));
  }
  res.writeHead(404);
  res.end("no");
});

async function wd(method, url, body) {
  const r = await fetch("http://127.0.0.1:" + GECKO_PORT + url, {
    method, headers: { "Content-Type": "application/json" }, body: body === undefined ? undefined : JSON.stringify(body)
  });
  const j = await r.json();
  if (j.value && j.value.error) throw new Error(j.value.error + ": " + j.value.message);
  return j.value;
}

async function main() {
  await new Promise((r) => site.listen(SITE_PORT, "127.0.0.1", r));
  const driver = spawn(GECKO, ["--port", String(GECKO_PORT)], { stdio: "ignore", env: { ...process.env, MOZ_REMOTE_ALLOW_SYSTEM_ACCESS: "1" } });
  await sleep(1500);
  let session;
  try {
    const created = await wd("POST", "/session", {
      capabilities: {
        alwaysMatch: {
          browserName: "firefox",
          "moz:firefoxOptions": {
            binary: FIREFOX,
            args: ["-headless"],
            prefs: {
              "network.dns.localDomains": "us06web.zoom.us",
              "app.update.auto": false, "app.update.enabled": false, "datareporting.healthreport.uploadEnabled": false,
              "toolkit.telemetry.enabled": false, "browser.safebrowsing.malware.enabled": false, "extensions.update.enabled": false
            }
          }
        }
      }
    });
    session = created.sessionId;
    console.log("Firefox " + created.capabilities.browserVersion);
    const S = "/session/" + session;
    const addon = await wd("POST", S + "/moz/addon/install", { path: built, temporary: true });
    check("the built Firefox extension installs (temporary)", !!addon, addon);
    await wd("POST", S + "/moz/context", { context: "chrome" });
    const uuids = await wd("POST", S + "/execute/sync", { script: "return Services.prefs.getStringPref('extensions.webextensions.uuids');", args: [] });
    const uuid = JSON.parse(uuids)["fetchjet@michelle.raouf"];
    check("it has a moz-extension address", !!uuid, uuids);
    await wd("POST", S + "/moz/context", { context: "content" });

    await wd("POST", S + "/url", { url: "moz-extension://" + uuid + "/popup.html" });
    await sleep(2500);
    const popup = await wd("POST", S + "/execute/sync", {
      script: "const b=document.getElementById('permBanner'); return {text: document.body.innerText.slice(0,160), banner: !!b, shown: b ? getComputedStyle(b).display : null};",
      args: []
    });
    check("the popup opens with text", popup.text.length > 20, popup);
    console.log("  info  the popup banner display with the default Firefox grant: " + popup.shown);
    const onClick = await wd("POST", S + "/execute/async", {
      script: `const done = arguments[arguments.length - 1];
        (async () => {
          browser.permissions.contains = async () => false;
          browser.permissions.request = async () => true;
          await checkHostPermission();
          const shown = getComputedStyle(document.getElementById('permBanner')).display;
          document.getElementById('grantBtn').click();
          await new Promise((r) => setTimeout(r, 300));
          const after = getComputedStyle(document.getElementById('permBanner')).display;
          browser.permissions.contains = async (o) => (o.origins || []).includes('*://*/*');
          await checkHostPermission();
          const withPattern = getComputedStyle(document.getElementById('permBanner')).display;
          done({ shown, after, withPattern });
        })();`,
      args: []
    });
    check("with site access 'On click' the banner shows", onClick.shown !== "none", onClick);
    check("'Allow access to all sites' hides it", onClick.after === "none", onClick);
    check("access to *://*/* alone also hides it", onClick.withPattern === "none", onClick);

    await wd("POST", S + "/url", { url: "moz-extension://" + uuid + "/options.html" });
    await sleep(2000);
    const options = await wd("POST", S + "/execute/sync", {
      script: "return {limit: !!document.getElementById('limitToListed'), iosRow: /Open this page in FetchJet/.test(document.body.innerText), length: document.body.innerText.length};",
      args: []
    });
    check("the options page opens with the new option and no iOS row", options.limit && !options.iosRow && options.length > 200, options);

    await wd("POST", S + "/url", { url: "http://127.0.0.1:" + SITE_PORT + "/test/real.html" });
    await sleep(4500);
    const real = await wd("POST", S + "/execute/sync", { script: "return document.querySelectorAll('fetchjet-button').length;", args: [] });
    console.log("  info  fetchjet-button elements on real.html: " + real);
    check("real.html gets page buttons (needs the all-sites grant in Firefox)", real >= 1, real);

    await wd("POST", S + "/url", { url: "http://us06web.zoom.us:" + SITE_PORT + "/rec/play/x.html" });
    await sleep(4500);
    const zoom = await wd("POST", S + "/execute/sync", {
      script: `const hero = document.getElementById('hero').getBoundingClientRect();
        const b = [...document.querySelectorAll('fetchjet-button')].map((e) => { const r = e.getBoundingClientRect(); return {x: r.x, y: r.y, w: r.width, h: r.height}; });
        return {host: location.hostname, buttons: b, hero: {x: hero.x, y: hero.y, w: hero.width, h: hero.height}};`,
      args: []
    });
    console.log("  info  zoom page: " + JSON.stringify(zoom));
    check("the Zoom audio page gets one page button on the audio block", zoom.buttons.length === 1 && zoom.buttons[0].x >= zoom.hero.x - 5 && zoom.buttons[0].y >= zoom.hero.y - 5, zoom);
  } finally {
    try { if (session) await wd("DELETE", "/session/" + session); } catch (_) {}
    driver.kill();
    site.close();
  }
  console.log(failed ? failed + " failed" : "all passed");
  process.exit(failed ? 1 : 0);
}

main().catch((e) => { console.log("  FAIL  " + e.message); process.exit(2); });
