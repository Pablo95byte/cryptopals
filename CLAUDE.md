# InkNote — registro del progetto e delle decisioni

> Questo file è la memoria del progetto. Contiene **cosa** abbiamo deciso, **perché**,
> e **cosa abbiamo scartato**. Va aggiornato ogni volta che si prende una decisione
> che qualcuno in futuro potrebbe voler rimettere in discussione — compreso un
> Claude che riprende il lavoro in una sessione nuova e non ha visto la
> conversazione in cui la scelta è nata.
>
> Regola: le decisioni si **aggiungono**, non si riscrivono. Una decisione superata
> si marca `SUPERATA da Dn`, con la ragione. Lo storico serve proprio a ricordare
> perché una strada era stata scartata.

Nome `InkNote`: **provvisorio**, è solo l'identificativo tecnico dei package
(`app.inknote`). Il nome commerciale si decide più avanti, con criteri di
posizionamento nello store, e non deve costare una rinomina dei package.

---

## 1. Il prodotto in una frase

Un'app per prendere appunti **scritti a mano** partendo dalla home del telefono: un
tocco, scrivi, confermi, e la nota è salvata e visibile nel widget. Poi le
ritrovi tutte aprendo l'app.

La promessa è la **velocità di cattura**: il valore non è archiviare, è non perdere
l'idea. Ogni decisione qui dentro si giudica con questo metro.

Obiettivo dichiarato: pubblicazione su App Store e Play Store (gli abbonamenti
sviluppatore sono già attivi su entrambi).

---

## 2. Il vincolo che decide la forma dell'app

**Non si può scrivere dentro un widget della home. Su nessuna delle due piattaforme.**
È un limite dei sistemi operativi, e va ricordato perché è controintuitivo e
perché ogni tanto qualcuno proporrà di "far scrivere direttamente nel widget":

- **iOS** — i widget sono interattivi da iOS 17, ma i soli elementi ammessi sono
  `Button` e `Toggle` tramite App Intents. Non esistono campi di testo né superfici
  di disegno dentro un widget.
