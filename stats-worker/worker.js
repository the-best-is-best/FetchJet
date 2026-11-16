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
.chart div{flex:1;background:var(--accent);border-radius:3px 3px 0 0;min-height:2px;opacity:.9}
.chart div.zero{background:var(--bar);opacity:1}
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
var max = list[0].users || 1;
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
var map = {};
perDay.forEach(function (d) { map[d.day] = d.users; });
var list = [];
for (var i = days - 1; i >= 0; i--) {
var d = new Date(Date.now() - i * 86400000).toISOString().slice(0, 10);
list.push({day: d, users: map[d] || 0});
}
var max = Math.max.apply(null, list.map(function (x) { return x.users; })) || 1;
var box = el("div", "chart");
list.forEach(function (x) {
var b = el("div", x.users ? "" : "zero");
b.style.height = x.users ? Math.max(4, Math.round(x.users / max * 100)) + "%" : "2px";
b.title = x.day + ": " + x.users;
box.appendChild(b);
});
s.appendChild(box);
var ax = el("div", "axis");
ax.appendChild(el("span", "", list[0].day));
ax.appendChild(el("span", "", "أعلى يوم: " + max));
ax.appendChild(el("span", "", list[list.length - 1].day));
s.appendChild(ax);
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
app.appendChild(chart(d.perDay));
app.appendChild(bars("الدول", "حسب عدد الأجهزة النشطة", d.byCountry, d.users, countryName));
var two = el("div", "two");
two.appendChild(bars("المنصات", "ويندوز وماك وأندرويد وغيرهم", d.byPlatform, d.users, function (k) { return NAMES[k] || k; }));
two.appendChild(bars("الإصدارات", "مين لسه على إصدار قديم", d.byVersion, d.users, function (k) { return "الإصدار " + k; }));
app.appendChild(two);
var noteText = "الجهاز بيتحسب مرة واحدة في اليوم مهما فتح التطبيق. مفيش أسماء ولا عناوين IP بتتخزن.";
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
  const country = (request.cf && request.cf.country) || "XX";
  const day = new Date().toISOString().slice(0, 10);
  await env.DB.prepare(
    "INSERT OR IGNORE INTO pings (day, install_id, version, platform, country) VALUES (?, ?, ?, ?, ?)"
  )
    .bind(day, id, version, platform, country)
    .run();
  return json({ ok: true });
}

async function aggregate(env, days) {
  const since = new Date(Date.now() - (days - 1) * 86400000).toISOString().slice(0, 10);
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
  const perDay = (
    await env.DB.prepare(
      "SELECT day, COUNT(*) AS users FROM pings WHERE day >= ? GROUP BY day ORDER BY day"
    )
      .bind(since)
      .all()
  ).results;
  return {
    days,
    users: total.users,
    byCountry: await group("country"),
    byPlatform: await group("platform"),
    byVersion: await group("version"),
    perDay,
  };
}

async function stats(request, env) {
  const url = new URL(request.url);
  if (!env.STATS_TOKEN || url.searchParams.get("token") !== env.STATS_TOKEN) {
    return json({ error: "unauthorized" }, 401);
  }
  return json(await aggregate(env, parseDays(url)));
}

async function publicStats(request, env) {
  const data = await aggregate(env, parseDays(new URL(request.url)));
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
