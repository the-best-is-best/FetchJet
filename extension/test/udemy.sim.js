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

const ORIGIN = "https://www.udemy.com";
const COURSE = 77;
const CDN = "https://att-c.udemycdn.com";

const curriculum = [
  { _class: "chapter", id: 1, title: "Start" },
  { _class: "lecture", id: 11, title: "Intro", asset: { asset_type: "Video" },
    supplementary_assets: [{ id: 901, asset_type: "File", title: "slides.pdf", filename: "slides.pdf" },
      { id: 902, asset_type: "ExternalLink", title: "Docs", external_url: "https://example.com/docs" }] },
  { _class: "lecture", id: 12, title: "Handbook", asset: { asset_type: "E-Book" }, supplementary_assets: [] },
  { _class: "quiz", id: 13, title: "Quiz 1" },
  { _class: "lecture", id: 14, title: "Read me", asset: { asset_type: "Article" }, supplementary_assets: [] },
  { _class: "lecture", id: 15, title: "Setup", asset: { asset_type: "Article" },
    supplementary_assets: [{ id: 903, asset_type: "SourceCode", title: "starter-code.zip", filename: "starter-code.zip" }] },
  { _class: "chapter", id: 2, title: "More" },
  { _class: "lecture", id: 16, title: "Protected", asset: { asset_type: "Video" },
    supplementary_assets: [{ id: 904, asset_type: "File", title: "drm-notes.txt", filename: "drm-notes.txt" }] },
  { _class: "lecture", id: 17, title: "Deck", asset: { asset_type: "Presentation" }, supplementary_assets: [] },
  { _class: "lecture", id: 18, title: "Only a link", asset: { asset_type: "ExternalLink", external_url: "https://example.com" } },
  { _class: "lecture", id: 19, title: "Old style" },
];

const lectures = {
  11: { title: "Intro", asset: { asset_type: "Video", stream_urls: { Video: [{ type: "application/x-mpegURL", file: `${CDN}/intro/index.m3u8?sig=a` }] } },
    supplementary_assets: [
      { id: 901, asset_type: "File", title: "slides.pdf", filename: "slides.pdf", download_urls: { File: [{ label: "download", file: `${CDN}/slides/original.pdf?sig=b` }] } },
      { id: 902, asset_type: "ExternalLink", title: "Docs", external_url: "https://example.com/docs" }] },
  12: { title: "Handbook", asset: { asset_type: "E-Book", filename: "handbook-v2.pdf", download_urls: { "E-Book": [{ label: "download", file: `${CDN}/ebook/x?sig=c` }] } }, supplementary_assets: [] },
  15: { title: "Setup", asset: { asset_type: "Article" }, supplementary_assets: [{ id: 903, asset_type: "SourceCode", title: "starter-code.zip", filename: "starter-code.zip" }] },
  16: { title: "Protected", asset: { asset_type: "Video", course_is_drmed: true, media_sources: [] },
    supplementary_assets: [{ id: 904, asset_type: "File", title: "drm-notes.txt", filename: "drm-notes.txt", download_urls: { File: [{ file: `${CDN}/n?sig=d` }] } }] },
  17: { title: "Deck", asset: { asset_type: "Presentation", title: "Deck", download_urls: { Presentation: [{ file: `${CDN}/p/blob?response-content-disposition=attachment%3B%20filename%3D%22deck.pptx%22&sig=e` }] } }, supplementary_assets: [] },
  19: { title: "Old style", asset: { asset_type: "Video", stream_urls: { Video: [{ type: "video/mp4", file: `${CDN}/old/720.mp4?sig=f`, label: "720" }] } } },
};

const apiLog = [];
let strict = false;
let pageHost = "www.udemy.com";
let foreign = false;
let loopNext = false;
async function udemyFetch(url) {
  const u = new URL(url);
  apiLog.push(u.pathname + u.search);
  const json = (body, status = 200) => ({ ok: status === 200, status, json: async () => body });
  if (strict && /supplementary_assets|filename|external_url/.test(decodeURIComponent(u.search))) return json({}, 400);
  let m;
  if (/^\/api-2\.0\/courses\/(my-course|77)\/$/.test(u.pathname)) return json({ id: COURSE, title: "My Course" });
  if (u.pathname === `/api-2.0/courses/${COURSE}/subscriber-curriculum-items/`) {
    const wantsRes = (u.searchParams.get("fields[lecture]") || "").includes("supplementary_assets");
    return json({ results: curriculum.map(e => wantsRes ? e : { ...e, supplementary_assets: undefined }), next: loopNext ? url : null });
  }
  if ((m = new RegExp(`^/api-2\\.0/users/me/subscribed-courses/${COURSE}/lectures/(\\d+)/supplementary-assets/(\\d+)/$`).exec(u.pathname))) {
    if (m[2] === "903") return json({ id: 903, filename: "starter-code.zip", download_urls: { SourceCode: [{ file: `${CDN}/code/z?sig=g` }] } });
    return json({}, 404);
  }
  if ((m = new RegExp(`^/api-2\\.0/users/me/subscribed-courses/${COURSE}/lectures/(\\d+)/$`).exec(u.pathname))) {
    const d = lectures[m[1]];
    if (foreign && m[1] === "19") return json({ ...d, asset: { asset_type: "Video", stream_urls: { Video: [{ type: "video/mp4", file: "https://evil.example/720.mp4", label: "720" }] } } });
    return d ? json(d) : json({}, 404);
  }
  return json({}, 404);
}

