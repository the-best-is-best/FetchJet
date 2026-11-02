const FJ_PLATFORM = (() => {
  const ua = navigator.userAgent;
  const mobile = /android/i.test(ua);
  const page = /Edg\//.test(ua) ? "edge://extensions/shortcuts"
    : /OPR\//.test(ua) ? "opera://extensions/shortcuts"
    : /Vivaldi/.test(ua) ? "vivaldi://extensions/shortcuts"
    : navigator.brave ? "brave://extensions/shortcuts"
    : "chrome://extensions/shortcuts";
  return {
    name: "chrome",
    hidden: mobile ? ["rowHoldKey", "cardShortcuts"] : [],
    shortcutsPage: page,
    shortcutsText: "",
    cookiesNote: mobile ? "optCookiesHintPhone" : "",
    status: "",
    qualitiesInPopup: true,
    hint: mobile ? "" : null,
  };
})();
