## Neu in 2.0.0

**Ein klügerer Hinzufügen-Dialog**
- **FetchJet wählt die Engine anhand des Links:** Eine Datei, ein Magnet-Link oder eine bekannte Videoseite landet von selbst bei Direct, Torrent oder Media. Der Link wird zuerst einmal geprüft („Link wird geprüft…“); die Auswahl zwischen Direct und Media erscheint nur bei einer Seite mit Video oder bei einem Link, der sich nicht prüfen ließ.
- **Keine Prüfung mehr bei jedem getippten Buchstaben:** Die Prüfung beginnt, wenn Sie aufhören zu tippen (bei einem eingefügten Link sofort). Ändern Sie den Link, wird das alte Auslesen abgebrochen. Der Dialog bleibt so nicht mehr bei „Name wird gesucht…“ hängen, etwa bei einem Magnet-Link, der keine Quellen findet.

**Dateien auf dieser Seite finden**
- **Neu:** Fügen Sie einen Seitenlink ein und wählen Sie „Dateien auf dieser Seite finden“. FetchJet listet die Dateien der Seite auf: Bilder, Links und als Text geschriebene Adressen. Bei Links ohne Dateinamen fragt es den Server nach echtem Namen und Größe. Auf Wunsch liest es auch die verlinkten Seiten, eine oder zwei Ebenen tief.
- **Ergebnisse schon beim Lesen:** Die Dateien erscheinen, während die Seite gelesen wird, und „Lesen stoppen“ behält, was gefunden wurde.
- **Auswählen, was geladen wird:** Dateitypen sind Schaltflächen, die diese Dateien aus- oder einblenden; ausgeblendete Dateien werden nicht heruntergeladen. Jede Zeile zeigt ihren Typ (ZIP, MP3, PNG…).
- **Als Gruppe gespeichert:** Die Dateien kommen in einen Ordner und eine Gruppe mit dem Namen der Seite. Später markiert „Nach neuen Dateien suchen“, was Sie schon haben, und sperrt es („N bereits heruntergeladen“).
- **GitHub-Release-Seiten** werden mit einer einzigen Anfrage gelesen.
- **Seiten, die sich nicht lesen lassen:** Seiten, die die Website abgewiesen hat (Prüfung oder Sperre), werden gezählt. Seiten, die ihre Links mit JavaScript aufbauen, werden genannt, mit dem Hinweis, sie im Browser zu öffnen und „Alle Links herunterladen“ der Erweiterung zu nutzen.

**Playlists und Untertitel**
- **Playlists:** Videos, die Sie schon heruntergeladen haben, werden markiert und gezählt („N bereits heruntergeladen“). Eine als Gruppe gespeicherte Playlist lässt sich auf neue Videos prüfen.
- **Untertitel:** Wählen Sie „Ins Video einbetten“ oder „Separate Datei“, dann SRT oder VTT. Die Sprachen werden nacheinander abgefragt, und eine Sprache, die die Seite kurz verweigert, wird erneut angefragt. Außerdem können Sie einem fertigen Video Untertitel hinzufügen, über seine Details oder sein Zeilenmenü.
- **„An Gerät senden“ behält die Untertitel:** Ein fertiges Video, das an ein anderes Gerät gesendet wird, bietet dort weiterhin „Untertitel hinzufügen“ an, weiß, welche Sprachen schon in der Datei sind, und die Untertiteldateien daneben werden mitgeschickt.

**Videos**
- **Google Drive:** Jeder Drive-Dateilink wird direkt heruntergeladen, und eine Datei, bei der der Besitzer das Herunterladen abgeschaltet hat, sagt das auch.
- **Geschützte Videos:** Ein durch DRM geschütztes Video wird vor der Qualitätsliste als solches benannt, statt später zu scheitern.
- **Videos, die überall laufen:** Der Schalter (jetzt unter Einstellungen > Medien-Tools und auch auf Android) wählt H.264 + AAC, das jeder Fernseher und Player öffnet, oder kleinere AV1- / VP9-Dateien.

**Einstellungen**
- **Neu geordnet:** Downloads (Ort, Verbindungen, Warteschlange), eine neue Seite Tempolimits (Voreinstellungen, Limit bei Inaktivität, Zeitplan) und eine neue Seite Dateien & Ordner (nach Typ sortieren, in Ordnern gruppieren, entpacken, Papierkorb, Scan, Ordner nach Website).

**Geschwindigkeit**
- Große Dateien nutzen standardmäßig bis zu 32 Verbindungen.
- Eine Anfrage, die 20 Sekunden lang keine Daten bekommt, wird abgebrochen und neu gestellt; eine hängende Verbindung hält einen Download nicht mehr eine Minute lang auf.
- Bei kurzen, schnellen Übertragungen wird eine langsame Verbindung früher ersetzt.
- macOS erlaubt 64 Verbindungen pro Website, sodass ein zweiter Download von derselben Seite nicht mehr wartet.

**Kleinere Dinge**
- „Wenn Downloads fertig sind“ wartet auch auf ein Archiv, das gerade entpackt wird, oder eine Datei, die gerade geprüft wird.
- Ein Torrent mit langem Namen dehnt seine Dateikarte nicht mehr, und die Dateizeilen zeigen zuerst den Dateinamen.
- Links und Ordnerpfade behalten ihre Richtung in Arabisch und Persisch.
- Android: Eine im Hintergrund gelesene Playlist bringt die App nicht mehr zum Absturz.
- Das Desktop-Fenster wird nach dem Einblenden oder Ändern der Größe vollständig neu gezeichnet, ohne ungezeichnete Flecken.
- Die macOS-App ist so gebaut, dass sie ab macOS 12 läuft.
- **Android: keine kaputte Liste nach Neuinstallation oder neuem Handy:** Die Download-Liste wird nicht mehr aus einer Sicherung wiederhergestellt, weil ihre Links an das Handy gebunden sind und dort nicht gelesen werden konnten. Die heruntergeladenen Dateien bleiben in Ihrem Ordner.
- **Klarere Untertitel-Meldung:** Wenn der Link eines alten Downloads nicht gelesen werden kann, sagt „Untertitel hinzufügen“, dass die Untertitel nicht gelesen werden konnten, statt zu sagen, das Video habe keine.
