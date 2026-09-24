# Guida — cosa devi fare tu

Questo file dice cosa tocca a te, in ordine. Tutto il resto lo faccio io.

Se leggi solo una cosa: **adesso tocca a iOS** (§0 e §0bis). Il resto aspetta che l'app
giri sul tuo iPhone.

---

## 1. La misura (la cosa che serve adesso)

### Perché

Tutta l'architettura poggia su una promessa misurabile. I tetti sono **due**, perché
un'apertura a freddo e una a caldo sono due fenomeni fisici diversi:

| | a caldo | a freddo |
|---|---|---|
| **Foglio pronto** — da qui il dito lascia inchiostro | **100 ms** | 400 ms |
| **Tocco → inchiostro** — quanto il tratto resta dietro al dito | 50 ms | 50 ms |

Il tempo che ci metti tu a toccare lo schermo non entra in nessuno dei due (D36).

**Misure sul Samsung S8 (build di debug):** tutto verde. 86 ms a caldo, 358–387 ms a
freddo, 252 ms a freddo con tutto compilato in anticipo; tratto 13–14 ms a caldo, circa 30
a freddo.

### La prossima misura: la build di rilascio

Le misure fatte finora sono della build di **debug**, che gira più lenta di quella che
avrà l'utente. La build di rilascio ora passa da R8 (D37):

```sh
./gradlew :androidApp:installRelease
```

Poi a freddo tre volte, come prima (arresto forzato + `am start -W`), e mandami i
`TotalTime`. Nella build di rilascio il misuratore a schermo non c'è: vale solo il numero
di `am start -W`. Se l'app si chiude da sola o si comporta diversamente dalla debug,
dimmelo subito: vuol dire che R8 ha tolto qualcosa che serviva.

Per tornare alla build col misuratore: `./gradlew :androidApp:installDebug`.

A freddo la maggior parte del tempo è creazione del processo e inizializzazione del
sistema: non è codice nostro e non si può saltare, ed è la ragione per cui 100 ms a
freddo non sono disponibili. A caldo invece resta solo la nostra parte, e lì 100 ms sono
l'obiettivo — ed è il caso più frequente per chi usa l'app ogni giorno.

