# Changelog

Tutte le modifiche rilevanti a questo progetto sono annotate qui.

Il formato segue [Keep a Changelog](https://keepachangelog.com/it/1.1.0/) e la
numerazione segue il [Versionamento Semantico](https://semver.org/lang/it/).

Le **decisioni** e le loro motivazioni stanno in [`CLAUDE.md`](CLAUDE.md); qui c'è
solo cosa è cambiato nel codice.

## [Non rilasciato]

### Corretto

- **Gradle si fermava su `BaseVariant` dove c'è l'SDK Android**: il plugin Android ora sta
  nel classpath della radice, solo quando `:androidApp` entra nel build (D58). Correggeva
  un errore introdotto da D57.
- **Codemagic, cache sui Mac**: solo il compilatore Kotlin/Native. Salvare anche quella di
  Gradle costava sei minuti su nove a ogni build (D56).
- **Gradle si fermava su ogni macchina con l'SDK Android** (Codemagic): il plugin Kotlin
  per Android è ora dichiarato nella radice. Quando il build lo chiama Xcode,
  `:androidApp` resta fuori (D57).

### Cambiato

- **Il nome visibile è Instink** (D55): sotto l'icona su iOS e Android, nel widget e nei
  testi. Package e bundle ID restano `app.inknote`.

### Aggiunto

- **Workflow `ios-check`** su Codemagic (D56): compila l'app iOS senza firma a ogni push
  che tocca iOS o il core, e stampa solo gli errori. L'Apple ID di Instink è nel file.
- **Smistamento a carte** (D52): `Note.sortedAt` con migrazione 5 → 6, la coda
  `notesToSort` nell'archivio, e su iOS la schermata a carte — destra manda, sinistra
  tieni, giù butta — con gli stessi tre gesti come pulsanti.
- **Riemersione** (D52): `Resurface.pick`, una nota vecchia al giorno in cima
  all'archivio, prima gli anniversari; su iOS si toglie per oggi.
- **Date riconosciute** (D52): `DateHints.find` in italiano e inglese; su iOS la nota aperta
  propone "Ricordamelo" con l'editor di eventi del sistema, senza permessi sul calendario.
- **Foglio di notte** (D52) su Android e iOS: col tema scuro il foglio si scurisce e
  l'inchiostro diventa chiaro, senza cambiare i tratti salvati.
- **Vibrazione su "Fatto"** (D52) su Android e iOS.
- **App iOS, seconda tappa**: tastiera e fotocamera sul foglio, nota aperta con foto,
  "Manda a…" con testo, immagine dell'inchiostro e foto, eliminazione, pulizia del
  cestino dopo trenta giorni, traduzione italiana. Il foglio si presenta sopra qualunque
  schermata aperta. Non ancora compilata.
- **`codemagic.yaml`** (D53): test del core a ogni push, TestFlight con un tag `ios-*`,
  canale interno del Play Store con un tag `android-*`.
- **Firma di rilascio Android** dalle variabili di Codemagic o da `keystore.properties`;
  `versionCode` dal numero della build del CI (D53).
- Nella facciata per Swift: `toSort`, `toSortCount`, `keep`, `resurfaced`, `dateHint`,
  `photoPaths`, `purge`, con 4 test.

- **App iOS, prima tappa** (`iosApp/`, D48): progetto XcodeGen, archivio in SwiftUI,
  foglio in UIKit con Apple Pencil e campioni intermedi, giornale con `fsync`, widget per
  home e schermata di blocco, pulsante del Centro di Controllo (iOS 18), azione per il
  tasto Azione e Comandi rapidi, icona. Non ancora compilata.
- **`:shared`**: il core in un framework per iOS (`InkNoteKit`) e una facciata per Swift
  (`InkSheet`, `InkArchive`, `InkPreview`), con 9 test.
- **Backup acceso su Android** (D49): archivio e giornale nel Google Drive dell'utente;
  foto solo nel passaggio diretto da telefono a telefono, per il limite di 25 MB.

- **Disegno nuovo** su tutte le schermate (D46): bigliettini di carta su una scrivania,
  Instrument Sans, tema scuro, da bordo a bordo, griglia che si adatta alla larghezza,
  pulsanti a pillola. Kit condiviso in `Ui.kt`.
- **Fotocamera dentro il foglio** (`InlineCamera`, Camera2): non si lascia più l'app per
  scattare (D45).

- **Archivio su Android** (`ArchiveActivity`, `NoteActivity`): l'icona dell'app apre
  l'elenco delle note con anteprima e ricerca; la nota aperta si manda ad altre app o si
  elimina. Il giornale entra in archivio all'apertura (D39).
- **Foglio di condivisione**: testo, immagine dell'inchiostro e foto; l'invio si registra
  solo quando l'utente sceglie una destinazione (D31, D39).
- **Tastiera e fotocamera sul foglio**: `TextClip` e `PhotoClip` nel modello, nel merge,
  nella ricerca, nell'esportazione e nell'archivio (migrazione 4 → 5); giornale in
  formato 2 con il tipo del record, il formato 1 si legge ancora (D38).
- **"Scrivi"** nell'elenco delle app, **scorciatoia "Nuova nota"** tenendo premuta
  l'icona, **icona dell'app** (D39).
- **`FilesProvider`** in un processo suo, per fotocamera e condivisione (D40).
- **Inglese come lingua di base**, italiano come traduzione (D41).

- **R8 nella build di rilascio** e un **profilo di riferimento** scritto a mano
  (`baseline-prof.txt`), per l'avvio a freddo. Nessuna libreria aggiunta (D37).

- **Widget della home su Android** (`SheetWidgetProvider`): un foglio bianco col segno
  tenue, tutto il riquadro apre la cattura. `RemoteViews` di piattaforma e non Glance,
  nessun aggiornamento periodico (D33).
- **Riquadro nelle impostazioni rapide** (`CaptureTileService`): l'ingresso alla cattura
  a telefono bloccato, che D17 prevedeva ma non aveva (D33).
- **Avviso di scrittura fallita** sul foglio: compare solo se il giornale non riesce a
  scrivere, cioè col disco pieno (D35).
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
- **261 test** sul core, eseguibili con `./gradlew jvmTest` senza Xcode né emulatori.
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
- **Uscita verso altre app di note** (decisione D31, dal committente): InkNote diventa
  il livello di cattura del sistema di note che l'utente ha già — Keep, Notion,
  Obsidian, una mail — invece di un archivio che compete con quelli.
  - `NoteExport` prepara il testo nudo (per il foglio di condivisione), il Markdown (per
    i sistemi a file) e il nome del file ricavato dalle prime parole;
  - le parti dettate escono come citazioni, e la riga finale dice da dove viene la nota;
  - quando il riconoscimento non è completo **lo dice il file stesso**, non solo l'app:
    la nota vivrà altrove e chi la rilegge lì deve saperlo;
  - `Note.exports` registra cosa è già stato mandato e dove, con migrazione 3 → 4 e la
    coda `notesToSend`. Senza quella traccia un secondo invio duplicherebbe la nota
    nell'archivio dell'utente, ed è un danno che non si ripara con un aggiornamento;
  - registrare un invio **non** fa avanzare la revisione della nota: mandarla non la
    modifica, e altrimenti ogni invio renderebbe vecchi tutti gli altri;
  - confine scritto come invariante: **esportazione, mai sincronizzazione**, e niente
    invii durante la cattura.
- **224 test** sul core (erano 193): cinque sono regressioni sui bug qui sotto.
- **In home il widget è un foglio bianco** (decisione D30, dal committente): nessuna
  nota, nessun conteggio, niente da configurare. Si tocca in qualunque punto e il
  foglio vero si apre. Dalla home si aggiungono note, non si rileggono.
  - risolve un'incoerenza vera: la schermata di blocco era cieca per privacy, ma la
    home mostrava spesa, indirizzi e numeri a chiunque passasse accanto al telefono;
  - un widget senza dati non va mai aggiornato, non mostra mai una nota vecchia, non
    sfonda il tetto di memoria e non ha bisogno della PNG per nota;
  - costa il gancio di marketing della "home coperta della propria calligrafia": la
    vetrina si sposta sulla sequenza tocco → scrivi → fatto.
- I mockup hanno una pagina **In home** rifatta: i tre formati come fogli bianchi su
  iOS e Android, la schermata di blocco, la sequenza completa del gesto, e due varianti
  del foglio (nudo o con un segno tenue) fra cui scegliere.

### Rimosso in questa tornata

- **`WidgetFraming` e i suoi test**, cancellati da D30: senza note nel widget non
  servivano più. Ne resta `NoteFraming`, le due regole che valgono in qualunque riquadro
  dentro l'app. I test scendono da 209 a 193, e va bene: c'è meno codice.
- **La schermata di configurazione del widget** dai mockup: non c'è più niente da
  configurare oltre al formato, che si sceglie mettendolo in home.

- **Inquadratura dei widget** in `core:geometry` (decisione D29): `WidgetFraming`
  decide quante note stanno in un widget, in quali caselle, con quale ritaglio e a
  quale dettaglio.
  - il numero di note lo decide lo spazio e non il formato: sotto il minimo leggibile
    si mostrano meno note più grandi, e se nemmeno una casella è leggibile il widget
    resta solo una porta per scrivere;
  - si inquadra l'inchiostro e non il foglio, con un tetto all'ingrandimento;
  - l'area di cattura va sul lato lungo del widget: su un formato basso e largo una
    striscia in alto costerebbe più spazio del contenuto;
  - le note vocali entrano nel widget con la loro trascrizione; se una nota ha
    inchiostro e voce vince l'inchiostro;
  - il dettaglio di disegno lo decide la casella, non il formato del widget.
- ~~209~~ **193 test** sul core: vedi "Rimosso in questa tornata".
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

- **Il tetto dell'attrito diventa due numeri** (decisione D32, richiesta del
  committente di scendere a 100 ms): **100 ms a caldo e 400 a freddo** per l'inchiostro
  accettato, 150 e 500 per l'inchiostro visibile.
  - a freddo 100 ms non sono disponibili: fra il tocco e la nostra prima istruzione il
    sistema crea il processo, carica e verifica le classi e inizializza il framework, e
    nessuno di quei passaggi è codice nostro. Un tetto che non si può rispettare viene
    ignorato;
  - a caldo sì, ed è il caso più frequente per chi usa l'app ogni giorno;
  - `INK_ACCEPTED` e `INK_DRAWN` sono ora due tappe distinte con due tetti: la prima è
    la missione (l'idea è al sicuro, e la superficie riceve i tocchi **prima** del primo
    fotogramma), la seconda è la sensazione;
  - il misuratore dice da quale caso partiva e riporta le tappe intermedie, perché
    dicono **dove** si perde il tempo;
  - individuata la leva vera sul freddo: un profilo di riferimento per l'avvio, da
    generare su un dispositivo. E scartato il tenere il processo vivo a forza, che darebbe
    il numero buono al prezzo della batteria e di una notifica persistente.
- **228 test** sul core.

### Corretto

- **Le icone del foglio sembravano adesivi** con un quadrato sfocato dietro: tolte la
  pillola e le ombre; pulsanti piatti, card con un filo di bordo invece dell'ombra (D46).

- **Scattare una foto dal foglio aperto al volo chiudeva il foglio e chiedeva lo
  sblocco**: la fotocamera del sistema portava fuori dall'app. Ora la fotocamera è dentro
  il foglio (D45).
- **La scheda del testo in alto era coperta dal misuratore**: il misuratore ora è una
  pillola piccola in basso, che un tocco nasconde; la scheda del testo sta sotto la barra
  di stato.

- **Due dita sul foglio tracciavano una retta**: il foglio seguiva l'indice 0 del
  `MotionEvent`, che passa al dito rimasto quando il primo si alza. Ora segue il dito
  che ha cominciato il tratto, per id; un secondo dito è ignorato.
- **Il misuratore contava il tempo della mano**: misurava fino al primo tocco, e sul
  primo telefono diceva 1468 ms in rosso con il foglio pronto in 360. Ora misura fino al
  foglio pronto (tetti 100/400 ms) e, separatamente, dal dito sul vetro all'inchiostro
  (tetto 50 ms), con l'istante del tocco preso dall'hardware (D36).

- **Un tratto interrotto a metà scrittura rendeva invisibili tutte le note scritte
  dopo**, e l'assorbimento le avrebbe cancellate. Il lettore del giornale ora salta i byte
  rotti e riprende dal record valido successivo (D35). Riprodotto con un test prima della
  correzione.
- **Lo svuotamento del giornale cancellava anche i tratti arrivati dopo la lettura.**
  `InkJournalSink.clear()` è sostituito da `discardPrefix(n)`, e `InkJournal.discard`
  toglie solo ciò che il `recover` corrispondente ha letto (D35, invariante 13).
- **Un record di una versione futura veniva svuotato come spazzatura**: ora la lettura
  si ferma lì e non lo consuma.
- **La nota restava leggibile sopra il blocco**: scritta una nota e spento lo schermo
  senza premere OK, il foglio ricompariva alla riaccensione con la nota visibile. Ora il
  foglio si chiude quando esce dallo schermo e non compare fra le app recenti (D34).
- **Ruotare il telefono ricreava il foglio**, togliendo dallo schermo l'inchiostro e
  spezzando la nota in due: ora i cambi di configurazione non ricreano l'Activity.
- **`fd.sync()` girava sul thread dell'interfaccia** al sollevamento del dito, fra una
  parola e l'altra: ora le scritture passano da un solo thread dedicato, svuotato in
  `onStop` (D35).
- **Il misuratore classificava "a freddo" un secondo foglio aperto entro 10 secondi**, e
  lo misurava dall'avvio del processo di prima: migliaia di millisecondi falsi, proprio
  nel protocollo a caldo. Ora è freddo solo il primo foglio del processo.
- Il pennello del tratto in corso non viene più allocato a ogni fotogramma.



- **La coda di invio poteva rimandare per sempre una nota già mandata, duplicandola
  nell'archivio dell'utente.** `updateExport` alzava la revisione registrata con `max`,
  mentre `updateNote` la assegnava secca: un salvataggio partito da una copia vecchia
  faceva retrocedere la nota **sotto la revisione del suo stesso invio**, e la condizione
  `revision = note.revision` non combaciava più. Tre correzioni: `revision` e
  `updated_at` della nota si aggiornano con `max` — sono monotoni per costruzione, come
  in `mergeNotes` — la coda confronta con `>=`, e `needsResendTo` usa `<` invece di `!=`.
- **Una nota a cui erano stati cancellati tutti i tratti restava in coda per sempre**,
  occupando un posto: `NoteExport.prepare` la rifiuta, quindi non poteva mai essere
  marcata come mandata. La coda ora richiede almeno un tratto o una registrazione vivi.
- **Una nota con inchiostro non ancora riconosciuto produceva un Markdown con dentro solo
  una riga orizzontale e un piè di pagina.** Senza contenuto non si scrive un file: ora
  `markdown` è `null` e chi chiama manda solo l'immagine dell'inchiostro.
- **`updateExport` massimizzava istante e revisione separatamente**, producendo righe che
  accoppiavano il timestamp di un invio con la revisione di un altro. Ora l'istante segue
  la revisione che vince, come in `mergeNotes`.
- **Aggiunta `GUIDA.md`**: cosa deve fare il committente, in ordine, col protocollo di
  misura e le cose da non fare adesso.



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
  Ventiquattro artboard su tre pagine. **In home:** il widget nei tre formati su
  sfondi diversi, il primo giorno senza note, una nota vocale fra quelle scritte, la
  schermata di blocco iOS e due home Android. È la pagina che mostra il prodotto dove
  conta davvero, perché la home è l'unico posto dove si vede senza aprirlo. **Flusso principale:** home iOS, cattura
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

- **Il foglio non ha più righe, e col dito il tratto è più spesso** (D42).
- `NoteStore.purgeDeleted` restituisce i percorsi delle foto da cancellare dal disco.

- `InkJournal.recover()` restituisce `JournalRecovery` (note, `hadTornTail`,
  `consumedBytes`) al posto di una lista di `RecoveredNote`.
- `JournalReadResult.discardedTailBytes` diventa `discardedBytes`: i byte illeggibili
  possono stare anche in mezzo.

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
