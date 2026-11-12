## Neu in 1.4.1

- **FTP- und FTPS-Downloads (neu):** Links, die mit ftp://, ftps:// oder ftpes:// beginnen, werden jetzt wie jeder direkte Link geladen, in mehreren Teilen und mit Fortsetzen, wenn der Server es erlaubt. Die Anmeldung kann im Link stehen, unter Einstellungen > Netzwerk > Website-Anmeldungen gespeichert werden oder bei anonymen Servern ganz entfallen. Gut zu wissen: FTP-Links laufen nicht über den Proxy; in den Desktop-Apps nutzt FTP über TLS eine Verbindung; FTP über TLS ist auf iPhone und iPad noch nicht verfügbar.
- **Eine Datei, die sich auf dem Server ändert:** Wird die Datei während des Downloads auf dem Server ersetzt, bemerkt FetchJet das. Der Download stoppt mit einer klaren Meldung, statt eine gemischte, defekte Datei zu speichern, und "Fortsetzen" lädt die neue Version.
- **Kein Warten mehr am Ende:** Das Ende eines Downloads wartet nicht mehr auf eine einzelne langsame Verbindung. Eine Verbindung, die am Ende nur noch kriecht, wird ersetzt.
- **Audio als OGG:** Wer OGG für ein YouTube-Audio (Opus) wählt, bekommt es nicht mehr neu kodiert. Es wird sofort gespeichert, in Originalqualität und Originalgröße.
- **Audio komprimieren:** bietet jetzt dieselben Formate wie das Konvertieren (M4A, MP3, Opus, OGG, FLAC, WAV), behält OGG bei, wenn die Datei OGG ist, und die geschätzte Größe liegt näher an der tatsächlichen.
- **Browser-Erweiterung:** Ist der Browser noch nicht mit der App verbunden, sagt die Erweiterung das sofort ("FetchJet öffnen und auf Zulassen tippen"), statt endlos zu laden. Nach dem Tippen auf "Zulassen" erscheinen die Qualitäten von selbst, ohne Neuladen. Das Neuladen einer Videoseite versucht es nach einem Fehler erneut.
- **Einstellungen > Browser-Erweiterung:** "Alle trennen" trennt alle gekoppelten Browser; jeder fragt beim nächsten Mal wieder nach "Zulassen".
- **Update-Fenster:** Die Versionshinweise folgen der Sprache der App, wenn du sie änderst.