Se il pavimento è molto oltre, la conseguenza cambia il progetto di **entrambe** le
app — e allora è meglio saperlo prima di scriverne una seconda.

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
./gradlew jvmTest              # 261 test, devono essere tutti verdi
./gradlew :androidApp:installDebug
```

Se `./gradlew jvmTest` è verde ma `installDebug` si lamenta, leggi il punto **"Se non
compila"** più sotto: c'è un rischio noto e la sua soluzione già scritta.

### Come misurare (questo conta più di come sembra)

Il numero appare **in alto a sinistra** appena tracci il primo segno, verde se è entro il
tetto e rosso se è oltre. Dice anche da quale caso partiva:

```
pronto freddo: 370ms (entro 400ms) · tocco→inchiostro 18ms (entro 50ms) · superficie 240ms
```

**Misura a freddo, che è il caso vero:**

1. Chiudi l'app **con l'arresto forzato**: Impostazioni → App → InkNote → Arresto
   forzato. Il foglio non compare più fra le app recenti — di proposito, perché lì si
   vedrebbe l'istantanea della nota — quindi da lì non si chiude.
2. Aspetta una decina di secondi.
3. Riaprila e traccia **subito** un segno.
4. Annota il numero.
5. **Ripeti cinque volte.** La prima apertura dopo l'installazione è sempre più lenta
   delle altre: se guardi solo quella ti spaventi per niente. Serve l'intervallo, non un
   numero solo.

**Poi misura a caldo, che è il caso che conta di più:** premi OK, riapri, traccia un
segno; tre o quattro volte. Lì il tetto è 100 ms, e il misuratore lo scrive da sé.

**Attenzione: il numero a caldo del misuratore è ottimista.** A caldo, da dentro l'app, il
momento del tuo tocco non si vede: si conta da quando il nostro codice parte, e i
millisecondi che il sistema spende prima restano fuori. Se hai voglia di un numero
onesto, col telefono collegato:

```sh
adb shell am start -W -n app.inknote.android/.CaptureActivity
```

e mandami la riga `TotalTime` (è il tempo fino al primo fotogramma, sistema compreso).
Premi OK fra un lancio e l'altro. Non è obbligatorio: se non ti va, il misuratore basta
per cominciare.

### Cosa mandarmi

- Modello del telefono e versione di Android.
- I cinque numeri a freddo e i due o tre a caldo, copiati come li vedi.
- Se è oltre il tetto: **copia la riga intera**. I due numeri intermedi (`superficie` e
  `1° fotogramma`) dicono **dove** si perde il tempo, e la cura è diversa a seconda del
  punto — non serve che li interpreti tu.

### La prova della compilazione anticipata (5 minuti)

Dice quanto potrebbe darci il profilo di riferimento, prima di scriverlo:

```sh
adb shell cmd package compile -m speed -f app.inknote.android
```

poi rimisura **a freddo tre volte** (arresto forzato + `am start -W`) e mandami i
`TotalTime`. Per tornare allo stato normale: reinstalla l'app.

### Se il numero a freddo è alto

Non ottimizzo alla cieca: prima serve la tua riga, poi si usa la leva giusta. La più
grossa è già individuata — un **profilo di riferimento** che precompila il percorso di
avvio, gratuito e senza cambiare una riga di logica. Si genera su un dispositivo, quindi
subito dopo la tua misura.

---

## 0. iOS, la prima tappa (D48)

Tutto sta in [`iosApp/README.md`](iosApp/README.md): installare XcodeGen, generare il
progetto, scegliere la tua squadra, eseguire sul tuo iPhone. **È codice mai compilato**:
se Xcode si lamenta, mandami gli errori così come li vedi (anche uno screenshot va bene).

E in parallelo, se riesci: **trova le 12 persone per il test chiuso Android**. I 14 giorni
del Play Store corrono mentre io scrivo iOS, e alla fine usciamo su tutti e due insieme.

## 0bis. iOS, la seconda tappa: cosa provare (D52)

Si compila insieme alla prima: stesso `git pull`, stesso `xcodegen generate`. Poi, sul
telefono:

1. **Il foglio**: in basso a sinistra tastiera e fotocamera. La tastiera apre una scheda in
   alto; la fotocamera si apre sopra il foglio e la miniatura compare in basso.
2. **"Fatto"** dà una vibrazione breve.
3. **Di notte**: metti il telefono in tema scuro e apri il foglio. Deve essere scuro, con
   l'inchiostro chiaro. Nell'archivio la stessa nota resta un bigliettino chiaro.
4. **Una nota aperta**: tocca un bigliettino. Inchiostro, testo, foto, "Manda a…" in
   basso, il cestino in alto.
5. **Il promemoria**: scrivi con la tastiera "dentista domani alle 10", poi apri la nota
   nell'archivio. Deve comparire "Ricordamelo · …": toccalo, e il calendario si apre già
   compilato.
6. **Lo smistamento**: in alto a destra nell'archivio c'è "Smista" col numero delle note
   nuove. Trascina a destra (manda), a sinistra (tieni), in giù (butta).
7. **La riemersione** comparirà da sola fra una settimana, quando avrai note abbastanza
   vecchie. Per provarla subito cambia la data del telefono di otto giorni in avanti.

Se Xcode si lamenta, mandami gli errori come li vedi: è Swift scritto senza compilatore.

## 0ter. Codemagic (D53)

Il file è già nel repository: `codemagic.yaml`. Su codemagic.io:

1. **Aggiungi il repository** (Add application → GitHub → `cryptopals`). Codemagic trova
   da solo il file di configurazione.
2. **La chiave di App Store Connect.** In App Store Connect → Utenti e accesso →
   Integrazioni → Chiavi API: crea una chiave col ruolo **App Manager** e scarica il file
   `.p8` (si scarica una volta sola). Su Codemagic: Team settings → Integrations → App
   Store Connect → aggiungi la chiave, e **chiamala `InkNote ASC`**, che è il nome scritto
   nel file.
3. **Certificato e profili iOS.** Team settings → codemagic.yaml settings → Code signing
   identities:
   - iOS certificates → **Generate certificate** di tipo *Apple Distribution*;
   - iOS provisioning profiles → **Fetch profiles**, e prendi i profili *App Store* per
     `app.inknote.ios` e `app.inknote.ios.widgets`. Se non esistono ancora, creali dal
     portale sviluppatori Apple (Certificates, IDs & Profiles), dopo aver registrato i due
     identificativi.
4. **La scheda dell'app** in App Store Connect (serve per TestFlight, ed è anche il modo di
   **prenotare il nome**, vedi D54). Poi copia il suo **Apple ID** numerico (Informazioni
   sull'app) nel file: togli il `#` dalle due righe `vars:` e `APP_STORE_APPLE_ID` e
   metti il numero al posto di quello d'esempio. Finché non lo fai il numero della build è
   il contatore di Codemagic, che va bene se non carichi build a mano da Xcode.
