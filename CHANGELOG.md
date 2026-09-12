# Changelog

Tutte le modifiche rilevanti a questo progetto sono annotate qui.

Il formato segue [Keep a Changelog](https://keepachangelog.com/it/1.1.0/) e la
numerazione segue il [Versionamento Semantico](https://semver.org/lang/it/).

Le **decisioni** e le loro motivazioni stanno in [`CLAUDE.md`](CLAUDE.md); qui c'è
solo cosa è cambiato nel codice.

## [Non rilasciato]

### Aggiunto

- **Fondazione del progetto**: struttura Kotlin Multiplatform con Gradle 8.14.3 e
  wrapper incluso, target `jvm` (per i test su qualunque macchina) e `iosArm64` /
  `iosSimulatorArm64` / `iosX64`.
- **`core:model`** — il modello dati delle note scritte a mano:
  - `Note`, `Stroke`, `InkPoint`, `Pen`, `PenKind`, `CanvasSize`;
  - identificatori UUID generati sul dispositivo (`NoteId`, `StrokeId`) e campi
    `updatedAt` / `revision` / `deletedAt` presenti dal primo giorno, perché
    aggiungerli dopo imporrebbe una migrazione sui dati degli utenti (decisione D9);
  - `mergeNotes`: fusione di due versioni della stessa nota senza conflitti e senza
    perdita di tratti, con tombstone che vincono sulla resurrezione (decisione D8);
  - `orderStrokes`: ordine di disegno deterministico, evidenziatori sotto
    l'inchiostro, così due dispositivi disegnano la stessa nota in modo identico;
  - `Clock` iniettabile, per non leggere l'orologio di sistema dal core.
- **`core:ink`** — il motore d'inchiostro condiviso fra le due piattaforme:
  - `StrokeBuilder`: raccoglie i campioni del digitizer, scarta il rumore, riconosce
    la penna tenuta ferma come punto voluto e non perde il campione di stacco;
  - `CatmullRom`: ricampionamento su spline in parametrizzazione centripeta, che
    evita le cuspidi che l'interpolazione uniforme produce sulle curve strette;
  - `WidthProfile`: spessore da pressione dove il dispositivo la riporta, **da
    velocità quando non c'è** (scrittura col dito), con affilatura in entrata e in
    uscita del tratto;
  - `StrokeSimplifier`: Ramer-Douglas-Peucker iterativo, per non tenere in archivio
    migliaia di campioni ridondanti per riga di scrittura;
  - `InkConfig`: tutti i parametri della calligrafia in un unico posto condiviso.
- **`core:geometry`** — la facciata che consumeranno i renderer nativi:
  - `StrokeGeometry`: da una nota ai contorni da riempire; è l'unica API che SwiftUI,
    Compose, WidgetKit e Glance devono conoscere;
  - `StrokeOutliner`: trasforma la linea a spessore variabile in un poligono chiuso
    con punte arrotondate, perché le API di disegno accettano un solo spessore per
    path;
  - `RenderQuality` (SCREEN / WIDGET / THUMBNAIL): il dettaglio è un parametro
    perché i widget hanno limiti di memoria stretti su entrambe le piattaforme
    (decisione D11);
  - `Bounds`: rettangolo dell'inchiostro, spessore della penna compreso, per il
    ritaglio nei widget piccoli.
- **187 test** sul core, eseguibili con `./gradlew jvmTest` senza Xcode né emulatori.
  Coprono fra l'altro l'idempotenza e la commutatività del merge, la tenuta della
  geometria su campioni duplicati o coincidenti, e il comportamento dello spessore
  in assenza di pressione, e il giro di andata e ritorno completo di una nota
  attraverso SQLite, e il percorso completo scrittura → morte del processo →
  archivio.
- **`core:store`** — l'archivio locale su SQLite, tramite SQLDelight:
  - `NoteStore`: lettura per id, elenco recenti, ricerca nel testo riconosciuto,
    elenco delle note da riconoscere, salvataggio transazionale, tombstone e
    `purgeDeleted` come unico punto in cui qualcosa viene davvero eliminato;
  - schema a due tabelle, con i tratti come righe proprie perché sono l'unità di
    sincronizzazione e ognuno ha il suo tombstone;
  - `StrokePointCodec` in `core:model`: formato binario con byte di versione,
    16 byte per campione, che permette di aggiungere campi in futuro continuando a
    leggere le note già salvate (decisione D13);
  - inserimenti `INSERT OR IGNORE` + `UPDATE` invece di un upsert nativo, per non
    imporre `minSdk 30` su Android, e **mai** `INSERT OR REPLACE`, che attraverso
    la cascata sulla chiave esterna porterebbe via tutti i tratti della nota
    (decisione D15, con due test di regressione);
  - il driver del database lo costruisce la piattaforma: il core fornisce solo lo
    schema, così non entra nessun tipo di piattaforma nel core;
  - schema versionato in `src/commonMain/sqldelight/databases/1.db`, versionato in
    git come base delle migrazioni future (`./gradlew verifySqlDelightMigration`).
- **`core:capture`** — il percorso di cattura, senza database e senza iniezione
  delle dipendenze, perché l'inchiostro sia disegnabile al primo fotogramma:
  - `CaptureSession`: sessione di scrittura che non conosce né UI né archivio; un
    secondo contatto mentre si scrive viene ignorato, perché è il palmo della mano;
  - `InkJournal`: giornale di scrittura. Ogni tratto chiuso finisce in coda a un
    file **prima** che `endStroke` ritorni, quindi la nota è al sicuro dal primo
    tratto e non dalla conferma (decisione D20);
  - formato del giornale a record `versione | lunghezza | checksum | contenuto`:
    un processo ucciso a metà scrittura costa quel tratto, non il giornale, e la
    coda troncata viene segnalata fino all'interfaccia invece di essere ignorata;
  - `FrictionTrace` e `FrictionBudget`: le tappe fra il gesto dell'utente e
    l'inchiostro, con il tetto di 400 ms che blocca il rilascio (decisione D19);
  - il modulo **non dipende** da `core:store`: l'invariante "niente database sul
    percorso di cattura" è una dipendenza di build e non un commento (decisione D22).
- **`JournalIngest`** in `core:store` — porta il giornale in archivio fondendolo con
  quanto già salvato. Prima salva, poi svuota: un test fallisce il salvataggio di
  proposito e verifica che il giornale sopravviva come unica copia dell'inchiostro.
- **Ricerca normalizzata** (decisione D27):
  - `SearchText` in `core:model`: minuscolo consapevole di Unicode, accenti rimossi con
    una tabella esplicita, punteggiatura e apostrofi come separatori — così
    "l'idraulico" si trova cercando "idraulico";
  - `NoteSearch`: **tutte** le parole cercate devono comparire, in qualsiasi ordine, e i
    risultati escono per pertinenza. "pane latte" ora trova "latte, pane, caffè", che
    con il `LIKE` di prima non trovava niente;
  - una parola che comincia una parola del testo vale il doppio di una che capita in
    mezzo: cercando "pane", "pane integrale" batte "accompanare";
  - colonne normalizzate accanto alle loro sorgenti in archivio (migrazione 2 → 3), con
    `search_version` e `reindexSearch` per ricalcolare l'indice dopo una migrazione o
    dopo un miglioramento della normalizzazione;
  - la query delle candidate riporta solo id, istante e testo normalizzato: le note
    complete si leggono solo per quelle che finiscono nei risultati.
- **187 test** sul core (erano 147).
- **Note vocali nel modello e nell'archivio** (decisione D25):
  - `VoiceClip` in `core:model`, con trascrizione e percorso dell'audio. Una nota può
    avere inchiostro, voce o entrambi;
  - `voiceClips` è una **lista** con tombstone, come i tratti: due dispositivi che
    registrano offline sulla stessa nota si fondono per unione invece di
    sovrascriversi, e `mergeNotes` lo fa già;
  - `searchableText`, `hasInk`, `hasVoice`, `voiceClipsNeedingTranscription`;
  - la ricerca dell'archivio copre le trascrizioni oltre al testo riconosciuto
    dall'inchiostro: per chi cerca sono la stessa cosa;
  - **la prima migrazione dello schema**, 1 → 2, con un test che rilegge un archivio
    scritto dalla versione 1 e verifica che nota, tratti, campioni e percorso
    dell'immagine del widget sopravvivano. `verifySqlDelightMigration` controlla che le
    istruzioni descrivano lo stesso schema; solo quel test controlla i dati.
- **147 test** sul core (erano 113).

### Corretto

- **Un salvataggio da una copia vecchia poteva far resuscitare un tratto cancellato**
  o una nota cestinata, contro l'invariante 2 (decisione D26). Gli aggiornamenti di
  `deleted_at` passano ora da `coalesce`: un tombstone si mette, non si toglie. Vale
  anche per trascrizione e audio, nella direzione opposta — fra "c'è" e "non c'è
  ancora" vince "c'è". Tre test di regressione.
- **`purgeDeleted` lasciava tratti e registrazioni orfani in archivio.** Contava sulla
  cascata delle chiavi esterne, che in SQLite è spenta per difetto e si accende con un
  PRAGMA per connessione — e la connessione la apre la piattaforma. Ora i figli si
  cancellano esplicitamente, prima della nota.
- **`needsRecognition` teneva le note di sola voce in coda all'OCR per sempre**, senza
  che ci fosse inchiostro da leggere. Ora richiede `hasInk`, nel modello e nella query.
- **Le colonne aggiunte dalla migrazione 2 → 3 stavano in mezzo al `CREATE TABLE`**,
  mentre `ALTER TABLE` le accoda: un database creato da zero e uno migrato avrebbero
  avuto le colonne in ordine diverso, e `SELECT *` le legge per posizione. Intercettato
  da `verifySqlDelightMigration` (decisione D28).
- **Il test della migrazione arrivava a una versione fissata** e si rompeva a ogni
  migrazione nuova. Ora arriva alla versione corrente dello schema, quindi copre da sé
  anche le migrazioni future.

- **`androidApp`** — la prova di velocità di D19, installabile su un telefono:
  - `CaptureActivity`: Activity di piattaforma, nessuna libreria, nessun database,
    nessuna animazione di apertura. Serve i due ingressi previsti — sopra il launcher
    e sopra la schermata di blocco, via `showWhenLocked` (D17);
  - `InkCanvasView`: `View` di disegno che riempie i contorni calcolati dal core.
    Legge i campioni storici del `MotionEvent`, senza i quali su uno schermo a 120 Hz
    si butta via metà della risoluzione del tratto; la pressione la prende solo dal
    pennino, perché sul dito Android riporta 1.0 fisso e dichiararla assente fa
    calcolare lo spessore dalla velocità;
  - `AndroidInkJournalSink`: scrittura in append con `fd.sync()`, senza cui i byte
    resterebbero nei buffer del sistema e il giornale non proteggerebbe da niente;
  - il misuratore dell'attrito a schermo nelle build di debug, con il verdetto sul
    tetto dei 400 ms;
  - `RecoveredNotesActivity`: schermata di servizio che ricostruisce dal giornale le
    note e segnala se un tratto si è perso.
- **`:androidApp` entra nel build solo dove c'è l'SDK Android** (decisione D23), così
  il core resta compilabile e testabile su qualunque macchina e in CI.
- **Il giornale sta nell'area protetta dal dispositivo** e `CaptureActivity` è
  `directBootAware`: è ciò che permette alla cattura sopra il blocco di salvare anche
  prima del primo sblocco dopo un riavvio (decisione D24, che chiude il caveat di D17).
- **Mockup delle schermate** in `design/mockups/`, pubblicati come canvas:
  <https://claude.ai/code/artifact/226f7658-faf9-43dc-bd68-a9942e68261a>
  Sedici artboard su due pagine. **Flusso principale:** home iOS, cattura
  all'apertura e con gli strumenti, Android da schermo bloccato, Android sopra il
  launcher, cattura a voce, archivio con ricerca, i tre formati di widget, paywall,
  direzione alternativa. **Stati e servizio:** primo avvio senza note, come mettere
  il widget in home, una nota riaperta col menù di esportazione, l'archivio con gli
  stati che il codice produce davvero (nota vocale, riconoscimento in corso, tratto
  perso nel giornale), la configurazione del widget e le impostazioni.
- **`CLAUDE.md`** — registro delle decisioni di prodotto e tecniche, con le
  alternative scartate e il perché.
- Integrazione continua su GitHub Actions: i test del core a ogni push.

### Modificato

- **Il target `jvm` dei moduli del core produce bytecode 11** invece di 21: è il jar
  che consuma l'app Android, e D8 lo digerisce senza discutere.
- **Il giornale non sta più nella cache** ma nei file dell'app: la cache il sistema la
  può svuotare quando vuole, e lì dentro c'è inchiostro non ancora archiviato.

- **La cattura si apre nuda**: solo foglio e conferma, gli strumenti dopo il primo
  tratto. I primi mockup mostravano selettore della carta e barra delle punte in
  apertura, cioè decisioni chieste a chi in quel momento non vuole decidere niente
  (decisione D21).
- **Corretti i mockup** dopo una revisione: `box-sizing` mancante che faceva
  scavalcare il pulsante di cattura sull'ultima riga di note e il terzo bigliettino
  sul pulsante del widget Android; bersagli di tocco portati a 44 punti (il pallino
  del colore resta piccolo, l'area di tocco no); il widget piccolo non ha più un
  pulsante, si tocca tutto; "Camera" corretto in "Fotocamera"; conteggi
  incoerenti fra widget e archivio rimossi.

### Rimosso

- Tutto il contenuto precedente del repository (gli esercizi Cryptopals in Go). La
  storia resta in git: il repository è stato riusato per un progetto nuovo su
  richiesta del committente.
