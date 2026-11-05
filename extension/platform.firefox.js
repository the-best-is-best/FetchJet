const FJ_PLATFORM = (() => {
  const mobile = /android/i.test(navigator.userAgent);
  return {
    name: "firefox",
    hidden: mobile ? ["rowCaptureDownloads", "rowMinSize", "rowHoldKey", "cardShortcuts"] : [],
    shortcutsPage: "",
    shortcutsText: "optShortcutsFirefox",
    cookiesNote: mobile ? "optCookiesHintPhone" : "",
    status: "",
    qualitiesInPopup: true,
    hint: mobile ? "" : null,
  };
})();
