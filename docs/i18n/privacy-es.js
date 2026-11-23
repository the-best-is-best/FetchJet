window.FJ_PRIVACY = window.FJ_PRIVACY || {};
window.FJ_PRIVACY.es = { title: "Política de privacidad de FetchJet", html: `
<h1>Política de privacidad</h1>
<p class="date">App FetchJet y extensión de navegador FetchJet Download Manager · última actualización: 16 de noviembre de 2026</p>
<p class="date">Esta es una traducción de la política en inglés. Si hay alguna diferencia, prevalece la versión en inglés.</p>
<h2>Resumen</h2>
<p>FetchJet no recopila datos personales. No hay cuentas ni rastreo. No se envía nada sobre ti ni sobre tu navegación al desarrollador ni a terceros. Lo único que envía la app es un recuento de uso anónimo al día, descrito abajo. La extensión del navegador no envía nada al desarrollador.</p>
<h2>La extensión del navegador</h2>
<p>Cuando eliges descargar un enlace, vídeo o página, la extensión envía ese enlace, el referer y las cookies de ese sitio <strong>solo a la app FetchJet de tu propio dispositivo</strong> (dirección 127.0.0.1, puerto 28450). Las cookies se usan para que las descargas que requieren inicio de sesión funcionen igual que en el navegador. Nunca salen de tu dispositivo a través de la extensión.</p>
<p>Los ajustes de la extensión (captura activada o no, tipos de archivo, tamaño mínimo, sitios excluidos) se guardan en el almacenamiento local de la extensión en el navegador.</p>
<h2>La app FetchJet</h2>
<p>La app solo se conecta a los sitios desde los que descargas, y a GitHub para comprobar si hay una nueva versión (esta solicitud contiene la versión de la app, nada más). Las descargas, los ajustes y el historial se quedan en tu dispositivo.</p>
<p><strong>Recuento de uso anónimo.</strong> Una vez al día la app envía un pequeño mensaje a un servidor del desarrollador (Cloudflare Workers) para que el desarrollador pueda ver cuántas personas usan FetchJet. El mensaje contiene un ID aleatorio generado en tu dispositivo, la versión de la app y la plataforma (Windows, macOS, Linux, Android o iOS). Cloudflare deduce el país a partir de la conexión. El desarrollador no guarda tu dirección IP, nombre, correo, enlaces, nombres de archivo ni nada de lo que descargas. Los totales son públicos en la <a href="https://fetchjet.the-best-is-best.workers.dev/dashboard">página de estadísticas</a>. Puedes desactivarlo definiendo la variable de entorno <code>FETCHJET_NO_USAGE_PING=1</code> antes de iniciar la app.</p>
<h2>Cambios y contacto</h2>
<p>Si esta política cambia, la nueva versión se publicará en esta página. Preguntas: <a href="mailto:eng.michelle.raouf@gmail.com">eng.michelle.raouf@gmail.com</a>.</p>
<footer><a href="./?lang=es">Descargas de FetchJet</a></footer>
` };
