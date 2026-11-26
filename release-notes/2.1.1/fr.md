## Nouveautés de la 2.1.1

**Lecteur multimédia**
- **Nouveau :** lisez l'audio et la vidéo terminés directement dans FetchJet, sur toutes les plateformes : un bouton de lecture sur la ligne, « Lire » pour tout un dossier, une playlist ou un groupe, une file d'attente avec précédent / suivant et la liste, des vitesses, et un appui maintenu pour passer en 2x. La dernière position est conservée.
- **Commandes compactes :** les commandes s'affichent par-dessus la vidéo et disparaissent après 3 secondes. Une fine barre de progression, une seule petite ligne avec la vitesse, un interrupteur **Lecture auto** et **CC**. Quand une vidéo est en pause, ou que la suivante attend avec la lecture auto désactivée, un grand ▶ apparaît au milieu.
- **Lecture auto :** activée, l'élément suivant démarre tout seul ; désactivée, il se charge en pause.
- **Sous-titres :** le menu CC propose les sous-titres intégrés à la vidéo, les fichiers de sous-titres placés à côté (votre langue d'abord) et « Ouvrir un fichier de sous-titres… » (SRT, VTT, ASS). Le choix est mémorisé pour chaque téléchargement. Les sous-titres sont en bas et remontent au-dessus des commandes quand elles s'affichent. CC est plein quand un sous-titre est actif.
- **Téléphones et tablettes :** glissez vers le haut ou le bas sur la moitié droite pour le volume, et sur la moitié gauche pour la luminosité (celle du lecteur seulement).
- **Ordinateurs :** un curseur de volume jusqu'à 200 % avec un bouton muet, et le plein écran. Sous Windows, macOS et Linux, la bibliothèque du lecteur se télécharge uniquement la première fois que vous lisez un fichier, avec sa progression affichée, et le fichier s'ouvre tout seul quand elle est prête.

**Couper, extraire et compresser**
- **Couper la vidéo :** depuis le menu de la ligne ou les détails, par temps ou sur une timeline. La timeline fonctionne comme dans un logiciel de montage : une échelle de temps fixe, glisser ou faire défiler pour se déplacer, un zoom de quelques secondes à la vidéo entière, des poignées qui font défiler la vue aux bords, des miniatures et une fine barre de défilement. L'extrait est un nouveau fichier, rangé sous sa vidéo.
- **Couper l'audio :** les fichiers audio se coupent de la même façon, sur une timeline en forme d'onde sonore. Les parties MP3 sont réencodées au débit de la source : aucun son n'est perdu et le fichier ne grossit pas.
- **Extraire l'audio :** enregistre la piste sonore d'une vidéo dans un fichier à part, sans réencodage.
- **Supprimer l'original :** tant que rien d'autre n'a été créé à partir d'un fichier, Couper et Compresser proposent « Supprimer l'original ». Le nouveau fichier devient alors le téléchargement principal : vous pouvez y ajouter des sous-titres (leur minutage suit l'extrait), le couper ou le compresser à nouveau. Supprimer un téléchargement supprime aussi les extraits et les copies créés à partir de lui, et la confirmation indique combien.

**Lecteur PDF**
- **Nouveau :** ouvrez les fichiers PDF directement dans FetchJet : les pages les unes sous les autres, le numéro de page et « Aller à la page », l'ajustement à la largeur, le zoom, et la dernière page lue est conservée. Android et iOS utilisent le moteur PDF du système ; Windows, macOS et Linux téléchargent une petite bibliothèque PDF la première fois. Un réglage permet de choisir le lecteur intégré, l'application du système, ou de demander à chaque fois.

