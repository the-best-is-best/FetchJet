import { DatabaseSync } from "node:sqlite";
import assert from "node:assert/strict";
import vm from "node:vm";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import worker from "../worker.js";

const NOW = Date.parse("2026-11-22T12:00:00Z");
const realNow = Date.now;
Date.now = () => NOW;
const DAY = 86400000;
const dayOf = (back) => new Date(NOW - back * DAY).toISOString().slice(0, 10);

const db = new DatabaseSync(":memory:");
db.exec(fs.readFileSync(new URL("../schema.sql", import.meta.url), "utf8"));
const insert = db.prepare("INSERT INTO pings (day, install_id, version, platform, country) VALUES (?, ?, ?, ?, ?)");

const env = {
  STATS_TOKEN: "secret",
  DB: {
    prepare(sql) {
      const st = db.prepare(sql);
      let args = [];
      const q = {
        bind(...a) { args = a; return q; },
        async all() { return { results: st.all(...args) }; },
        async first() { return st.get(...args) || null; },
        async run() { st.run(...args); return {}; },
      };
      return q;
    },
  },
};

const PLATFORMS = ["windows", "macos", "linux", "android", "ios"];
const model = [];
let n = 0;
function addInstall(firstBack, extraBacks) {
  const idx = n++;
  const id = "install" + String(idx).padStart(10, "0");
  const backs = [firstBack, ...extraBacks].filter((b) => b >= 0);
  backs.forEach((b) => {
    let version = ["1.5.0", "1.6.0", "1.10.0"][idx % 3];
    if (idx % 7 === 0) version = b >= 4 ? "1.5.0" : "1.10.0";
    const country = idx < 12 ? "ZZ" + (idx % 2 ? "" : "") : idx % 2 ? "EG" : "US";
    const row = { day: dayOf(b), id, version, platform: PLATFORMS[idx % 5], country: idx < 3 ? "KW" : country, back: b };
    model.push(row);
    insert.run(row.day, id, version, row.platform, row.country);
  });
  return id;
}

const cohortSpec = [];
for (let back = 1; back <= 20; back++) {
  const size = back === 9 ? 3 : 20;
  const d1 = back === 9 ? 3 : 10 + (back % 5);
  const d7 = Math.min(size, 4 + (back % 3));
  cohortSpec.push({ back, size, d1, d7 });
  for (let i = 0; i < size; i++) {
    const extra = [];
    if (i < d1) extra.push(back - 1);
    if (i < d7) extra.push(back - 7);
    if (i >= 12 && i < 18) extra.push(back - 3);
    if (i >= 18) extra.push(back - 2, back - 5);
    addInstall(back, extra.filter((b) => b >= 0 && b < back));
  }
}
const todayOnly = [];
for (let i = 0; i < 7; i++) todayOnly.push(addInstall(0, []));
const total = n;

const get = async (url) => {
  const res = await worker.fetch(new Request("https://x.test" + url), env);
  return { status: res.status, headers: res.headers, body: await res.json() };
};

const byDay = (rows) => {
  const m = {};
  rows.forEach((r) => ((m[r.day] = m[r.day] || new Set()).add(r.id)));
  return m;
};
const first = {};
model.forEach((r) => { if (!first[r.id] || r.day < first[r.id]) first[r.id] = r.day; });
const active = byDay(model);

const tests = [];
const test = (name, fn) => tests.push([name, fn]);

test("rows are as designed", () => {
  assert.equal(total, 20 * 20 - 17 + 7);
  assert.equal(db.prepare("SELECT COUNT(DISTINCT install_id) AS c FROM pings").get().c, total);
});

