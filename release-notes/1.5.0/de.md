## Neu in 1.5.0

**Archive**
- **Nach dem Download entpacken:** ZIP-, TAR-, GZ- und XZ-Dateien können nach dem Download automatisch entpackt werden, oder mit der neuen Schaltfläche Entpacken in der Zeile. Auf dem Desktop funktionieren auch 7Z, RAR, geteilte Archive (.001, .part1.rar, .z01) und Archive mit Passwort, wenn 7-Zip installiert ist; FetchJet fragt nach dem Passwort.
- **Wohin die Dateien kommen:** in einen Ordner mit dem Namen des Archivs, immer in einen Ordner oder neben das Archiv. Du kannst das Archiv nach dem Entpacken auch in den Papierkorb verschieben.

**Downloads**
- **Rechtsklick-Menü auf einem Download** (lange drücken auf Smartphones): öffnen, Ordner öffnen, Link kopieren, pausieren, fortsetzen, löschen und mehr.
- **Ordner nach Website:** Alles von einer Website oder von Links, die gleich beginnen (ein Kurs, ein Projekt), landet im Ordner deiner Wahl. Auch ein Kurs aus dem Browser folgt der Regel.
- **Gelöschte Dateien kommen in den Papierkorb** (auch unter macOS und Linux), statt zu verschwinden. Du kannst das in den Einstellungen abschalten.
- **Prüfung nach dem Download:** Windows prüft eine fertige Datei mit deinem Virenschutz, und ein Download, den der Virenschutz blockiert, stoppt sofort mit einer klaren Meldung. Unter macOS tragen heruntergeladene und entpackte Dateien die übliche Markierung "aus dem Internet geladen".

**Netzwerk (Desktop)**
- **Eigenes DNS:** Wähle einen DNS-Server für die Downloads von FetchJet (DNS über HTTPS oder ein normaler DNS-Server) und den Netzwerkadapter, den sie nutzen. Das betrifft nur die Downloads von FetchJet, nicht den Rest des Computers.

**Windows**
- **Neue Installer:** ein Setup.exe und ein neues MSI, bei denen du eine Verknüpfung auf dem Desktop und im Startmenü wählen kannst. Sie zeigen, ob diese, eine neuere oder eine ältere Version schon installiert ist, und können FetchJet am Ende starten.
- **Portable Version:** ein .zip, das ohne Installation läuft, mit seinen Daten in einem Ordner daneben.
- **Neueres ffmpeg** unter Windows und Linux (das Werkzeug, das Video und Audio zusammenführt). Es aktualisiert sich beim nächsten Start von FetchJet von selbst.

**Android**
- **Kleinere App:** etwa 53 MB statt 83 MB auf den meisten Smartphones, und sie braucht nach der Installation weniger Platz.

**iPhone und iPad**
- **Seiten mit Anmeldung, etwa Zoom-Aufzeichnungen:** Öffne die Seite in FetchJet, gib den Kenncode einmal ein und lade mit der Sitzung der Seite herunter. Die Seiten-Schaltfläche in Safari hat außerdem "Diese Seite in FetchJet öffnen".

**Für Fortgeschrittene**
- **Befehlszeile:** `FetchJet status`, `pause`, `start`, `remove` und `delete` steuern ein laufendes FetchJet. Sie sind aus, bis du `FetchJet automation on` ausführst.

**Browser-Erweiterung (3.5)**
- Übernimmt jeden Browser-Download, außer du beschränkst sie auf die Dateitypen deiner Liste.
- Eine Download-Schaltfläche auf reinen Audio-Aufzeichnungen von Zoom.
- Player in Frames finden ihr Video zuverlässiger.

**Datenschutz**
- FetchJet sendet jetzt einmal am Tag eine anonyme Nachricht, damit wir wissen, wie viele Menschen es nutzen: eine Zufallszahl für diese Installation, die App-Version und das System; der Server liest das Land aus der Verbindung. Keine Links, Dateien oder persönlichen Daten. Die Summen stehen auf einer öffentlichen Seite, die im README verlinkt ist.