**Téléchargements masqués**
- **Nouveau :** masquez un téléchargement, une playlist ou un groupe de dossier derrière un code PIN de 4 à 6 chiffres ou un schéma (Plus > Masqués). Les éléments masqués quittent la liste, la galerie et le gestionnaire de fichiers, et leurs fichiers passent dans un dossier privé de l'application. Le masquage est instantané et affiche une ligne de progression pendant le déplacement des fichiers. Quand vous les démasquez, les fichiers retournent dans leur dossier.
- **L'audio et la vidéo sont chiffrés :** l'audio et la vidéo que vous masquez sont chiffrés avec une clé liée à l'appareil, leurs noms de fichier sont brouillés, et ils ne sont déchiffrés que pendant la lecture dans le lecteur de l'application. Les autres types de fichier sont seulement mis hors de vue. Un message l'explique la première fois.
- **Quitter l'application la recouvre :** à votre retour, elle demande votre empreinte, Face ID, Windows Hello ou votre code PIN, et une vidéo masquée en cours de lecture se ferme.
- **Les nouveaux téléchargements démarrent masqués** quand vous les lancez depuis l'écran Masqués (le bouton +). Les téléchargements masqués n'affichent aucune notification.
- **PIN oublié :** confirmez avec le verrouillage de votre téléphone ou de votre ordinateur et choisissez un nouveau PIN. Vos fichiers masqués restent en place. Indisponible sous Linux.
- **Code de récupération :** un seul code pour tout le coffre, affiché une fois. Avec lui, vous pouvez rouvrir l'audio et la vidéo masqués si la clé de l'appareil est perdue, par exemple après une restauration sur un nouveau téléphone. Supprimer l'application ou effacer ses données supprime les fichiers masqués : démasquez-les avant de désinstaller.

**Sauvegarde et restauration**
- **« Sauvegarder les téléchargements »** enregistre dans un seul fichier la liste de vos téléchargements, vos dossiers et vos paramètres, sans mots de passe ni cookies. **« Restaurer une sauvegarde »** la rétablit et la fusionne avec l'existant. Les fichiers téléchargés ne sont pas dans la sauvegarde : les téléchargements terminés se relient à leurs fichiers, et les téléchargements inachevés reviennent en pause.

**Navigateur et connexions**
- **Navigateur intégré** (Plus > Navigateur, Android et iOS) : une barre d'adresse, précédent / suivant / actualiser ; un fichier ou une vidéo que vous y ouvrez arrive dans la boîte d'ajout avec la connexion de la page.
- **Se connecter avec le navigateur** (Paramètres > Réseau > Identifiants de sites) : connectez-vous une fois à un site et ses téléchargements utilisent cette session. Supprimer l'identifiant du site efface aussi les cookies du navigateur pour ce site.

**Téléchargements**
- **Connexions choisies automatiquement :** FetchJet démarre avec 4 connexions, en ajoute tant que la vitesse augmente, et retient ce qui a marché pour chaque site.
- **Des extraits au temps exact :** un extrait commence et finit désormais exactement à De et À, et De / À acceptent des fractions de seconde (12.500). Avant, un extrait pouvait commencer en retard (De 12 commençait à la fin de la 12e seconde) ou en avance (11.5 commençait à 11), car la coupe se calait sur l'image clé la plus proche. C'était le cas sur toutes les plateformes depuis l'arrivée des extraits.
- **Reprendre affiche une flèche de téléchargement** (⬇) au lieu de ▶, pour ne plus ressembler à la lecture d'une vidéo. ▶ sert désormais uniquement à lire un fichier terminé.
- **Le cours Udemy complet** récupère aussi les fichiers du cours : les leçons PDF, livre numérique, diapositives et code, ainsi que les ressources jointes aux leçons, chacune juste après sa leçon.
- **Les détails** affichent de nouveau le lien source ; un lien qui contient une connexion ou un jeton affiche une note à la place.
- **Nouvelle icône** de l'application sur toutes les plateformes.

**Sécurité**
- Supprimer un téléchargement inachevé ou en échec avec « supprimer aussi les fichiers » ne supprime plus un ancien fichier à vous portant le même nom. Supprimer un fichier restauré depuis une sauvegarde vérifie d'abord qu'il s'agit bien du fichier de ce téléchargement.
- Un téléchargement qui n'a pas pu être mis à sa place ne laisse plus un fichier tronqué sous le nom final, et une taille incorrecte à la fin ne jette plus les parties déjà téléchargées.
- Les archives 7z et RAR sont vérifiées avant l'extraction (pas de liens, pas de chemins qui sortent du dossier, limites de taille), et le mot de passe de l'archive n'est plus visible par les autres programmes.
- La connexion locale avec l'extension de navigateur exige toujours l'appairage ; l'API d'automatisation ne peut être activée que par vous, pas par l'API elle-même.
- Des paramètres momentanément impossibles à déchiffrer ne remplacent plus l'appairage de l'extension.

