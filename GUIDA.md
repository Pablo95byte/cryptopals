# Guida — cosa devi fare tu

Questo file dice cosa tocca a te, in ordine. Tutto il resto lo faccio io.

Se leggi solo una cosa: **§0decies**, il piano delle prossime due settimane fino all'App
Store. Il primo passo è sempre lo stesso: **mettere in rete il sito** (§0quater), perché
TestFlight esterno e Play Console chiedono l'indirizzo della privacy.

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

## 0, senza Mac. La prima compilazione su Codemagic (D56)

Se non vuoi passare da Xcode: su codemagic.io, dopo aver aggiunto il repository,
**Start new build → branch `claude/notes-homescreen-app-drbsfv` → workflow
"iOS — compila (senza firma)"**. Da lì in poi parte da solo a ogni mio push che tocca iOS.

Non serve nessun certificato. Se fallisce, apri il passo **"Compila per iPhone, senza
firma"** e copiami il blocco fra le due righe `ERRORI: copia da qui`. Il primo giro
dura una ventina di minuti; i successivi meno.

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
   Store Connect → aggiungi la chiave. Il file usa quella che c'è già per l'altra app, di
   nome **`codemagic`**: una chiave vale per tutto il team.
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

## 0decies. Dalla build su TestFlight all'App Store, in due settimane (D69)

**Settimana 1 — farla provare a persone vere.**

1. **Il sito in rete** (§0quater): `instink.app`, con l'inoltro di `hello@instink.app`.
2. **Una build nuova su TestFlight** (tag `ios-*`): porta le sette lingue. Per provarle:
   Impostazioni → Instink → Lingua, senza cambiare la lingua del telefono.
3. **TestFlight esterno.** App Store Connect → TestFlight → Test esterni → nuovo gruppo →
   aggiungi la build. Apple chiede una revisione breve della beta (un giorno circa) e
   l'indirizzo della privacy. Poi **Link pubblico**: 20–50 persone, e **almeno una per
   lingua** (spagnolo, tedesco, francese, portoghese, giapponese) a cui chiedi solo:
   "c'è una frase che suona strana?". Le traduzioni le ho scritte io: un madrelingua le
   deve rileggere prima del lancio in quel paese.
4. **Il test chiuso del Play Store** in parallelo (§0nonies): i 14 giorni corrono intanto.

**Settimana 2 — la scheda e l'invio.**

5. **La scheda in sette lingue.** App Store Connect → la tua app → Distribuzione →
   informazioni sull'app e la versione: in alto a destra il menu della lingua → aggiungi
   Spagnolo (Spagna) **e** Spagnolo (Messico), Tedesco, Francese **e** Francese (Canada),
   Portoghese (Brasile), Giapponese. In ognuna copia i campi da `lancio/STORE.md`. Lo
   spagnolo del Messico conta anche per la ricerca negli Stati Uniti, il francese del
   Canada in Canada: stessi testi, doppia presenza.
6. **Gli screenshot** (servono quelli da 6,9 pollici): le sei frasi di `STORE.md`. Te li
   preparo io in tutte le lingue se mi mandi sei schermate vere dal telefono, una per
   frase.
7. **Privacy dell'app:** "Nessun dato raccolto". **Età:** 4+. **Prezzo:** gratuita, in
   tutti i paesi.
8. **Invia per la revisione** quando i tester esterni non trovano più niente di grave.
   Scegli **rilascio manuale**: così decidi tu il giorno, e puoi farlo coincidere con il
   primo post (`lancio/STORIA.md`).

## 0quater. Il sito su Cloudflare, passo per passo (D61)

Le pagine sono pronte in `site/`: home, **privacy** e **assistenza**, in inglese e in
italiano, con l'icona, le anteprime per chi condivide il link e nessun tracciamento.
Tempo: venti minuti, più l'attesa del dominio. Costo: il dominio, una quindicina di euro
l'anno. Il resto è gratis.

**Prima di cominciare:** unisci la PR di questo branch in `master`, così Cloudflare
pubblica sempre `master` e non un branch di lavoro. Se non vuoi aspettare, al punto 2
scegli `claude/notes-homescreen-app-drbsfv` e cambialo dopo (Settings → Builds →
Branch control).

1. **L'account.** Vai su dash.cloudflare.com e registrati (gratis). Conferma l'email.
2. **Il sito.** Nel menu a sinistra: **Workers & Pages** → **Create** → scheda **Pages**
   → **Connect to Git** (o "Import an existing Git repository"). Autorizza GitHub e
   concedi l'accesso **solo** al repository `cryptopals`. Poi:
   - Project name: **`instink`** (diventa `instink.pages.dev`; se è preso, `instink-app`);
   - Production branch: **`master`**;
   - Framework preset: **None**;
   - Build command: **lascia vuoto**;
   - Build output directory: **`site`**;
   - **Save and Deploy**. Dopo un minuto il sito è su `https://instink.pages.dev`. Aprilo
     dal telefono e controlla privacy e assistenza.
   Da qui in poi ogni push su `master` aggiorna il sito da solo.
