# Guida — cosa devi fare tu

Questo file dice cosa tocca a te, in ordine. Tutto il resto lo faccio io.

Se leggi solo una cosa: **il passo 1 sblocca tutto il resto.** Finché non c'è quel
numero, scrivere la seconda app è lavoro a rischio.

---

## 1. La misura (la cosa che serve adesso)

### Perché

Tutta l'architettura poggia su una promessa misurabile: **dal tocco al primo tratto,
massimo 400 ms** su un telefono vero. Se il pavimento è molto oltre, la conseguenza
cambia il progetto di **entrambe** le app — e allora è meglio saperlo prima di scriverne
una seconda.

Io non posso prenderla: nell'ambiente in cui sviluppo il dominio di Google è bloccato,
quindi non ho né SDK Android né modo di compilare un APK.

### Cosa ti serve

- **Android Studio** installato (gratis). Al primo avvio scrive da sé il file
  `local.properties`, e da quel momento il modulo dell'app entra nel build senza che tu
  tocchi niente.
- **Un telefono Android vero**, non un emulatore. Meglio se è quello di fascia media che
  userebbe un utente normale, non il più veloce che hai.
- Un cavo USB, con il debug USB attivato nelle opzioni sviluppatore del telefono.

### I passi

```sh
git clone <questo repository>
cd cryptopals
./gradlew jvmTest              # 224 test, devono essere tutti verdi
./gradlew :androidApp:installDebug
```

Se `./gradlew jvmTest` è verde ma `installDebug` si lamenta, leggi il punto **"Se non
compila"** più sotto: c'è un rischio noto e la sua soluzione già scritta.

### Come misurare (questo conta più di come sembra)

Il numero appare **in alto a sinistra** appena tracci il primo segno, verde se è entro i
400 ms e rosso se è oltre:

```
attrito: 210ms (entro 400ms) · superficie 150ms · primo fotogramma 180ms
```

**Misura a freddo, che è il caso vero:**

1. Chiudi l'app dalle app recenti.
2. Aspetta una decina di secondi.
3. Riaprila e traccia **subito** un segno.
4. Annota il numero.
5. **Ripeti cinque volte.** La prima apertura dopo l'installazione è sempre più lenta
   delle altre: se guardi solo quella ti spaventi per niente. Serve l'intervallo, non un
   numero solo.

Poi fai due o tre aperture **a caldo** (riapri subito dopo aver chiuso) per confronto.

### Cosa mandarmi

- Modello del telefono e versione di Android.
- I cinque numeri a freddo e i due o tre a caldo, copiati come li vedi.
- Se è oltre i 400 ms: anche i due numeri intermedi (`superficie` e `primo fotogramma`),
  perché dicono **dove** si perde il tempo, e la cura è diversa a seconda del punto.

---

## 2. Le altre due verifiche, mentre hai il telefono in mano

**Il tratto.** Scrivi una riga col dito e guarda se lo spessore vive: più sottile nei
movimenti rapidi, più pieno dove rallenti. Se sembra un tubo di spessore costante c'è
qualcosa che non va nel calcolo per velocità, e va sistemato prima di andare avanti. Con
un pennino attivo lo spessore deve seguire la pressione.

**La sopravvivenza.** Scrivi due o tre tratti, **non premere OK**, e uccidi l'app dalle
recenti. Riaprila e tocca il misuratore in alto a sinistra: si apre l'elenco delle note
nel giornale, e i tratti devono essere lì. Se non ci sono, il meccanismo che promette
"al sicuro dal primo tratto" non funziona e quella è la prima cosa da riparare.

---

## 3. Se non compila

C'è **un** rischio noto, dichiarato. I moduli condivisi non hanno un target Android —
di proposito, altrimenti il codice non si compilerebbe più dove l'SDK non c'è, e
perderei i 224 test che girano su qualunque macchina. L'app chiede quindi la loro
variante `jvm` con un attributo Gradle, e **quella riga non l'ha mai provata nessuno con
l'SDK presente**.

Se Gradle si lamenta della risoluzione delle varianti, la strada alternativa è già
scritta in [`androidApp/README.md`](androidApp/README.md), col codice esatto da
incollare. **Mandami l'errore e la applico io**: non serve che lo risolva tu.

---

## 4. Le decisioni che aspettano te

Nessuna blocca il codice, ma prima o poi servono. In ordine di quanto pesano:

1. **Il foglio del widget: nudo o con un segno tenue?** Un rettangolo bianco del tutto
   vuoto può sembrare un widget rotto. Le due varianti sono a confronto nel canvas dei
   mockup, artboard `DueVarianti`. Io consiglio il segno.
2. **La direzione visiva.** Carta calda (quella attuale) o la direzione B, "gesso su
   lavagna". Va scelta prima di rifinire l'interfaccia.
3. **Il nome commerciale.** `InkNote` è solo l'identificativo tecnico. Il nome vero conta
   più del codice per farsi trovare, e va deciso guardando cosa cerca la gente negli
   store, non a intuito.
4. **Cosa sta nel Pro.** "Widget multipli" non vale più niente da quando il widget è
   bianco. La proposta: il Pro poggia su esportazione automatica verso Notion e i file,
   ricerca nel testo, punte e temi — col foglio di condivisione sempre gratuito.
5. **Prezzo del Pro.** 6,99 € è la proposta, da confrontare col mercato.

---

## 5. Cosa NON fare adesso

Ti risparmia soldi e tempo:

- **Non creare le schede negli store.** Una scheda aperta troppo presto invecchia, e i
  testi vanno scritti quando l'app è finita, non prima.
- **Non aprire l'account RevenueCat** e non configurare prodotti in-app. Serve quando il
  paywall esiste davvero, ed è gratuito fino a 2500 $ al mese di ricavi: non c'è fretta.
- **Non comprare un dominio** né aprire profili social. Prima si vede se l'app regge in
  mano.
- **Non installare Xcode** finché Android non gira. Se il pavimento dell'attrito è un
  problema, la conseguenza cambia anche l'app iOS: scriverla prima di saperlo è lavoro
  da rifare.

---

## 6. Dove siamo, in tre righe

Il **core condiviso è scritto e verificato**: modello dati pronto per il sync, motore
d'inchiostro, geometria, archivio SQLite con quattro migrazioni provate, giornale che
mette l'inchiostro al sicuro dal primo tratto, ricerca che ignora accenti e ordine delle
parole, uscita verso altre app. **224 test, tutti verdi, su qualunque macchina.**

L'**app Android è scritta ma non l'ha compilata nessuno**: è la prova di velocità, e il
primo build è il tuo. L'**app iOS non è ancora scritta**, per scelta: aspetta la misura.

Le ragioni di ogni scelta stanno in [`CLAUDE.md`](CLAUDE.md), che è la memoria del
progetto: se una decisione ti sembra sbagliata, lì c'è scritto perché era stata presa e
cosa era stato scartato.
