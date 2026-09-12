# InkNote

Appunti **scritti a mano** che partono dalla home del telefono: un tocco, si
scrive, si conferma, e la nota è salvata e visibile nel widget. Poi si ritrovano
tutte aprendo l'app.

iOS e Android, con un core condiviso in Kotlin Multiplatform e interfacce native su
entrambe le piattaforme.

> `InkNote` è il nome tecnico provvisorio dei package. Il nome commerciale non è
> ancora deciso.

## Stato

Prime fondamenta. C'è il core condiviso — modello dati, motore d'inchiostro,
geometria dei tratti, archivio locale — con i suoi test, e i mockup delle
schermate. Le app e i widget non sono ancora scritti.

## Struttura

```
core/model/      note, tratti, penne, fusione delle versioni
core/ink/        levigatura della curva, spessore, semplificazione
core/geometry/   contorni pronti da riempire per i renderer nativi
core/capture/    sessione di scrittura e giornale dell'inchiostro
core/store/      archivio locale SQLite: note, tratti, ricerca
design/mockups/  le schermate, come artboard di un canvas pubblicato
```

## Verificare

```sh
./gradlew jvmTest
```

Esegue tutti i test del core e funziona su qualunque macchina con JDK 21, Linux
compreso: non servono né Xcode né un emulatore. Per compilare le app serviranno
Android Studio e, per iOS, un Mac con Xcode.

## Decisioni

[`CLAUDE.md`](CLAUDE.md) contiene il registro delle scelte di prodotto e tecniche,
con le motivazioni e le alternative scartate. Va letto prima di rimettere in
discussione un pezzo di architettura, e aggiornato quando si decide qualcosa di
nuovo.

Cosa è cambiato e quando: [`CHANGELOG.md`](CHANGELOG.md).
