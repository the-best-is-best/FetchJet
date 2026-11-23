window.FJ_PAGE_TEXT = window.FJ_PAGE_TEXT || {};
window.FJ_PAGE_TEXT.ko = {
  pageTitle: "FetchJet 다운로드", title: "FetchJet 다운로드", subtitle: "모든 플랫폼의 모든 버전. 원하는 것을 고르세요.",
  loading: "불러오는 중…", loadingReleases: "GitHub에서 릴리스를 불러오는 중…",
  footer: '파일은 <a href="{releases}">GitHub Releases</a>에 게시됩니다. Android: 사용하는 CPU에 맞는 APK를 설치하세요(잘 모르면 <em>arm64-v8a</em>). iPhone과 iPad: 둘 다 쓰는 IPA 하나, 사이드로드용(AltStore / Sideloadly). Firefox: Firefox Add-ons에서 설치하세요. <a href="privacy.html">개인정보 처리방침</a>',
  winHint: ".msi는 FetchJet을 설치하고 스스로 업데이트하게 합니다.",
  winNotice: '<div class="notice"><h3>Microsoft Store에서 받기(권장)</h3>클릭 한 번으로 설치, SmartScreen 경고 없음, Windows가 최신 상태로 유지합니다.<div class="stores"><a class="btn" href="https://apps.microsoft.com/detail/9NP3HJRHMPJS">Microsoft Store</a></div><div class="hint">설치 프로그램이 좋다면 아래 .msi를 받으세요. GitHub에서 스스로 업데이트됩니다.</div></div>',
  macHint: "Apple Silicon Mac(M1 이상) 전용입니다. Intel Mac은 지원하지 않습니다. .dmg를 열고 FetchJet을 응용 프로그램으로 드래그하세요.",
  macNotice: '<div class="notice"><h3>처음 실행: "Apple could not verify FetchJet"</h3>FetchJet은 아직 Apple 공증을 받지 않아 macOS가 처음 실행을 막습니다. 한 번 열고 경고를 닫은 뒤 <b>시스템 설정 &rarr; 개인정보 보호 및 보안</b>에서 <b>그래도 열기</b>를 누르세요. 또는 터미널에서 한 번 실행하세요:<div class="hint"><code>xattr -dr com.apple.quarantine /Applications/FetchJet.app</code></div></div>',
  linuxHint: "Debian/Ubuntu는 .deb, .tar.gz는 어디서나 실행됩니다(압축을 풀고 bin/FetchJet 실행).",
  androidHint: "arm64-v8a는 2017년 이후 거의 모든 휴대폰에 맞습니다. 오래된 32비트 휴대폰: armeabi-v7a. 에뮬레이터와 Chromebook: x86_64.",
  iosLabel: "iPhone · iPad", iosHint: "iPhone과 iPad용 IPA 하나. 서명되지 않음: AltStore 또는 Sideloadly로 사이드로드하세요.",
  extLabel: "브라우저 확장 프로그램", extHint: ".zip = Chrome / Edge / Brave(압축해제된 확장 프로그램으로 로드하거나 chrome://extensions에 드래그).",
  extNotice: '<div class="notice"><h3>스토어에서 설치(권장)</h3>업데이트가 자동으로 설치되고 브라우저에 경고가 표시되지 않습니다.<div class="stores"><a class="btn" href="https://addons.mozilla.org/firefox/addon/fetchjet-download-manager/">Firefox Add-ons</a><a class="btn" href="https://microsoftedge.microsoft.com/addons/detail/phngekpnkcnpkjkcoggcfmlmebmociaj">Microsoft Edge Add-ons</a></div><div class="hint">Chrome, Brave, Opera, Vivaldi: FetchJet은 Chrome 웹 스토어에 없으니 아래 .zip을 사용하세요.</div></div><div class="notice warn"><h3>⚠ 아래 .zip 설치하기(개발자 모드)</h3>.zip은 스토어를 쓸 수 없을 때 Chrome / Edge / Brave용입니다. 브라우저는 이를 개발자 확장 프로그램으로 취급합니다. 시작할 때 "developer mode extensions" 경고가 나올 수 있고, 스스로 업데이트되지 <b>않습니다</b>. 새 버전은 여기로 돌아와 받으세요.<ol><li><code>.zip</code>을 내려받아 계속 둘 폴더에 압축을 푸세요(나중에 지우지 마세요).</li><li><code>chrome://extensions</code>를 엽니다(Edge: <code>edge://extensions</code>).</li><li><b>개발자 모드</b>를 켭니다.</li><li><b>압축해제된 확장 프로그램을 로드합니다</b>를 누르고 압축을 푼 폴더를 고릅니다.</li></ol></div>',
  noBuild: "아직 {p}용 빌드가 게시되지 않았습니다.", latestFor: "{p} 최신 버전: {v}",
  skipped: "더 새로운 릴리스({list})에는 {p} 변경 사항이 없었습니다", releaseNotes: "릴리스 노트", whatsNew: "{v}의 새로운 기능",
  hideOlder: "이전 버전 숨기기", showOlder: "이전 버전 보기({n})", preRelease: "프리릴리스", beta: "베타",
  whatChanged: "변경 사항", onGitHub: "GitHub에서", release1: "릴리스 1개", releasesN: "릴리스 {n}개", offline: "오프라인",
  loadError: '릴리스 목록을 불러오지 못했습니다({e}). 대신 <a href="{releases}">GitHub의 릴리스 페이지</a>를 여세요.',
  fileMsi: "설치 프로그램 (.msi)", fileZip: "포터블, 설치 없음 (.zip)", fileExe: "설치 (.exe)", fileDeb: "Debian / Ubuntu (.deb)",
  fileTar: "포터블 (.tar.gz)", fileDmg: "Apple Silicon (.dmg)", fileXpi: "Firefox (.xpi)", fileExtZip: "Chrome / Edge (.zip)"
};
