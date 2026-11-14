## Nouveautés de la 1.4.1

**Téléchargements**
- **Téléchargements FTP et FTPS (nouveau) :** les liens qui commencent par ftp://, ftps:// ou ftpes:// se téléchargent maintenant comme n'importe quel lien direct, en plusieurs parties et avec reprise quand le serveur le permet. Mettez l'identifiant dans le lien, enregistrez-le dans Réglages > Réseau > Identifiants de sites, ou laissez-le vide pour les serveurs anonymes. Bon à savoir : les liens FTP ne passent pas par le proxy ; sur les applications de bureau, FTP sur TLS est récupéré avec le curl du système ; FTP sur TLS n'est pas encore disponible sur iPhone et iPad.
- **Un fichier qui change sur le serveur :** si le fichier est remplacé sur le serveur pendant le téléchargement, FetchJet s'en aperçoit. Le téléchargement s'arrête avec un message clair au lieu d'enregistrer un fichier mélangé et cassé, et Reprendre télécharge la nouvelle version.
- **Plus d'attente à la fin :** la fin d'un téléchargement n'attend plus une seule connexion lente. Une connexion qui rampe à la fin est remplacée.
- **Vérifications avant l'enregistrement :** collez une somme de contrôle (SHA-256 ou MD5) dans la fenêtre Ajouter, ou laissez l'en-tête de somme de contrôle du serveur décider. Un fichier qui ne correspond pas n'est pas enregistré. Les fichiers ZIP sont vérifiés entrée par entrée, et une partie endommagée est retéléchargée une fois.
- **Les limites du serveur sont mémorisées :** un site qui n'autorise que quelques connexions est mémorisé pendant un jour, pour que le téléchargement suivant ne démarre pas avec des connexions qu'il refuse. Après un 403, FetchJet peut réessayer en IPv4 (Windows, Android et Linux).
- **Tester le lien :** un bouton dans la fenêtre Ajouter vous dit si le lien fonctionne, sa taille et s'il peut être repris, sans le télécharger.
- **Un dossier par type de fichier :** le dossier choisi pour les vidéos, la musique ou les archives n'est mémorisé que pour ce type.
- **Lecture depuis la liste :** un fichier vidéo ou audio terminé affiche désormais un bouton ▶ qui l'ouvre dans votre lecteur.
- **Plus de noms inventés :** un lien sans nom de fichier affiche un champ « Nom du fichier » vide, et le démarrage attend que vous en saisissiez un.
- **Adapté à votre écran :** la fenêtre du bureau s'ouvre maintenant dans l'écran visible, et non plus en partie sous la barre des tâches.

**Torrents**
- **Détails :** ratio de partage, quantité envoyée, morceaux et taille des morceaux, trackers avec leur état, pairs, « Vérifier à nouveau », copie du lien magnet et « premiers et derniers morceaux d'abord » pour prévisualiser les vidéos.
- **Ajouter un tracker** à un torrent en cours, et **arrêter le partage à un ratio** de votre choix (Réglages > BitTorrent).
- **Plus sûr :** un torrent dont les fichiers seraient écrits en dehors de son propre dossier est refusé.
- **Derrière un proxy HTTP :** les torrents qui n'ont que des trackers UDP n'y trouvent pas de pairs ; Réglages > Réseau et le message d'erreur le disent maintenant.

**Bureau**
- **Clavier :** flèches, Shift, Home / End, Page Up / Down, Enter (Alt+Enter ouvre ou ferme les détails), Ctrl+R / P / O / E / N / F / A, Delete, Esc et F1 pour la liste des raccourcis. Ils fonctionnent aussi avec les claviers arabe, persan et russe, et le copier / coller dans chaque champ également.
- **Ligne de commande :** `/d` lien, `/p` dossier, `/f` nom de fichier, `/a` ajouter en pause sans la fenêtre Ajouter, `/n` ajouter et démarrer aussitôt sans fenêtre, `/s` démarrer tout ce qui attend.
- **Limite de vitesse seulement pendant que vous utilisez l'ordinateur,** et **réveil de l'ordinateur pour les téléchargements planifiés** (Windows).
- **Petits détails :** la version dans le titre de la fenêtre, Esc ferme la fenêtre Ajouter, les badges de type de fichier sont lisibles dans le thème clair, et le titre Envoyer vers un appareil est traduit partout.

**Audio**
- **OGG :** choisir OGG pour un audio YouTube (Opus) ne le réencode plus. Il est enregistré aussitôt, avec la qualité et la taille d'origine.
- **Compresser l'audio :** propose maintenant les mêmes formats que Convertir (M4A, MP3, Opus, OGG, FLAC, WAV), garde OGG quand le fichier est en OGG, et la taille estimée est plus proche de la taille réelle.

**Extension de navigateur (3.4)**
- **Connexion :** quand le navigateur n'est pas encore connecté à l'application, l'extension le dit aussitôt (« Ouvrez FetchJet et touchez Autoriser ») au lieu de charger sans fin. Après avoir touché Autoriser, les qualités apparaissent d'elles-mêmes. Si le navigateur demande « Ouvrir FetchJet ? », l'onglet reste maintenant jusqu'à votre réponse. Réglages > Extension de navigateur propose « Tout dissocier ».
- **Le navigateur garde un téléchargement jusqu'à ce que l'application l'ait,** les formulaires que vous avez envoyés restent dans le navigateur, et les fenêtres privées utilisent leurs propres cookies.
- **Liens :** un lien de fichier est pris après l'exécution du code de clic de la page elle-même, donc les sites qui répondaient 403 fonctionnent maintenant. Un lien PDF s'ouvre dans le navigateur, sauf si la page le marque comme téléchargement. « Télécharger tous les liens » trouve aussi les liens marqués comme téléchargements et ceux conservés dans des attributs data-*, et Google Drive a une ligne « Fichier d'origine ».
- **Le bouton vidéo :** il fonctionne au clavier (Tab, Enter), se colle à un bord quand vous l'y faites glisser, il y a un bouton par groupe de vidéos empilées et aucun sur une vidéo cachée sous la page. Vous pouvez le masquer sur un site, et Telegram Web explique pourquoi il ne peut pas être téléchargé.
- **L'extension suit le thème de l'application :** sa fenêtre et sa page de réglages sont claires ou sombres comme l'application (Système suit l'appareil), et comme le navigateur quand l'application n'est pas lancée.

**Mises à jour**
- **La fenêtre de mise à jour** suit la langue de l'application, et les notes de version sont traduites.
