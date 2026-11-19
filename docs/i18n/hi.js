window.FJ_PAGE_TEXT = window.FJ_PAGE_TEXT || {};
window.FJ_PAGE_TEXT.hi = {
  pageTitle: "FetchJet डाउनलोड", title: "FetchJet डाउनलोड", subtitle: "हर प्लेटफ़ॉर्म के लिए हर वर्ज़न। अपना चुनें।",
  loading: "लोड हो रहा है…", loadingReleases: "GitHub से रिलीज़ लोड हो रही हैं…",
  footer: 'फ़ाइलें <a href="{releases}">GitHub Releases</a> पर प्रकाशित होती हैं। Android: अपने CPU के लिए APK इंस्टॉल करें (पक्का न हो तो <em>arm64-v8a</em>)। iPhone और iPad: दोनों के लिए एक IPA, साइडलोड करने के लिए (AltStore / Sideloadly)। Firefox: Firefox Add-ons से इंस्टॉल करें। <a href="privacy.html?lang=hi">गोपनीयता नीति</a>',
  winHint: ".msi FetchJet को इंस्टॉल करता है और उसे ख़ुद अपडेट होने देता है।",
  winNotice: '<div class="notice"><h3>Microsoft Store से लें (सुझाया गया)</h3>एक क्लिक में इंस्टॉल, कोई SmartScreen चेतावनी नहीं, और Windows इसे अपडेट रखता है।<div class="stores"><a class="btn" href="https://apps.microsoft.com/detail/9NP3HJRHMPJS">Microsoft Store</a></div><div class="hint">इंस्टॉलर चाहिए? नीचे से .msi लें; यह GitHub से ख़ुद अपडेट होता है।</div></div>',
  macHint: "सिर्फ़ Apple Silicon Mac (M1 या नया) के लिए; Intel Mac समर्थित नहीं हैं। .dmg खोलें और FetchJet को Applications में खींचें।",
  macNotice: '<div class="notice"><h3>पहली बार खोलने पर: "Apple could not verify FetchJet"</h3>FetchJet अभी Apple से notarized नहीं है, इसलिए macOS पहली बार खोलने से रोकता है। इसे एक बार खोलें, चेतावनी बंद करें, फिर <b>System Settings &rarr; Privacy &amp; Security</b> में जाकर <b>Open Anyway</b> दबाएँ। या Terminal में एक बार यह चलाएँ:<div class="hint"><code>xattr -dr com.apple.quarantine /Applications/FetchJet.app</code></div></div>',
  linuxHint: "Debian/Ubuntu के लिए .deb; .tar.gz कहीं भी चलता है (खोलें और bin/FetchJet शुरू करें)।",
  androidHint: "arm64-v8a 2017 के बाद बने लगभग हर फ़ोन पर चलता है। पुराने 32-bit फ़ोन: armeabi-v7a। एमुलेटर और Chromebook: x86_64।",
  iosLabel: "iPhone · iPad", iosHint: "iPhone और iPad के लिए एक IPA। साइन नहीं है: AltStore या Sideloadly से साइडलोड करें।",
  extLabel: "ब्राउज़र एक्सटेंशन", extHint: ".zip = Chrome / Edge / Brave (load unpacked करें या chrome://extensions पर खींचें)।",
  extNotice: '<div class="notice"><h3>स्टोर से इंस्टॉल करें (सुझाया गया)</h3>अपडेट अपने आप इंस्टॉल होते हैं और ब्राउज़र कोई चेतावनी नहीं दिखाता।<div class="stores"><a class="btn" href="https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/">Firefox Add-ons</a><a class="btn" href="https://microsoftedge.microsoft.com/addons/detail/phngekpnkcnpkjkcoggcfmlmebmociaj">Microsoft Edge Add-ons</a></div><div class="hint">Chrome, Brave, Opera और Vivaldi: FetchJet Chrome Web Store में नहीं है, इसलिए नीचे दिया .zip इस्तेमाल करें।</div></div><div class="notice warn"><h3>⚠ नीचे दिया .zip इंस्टॉल करना (developer mode)</h3>.zip Chrome / Edge / Brave के लिए है जब आप स्टोर इस्तेमाल नहीं कर सकते। ब्राउज़र इसे developer एक्सटेंशन मानता है: शुरू में "developer mode extensions" चेतावनी दिख सकती है, और यह ख़ुद अपडेट <b>नहीं</b> होता - नए वर्ज़न के लिए यहाँ वापस आएँ।<ol><li><code>.zip</code> डाउनलोड करें और उसे ऐसे फ़ोल्डर में खोलें जिसे आप रखेंगे (बाद में डिलीट न करें)।</li><li><code>chrome://extensions</code> खोलें (Edge: <code>edge://extensions</code>)।</li><li><b>Developer mode</b> चालू करें।</li><li><b>Load unpacked</b> दबाएँ और खोला हुआ फ़ोल्डर चुनें।</li></ol></div>',
  noBuild: "{p} के लिए अभी कोई बिल्ड प्रकाशित नहीं हुआ है।", latestFor: "{p} के लिए नया: {v}",
  skipped: "नई रिलीज़ ({list}) में {p} के लिए कोई बदलाव नहीं था", releaseNotes: "रिलीज़ नोट्स", whatsNew: "{v} में क्या नया है",
  hideOlder: "पुराने वर्ज़न छिपाएँ", showOlder: "पुराने वर्ज़न दिखाएँ ({n})", preRelease: "pre-release", beta: "beta",
  whatChanged: "क्या बदला", onGitHub: "GitHub पर", release1: "1 रिलीज़", releasesN: "{n} रिलीज़", offline: "ऑफ़लाइन",
  loadError: 'रिलीज़ की सूची लोड नहीं हो सकी ({e})। इसके बजाय <a href="{releases}">GitHub पर रिलीज़ पेज</a> खोलें।',
  fileMsi: "इंस्टॉलर (.msi)", fileZip: "पोर्टेबल, बिना इंस्टॉल (.zip)", fileExe: "सेटअप (.exe)", fileDeb: "Debian / Ubuntu (.deb)",
  fileTar: "पोर्टेबल (.tar.gz)", fileDmg: "Apple Silicon (.dmg)", fileXpi: "Firefox (.xpi)", fileExtZip: "Chrome / Edge (.zip)"
};
