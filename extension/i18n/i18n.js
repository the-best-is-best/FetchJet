if (typeof globalThis.fjT !== "function") (() => {
  const STRINGS = {};
  const KNOWN = ["en", "ar", "fr", "de", "es", "pt", "it", "ru", "tr", "id", "zh", "ja", "ko", "hi", "fa"];
  const REASONS = {
    "unsupported url": "reasonUnsupportedUrl",
    "could not open FetchJet": "reasonCouldNotOpen",
    "app not running": "reasonNotRunning",
    "app did not start": "reasonDidNotStart",
    "no links": "reasonNoLinks",
    "refused": "reasonRefused",
    "no answer": "reasonNoAnswer",
    "pairing required": "errNeedsPairing"
  };
  const extApi = typeof browser !== "undefined" ? browser : (typeof chrome !== "undefined" ? chrome : null);
  const pick = (code) => {
    const c = String(code || "").toLowerCase().split(/[-_]/)[0];
    return KNOWN.includes(c) ? c : null;
  };
  let lang = pick(typeof navigator !== "undefined" && navigator.language) || "en";
  const listeners = [];

  globalThis.fjAddLanguage = (code, table) => { STRINGS[code] = table; };
  globalThis.fjT = (key, vars) => {
    const s = (STRINGS[lang] && STRINGS[lang][key]) || STRINGS.en[key] || key;
    return vars ? s.replace(/\{(\w+)\}/g, (m, k) => (vars[k] != null ? String(vars[k]) : "")) : s;
  };
  globalThis.fjReason = (reason) => {
    if (!reason) return fjT("reasonUnknown");
    return REASONS[reason] ? fjT(REASONS[reason]) : String(reason);
  };
  globalThis.fjIsRtl = () => lang === "ar" || lang === "fa";
  globalThis.fjLanguage = () => lang;
  globalThis.fjOnLanguage = (cb) => { listeners.push(cb); };
  globalThis.fjUseLanguage = (code) => {
    const c = pick(code);
    if (!c || c === lang) return;
    lang = c;
    for (const cb of listeners) { try { cb(c); } catch (_) {} }
  };
  globalThis.fjApplyTexts = (root) => {
    if (!root || !root.querySelectorAll) return;
    root.querySelectorAll("[data-i18n]").forEach(el => { el.textContent = fjT(el.getAttribute("data-i18n")); });
    root.querySelectorAll("[data-i18n-title]").forEach(el => { el.title = fjT(el.getAttribute("data-i18n-title")); });
    if (root.documentElement) {
      root.documentElement.lang = lang;
      root.documentElement.dir = fjIsRtl() ? "rtl" : "ltr";
    }
  };

  try {
    const got = extApi && extApi.storage && extApi.storage.local.get("appLanguage");
    if (got && got.then) got.then(r => fjUseLanguage(r && r.appLanguage)).catch(() => {});
    extApi.storage.onChanged.addListener((changes, area) => {
      if (area === "local" && changes.appLanguage) fjUseLanguage(changes.appLanguage.newValue);
    });
  } catch (_) {}
})();
