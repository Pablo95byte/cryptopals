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
- **52 test** sul core, eseguibili con `./gradlew jvmTest` senza Xcode né emulatori.
  Coprono fra l'altro l'idempotenza e la commutatività del merge, la tenuta della
  geometria su campioni duplicati o coincidenti, e il comportamento dello spessore
  in assenza di pressione.
- **`CLAUDE.md`** — registro delle decisioni di prodotto e tecniche, con le
  alternative scartate e il perché.
- Integrazione continua su GitHub Actions: i test del core a ogni push.

### Rimosso

- Tutto il contenuto precedente del repository (gli esercizi Cryptopals in Go). La
  storia resta in git: il repository è stato riusato per un progetto nuovo su
  richiesta del committente.