function load() {
  const log = { adds: [], notes: [] };
  const chrome = deep();
  chrome.storage = { local: { get: async () => ({ settings: { notifications: true } }), set: async () => {} }, session: { get: async () => ({}), set: async () => {}, remove: async () => {} }, onChanged: { addListener() {} } };
  chrome.permissions = { contains: async () => true };
  chrome.runtime = { getManifest: () => ({ version: "9.9.9" }), onMessage: { addListener() {} }, onInstalled: { addListener() {} }, onStartup: { addListener() {} }, lastError: null, getURL: (p) => p };
  chrome.tabs = {
    get: async () => ({ id: 5, url: `${ORIGIN}/course/my-course/learn/lecture/11#overview` }),
    create: async () => ({ id: 1 }), update: async () => ({}), remove: async () => {}, query: async () => [{ id: 5 }],
    sendMessage: async () => ({}),
    onUpdated: { addListener() {}, removeListener() {} }, onRemoved: { addListener() {}, removeListener() {} }, onActivated: { addListener() {} }, onCreated: { addListener() {} },
  };
  chrome.notifications = { create: async (o) => { log.notes.push(o.message); return "n"; } };
  chrome.scripting = {
    executeScript: async ({ func, args, world }) => {
      const page = { console, URL, Promise, JSON, Error, String, Object, Array, encodeURIComponent, fetch: udemyFetch,
        location: { origin: ORIGIN, hostname: pageHost }, document: { documentElement: { innerHTML: `<div data-course-id="${COURSE}"></div>` } } };
      vm.createContext(page);
      const fn = vm.runInContext("(" + func.toString() + ")", page);
      log.world = world;
      return [{ result: JSON.parse(JSON.stringify(await fn(...JSON.parse(JSON.stringify(args))))) }];
    },
  };
  const appFetch = async (u, opts) => {
    if (/\/add$/.test(String(u)) && opts && opts.body) log.adds.push(JSON.parse(opts.body));
    return { ok: true, status: 200, json: async () => ({ ok: true }), text: async () => "ok" };
  };
  const sandbox = { chrome, console, URL, URLSearchParams, Promise, setTimeout, clearTimeout, Date, Set, Map, JSON, Math, fetch: appFetch, self: {}, navigator: { userAgent: "Mozilla/5.0 Windows" }, AbortController, TextEncoder, TextDecoder };
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  const root = __dirname + "/..";
  for (const f of ["i18n/i18n.js", "i18n/en.js", "yt-core.js"]) {
    try { vm.runInContext(fs.readFileSync(root + "/" + f, "utf8"), sandbox, { filename: f }); } catch (_) {}
  }
  vm.runInContext(fs.readFileSync(root + "/background.js", "utf8"), sandbox, { filename: "background.js" });
  return { sandbox, log };
}

