const PLATFORMS = ["windows", "macos", "linux", "android", "ios"];

const PAGE = `<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>إحصائيات FetchJet</title>
<style>
:root{--bg:#f6f7fb;--card:#fff;--text:#161a2b;--muted:#6a7090;--line:#e4e7f1;--accent:#6c4cf1;--accent2:#b9a8ff;--bar:#ece9ff}
@media (prefers-color-scheme: dark){:root{--bg:#0d1020;--card:#161a2e;--text:#eef0fb;--muted:#9aa1c4;--line:#252a45;--accent:#8f78ff;--accent2:#5a46c9;--bar:#232846}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,"Segoe UI",Tahoma,sans-serif;line-height:1.5}
main{max-width:900px;margin:0 auto;padding:20px 16px 48px}
header{display:flex;flex-wrap:wrap;gap:12px;align-items:center;justify-content:space-between;margin-bottom:20px}
h1{font-size:22px;margin:0}
.tabs{display:flex;gap:6px}
.tabs a{padding:6px 14px;border-radius:999px;border:1px solid var(--line);color:var(--muted);text-decoration:none;font-size:14px;background:var(--card)}
.tabs a.on{background:var(--accent);border-color:var(--accent);color:#fff}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:12px;margin-bottom:16px}
.card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px}
.card .k{color:var(--muted);font-size:13px}
.card .v{font-size:30px;font-weight:700;margin-top:4px}
.card .s{color:var(--muted);font-size:12px;margin-top:2px}
section{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px;margin-bottom:16px}
h2{font-size:16px;margin:0 0 4px}
.hint{color:var(--muted);font-size:12px;margin:0 0 14px}
.chart{display:flex;align-items:flex-end;gap:2px;height:140px;direction:ltr}
.chart .col{flex:1;height:100%;display:flex;flex-direction:column-reverse}
.chart .col div{width:100%;min-height:2px}
.chart .col .rt{background:var(--accent2)}
.chart .col .nw{background:var(--accent);border-radius:3px 3px 0 0}
.chart .col .zero{background:var(--bar);height:2px}
.legend{display:flex;gap:14px;font-size:12px;color:var(--muted);margin:0 0 10px}
.legend i{display:inline-block;width:10px;height:10px;border-radius:3px;margin-inline-end:5px;vertical-align:middle}
.axis{display:flex;justify-content:space-between;color:var(--muted);font-size:11px;margin-top:6px;direction:ltr}
.row{display:grid;grid-template-columns:minmax(110px,170px) 1fr 74px;gap:10px;align-items:center;padding:6px 0}
.row .n{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.row .t{height:10px;background:var(--bar);border-radius:999px;overflow:hidden}
.row .t i{display:block;height:100%;background:linear-gradient(90deg,var(--accent2),var(--accent));border-radius:999px}
.row .c{text-align:left;color:var(--muted);font-size:13px;direction:ltr}
.two{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:16px}
.two section{margin:0}
.empty{text-align:center;color:var(--muted);padding:40px 12px}
.note{color:var(--muted);font-size:12px;text-align:center;margin-top:8px}
</style>
</head>
<body>
<main id="app"><div class="empty">جاري التحميل...</div></main>
<script>
(function () {
var qs = new URLSearchParams(location.search);
var token = qs.get("token") || "";
var days = parseInt(qs.get("days") || "30", 10) || 30;
var app = document.getElementById("app");
var NAMES = {windows: "ويندوز", macos: "ماك", linux: "لينكس", android: "أندرويد", ios: "آيفون / iOS"};
function el(tag, cls, text) {
var e = document.createElement(tag);
if (cls) e.className = cls;
if (text !== undefined) e.textContent = text;
return e;
}
function flag(code) {
if (!/^[A-Z]{2}$/.test(code) || code === "XX") return "";
return String.fromCodePoint(127397 + code.charCodeAt(0), 127397 + code.charCodeAt(1)) + " ";
}
function countryName(code) {
if (code === "XX" || code === "T1") return "غير معروف";
try { return flag(code) + (new Intl.DisplayNames(["ar"], {type: "region"}).of(code) || code); } catch (e) { return code; }
}
function fmt(n) { return new Intl.NumberFormat("ar-EG").format(n); }
function card(k, v, s) {
var c = el("div", "card");
c.appendChild(el("div", "k", k));
c.appendChild(el("div", "v", v));
if (s) c.appendChild(el("div", "s", s));
return c;
}
function bars(title, hint, list, total, label) {
var s = el("section");
s.appendChild(el("h2", "", title));
s.appendChild(el("p", "hint", hint));
if (!list.length) { s.appendChild(el("div", "empty", "لسه مفيش بيانات")); return s; }
var max = Math.max.apply(null, list.map(function (x) { return x.users; })) || 1;
list.forEach(function (it) {
var r = el("div", "row");
r.appendChild(el("div", "n", label(it.key)));
var t = el("div", "t");
var i = el("i");
i.style.width = Math.max(3, Math.round(it.users / max * 100)) + "%";
t.appendChild(i);
r.appendChild(t);
r.appendChild(el("div", "c", it.users + " (" + Math.round(it.users / total * 100) + "%)"));
s.appendChild(r);
});
return s;
}
function chart(perDay) {
var s = el("section");
s.appendChild(el("h2", "", "المستخدمين كل يوم"));
s.appendChild(el("p", "hint", "كل عمود = عدد الأجهزة اللي فتحت التطبيق في اليوم ده"));
var lg = el("div", "legend");
[["nw", "var(--accent)", "جدد"], ["rt", "var(--accent2)", "راجعين"]].forEach(function (x) {
var item = el("span");
var sw = el("i");
sw.style.background = x[1];
item.appendChild(sw);
item.appendChild(document.createTextNode(x[2]));
lg.appendChild(item);
});
s.appendChild(lg);
var map = {};
perDay.forEach(function (d) { map[d.day] = d; });
var list = [];
for (var i = days - 1; i >= 0; i--) {
var d = new Date(Date.now() - i * 86400000).toISOString().slice(0, 10);
var m = map[d] || {users: 0, new: 0, returning: 0};
list.push({day: d, users: m.users || 0, nw: m.new || 0, rt: m.returning || 0});
}
var max = Math.max.apply(null, list.map(function (x) { return x.users; })) || 1;
var box = el("div", "chart");
list.forEach(function (x) {
var col = el("div", "col");
if (!x.users) {
col.appendChild(el("div", "zero"));
} else {
var rt = el("div", "rt");
rt.style.height = Math.round(x.rt / max * 100) + "%";
if (!x.rt) rt.style.display = "none";
var nw = el("div", "nw");
nw.style.height = Math.round(x.nw / max * 100) + "%";
if (!x.nw) nw.style.display = "none";
col.appendChild(rt);
col.appendChild(nw);
}
col.title = x.day + ": " + x.users + " (جدد " + x.nw + " / راجعين " + x.rt + ")";
box.appendChild(col);
});
s.appendChild(box);
var ax = el("div", "axis");
ax.appendChild(el("span", "", list[0].day));
ax.appendChild(el("span", "", "أعلى يوم: " + max));
ax.appendChild(el("span", "", list[list.length - 1].day));
s.appendChild(ax);
return s;
}
function pct(v) { return v === null || v === undefined ? "—" : fmt(v) + "%"; }
function cohorts(list) {
var s = el("section");
s.appendChild(el("h2", "", "الاحتفاظ بعد يوم لكل يوم تسجيل"));
s.appendChild(el("p", "hint", "الأجهزة اللي بدأت في اليوم ده، كام منهم رجع تاني تاني يوم. الأيام اللي أقل من 5 أجهزة مخفية."));
if (!list.length) { s.appendChild(el("div", "empty", "لسه مفيش بيانات")); return s; }
list.forEach(function (c) {
var r = el("div", "row");
r.appendChild(el("div", "n", c.day + " · " + c.size));
var t = el("div", "t");
var i = el("i");
i.style.width = c.d1 === null ? "0%" : Math.max(3, Math.round(c.d1 / c.size * 100)) + "%";
t.appendChild(i);
r.appendChild(t);
r.appendChild(el("div", "c", c.d1 === null ? "—" : c.d1 + " (" + Math.round(c.d1 / c.size * 100) + "%)"));
s.appendChild(r);
});
return s;
}
function render(d) {
app.textContent = "";
var h = el("header");
h.appendChild(el("h1", "", "إحصائيات FetchJet"));
var tabs = el("div", "tabs");
[7, 30, 90].forEach(function (n) {
var a = el("a", n === days ? "on" : "", "آخر " + n + " يوم");
a.href = (token ? "?token=" + encodeURIComponent(token) + "&days=" : "?days=") + n;
tabs.appendChild(a);
});
h.appendChild(tabs);
app.appendChild(h);
if (!d.users) {
app.appendChild(el("div", "empty", "لسه مفيش بيانات في الفترة دي. الأرقام بتظهر بعد ما التطبيق يبعت أول إشارة."));
return;
}
var top = d.byCountry[0];
var plat = d.byPlatform[0];
var countryCount = d.countryCount !== undefined ? d.countryCount : d.byCountry.filter(function (c) { return c.key !== "XX"; }).length;
var cards = el("div", "cards");
cards.appendChild(card("مستخدمين نشطين", fmt(d.users), "أجهزة مختلفة في آخر " + days + " يوم"));
cards.appendChild(card("عدد الدول", fmt(countryCount)));
cards.appendChild(top ? card("أكتر دولة", countryName(top.key), top.users + " مستخدم") : card("أكتر دولة", "—", "الدول الصغيرة مخفية"));
cards.appendChild(card("أكتر منصة", NAMES[plat.key] || plat.key, plat.users + " مستخدم"));
app.appendChild(cards);
var ret = d.retention || {};
var stick = d.stickiness;
var more = el("div", "cards");
more.appendChild(card("الاحتفاظ بعد يوم", ret.d1 ? pct(ret.d1.rate) : "—", ret.d1 ? ret.d1.returned + " من " + ret.d1.size + " جهاز جديد" : "لسه مفيش بيانات كفاية"));
more.appendChild(card("الاحتفاظ بعد 7 أيام", ret.d7 ? pct(ret.d7.rate) : "—", ret.d7 ? ret.d7.returned + " من " + ret.d7.size + " جهاز جديد" : "محتاج 7 أيام من البيانات"));
more.appendChild(card("النشاط اليومي ÷ الأسبوعي", stick && stick.rate !== null ? pct(stick.rate) : "—", stick ? stick.dau + " من " + stick.wau + " في آخر 7 أيام (آخر يوم كامل " + stick.day + ")" : ""));
app.appendChild(more);
app.appendChild(chart(d.perDay));
if (d.cohorts) app.appendChild(cohorts(d.cohorts));
app.appendChild(bars("الدول", "حسب عدد الأجهزة النشطة", d.byCountry, d.users, countryName));
var two = el("div", "two");
two.appendChild(bars("المنصات", "ويندوز وماك وأندرويد وغيرهم", d.byPlatform, d.users, function (k) { return NAMES[k] || k; }));
two.appendChild(bars("انتشار الإصدار", "آخر إصدار فتحه كل جهاز في آخر 7 أيام، الأحدث فوق", d.versionAdoption || d.byVersion, d.users7 || d.users, function (k) { return "الإصدار " + k; }));
app.appendChild(two);
var noteText = "الجهاز بيتحسب مرة واحدة في اليوم مهما فتح التطبيق. مفيش أسماء ولا عناوين IP بتتخزن. اليوم الحالي ناقص فمش بيتحسب في الاحتفاظ والنشاط.";
if (d.hiddenCountries) noteText += " الدول اللي أقل من 5 مستخدمين مخفية (" + d.hiddenCountries + ").";
app.appendChild(el("p", "note", noteText));
}
fetch((token ? "/stats?token=" + encodeURIComponent(token) + "&days=" : "/public?days=") + days)
.then(function (r) {
if (r.status === 401) { app.textContent = ""; app.appendChild(el("div", "empty", "التوكن غلط. افتح /dashboard من غير توكن للصفحة العامة.")); return null; }
return r.json();
})
.then(function (d) { if (d) render(d); })
.catch(function () { app.textContent = ""; app.appendChild(el("div", "empty", "حصل خطأ في التحميل")); });
})();
</script>
</body>
</html>`;

