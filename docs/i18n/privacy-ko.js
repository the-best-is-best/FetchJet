window.FJ_PRIVACY = window.FJ_PRIVACY || {};
window.FJ_PRIVACY.ko = { title: "FetchJet 개인정보 처리방침", html: `
<h1>개인정보 처리방침</h1>
<p class="date">FetchJet 앱과 브라우저 확장 프로그램 FetchJet Download Manager · 최종 업데이트 2026년 11월 16일</p>
<p class="date">이 문서는 영어 방침의 번역입니다. 차이가 있으면 영어판이 우선합니다.</p>
<h2>요약</h2>
<p>FetchJet은 개인 데이터를 수집하지 않습니다. 계정도 추적도 없습니다. 사용자나 사용자의 브라우징에 관한 어떤 정보도 개발자나 제3자에게 보내지 않습니다. 앱이 보내는 유일한 것은 아래에 설명한 하루 한 번의 익명 사용 횟수입니다. 브라우저 확장 프로그램은 개발자에게 아무것도 보내지 않습니다.</p>
<h2>브라우저 확장 프로그램</h2>
<p>링크, 동영상 또는 페이지를 다운로드하기로 하면 확장 프로그램은 그 링크, referer, 해당 사이트의 쿠키를 <strong>사용자 기기의 FetchJet 앱에만</strong> 보냅니다(주소 127.0.0.1, 포트 28450). 쿠키는 로그인이 필요한 다운로드가 브라우저에서와 똑같이 동작하도록 쓰입니다. 확장 프로그램을 통해 기기 밖으로 나가는 일은 없습니다.</p>
<p>확장 프로그램 설정(캡처 켜기/끄기, 파일 형식, 최소 크기, 제외 사이트)은 브라우저의 확장 프로그램 로컬 저장소에 저장됩니다.</p>
<h2>FetchJet 앱</h2>
<p>앱은 사용자가 다운로드하는 사이트와, 새 버전을 확인하기 위한 GitHub에만 연결합니다(이 요청에는 앱 버전만 들어 있습니다). 다운로드, 설정, 기록은 사용자 기기에 남습니다.</p>
<p><strong>익명 사용 횟수.</strong> 앱은 하루에 한 번 개발자가 운영하는 서버(Cloudflare Workers)에 작은 메시지를 보내 FetchJet 사용자 수를 알 수 있게 합니다. 메시지에는 기기에서 만든 임의 ID, 앱 버전, 플랫폼(Windows, macOS, Linux, Android 또는 iOS)이 들어 있습니다. 국가는 Cloudflare가 연결로부터 판단합니다. 개발자는 IP 주소, 이름, 이메일, 링크, 파일 이름, 다운로드한 어떤 것도 보관하지 않습니다. 합계는 <a href="https://fetchjet.the-best-is-best.workers.dev/dashboard">통계 페이지</a>에 공개됩니다. 앱을 시작하기 전에 환경 변수 <code>FETCHJET_NO_USAGE_PING=1</code>을 설정하면 끌 수 있습니다.</p>
<h2>변경 및 연락처</h2>
<p>이 방침이 바뀌면 새 버전을 이 페이지에 게시합니다. 문의: <a href="mailto:eng.michelle.raouf@gmail.com">eng.michelle.raouf@gmail.com</a></p>
<footer><a href="./?lang=ko">FetchJet 다운로드</a></footer>
` };
