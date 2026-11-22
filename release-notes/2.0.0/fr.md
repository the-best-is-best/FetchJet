## Nouveautés de la 2.0.0

**Une boîte d'ajout plus intelligente**
- **FetchJet choisit le moteur d'après le lien :** un fichier, un lien magnet ou un site vidéo connu part tout seul vers Direct, Torrent ou Media. Il vérifie d'abord le lien une fois (« Vérification du lien… »), et le choix Direct / Media n'apparaît que pour une page avec une vidéo, ou un lien qu'il n'a pas pu vérifier.
- **Plus de vérification à chaque lettre tapée :** la vérification démarre quand vous arrêtez de taper (tout de suite pour un lien collé). Changer le lien annule la lecture précédente, et la boîte ne reste plus bloquée sur « Recherche du nom… », par exemple avec un magnet qui ne trouve aucun pair.

**Trouver les fichiers de cette page**
- **Nouveau :** collez le lien d'une page et utilisez « Trouver les fichiers de cette page ». FetchJet liste les fichiers de la page : images, liens et adresses écrites en texte simple. Pour les liens sans nom de fichier, il demande au serveur le vrai nom et la taille. Si vous le demandez, il lit aussi les pages vers lesquelles elle pointe, un ou deux niveaux plus bas.
- **Résultats pendant la lecture :** les fichiers apparaissent au fil de la lecture, et « Arrêter la lecture » garde ce qui a été trouvé.
- **Choisissez ce qui est téléchargé :** les types de fichiers sont des boutons qui masquent ou affichent ces fichiers, et les fichiers masqués ne sont pas téléchargés. Chaque ligne indique son type (ZIP, MP3, PNG…).
- **Enregistrés en groupe :** les fichiers vont dans un dossier et un groupe portant le nom de la page. Plus tard, « Chercher de nouveaux fichiers » marque ceux que vous avez déjà et les verrouille (« N déjà téléchargés »).
- **Les pages de versions GitHub** sont lues en une seule requête.
- **Pages illisibles :** les pages que le site a refusées (une vérification ou un blocage) sont comptées. Les pages qui construisent leurs liens avec JavaScript sont nommées, avec une indication pour les ouvrir dans votre navigateur et utiliser « Télécharger tous les liens » de l'extension.

**Listes de lecture et sous-titres**
- **Listes de lecture :** les vidéos déjà téléchargées sont marquées et comptées (« N déjà téléchargés »), et une liste enregistrée en groupe peut être vérifiée pour y trouver de nouvelles vidéos.
- **Sous-titres :** choisissez « Intégrer à la vidéo » ou « Fichier séparé », puis SRT ou VTT. Les langues sont demandées une à une, et une langue que le site refuse un instant est redemandée. Vous pouvez aussi ajouter des sous-titres à une vidéo terminée, depuis ses détails ou le menu de sa ligne.
- **« Envoyer vers un appareil » garde les sous-titres :** une vidéo terminée envoyée à un autre appareil y propose toujours « Ajouter des sous-titres », sait quelles langues sont déjà dans le fichier, et les fichiers de sous-titres placés à côté voyagent avec elle.

**Vidéos**
- **Google Drive :** tout lien de fichier Drive se télécharge directement, et un fichier dont le propriétaire a désactivé le téléchargement le dit.
- **Vidéos protégées :** une vidéo protégée par DRM est signalée comme telle avant la liste des qualités, au lieu d'échouer plus tard.
- **Vidéos lisibles partout :** le réglage (désormais dans Paramètres > Outils média, et aussi sur Android) choisit des fichiers H.264 + AAC que tout téléviseur et tout lecteur ouvre, ou des fichiers AV1 / VP9 plus petits.

**Paramètres**
- **Réorganisés :** Téléchargements (emplacement, connexions, file d'attente), une nouvelle page Limites de vitesse (préréglages, limite en cas d'inactivité, planification) et une nouvelle page Fichiers et dossiers (tri par type, regroupement par dossier, extraction, corbeille, analyse, dossiers par site).

**Vitesse**
- Les gros fichiers utilisent par défaut jusqu'à 32 connexions.
- Une requête qui ne reçoit aucune donnée pendant 20 secondes est abandonnée puis redemandée : une connexion bloquée ne fige plus un téléchargement pendant une minute.
- Sur les transferts courts et rapides, une connexion lente est remplacée plus tôt.
- macOS autorise 64 connexions par site : un deuxième téléchargement depuis le même site n'attend plus.

**Petits changements**
- « Quand les téléchargements sont terminés » attend aussi une archive en cours d'extraction ou un fichier en cours d'analyse.
- Un torrent au nom long n'étire plus sa carte de fichiers, et les lignes de ses fichiers affichent d'abord le nom du fichier.
- Les liens et les chemins de dossiers gardent leur sens en arabe et en persan.
- Android : une liste de lecture lue en arrière-plan ne fait plus planter l'application.
- La fenêtre du bureau se redessine entièrement après son affichage ou un changement de taille, sans zones non peintes.
- L'application macOS est compilée pour fonctionner sous macOS 12 et versions ultérieures.
- **Android : plus de liste cassée après une réinstallation ou un nouveau téléphone :** la liste des téléchargements n'est plus restaurée depuis une sauvegarde, car ses liens sont liés au téléphone et ne pouvaient pas y être lus. Les fichiers téléchargés restent dans votre dossier.
- **Message plus clair pour les sous-titres :** quand le lien d'un ancien téléchargement ne peut pas être lu, « Ajouter des sous-titres » indique qu'il n'a pas pu lire les sous-titres au lieu de dire que la vidéo n'en a pas.
