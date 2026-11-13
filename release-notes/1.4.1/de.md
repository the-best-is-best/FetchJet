## Neu in 1.4.1

**Downloads**
- **FTP- und FTPS-Downloads (neu):** Links, die mit ftp://, ftps:// oder ftpes:// beginnen, werden jetzt wie jeder direkte Link geladen, in mehreren Teilen und mit Fortsetzen, wenn der Server es erlaubt. Die Anmeldung kann im Link stehen, unter Einstellungen > Netzwerk > Website-Anmeldungen gespeichert werden oder bei anonymen Servern ganz entfallen. Gut zu wissen: FTP-Links laufen nicht über den Proxy; in den Desktop-Apps wird FTP über TLS mit dem curl des Systems geladen; FTP über TLS ist auf iPhone und iPad noch nicht verfügbar.
- **Eine Datei, die sich auf dem Server ändert:** Wird die Datei während des Downloads auf dem Server ersetzt, bemerkt FetchJet das. Der Download stoppt mit einer klaren Meldung, statt eine gemischte, defekte Datei zu speichern, und Fortsetzen lädt die neue Version.
- **Kein Warten mehr am Ende:** Das Ende eines Downloads wartet nicht mehr auf eine einzelne langsame Verbindung. Eine Verbindung, die am Ende nur noch kriecht, wird ersetzt.
- **Prüfungen vor dem Speichern:** Füge im Dialog Hinzufügen eine Prüfsumme (SHA-256 oder MD5) ein oder lass den Prüfsummen-Header des Servers entscheiden. Eine Datei, die nicht passt, wird nicht gespeichert. ZIP-Dateien werden Eintrag für Eintrag geprüft, und ein beschädigter Teil wird einmal neu geladen.
- **Server-Grenzen werden gemerkt:** Eine Website, die nur wenige Verbindungen erlaubt, wird einen Tag lang gemerkt, damit der nächste Download nicht mit Verbindungen startet, die sie ablehnt. Nach einem 403 kann FetchJet es über IPv4 erneut versuchen (Windows, Android und Linux).
- **Link testen:** Eine Schaltfläche im Dialog Hinzufügen zeigt, ob der Link funktioniert, wie groß die Datei ist und ob sie fortgesetzt werden kann, ohne sie zu laden.
- **Ordner pro Dateityp:** Der Ordner, den du für Videos, Musik oder Archive wählst, wird nur für diesen Typ gemerkt.

**Torrents**
- **Details:** Teilverhältnis, hochgeladene Menge, Teile und Teilgröße, Tracker mit ihrem Status, Peers, "Erneut prüfen", Magnet-Link kopieren und "Erste und letzte Teile zuerst" für die Vorschau von Videos.
- **Tracker hinzufügen** bei einem laufenden Torrent und **Teilen bei einem gewählten Verhältnis beenden** (Einstellungen > BitTorrent).
- **Sicherer:** Ein Torrent, dessen Dateien außerhalb seines eigenen Ordners geschrieben würden, wird abgelehnt.
- **Hinter einem HTTP-Proxy:** Torrents, die nur UDP-Tracker haben, finden dort keine Peers; Einstellungen > Netzwerk und die Fehlermeldung sagen das jetzt.

**Desktop**
- **Tastatur:** Pfeiltasten, Shift, Home / End, Page Up / Down, Enter (Alt+Enter öffnet oder schließt die Details), Ctrl+R / P / O / E / N / F / A, Delete, Esc und F1 für die Liste der Tastenkürzel. Sie funktionieren auch mit arabischen, persischen und russischen Tastaturen, ebenso Kopieren / Einfügen in jedem Feld.
- **Befehlszeile:** `/d` Link, `/p` Ordner, `/f` Dateiname, `/a` pausiert hinzufügen ohne den Dialog Hinzufügen, `/n` ohne Fenster hinzufügen und sofort starten, `/s` alles starten, was wartet.
- **Geschwindigkeitsbegrenzung nur während du den Computer benutzt** und **Computer für geplante Downloads aufwecken** (Windows).
- **Kleinere Dinge:** die Version im Fenstertitel, Esc schließt den Dialog Hinzufügen, Dateityp-Symbole sind im hellen Design gut lesbar, und der Titel "An Gerät senden" ist überall übersetzt.

**Audio**
- **OGG:** Wer OGG für ein YouTube-Audio (Opus) wählt, löst keine neue Kodierung mehr aus. Es wird sofort gespeichert, in Originalqualität und -größe.
- **Audio komprimieren:** bietet jetzt dieselben Formate wie Umwandeln (M4A, MP3, Opus, OGG, FLAC, WAV), behält OGG bei, wenn die Datei OGG ist, und die geschätzte Größe liegt näher an der echten.

**Browser-Erweiterung (3.4)**
- **Verbindung:** Ist der Browser noch nicht mit der App verbunden, sagt die Erweiterung das sofort ("FetchJet öffnen und auf Erlauben tippen"), statt endlos zu laden. Nach dem Tippen auf Erlauben erscheinen die Qualitäten von selbst. Fragt der Browser "FetchJet öffnen?", bleibt der Tab jetzt bestehen, bis du antwortest. Unter Einstellungen > Browser-Erweiterung gibt es "Alle trennen".
- **Der Browser behält einen Download, bis die App ihn hat,** gesendete Formulare bleiben im Browser, und private Fenster nutzen ihre eigenen Cookies.
- **Links:** Ein Dateilink wird übernommen, nachdem der eigene Klick-Code der Seite gelaufen ist, daher funktionieren Seiten, die früher 403 meldeten. Ein PDF-Link öffnet sich im Browser, außer die Seite markiert ihn als Download. "Alle Links herunterladen" findet auch als Download markierte Links und Links in data-*-Attributen, und Google Drive hat eine Zeile "Originaldatei".
- **Die Video-Schaltfläche:** Sie lässt sich per Tastatur bedienen (Tab, Enter), rastet an einem Rand ein, wenn du sie dorthin ziehst, es gibt eine Schaltfläche pro Gruppe übereinanderliegender Videos und keine auf einem Video, das unter der Seite versteckt ist. Du kannst sie auf einer Website ausblenden, und Telegram Web erklärt, warum es sich nicht herunterladen lässt.

**Updates**
- **Das Update-Fenster** folgt der Sprache der App, und die Versionshinweise sind übersetzt.