for (const days of [7, 30, 90]) {
  test("perDay new + returning = active, new matches first day (days=" + days + ")", async () => {
    const { body } = await get("/public?days=" + days);
    assert.ok(body.perDay.length > 0);
    body.perDay.forEach((d) => {
      assert.equal(d.new + d.returning, d.users);
      assert.equal(d.users, active[d.day].size);
      const expectNew = [...active[d.day]].filter((id) => first[id] === d.day).length;
      assert.equal(d.new, expectNew, d.day);
    });
    const since = dayOf(days - 1);
    assert.equal(body.perDay.length, Object.keys(active).filter((d) => d >= since).length);
    assert.equal(body.perDay.find((d) => d.day === dayOf(0)).new, 7);
    assert.equal(body.perDay.find((d) => d.day === dayOf(0)).returning, body.perDay.find((d) => d.day === dayOf(0)).users - 7);
  });
}

test("first day uses the whole table, not the range", async () => {
  const { body } = await get("/public?days=1");
  assert.equal(body.perDay.length, 1);
  const today = body.perDay[0];
  assert.equal(today.new, 7);
  assert.ok(today.returning > 0);
});

test("retention D1 and D7 exact (pooled over eligible cohorts)", async () => {
  const { body } = await get("/stats?token=secret&days=30");
  const d1c = cohortSpec.filter((c) => c.back >= 1);
  const d7c = cohortSpec.filter((c) => c.back >= 7);
  const rate = (arr, f) => Math.round((arr.reduce((a, c) => a + c[f], 0) / arr.reduce((a, c) => a + c.size, 0)) * 1000) / 10;
  assert.equal(body.retention.d1.size, d1c.reduce((a, c) => a + c.size, 0) + 0);
  assert.equal(body.retention.d1.returned, d1c.reduce((a, c) => a + c.d1, 0));
  assert.equal(body.retention.d1.rate, rate(d1c, "d1"));
  assert.equal(body.retention.d7.size, d7c.reduce((a, c) => a + c.size, 0));
  assert.equal(body.retention.d7.returned, d7c.reduce((a, c) => a + c.d7, 0));
  assert.equal(body.retention.d7.rate, rate(d7c, "d7"));
  // independent check straight from the model: "exactly on D+7", not "within 7 days"
  let r7 = 0, s7 = 0;
  Object.entries(first).forEach(([id, f]) => {
    if (f > dayOf(7)) return;
    s7++;
    const d7 = new Date(Date.parse(f) + 7 * DAY).toISOString().slice(0, 10);
    if (active[d7] && active[d7].has(id)) r7++;
  });
  assert.equal(body.retention.d7.returned, r7);
  assert.equal(body.retention.d7.size, s7);
});

test("cohort list: today has D1/D7 null, D7 null for young cohorts, private keeps small cohort", async () => {
  const { body } = await get("/stats?token=secret&days=30");
  assert.equal(body.cohorts.length, 10);
  const today = body.cohorts.find((c) => c.day === dayOf(0));
  assert.deepEqual(today, { day: dayOf(0), size: 7, d1: null, d7: null });
  const y = body.cohorts.find((c) => c.day === dayOf(1));
  assert.equal(y.d1, cohortSpec.find((c) => c.back === 1).d1);
  assert.equal(y.d7, null);
  const all = await get("/stats?token=secret&days=90");
  assert.equal(all.body.cohorts.length, 10);
});

test("public view hides cohorts under 5 but keeps them in the average", async () => {
  const pub = (await get("/public?days=30")).body;
  const priv = (await get("/stats?token=secret&days=30")).body;
  assert.ok(pub.cohorts.every((c) => c.size >= 5));
  assert.ok(!pub.cohorts.some((c) => c.day === dayOf(9)));
  assert.deepEqual(pub.retention, priv.retention);
  const withoutSmall = cohortSpec.filter((c) => c.back >= 1 && c.size >= 5);
  const r = Math.round((withoutSmall.reduce((a, c) => a + c.d1, 0) / withoutSmall.reduce((a, c) => a + c.size, 0)) * 1000) / 10;
  assert.notEqual(pub.retention.d1.rate, r);
  assert.equal(pub.retention.d1.cohorts, 20);
});

