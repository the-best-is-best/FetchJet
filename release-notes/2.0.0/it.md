## Novità della 2.0.0

**Una finestra di aggiunta più intelligente**
- **FetchJet sceglie il motore dal link:** un file, un link magnet o un sito video noto va da solo a Direct, Torrent o Media. Prima controlla il link una volta («Controllo del link…»), e la scelta tra Direct e Media compare solo per una pagina con un video o per un link che non è stato possibile controllare.
- **Niente più controllo a ogni lettera digitata:** il controllo parte quando smetti di scrivere (subito per un link incollato). Cambiare il link annulla la lettura precedente, quindi la finestra non resta più bloccata su «Ricerca del nome…», per esempio con un magnet che non trova peer.

**Trova i file in questa pagina**
- **Novità:** incolla il link di una pagina e usa «Trova i file in questa pagina». FetchJet elenca i file della pagina: immagini, link e indirizzi scritti come testo semplice. Per i link senza nome di file chiede al server il nome vero e la dimensione. Se lo chiedi, legge anche le pagine collegate, a uno o due livelli di distanza.
- **Risultati durante la lettura:** i file compaiono mentre la pagina viene letta, e «Interrompi la lettura» conserva quanto trovato.
- **Scegli cosa scaricare:** i tipi di file sono pulsanti che nascondono o mostrano quei file, e i file nascosti non vengono scaricati. Ogni riga mostra il suo tipo (ZIP, MP3, PNG…).
- **Salvati come gruppo:** i file vanno in una cartella e in un gruppo con il nome della pagina. In seguito «Cerca nuovi file» segna quelli che hai già e li blocca («N già scaricati»).
- **Le pagine delle release di GitHub** vengono lette con una sola richiesta.
- **Pagine che non si possono leggere:** le pagine rifiutate dal sito (un controllo o un blocco) vengono contate. Le pagine che costruiscono i link con JavaScript vengono nominate, con il suggerimento di aprirle nel browser e usare «Scarica tutti i link» dell'estensione.

**Playlist e sottotitoli**
- **Playlist:** i video già scaricati vengono segnati e contati («N già scaricati»), e una playlist salvata come gruppo può essere controllata per cercare nuovi video.
- **Sottotitoli:** scegli «Incorpora nel video» o «File separato», poi SRT o VTT. Le lingue vengono richieste una alla volta, e una lingua che il sito rifiuta per un momento viene richiesta di nuovo. Puoi anche aggiungere sottotitoli a un video concluso, dai suoi dettagli o dal menu della sua riga.
- **«Invia al dispositivo» mantiene i sottotitoli:** un video finito inviato a un altro dispositivo offre lì ancora «Aggiungi sottotitoli», sa quali lingue sono già nel file, e i file dei sottotitoli accanto ad esso viaggiano con lui.

**Video**
- **Google Drive:** qualsiasi link a un file di Drive si scarica direttamente, e un file di cui il proprietario ha disattivato il download lo dice.
- **Video protetti:** un video protetto da DRM viene indicato come tale prima dell'elenco delle qualità, invece di fallire più tardi.
- **Video riproducibili ovunque:** l'interruttore (ora in Impostazioni > Strumenti media, e anche su Android) sceglie file H.264 + AAC che ogni TV e lettore apre, oppure file AV1 / VP9 più piccoli.

**Impostazioni**
- **Riorganizzate:** Download (posizione, connessioni, coda), una nuova pagina Limiti di velocità (preset, limite a riposo, pianificazione) e una nuova pagina File e cartelle (ordina per tipo, raggruppa per cartella, estrai, Cestino, scansione, cartelle per sito).

**Velocità**
- I file grandi usano di default fino a 32 connessioni.
- Una richiesta che per 20 secondi non riceve dati viene lasciata e richiesta di nuovo, così una connessione bloccata non ferma più un download per un minuto.
- Nei trasferimenti brevi e veloci una connessione lenta viene sostituita prima.
- macOS consente 64 connessioni per sito, quindi un secondo download dallo stesso sito non aspetta più.

**Piccole cose**
- «Al termine dei download» aspetta anche un archivio in fase di estrazione o un file in fase di scansione.
- Un torrent con un nome lungo non allarga più la sua scheda dei file, e le righe dei suoi file mostrano prima il nome del file.
- Link e percorsi delle cartelle mantengono la loro direzione in arabo e persiano.
- Android: una playlist letta in background non manda più in crash l'app.
- La finestra del desktop viene ridisegnata per intero dopo essere stata mostrata o ridimensionata, senza zone non disegnate.
- L'app per macOS è compilata per funzionare da macOS 12 in poi.
- **Android: niente elenco rotto dopo una reinstallazione o un nuovo telefono:** l'elenco dei download non viene più ripristinato da un backup, perché i suoi link sono legati al telefono e non potevano essere letti altrove. I file scaricati restano nella tua cartella.
- **Messaggio più chiaro sui sottotitoli:** quando il link di un vecchio download non può essere letto, «Aggiungi sottotitoli» dice che non ha potuto leggere i sottotitoli invece di dire che il video non ne ha.
- **Coda più veloce:** il download successivo in attesa parte appena si libera un posto (50 file piccoli: da 16 s a meno di 1 s), e l'unione delle parti di un file è più rapida.
- **Una pagina Altro** sostituisce Impostazioni nella navigazione: versione e aggiornamento, Impostazioni, FetchJet su ogni dispositivo, Condividi FetchJet, e Community e assistenza.
- **Stesso nome file:** due download che finiscono con lo stesso nome in una cartella ora vengono salvati come «nome (1).ext», e un download non può più sparire da una coda lunga.
- **Un font per lingua** su tutte le piattaforme (l'app pesa circa 7 MB in più).
- **Siti video ricordati:** un sito in cui un controllo Media ha trovato delle qualità viene trattato come sito video la volta dopo, e le dimensioni delle qualità HLS sono stimate e mostrate con ~.
- **La finestra di aggiunta riparte vuota** dopo la chiusura.
- **Windows:** niente più finestre di console che lampeggiano, ogni versione ha SHA256SUMS.txt e la versione portatile si aggiorna da sola.
- **Anche dalla 1.5.1:** il controllo degli aggiornamenti del Microsoft Store, i nomi di file arabi negli ZIP creati su un Mac e i torrent su iOS tramite proxy.