- **Android** — i widget sono `RemoteViews`, il cui insieme di view ammesse non
  include `EditText`. Vale anche con Jetpack Glance. (È la ragione per cui il
  widget di Google Keep si limita ad aprire l'app.)

**Quindi il flusso reale è:** widget → apertura immediata sulla schermata di
scrittura, foglio già pronto → si scrive → conferma → ritorno alla home.

Due accorgimenti per cui la cosa *sembri* scrivere sulla home:

- **Android** — il widget lancia una Activity trasparente sopra il launcher: non si
  percepisce il cambio di app.
- **iOS** — non è possibile sovrapporsi alla home, quindi la continuità si ottiene
  con la transizione: il foglio nasce dal riquadro del widget.

**Su iOS vanno usate tutte le porte d'ingresso disponibili**, perché è lì che si
vince sulla velocità: widget della home, widget della schermata di blocco,
Controllo nel Centro di Controllo (iOS 18+), tasto Azione (iPhone 15 Pro e
successivi), e uno Shortcut per il tocco sul retro.

---

## 3. Decisioni di prodotto

### D1 — Inchiostro vero, non testo digitato
**Data:** 2026-09-12 · **Stato:** attiva

Si scrive con dito o pennino e resta la propria calligrafia. Non è una nota di
testo con un font che imita la scrittura.

**Perché:** come app di note testuali veloci il campo è già occupato molto bene
(Apple Notes con Quick Note dal Centro di Controllo, Google Keep, e soprattutto
Drafts, che su "si apre su un foglio bianco" ha costruito tutta la propria
identità). Con l'inchiostro vero invece: una home coperta di bigliettini **nella
calligrafia di chi li ha scritti** è riconoscibile a colpo d'occhio, si presenta da
sé negli screenshot dello store, e nessuno dei grandi la fa bene su telefono.

**Conseguenza:** il widget mostra l'inchiostro, non una trascrizione. È permesso,
perché i tratti si disegnano come immagine.

### D2 — Le note diventano cercabili con l'OCR della scrittura
**Data:** 2026-09-12 · **Stato:** attiva, da implementare

Un riconoscimento del testo scritto a mano gira in sottofondo e rende le note
cercabili, senza mai sostituire l'inchiostro mostrato.

**Perché:** è ciò che trasforma "app di scarabocchi" in "cattura veloce + archivio
consultabile", che è esattamente la richiesta iniziale ("dopo le controlli"). Senza
ricerca, a cinquanta note l'app diventa inutile.

**Come:** Vision su iOS, ML Kit Digital Ink Recognition su Android, dietro
un'unica interfaccia nel core (`core:ocr`, ancora da scrivere). Entrambi funzionano
sul dispositivo: nessun testo dell'utente esce dal telefono.

**Nota sul campo:** il riconoscimento è buono sullo stampatello, meno sul corsivo
stretto. L'interfaccia non deve mai far sembrare l'OCR un fallimento quando non
riconosce: l'inchiostro resta la nota, il testo è solo un indice.

### D3 — Entrambe le piattaforme in parallelo
**Data:** 2026-09-12 · **Stato:** attiva

**Perché:** scelta del committente. Ha già entrambi gli abbonamenti sviluppatore.

**Conseguenza tecnica pesante:** ha determinato D6. Se fosse stata "iOS prima", la
scelta ragionevole sarebbe stata Swift nativo con PencilKit.

### D4 — Gratis con sblocco una volta sola; abbonamento solo quando esisterà il sync
**Data:** 2026-09-12 · **Stato:** attiva, da implementare

| Livello | Cosa comprende |
|---|---|
| Gratis | Note illimitate, 1 widget, penna base e pochi colori. Nessun account, nessun limite di tempo. |
| Pro — acquisto singolo (~6,99 €) | Widget multipli, tutte le punte e i colori, temi, ricerca OCR, esportazione. |
| Più avanti — abbonamento annuale (~9,99 €) | **Solo** sync e backup multi-dispositivo, quando esisteranno. |

**Perché:** un abbonamento su funzioni locali, in questa categoria, si paga con le
recensioni. Il ricorrente si giustifica solo dove c'è un costo ricorrente reale e
un valore continuo, cioè il servizio di sync. Il pagamento secco senza livello
gratuito, d'altra parte, azzera le installazioni e quindi la visibilità.

**Conseguenze da rispettare nel codice:**

- Un **unico** punto in cui si risponde alla domanda "è Pro?" (`core:billing`).
  Nessun controllo di entitlement sparso nella UI.
- **RevenueCat** come livello di acquisto: un solo modello per i due store, e
  permette di cambiare prezzi e struttura senza rilasciare una nuova versione.
  Gratuito sotto i 2500 $/mese di ricavi.
- **Niente pubblicità**, in nessun livello.

### D5 — Il salvataggio è automatico, il pulsante di conferma è un'uscita
**Data:** 2026-09-12 · **Stato:** attiva

Il pulsante "ok" chiude e torna alla home, ma la nota va persistita comunque:
all'uscita dall'app, alla sospensione, a ogni tratto completato.

**Perché:** una nota persa perché l'utente non ha premuto il pulsante è una
recensione a una stella, e in un'app che si vende sulla velocità succederà.

---

## 4. Decisioni tecniche

### D6 — Kotlin Multiplatform per il core, interfaccia nativa su ciascuna piattaforma
**Data:** 2026-09-12 · **Stato:** attiva

Core condiviso in Kotlin; UI in **SwiftUI** su iOS e **Compose** su Android; widget
in **WidgetKit** e **Glance**.

**Criteri chiesti dal committente:** modularità e qualità, non velocità di uscita.

**Perché regge il caso d'uso:** il pezzo delicato è l'inchiostro, e questa divisione
lo taglia nel punto giusto.

- La **matematica del tratto** sta nel core: levigatura, spessore, contorni. Un solo
  algoritmo, quindi la calligrafia è identica su iPhone e Android.
- Il **disegno** lo fa il canvas nativo: 120 Hz ProMotion, pressione e inclinazione
  dell'Apple Pencil, palm rejection, latenza bassa.
- **Anche i widget usano la stessa matematica**, perché Glance è Kotlin e WidgetKit
  è Swift che chiama il framework Kotlin/Native: nessuno dei due deve chiedere
  all'app di disegnargli l'immagine.

**Alternative scartate:**

- **Flutter** — si consegna prima, ma i widget vanno comunque scritti nativi e non
  possono ridisegnare una nota da soli: dipenderebbero sempre dalla PNG prodotta
  dall'app. E su iOS l'inchiostro girerebbe su un canvas non di sistema.
- **Compose Multiplatform** — una sola UI, tentazione forte, ma su iOS disegna una
  propria superficie invece di usare quella di sistema: si paga esattamente nella
  qualità percepita che era il criterio.
- **PencilKit su iOS** — regala un'esperienza di inchiostro eccellente senza
  scriverla, ma darebbe un'app bellissima su iOS e diversa su Android, e un formato
  dei tratti che non controlliamo (quindi niente OCR uniforme, niente sync,
  niente esportazione nostra). Scartato come conseguenza di D3.

**Costo accettato:** due codebase di UI da mantenere, circa 1,5 volte il lavoro di
Flutter, e per iOS serve un Mac con Xcode.

### D7 — I tratti si salvano come campioni d'ingresso, la curva si ricalcola al disegno
**Data:** 2026-09-12 · **Stato:** attiva

In archivio finiscono i campioni del digitizer ripuliti e semplificati, non la
curva levigata.

**Perché:** così la stessa nota si rende alla qualità che serve (piena a schermo,
ridotta nel widget) e si può **migliorare il motore d'inchiostro in una versione
futura senza che le note vecchie restino brutte**. Salvare la curva significa
congelare per sempre la qualità del motore del giorno in cui la nota è stata scritta.

### D8 — I tratti sono immutabili, con tombstone; il merge è senza conflitti
**Data:** 2026-09-12 · **Stato:** attiva

Un tratto, una volta chiuso, non si modifica più: si aggiunge, oppure si marca
cancellato (`deletedAt`). Vale anche per le note.

**Perché:** rende il sync futuro un'unione di insiemi, cioè un'operazione che non
può perdere dati e non ha conflitti da risolvere a mano. Due dispositivi che
scrivono offline sulla stessa nota si fondono senza che nessuno dei due perda
quello che ha disegnato. `mergeNotes` in `core:model` **è già scritta e testata**
pur non essendoci ancora il sync: è la prova che il modello regge. Se non si
potesse scrivere, il modello sarebbe sbagliato, e conviene saperlo adesso e non
dopo aver venduto l'app.

### D9 — I campi per il sync ci sono dal primo giorno, anche se la v1 è locale
**Data:** 2026-09-12 · **Stato:** attiva

`NoteId`/`StrokeId` sono UUID generati sul dispositivo (non interi
auto-incrementali del database), e ogni nota ha `updatedAt`, `revision`,
`deletedAt`.

**Perché:** aggiungerli dopo significa migrare i dati degli utenti già installati,
che è il modo più rapido di perdere note altrui. Il costo oggi è quattro campi; il
costo dopo è una migrazione e dei rimborsi.

### D10 — Le coordinate stanno nello spazio logico del foglio, non in pixel
**Data:** 2026-09-12 · **Stato:** attiva

I tratti sono espressi in unità di `CanvasSize`; ogni renderer scala di
`bersaglio / canvas`.

**Perché:** la stessa nota deve risultare coerente sul telefono, sul widget piccolo,
sul widget grande e su un futuro iPad. In pixel dello schermo su cui è stata
scritta, non lo sarebbe.

### D11 — La qualità di rendering è un parametro, per stare nei limiti dei widget
**Data:** 2026-09-12 · **Stato:** attiva

`RenderQuality` (SCREEN / WIDGET / THUMBNAIL) regola passo di ricampionamento e
segmenti delle punte.

**Perché:** non è una rifinitura, è un vincolo. Su iOS l'estensione widget ha un
tetto di memoria basso ed è terminata se lo supera; su Android il bitmap del widget
passa da una transazione Binder con un limite di dimensione. Lo stesso dettaglio
che serve a schermo, nel widget, fa **sparire** la nota. Per la stessa ragione ogni
nota conserverà, oltre ai tratti, una **PNG piccola** dedicata al widget.

### D12 — Niente account e niente cloud nella v1
**Data:** 2026-09-12 · **Stato:** attiva

**Perché:** è coerente con la promessa di velocità (un onboarding con registrazione
è il contrario), diventa un argomento di vendita ("le note non escono dal
telefono"), e risparmia backend, obblighi sui dati personali e assistenza. Il
modello dati è comunque pronto (D8, D9).

### D13 — I campioni si salvano in un formato binario versionato
**Data:** 2026-09-12 · **Stato:** attiva

`StrokePointCodec`: un byte di versione, poi 16 byte per campione (`x`, `y`,
`pressure` come float a 32 bit e `tMs` come intero).

**Perché non JSON:** una riga di scrittura sono centinaia di campioni e una nota ne
ha molte; in JSON diventano decine di kilobyte di testo per nota, da rileggere e
riparsare a ogni aggiornamento del widget — dove la memoria è il vincolo (D11).

**Perché il byte di versione:** permette di aggiungere un campo al campione
(l'inclinazione del pennino, per dirne uno) **continuando a leggere le note già
salvate**. Un formato senza versione obbliga a indovinare, e sui dati degli utenti
non si indovina. Una versione non riconosciuta è un errore esplicito, non una
lettura approssimativa.

**Conseguenza:** un tratto troncato o illeggibile solleva un errore e non
restituisce una lista vuota. Un archivio corrotto letto come "nota senza tratti"
cancellerebbe la nota al primo salvataggio successivo.

### D14 — L'archivio è sincrono, e il driver lo costruisce la piattaforma
**Data:** 2026-09-12 · **Stato:** attiva

`NoteStore` espone operazioni sincrone, senza flussi osservabili. `core:store`
fornisce lo schema e apre l'archivio su un driver che gli viene passato.

**Perché sincrono:** sulle quantità in gioco — note singole, elenchi di poche
decine — SQLite locale risponde in frazioni di millisecondo. Un'API sincrona è
verificabile e identica sulle due piattaforme; chi chiama decide su quale thread
stare. L'osservazione continua, se servirà, si aggiunge sopra senza cambiare questo
contratto.

**Perché il driver sta fuori:** su Android serve il `Context`, su iOS no. Mettere
quella differenza nel core lo legherebbe alle piattaforme, contro l'invariante 5.

**Conseguenza:** `save` **unisce** i tratti e non cancella quelli assenti dalla
lista, coerentemente con l'invariante 1. Così un salvataggio partito da una copia
incompleta della nota non può far sparire inchiostro. La cancellazione vera esiste
solo in `purgeDeleted`.

### D15 — SQL portabile: nessun upsert nativo
**Data:** 2026-09-12 · **Stato:** attiva

Gli inserimenti sono `INSERT OR IGNORE` seguito da `UPDATE` dentro una
transazione, non `ON CONFLICT DO UPDATE`.

**Perché:** quella sintassi esiste da SQLite 3.24, che su Android vuol dire
Android 11. Significherebbe imporre `minSdk 30` per una comodità di scrittura,
tagliando fuori dei telefoni per niente.

**E soprattutto niente `INSERT OR REPLACE`:** REPLACE cancella la riga e la
reinserisce, quindi farebbe scattare la cascata sulla chiave esterna portando via
**tutti i tratti della nota**, e azzererebbe il percorso dell'immagine del widget a
ogni salvataggio. Ci sono due test di regressione su questo.

### D16 — Direzione visiva: carta e inchiostro, la calligrafia come segno distintivo
**Data:** 2026-09-12 · **Stato:** attiva, alternativa ancora aperta

Fondo carta caldo, bigliettini a tinte tenui, inchiostro scuro; testo dell'interfaccia
in Instrument Sans, scrittura in Caveat nei mockup. Schermate in
[`design/mockups/`](design/mockups), canvas pubblicato:
<https://claude.ai/code/artifact/226f7658-faf9-43dc-bd68-a9942e68261a>

**Perché:** la calligrafia dell'utente è l'unica cosa che questa app ha e le altre
no (D1). La direzione la mette al centro e rende la home riconoscibile a colpo
d'occhio, che è anche ciò che si vede negli screenshot dello store.

**Aperto:** la direzione B ("gesso su lavagna", inchiostro chiaro su fondo scuro) è
uno schizzo accanto alle schermate. Più sobria in home, meno riconoscibile. Va
scelta prima di rifinire l'interfaccia.

**Vincoli già rispettati nei mockup:** nessuna barra di stato né tastiera disegnate
(sul telefono quelle vere si sovrappongono), e nessun bersaglio di tocco sotto i
44 punti.

### D17 — Su Android la cattura parte dalla schermata di blocco
**Data:** 2026-09-12 · **Stato:** attiva, approvata dal committente

Un'Activity con `setShowWhenLocked(true)` compare sopra il blocco e riceve i
tocchi, senza sbloccare: è lo stesso meccanismo delle sveglie e delle chiamate in
arrivo.

**Perché:** la catena reale da "mi viene l'idea" a "è annotata" è tirare fuori il
telefono, **sbloccarlo**, trovare il widget, aprire, scrivere, confermare. Lo
sblocco e la ricerca del widget costano più di tutto il resto sommato, e questa è
l'unica strada che li elimina entrambi.

**Vincolo non negoziabile:** quel foglio è **cieco e in sola scrittura**. Niente
elenco note, niente anteprime, niente ricerca. Chi raccoglie il telefono da un
tavolo può scrivere una nota, e va bene; non deve poter leggere le tue. Qualunque
aggiunta futura a quella schermata va misurata su questa frase.

**Caveat tecnico:** subito dopo un riavvio, prima del primo sblocco, l'archivio
cifrato con le credenziali non è accessibile. In quel caso l'inchiostro va nel
giornale di scrittura (D20) su area disponibile a freddo, e viene assorbito al
primo sblocco.

**Asimmetria da ricordare:** su iPhone **non si può**. Apple non concede a nessuno
una superficie di scrittura sopra il blocco: widget della schermata di blocco,
Centro di Controllo e tasto Azione portano tutti a "sblocca prima". Il meglio
ottenibile è comprimere sblocco e apertura in un gesto solo. Il pavimento
dell'attrito è quindi diverso sulle due piattaforme, e non va raccontato come
uguale.

### D18 — La voce è la seconda modalità di cattura
**Data:** 2026-09-12 · **Stato:** attiva, approvata dal committente

Registrazione con trascrizione sul dispositivo; la nota vocale finisce accanto a
quelle scritte ed è cercabile con lo stesso meccanismo dell'OCR (D2).

**Perché:** in bici, con le borse della spesa, al volante, **scrivere a mano non è
attrito zero e non lo diventerà**, per quanto buona sia l'app. E su iPhone la voce è
l'unica cattura che funziona senza sbloccare, quindi lì non è un extra: è il
percorso più breve che esista.

**Il rischio accettato:** allarga il prodotto e può diluire il messaggio. La
scrittura resta l'identità — è quello che ci differenzia e ciò che si vede negli
screenshot dello store; la voce è il ripiego per le mani occupate, non il titolo.

### D19 — L'attrito è un numero, con un tetto che blocca il rilascio
**Data:** 2026-09-12 · **Stato:** attiva

**Tempo dal tocco al primo tratto disegnabile: massimo 400 ms**, misurato su un
Android di fascia media reale e su un iPhone, non su un emulatore. Un misuratore
in modalità debug lo mostra a ogni avvio, e **nessuna build che peggiora quel
numero si rilascia.**

**Perché:** "attrito zero" senza un numero è uno slogan, e gli slogan non
sopravvivono alla prima settimana di sviluppo in cui qualcuno aggiunge
un'animazione di apertura. Un tetto misurato invece cambia le decisioni di
architettura — è esattamente come è nata D20.

**Prima cosa da fare, prima della UI vera:** una prova di velocità, cioè
un'Activity nuda che disegna un tratto, misurata sul telefono. Se il pavimento con
un'Activity vuota è già 900 ms, nessuna rifinitura successiva lo recupera, e
conviene saperlo subito. **Richiede una macchina con Android Studio e un telefono
vero: non è misurabile nell'ambiente in cui gira questo repository.**

### D20 — Il percorso di cattura non passa dall'app
**Data:** 2026-09-12 · **Stato:** attiva

La schermata di cattura su Android **non usa Compose** e non vede né iniezione
delle dipendenze né database. È un'Activity minima con una View di disegno
normale, che accetta il primo tocco al primo fotogramma e tiene i tratti in
memoria. Su iOS, il widget apre una scena leggera, non l'app completa con il
modello dati caricato.

**Perché:** l'avvio a freddo di una Activity Compose su un telefono di fascia
media sta sui 700-1200 ms. È lì che si perde la partita di D19, non nel disegno.
Questa decisione contraddice di proposito il piano ovvio ("Compose per tutto"):
Compose va benissimo per l'archivio e le impostazioni, non sul percorso critico.

**Giornale di scrittura:** ogni tratto chiuso viene aggiunto in coda a un file
semplice nella cache, subito, fuori dal percorso critico; `core:store` lo assorbe
dopo. Così l'inchiostro è al sicuro **dal primo tratto** e non dalla conferma: se
l'utente rimette il telefono in tasca, preme il tasto laterale, riceve una
chiamata o il sistema uccide l'app, la nota c'è già. È il completamento di D5.

### D21 — Il foglio si apre nudo
**Data:** 2026-09-12 · **Stato:** attiva

All'apertura la schermata di cattura mostra **soltanto il foglio e il pulsante di
conferma**. Punte, colori e scelta della carta compaiono dopo il primo tratto.

**Perché:** ogni comando visibile prima del primo tratto è una decisione chiesta a
chi, in quel momento, non vuole decidere niente. I primi mockup avevano selettore
della carta e barra delle punte in apertura: era un errore, ed è stato corretto.

---

## 5. Struttura del repository

```
core/            Kotlin Multiplatform. Non conosce la UI e non conosce la rete.
  model/         Note, Stroke, InkPoint, Pen, CanvasSize, mergeNotes
  ink/           StrokeBuilder, CatmullRom, WidthProfile, StrokeSimplifier, InkConfig
  geometry/      StrokeGeometry (la facciata per i renderer), StrokeOutliner, Outline, Bounds
  store/         NoteStore, schema SQLDelight, schema versionato in sqldelight/databases/
design/
  mockups/       Le schermate come artboard .dc.html, più il canvas pubblicato:
                 home iOS, cattura nuda, cattura con strumenti, Android da
                 schermo bloccato, Android sopra il launcher, voce, archivio,
                 widget, paywall, direzione alternativa
```

Ancora da creare: `core/ocr` (Vision / ML Kit), `core/billing` (RevenueCat),
`androidApp`, `androidWidget`, `iosApp`, `iosWidget`.

**Regola di dipendenza da non rompere:** il core non dipende dalla UI né dalla rete.
Le dipendenze vanno in una sola direzione: `geometry → ink → model` e `store → model`. Quando i moduli
supereranno i cinque, la configurazione Gradle duplicata va estratta in un plugin di
convenzione in `build-logic/`.

**La facciata per i renderer è `StrokeGeometry`.** SwiftUI, Compose, WidgetKit e
Glance devono conoscere solo quella: danno una nota, ricevono contorni da riempire.
Tutta la calligrafia sta dal lato Kotlin, quindi è identica sulle quattro superfici.

---

## 6. Invarianti da non violare

Sono le regole su cui poggiano le decisioni sopra. Rompere una di queste non è un
bug locale: invalida il sync, o la compatibilità delle note già salvate.

1. **Un `Stroke` non si modifica dopo la creazione.** Si aggiunge o si marca
   `deletedAt`. (D8)
2. **Non si cancellano righe.** Le cancellazioni sono tombstone, altrimenti al primo
   sync tornano indietro. (D8)
3. **L'ordine di disegno è una funzione deterministica** dei dati, non l'ordine di
   arrivo: evidenziatori prima, poi `createdAt`, poi `id`. Due dispositivi che hanno
   fuso la stessa nota devono disegnarla identica. (`orderStrokes`)
4. **`mergeNotes` resta idempotente e indipendente dall'ordine degli operandi.**
   È verificato da test: se cade, il sync farebbe divergere i dispositivi.
5. **Nel core non entrano tipi di piattaforma.** Nessun colore di UIKit, nessun
   `Context`, nessun orologio di sistema: il tempo arriva iniettato (`Clock`), così
   i test sui timestamp sono deterministici.
6. **La pressione assente non si inventa.** `InkPoint.NO_PRESSURE` significa che lo
   spessore va dedotto dalla velocità: è ciò che distingue una nota scritta a mano
   da un tubo di spessore costante.
7. **Il modello non sa nulla dei pixel.** (D10)
8. **Il formato dei campioni si cambia solo alzando la versione** in
   `StrokePointCodec`, mai modificando il significato dei byte esistenti. (D13)
9. **Lo schema versionato in `core/store/src/commonMain/sqldelight/databases/`
   fa parte del repository.** È la base su cui le migrazioni future vengono
   verificate (`./gradlew verifySqlDelightMigration`), non un artefatto di build.
10. **Sul percorso di cattura non entrano database, iniezione delle dipendenze né
    Compose.** L'inchiostro deve essere disegnabile al primo fotogramma. (D20)
11. **La schermata di blocco resta cieca:** nessun contenuto di nota leggibile
    senza sblocco, mai. (D17)
12. **400 ms dal tocco al primo tratto** è un tetto che blocca il rilascio, non un
    obiettivo. (D19)

---

## 7. Ambiente e verifica

- **Gradle 8.14.3, JDK 21.** Il wrapper è nel repository: si usa `./gradlew`.
- **`./gradlew jvmTest` esegue tutti i test del core** e gira su qualunque macchina,
  Linux compreso. Il target `jvm()` dei moduli condivisi esiste esattamente per
  questo: la logica in cui si annidano i bug veri — geometria del tratto, merge,
  semplificazione — è verificabile senza né Xcode né emulatori.
- **I target iOS si configurano ma non si compilano fuori da macOS.** È normale, ed è
  il motivo di `kotlin.native.ignoreDisabledTargets=true` in `gradle.properties`.
- Per l'app Android serve Android Studio (SDK); per quella iOS un Mac con Xcode.

- **`./gradlew verifySqlDelightMigration`** controlla che schema e migrazioni
  coincidano. Va eseguito quando si toccano i file `.sq`.

Stato attuale: **75 test, tutti verdi.**

---

## 8. Convenzioni di lavoro

- **Ogni decisione che si può rimettere in discussione va in questo file**, con
  numero progressivo, data, ragione e alternative scartate. Le voci si aggiungono.
- **`CHANGELOG.md` a ogni modifica sostanziale**, nel formato Keep a Changelog.
- **Il codice del core nasce con i suoi test.** Non per disciplina astratta: è la
  parte che non si può provare a mano su un simulatore, e gli errori di geometria si
  vedono solo come "il tratto è un po' brutto", che è il tipo di bug che non si
  trova mai guardando.
- I commenti nel codice spiegano il **perché**, non il cosa; dove una scelta nasce da
  una decisione di questo file, si rimanda a quella.
- Lingua: italiano per documentazione, commenti e nomi dei test; inglese per i nomi
  di codice (tipi, funzioni, campi).

---

## 9. Prossimi passi

1. ~~`core:store`~~ — fatto: note, tratti, percorso della PNG per il widget,
   schema versionato e migrazioni verificate.
2. **Prova di velocità** — Activity nuda che disegna un tratto, misurata su un
   telefono vero contro il tetto di D19. Va fatta **prima** della UI: richiede
   Android Studio e un dispositivo.
3. `androidApp` — Activity di cattura minima (D20), in due varianti di ingresso:
   trasparente sopra il launcher e sopra la schermata di blocco (D17). Più il
   giornale di scrittura.
4. `androidWidget` — Glance, con la PNG ridotta e il ritaglio su `Bounds`.
5. `iosApp` + `iosWidget` — SwiftUI e WidgetKit sulla stessa facciata, con widget
   di blocco, Controllo e tasto Azione come porte d'ingresso.
6. `core:ocr` — Vision e ML Kit dietro un'unica interfaccia.
7. `core:voice` — registrazione e trascrizione sul dispositivo (D18).
8. `core:billing` — RevenueCat, con l'unico punto in cui si decide "è Pro?".

## 10. Questioni ancora aperte

- **Nome commerciale e posizionamento nello store.** Contano più del codice per la
  scoperta: vanno decisi guardando le ricerche reali, non a intuito.
- **Quanti widget nel livello gratuito.** Uno è la proposta; va verificato che non
  renda il livello gratuito inutile e quindi l'app non recensita.
- **Prezzo effettivo del Pro**, per mercato.
- **Gesto della gomma** con dito e con pennino, che sono casi diversi.
- **Se la nota vocale conservi anche l'audio** o solo la trascrizione. L'audio
  occupa spazio e va sincronizzato; la trascrizione da sola può sbagliare una
  parola importante.
- **Come si entra nella cattura vocale su Android** a telefono bloccato, dato che
  lì la scrittura sopra il blocco esiste già (D17) e la voce servirebbe soprattutto
  a mani occupate.
