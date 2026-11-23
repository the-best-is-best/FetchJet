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
  const log = { created: [], updated: [], removed: [], bridge: [] };
  const chrome = deep();
  chrome.storage = { local: { get: async () => ({ settings: stored || {} }), set: async () => {} }, session: { get: async () => ({}), set: async () => {}, remove: async () => {} }, onChanged: { addListener() {} } };
  chrome.permissions = { contains: async () => true };
  chrome.runtime = { getManifest: () => ({ version: "9.9.9" }), onMessage: { addListener() {} }, onInstalled: { addListener() {} }, onStartup: { addListener() {} }, lastError: null, getURL: (p) => p };
  chrome.tabs = {
    create: async (o) => { log.created.push(o); return { id: log.created.length }; },
    update: async (id, o) => { log.updated.push({ id, ...o }); return {}; },
    remove: async (id) => { log.removed.push(id); },
    query: async () => [{ id: 99 }],
    onUpdated: { addListener() {}, removeListener() {} }, onRemoved: { addListener() {}, removeListener() {} }, onActivated: { addListener() {} }, onCreated: { addListener() {} },
  };
  const fakeFetch = async (u) => { if (/\/(add|links)$/.test(String(u))) log.bridge.push(String(u)); return { ok: true, status: 200, json: async () => ({ ok: true }), text: async () => "ok" }; };
  const sandbox = { chrome, console, URL, Promise, setTimeout, clearTimeout, Date, Set, Map, JSON, Math, fetch: fakeFetch, self: {}, navigator: { userAgent: "x" }, AbortController, TextEncoder, TextDecoder };
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  const root = __dirname + "/..";
  for (const f of ["i18n/i18n.js", "i18n/en.js", "yt-core.js"]) {
    try { vm.runInContext(fs.readFileSync(root + "/" + f, "utf8"), sandbox, { filename: f }); } catch (_) {}
  }
  vm.runInContext(fs.readFileSync(root + "/background.js", "utf8") + "\n;this.__send = sendToFetchJet; this.__batch = sendBatchToFetchJet;", sandbox, { filename: "background.js" });
  return { send: sandbox.__send, batch: sandbox.__batch, log };
}

let failures = 0;
function check(name, ok) {
  if (!ok) failures++;
  console.log((ok ? "  ok    " : "  FAIL  ") + name);
}

(async () => {
  const on = load({ openDirectly: true });
  const r = await on.send({ url: "https://example.com/a.zip" }, "test");
  check("plain link: ok", r.ok === true);
  check("plain link: one new tab, fetchjet:// url", on.log.created.length === 1 && on.log.created[0].url.startsWith("fetchjet://download?url="));
  check("plain link: the active tab is not navigated", on.log.updated.length === 0);
  check("plain link: no bridge call", on.log.bridge.length === 0);

  const named = load({ openDirectly: true });
  await named.send({ url: "https://example.com/a.zip", fileName: "x.zip" }, "test");
  check("captured file name: no fetchjet:// tab", named.log.created.length === 0);
  const ref = load({ openDirectly: true });
  await ref.send({ url: "https://example.com/a.zip", referer: "https://example.com/page" }, "test");
  check("referer: no fetchjet:// tab", ref.log.created.length === 0);

  const big = load({ openDirectly: true });
  const links = [1, 2, 3, 4, 5].map((n) => "https://example.com/f" + n + ".zip");
  await big.batch(links);
  check("batch of 5: no fetchjet:// tabs", big.log.created.every((t) => !String(t.url).startsWith("fetchjet://")));
  check("batch of 5: bridge used", big.log.bridge.length > 0);

  const small = load({ openDirectly: true });
  await small.batch(links.slice(0, 2));
  check("batch of 2: two fetchjet:// tabs", small.log.created.length === 2 && small.log.created.every((t) => t.url.startsWith("fetchjet://download?url=")));
  check("batch of 2: no bridge call", small.log.bridge.length === 0);

  const off = load({ openDirectly: false });
  await off.send({ url: "https://example.com/a.zip" }, "test");
  check("option off: no fetchjet:// tab", off.log.created.length === 0 && off.log.bridge.length > 0);

  process.exit(failures ? 1 : 0);
})();