function clean(value, max) {
  return String(value || "").replace(/[^\w.\-]/g, "").slice(0, max);
}

const MIN_COUNTRY = 5;

const RELEASES_URL = "https://api.github.com/repos/the-best-is-best/FetchJet/releases?per_page=100";
const RELEASES_TTL_MS = 3600000;
let releasesCache = { at: 0, versions: null };

async function publishedVersions(env) {
  if (releasesCache.versions && Date.now() - releasesCache.at < RELEASES_TTL_MS) return releasesCache.versions;
  try {
    const headers = { "user-agent": "FetchJet-stats", accept: "application/vnd.github+json" };
    if (env.GITHUB_TOKEN) headers.authorization = "Bearer " + env.GITHUB_TOKEN;
    const res = await fetch(RELEASES_URL, { headers });
    if (!res.ok) return releasesCache.versions;
    const list = await res.json();
    const versions = new Set(
      list.filter((r) => r && !r.draft && typeof r.tag_name === "string").map((r) => r.tag_name.replace(/^v/i, ""))
    );
    String(env.EXTRA_VERSIONS || "")
      .split(",")
      .map((v) => v.trim())
      .filter(Boolean)
      .forEach((v) => versions.add(v));
    releasesCache = { at: Date.now(), versions };
    return versions;
  } catch (e) {
    return releasesCache.versions;
  }
}

