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

  let appTheme = "";
  const onExtensionPage = () => typeof document !== "undefined" && document.documentElement
    && typeof location !== "undefined" && /^[a-z-]+-extension:$/.test(location.protocol);
  const applyTheme = () => {
    if (!onExtensionPage()) return;
    let theme = appTheme;
    if (!theme) {
      try { theme = globalThis.matchMedia && globalThis.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark"; } catch (_) { theme = "dark"; }
    }
    document.documentElement.setAttribute("data-theme", theme);
  };
  globalThis.fjUseTheme = (theme) => {
    appTheme = theme === "light" || theme === "dark" ? theme : "";
    applyTheme();
  };
  applyTheme();
  try {
    if (onExtensionPage() && globalThis.matchMedia) globalThis.matchMedia("(prefers-color-scheme: light)").addEventListener("change", applyTheme);
  } catch (_) {}

  try {
    const got = extApi && extApi.storage && extApi.storage.local.get(["appLanguage", "appTheme"]);
    if (got && got.then) got.then(r => { fjUseLanguage(r && r.appLanguage); fjUseTheme(r && r.appTheme); }).catch(() => {});
    extApi.storage.onChanged.addListener((changes, area) => {
      if (area !== "local") return;
      if (changes.appLanguage) fjUseLanguage(changes.appLanguage.newValue);
      if (changes.appTheme) fjUseTheme(changes.appTheme.newValue);
    });
  } catch (_) {}
})();
