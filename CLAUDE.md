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

Nome commerciale: **Instink** (D55). `InkNote` resta l'identificativo tecnico: package
(`app.inknote`), bundle ID, moduli e nomi nel codice. Cambiare il nome commerciale non
costa una rinomina dei package.

---

## 1. Il prodotto in una frase

Un'app per prendere appunti **scritti a mano** partendo dalla home del telefono: un
tocco, scrivi, confermi, ed è salvata. Poi, quando hai un minuto, la ritrovi nell'app e
la mandi dove tieni le tue note — Keep, Notion, Obsidian, una mail (D31).

**InkNote è il livello di cattura del sistema di note che hai già**, non un archivio che
compete con quelli.

La promessa è la **velocità di cattura**: il valore non è archiviare, è non perdere
l'idea.

> **No effort quick notes.**
>
> È la missione, nelle parole del committente, ed è il metro con cui si giudica ogni
> decisione di questo file. Non "poche azioni": **nessuno sforzo** — né fisico, né di
> attenzione, né di scelta. Una funzione che chiede all'utente di decidere qualcosa nel
> momento in cui vuole solo scrivere ha già perso, anche se è utile.

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

### Le funzionalità che tolgono attrito, e dove agiscono

La catena reale da "mi viene l'idea" a "è annotata" è: **tirare fuori il telefono →
sbloccarlo → trovare il punto d'ingresso → aprire → scrivere → salvare → tornare
indietro.** Ogni anello si accorcia con qualcosa di diverso, ed è utile vederli in un
posto solo invece che sparsi fra le decisioni.

| Anello | Cosa lo accorcia | Stato |
|---|---|---|
| Sblocco + ricerca dell'ingresso | **Cattura dalla schermata di blocco** su Android: `showWhenLocked`, foglio cieco (D17) | **provata** sul Samsung S8 |
| Sblocco + ricerca dell'ingresso | **Riquadro nelle impostazioni rapide** su Android: l'ingresso a telefono bloccato, da qualunque schermata (D33) | **provato**: sopra il blocco, senza codice |
| Sblocco | **Più porte d'ingresso su iOS**: widget di blocco, Controllo, tasto Azione, tocco sul retro (§2) | da scrivere |
| Ricerca dell'ingresso | **Il widget è un foglio bianco che si tocca tutto** (D30): nessun bersaglio da centrare, in nessun formato | Android **provato** (D33), iOS da scrivere |
| Ricerca dell'ingresso | **Ogni apertura trova un foglio bianco**: la nota si chiude quando il foglio esce dallo schermo (D34) | **provato** |
| Apertura | **Finestra trasparente sopra il launcher** su Android: non si percepisce il cambio di app | nel codice |
| Apertura | **Nessuna animazione**: tema senza `windowAnimationStyle`, `overridePendingTransition(0, 0)` | nel codice |
| Apertura | **Il percorso di cattura non passa dall'app**: niente Compose, niente iniezione, niente database (D20) | nel codice |
| Apertura | **Il primo tocco accettato al primo fotogramma**, con i campioni storici del `MotionEvent` | nel codice |
| Prima decisione | **Il foglio si apre nudo**: nessuna carta, nessuna punta, nessun colore da scegliere (D21) | nel codice |
| Salvataggio | **Giornale di scrittura con `fd.sync()`**: al sicuro dal primo tratto, non dalla conferma (D20, D22) | nel codice, testato |
| Scrittura | **Il disco non tocca il thread dell'interfaccia**: la parola dopo non aspetta la memoria flash (D35) | nel codice |
| Salvataggio | **Il giornale sta in area protetta dal dispositivo**: scrive anche prima del primo sblocco dopo un riavvio (D24) | nel codice |
| Ritorno | **OK non salva, è solo un'uscita** (D5): nessun gesto è obbligatorio per non perdere la nota | nel codice |
| Primo avvio | **Nessun account e nessun onboarding** (D12): all'apertura non c'è niente da configurare | deciso |
| Sblocco + ricerca dell'ingresso | **"Scrivi" nell'elenco delle app**: nel dock, o sul tasto laterale dei Samsung (D39) | nel codice |
| Scrittura | **Foglio senza righe e tratto da pennarello col dito**: si scrive grande, l'archivio rimpicciolisce (D42) | nel codice, da provare |
| Mani occupate | **Tastiera e foto dal foglio**, a un tocco, anche a telefono bloccato; la fotocamera sta dentro il foglio (D38, D45) | nel codice |
| Mani occupate | **Cattura a voce** (D18): l'unica strada senza sblocco su iPhone | modello fatto, cattura da scrivere |
| Scrittura, di notte | **Il foglio si scurisce col tema scuro**: a letto al buio non acceca (D52) | nel codice, Android e iOS |
| Ritorno | **Una vibrazione breve su "Fatto"**: la nota è al sicuro, senza guardare (D52) | nel codice, Android e iOS |
| Tutti | **I tetti: foglio pronto in 100 ms a caldo, 400 a freddo; tratto dietro al dito di al massimo 50 ms** (D19, D32, D36). Non sono funzionalità: impediscono alle altre di degradarsi | **tutti verdi** sul Samsung S8: 86 ms a caldo, 358–387 a freddo, tratto 13–14 ms a caldo e 30 a freddo |
| Tutti | **Restare piccoli**: un processo leggero resta nella cache del sistema, e la seconda apertura della giornata è calda (D32) | nel codice, zero dipendenze |

**Fuori da questa catena** sta la ricerca (OCR e trascrizioni, D2 e D25): riduce
l'attrito del *ritrovare*, non dello scrivere. È la seconda metà della promessa, e non
va confusa con la prima — è esattamente l'errore che D30 ha corretto, quando il widget
mostrava le vecchie note.

**Come si giudica un'aggiunta futura:** se allunga uno di questi anelli, non entra —
o entra dopo il primo tratto, dove non costa niente.

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

**Conseguenza:** ~~il widget mostra l'inchiostro, non una trascrizione.~~
**SUPERATA da D30:** in home il widget è un foglio bianco e non mostra nessuna nota. La
calligrafia resta l'identità del prodotto, ma si vede dentro l'app e negli screenshot
dello store, non sulla home dell'utente.

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
in **WidgetKit** e ~~**Glance**~~ **`RemoteViews` di piattaforma** (D33).

**Criteri chiesti dal committente:** modularità e qualità, non velocità di uscita.

**Perché regge il caso d'uso:** il pezzo delicato è l'inchiostro, e questa divisione
lo taglia nel punto giusto.

- La **matematica del tratto** sta nel core: levigatura, spessore, contorni. Un solo
  algoritmo, quindi la calligrafia è identica su iPhone e Android.
- Il **disegno** lo fa il canvas nativo: 120 Hz ProMotion, pressione e inclinazione
  dell'Apple Pencil, palm rejection, latenza bassa.