test("the dash case: no cohort old enough", async () => {
  const saved = db.prepare("SELECT * FROM pings").all();
  db.exec("DELETE FROM pings");
  for (let b = 3; b >= 0; b--) for (let i = 0; i < 6; i++) insert.run(dayOf(b), "fresh" + String(i + 100 * b).padStart(12, "0"), "1.6.0", "windows", "EG");
  const { body } = await get("/public?days=30");
  assert.equal(body.retention.d7, null);
  assert.equal(body.retention.d1.size, 18);
  assert.equal(body.retention.d1.returned, 0);
  db.exec("DELETE FROM pings");
  saved.forEach((r) => insert.run(r.day, r.install_id, r.version, r.platform, r.country));
});

test("empty table does not fail", async () => {
  const saved = db.prepare("SELECT * FROM pings").all();
  db.exec("DELETE FROM pings");
  const { status, body } = await get("/public?days=30");
  assert.equal(status, 200);
  assert.equal(body.users, 0);
  assert.equal(body.retention.d1, null);
  assert.equal(body.stickiness.rate, null);
  db.exec("DELETE FROM pings");
  saved.forEach((r) => insert.run(r.day, r.install_id, r.version, r.platform, r.country));
});

test("stickiness: last complete day over the 7 days ending that day", async () => {
  const { body } = await get("/public?days=30");
  const y = dayOf(1);
  const week = new Set();
  for (let b = 1; b <= 7; b++) (active[dayOf(b)] || new Set()).forEach((id) => week.add(id));
  assert.equal(body.stickiness.day, y);
  assert.equal(body.stickiness.dau, active[y].size);
  assert.equal(body.stickiness.wau, week.size);
  assert.equal(body.stickiness.rate, Math.round((active[y].size / week.size) * 1000) / 10);
  assert.ok(![...active[dayOf(0)]].every((id) => week.has(id)), "today-only installs must not count");
});

test("version adoption: latest version per install in the last 7 days, newest first, sums to users7", async () => {
  const { body } = await get("/public?days=30");
  const latest = {};
  model.filter((r) => r.back <= 6).forEach((r) => {
    if (!latest[r.id] || r.back < latest[r.id].back) latest[r.id] = r;
  });
  const counts = {};
  Object.values(latest).forEach((r) => (counts[r.version] = (counts[r.version] || 0) + 1));
  assert.deepEqual(body.versionAdoption.map((v) => v.key), ["1.10.0", "1.6.0", "1.5.0"]);
  body.versionAdoption.forEach((v) => assert.equal(v.users, counts[v.key], v.key));
  assert.equal(body.users7, Object.keys(latest).length);
  assert.equal(body.versionAdoption.reduce((a, v) => a + v.users, 0), body.users7);
});

test("old fields and privacy of countries are unchanged", async () => {
  const pub = (await get("/public?days=30")).body;
  const priv = (await get("/stats?token=secret&days=30")).body;
  assert.equal(pub.users, total);
  assert.ok(pub.byCountry.every((c) => c.users >= 5));
  assert.ok(priv.byCountry.some((c) => c.users < 5));
  assert.equal(pub.public, true);
  assert.equal(priv.public, undefined);
  assert.equal(pub.countryCount, priv.byCountry.filter((c) => c.key !== "XX").length);
  assert.equal(pub.byPlatform.reduce((a, p) => a + p.users, 0), total);
  assert.equal((await get("/stats?token=wrong")).status, 401);
  assert.equal((await get("/stats")).status, 401);
});

test("cache and CORS headers on /public", async () => {
  const { headers } = await get("/public?days=7");
  assert.equal(headers.get("cache-control"), "public, max-age=300");
  assert.equal(headers.get("access-control-allow-origin"), "*");
});

test("ping still inserts", async () => {
  const res = await worker.fetch(new Request("https://x.test/ping", { method: "POST", body: JSON.stringify({ id: "abcdefghijklmnop1234", version: "1.6.0", platform: "windows" }) }), env);
  assert.equal(res.status, 200);
  db.prepare("DELETE FROM pings WHERE install_id = 'abcdefghijklmnop1234'").run();
});

