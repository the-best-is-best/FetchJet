window.FJ_PRIVACY = window.FJ_PRIVACY || {};
window.FJ_PRIVACY.fr = { title: "Politique de confidentialité de FetchJet", html: `
<h1>Politique de confidentialité</h1>
<p class="date">Application FetchJet et extension de navigateur FetchJet Download Manager · dernière mise à jour le 16 novembre 2026</p>
<p class="date">Ceci est une traduction de la politique en anglais. En cas de différence, la version anglaise fait foi.</p>
<h2>Résumé</h2>
<p>FetchJet ne collecte aucune donnée personnelle. Il n’y a ni compte ni pistage. Rien sur vous ou votre navigation n’est envoyé au développeur ni à un tiers. La seule chose que l’application envoie est un comptage d’utilisation anonyme par jour, décrit ci-dessous. L’extension de navigateur n’envoie rien au développeur.</p>
<h2>L’extension de navigateur</h2>
<p>Quand vous choisissez de télécharger un lien, une vidéo ou une page, l’extension envoie ce lien, le referer et les cookies de ce site <strong>uniquement à l’application FetchJet sur votre propre appareil</strong> (adresse 127.0.0.1, port 28450). Les cookies servent à ce que les téléchargements qui demandent une connexion fonctionnent exactement comme dans le navigateur. Ils ne quittent jamais votre appareil par l’extension.</p>
<p>Les réglages de l’extension (capture activée ou non, types de fichiers, taille minimale, sites exclus) sont enregistrés dans le stockage local de l’extension dans le navigateur.</p>
<h2>L’application FetchJet</h2>
<p>L’application se connecte uniquement aux sites depuis lesquels vous téléchargez, et à GitHub pour vérifier s’il existe une nouvelle version (cette requête contient la version de l’application, rien d’autre). Les téléchargements, réglages et historique restent sur votre appareil.</p>
<p><strong>Comptage d’utilisation anonyme.</strong> Une fois par jour, l’application envoie un petit message à un serveur géré par le développeur (Cloudflare Workers) pour qu’il sache combien de personnes utilisent FetchJet. Le message contient un identifiant aléatoire créé sur votre appareil, la version de l’application et la plateforme (Windows, macOS, Linux, Android ou iOS). Le pays est déterminé par Cloudflare à partir de la connexion. Le développeur ne conserve ni votre adresse IP, ni votre nom, ni votre e-mail, ni les liens, ni les noms de fichiers, ni rien de ce que vous téléchargez. Les totaux sont publics sur la <a href="https://fetchjet.the-best-is-best.workers.dev/dashboard">page de statistiques</a>. Vous pouvez désactiver cela en définissant la variable d’environnement <code>FETCHJET_NO_USAGE_PING=1</code> avant de lancer l’application.</p>
<h2>Modifications et contact</h2>
<p>Si cette politique change, la nouvelle version sera publiée sur cette page. Questions : <a href="mailto:eng.michelle.raouf@gmail.com">eng.michelle.raouf@gmail.com</a>.</p>
<footer><a href="./?lang=fr">Téléchargements FetchJet</a></footer>
` };