- **Anche i widget usano la stessa matematica**, perché Glance è Kotlin e WidgetKit
  è Swift che chiama il framework Kotlin/Native (dopo D30 i widget non disegnano più
  note, quindi questo argomento oggi vale solo dentro l'app): nessuno dei due deve chiedere
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
che serve a schermo, nel widget, fa **sparire** la nota. ~~Per la stessa ragione ogni
nota conserverà, oltre ai tratti, una **PNG piccola** dedicata al widget.~~
**Superata da D30:** il widget non mostra note, quindi la PNG non serve. Il parametro
resta utile per le anteprime dentro l'app.

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
**Data:** 2026-09-12 · **Stato:** attiva, **precisata da D32**: i tetti sono due (100 ms
a caldo, 400 a freddo) e le tappe misurate sono due (inchiostro accettato e inchiostro
visibile)

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

### D22 — Il giornale sta in un modulo che non può vedere l'archivio
**Data:** 2026-09-12 · **Stato:** attiva

`core:capture` dipende solo da `core:model` e `core:ink`. **Non** dipende da
`core:store`. L'assorbimento del giornale in archivio (`JournalIngest`) sta in
`core:store`, che dipende da `core:capture`: la dipendenza va in una direzione sola.

**Perché questa forma e non un solo modulo:** D20 dice che sul percorso di cattura
non entrano database né iniezione delle dipendenze. Detta così è un commento, e i
commenti si violano — fra tre mesi qualcuno avrà bisogno "solo di leggere una
impostazione" dal database dentro la cattura, e nessuno se ne accorgerà in revisione.
Messa così è il compilatore che glielo impedisce: da `core:capture` l'archivio non è
raggiungibile. **Un invariante che conta va reso una dipendenza di build, non una
frase in un file.**

**Perché l'assorbimento sta nell'archivio:** deve fondere il recuperato con il
salvato (`mergeNotes`), quindi ha bisogno di entrambi. Metterlo in `core:store` è
l'unico posto che rispetta il senso unico.

**Ordine delle operazioni, che è la parte delicata:** prima si salva, **poi** si
svuota il giornale. Mai il contrario. Se il salvataggio non riesce, il giornale resta
l'unica copia dell'inchiostro: svuotarlo prima è il solo modo di perdere una nota con
questo meccanismo. C'è un test che fallisce il salvataggio di proposito e verifica
che il giornale sopravviva. **Precisato da D35:** "svuotare" vuol dire togliere dalla
testa solo i byte letti, non cancellare il file.

**Cosa si perde comunque:** il tratto in corso nel momento in cui il processo muore.
Sono meno di un secondo di inchiostro, e recuperarlo richiederebbe scrivere durante
il movimento del dito, cioè sul percorso critico. Quando succede,
`JournalRecovery.hadTornTail` lo segnala fino all'interfaccia: è meglio dire all'utente
che un tratto si è perso che lasciargli trovare una nota incompleta senza
spiegazione.

### D23 — L'app Android entra nel build solo dove c'è l'SDK, e consuma la variante jvm del core
**Data:** 2026-09-12 · **Stato:** attiva, **provata**: il 2026-09-24 il committente ha
compilato e installato l'app su un Samsung Galaxy S8 senza modificare niente

`settings.gradle.kts` include `:androidApp` soltanto se trova l'SDK Android
(`ANDROID_HOME`, `ANDROID_SDK_ROOT` o `sdk.dir` in `local.properties`). I moduli del
core **non** hanno `androidTarget()`: l'app chiede esplicitamente la loro variante
`jvm` con l'attributo `KotlinPlatformType.jvm`, e il target `jvm` dei moduli produce
bytecode 11.

**Perché:** il plugin Android non si può mettere sul classpath senza l'SDK. Senza
questa condizione il core non si compilerebbe più su una macchina senza Android
Studio né in CI, e si perderebbe la cosa che tiene in piedi questo progetto: 113 test
eseguibili su qualunque macchina. Con l'SDK presente — Android Studio scrive
`local.properties` da sé — il modulo compare senza che nessuno modifichi niente.

**Perché bytecode 11 e non 21:** quel jar lo mangia D8, e il bytecode 21 è una fonte
di grattacapi che non vale la pena correre per nessun vantaggio.

**Rischio dichiarato:** l'attributo che seleziona la variante `jvm` **non è stato
provato con l'SDK**, perché nell'ambiente di sviluppo di questo repository il dominio
di Google è bloccato dalla policy di rete. La strada alternativa, se non regge, è
`androidTarget()` sui moduli del core, documentata in
[`androidApp/README.md`](androidApp/README.md). Va scelta solo in second'ordine,
perché lega il build del core all'SDK.

**Nessuna dipendenza esterna in `:androidApp`:** né AndroidX, né Material, né
librerie di iniezione. Le `Activity` sono quelle di piattaforma. Non è minimalismo
per sport: ogni libreria sul percorso di avvio è tempo che l'utente aspetta prima di
poter scrivere, e un pavimento misurato con mezzo framework addosso non dice niente
(D19).

### D24 — Il giornale sta nell'area protetta dal dispositivo, non dalle credenziali
**Data:** 2026-09-12 · **Stato:** attiva · **Precisa D17 e corregge D20**

Il file del giornale vive in `createDeviceProtectedStorageContext().filesDir`, e
`CaptureActivity` è `directBootAware`.

**Perché:** è l'unica area disponibile **prima del primo sblocco dopo un riavvio**, e
senza di essa la cattura sopra la schermata di blocco (D17) non potrebbe salvare in
quel caso — che era annotato come caveat aperto in D17. Ora non lo è più.

**Compromesso accettato:** in quell'area la cifratura è legata al dispositivo e non
alla credenziale dell'utente, quindi è più debole. Il giornale però è transitorio:
vive fino al primo assorbimento in archivio, mentre le note vere stanno nell'area
protetta dalle credenziali.

**Correzione a D20:** lì si diceva "un file semplice nella cache". Sbagliato: la
cache il sistema la può svuotare quando vuole, e nel giornale c'è inchiostro non
ancora archiviato. Sta nei file dell'app.

### D25 — Le note vocali stanno nella stessa nota, come lista, e l'audio si conserva
**Data:** 2026-09-12 · **Stato:** attiva · **Chiude una questione aperta del §10**

`Note` porta `voiceClips: List<VoiceClip>` accanto a `strokes`. Una nota può avere
inchiostro, voce, o entrambi. L'audio si conserva su disco (`audioPath`), non solo la
trascrizione.

**Perché l'audio e non solo il testo:** per una nota vocale **l'audio è la nota**, e la
trascrizione è l'indice che serve a ritrovarla — lo stesso rapporto che c'è fra
l'inchiostro e l'OCR (D1, D2). Il riconoscimento del parlato sbaglia una parola ogni
tanto, e se quella parola era un numero di telefono la nota senza audio è persa.
Tenere l'originale nell'un caso e buttarlo nell'altro sarebbe incoerente.

**Perché una lista e non un campo singolo:** con un campo singolo due dispositivi che
registrano offline sulla stessa nota si sovrascrivono e uno dei due perde la
registrazione. Una lista con id si fonde per unione, come i tratti (D8), e il merge è
scritto e testato.

**Perché non una gerarchia `InkNote | VoiceNote`:** avrebbe spezzato `mergeNotes`, la
facciata della geometria e tutto l'archivio per guadagnare niente — e avrebbe reso
impossibile una nota che è scritta *e* dettata. Il campo in più costa una riga.

**La trascrizione non diventa mai vecchia:** una registrazione, una volta fatta, non
cambia più. Quindi non serve il campo "revisione a cui il testo si riferisce" che
invece serve per l'inchiostro, dove la nota si può continuare a scrivere.

**Conseguenza da non dimenticare:** `needsRecognition` ora richiede `hasInk`. Senza
quella condizione una nota di sola voce resterebbe in coda all'OCR per sempre, senza
mai avere niente da leggere. La stessa condizione è nella query dell'archivio.

**Lasciato fuori di proposito:** il giornale di scrittura (D20, D22) non copre l'audio.
Una registrazione interrotta dalla morte del processo si perde, perché proteggerla
vorrebbe dire scrivere metadati durante la registrazione. Va deciso quando si scrive
`core:voice`.

### D26 — I tombstone non si possono togliere con un salvataggio
**Data:** 2026-09-12 · **Stato:** attiva

Nell'archivio, i campi `deleted_at` di note, tratti e registrazioni si aggiornano con
`coalesce(deleted_at, :nuovo)`: si possono mettere, non togliere. Stessa forma per
trascrizione e percorso dell'audio, nella direzione opposta — fra "c'è" e "non c'è
ancora" vince "c'è".

**Perché:** era un bug reale. `save` con un'assegnazione secca permetteva a un
salvataggio partito da una copia vecchia della nota di **far resuscitare un tratto
cancellato** o una nota cestinata, contro l'invariante 2. Succede appena due pezzi di
codice tengono in mano la stessa nota, che è la norma. Ora è lo SQL a impedirlo, non
la disciplina di chi chiama. Tre test di regressione.

**Conseguenza aperta:** il recupero dal cestino non può essere un semplice azzeramento
di `deletedAt` — né qui né al primo sync, dove la cancellazione vince comunque. Serve
un meccanismo suo: probabilmente copiare la nota sotto un id nuovo. Sta fra le
questioni aperte del §10.

### D27 — La ricerca confronta parole normalizzate, non sottostringhe
**Data:** 2026-09-12 · **Stato:** attiva

`SearchText.normalize` riduce il testo a minuscolo senza accenti con le parole separate
da spazi; `NoteSearch` richiede che **tutte** le parole cercate siano presenti, in
qualsiasi ordine, e ordina i risultati per pertinenza. Le forme normalizzate stanno in
archivio in colonne accanto alla loro sorgente.

**Cosa era rotto, e cosa no:** il `LIKE` di SQLite pareggia già maiuscole e minuscole
**ASCII**, quindi "SPESA" trovava "spesa" anche prima — l'avevo detto sbagliato.
Restavano tre problemi:

1. **gli accenti** — "caffe" non trovava "caffè", e in italiano chi cerca in fretta non
   li mette;
2. **le maiuscole non ASCII** — "PERCHÉ" non trovava "perché";
3. **l'ordine delle parole** — "pane latte" non trovava "latte, pane, caffè", perché
   `LIKE` cerca una sottostringa contigua. È il difetto che si incontra per primo: la
   gente digita parole, non sottostringhe.

**Dove sta cosa, e perché:** SQL restringe con **una** parola, la più lunga (in genere
la più selettiva), su una colonna normalizzata. Tutto il resto — tutte le parole
presenti, in che ordine presentare i risultati — si decide in Kotlin su poche
candidate. Chiedere a SQLite "tutte le parole in qualsiasi ordine" vorrebbe dire una
query di lunghezza variabile, oppure FTS5, che su Android dipende dalla versione di
SQLite del telefono e imporrebbe un minimo di sistema per una comodità: la stessa
ragione di D15.

**La query delle candidate riporta solo id, istante e testo normalizzato**, non la nota
intera: caricare tratti e registrazioni di ogni candidata sarebbero due query per nota
su centinaia di righe, per poi scartarne quasi tutte. Le note complete si leggono solo
per quelle che finiscono nei risultati.

**Ordinamento:** una parola che comincia una parola del testo vale il doppio di una che
capita in mezzo — cercando "pane", "pane integrale" batte "accompanare". Poi la nota
più recente, poi l'id, perché due ricerche identiche devono dare lo stesso ordine.

**`SearchText.VERSION` va alzato ogni volta che la normalizzazione cambia risultato.**
Le note indicizzate da una versione precedente si riconoscono
(`notesNeedingSearchIndex`) e si ricalcolano (`reindexSearch`, da chiamare all'avvio
fuori dal percorso critico). Senza quel numero, migliorare la normalizzazione
lascerebbe in archivio un indice misto e alcune note diventerebbero introvabili senza
che nessuno capisca perché. È lo stesso meccanismo che serve al riempimento dopo una
migrazione, che in SQL non si può fare: `lower()` non toglie gli accenti.

**Limite accettato:** la prima parola tira su al massimo 500 candidate. Con una parola
molto corta e un archivio molto grande qualche risultato buono può restare fuori.
Alzare il numero vuol dire scandire più righe a ogni tasto digitato.

### D28 — Le colonne aggiunte da una migrazione vanno in fondo alla tabella
**Data:** 2026-09-12 · **Stato:** attiva

Nei file `.sq`, una colonna introdotta da una migrazione si scrive **alla fine** del
`CREATE TABLE`, nell'ordine in cui la migrazione la aggiunge — anche quando starebbe
più leggibile accanto alla colonna di cui è la forma normalizzata.

**Perché:** `ALTER TABLE ADD COLUMN` accoda. Mettendo la colonna in mezzo al
`CREATE TABLE`, un database creato da zero e uno migrato hanno le colonne in ordine
diverso, e `SELECT *` le legge per posizione. È esattamente il caso che
`verifySqlDelightMigration` ha intercettato mentre si scriveva D27, ed è il motivo per
cui quel controllo esiste.

### D29 — L'inquadratura dei widget sta nel core, e la leggibilità viene prima della quantità
**Data:** 2026-09-12 · **Stato:** **SUPERATA da D30** dopo poche ore di vita

Il widget non mostra più note, quindi non c'è più niente da inquadrare in un widget:
`WidgetFraming` è stato cancellato. Sopravvivono, in `NoteFraming`, le due regole che
valgono in qualunque riquadro dentro l'app — si inquadra l'inchiostro e non il foglio,
e l'ingrandimento ha un tetto — più la scelta del dettaglio in base al riquadro.

La voce resta qui per intero perché il ragionamento che segue è ancora quello giusto
*per l'archivio*, e perché serve ricordare che la regola dell'area di cattura sul lato
lungo era stata trovata da un test: informazione che tornerà utile se un giorno si
rimetterà mano ai widget.

`WidgetFraming.layout` in `core:geometry` decide quante note stanno in un widget, in
quali caselle, con quale ritaglio e a quale dettaglio. **Quali** note non lo decide:
arrivano già scelte e ordinate da chi chiama (archivio più configurazione del widget).

**Perché in `core:geometry` e non in un modulo nuovo:** inquadrare è geometria, e quel
modulo ha già `Bounds` e `RenderQuality`. Un sesto modulo avrebbe fatto scattare il
debito del plugin di convenzione (§5) per duecento righe. Il confine resta però netto:
qui non entra nessuna politica su *quali* note mostrare.

**Le regole, tutte dalla missione:**

- **La leggibilità viene prima della quantità.** Il numero di note mostrate lo decide lo
  spazio, non il formato: se la griglia preferita darebbe caselle sotto il minimo
  leggibile, si mostrano meno note più grandi. Un widget con sei francobolli
  indistinguibili costringe ad aprire l'app, cioè esattamente lo sforzo che vogliamo
  togliere. E se nemmeno una casella è leggibile, il widget resta **solo** una porta.
- **Si inquadra l'inchiostro, non il foglio** (`Bounds` più un margine), altrimenti tre
  parole scritte in alto a sinistra sono invisibili.
- **L'ingrandimento ha un tetto.** Due parole ritagliate strette e portate a riempire una
  casella grande sembrano un manifesto, non una nota.
- **L'area di cattura c'è sempre**, e nel formato piccolo — o con il widget vuoto — è
  tutto il widget: nessun bersaglio da centrare col pollice.
- **L'area di cattura va sul lato lungo.** Su un widget basso e largo (il formato medio)
  una striscia in alto costerebbe 44 punti su 131 di altezza utile, cioè più spazio del
  contenuto; di lato costa 44 su 305 di larghezza. È la regola della leggibilità
  applicata al pulsante, e l'ha trovata un test che cadeva.
- **Il dettaglio lo decide la casella, non il formato**: un widget grande con quattro note
  ha caselle piccole quanto quelle di un medio con due (D11).
- **Le note vocali entrano nel widget.** Senza inchiostro da mostrare si disegna la
  trascrizione; se una nota ha entrambi vince l'inchiostro, perché è quello che si
  riconosce a colpo d'occhio (D1). Lasciarle fuori vorrebbe dire che metà della cattura
  (D18) non si vede in home.
- **La prima nota va nella prima casella**, e a parità di ingresso l'uscita è identica.

### D30 — In home il widget è un foglio bianco. Non mostra nessuna nota
**Data:** 2026-09-12 · **Stato:** attiva, decisa dal committente ·
**Supera la conseguenza di D1 e la maggior parte di D29**

Il widget, in tutti i formati, è **un foglio bianco e nient'altro**: nessuna nota,
nessun conteggio, nessuna intestazione, niente da configurare. Si tocca in qualunque
punto e il foglio vero si apre già pronto. **Dalla home si possono solo aggiungere
note, mai rileggerle.**

**Cosa non cambia:** dentro un widget non si può disegnare (§2). Il widget *sembra* un
foglio su cui scrivere perché non c'è nient'altro da guardare né da centrare; il tratto
lo prende la schermata di cattura, un tocco dopo.

**Perché è meglio di quello che avevamo:**

1. **Risolve un'incoerenza vera.** La schermata di blocco era cieca per privacy
   (invariante 11), ma la home mostrava spesa, indirizzi e numeri di telefono a
   chiunque passasse accanto al telefono appoggiato su un tavolo. Ora la regola è una
   sola, su tutte le superfici fuori dall'app.
2. **Rispetta la missione meglio.** Mostrare le vecchie note è *ritrovare*, non
   *catturare*: è la seconda metà della promessa messa nel posto della prima. Il
   ritrovare ha già il suo posto, che è l'archivio dentro l'app.
3. **Cancella un sottosistema intero.** Un widget senza dati non va mai aggiornato, non
   mostra mai una nota vecchia, non sfonda il tetto di memoria di D11, non ha bisogno
   della PNG per nota, e su iOS l'estensione non deve nemmeno leggere l'archivio.

**Cosa costa, e va detto:** perde il gancio di marketing di D1 — "la home coperta della
tua calligrafia" — che era l'argomento più forte per gli screenshot dello store. La
vetrina si sposta sulla **sequenza** (tocco → scrivi → fatto) e sull'archivio.
L'artboard `Sequenza` esiste per questo.

**Conseguenze già applicate:**

- `WidgetFraming` **cancellato**: senza note nel widget non serviva più a niente, e
  tenere codice inutile "per sicurezza" è il modo in cui un progetto imputridisce. Ne
  resta `NoteFraming`, le due regole che valgono in qualunque riquadro dentro l'app —
  si inquadra l'inchiostro e non il foglio, e l'ingrandimento ha un tetto.
- La schermata di configurazione del widget è **cancellata dai mockup**: non c'è più
  niente da configurare oltre al formato, che si sceglie mettendolo in home.
- La colonna `widget_image_path` in archivio **resta ma è inutilizzata**: togliere una
  colonna in SQLite costa una migrazione con ricostruzione della tabella, cioè un
  rischio sui dati per nessun guadagno. Va lasciata lì, documentata come morta.

**Aperto:** il foglio è del tutto nudo, o porta un solo segno tenue? Un rettangolo
bianco vuoto può sembrare un widget rotto o non caricato. Proposta: un segno, a basso
contrasto. Vedi l'artboard `DueVarianti`.

### D31 — InkNote alimenta il sistema di note che l'utente ha già. Esportazione, mai sincronizzazione
**Data:** 2026-09-13 · **Stato:** attiva, decisa dal committente ·
**Cambia il posizionamento del prodotto**

Una nota, quando l'utente vuole, si manda fuori: a Google Keep, Notion, Obsidian, Apple
Note, a un messaggio, a una mail. InkNote è **il livello di cattura** di qualunque
sistema di note la persona usi già, non un archivio che compete con quelli.

**Perché è una posizione migliore di "un'altra app di note":** il motivo per cui
qualcuno non installa un'altra app di note è sempre lo stesso — *"uso già Notion, non
voglio un secondo archivio da controllare"*. Se alimentiamo il suo sistema, l'obiezione
sparisce. Ed è onesto su cosa sappiamo fare: siamo costruiti per catturare (400 ms,
zero decisioni), non batteremo Notion sull'archivio, mai.

Combacia anche con D12: si manda **sull'account dell'utente**, non sul nostro. Restiamo
un'app locale senza backend e senza registrazione.

**Il confine, e non è negoziabile: esportazione, mai sincronizzazione.** Una direzione
sola, nessuna rilettura, nessuno stato condiviso con la destinazione. Sincronizzare
vorrebbe dire ereditare la risoluzione dei conflitti con sistemi che non controlliamo:
per uno sviluppatore solo è il modo di passare le giornate a leggere segnalazioni.

**Quando si manda: mai nel momento della cattura.** Mandare richiede di scegliere una
destinazione, cioè una decisione, nell'istante in cui l'utente non vuole decidere
niente; e il riconoscimento del testo non è ancora arrivato. Si manda **dopo**,
dall'archivio, una nota o molte insieme. È la missione applicata: cattura adesso,
smista quando hai un minuto.

**L'inchiostro diventa testo più immagine.** Keep e Notion accettano testo, non
calligrafia. Si manda quindi il testo riconosciuto **con l'immagine dell'inchiostro
allegata**: il testo serve a leggerla e ritrovarla dove è arrivata, l'immagine è
l'originale e non sbaglia parole. Quando il riconoscimento non è completo lo dice il
file stesso, non solo l'app: la nota vivrà altrove, e chi la rilegge lì deve saperlo.

**Il fatto tecnico che decide il piano: Google Keep non si integra.** Non esiste un'API
pubblica usabile da un'app di consumo — quella che esiste è riservata a Google Workspace
Enterprise. Per Keep l'unica strada è il foglio di condivisione del sistema. **Da
verificare prima di scriverlo in una scheda dello store.**

**Il piano, in due fasi:**

1. **Il foglio di condivisione, e basta.** Si produce testo più immagine e si passa al
   sistema: da lì arriva a Keep, Notion, Obsidian, Bear, Apple Note, Todoist, mail,
   messaggi — e ad app che ancora non esistono. Zero integrazioni, zero OAuth, zero
   chiavi, zero backend. Copre quasi tutto in giorni, non settimane.
2. **Una o due integrazioni vere, solo se qualcuno le chiede.** Notion (API seria,
   pubblico che paga) e una cartella di Markdown (Obsidian e i sistemi a file).

**Conseguenza sul Pro, e ripara il buco che D30 aveva aperto:** "widget multipli" non
vale più niente su widget bianchi. "Manda le note dove vuoi, automaticamente" è una
ragione vera per pagare. Il foglio di condivisione resta gratuito — è il minimo per non
essere un'app che tiene in ostaggio le note.

**Perché si registra ogni invio (`Note.exports`):** senza sapere cosa è già andato e
dove, un secondo invio creerebbe una seconda pagina in Notion. L'utente si troverebbe
**l'archivio altrui** pieno di doppioni per colpa nostra, ed è il tipo di danno che non
si ripara con un aggiornamento. Il campo costa una riga adesso; dopo costa una
migrazione e delle scuse — lo stesso argomento di D9 e D25.

**Revisione monotona, e non è un dettaglio:** in archivio `revision` e `updated_at`
della nota si aggiornano con `max`, non con un'assegnazione secca. Salgono solo, per
costruzione, e `mergeNotes` prende già il massimo. Senza il `max`, un salvataggio partito
da una copia vecchia faceva retrocedere la nota **sotto la revisione del suo stesso
invio**, e la coda la rimandava per sempre duplicandola nell'archivio dell'utente. La
coda confronta con `>=` e `needsResendTo` con `<`, per la stessa ragione. Cinque test di
regressione, trovati da una revisione del codice.

**Dettaglio che sembra un cavillo e non lo è:** registrare un invio **non** fa avanzare
`revision`. Mandare una nota non la modifica, e se la revisione salisse ogni invio
renderebbe "vecchi" tutti gli altri invii della stessa nota, che tornerebbero in coda
per sempre.

**Dove sta il codice:** `NoteExport` in `core:model`, accanto a `NoteSearch`, perché è
una proiezione pura del modello come quella. Le destinazioni vere (l'API di Notion, il
foglio di condivisione) sono codice di piattaforma e quando arriveranno avranno il loro
modulo. La data della riga finale arriva **da fuori** già formattata: fuso orario e
lingua sono cose di piattaforma, e il core non si tira dentro una libreria di date per
una riga in fondo a un file.

### D32 — Il tetto dell'attrito è due numeri, non uno: 100 ms a caldo, 400 a freddo
**Data:** 2026-09-13 · **Stato:** attiva · **Precisa D19** · **le tappe misurate sono
corrette da D36**: i tetti 100/400 valgono fino al foglio pronto, non fino al primo tocco

Si misurano **due tappe** con **due tetti**, e separatamente per apertura a freddo e a
caldo:

| | a caldo | a freddo |
|---|---|---|
| **Inchiostro accettato** — l'idea non si perde più | **100 ms** | 400 ms |
| **Inchiostro visibile** — l'utente vede il tratto | 150 ms | 500 ms |

**Perché non 100 ms a freddo.** Il committente ha chiesto di scendere a 100 ms, e a
freddo non è disponibile. Fra il tocco e la nostra prima istruzione il sistema fa:
gestione del tocco nel launcher, creazione del processo, caricamento e verifica delle
classi, inizializzazione del framework. **Nessuno di quei passaggi è codice nostro e
nessuno si può saltare**, e sono la fetta grossa; la nostra parte — `onCreate`, la
superficie, il primo fotogramma — è la più piccola. Un tetto che non si può rispettare
viene ignorato dopo due settimane: tanto vale non averlo.

**Perché 100 ms a caldo sì.** Con il processo già vivo resta la nostra parte più un
fotogramma. È ambizioso ma raggiungibile, **ed è il caso più frequente per chi usa l'app
ogni giorno** — quindi è anche il numero che conta di più per l'esperienza reale.

**Perché due tappe e non una.** "Accettato" e "visibile" sono due promesse diverse.
L'accettazione è la missione: da lì l'idea è al sicuro, e la superficie riceve i tocchi
**appena esiste**, prima del primo fotogramma. La visibilità è la sensazione, e arriva
dopo di almeno un intervallo di aggiornamento dello schermo — ritardo che non è nostro.
Mescolarle in un numero solo nascondeva la tappa che conta.

**La conseguenza strategica, e non è codice più furbo:** il modo di stare a 100 ms il più
spesso possibile è **restare piccoli**. Un processo leggero sopravvive più a lungo nella
cache del sistema, quindi la seconda apertura della giornata è calda. È la seconda
ragione — arrivata dopo — per cui `:androidApp` non ha nessuna dipendenza esterna (D23).

**Le leve vere sul freddo, in ordine di resa:**

1. **Profilo di riferimento** (baseline profile): precompila il percorso di avvio e
   taglia il tempo a freddo in misura significativa. È gratuito e non cambia una riga di
   logica. **Va generato su un dispositivo**, quindi aspetta la misura.
2. **Niente `Application` personalizzata, nessun `ContentProvider` nel manifest.** Ogni
   componente dichiarato viene inizializzato prima della nostra Activity. Oggi non ne
   abbiamo: va tenuto così, ed è una cosa che si perde per distrazione aggiungendo una
   libreria (molte ne registrano uno di nascosto per inizializzarsi).
3. **Accettare l'inchiostro prima del primo fotogramma**, che è già come funziona e che
   ora la misura dichiara invece di nascondere.

**Scartato per ora: la partenza "calda calda".** Invece di chiudere il foglio quando
esce dallo schermo, lo si potrebbe svuotare e tenere vivo in fondo alla pila: il tocco
successivo lo riporterebbe davanti senza nemmeno crearlo, sotto i 100 ms con margine.
Costa però due percorsi di avvio invece di uno, e un foglio vivo che il sistema può
rimettere sopra il blocco in momenti che non scegliamo noi (D34). Si riconsidera **solo
se la misura a caldo sfora**.

**La misura a caldo del misuratore è ottimista, e va detto.** A freddo si parte
dall'avvio del processo, che il sistema ci dice. A caldo il momento del tocco da dentro
l'app non si vede: si parte da `onCreate`, e i millisecondi che il sistema spende prima
non si contano. Il numero vero a caldo è quello di `adb shell am start -W` (la guida
spiega come). Il misuratore resta utile per le tappe intermedie, che sono tutte nostre.

**Scartato: tenere il processo vivo a forza.** Un servizio in primo piano o una notifica
persistente terrebbero il processo caldo sempre, e darebbero il numero buono. Ma costano
batteria, occupano la barra delle notifiche e sono il genere di cosa per cui un'app viene
disinstallata. Non vale 300 ms.

**Come si itera, e in che ordine.** Non si ottimizza un numero che non si è mai misurato:
il misuratore ora riporta anche le tappe intermedie (`superficie`, `primo fotogramma`)
proprio perché dicono **dove** si perde il tempo, e la cura è diversa a seconda del
punto. Prima la misura, poi la leva giusta.

### D33 — Su Android widget e riquadro rapido sono di piattaforma, e il riquadro è l'ingresso a telefono bloccato
**Data:** 2026-09-24 · **Stato:** attiva · **Precisa D6 e D17**

Il widget della home è un `AppWidgetProvider` con `RemoteViews`, non Glance. Accanto c'è
un **riquadro nelle impostazioni rapide** (`TileService`) che apre lo stesso foglio. Tutti
e due stanno in `:androidApp`, non in un modulo a parte.

**Perché non Glance, che D6 aveva previsto.** Glance porta con sé il runtime di Compose e
WorkManager, e WorkManager registra un `ContentProvider` per inizializzarsi. Un
`ContentProvider` dichiarato viene eseguito **a ogni avvio del processo**, compreso quello
della cattura: è esattamente la leva 2 di D32, violata da una libreria. Glance aveva senso
quando il widget disegnava note (D1, D29). Dopo D30 è un rettangolo che apre un'Activity,
e con `RemoteViews` sono trenta righe senza nessuna dipendenza.

**Perché nello stesso modulo.** Un widget deve stare nell'APK dell'app che apre, e un
modulo in più per trenta righe avrebbe fatto scattare il debito del plugin di
convenzione (§5) senza separare niente di utile.

**Perché il riquadro, e perché è la parte importante.** D17 diceva che il foglio *può*
stare sopra il blocco, ma non diceva **come ci si arriva**: su Android un'app non può
mettere una scorciatoia sulla schermata di blocco, e i widget lì non esistono più dai
telefoni di dieci anni fa. Senza un ingresso, `showWhenLocked` non serviva a niente. Le
impostazioni rapide si aprono a telefono bloccato, da qualunque schermata, con un gesto
che l'utente fa già: **scorri giù, tocca, scrivi**. È anche l'equivalente del Controllo
nel Centro di Controllo di iOS.

**Il riquadro non chiede lo sblocco, di proposito.** `TileService.unlockAndRun` lo
chiederebbe, e lo sblocco è l'anello che questo ingresso toglie. Il foglio è cieco (D17),
quindi dietro lo sblocco non ci sarebbe niente da proteggere.

**Il segno tenue.** Il widget porta il segno della variante B di `DueVarianti`, che era la
proposta. La decisione resta del committente (§10): per il foglio nudo basta togliere una
vista dal layout.

**Provato sul telefono** (Samsung S8, 2026-09-24): dal riquadro, a telefono bloccato, il
foglio compare sopra il blocco senza chiedere il codice. Anche il widget funziona.

### D34 — Un foglio vive finché è sullo schermo
**Data:** 2026-09-24 · **Stato:** attiva · **Ripara una falla contro l'invariante 11**

Quando il foglio esce dallo schermo — tasto home, schermo spento, una chiamata — la nota è
chiusa e l'Activity con lei. L'apertura successiva, da qualunque ingresso, trova un
foglio bianco. Il foglio non compare fra le app recenti.

**Cosa era rotto.** Il foglio restava vivo in secondo piano con la nota di prima. Due
conseguenze, entrambe contro decisioni prese:

1. **Privacy (D17, invariante 11).** Scrivi una nota, premi il tasto laterale senza
   premere OK. Il foglio ha `showWhenLocked`, quindi alla riaccensione compariva **sopra
   il blocco, con la nota leggibile da chiunque raccolga il telefono**. Lo stesso valeva
   per l'istantanea nelle app recenti.
2. **Il foglio bianco (D30).** Il tocco successivo sul widget riportava davanti il foglio
   vecchio, con la nota di ore prima, invece di un foglio bianco.

**Cosa costa.** Se arriva una chiamata a metà nota, al ritorno il foglio è nuovo e la nota
interrotta è già salva, ma separata. È il compromesso giusto: la nota non si perde (D5), e
un foglio che riappare da solo con dentro del testo è peggio di due note.

**Provato sul telefono** (Samsung S8): spento lo schermo senza OK, alla riaccensione si
vede il blocco e non la nota; il widget apre un foglio bianco.

**Ruotare il telefono non è uscire.** Il manifest dichiara i cambi di configurazione, così
l'Activity non viene ricreata: ricrearla avrebbe tolto dallo schermo l'inchiostro appena
scritto e spezzato la nota in due.

### D35 — Il giornale scrive su un thread suo, e si svuota solo di ciò che è stato letto
**Data:** 2026-09-24 · **Stato:** attiva · **Precisa D20 e D22, ripara due perdite di dati**

Tre cambiamenti al giornale, tutti trovati rileggendo il sistema.

**1. Un record rotto non nasconde più quelli dopo.** Il lettore si fermava al primo record
illeggibile. Ma il processo muore a metà di un tratto, e all'apertura successiva la nuova
sessione **scrive in coda, dopo i byte rotti**: da quel momento tutte le note nuove erano
invisibili, e l'assorbimento, svuotando il giornale, le avrebbe cancellate senza averle
mai salvate. Riprodotto con un test prima di correggerlo. Ora davanti a un record che
non torna il lettore avanza di un byte e cerca il prossimo record valido: lunghezza
plausibile e checksum giusto, che per caso capita una volta su quattro miliardi.

Un record con la cornice integra ma di una **versione futura** non è spazzatura: la
lettura si ferma lì e nessuno svuotamento lo tocca (D13).

**2. Svuotare vuol dire togliere dalla testa ciò che si è letto.** `clear()` cancellava il
file intero dopo il salvataggio. Ma fra la lettura e lo svuotamento l'archivio impiega il
suo tempo, e intanto la cattura può aggiungere un tratto: cancellarlo significava perdere
inchiostro mai arrivato in archivio, contro l'invariante 13. Ora `InkJournalSink` ha solo
`discardPrefix(n)`, e `InkJournal.discard` toglie esattamente i byte che il `recover`
corrispondente ha letto. **Non esiste più uno svuotamento totale**: l'API non permette di
sbagliare. Le tre operazioni del sink devono escludersi a vicenda; su Android lo garantisce
il thread unico del punto 3, su iOS servirà un lock sul file perché widget e app sono
processi diversi.

**3. La scrittura su disco esce dal thread dell'interfaccia.** `fd.sync()` aspetta che la
memoria flash confermi: pochi millisecondi su un telefono buono, decine su uno economico.
Fatto al sollevamento del dito, cadeva fra un tratto e il successivo — mentre l'utente
scrive, la parola dopo partiva in ritardo. D20 stesso diceva "fuori dal percorso critico",
e il codice non lo rispettava. Ora c'è un solo thread di scrittura per processo.

**Cosa costa il punto 3, e va detto.** "Al sicuro quando `endStroke` ritorna" diventa "al
sicuro pochi millisecondi dopo". La finestra si chiude quando il foglio esce dallo schermo
(`awaitWrites` in `onStop`), che è il momento in cui il sistema può uccidere il processo;
a foglio aperto il processo è in primo piano e non viene ucciso. Un errore di scrittura
non risale più al chiamante: lo conta il sink, e il foglio mostra un avviso — l'unico
testo che può comparire prima del primo tratto, perché è l'unico caso in cui la promessa
non vale.

### D36 — Il misuratore misura il telefono, non la mano
**Data:** 2026-09-24 · **Stato:** attiva · **Corregge le tappe di D32**

I tetti di D32 (100 ms a caldo, 400 a freddo) valgono **dal gesto al foglio pronto**, cioè
al primo fotogramma: da lì un dito che tocca il vetro lascia inchiostro. Il secondo
numero è la **latenza del tratto**: dal dito sul vetro — l'istante dell'hardware, non
quello in cui l'evento ci arriva — all'inchiostro disegnato, con un tetto di **50 ms**
uguale a freddo e a caldo.

**Cosa era sbagliato.** D32 misurava fino al "primo inchiostro accettato", che arriva solo
quando l'utente tocca. Il numero conteneva quindi **il tempo della mano**: sul primo
telefono vero il misuratore diceva 1468 ms in rosso, mentre `am start -W` diceva che il
foglio era pronto in 360. Un tetto che dipende da quanto in fretta si muove chi prova
l'app non misura l'app, e un numero rosso falso è peggio di nessun numero: insegna a
ignorare il rosso.

**Perché il tocco dall'istante dell'hardware.** Ogni `MotionEvent` porta il momento in cui
il digitizer ha sentito il dito. Misurare da quando l'evento ci viene consegnato
nasconderebbe proprio il ritardo di consegna, che è parte di ciò che l'utente sente.

**Perché 50 ms.** È circa tre fotogrammi a 60 Hz. Oltre, l'inchiostro resta visibilmente
indietro rispetto al dito, e scrivere diventa difficile — che è esattamente la
lamentela che conta per un'app di scrittura. Il numero è **provvisorio**: va rivisto
quando avremo le prime misure.

**La prima misura vera (Samsung Galaxy S8, Android 9, 2026-09-24), con `am start -W`:**

| | primo fotogramma |
|---|---|
| a caldo | 86 ms — entro 100 |
| a freddo, dopo l'installazione | 1313 ms — il codice non è ancora compilato dal sistema |
| a freddo, le volte successive | 358–387 ms — entro 400, ma col margine di un fotogramma o due |

**Seconda serie, stesso giorno, col misuratore corretto:** oltre dieci aperture, tutte
verdi. Latenza del tratto **13–14 ms a caldo, circa 30 a freddo**, sotto il tetto di 50
anche nel caso peggiore. Con tutto il codice compilato in anticipo a mano
(`cmd package compile -m speed`), il freddo scende da 358–387 a **252 ms**: circa un
terzo in meno, ed è il tetto di quello che il profilo di riferimento può dare.

**Queste misure sono della build di debug**, e un'app debuggabile gira più lenta: ART
rinuncia ad alcune ottimizzazioni per permettere il debugger. Il numero che vedrà
l'utente è quello della build di rilascio, ancora da misurare (D37).

Sul freddo siamo al limite, su un telefono del 2017. La leva è il profilo di riferimento
(D32, leva 1), e il primo passo è misurare quanto potrebbe dare: compilare tutto in
anticipo a mano sul telefono e rimisurare. Il caso "subito dopo l'installazione" è
esattamente quello che il profilo di riferimento cura, e sarà la prima impressione di
ogni utente nuovo.

### D37 — L'avvio a freddo si cura con R8 e con un profilo scritto a mano, senza librerie
**Data:** 2026-09-24 · **Stato:** attiva, **da misurare** sulla build di rilascio

La build di rilascio passa da R8, e `src/main/baseline-prof.txt` dichiara come percorso di
avvio **tutto il nostro codice e tutta la parte di Kotlin che R8 lascia**. Nessuna
libreria aggiunta: niente `profileinstaller`, niente modulo di Macrobenchmark.

**Perché R8.** Senza, l'APK si porta dietro la libreria standard di Kotlin intera, e a
freddo ogni classe caricata va letta e verificata. R8 toglie quello che non usiamo e
fonde il resto: meno classi, meno lavoro prima del primo fotogramma. È gratuito, e con
zero dipendenze e zero riflessione il rischio di togliere qualcosa che serve è basso.

**Perché un profilo scritto a mano e non generato.** Il modo canonico è generarlo con
Macrobenchmark, che registra quali metodi girano all'avvio. Serve quando l'app è grande e
se ne vuole compilare solo il percorso caldo. La nostra è minuscola: il percorso di avvio
**è** quasi tutta l'app. Quattro righe con i caratteri jolly dicono la stessa cosa, e
restano giuste anche quando il codice cambia. Il tetto di quanto possono dare l'abbiamo
già misurato: compilare tutto porta il freddo a 252 ms (D36).

**Perché niente `profileinstaller`.** È la libreria che installa il profilo quando l'app
**non** arriva dal Play Store. Si inizializza con un `ContentProvider`, cioè esattamente
ciò che l'invariante 21 vieta sul percorso di avvio. Per chi installa dal Play Store il
profilo nell'APK lo usa lo store all'installazione, senza la libreria. **Da verificare**
col primo rilascio sul canale di test interno, che è anche l'unico modo di vedere il
profilo all'opera: installando col cavo il profilo non viene applicato.

**Il caso che conta di più è il primo avvio dopo l'installazione** (1313 ms sul S8): è la
prima impressione di ogni utente nuovo, ed è esattamente quello che il profilo cura.

### D38 — Tastiera e fotocamera sul foglio, come pezzi della stessa nota
**Data:** 2026-09-24 · **Stato:** attiva, chiesta dal committente · **Precisa D1 e D21**

Il foglio porta due icone piccole e tenui, in basso a sinistra: **tastiera** e
**fotocamera**. Il testo digitato e le foto diventano pezzi della nota — `TextClip` e
`PhotoClip`, accanto a tratti e registrazioni — e non note di un altro tipo.

**Perché, rispetto a D1.** D1 dice che l'identità è l'inchiostro, e resta vero: il foglio
si apre pronto per la mano, e chi scrive a mano non tocca niente. Ma "nessuno sforzo"
vuol dire anche non costringere: in piedi sul tram si digita meglio che col dito, e una
lavagna o uno scontrino si fotografano, non si ricopiano. Il committente l'ha chiesto,
ed è coerente con la missione.

**Perché non contraddice D21.** D21 toglie dall'apertura le **decisioni**: punta, colore,
carta. Due icone che si possono ignorare senza pensarci non sono una decisione: chi vuole
scrivere a mano comincia a scrivere, e le icone non gli costano niente. Stanno fuori dal
percorso del dito e sono a basso contrasto.

**Perché pezzi immutabili e non un campo di testo.** Un campo modificabile sarebbe il
primo punto del modello dove due dispositivi possono non essere d'accordo, e uno dei due
perderebbe ciò che ha scritto. Correggere un testo vuol dire marcare il vecchio come
cancellato e aggiungerne uno nuovo (D8). L'utente vede un campo che si modifica; sotto
è un'unione di insiemi, come tutto il resto.

**Il giornale li protegge da subito.** Il testo va nel giornale dopo una breve pausa
nella digitazione e quando il foglio perde il fuoco; la foto **prima** di aprire la
fotocamera, perché mentre è aperta il sistema può uccidere il nostro processo. Se lo
scatto non arriva, se ne scrive il tombstone. Il formato del giornale passa alla
versione 2, con un byte di tipo; la versione 1 si legge ancora.

**La foto a telefono bloccato** ~~usa la fotocamera in modalità sicura: si apre sopra il
blocco senza chiedere il codice e senza dare accesso alla galleria.~~ **Superato da D45:**
la fotocamera del sistema, sul primo telefono vero, chiudeva il foglio e chiedeva lo
sblocco; ora la fotocamera sta dentro il foglio. Nasce nell'area
protetta dal dispositivo, come il giornale, e passa in quella protetta dalle credenziali
quando la nota entra in archivio. Per questo il modello conosce solo percorsi relativi.

~~**La fotocamera sospende D34.**~~ Superato da D45: la fotocamera non copre più il
foglio, ci sta dentro. Resta un'eccezione a D34 solo per il dialogo del permesso.

**Cosa resta fuori.** Lo scanner di documenti (ritaglio e raddrizzamento automatico) è un
miglioramento naturale della foto, ma su Android è una libreria di Google Play Services:
si valuta quando si scrive l'OCR, che la usa comunque.

### D39 — L'archivio su Android: View di piattaforma, e l'icona apre l'archivio
**Data:** 2026-09-24 · **Stato:** attiva · **Precisa D6, D20 e D23**

L'icona dell'app apre l'**archivio**: elenco delle note con anteprima, ricerca, nota
aperta con "Manda a…" ed "Elimina". Il foglio si apre dal widget, dal riquadro rapido,
dalla scorciatoia che compare tenendo premuta l'icona, dal pulsante + dell'archivio, e da
una seconda voce nell'elenco delle app, **"Scrivi"**.

**Perché l'icona apre l'archivio.** È la richiesta iniziale: "se apro l'app le ho tutte".
E D30 ha diviso i ruoli: dalla home si aggiunge, dall'app si ritrova. Chi apre l'app vuole
rileggere; chi vuole scrivere ha già quattro ingressi più rapidi dell'icona.

**Perché la voce "Scrivi".** È il modo di mettere il foglio nel dock accanto al telefono e
ai messaggi, e soprattutto di assegnarlo al **tasto laterale**: sui Samsung, doppia
pressione del tasto laterale → apri un'app → Scrivi. Zero codice, e a telefono in tasca
è l'ingresso più rapido che esista. Costa una voce in più nell'elenco delle app.

**Perché View di piattaforma e non Compose, che D6 prevedeva per l'archivio.** Compose
porta con sé librerie che si inizializzano con un `ContentProvider` (emoji2, il ciclo di
vita del processo). Nello stesso processo della cattura, quel costo lo pagherebbe **anche il
foglio**, a ogni avvio a freddo (invariante 21). Un archivio di elenco, ricerca e dettaglio
con le View di sistema è poco codice in più e zero millisecondi sul percorso che conta. Si
riconsidera se l'archivio diventa complesso, mettendolo in un processo suo.

**La prima dipendenza esterna** (precisa D23): il driver SQLite di SQLDelight, che porta
`androidx.sqlite`. È ammessa perché rispetta la regola che conta davvero — niente sul
percorso di cattura, niente `ContentProvider` — e scrivere un driver nostro sarebbe stato
codice di infrastruttura senza valore per l'utente.

**L'assorbimento del giornale** avviene quando si apre l'archivio, su un thread suo: prima
si salva, poi si svuota (D22), poi le foto passano nell'area delle credenziali, poi si
ricalcola l'indice vecchio (D27) e si eliminano le note cestinate da più di trenta giorni,
con le loro foto.

**Condividere registra l'invio solo a destinazione scelta** (invariante 18): il foglio di
condivisione avvisa quando l'utente sceglie un'app, e solo allora l'invio va in archivio.
Aprire il foglio e chiuderlo non è mandare.

### D40 — I file passano da un nostro `ContentProvider`, in un processo a parte
**Data:** 2026-09-24 · **Stato:** attiva

La fotocamera ha bisogno di un indirizzo dove scrivere la foto, e l'app di destinazione di
un indirizzo da cui leggerla. Li dà `FilesProvider`, nostro, nel processo `:files`.

**Perché non `FileProvider` di AndroidX, e perché un processo a parte.** Ogni
`ContentProvider` del processo principale viene creato **all'avvio del processo, prima del
foglio** (invariante 21). In un processo suo nasce solo quando un'altra app chiede un
file: la cattura non lo vede mai. Scriverlo costa sessanta righe.

**Cosa permette.** Leggere foto e immagini esportate. ~~Scrivere **solo** una foto nuova
nell'area del dispositivo.~~ **Precisato da D45:** con la fotocamera dentro il foglio
nessuno scrive più da qui, e la scrittura è stata tolta: sola lettura. Non è esportato: ci si arriva solo con un permesso concesso da
noi per un singolo indirizzo. I percorsi con `..` sono rifiutati due volte, prima come
testo e poi dopo averli risolti sul disco.

### D41 — L'inglese è la lingua di base dell'app
**Data:** 2026-09-24 · **Stato:** attiva

I testi dell'interfaccia stanno in inglese in `values/`, e in italiano in `values-it/`.

**Perché.** La lingua di base è quella che vede chiunque non abbia una traduzione. Per
un'app che punta a tutto il mondo, quella lingua è l'inglese; l'italiano è la prima
traduzione, non il punto di partenza.

**Debito dichiarato:** le etichette in fondo ai file esportati (`NoteExport`: "Scritta a
mano", "Il riconoscimento non era completo…") sono in italiano **nel core**. Vanno passate
da fuori come la data, prima del lancio.

### D42 — Il corsivo col dito: foglio senza righe e tratto da pennarello
**Data:** 2026-09-24 · **Stato:** attiva, **da provare** sul telefono

Il committente, sul primo telefono vero: col dito il corsivo è difficile, perché il dito è
grosso, il telefono è stretto e il tratto è fine. È vero, ed è un limite fisico: il dito
non diventerà mai una penna. Ma due cose lo peggioravano, e si potevano togliere.

1. **Le righe.** Una riga ogni 36 dp chiede lettere alte cinque millimetri, che col dito
   non esistono. Tolte: il foglio è bianco, come il widget. Scrivere grande non costa
   niente, perché l'archivio inquadra l'inchiostro e non il foglio, e rimpicciolisce da
   solo.
2. **Lo spessore.** Col dito la punta è più grossa (4,8 invece di 3,2): un pennarello, non
   una biro. Su lettere di un centimetro e mezzo, un tratto da biro sembra un filo e nelle
   parti veloci del corsivo sparisce. Col pennino resta sottile, perché lì c'è la
   pressione.

**La risposta strutturale, non ancora fatta:** la **fascia di scrittura ingrandita**, come
nelle app per tablet. Si scrive grande in una fascia in basso, e la parola va a posto
rimpicciolita sulla riga sopra. È la soluzione nota al problema del dito, ma cambia il
foglio e aggiunge una modalità: si decide dopo aver provato i due punti sopra.

### D43 — Il piano per il lancio: Android prima, gratis all'inizio, iOS subito dopo
**Data:** 2026-09-24 · **Stato:** proposta, **da confermare col committente**

**Il criterio.** Un'app di cattura vince con due numeri: quanti la usano ancora dopo
trenta giorni, e quanti ne parlano. Nessuno dei due si misura prima del lancio. Quindi:
arrivare presto a utenti veri, misurare, poi far pagare.

1. **Android, canale di test interno del Play Store** appena l'archivio è provato sul
   telefono. È anche l'unico modo di vedere il profilo di avvio all'opera (D37).
2. **Test chiuso con 20–50 persone vere**, non amici che dicono "bella". Il Play Store lo
   richiede comunque per i profili sviluppatore nuovi, prima della pubblicazione.
3. **Gratis al lancio, tutto incluso.** Il Pro (D4) arriva quando sapremo cosa la gente usa
   davvero. Un paywall prima di avere utenti ottimizza un numero che ancora non esiste.
4. **iOS subito dopo**, sul core già provato. Serve un Mac.
5. **Nel frattempo, in quest'ordine:** voce (D18), riconoscimento della scrittura (D2), la
   "condivisione verso InkNote" dalle altre app, poi il Pro.

### D44 — L'app Android si compila per controllo anche dove l'SDK non c'è
**Data:** 2026-09-24 · **Stato:** attiva

`tools/android-check/check.sh` compila i sorgenti Kotlin di `:androidApp` contro il
framework vero di Android 15 — il jar `android-all` che Robolectric pubblica su Maven
Central — con un `R` generato dalle risorse e due interfacce finte al posto di
`androidx.sqlite`.

**Perché.** In questo ambiente il dominio di Google è bloccato, e fino a D43 tutto il
codice Android arrivava al committente senza essere mai stato compilato: ogni errore di
battitura costava un giro col telefono. Così gli errori di API, di tipi e di import si
trovano qui. Il primo uso ha compilato al primo colpo l'archivio, la condivisione, la
fotocamera e il provider dei file, e ha trovato due avvisi, corretti.

**Cosa non controlla, e va detto:** risorse e manifest (serve `aapt2`), R8, lint, e il
comportamento. Il primo build vero resta sulla macchina del committente.

### D45 — La fotocamera sta dentro il foglio
**Data:** 2026-09-24 · **Stato:** attiva · **Supera la parte fotocamera di D38**

Toccando la fotocamera, il mirino si apre **sopra il foglio, nella stessa Activity**
(Camera2, API di piattaforma). Si scatta, il mirino si chiude, la miniatura compare sul
foglio.

**Cosa era rotto.** Sul primo telefono vero, aprire la fotocamera del sistema dal foglio
aperto al volo lo chiudeva, e chiedeva di sbloccare il telefono. Le cause possibili sono
più d'una, e tutte vengono dal lasciare la nostra app: alcune fotocamere a telefono
bloccato chiedono lo sblocco; alcune girano in un compito loro, e allora Android ci
risponde subito "annullato" — il foglio crede di essere stato abbandonato e si chiude
(D34), la foto va persa. Non serviva capire quale: la soluzione le toglie tutte.

**Perché dentro il foglio.** Resta sopra il blocco come il foglio stesso (D17), non cambia
app, non cambia compito, e la foto arriva a noi come byte. Niente libreria (CameraX è
AndroidX), niente `ContentProvider` (invariante 21), e il provider dei file torna in sola
lettura (D40).

**Cosa costa.** Il permesso della fotocamera, chiesto al primo scatto. Se il primo scatto
avviene a telefono bloccato, il sistema può chiedere lo sblocco per mostrare il dialogo
del permesso: succede una volta sola. E un centinaio di righe di Camera2 nostre invece
dell'app fotocamera del telefono: niente zoom, niente flash manuale. Per una lavagna o uno
scontrino bastano messa a fuoco e esposizione automatiche.

**L'ordine delle operazioni.** La foto entra nella nota subito, il file si scrive su un
altro thread (invariante 20). Se il processo morisse fra le due cose, l'archivio troverebbe
una foto senza file e non la mostrerebbe: meglio di una nota senza la foto nel giornale.

### D46 — Il disegno: bigliettini di carta su una scrivania
**Data:** 2026-09-24 · **Stato:** attiva · **Precisa D16**

Il committente, vedendo l'app sul telefono: "graficamente sembra un'app degli anni '90".
Aveva ragione: pulsanti di sistema, righe sottolineate, testo attaccato ai bordi. Il
disegno nuovo, su tutte le schermate:

- **Carta su scrivania.** Le note sono bigliettini chiari, angoli tondi e un'ombra leggera,
  su uno sfondo appena più scuro. **Di notte la scrivania si scurisce e i bigliettini
  restano carta**: l'inchiostro è scuro, e un foglio scuro lo renderebbe invisibile.
- **Instrument Sans** (D16), incluso nell'app come font variabile, con pochi gradini di
  scala e peso. **Non sul foglio**: leggere un font dai file costa millisecondi sul
  percorso che misuriamo; i comandi del foglio usano il carattere di sistema.
- **Da bordo a bordo.** Il contenuto passa sotto le barre di sistema trasparenti, e ogni
  schermata sposta dentro solo ciò che deve restare leggibile. Da Android 15 è comunque
  obbligatorio.
- **Una griglia che si adatta.** L'archivio mette tante colonne quante ne stanno: due su
  un telefono, di più su un tablet o in orizzontale. La nota aperta resta una colonna di
  al massimo 680 dp, perché una riga lunga un metro non si legge.
- **Un solo pulsante pieno per schermata**: "Scrivi" nell'archivio, "Manda a…" nella nota,
  "Fatto" sul foglio. Il resto è tenue.
- **Sul foglio, i comandi in basso**, dove arriva il pollice, e niente in alto: in alto si
  scrive. Il testo digitato è una scheda sotto la barra di stato; il misuratore di debug
  è una pillola piccola in basso, che un tocco nasconde.

**Senza Material Components, di proposito.** Material e AppCompat si inizializzano con un
`ContentProvider` (invariante 21). Il disegno moderno si ottiene con le View di sistema e
un piccolo kit nostro (`Ui.kt`): raggi, ombre, pillole, margini sicuri, tipografia. È anche
ciò che tiene l'app coerente: ogni schermata usa gli stessi pezzi.

**Aperto:** il disegno è stato scritto senza vederlo su uno schermo — qui non c'è un
emulatore. Il giudizio vero è quello del committente sul telefono.

**Primo giudizio, e correzione: piatto, niente ombre.** Sul telefono le icone del foglio
"mostravano un quadrato sfocato dietro, sembrano appiccicate sopra": era la pillola chiara
con l'ombra che le conteneva. Ora le icone stanno direttamente sulla carta, i pulsanti
pieni non hanno ombra, e le card si staccano dalla scrivania con un filo di bordo invece
che con un'ombra. Regola per il futuro: **nessuna ombra sotto i comandi**; la gerarchia la
fanno colore e dimensione.

### D47 — Crescere e guadagnare: prima la fedeltà, poi il pubblico, poi il prezzo
**Data:** 2026-09-24 · **Stato:** proposta, **da confermare col committente**

Il committente chiede quali funzioni servono per moltiplicare il pubblico e poi
monetizzare. La risposta onesta: **le funzioni non portano utenti da sole**. Un'app di
cattura cresce se chi la prova la usa ancora dopo un mese, e se si fa trovare nello
store. Aggiungere funzioni prima di sapere se la gente torna è lavoro alla cieca.

**Le leve, in ordine di resa per costo:**

1. **iOS.** Raddoppia il mercato, e il pubblico della scrittura a mano che paga sta lì
   (iPad e Apple Pencil). Serve un Mac.
2. **Nome e scheda dello store**, in più lingue. È la leva gratuita più grande: la gente
   trova le app cercando, e decide dagli screenshot. Si fa con dati di ricerca, non a
   intuito (§10).
3. **Riconoscimento della scrittura (D2).** Rende le note cercabili e le fa arrivare in
   Notion e Keep come testo, non solo come immagine: senza, la promessa di D31 è a metà.
4. **Voce (D18)**: le mani occupate sono metà delle idee perse.
5. **"Condividi verso InkNote"**: da qualunque app un link, un testo o una foto diventano
   una nota.
6. **Backup.** Oggi `allowBackup` è spento: chi cambia telefono **perde tutte le note**.
   Per un'app che vuole fiducia è il buco più grave, e il backup automatico di Android
   (sull'account dell'utente, cifrato) costa poche righe. Va deciso contro la frase di
   D12 "le note non escono dal telefono": proposta, sì, perché va sull'account
   dell'utente come l'esportazione di D31.
7. **Un anello di crescita:** in fondo alle note mandate fuori, "scritta con InkNote".
   Chi riceve la nota vede da dove viene. Nel Pro si toglie.

**Come si guadagna (conferma D4, con i tempi di D43):**

- **Catturare resta gratis per sempre**: è il gancio, e farlo pagare ucciderebbe la
  crescita.
- **Pro, acquisto singolo:** invio automatico a Notion e a una cartella (Obsidian, Drive),
  punte e temi, niente riga "scritta con InkNote". Il riconoscimento della scrittura si
  decide coi dati: se è ciò che fa tornare la gente, deve restare gratis.
- **Abbonamento solo quando esisterà un servizio che costa**: sincronizzazione fra
  dispositivi e backup nostro.
- **Mai pubblicità** (D4).

**Il metodo: soglie, non opinioni.**

1. Correggere il disegno col committente (in corso).
2. **Canale di test chiuso del Play Store**: per un account sviluppatore personale nuovo
   Google chiede un test chiuso di almeno 12 persone per 14 giorni prima della
   pubblicazione. Si usa per misurare, non come formalità: la Play Console dà ritorno e
   disinstallazioni **senza nessun SDK di statistiche** dentro l'app (D12).
3. Intanto si costruisce: riconoscimento della scrittura, poi voce, poi "condividi verso".
4. **Soglia:** se dopo 7 giorni almeno un tester su quattro la usa ancora, si pubblica e si
   parte con iOS. Se no, si lavora sulla cattura e non si aggiungono funzioni.
5. Il Pro arriva dopo il lancio, con i dati di chi la usa davvero.

### D48 — iOS prima di Android per il lancio, e l'app iOS parla col core attraverso una facciata
**Data:** 2026-09-24 · **Stato:** attiva, decisa dal committente · **Cambia l'ordine di D43**

Il committente: **prima iOS**. Ha un Mac, Xcode e l'account sviluppatore Apple. E su
iOS non c'è l'obbligo del Play Store di un test chiuso con almeno 12 persone per 14 giorni:
TestFlight permette di far provare l'app senza un numero minimo.

**Il consiglio che resta, e va detto:** il test chiuso del Play Store costa solo di
trovare le persone, e **i 14 giorni corrono mentre si scrive iOS**. Farlo partire subito
vuol dire avere Android pronto a uscire quando iOS esce, invece che due settimane dopo.

**Come è fatta l'app iOS:**

- **`:shared`**, un modulo Kotlin nuovo che mette tutto il core in un framework,
  `InkNoteKit`, più una **facciata** pensata per Swift: `InkSheet` (tocco, movimento,
  sollevamento, contorni da riempire), `InkArchive` e `InkPreview`. Swift non deve
  conoscere `CaptureSession` né i tipi valore di Kotlin, che da Swift sono scomodi. La
  facciata sta in `commonMain` e **ha i suoi test su qualunque macchina**: la parte
  delicata della cattura iOS si verifica qui, senza Mac.
- **L'app in SwiftUI**, col foglio in una `UIView` di UIKit perché servono i campioni
  intermedi dell'Apple Pencil e il controllo del palmo, che SwiftUI non dà. Il giornale
  su disco è in Swift (`FileJournalSink`: una coda sola, `fsync`, come D35).
- **Il progetto Xcode si genera** da `iosApp/project.yml` con XcodeGen: il file di Xcode
  non si scrive a mano senza un Mac, e nel repository si rompe a ogni fusione.
- **Tutti gli ingressi di §2**: widget della home, widget della schermata di blocco,
  pulsante del Centro di Controllo (iOS 18), azione per il tasto Azione e Comandi rapidi
  (che copre anche il doppio tocco sul retro). Tutti portano a "sblocca e scrivi": sopra il
  blocco su iPhone non si può (D17).
- **D34 su iOS:** quando l'app perde il primo piano il foglio si copre di carta bianca —
  così l'istantanea del selettore delle app non mostra la nota — e poi si chiude.

**Rischio dichiarato:** la parte Swift non l'ha compilata nessuno. La parte Kotlin sì,
con 9 test.

### D49 — Il backup è acceso, sull'account dell'utente
**Data:** 2026-09-24 · **Stato:** attiva, decisa dal committente · **Precisa D12**

Su Android il backup automatico va nel Google Drive dell'utente, cifrato. Su iOS è il
backup di iCloud, che copre già il contenitore dell'app.

**Perché, rispetto a D12.** "Le note non escono dal telefono" voleva dire: non passano da
un server nostro. Il backup va sull'account dell'utente, come l'esportazione di D31, ed è
ciò che impedisce di perdere tutte le note cambiando telefono.

**Il limite di Android, e come lo si rispetta.** Il backup nel cloud ha 25 MB per app, e
**oltre non fa nessun backup, nemmeno delle note**. Quindi nel cloud vanno archivio e
giornale, e le foto restano fuori. Nel passaggio diretto da telefono a telefono, dove il
limite non c'è, va tutto. Un backup nostro, con le foto, è il servizio che giustificherà
l'abbonamento (D4).

### D50 — Il sito: una pagina sola, bellissima, dopo il nome
**Data:** 2026-09-24 · **Stato:** proposta

**Serve?** Sì, ma non per il motivo che sembra.

- **È obbligatorio.** L'App Store chiede un indirizzo per l'informativa sulla privacy e
  uno per l'assistenza. Il Play Store la privacy.
- **Il traffico vero di un'app arriva dallo store**, dalla ricerca e dalle classifiche, non
  dal sito. Il sito serve a chi arriva da fuori — un articolo, un video, un messaggio — e
  deve convertire in un tocco: "scarica".
- **È la destinazione dell'anello di crescita** (D47): la riga "scritta con InkNote" in
  fondo alle note mandate fuori porta lì.

**Come:** una pagina sola in stile Apple — una frase grande, la scrittura che si disegna
da sola mentre si scorre, la sequenza tocco → scrivi → fatto, la promessa sulla privacy,
i due pulsanti degli store. Più le pagine di privacy e assistenza. Statico, senza
framework, ospitato gratis (GitHub Pages o Cloudflare Pages). **Nessun tracciamento**:
sarebbe incoerente con D12.

**Dopo il nome commerciale**, perché il dominio e il titolo della pagina sono il nome. Le
pagine di privacy e assistenza servono prima, per TestFlight esterno: si possono
pubblicare subito con l'indirizzo provvisorio.

### D51 — Cosa aggiungere, e cosa no
**Data:** 2026-09-24 · **Stato:** approvata dal committente · **Il metro è §1: nessuno sforzo** ·
**i punti 1–4 e 6 sono attuati da D52**; il 5, le formule, è rimandato

Il committente chiede cosa aggiungerebbe un genio che deve fermare un'idea al volo. La
regola per rispondere è sempre la stessa: **una funzione entra se toglie sforzo, o se
lavora quando l'utente non sta scrivendo.** Tutto ciò che chiede una decisione mentre si
scrive, perde.

**Sì, in quest'ordine:**

1. **Lo smistamento a carte.** Una volta al giorno o alla settimana, le note nuove una alla
   volta, a tutto schermo: a destra "manda" (Notion, Keep, dove si è scelto una volta), a
   sinistra "tieni", in basso "butta". Cattura adesso, smista quando hai un minuto (D31)
   diventa un gesto da dieci secondi. È anche il miglior argomento per il Pro: la
   destinazione automatica.
2. **La riemersione.** Un'idea non persa ma mai più riletta è persa lo stesso. Nell'archivio,
   in cima, "una settimana fa hai scritto…": una nota vecchia alla volta, senza notifiche.
   È ciò che nessuna app di note fa, ed è la seconda metà della promessa "non perdere
   l'idea".
3. **Il foglio di notte.** Chi scrive un'idea a letto al buio viene accecato da un foglio
   bianco. Di notte il foglio si scurisce e l'inchiostro diventa chiaro; la nota salvata
   resta la stessa, perché il colore si decide al disegno (D7).
4. **Un tocco di conferma.** Una vibrazione breve a "Fatto": la nota è al sicuro, senza
   guardare.
5. **Le formule**, per studenti e ricercatori: scrivi a mano, esporta come LaTeX. Un
   pubblico piccolo che paga, ma il riconoscimento delle formule è un motore a parte:
   dopo la scrittura normale.
6. **Date riconosciute**: "domani alle 9" scritto a mano diventa, **nell'archivio**, un
   suggerimento di promemoria. Mai sul foglio.

**No, e perché:**

- **Cartelle, etichette, titoli al momento della cattura**: sono decisioni, e D21 le toglie.
  L'ordine lo mette lo smistamento, dopo.
- **Una chat con l'intelligenza artificiale, riassunti nel cloud**: le note uscirebbero dal
  telefono (D12), e trasformerebbero un'app di cattura in un'app di chiacchiere. Se un
  giorno, sul dispositivo.
- **Collaborazione, condivisione di quaderni, profili**: ci farebbero diventare un archivio
  che compete con Notion, cioè la posizione che D31 ha scartato.
- **Serie di giorni consecutivi, badge, notifiche "non hai scritto oggi"**: un'app di
  cattura si usa quando arriva l'idea, non per tenere viva una serie. Metterci ansia è il
  modo di farla disinstallare.
- **Formattazione, modelli, pagine**: sforzo, per definizione.

### D52 — Smistamento, riemersione, date, foglio di notte, conferma al tatto
**Data:** 2026-09-24 · **Stato:** attiva · **Attua D51** · core e iOS scritti; su Android
solo foglio di notte e vibrazione

Il committente ha approvato la lista di D51. Cinque punti su sei sono scritti; le formule no.

**1. Lo smistamento a carte.** Una nota è **da smistare** finché non è stata tenuta, mandata
o buttata. "Tenuta" è un campo nuovo, `Note.sortedAt`, con la migrazione 5 → 6; "mandata" è
già scritto in `exports` (D31); "buttata" è il tombstone. La coda (`notesToSort`) la decide
l'archivio con una query sola, dalla più vecchia.

- **`sortedAt` non fa avanzare `revision`**, per la stessa ragione di un invio (D31):
  smistare non modifica la nota, e altrimenti la rimetterebbe in coda d'invio.
- **In archivio si aggiorna col minimo, e un salvataggio vecchio non può toglierlo**
  (`CASE … min(…)`), come i tombstone (D26). Nel merge vince il più vecchio: è la prima
  volta che la nota è stata smistata, e la risposta non dipende dall'ordine (invariante 4).
- **Il pulsante compare solo se c'è qualcosa da smistare.** Un "0 da smistare" sarebbe un
  compito, e D51 esclude le app che danno compiti.
- Su iOS: a destra si manda (foglio di condivisione, D31), a sinistra si tiene, in basso si
  butta; gli stessi tre gesti come pulsanti, per chi non trascina e per VoiceOver. Se la
  condivisione si chiude senza destinazione, la carta torna al centro e resta in coda
  (invariante 18).

**2. La riemersione.** `Resurface.pick`: una nota vecchia al giorno, in cima all'archivio.
Prima gli anniversari — una settimana, un mese, tre mesi, un anno fa oggi — poi una nota
qualunque più vecchia di una settimana. **Stabile per tutta la giornata** e **senza nessun
campo in archivio**: è una funzione del giorno e delle note. Si toglie per oggi con la
crocetta, ricordato nelle preferenze di chi guarda, non in archivio. Nessuna notifica.

**3. Le date riconosciute.** `DateHints.find` legge il testo della nota (digitato,
riconosciuto, trascritto) in italiano e in inglese: parole relative (domani, dopodomani),
giorni della settimana, date numeriche e con il nome del mese, ore. Diventa, **nella nota
aperta dell'archivio e mai sul foglio**, un pulsante "Ricordamelo", che apre l'editor di
eventi del sistema già compilato. Su iOS 17 quell'editor non chiede nessun permesso sul
calendario: non leggiamo niente, e l'utente decide se salvare (D12).

- **Il fuso arriva da fuori** come scarto in millisecondi (invariante 5): "domani alle 9" è
  un fatto locale.
- **Un giorno della settimana è sempre il prossimo**, mai oggi: chi scrive "lunedì" di
  lunedì intende quello dopo. **Un'ora da sola** è oggi se deve ancora venire, se no domani.
  **Una data senza ora** vale le 9.
- **`\b` non basta per l'italiano**: da Java 19 è solo ASCII, e "lunedì" non veniva mai
  trovato. I confini di parola sono scritti con le lettere Unicode. L'ha trovato un test.
- Con il riconoscimento della scrittura (D2) le date arriveranno anche dall'inchiostro,
  senza cambiare questo codice.

**4. Il foglio di notte.** Col tema scuro del sistema il **foglio di scrittura** si scurisce
e l'inchiostro della casa diventa chiaro. È **solo un modo di mostrare**: i tratti salvati
hanno sempre l'inchiostro del giorno (D7), quindi la stessa nota nell'archivio è un
bigliettino chiaro come le altre (D46), e l'immagine mandata fuori è sempre su carta
chiara, perché andrà a vivere altrove. Precisa D46: "i fogli restano carta" vale per i
bigliettini, non per la superficie su cui si scrive a letto al buio.

**5. Un tocco di conferma.** Una vibrazione breve su "Fatto": la nota è al sicuro, senza
guardare. Su Android segue l'impostazione di sistema e non chiede permessi. Arriva
**all'uscita**, non al primo tratto: al primo tratto sarebbe una distrazione.

**Rimandato: le formule in LaTeX.** Servono un riconoscitore di scrittura matematica, che
né Vision né ML Kit danno. Esistono motori commerciali (MyScript) con licenze a pagamento.
Si decide dopo il riconoscimento normale (D2), e solo se gli studenti lo chiedono.

**Su Android**, per ora, solo foglio di notte e vibrazione: smistamento e riemersione sono
nel core e aspettano l'interfaccia, dopo iOS (D48).

### D53 — Il CI è Codemagic, con tre workflow
**Data:** 2026-09-24 · **Stato:** attiva, **da provare** al primo giro

`codemagic.yaml` nella radice:

| Workflow | Quando | Cosa fa |
|---|---|---|
| `core-tests` | ogni push e ogni pull request | `jvmTest` e `verifySqlDelightMigration`, su Linux |
| `ios-testflight` | a mano, o con un tag `ios-*` | XcodeGen, firma, IPA, TestFlight |
| `android-internal` | a mano, o con un tag `android-*` | AAB firmato, canale di test interno |

**Perché Codemagic.** È il CI con i Mac più semplice da mettere in piedi per un'app
Kotlin Multiplatform: ha gli strumenti di firma di Apple già pronti, e un piano gratuito
di minuti su Mac. Su GitHub Actions le stesse cose richiedono di scrivere a mano la
gestione dei certificati.

**Perché iOS e Android non partono a ogni push.** Ogni build su Mac costa minuti, e ogni
build su TestFlight arriva ai tester come un aggiornamento. Si consegna quando c'è
qualcosa da provare.

**Il numero della build** su iOS è uno più dell'ultimo su TestFlight, o il contatore di
Codemagic se è più alto: le build caricate a mano da Xcode contano anche loro. Su Android è
il contatore di Codemagic, letto da Gradle (`BUILD_NUMBER`).

**Codemagic rifiuta le variabili vuote** nel file: al primo giro `APP_STORE_APPLE_ID: ""`
ha bloccato la validazione dell'intera configurazione. La riga sta commentata finché la
scheda dell'app non esiste, e senza quel numero lo script usa il contatore di Codemagic.

**La firma.** Su iOS è dichiarativa (`ios_signing`): certificato e profili stanno nelle
impostazioni di Codemagic, che prende anche il profilo del widget perché il suo id comincia
con quello dell'app. Su Android la chiave di caricamento arriva a Gradle dalle variabili di
Codemagic, o da `keystore.properties` sulla propria macchina; **nessuna chiave nel
repository**, e senza chiave la build di rilascio si firma con quella di debug — si
installa, ma lo store la rifiuta.

**Lo schema Xcode è dichiarato in `project.yml`**: XcodeGen non lo crea da sé, e senza
`xcodebuild` sul CI non trova niente da costruire.

### D54 — Il nome: breve, dice "penna" e "veloce", e si verifica prima di innamorarsene
**Data:** 2026-09-24 · **Stato:** proposta, **la scelta è del committente**

**Il nome serve a tre cose, in quest'ordine:** farsi trovare nello store, farsi ricordare
dopo averlo sentito una volta, e diventare il dominio del sito (D50).

**I criteri.**

1. **Corto**: sotto l'icona iOS ci stanno dodici caratteri circa.
2. **Si pronuncia in un modo solo**, in italiano e in inglese: il passaparola è a voce.
3. **Dice le due cose che siamo**: scrittura a mano (D1) e velocità (§1).
4. **Non è una parola generica.** "InkNote" o "QuickNote" si confondono con decine di app,
   e un nome descrittivo non si può registrare come marchio.
5. **Le parole chiave vanno nel sottotitolo, non nel nome.** Nell'App Store il nome ha 30
   caratteri e il sottotitolo altri 30, ed entrambi contano per la ricerca: "Quicknib —
   Handwritten quick notes" lavora meglio di un nome lungo.
6. **Il dominio `.app` è libero.** È il dominio naturale per un'app, e impone HTTPS.

**La rosa**, dopo un controllo dei DNS (un dominio senza record non è per forza libero:
lo conferma solo un registrar):

| Nome | Perché sì | Perché no | `.app` |
|---|---|---|---|
| **Quicknib** | "veloce" + "pennino": le due promesse in otto lettere | "nib" è poco noto fuori dall'inglese | senza record |
| **Inkflash** | inchiostro + lampo: l'idea al volo | "flash" è molto usato | senza record |
| **Catchink** | si legge "catching": acchiappare l'idea con l'inchiostro | sentito a voce, non si sa come si scrive | senza record |
| Nibnote | corto, suona bene | dice "nota", non "veloce" | senza record |

Scartati perché i domini sono già in uso: Inkling, Jotly, Inklet, Scrawl, Inkdrop, Tapjot,
Jotink, Inkspark, Scriblet, Scribo. InkNote resta l'identificativo tecnico: generico, e
nello store ci sono già app con nomi quasi uguali.

**Proposta: Quicknib**, con Inkflash come seconda scelta.

**Come si verifica, in quest'ordine, prima di dirlo a chiunque:**

1. **App Store Connect**: creare la scheda dell'app con quel nome. Se il nome è preso, lo
   dice subito; se è libero, **resta nostro**. Serve comunque per TestFlight (D53).
2. **Play Console**, e una ricerca nei due store per nomi quasi uguali.
3. **Marchi**: una ricerca su EUIPO e USPTO nella classe 9 (software).
4. **Il dominio** `.app` da un registrar, e subito: costa una quindicina di euro l'anno.

Il nome non costa una rinomina del codice: i package restano `app.inknote` (§ iniziale).

**Secondo giro, stesso giorno: il nome deve farsi raccontare, non solo descrivere.**
Il committente chiede il nome che fa parlare e scaricare. Riletto con quel metro,
Quicknib è corretto ma **descrive**: "nib" fuori dall'inglese non lo conosce quasi
nessuno, e a voce si confonde con "quick nip". I nomi che la gente ripete hanno una
piccola storia dentro. Tutte le parole vere che la contengono (Inkling, Inky, Jotter,
Nibble, Glimmer, Appunto, Scrivo, Lampo, Segno, Al volo) hanno già il `.app` in uso;
restano i composti inventati.

| Nome | La storia | Il rischio | Domini |
|---|---|---|---|
| **Inkpop** | l'idea che *spunta* (pops up), fermata in inchiostro; si dice e si scrive in un modo solo | suona giocoso, forse giovane; era il nome di una comunità di scrittura di HarperCollins chiusa nel 2011: controllare il marchio | `.app` **e** `.com` senza record |
| **Instink** | "instinct" + "ink": scrivere d'istinto | sentito a voce lo si scrive "instinct" | `.app` senza record |
| Quicknib | veloce + pennino | descrittivo, "nib" poco noto | `.app` senza record |

**Proposta aggiornata: Inkpop**, per tre ragioni: si capisce sentito una volta, dice la
velocità senza la parola "quick", e ha libero anche il `.com`, che è raro e vale più di
quanto costa. **Il criterio che decide, e costa zero:** dire i tre nomi a dieci persone,
e il giorno dopo chiedere quale ricordano e come lo scriverebbero. Vince quello ricordato
e scritto giusto, non quello che piace di più sul momento.


### D55 — Il nome è Instink
**Data:** 2026-09-24 · **Stato:** attiva, **scelta dal committente** · **Chiude D54** ·
da verificare nello store, nei marchi e dal registrar

"Instinct" + "ink": scrivere d'istinto, senza pensarci. È la missione (§1) detta in una
parola, e ha una storia che si racconta in una frase.

**Dove si vede e dove no.** È il nome sotto l'icona, nel widget, nelle impostazioni e nei
testi: `CFBundleDisplayName` su iOS, `app_name` su Android, la riga "Da una nota scritta
con Instink". **Non** cambiano i package `app.inknote`, i bundle ID già registrati, i
moduli e i nomi nel codice: un identificativo tecnico non si vede, e cambiarlo dopo la
registrazione su Apple costerebbe un'app nuova nello store.

**Il rischio dichiarato, e come lo si riduce.** Sentito a voce, "Instink" si scrive
"instinct". Tre contromisure che non costano niente:

1. **"instinct" nel campo parole chiave** dell'App Store (100 caratteri, invisibile): chi
   cerca la parola giusta trova comunque l'app.
2. **Il nome si mostra sempre scritto**, con il segno dell'inchiostro: sul sito, negli
   screenshot, in fondo alle note mandate fuori (D47).
3. **Un dominio di riserva** che reindirizza, se il `.com` resta non disponibile
   (per esempio `getinstink.com`).

**Ancora da verificare**, nell'ordine di D54: scheda in App Store Connect, Play Console,
marchi EUIPO e USPTO in classe 9 (attenzione a "Instinct", marchio di altri in altre
categorie: conta la somiglianza nella stessa classe), dominio `instink.app` dal registrar.
Se una di queste verifiche cade, si torna alla rosa di D54 e si cambiano solo le righe
elencate sopra.


### D56 — Lo Swift si compila su Codemagic, senza firma, prima che sul Mac
**Data:** 2026-09-24 · **Stato:** attiva, chiesta dal committente · **Precisa D53**

Il committente preferisce non passare dal Mac per la prima compilazione. Si aggiunge un
quarto workflow, `ios-check`: genera il progetto, compila l'app e il widget per iPhone
**senza firma** (`CODE_SIGNING_ALLOWED=NO`), e se fallisce stampa **solo gli errori** di
Swift e di Kotlin in un blocco da copiare.

**Perché senza firma.** La firma richiede certificato, profili e chiave API: tre cose da
configurare prima di sapere se il codice compila. Compilare senza firma separa i due
problemi, e il primo si può risolvere subito.

**Quando parte:** a ogni push che tocca `iosApp/`, `shared/`, `core/` o il file stesso, su
qualunque branch. Un push che cambia solo la documentazione non consuma minuti di Mac.

**Cosa costa, e va detto.**

- **Il giro è più lento**: dieci-venti minuti per tentativo, contro uno o due in Xcode
  dopo la prima volta. Il primo è il più lungo, perché scarica il compilatore di
  Kotlin/Native (poi resta in cache).
- **Consuma i minuti di Mac gratuiti** del piano di Codemagic.
- **Chi sviluppa qui non legge i registri di Codemagic**: gli errori li copia il
  committente. Per questo il workflow li stampa già filtrati.
- **Non prova l'app sul telefono.** Compilare dice che il codice è giusto per il
  compilatore, non che funziona: per quello serve TestFlight, cioè la firma (D53).

L'Apple ID di Instink (`6815617566`) è nel file: il numero della build su TestFlight ora
parte dall'ultimo caricato.


### D57 — Il plugin Kotlin per Android si dichiara nella radice, e Xcode non vede l'app Android
**Data:** 2026-09-24 · **Stato:** attiva · **Trovata dal primo giro di D56**

Il primo `ios-check` su Codemagic si è fermato prima dello Swift, in Gradle:
*"plugin 'org.jetbrains.kotlin.android' already on the classpath with an unknown
version"*. Il Mac di Codemagic ha l'SDK Android, quindi `:androidApp` entrava nel build
(D23). Il plugin Kotlin per Android sta **nello stesso jar** di quello multipiattaforma,
che la radice carica: `:androidApp` lo chiedeva con una versione, e Gradle non poteva
confrontarla con quella di un plugin arrivato sotto un altro id.

**Due correzioni, perché sono due problemi.**

1. **La radice dichiara anche `kotlinAndroid`, `apply false`**, con la stessa versione. Ora
   Gradle sa quale versione è sul classpath. È la causa, e colpiva ogni macchina con l'SDK:
   anche i workflow Linux di Codemagic, che l'SDK ce l'hanno.
2. **Quando Gradle è chiamato da Xcode** (`XCODE_VERSION_ACTUAL` nell'ambiente),
   `:androidApp` resta fuori. Per costruire InkNoteKit non serve, e configurarla è tempo
   perso a ogni build iOS. Non è un rimedio al punto 1, è igiene: il build iOS non deve
   poter rompersi per colpa dell'app Android.

**Perché non anche il plugin Android nella radice**, come fanno i modelli ufficiali: la
radice lo scaricherebbe sempre, anche dove il dominio di Google è bloccato, e il core non
si compilerebbe più ovunque (D23). Il plugin Android resta caricato da `:androidApp`, come
nel build che ha già funzionato sul telefono del committente.

---

## 5. Struttura del repository

```
core/            Kotlin Multiplatform. Non conosce la UI e non conosce la rete.
  model/         Note, Stroke, VoiceClip, TextClip, PhotoClip, InkPoint, Pen, CanvasSize, mergeNotes,
                 SearchText e NoteSearch (ricerca), NoteExport (uscita verso altre app)
  ink/           StrokeBuilder, CatmullRom, WidthProfile, StrokeSimplifier, InkConfig
  geometry/      StrokeGeometry (la facciata per i renderer), StrokeOutliner, Outline,
                 Bounds, NoteFraming (inquadrare una nota in un riquadro, dentro l'app)
  capture/       CaptureSession, InkJournal, FrictionTrace — non vede l'archivio
  store/         NoteStore, JournalIngest, schema SQLDelight e schema versionato
shared/          Il core in un framework per iOS, InkNoteKit, più la facciata per Swift
                 (InkSheet, InkArchive, InkPreview) con i suoi test (D48)
iosApp/          L'app iOS: project.yml per XcodeGen, SwiftUI, il foglio in UIKit,
                 widget, Controllo, azione per il tasto Azione (D48)
androidApp/      L'app Android: il foglio (cattura, D20), l'archivio (D39), il widget
                 e il riquadro rapido (D33), il provider dei file (D40). View di
                 piattaforma; una sola dipendenza esterna, il driver SQLite.
tools/
  android-check/ Compilazione di controllo di :androidApp senza SDK (D44)
codemagic.yaml   Il CI: test del core a ogni push, TestFlight e Play interno su tag (D53)
design/
  mockups/       Le schermate come artboard .dc.html, più il canvas pubblicato:
                 home iOS, cattura nuda, cattura con strumenti, Android da
                 schermo bloccato, Android sopra il launcher, voce, archivio,
                 widget, paywall, direzione alternativa
```

Ancora da creare: `core/ocr` (Vision / ML Kit), `core/billing` (RevenueCat). I widget
non hanno un modulo loro: su Android stanno in `:androidApp` (D33), su iOS
nell'estensione `InkNoteWidgets` del progetto iOS.

**Regola di dipendenza da non rompere:** il core non dipende dalla UI né dalla rete.
Le dipendenze vanno in una sola direzione: `geometry → ink → model`,
`capture → ink → model`, `store → capture → model`. **Mai `capture → store`.** Quando i moduli
supereranno i cinque, la configurazione Gradle duplicata va estratta in un plugin di
convenzione in `build-logic/`.

**La facciata per i renderer è `StrokeGeometry`.** SwiftUI, Compose, WidgetKit e
i renderer dell'archivio Android devono conoscere solo quella: danno una nota, ricevono contorni da riempire.
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
11. **Fuori dall'app non si legge nessuna nota.** Né sulla schermata di blocco (D17)
    né nel widget della home (D30): il widget è un foglio bianco. Dalla home si
    aggiunge, non si rilegge. Vale anche per il foglio stesso: quando esce dallo
    schermo si chiude, e non compare fra le app recenti (D34).
12. **I tetti dell'attrito bloccano il rilascio**, non sono obiettivi: 100 ms a caldo e
    400 a freddo dal gesto al foglio pronto, 50 ms dal dito all'inchiostro. Il tempo
    della mano non entra in nessuno dei due. (D19, D32, D36)
13. **Il giornale si svuota solo dopo che i record sono in archivio, e solo dei byte
    letti.** Non esiste uno svuotamento totale: un tratto arrivato dopo la lettura resta
    dov'è. (D22, D35)
14. **`core:capture` non dipende da `core:store`.** Se un giorno serve, la risposta
    è quasi certamente spostare il chiamante, non aggiungere la dipendenza. (D22)
15. **Un tombstone si mette, non si toglie.** Nell'archivio gli aggiornamenti di
    `deleted_at` passano da `coalesce`: nessun salvataggio può far resuscitare
    qualcosa. (D26)
16. **Ogni modifica allo schema porta la sua migrazione** in un file `.sqm`, e un
    test che rilegge un archivio della versione precedente. `verifySqlDelightMigration`
    controlla che le istruzioni descrivano lo stesso schema; solo il test controlla che
    i dati sopravvivano. Le colonne nuove vanno **in fondo** al `CREATE TABLE`. (D28)
17. **`SearchText.VERSION` si alza ogni volta che la normalizzazione cambia
    risultato**, altrimenti l'archivio resta con un indice misto e alcune note
    diventano introvabili. (D27)
18. **Verso l'esterno si esporta, non si sincronizza.** Una direzione sola, nessuna
    rilettura. E ogni invio si registra, o si duplicano le note nell'archivio
    dell'utente. (D31)
19. **Non si manda niente durante la cattura.** Scegliere una destinazione è una
    decisione, e nel momento della cattura non si chiedono decisioni. (D31)
20. **Sul thread dell'interfaccia della cattura non si tocca il disco.** Né per
    scrivere il giornale né per trovarne la cartella. (D35)
21. **Nessun `ContentProvider` nel processo principale**, né nostro né di una libreria. Il
    sistema lo esegue a ogni avvio del processo, prima della cattura. Prima di aggiungere
    una dipendenza si guarda il suo manifest; un provider nostro va in un processo suo.
    (D32, D33, D40)
22. **Testo e foto sono pezzi immutabili della nota**, come i tratti: correggere è
    cancellare e aggiungere. (D38)

---

## 7. Ambiente e verifica

- **Gradle 8.14.3, JDK 21.** Il wrapper è nel repository: si usa `./gradlew`.
- **`./gradlew jvmTest` esegue tutti i test del core** e gira su qualunque macchina,
  Linux compreso. Il target `jvm()` dei moduli condivisi esiste esattamente per
  questo: la logica in cui si annidano i bug veri — geometria del tratto, merge,
  semplificazione — è verificabile senza né Xcode né emulatori.
- **I target iOS si configurano ma non si compilano fuori da macOS.** È normale, ed è
  il motivo di `kotlin.native.ignoreDisabledTargets=true` in `gradle.properties`.
- **Per l'app Android serve Android Studio.** `:androidApp` entra nel build da sé
  quando l'SDK c'è (D23): `./gradlew :androidApp:installDebug`. Senza SDK il modulo
  resta fuori e il core si compila e si testa comunque — è voluto.
- **Per l'app iOS serve un Mac con Xcode e XcodeGen** (`iosApp/README.md`). Qui si
  compila e si testa la facciata (`./gradlew :shared:jvmTest`), non il framework né Swift.
- **Il dominio di Google è bloccato** nell'ambiente in cui questo repository viene
  sviluppato: qui l'APK non si compila e la misura di D19 non si può prendere. Va
  fatta sulla macchina del committente.

- **`tools/android-check/check.sh`** compila `:androidApp` contro Android 15 anche senza
  SDK (D44). Va eseguito ogni volta che si tocca il codice Android. Maven Central limita
  le richieste: se lo script dice "download limitato", aspetta da solo.
- **`./gradlew verifySqlDelightMigration`** controlla che schema e migrazioni
  coincidano. Va eseguito quando si toccano i file `.sq`.
- **Codemagic** (`codemagic.yaml`, D53) ripete i test del core a ogni push, compila l'app
  iOS senza firma quando si tocca iOS o il core (D56), e con un tag
  `ios-*` o `android-*` consegna a TestFlight o al canale interno del Play Store. È anche
  il primo posto, fuori dal Mac del committente, dove lo Swift viene compilato.
- **[`GUIDA.md`](GUIDA.md)** dice cosa tocca al committente, in ordine: la misura di D19
  col suo protocollo, le verifiche da fare col telefono in mano, le decisioni aperte e
  le cose da non fare ancora.

Stato attuale: **tutti i test verdi** (`./gradlew jvmTest`). L'app Android **compila e si installa** (Samsung
Galaxy S8, 2026-09-24, nessuna modifica al codice). Misure in D36: tutte verdi. Widget,
riquadro sopra il blocco e privacy **provati sul telefono**. Archivio, tastiera, foto e
condivisione (D38–D40) **compilano contro Android 15** (D44) ma non sono ancora stati
costruiti con l'SDK né provati sul telefono. L'app iOS (D48, D52) è scritta e **non è mai stata
compilata**: qui non c'è Swift. La prima compilazione è sul Mac del committente o su
Codemagic (D53).

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

### Fatto

1. ~~`core:store`~~ — note, tratti, percorso della PNG per il widget, schema
   versionato e migrazioni verificate.
2. ~~`core:capture`~~ — sessione di scrittura, giornale resistente alle scritture
   interrotte, misuratore dell'attrito, e `JournalIngest` che chiude il cerchio.
3. ~~Prova di velocità, scritta~~ — `androidApp` con `CaptureActivity`,
   `InkCanvasView`, `AndroidInkJournalSink` e il misuratore a schermo.
4. ~~Note vocali nel modello~~ — `VoiceClip`, migrazione 1 → 2, ricerca che copre le
   trascrizioni, e un test che rilegge un archivio della versione 1.
5. ~~Widget e riquadro rapido su Android~~ — scritti, non compilati (D33). Dopo D30 non
   c'era motivo di aspettare la misura: non toccano il percorso di cattura.
6. ~~Revisione del sistema~~ — due perdite di dati nel giornale, una falla di privacy
   sopra il blocco, il disco sul thread dell'interfaccia, un errore del misuratore
   (D34, D35).

### Bloccato sulla misura

7. ~~Prendere il numero di D19~~ — preso sul Samsung S8: tutti verdi, a freddo al limite
   (D36). La compilazione anticipata vale un terzo del freddo. **Resta da misurare la
   build di rilascio** con R8 (D37). Istruzioni in [`GUIDA.md`](GUIDA.md).

### Si può fare adesso, senza telefono (core puro, verificabile qui)

8. ~~Ricerca normalizzata~~ — fatto: accenti e maiuscole ignorati, parole in qualsiasi
   ordine, risultati per pertinenza, indice versionato con reindicizzazione
   (migrazione 2 → 3).
9. ~~Inquadratura dei widget~~ — scritta e poi cancellata: D30 ha tolto le note dal
   widget, e di `WidgetFraming` resta `NoteFraming` per l'archivio.
10. ~~Uscita verso altre app~~ — fatto nella parte core: `NoteExport` prepara testo,
   Markdown e nome del file; `Note.exports` registra cosa è già andato e dove, con
   migrazione 3 → 4 e la coda `notesToSend` (D31).
11. **`core:billing`** — l'unico punto che risponde a "è Pro?" (D4), con le regole del
   livello gratuito. Ora ha una ragione d'essere più solida: il Pro poggia
   sull'esportazione automatica, non più sui widget multipli.

### Fatto dopo la misura

12. ~~Profilo di riferimento per l'avvio a freddo~~ — scritto a mano, con R8 (D37). Si
    vede all'opera solo installando dal Play Store.
13. ~~Archivio su Android~~ — elenco, ricerca, nota aperta, eliminazione, assorbimento
    del giornale all'apertura (D39). **Scritto, non ancora provato sul telefono.**
14. ~~Foglio di condivisione~~ — testo più immagine dell'inchiostro più foto, invio
    registrato solo a destinazione scelta (D31, D39).
15. ~~Tastiera e fotocamera sul foglio~~ — pezzi immutabili della nota, nel giornale da
    subito, fotocamera sicura a telefono bloccato (D38).
16. ~~Corsivo col dito, primo passo~~ — foglio senza righe, tratto da pennarello (D42).
17. ~~Fotocamera dentro il foglio e disegno nuovo~~ — dopo il primo giro sul telefono
    (D45, D46). **Da provare.**

### Prossimi, in ordine (D48, D51)

18. **iOS, prima tappa** — foglio, giornale, archivio, widget, Controllo, tasto Azione:
    scritta (D48), **da compilare e provare sul Mac del committente**.
19. ~~iOS, seconda tappa~~ — tastiera e foto sul foglio, nota aperta, "Manda a…",
    smistamento, riemersione, promemoria dalle date, foglio di notte, vibrazione (D52).
    Scritta, **da compilare** insieme alla prima.
20. **Test chiuso del Play Store in parallelo** — i 14 giorni corrono mentre si fa iOS (D48).
21. **`core:ocr`** — riconoscimento della scrittura (D2): Vision su iOS, ML Kit su
    Android, che registra un `ContentProvider` da togliere (invariante 21).
22. ~~Smistamento a carte e riemersione~~ — core e iOS fatti (D52). **Su Android manca
    l'interfaccia.**
22bis. **Codemagic al primo giro** (D53): i tre workflow, con le chiavi del committente.
23. **`core:voice`** (D18), **"Condividi verso InkNote"**.
24. **Il sito** (D50): privacy e assistenza subito, la pagina vera dopo il nome.
25. **`core:billing`** — il Pro (D4), dopo il lancio (D43, D47).

### Prima di pubblicare

26. **Schede degli store** per Instink (D55), screenshot, testi, informativa sulla
    privacy, etichette dell'esportazione tradotte (D41), firma di rilascio vera.

## 10. Questioni ancora aperte

- **Nome commerciale:** scelto, **Instink** (D55). Restano le verifiche: store, marchi,
  dominio.
- **Quanti widget nel livello gratuito.** Uno è la proposta; va verificato che non
  renda il livello gratuito inutile e quindi l'app non recensita.
- **Prezzo effettivo del Pro**, per mercato.
- **Gesto della gomma** con dito e con pennino, che sono casi diversi.
- **Scrivere in corsivo col dito è difficile** (primo telefono vero). Da capire se è
  latenza, spessore, righe troppo fitte o la natura del dito. Se è la dimensione, la
  risposta classica è una fascia di scrittura ingrandita (si scrive grande, la riga si
  rimpicciolisce): cambia il foglio, quindi va decisa col committente, non introdotta.
- **Il recupero dal cestino.** Azzerare `deletedAt` non funziona: l'archivio non lo
  permette più (D26) e al primo sync la cancellazione vincerebbe comunque. Probabile
  soluzione: copiare la nota sotto un id nuovo, accettando di perdere lo storico.
- **Se il giornale debba coprire anche l'audio** (D25 lo lascia fuori per ora).
- **Il piano di lancio (D43) e la strategia di crescita (D47)**: da confermare.
- **Le formule in LaTeX** (D51, D52): serve un motore di riconoscimento matematico a
  pagamento. Dopo D2, e solo se richiesto.
- **Smistamento e riemersione su Android**: il core c'è, l'interfaccia no (D52).
- ~~Il backup è spento~~ — acceso, sull'account dell'utente (D49).
- **La fascia di scrittura ingrandita** per il corsivo col dito (D42): dopo aver provato
  foglio senza righe e tratto più spesso.
- **Il foglio del widget: nudo o con un segno tenue?** Un rettangolo bianco vuoto può
  sembrare rotto. Proposta: un segno a basso contrasto (D30, artboard `DueVarianti`).
  Il widget Android oggi porta il segno; il foglio nudo è una vista da togliere (D33).
- **Il Pro va rivisto dopo D30 e D31.** "Widget multipli" non vale più niente su widget
  bianchi. La proposta è che il Pro poggi su esportazione automatica, ricerca OCR, punte
  e temi, col foglio di condivisione sempre gratuito.
- **Verificare che Keep non abbia davvero un'API di consumo** prima di scriverlo in una
  scheda dello store (D31).
- **Se l'invio possa diventare automatico.** Una coda visibile con un "manda tutte" a un
  tocco è il compromesso proposto; un invio silenzioso a ogni nota ci trasformerebbe in
  un motore di sincronizzazione travestito, contro l'invariante 18.
- **Come si entra nella cattura vocale su Android** a telefono bloccato, dato che
  lì la scrittura sopra il blocco esiste già (D17) e la voce servirebbe soprattutto
  a mani occupate.