(async () => {
  let failed = 0;
  const check = (name, ok, extra) => {
    if (ok) console.log("ok   " + name);
    else { failed++; console.log("FAIL " + name + (extra !== undefined ? "  " + JSON.stringify(extra) : "")); }
  };

  const { sandbox, log } = load();
  const r = await sandbox.downloadItem({ kind: "course" }, { id: 5, url: `${ORIGIN}/course/my-course/learn/lecture/11#overview`, title: "Udemy" });
  check("course: sent", r && r.ok === true, r);
  check("course: read in the page's own world", log.world === "MAIN", log.world);

  const final = log.adds.find(b => b.playlist);
  check("course: the list reached the app", !!final, log.adds);
  if (!final) { console.log(failed + " FAILED"); process.exit(1); }
  check("course: link is the course page", final.url === `${ORIGIN}/course/my-course/`, final.url);
  check("course: title", final.playlist.title === "My Course", final.playlist.title);
  const got = final.playlist.entries.map(e => [e.title, e.url, e.fileName || ""]);
  const want = [
    ["001 - Intro", `${CDN}/intro/index.m3u8?sig=a`, ""],
    ["001 - slides.pdf", `${CDN}/slides/original.pdf?sig=b`, "001 - slides.pdf"],
    ["002 - Handbook.pdf", `${CDN}/ebook/x?sig=c`, "002 - Handbook.pdf"],
    ["003 - starter-code.zip", `${CDN}/code/z?sig=g`, "003 - starter-code.zip"],
    ["004 - drm-notes.txt", `${CDN}/n?sig=d`, "004 - drm-notes.txt"],
    ["005 - Deck.pptx", `${CDN}/p/blob?response-content-disposition=attachment%3B%20filename%3D%22deck.pptx%22&sig=e`, "005 - Deck.pptx"],
    ["006 - Old style", `${CDN}/old/720.mp4?sig=f`, ""],
  ];
  check("course: entries, names and order", JSON.stringify(got) === JSON.stringify(want), got);
  check("course: no external link sent", !final.playlist.entries.some(e => /example\.com/.test(e.url)));
  check("course: no Article / quiz lecture sent", !final.playlist.entries.some(e => /Read me|Quiz|Only a link/.test(e.title)));

  const reading = log.adds.filter(b => b.reading);
  const totals = new Set(reading.map(b => b.reading.total).filter(Boolean));
  check("course: reading counts the lectures with a video or a file (6)", totals.size === 1 && totals.has(6), reading.map(b => b.reading));
  check("course: DRM lecture reported once", log.notes.some(n => /^1 lectures are DRM/.test(n)), log.notes);
  check("course: supplementary asset without links fetched on its own", apiLog.some(p => p.includes("/lectures/15/supplementary-assets/903/")), apiLog);
  check("course: no lecture call for the Article without files", !apiLog.some(p => p.includes("/lectures/14/")), apiLog);
  check("course: curriculum asks for the resources", apiLog.some(p => p.includes("subscriber-curriculum-items") && p.includes("supplementary_assets")));

  const name = sandbox.udemyFileName;
  check("name: extension from the file name", name("007", "Lecture", "a.PDF", "https://x/y") === "007 - Lecture.pdf");
  check("name: a dotted title is not taken for an extension", name("008", "Section 1.2", "", "https://x/f.zip?sig=1") === "008 - Section 1.2.zip");
  check("name: no double extension", name("009", "notes.pdf", "notes.pdf", "https://x/y") === "009 - notes.pdf");
  check("name: unsafe characters", name("010", "a/b:c", "a.txt", "https://x/y") === "010 - a b c.txt", name("010", "a/b:c", "a.txt", "https://x/y"));
  check("name: no extension known", name("011", "readme", "", "https://x/blob") === "011 - readme");

  strict = true;
  const old = load();
  const r2 = await old.sandbox.downloadItem({ kind: "course" }, { id: 5, url: `${ORIGIN}/course/my-course/learn/lecture/11#overview`, title: "Udemy" });
  const list2 = (old.log.adds.find(b => b.playlist) || { playlist: { entries: [] } }).playlist.entries;
  check("API refuses the new fields: still sent", r2 && r2.ok === true, r2);
  check("API refuses the new fields: the videos are still there", list2.some(e => e.url === `${CDN}/intro/index.m3u8?sig=a`) && list2.some(e => e.url === `${CDN}/old/720.mp4?sig=f`), list2);

  strict = false;
  foreign = true;
  loopNext = true;
  const other = load();
  const r3 = await other.sandbox.downloadItem({ kind: "course" }, { id: 5, url: `${ORIGIN}/course/my-course/learn/lecture/11#overview`, title: "Udemy" });
  const list3 = (other.log.adds.find(b => b.playlist) || { playlist: { entries: [] } }).playlist.entries;
  check("a repeating next page ends the reading", r3 && r3.ok === true, r3);
  check("a link outside Udemy is dropped", list3.length > 0 && !list3.some(e => /evil\.example/.test(e.url)), list3);
  foreign = false;
  loopNext = false;

  pageHost = "evil.example";
  const fake = load();
  const r4 = await fake.sandbox.downloadItem({ kind: "course" }, { id: 5, url: `${ORIGIN}/course/my-course/learn/lecture/11#overview`, title: "Udemy" });
  check("not a Udemy page: nothing read", r4 && r4.ok === false && !fake.log.adds.some(b => b.playlist), r4);

  console.log(failed ? failed + " FAILED" : "all ok");
  process.exit(failed ? 1 : 0);
})();
