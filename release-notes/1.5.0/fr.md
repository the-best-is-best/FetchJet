## Nouveautés de la 1.5.0

**Archives**
- **Extraire après le téléchargement :** les fichiers ZIP, TAR, GZ et XZ peuvent être extraits automatiquement à la fin du téléchargement, ou avec le nouveau bouton Extraire sur la ligne. Sur le bureau, les fichiers 7Z, RAR, les archives découpées (.001, .part1.rar, .z01) et les archives protégées par mot de passe fonctionnent aussi quand 7-Zip est installé ; FetchJet demande le mot de passe.
- **Où vont les fichiers :** dans un dossier au nom de l'archive, toujours dans un dossier, ou à côté de l'archive. Vous pouvez aussi envoyer l'archive dans la Corbeille une fois extraite.

**Téléchargements**
- **Menu clic droit sur un téléchargement** (appui long sur les téléphones) : ouvrir, ouvrir le dossier, copier le lien, mettre en pause, reprendre, supprimer, et plus encore.
- **Dossiers par site :** envoyez tout ce qui vient d'un site choisi dans son propre dossier.
- **Les fichiers supprimés vont dans la Corbeille** (aussi sur macOS et Linux) au lieu de disparaître. Vous pouvez désactiver cela dans Réglages.
- **Analyse après le téléchargement :** Windows vérifie un fichier terminé avec votre antivirus, et un téléchargement bloqué par l'antivirus s'arrête aussitôt avec un message clair. Sur macOS, les fichiers téléchargés et extraits portent la marque habituelle « téléchargé depuis Internet ».
- **Rapport mensuel :** Réglages > Téléchargements affiche, pour chaque mois, le nombre de fichiers téléchargés, leur taille totale et les sites d'où ils viennent. Le calcul se fait uniquement sur votre appareil.

**Réseau (bureau)**
- **Votre propre DNS :** choisissez un serveur DNS pour les téléchargements de FetchJet (DNS over HTTPS ou un serveur DNS classique), et l'adaptateur réseau qu'ils utilisent. Seuls les téléchargements de FetchJet sont concernés, pas le reste de l'ordinateur.

**Windows**
- **Nouveaux installeurs :** un Setup.exe et un nouveau MSI, avec le choix d'un raccourci sur le bureau et dans le menu Démarrer. Ils vous disent si cette version, une plus récente ou une plus ancienne est déjà installée, et peuvent lancer FetchJet à la fin.
- **Version portable :** un .zip qui fonctionne sans installation, avec ses données dans un dossier à côté.
- **ffmpeg plus récent** sur Windows et Linux (l'outil qui fusionne la vidéo et l'audio). Il se met à jour tout seul au prochain démarrage de FetchJet.

**Android**
- **Application plus légère :** environ 53 Mo au lieu de 83 Mo sur la plupart des téléphones, et elle prend moins de place une fois installée.

**iPhone et iPad**
- **Pages qui demandent une connexion, comme les enregistrements Zoom :** ouvrez la page dans FetchJet, saisissez le code d'accès une fois, et téléchargez avec la session de la page. Le bouton de page dans Safari propose aussi « Ouvrir cette page dans FetchJet ».

**Pour les utilisateurs avancés**
- **Ligne de commande :** `FetchJet status`, `pause`, `start`, `remove` et `delete` contrôlent un FetchJet en cours d'exécution. Elles sont désactivées jusqu'à ce que vous lanciez `FetchJet automation on`.

**Extension de navigateur (3.5)**
- Prend tous les téléchargements du navigateur, sauf si vous la limitez aux types de fichiers de votre liste.
- Un bouton de téléchargement sur les enregistrements Zoom audio uniquement.
- Les lecteurs dans des cadres trouvent leur vidéo de façon plus fiable.

**Confidentialité**
- FetchJet envoie désormais un message anonyme par jour pour que nous sachions combien de personnes l'utilisent : un numéro aléatoire pour cette installation, la version de l'application et le système ; le serveur déduit le pays de la connexion. Aucun lien, fichier ni donnée personnelle. Les totaux sont sur une page publique accessible depuis le README.
