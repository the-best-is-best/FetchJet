"use strict";
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

function load(userAgent) {
  const log = { created: [], updated: [], handlers: [] };
  const chrome = deep();
  chrome.storage = { local: { get: async () => ({ settings: {} }), set: async () => {} }, session: { get: async () => ({}), set: async () => {}, remove: async () => {} }, onChanged: { addListener() {} } };
  chrome.permissions = { contains: async () => true };
  chrome.runtime = { getManifest: () => ({ version: "9.9.9" }), onMessage: { addListener(fn) { log.handlers.push(fn); } }, onInstalled: { addListener() {} }, onStartup: { addListener() {} }, lastError: null, getURL: (p) => p };
  chrome.tabs = {
    create: async (o) => { log.created.push(o); return { id: log.created.length }; },
    update: async (id, o) => { log.updated.push({ id, ...o }); return {}; },
    remove: async () => {}, query: async () => [{ id: 99 }],
    onUpdated: { addListener() {}, removeListener() {} }, onRemoved: { addListener() {} }, onActivated: { addListener() {} }, onCreated: { addListener() {} },
  };
  const fakeFetch = async () => ({ ok: true, status: 200, json: async () => ({ ok: true }), text: async () => "ok" });
  const sandbox = { chrome, console, URL, Promise, setTimeout, clearTimeout, Date, Set, Map, JSON, Math, fetch: fakeFetch, self: {}, navigator: { userAgent }, AbortController, TextEncoder, TextDecoder };
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  const root = __dirname + "/..";
  for (const f of ["i18n/i18n.js", "i18n/en.js", "yt-core.js"]) {
    try { vm.runInContext(fs.readFileSync(root + "/" + f, "utf8"), sandbox, { filename: f }); } catch (_) {}
  }
  vm.runInContext(fs.readFileSync(root + "/background.js", "utf8"), sandbox, { filename: "background.js" });
  const ask = (request, sender) => new Promise((resolve) => {
    const r = log.handlers[0](request, sender, resolve);
    if (r && typeof r.then === "function") r.then(resolve);
  });
  return { ask, log };
}

exports.run = async (check) => {
  const link = "https://www.udemy.com/course/x/learn/lecture/1";
  const a = load("Mozilla/5.0 Windows");
  const r = await a.ask({ action: "openPageDialog", page: link }, { tab: { id: 5, url: "https://other/" } });
  check("background: ok", r && r.ok === true, r);
  check("background: opens fetchjet:// in a NEW tab", a.log.created.length === 1 && a.log.created[0].url === "fetchjet://download?url=" + encodeURIComponent(link), a.log.created);
  check("background: tab is active, the current tab is not navigated", a.log.created[0].active === true && a.log.updated.length === 0);
  const b = load("Mozilla/5.0 Windows");
  await b.ask({ action: "openPageDialog", page: "" }, { tab: { id: 5, url: link } });
  check("background: empty page falls back to the sender tab", b.log.created.length === 1 && b.log.created[0].url.endsWith(encodeURIComponent(link)), b.log.created);
  const c = load("Mozilla/5.0 Windows");
  const rc = await c.ask({ action: "openPageDialog", page: "javascript:alert(1)" }, { tab: { id: 5, url: "about:blank" } });
  check("background: non-http page refused", rc && rc.ok === false && c.log.created.length === 0, rc);
  const d = load("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)");
  const rd = await d.ask({ action: "openPageDialog", page: link }, { tab: { id: 5, url: link } });
  check("background: iOS does not use it", rd && rd.ok === false && d.log.created.length === 0, rd);
};