async function isPublishedVersion(env, version) {
  if (env.ALLOW_ANY_VERSION === "1") return true;
  const versions = await publishedVersions(env);
  return !versions || versions.has(version);
}

function json(body, status, extra) {
  return new Response(JSON.stringify(body), {
    status: status || 200,
    headers: Object.assign({ "content-type": "application/json" }, extra || {}),
  });
}

function parseDays(url) {
  return Math.min(Math.max(parseInt(url.searchParams.get("days") || "30", 10) || 30, 1), 365);
}

async function ping(request, env) {
  let body;
  try {
    body = await request.json();
  } catch (e) {
    return json({ error: "bad json" }, 400);
  }
  const id = clean(body.id, 64);
  const version = clean(body.version, 16);
  const platform = clean(body.platform, 16).toLowerCase();
  if (id.length < 16 || !version || !PLATFORMS.includes(platform)) {
    return json({ error: "bad fields" }, 400);
  }
  if (!(await isPublishedVersion(env, version))) return json({ ok: true });
  const country = (request.cf && request.cf.country) || "XX";
  const day = new Date().toISOString().slice(0, 10);
  await env.DB.prepare(
    "INSERT OR IGNORE INTO pings (day, install_id, version, platform, country) VALUES (?, ?, ?, ?, ?)"
  )
    .bind(day, id, version, platform, country)
    .run();
  return json({ ok: true });
}