async function pageHtml() {
  const res = await worker.fetch(new Request("https://x.test/dashboard"), env);
  return res.text();
}

function scriptOf(html) {
  return html.slice(html.indexOf("<script>") + 8, html.indexOf("</script>"));
}

test("dashboard script parses (node --check)", async () => {
  const file = path.join(os.tmpdir(), "fj-dashboard-check.js");
  fs.writeFileSync(file, scriptOf(await pageHtml()));
  execFileSync(process.execPath, ["--check", file]);
});

function runPage(html, data, search) {
  function node(tag) {
    return { tag, className: "", textContent: "", children: [], style: {}, title: "", href: "",
      appendChild(c) { this.children.push(c); return c; } };
  }
  const app = node("main");
  const doc = {
    getElementById: () => app,
    createElement: node,
    createTextNode: (t) => ({ tag: "#text", textContent: t, children: [] }),
  };
  const errors = [];
  const ctx = {
    document: doc, location: { search: search || "" }, Intl, Date, Math, String, Number, parseInt, URLSearchParams, encodeURIComponent,
    fetch: async () => ({ status: 200, json: async () => data }),
    console,
  };
  vm.runInNewContext(scriptOf(html), ctx);
  return new Promise((resolve) => setTimeout(() => resolve({ app, errors }), 30));
}

function textOf(n) {
  return (n.textContent || "") + " " + (n.children || []).map(textOf).join(" ");
}

test("dashboard renders new/returning chart, retention cards, stickiness, adoption with no JS error", async () => {
  const html = await pageHtml();
  const data = (await get("/public?days=30")).body;
  const { app } = await runPage(html, data, "?days=30");
  const t = textOf(app);
  for (const s of ["جدد", "راجعين", "الاحتفاظ بعد يوم", "الاحتفاظ بعد 7 أيام", "النشاط اليومي ÷ الأسبوعي", "انتشار الإصدار", "إحصائيات FetchJet"]) {
    assert.ok(t.includes(s), s);
  }
  assert.ok(!t.includes("حصل خطأ"), "page showed the error box");
  const find = (n, cls, out = []) => { if ((n.className || "").split(" ").includes(cls)) out.push(n); (n.children || []).forEach((c) => find(c, cls, out)); return out; };
  assert.equal(find(app, "col").length, 30);
  const v = find(app, "row");
  assert.ok(v.length > 0);
});

test("dashboard shows the dash and hint when D7 is missing", async () => {
  const html = await pageHtml();
  const data = (await get("/public?days=30")).body;
  data.retention.d7 = null;
  const { app } = await runPage(html, data, "?days=30");
  assert.ok(textOf(app).includes("محتاج 7 أيام من البيانات"));
  assert.ok(textOf(app).includes("—"));
});

test("dashboard renders old-shaped data (no new fields) without errors", async () => {
  const html = await pageHtml();
  const data = (await get("/public?days=30")).body;
  ["retention", "cohorts", "stickiness", "versionAdoption", "users7"].forEach((k) => delete data[k]);
  data.perDay = data.perDay.map((d) => ({ day: d.day, users: d.users }));
  const { app } = await runPage(html, data, "?days=30");
  assert.ok(!textOf(app).includes("حصل خطأ"));
});

if (process.argv.includes("--dump")) {
  const out = process.argv[process.argv.indexOf("--dump") + 1];
  fs.writeFileSync(out, JSON.stringify((await get("/public?days=30")).body));
}

let failed = 0;
for (const [name, fn] of tests) {
  try { await fn(); console.log("ok   " + name); } catch (e) { failed++; console.log("FAIL " + name + "\n" + (e.stack || e)); }
}
Date.now = realNow;
console.log(failed ? failed + " failed" : "all " + tests.length + " passed");
process.exit(failed ? 1 : 0);
