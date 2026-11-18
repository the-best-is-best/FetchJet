const fs = require("fs");
const vm = require("vm");

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

function load(stored) {
  const chrome = deep();
  chrome.storage = { local: { get: async () => ({ settings: stored || {} }), set: async () => {} }, onChanged: { addListener() {} } };
  chrome.storage.session = { get: async () => ({}), set: async () => {}, remove: async () => {} };
  chrome.permissions = { contains: async () => true };
  chrome.runtime = { getManifest: () => ({ version: "9.9.9" }), onMessage: { addListener() {} }, onInstalled: { addListener() {} }, onStartup: { addListener() {} }, lastError: null, getURL: (p) => p };
  const sandbox = { chrome, console, URL, Promise, setTimeout, clearTimeout, Date, Set, Map, JSON, Math, fetch: async () => ({ ok: false }), self: {}, navigator: { userAgent: "x" }, AbortController, TextEncoder, TextDecoder };
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  const root = __dirname + "/..";
  for (const f of ["i18n/i18n.js", "i18n/en.js", "yt-core.js"]) {
    try { vm.runInContext(fs.readFileSync(root + "/" + f, "utf8"), sandbox, { filename: f }); } catch (_) {}
  }
  vm.runInContext(fs.readFileSync(process.env.BG_PATH || root + "/background.js", "utf8") + "\n;this.__shouldTake = shouldTake;", sandbox, { filename: "background.js" });
  return sandbox.__shouldTake;
}

let failures = 0;
async function check(name, got, want) {
  const ok = got === want;
  if (!ok) failures++;
  console.log((ok ? "  ok    " : "  FAIL  ") + name + (ok ? "" : " (got " + got + ")"));
}

(async () => {
  const item = (name, url) => ({ id: 1, url: url || "https://example.com/dl/" + name, finalUrl: url || "https://example.com/dl/" + name, filename: "/home/x/Downloads/" + name, startTime: new Date().toISOString(), totalBytes: 5000000 });
  const open = load({ autoLaunch: true });
  await check("zip is taken", await open(item("data.zip")), true);
  await check("zip with a query string and a server name", await open(item("backup.zip", "https://example.com/get?id=7")), true);
  await check("tgz is taken", await open(item("src.tgz")), true);
  await check("docx is taken (not in the old list)", await open(item("report.docx")), true);
  await check("an unlisted type is taken when the list is not enforced", await open(item("model.stl")), true);
  await check("a file with no extension is taken", await open(item("download")), true);
  await check("a blob: link stays in the browser", await open(item("a.zip", "blob:https://example.com/abc")), false);

  const strict = load({ autoLaunch: true, limitToListed: true });
  await check("strict: zip is taken", await strict(item("data.zip")), true);
  await check("strict: docx is taken (now in the list)", await strict(item("report.docx")), true);
  await check("strict: an unlisted type stays in the browser", await strict(item("model.stl")), false);

  const off = load({ enabled: false });
  await check("master switch off: nothing is taken", await off(item("data.zip")), false);
  const small = load({ minSizeKB: 99999 });
  await check("minimum size still applies", await small(item("data.zip")), false);
  console.log(failures ? failures + " failed" : "all passed");
  process.exit(failures ? 1 : 0);
})();