const DAY_MS = 86400000;
const FIRST_SEEN = "SELECT install_id, MIN(day) AS first FROM pings GROUP BY install_id";

function dayString(back) {
  return new Date(Date.now() - back * DAY_MS).toISOString().slice(0, 10);
}

function versionParts(v) {
  return String(v).split(/[^0-9]+/).filter(Boolean).map(Number);
}

function newestFirst(a, b) {
  const x = versionParts(a.key);
  const y = versionParts(b.key);
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    const d = (y[i] || 0) - (x[i] || 0);
    if (d) return d;
  }
  return 0;
}

function pooled(list, field) {
  const size = list.reduce((n, c) => n + c.size, 0);
  if (!size) return null;
  const returned = list.reduce((n, c) => n + c[field], 0);
  return { rate: Math.round((returned / size) * 1000) / 10, returned, size, cohorts: list.length };
}

async function aggregate(env, days, publicView) {
  const since = dayString(days - 1);
  const group = async (column) =>
    (
      await env.DB.prepare(
        `SELECT ${column} AS key, COUNT(DISTINCT install_id) AS users FROM pings WHERE day >= ? GROUP BY ${column} ORDER BY users DESC`
      )
        .bind(since)
        .all()
    ).results;
  const total = await env.DB.prepare(
    "SELECT COUNT(DISTINCT install_id) AS users FROM pings WHERE day >= ?"
  )
    .bind(since)
    .first();
  const perDayRows = (
    await env.DB.prepare(
      `SELECT p.day AS day, COUNT(*) AS users, SUM(CASE WHEN f.first = p.day THEN 1 ELSE 0 END) AS new FROM pings p JOIN (${FIRST_SEEN}) f ON f.install_id = p.install_id WHERE p.day >= ? GROUP BY p.day ORDER BY p.day`
    )
      .bind(since)
      .all()
  ).results;
  const perDay = perDayRows.map((r) => ({ day: r.day, users: r.users, new: r.new, returning: r.users - r.new }));

  const cohortSince = dayString(Math.max(days, 30) - 1);
  const yesterday = dayString(1);
  const weekAgo = dayString(7);
  const cohortRows = (
    await env.DB.prepare(
      `SELECT f.first AS day, COUNT(*) AS size,
        SUM(CASE WHEN EXISTS (SELECT 1 FROM pings x WHERE x.install_id = f.install_id AND x.day = date(f.first, '+1 day')) THEN 1 ELSE 0 END) AS d1,
        SUM(CASE WHEN EXISTS (SELECT 1 FROM pings x WHERE x.install_id = f.install_id AND x.day = date(f.first, '+7 day')) THEN 1 ELSE 0 END) AS d7
       FROM (${FIRST_SEEN}) f WHERE f.first >= ? GROUP BY f.first ORDER BY f.first`
    )
      .bind(cohortSince)
      .all()
  ).results;
  const cohortList = cohortRows.map((c) => ({
    day: c.day,
    size: c.size,
    d1: c.day <= yesterday ? c.d1 : null,
    d7: c.day <= weekAgo ? c.d7 : null,
  }));
  const retention = {
    d1: pooled(cohortList.filter((c) => c.d1 !== null), "d1"),
    d7: pooled(cohortList.filter((c) => c.d7 !== null), "d7"),
  };
  let shownCohorts = cohortList;
  if (publicView) shownCohorts = shownCohorts.filter((c) => c.size >= MIN_COUNTRY);
  shownCohorts = shownCohorts.slice(-10);

  const active = await env.DB.prepare(
    "SELECT COUNT(DISTINCT CASE WHEN day = ? THEN install_id END) AS dau, COUNT(DISTINCT install_id) AS wau FROM pings WHERE day >= ? AND day <= ?"
  )
    .bind(yesterday, dayString(7), yesterday)
    .first();
  const stickiness = {
    day: yesterday,
    dau: active.dau,
    wau: active.wau,
    rate: active.wau ? Math.round((active.dau / active.wau) * 1000) / 10 : null,
  };

  const recent = dayString(6);
  const versionAdoption = (
    await env.DB.prepare(
      "SELECT p.version AS key, COUNT(*) AS users FROM pings p JOIN (SELECT install_id, MAX(day) AS last FROM pings WHERE day >= ? GROUP BY install_id) m ON m.install_id = p.install_id AND m.last = p.day GROUP BY p.version"
    )
      .bind(recent)
      .all()
  ).results.sort(newestFirst);
  const users7 = versionAdoption.reduce((n, v) => n + v.users, 0);

  return {
    days,
    users: total.users,
    byCountry: await group("country"),
    byPlatform: await group("platform"),
    byVersion: await group("version"),
    perDay,
    retention,
    cohorts: shownCohorts,
    stickiness,
    versionAdoption,
    users7,
  };
}

async function stats(request, env) {
  const url = new URL(request.url);
  if (!env.STATS_TOKEN || url.searchParams.get("token") !== env.STATS_TOKEN) {
    return json({ error: "unauthorized" }, 401);
  }
  return json(await aggregate(env, parseDays(url), false));
}

async function publicStats(request, env) {
  const data = await aggregate(env, parseDays(new URL(request.url)), true);
  data.countryCount = data.byCountry.filter((c) => c.key !== "XX").length;
  const shown = data.byCountry.filter((c) => c.users >= MIN_COUNTRY);
  data.hiddenCountries = data.byCountry.length - shown.length;
  data.byCountry = shown;
  data.public = true;
  return json(data, 200, {
    "access-control-allow-origin": "*",
    "cache-control": "public, max-age=300",
  });
}

export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (path === "/ping" && request.method === "POST") return ping(request, env);
    if (path === "/stats" && request.method === "GET") return stats(request, env);
    if (path === "/public" && request.method === "GET") return publicStats(request, env);
    if (path === "/dashboard" && request.method === "GET") {
      return new Response(PAGE, {
        headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" },
      });
    }
    return json({ error: "not found" }, 404);
  },
};
