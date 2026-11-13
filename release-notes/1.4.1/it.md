## Novità della 1.4.1

**Download**
- **Download FTP e FTPS (nuovo):** i link che iniziano con ftp://, ftps:// o ftpes:// ora si scaricano come qualsiasi link diretto, in più parti e con ripresa quando il server lo consente. Metti l'accesso nel link, salvalo in Impostazioni > Rete > Accessi ai siti, oppure lascialo vuoto per i server anonimi. Da sapere: i link FTP non passano dal proxy; nelle app desktop FTP su TLS viene scaricato con il curl del sistema; FTP su TLS non è ancora disponibile su iPhone e iPad.
- **Un file che cambia sul server:** se il file viene sostituito sul server mentre si scarica, FetchJet se ne accorge. Il download si ferma con un messaggio chiaro invece di salvare un file misto e rotto, e Riprendi scarica la nuova versione.
- **Niente più attese alla fine:** la fine di un download non aspetta più una sola connessione lenta. Una connessione che arranca alla fine viene sostituita.
- **Controlli prima del salvataggio:** incolla un checksum (SHA-256 o MD5) nella finestra Aggiungi, oppure lascia decidere l'intestazione checksum del server. Un file che non corrisponde non viene salvato. I file ZIP vengono controllati voce per voce, e una parte danneggiata viene scaricata di nuovo una volta.
- **I limiti del server vengono ricordati:** un sito che consente poche connessioni viene ricordato per un giorno, così il download successivo non parte con connessioni che rifiuta. Dopo un 403, FetchJet può riprovare via IPv4 (Windows, Android e Linux).
- **Prova il link:** un pulsante nella finestra Aggiungi dice se il link funziona, la sua dimensione e se può essere ripreso, senza scaricarlo.
- **Una cartella per tipo di file:** la cartella scelta per video, musica o archivi viene ricordata solo per quel tipo.
- **Riproduzione dall'elenco:** un file video o audio completato mostra ora un pulsante ▶ che lo apre nel tuo lettore.
- **Niente nomi di file inventati:** un link senza nome di file mostra il campo «Nome file» vuoto, e l'avvio attende che tu ne scriva uno.
- **Si adatta al tuo schermo:** la finestra del desktop ora si apre nello schermo visibile invece che in parte sotto la barra delle applicazioni.

**Torrent**
- **Dettagli:** rapporto di condivisione, quantità inviata, parti e dimensione delle parti, tracker con il loro stato, peer, "Verifica di nuovo", copia del link magnet e "prime e ultime parti per prime" per l'anteprima dei video.
- **Aggiungi un tracker** a un torrent in corso e **smetti di condividere a un rapporto** a tua scelta (Impostazioni > BitTorrent).
- **Più sicuro:** un torrent i cui file verrebbero scritti fuori dalla sua cartella viene rifiutato.
- **Dietro un proxy HTTP:** i torrent che hanno solo tracker UDP lì non trovano peer; Impostazioni > Rete e il messaggio di errore ora lo dicono.

**Desktop**
- **Tastiera:** frecce, Shift, Home / End, Page Up / Down, Enter (Alt+Enter apre o chiude i dettagli), Ctrl+R / P / O / E / N / F / A, Delete, Esc e F1 per l'elenco delle scorciatoie. Funzionano anche con le tastiere araba, persiana e russa, e così anche copia / incolla in ogni campo.
- **Riga di comando:** `/d` link, `/p` cartella, `/f` nome del file, `/a` aggiungi in pausa senza la finestra Aggiungi, `/n` aggiungi e avvia subito senza finestra, `/s` avvia tutto ciò che è in attesa.
- **Limite di velocità solo mentre usi il computer,** e **riattiva il computer per i download programmati** (Windows).
- **Cose più piccole:** la versione nel titolo della finestra, Esc chiude la finestra Aggiungi, i badge del tipo di file sono leggibili nel tema chiaro, e il titolo Invia a un dispositivo è tradotto ovunque.

**Audio**
- **OGG:** scegliere OGG per un audio di YouTube (Opus) non lo ricodifica più. Viene salvato subito, con la qualità e le dimensioni originali.
- **Comprimi audio:** ora offre gli stessi formati di Converti (M4A, MP3, Opus, OGG, FLAC, WAV), mantiene OGG quando il file è OGG, e la dimensione stimata è più vicina a quella reale.

**Estensione browser (3.4)**
- **Connessione:** quando il browser non è ancora collegato all'app, l'estensione lo dice subito ("Apri FetchJet e tocca Consenti") invece di caricare all'infinito. Dopo aver toccato Consenti, le qualità compaiono da sole. Se il browser chiede "Aprire FetchJet?", la scheda ora resta finché non rispondi. In Impostazioni > Estensione browser c'è "Dissocia tutti".
- **Il browser tiene un download finché l'app non lo ha,** i moduli che hai inviato restano nel browser, e le finestre private usano i propri cookie.
- **Link:** un link a un file viene preso dopo che il codice di clic della pagina è stato eseguito, quindi i siti che prima rispondevano 403 ora funzionano. Un link a un PDF si apre nel browser a meno che la pagina lo segni come download. "Scarica tutti i link" trova anche i link segnati come download e quelli conservati negli attributi data-*, e Google Drive ha una riga "File originale".
- **Il pulsante video:** funziona con la tastiera (Tab, Enter), si aggancia a un bordo quando lo trascini lì, c'è un pulsante per ogni gruppo di video sovrapposti e nessuno su un video nascosto sotto la pagina. Puoi nasconderlo su un sito, e Telegram Web spiega perché non si può scaricare.

**Aggiornamenti**
- **La finestra di aggiornamento** segue la lingua dell'app, e le note di versione sono tradotte.
