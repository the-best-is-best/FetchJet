## Novità della 1.5.0

**Archivi**
- **Estrai dopo il download:** i file ZIP, TAR, GZ e XZ possono essere estratti da soli quando finiscono, oppure con il nuovo pulsante Estrai sulla riga. Sul desktop funzionano anche 7Z, RAR, gli archivi divisi (.001, .part1.rar, .z01) e gli archivi con password quando 7-Zip è installato; FetchJet chiede la password.
- **Dove finiscono i file:** in una cartella con il nome dell'archivio, sempre in una cartella, oppure accanto all'archivio. Puoi anche spostare l'archivio nel Cestino dopo l'estrazione.

**Download**
- **Menu del clic destro su un download** (pressione prolungata sui telefoni): apri, apri la cartella, copia il link, metti in pausa, riprendi, elimina e altro.
- **Cartelle per sito:** manda tutto ciò che arriva da un sito a tua scelta in una cartella tutta sua.
- **I file eliminati finiscono nel Cestino** (anche su macOS e Linux) invece di sparire. Puoi disattivarlo in Impostazioni.
- **Scansione dopo il download:** Windows controlla un file finito con il tuo antivirus, e un download bloccato dall'antivirus si ferma subito con un messaggio chiaro. Su macOS i file scaricati ed estratti hanno il solito contrassegno "scaricato da Internet".
- **Resoconto mensile:** Impostazioni > Download mostra, per ogni mese, quanti file hai scaricato, la loro dimensione totale e i siti da cui provengono. Il conteggio avviene solo sul tuo dispositivo.

**Rete (desktop)**
- **Il tuo DNS:** scegli un server DNS per i download di FetchJet (DNS over HTTPS o un normale server DNS) e quale scheda di rete usano. Riguarda solo i download di FetchJet, non il resto del computer.

**Windows**
- **Nuovi programmi di installazione:** un Setup.exe e un nuovo MSI in cui scegli se creare un collegamento sul desktop e nel menu Start. Ti dicono se questa versione, una più recente o una più vecchia è già installata, e possono avviare FetchJet alla fine.
- **Versione portatile:** un .zip che funziona senza installazione, con i dati in una cartella accanto.
- **ffmpeg più recente** su Windows e Linux (lo strumento che unisce video e audio). Si aggiorna da solo al prossimo avvio di FetchJet.

**Android**
- **App più leggera:** circa 53 MB invece di 83 MB sulla maggior parte dei telefoni, e occupa meno spazio una volta installata.

**iPhone e iPad**
- **Pagine che richiedono l'accesso, come le registrazioni Zoom:** apri la pagina dentro FetchJet, inserisci il passcode una volta e scarica con la sessione della pagina. Il pulsante della pagina in Safari ha anche "Apri questa pagina in FetchJet".

**Per utenti esperti**
- **Riga di comando:** `FetchJet status`, `pause`, `start`, `remove` e `delete` controllano un FetchJet in esecuzione. Sono disattivati finché non esegui `FetchJet automation on`.

**Estensione browser (3.5)**
- Prende tutti i download del browser, a meno che tu non la limiti ai tipi di file della tua lista.
- Un pulsante di download sulle registrazioni Zoom solo audio.
- I player dentro i frame trovano il loro video in modo più affidabile.

**Privacy**
- FetchJet ora invia un messaggio anonimo al giorno, così sappiamo quante persone lo usano: un numero casuale per questa installazione, la versione dell'app e il sistema; il server ricava il paese dalla connessione. Nessun link, file o dato personale. I totali sono su una pagina pubblica collegata dal README.
