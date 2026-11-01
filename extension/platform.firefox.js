const FJ_PLATFORM = (() => {
  const mobile = /android/i.test(navigator.userAgent);
  return {
    name: "firefox",
    hidden: mobile ? ["rowCaptureDownloads", "rowMinSize", "rowHoldKey", "cardShortcuts"] : [],
    shortcutsPage: "",
    shortcutsText: "Change them at Add-ons → ⚙ → Manage Extension Shortcuts.",
    cookiesNote: mobile ? "Needed for downloads behind a login. Cookies go only to the FetchJet app on this phone (127.0.0.1)." : "",
    status: "",
    qualitiesInPopup: true,
    hint: mobile ? "" : null,
  };
})();