5. **Android**, quando servirà: Team settings → Code signing identities → Android keystores →
   carica la chiave di caricamento e **chiamala `inknote_upload_key`**. Poi un gruppo di
   variabili `google_play` con `GCLOUD_SERVICE_ACCOUNT_CREDENTIALS`, il file JSON
   dell'account di servizio del Play Console. **Il primo AAB va caricato a mano** dal Play
   Console: l'API non può creare un'app nuova.

**Come si usa:** i test del core partono da soli a ogni push. Per mandare una build a
TestFlight: *Start new build* → workflow **iOS — TestFlight**, oppure un tag, per esempio
`git tag ios-0.1-1 && git push origin ios-0.1-1`.

**Il primo giro iOS dura di più** (una ventina di minuti): scarica il compilatore di
Kotlin/Native. Dal secondo è in cache.

## 1bis. Il giro di prova dell'app intera (D38–D42)

Dopo `git pull`, reinstalla (`./gradlew :androidApp:installDebug`). **È la prima volta
che questo codice viene compilato**: se il build si lamenta, mandami l'errore così com'è.

1. **L'icona apre l'archivio.** Le note scritte finora devono comparire nell'elenco.
   Nell'elenco delle app c'è anche **"Scrivi"**, che apre il foglio.
2. **Il foglio**: niente righe, e col dito il tratto è più spesso. **Prova il corsivo**
   scrivendo grande: com'è adesso?
3. **Tastiera**: tocca l'icona in basso a sinistra, scrivi, premi OK. La nota compare
   nell'archivio col testo, e la ricerca la trova.
4. **Foto**: tocca la fotocamera, scatta, conferma. Una miniatura compare sul foglio.
   Poi la stessa prova **a telefono bloccato** dal riquadro rapido: la fotocamera deve
   aprirsi senza chiederti il codice.
5. **Manda a…**: apri una nota dall'archivio e mandala a Keep, o a una mail a te stesso.
   Deve arrivare il testo con l'immagine dell'inchiostro e le foto.
6. **Elimina**: la nota sparisce dall'elenco.
7. **Tasto laterale** (facoltativo): Impostazioni → Funzioni avanzate → Tasto laterale →
   Doppia pressione → Apri app → **Scrivi**. Poi, a telefono in tasca: doppio clic,
   scrivi.

## 1ter. Il giro di prova del disegno nuovo (D45, D46)

1. **L'archivio**: griglia di bigliettini, titolo grande, ricerca a pillola, "Scrivi" in
   basso a destra. Prova anche col **tema scuro** del telefono: la scrivania diventa
   scura, i bigliettini restano chiari.
2. **Il foglio**: in basso a sinistra tastiera e fotocamera, a destra "Fatto". Il
   misuratore è una pillola piccola in basso: **un tocco lo nasconde**, tenendolo premuto
   apre il giornale.
3. **La tastiera**: la scheda del testo compare in alto, sotto l'orologio, e non è più
   coperta da niente. La X la chiude.