**Android**
- **« L'extrait seulement » fonctionne de nouveau :** depuis la 1.5.0, « L'extrait seulement » échouait sur Android avec « ffmpeg exited with code 8 » sur tous les sites, car la version allégée de ffmpeg construite pour l'application n'avait pas accès au réseau. ffmpeg lit de nouveau la partie de la vidéo depuis le site, et si cela échoue un jour, l'application télécharge la vidéo entière et la coupe exactement au lieu d'échouer.
- **Assemblage des parties de vidéo :** depuis la 1.5.0, certains sites qui envoient une vidéo en parties (HLS) échouaient avec « dimensions not set ». Corrigé.
- Un extrait coupé exactement sur le téléphone n'est plus beaucoup plus lourd que sa partie de la vidéo (un extrait de 5 secondes d'une petite vidéo faisait 1,6 Mo).
- Dans le lecteur, un glissement sur les bords latéraux règle le volume ou la luminosité au lieu de faire Retour, et Retour ferme d'abord un menu ouvert avant le lecteur.
- Les liens ouverts depuis d'autres applications ou des pages web affichent toujours d'abord la boîte d'ajout, un fichier torrent ouvert depuis une autre application est d'abord vérifié, et la question sur la batterie n'est posée qu'une seule fois.

**Windows, macOS et Linux**
- Les bibliothèques du lecteur et du PDF se chargent uniquement depuis les dossiers de FetchJet, jamais depuis le dossier courant ni le PATH.
- macOS et Linux : les mises à jour et les fichiers temporaires utilisent un dossier que vous seul pouvez ouvrir, et une mise à jour est vérifiée de nouveau juste avant son installation.
- Le choix de « Couper l'audio » affiche les deux options sur toute la largeur.

**iOS**
- **« L'extrait seulement » :** un extrait pouvait être enregistré comme un tout petit fichier illisible. L'application le détecte maintenant, télécharge la vidéo entière et la coupe exactement.
- Le navigateur intégré coupe son son et libère sa mémoire quand vous le fermez.

**Extension de navigateur 3.8.0**
- **Ouvrir dans FetchJet :** une flèche à côté du bouton de téléchargement de la page et une ligne « Ouvrir dans FetchJet » envoient aussitôt le lien de la page à l'application, et vous y choisissez la qualité. Cela marche aussi sur les pages dont les qualités ne peuvent pas être listées, par exemple une leçon Udemy.
- **Ouvrir FetchJet directement :** une nouvelle option remet les liens à l'application sans passer par le pont local.
- **Canva :** chaque vidéo en lecture reçoit le bouton de téléchargement, nommé d'après sa page ; son image et son son sont enregistrés dans un seul fichier.
- **Page d'accueil de YouTube :** le bouton d'une vidéo qui se lance au survol ne clignote plus quand vous passez la souris dessus, et il appartient toujours à la vidéo sous la souris. Avant, il disparaissait puis revenait, et pouvait afficher les qualités de la première vidéo sur une autre.
- **Udemy :** le flux d'une leçon porte le nom de la leçon au lieu d'un code aléatoire.
- **Plus sûre :** seules les pages de l'extension peuvent modifier ses réglages, les cookies d'une page ne sont envoyés que pour ce même site, et la lecture d'un cours Udemy ne fonctionne que sur udemy.com.

**Petits changements**
- Un appui long sur un téléchargement dans l'écran Masqués ouvre un menu. Toutes les listes partagent désormais un même menu de ligne, organisé en sections, avec Principal en premier.
- Android : si un fichier a été téléchargé par une autre installation de FetchJet et ne peut pas être lu, FetchJet ouvre maintenant la page « Autoriser l'accès à tous les fichiers » au lieu d'échouer en silence.
- La boîte d'ajout n'a plus l'interrupteur « Télécharger en masqué ».