3. **Il dominio.** Nel menu: **Domain Registration** → **Register Domains** → cerca
   `instink.app` → acquista (Cloudflare lo vende al prezzo di costo, senza rincari
   al rinnovo). Già che ci sei guarda se c'è `instink.com`: se costa il prezzo normale,
   prendilo, perché la gente scrive ".com" per abitudine. Se costa centinaia di euro, no.
4. **Il dominio sul sito.** **Workers & Pages** → `instink` → **Custom domains** → **Set up
   a custom domain** → `instink.app` → Activate. Ripeti per `www.instink.app`. Il
   certificato HTTPS arriva da solo in pochi minuti.
5. **L'email.** Cloudflare → il dominio `instink.app` → **Email** → **Email Routing** →
   **Get started**. Crea l'indirizzo **`hello`**, destinazione la tua Gmail, conferma dal
   link che ti arriva, e lascia che Cloudflare aggiunga da solo i record DNS. Scrivi una
   prova a `hello@instink.app` da un altro indirizzo. Per **rispondere** da quell'indirizzo:
   Gmail → Impostazioni → Account → "Invia messaggio come" → aggiungi `hello@instink.app`
   (serve una password per le app di Google; se è un problema, all'inizio rispondi pure
   dalla tua casella).
6. **Controlla l'anteprima del link:** incolla `https://instink.app` in una chat di
   WhatsApp con te stesso. Deve comparire l'immagine col segno e "Write on instinct.".

**Piano B, senza collegare GitHub:** Workers & Pages → Create → Pages → **Upload assets**,
e trascini la cartella `site` scaricata dal repository. Funziona, ma ogni modifica va
ricaricata a mano.

**In App Store Connect**, appena il sito è in rete:

- Informazioni sull'app → **URL della privacy**: `https://instink.app/privacy.html`
  (o `https://instink.pages.dev/privacy.html` finché il dominio non c'è);
- **Privacy dell'app** → Inizia → **No, non raccogliamo dati**. È vero, e nella scheda
  compare "Nessun dato raccolto": per chi scarica un'app di note è un argomento;
- TestFlight → **Informazioni sul test**: URL della privacy e email per i commenti, che
  servono per il **test esterno**;
- alla prima versione per lo store: **URL di assistenza**
  `https://instink.app/support.html` e **URL di marketing** `https://instink.app`.

## 0quinquies. Cosa provare: scrittura letta, voce, Siri (D62–D64)

Prima una build nuova: Codemagic → **iOS — TestFlight** (il controllo `ios-check` parte da
solo a ogni push; se è rosso, copiami il blocco "ERRORI").

1. **La scrittura letta.** Scrivi a mano "latte pane" sul foglio, Fatto, apri l'app.
   Dopo qualche secondo il bigliettino ha sotto la didascalia "latte pane", e cercando
   "pane" la nota si trova. Prova anche corsivo e stampatello: dimmi quale legge male.
2. **Una data scritta a mano.** "Dentista domani alle 10", a mano. Aprendo la nota compare
   "Ricordamelo".
3. **Il microfono** (corretto dopo la prima prova: la registrazione non partiva). Sul
   foglio, terza icona in basso. La prima volta chiede microfono e
   riconoscimento vocale. Parla qualche secondo, tocca la pillola rossa: sul foglio compare
   la forma d'onda con la durata. Fatto. Nell'app, la nota ha il tasto ▶ e, dopo qualche
   secondo, il testo. Se il testo non arriva mai: Impostazioni → Generali → Tastiera →
   Dettatura, controlla che la lingua sia scaricata (serve per il riconoscimento senza rete).
4. **Siri a telefono bloccato.** Blocca l'iPhone e di' **"Ehi Siri, aggiungi una nota a
   Instink"**, poi detta. Siri risponde "Salvata" senza chiederti di sbloccare. Sblocca,
   apri l'app: la nota c'è. Se Siri non riconosce la frase, apri Comandi rapidi una volta:
   le frasi di un'app nuova a volte compaiono solo dopo.
5. **"Manda a…" di una nota vocale**: nel foglio di condivisione, oltre al testo, c'è il
   file audio.
6. **Android** (quando rifai il build): nell'archivio, "Smista N" in alto a destra e, se
   hai note di più di una settimana, la nota riemersa sotto la ricerca.

Dimmi cosa non torna, anche le sensazioni: "la pillola rossa è troppo", "il testo letto è
sbagliato", "Siri non capisce il nome".

## 0sexies. Per "Condividi verso Instink": l'App Group (D65)

Da qualunque app — Safari, Messaggi, Foto — "Condividi → Instink" per farne una nota. Serve
un'estensione, e un'estensione può scrivere dove l'app legge solo con un **App Group**. È
configurazione tua, sul portale Apple, e va fatta **prima** che io scriva l'estensione: se la
scrivo prima, ogni build su TestFlight si ferma sulla firma.

Su developer.apple.com → Certificates, IDs & Profiles:

1. **Identifiers → + → App Groups** → `group.app.inknote`.
2. **Identifiers → `app.inknote.ios`** → spunta **App Groups** → Configure → scegli
   `group.app.inknote` → Save.
3. **Identifiers → + → App IDs → App** → Bundle ID `app.inknote.ios.share`, descrizione
   "Instink Share", spunta **App Groups** con `group.app.inknote`.
4. **Profiles**: rigenera il profilo App Store di `app.inknote.ios` (è cambiato al punto 2)
   e creane uno nuovo App Store per `app.inknote.ios.share`.
5. **Codemagic** → Code signing identities → iOS provisioning profiles → Fetch profiles, e
   prendi i due.

Poi dimmi "fatto" e scrivo l'estensione.

## 0septies. Le icone (D66)

Tutto è già nel repository, e si rigenera con tre comandi (sono in
`tools/brand/icons.py`).

| Dove | File | Cosa fare |
|---|---|---|
| iOS, l'app | `iosApp/.../AppIcon.appiconset/` | niente: entra nella prossima build, con le varianti **scura** e **colorata** di iOS 18 |
| App Store | `design/brand/store/app-store-1024.png` | niente: App Store Connect la prende dalla build |
| Android, l'app | `androidApp/src/main/res/drawable/ic_launcher_*.xml` | niente: entra nel prossimo build, anche monocromatica per Android 13 |
| Play Store | `design/brand/store/play-store-512.png` | caricala nella scheda dello store, "Icona dell'app" |
| Play Store | `design/brand/store/play-feature-graphic-1024x500.png` | "Grafica in evidenza" |
| Sito | `site/favicon.svg`, `apple-touch-icon.png`, `og.png` | niente: sono già nelle pagine |

Manda una build nuova su TestFlight e guarda l'icona sulla tua home, di giorno e col tema
scuro. Se non ti convince, dimmi cosa: si cambia in un punto solo e si rigenera tutto.

## 0octies. Per guadagnare dopo: cosa fare adesso, e cosa no (D68)

Si lancia **gratis**; Pro arriva dopo, quando fedeltà, voto e il primo pezzo di Pro ci
sono (D68, `lancio/MERCATO.md`). Adesso servono solo due cose, e nessuna è urgente:

1. **Small Business Program** (developer.apple.com → Programma per le piccole imprese):
   quando Pro esisterà, Apple prenderà il 15% invece del 30%. Gratis, una volta sola:
   tanto vale farlo adesso.
2. **I testi dello store**, da `lancio/STORE.md`: nome "Instink: Handwritten Notes"
   (sotto l'icona resta "Instink"), sottotitolo, parole chiave, testo promozionale,
   descrizione. **Il blocco "Instink Pro" non va messo**: è per dopo.

**Non ancora:** l'accordo per le app a pagamento (Business → Paid Apps) serve solo quando
Pro arriva; si può fare prima, ma non serve. E **non creare prodotti in app**: te li dico
io, con id e prezzi, quando accendiamo Pro.

## 0nonies. Il Play Store: quando e come (D68)

**Quando: questa settimana, subito dopo il sito** (il Play Console chiede l'URL della
privacy). La ragione è una sola: Google vuole **14 giorni di test chiuso con almeno 12
persone** prima di pubblicare, e quei 14 giorni non si accorciano. Cominciandoli adesso,
Android è pronto quando è pronto iOS.

**0. Prima, la prova sul telefono.** L'archivio, la foto, lo smistamento e la riemersione
su Android compilano, ma nessuno li ha ancora costruiti con l'SDK. Da Android Studio:
`./gradlew :androidApp:installDebug` sul tuo S8, e il giro di §1bis. Se qualcosa non
compila, mandami l'errore.

**1. La chiave di caricamento** (una volta sola, e **non perderla**: tienine una copia fuori
dal computer):

```
keytool -genkeypair -v -keystore instink-upload.keystore -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Poi, nella radice del repository, un file `keystore.properties` (è già escluso da git):

```
storeFile=/percorso/di/instink-upload.keystore
storePassword=…
keyAlias=upload
keyPassword=…
```

**2. Il primo bundle:** `./gradlew :androidApp:bundleRelease`. Il file è in
`androidApp/build/outputs/bundle/release/androidApp-release.aab`.

**3. Play Console → Crea app.** Nome "Instink: Handwritten Notes", lingua predefinita
inglese (Stati Uniti), app, **gratuita**. Poi, nel pannello a sinistra:

- **Test → Test interno → Crea release** → carica l'`.aab`. Accetta la firma delle app di
  Google Play (Google custodisce la chiave vera, tu tieni quella di caricamento).
- **Scheda dello store**: i testi Play di `lancio/STORE.md` (inglese, poi aggiungi
  l'italiano), l'icona `design/brand/store/play-store-512.png`, la grafica
  `play-feature-graphic-1024x500.png`, e **almeno due screenshot** dal telefono (foglio con
  una parola scritta, e l'archivio).
- **Contenuti dell'app**:
  - norme sulla privacy: `https://instink.app/privacy.html`;
  - annunci: **no**;
  - accesso all'app: tutto accessibile, nessun login;
  - classificazione dei contenuti: il questionario, risposte "no" dappertutto;
  - pubblico di destinazione: **13 anni e oltre** (non è un'app per bambini, e così non
    entrano le regole delle app per famiglie);
  - **sicurezza dei dati: "Nessun dato raccolto" e "nessun dato condiviso"**. È vero: note,
    foto e disegni restano sul telefono; il backup va sull'account Google dell'utente, e
    Google non lo conta come raccolta nostra.
- **Test → Test chiuso → Crea traccia** → aggiungi i tester (un elenco di email, o un
  Gruppo Google) → carica lo stesso `.aab` → invia per la revisione. Manda il **link di
  adesione** ai tester: devono accettare, **installare dal Play Store** e restare iscritti
  per 14 giorni di fila. Prendine 15–20, non 12: qualcuno si dimentica sempre.

**4. Dopo 14 giorni:** Dashboard → **Richiedi l'accesso alla produzione**. Google fa
qualche domanda sul test (quanti tester, cosa hai cambiato): rispondi con le note dei
tester. Poi si pubblica.

**Dopo il primo caricamento a mano**, i successivi li fa Codemagic (`android-internal`,
§0ter, punto 5).

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
3. ~~Il nome commerciale~~ — **Instink** (D55). Restano le verifiche: la scheda in App
   Store Connect, una ricerca marchi su EUIPO in classe 9, e il dominio `instink.app`
   dal registrar.
4. **Cosa sta nel Pro.** "Widget multipli" non vale più niente da quando il widget è
   bianco. La proposta: il Pro poggia su esportazione automatica verso Notion e i file,
   ricerca nel testo, punte e temi — col foglio di condivisione sempre gratuito.
5. ~~Prezzo del Pro~~ — deciso in D68: si lancia gratis, Pro dopo.

---

## 5. Cosa NON fare adesso

Ti risparmia soldi e tempo:

- ~~Non creare le schede negli store~~: la scheda App Store esiste, e i testi sono pronti
  in `lancio/STORE.md` (§0octies). Quella del Play Store aspetta Android.
- **Non aprire l'account RevenueCat.** Su iOS gli acquisti si fanno con StoreKit, senza
  intermediari: così la scheda resta "Nessun dato raccolto" (D67). E **non creare ancora i
  prodotti in app**: Pro arriva dopo il lancio (D68).
- ~~Non comprare domini prima di aver scelto il nome~~: il nome è Instink, e `instink.app`
  va comprato adesso (§0quater).

---

## 6. Dove siamo, in tre righe

Il **core condiviso è scritto e verificato**: modello dati pronto per il sync, motore
d'inchiostro, archivio SQLite con cinque migrazioni provate, giornale che mette
l'inchiostro al sicuro dal primo tratto, ricerca, uscita verso altre app, smistamento,
riemersione e date, lettura della scrittura e voce. **320 test, tutti verdi, su qualunque
macchina.**

L'**app Android compila e gira** sul tuo S8, con le misure tutte verdi. L'**app iOS è su
TestFlight** e funziona sul tuo iPhone, con scrittura letta, voce e Siri (D62, D63).
L'app parla **sette lingue**, e le schede dello store sono pronte nelle stesse (D69).

Le ragioni di ogni scelta stanno in [`CLAUDE.md`](CLAUDE.md), che è la memoria del
progetto: se una decisione ti sembra sbagliata, lì c'è scritto perché era stata presa e
cosa era stato scartato.
