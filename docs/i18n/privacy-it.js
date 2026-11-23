window.FJ_PRIVACY = window.FJ_PRIVACY || {};
window.FJ_PRIVACY.it = { title: "Informativa sulla privacy di FetchJet", html: `
<h1>Informativa sulla privacy</h1>
<p class="date">App FetchJet ed estensione del browser FetchJet Download Manager · ultimo aggiornamento 16 novembre 2026</p>
<p class="date">Questa è una traduzione dell’informativa in inglese. In caso di differenze prevale la versione inglese.</p>
<h2>In breve</h2>
<p>FetchJet non raccoglie dati personali. Non ci sono account né tracciamento. Niente su di te o sulla tua navigazione viene inviato allo sviluppatore o a terzi. L’unica cosa che l’app invia è un conteggio d’uso anonimo al giorno, descritto sotto. L’estensione del browser non invia nulla allo sviluppatore.</p>
<h2>L’estensione del browser</h2>
<p>Quando scegli di scaricare un link, un video o una pagina, l’estensione invia quel link, il referer e i cookie di quel sito <strong>solo all’app FetchJet sul tuo dispositivo</strong> (indirizzo 127.0.0.1, porta 28450). I cookie servono perché i download che richiedono l’accesso funzionino esattamente come nel browser. Non lasciano mai il tuo dispositivo tramite l’estensione.</p>
<p>Le impostazioni dell’estensione (cattura attiva o no, tipi di file, dimensione minima, siti esclusi) sono salvate nella memoria locale dell’estensione nel browser.</p>
<h2>L’app FetchJet</h2>
<p>L’app si collega solo ai siti da cui scarichi, e a GitHub per verificare se c’è una nuova versione (questa richiesta contiene solo la versione dell’app). Download, impostazioni e cronologia restano sul tuo dispositivo.</p>
<p><strong>Conteggio d’uso anonimo.</strong> Una volta al giorno l’app invia un piccolo messaggio a un server dello sviluppatore (Cloudflare Workers) perché lo sviluppatore possa vedere quante persone usano FetchJet. Il messaggio contiene un ID casuale creato sul tuo dispositivo, la versione dell’app e la piattaforma (Windows, macOS, Linux, Android o iOS). Il paese viene ricavato da Cloudflare dalla connessione. Lo sviluppatore non conserva il tuo indirizzo IP, nome, email, link, nomi di file o qualsiasi cosa tu scarichi. I totali sono pubblici sulla <a href="https://fetchjet.the-best-is-best.workers.dev/dashboard">pagina delle statistiche</a>. Puoi disattivarlo impostando la variabile d’ambiente <code>FETCHJET_NO_USAGE_PING=1</code> prima di avviare l’app.</p>
<h2>Modifiche e contatti</h2>
<p>Se questa informativa cambia, la nuova versione sarà pubblicata su questa pagina. Domande: <a href="mailto:eng.michelle.raouf@gmail.com">eng.michelle.raouf@gmail.com</a>.</p>
<footer><a href="./?lang=it">Download di FetchJet</a></footer>
` };