4. **La fotocamera**: si apre **dentro il foglio**. La prima volta chiede il permesso —
   **fai il primo scatto a telefono sbloccato**, così il permesso lo dai una volta per
   tutte. Poi prova a telefono bloccato dal riquadro rapido: scatto, la miniatura compare
   sul foglio, il foglio **non si chiude** e non chiede il codice.
5. **Dimmi cosa non ti piace**, anche a sensazione: colori, dimensioni, cosa manca.
   Il disegno è stato scritto senza vederlo su uno schermo, quindi il tuo occhio è la
   prova vera.

## 2. Le altre due verifiche, mentre hai il telefono in mano

**Il tratto.** Scrivi una riga col dito e guarda se lo spessore vive: più sottile nei
movimenti rapidi, più pieno dove rallenti. Se sembra un tubo di spessore costante c'è
qualcosa che non va nel calcolo per velocità, e va sistemato prima di andare avanti. Con
un pennino attivo lo spessore deve seguire la pressione.

**La sopravvivenza.** Scrivi due o tre tratti, **non premere OK**, torna alla home e fai
l'arresto forzato. Riapri e tocca il misuratore in alto a sinistra: si apre l'elenco
delle note nel giornale, e i tratti devono essere lì. Se non ci sono, il meccanismo che
promette "al sicuro dal primo tratto" non funziona e quella è la prima cosa da riparare.

**Il widget.** Tieni premuto sulla home → Widget → InkNote. Deve essere un foglio caldo
con un piccolo segno al centro, e toccandolo **in qualunque punto** si apre il foglio.

**Il riquadro rapido, a telefono bloccato.** È la verifica più importante di questo
giro. Per aggiungerlo (sui Samsung): scorri giù **con due dita** dall'alto, così si apre
il pannello intero; tocca i **tre puntini** in alto a destra → **Ordine pulsanti**;
cerca "Scrivi una nota" nella parte alta, tienilo premuto e trascinalo fra i pulsanti in
basso; **Fatto**. Poi **blocca il telefono**, riaccendi lo schermo senza
sbloccare, scorri giù e tocca il riquadro: il foglio deve comparire **sopra il blocco,
senza chiederti il codice**. Scrivi, premi OK: devi tornare alla schermata di blocco.

**La privacy.** Scrivi qualcosa e premi il tasto laterale **senza premere OK**.
Riaccendi: devi vedere la schermata di blocco, **non la tua nota**. Poi sblocca e tocca
il widget: il foglio deve essere bianco. Se vedi la nota di prima in uno dei due casi,
dimmelo subito: è una falla, non un dettaglio.

---

## 3. Se non compila

C'è **un** rischio noto, dichiarato. I moduli condivisi non hanno un target Android —
di proposito, altrimenti il codice non si compilerebbe più dove l'SDK non c'è, e
perderei i 261 test che girano su qualunque macchina. L'app chiede quindi la loro
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
3. **Il nome commerciale.** `InkNote` è solo l'identificativo tecnico. La mia proposta è
   **Quicknib** ("veloce" + "pennino"), poi Inkflash; criteri e verifiche in D54. Quando
   hai scelto: crea la scheda in App Store Connect con quel nome (se è libero, resta tuo),
   e compra subito il dominio `.app`.
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
- **Non comprare domini a caso** né aprire profili social prima di aver scelto il nome:
  il dominio si compra il giorno in cui il nome è deciso, non prima (D54).

---

## 6. Dove siamo, in tre righe

Il **core condiviso è scritto e verificato**: modello dati pronto per il sync, motore
d'inchiostro, archivio SQLite con cinque migrazioni provate, giornale che mette
l'inchiostro al sicuro dal primo tratto, ricerca, uscita verso altre app, smistamento,
riemersione e date. **302 test, tutti verdi, su qualunque macchina.**

L'**app Android compila e gira** sul tuo S8, con le misure tutte verdi. L'**app iOS è
scritta** — le due tappe — e **aspetta la prima compilazione**, sul tuo Mac o su Codemagic.

Le ragioni di ogni scelta stanno in [`CLAUDE.md`](CLAUDE.md), che è la memoria del
progetto: se una decisione ti sembra sbagliata, lì c'è scritto perché era stata presa e
cosa era stato scartato.
