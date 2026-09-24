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
| Sblocco + ricerca dell'ingresso | **Cattura dalla schermata di blocco** su Android: `showWhenLocked`, foglio cieco (D17) | nel codice, non provata |
| Sblocco + ricerca dell'ingresso | **Riquadro nelle impostazioni rapide** su Android: l'ingresso a telefono bloccato, da qualunque schermata (D33) | nel codice, non provato |
| Sblocco | **Più porte d'ingresso su iOS**: widget di blocco, Controllo, tasto Azione, tocco sul retro (§2) | da scrivere |
| Ricerca dell'ingresso | **Il widget è un foglio bianco che si tocca tutto** (D30): nessun bersaglio da centrare, in nessun formato | Android nel codice (D33), iOS da scrivere |
| Ricerca dell'ingresso | **Ogni apertura trova un foglio bianco**: la nota si chiude quando il foglio esce dallo schermo (D34) | nel codice |
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
| Mani occupate | **Cattura a voce** (D18): l'unica strada senza sblocco su iPhone | modello fatto, cattura da scrivere |
| Tutti | **I tetti: 100 ms a caldo, 400 a freddo** (D19, D32). Non sono funzionalità: impediscono alle altre di degradarsi | misuratore nel codice, misura da fare |
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
**Data:** 2026-09-13 · **Stato:** attiva · **Precisa D19**

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

**Da provare sul telefono:** che l'Activity lanciata dal riquadro compaia davvero sopra
il blocco senza chiederne lo sblocco. È il comportamento documentato per le Activity con
`showWhenLocked`, ma nessuno l'ha visto su questo codice.

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

---

## 5. Struttura del repository

```
core/            Kotlin Multiplatform. Non conosce la UI e non conosce la rete.
  model/         Note, Stroke, VoiceClip, InkPoint, Pen, CanvasSize, mergeNotes,
                 SearchText e NoteSearch (ricerca), NoteExport (uscita verso altre app)
  ink/           StrokeBuilder, CatmullRom, WidthProfile, StrokeSimplifier, InkConfig
  geometry/      StrokeGeometry (la facciata per i renderer), StrokeOutliner, Outline,
                 Bounds, NoteFraming (inquadrare una nota in un riquadro, dentro l'app)
  capture/       CaptureSession, InkJournal, FrictionTrace — non vede l'archivio
  store/         NoteStore, JournalIngest, schema SQLDelight e schema versionato
androidApp/      La prova di velocità di D19, installabile. Zero dipendenze
                 esterne: Activity di piattaforma, una View di disegno, il widget
                 della home e il riquadro delle impostazioni rapide (D33).
design/
  mockups/       Le schermate come artboard .dc.html, più il canvas pubblicato:
                 home iOS, cattura nuda, cattura con strumenti, Android da
                 schermo bloccato, Android sopra il launcher, voce, archivio,
                 widget, paywall, direzione alternativa
```

Ancora da creare: `core/ocr` (Vision / ML Kit), `core/billing` (RevenueCat),
`iosApp`, `iosWidget`. Il widget Android non avrà un modulo suo (D33).

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
    400 a freddo dal gesto all'inchiostro accettato. (D19, D32)
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
21. **Nessuna libreria che registri un `ContentProvider`** in `:androidApp`. Il sistema
    lo esegue a ogni avvio del processo, prima della cattura. Prima di aggiungere una
    dipendenza si guarda il suo manifest. (D32, D33)

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
- Per l'app iOS serve un Mac con Xcode.
- **Il dominio di Google è bloccato** nell'ambiente in cui questo repository viene
  sviluppato: qui l'APK non si compila e la misura di D19 non si può prendere. Va
  fatta sulla macchina del committente.

- **`./gradlew verifySqlDelightMigration`** controlla che schema e migrazioni
  coincidano. Va eseguito quando si toccano i file `.sq`.
- **[`GUIDA.md`](GUIDA.md)** dice cosa tocca al committente, in ordine: la misura di D19
  col suo protocollo, le verifiche da fare col telefono in mano, le decisioni aperte e
  le cose da non fare ancora.

Stato attuale: **233 test, tutti verdi.** L'app Android **compila e si installa** (Samsung
Galaxy S8, 2026-09-24, nessuna modifica al codice). Prima lettura:
`am start -W` → `TotalTime: 86` ms, senza sapere se l'avvio era a freddo o a caldo. Le
misure ripetute e le verifiche col telefono in mano sono ancora da fare.

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

7. **Prendere il numero di D19** su un telefono vero. Istruzioni in
   [`GUIDA.md`](GUIDA.md). **Tocca al committente**, e va prima
   di scrivere altro codice di piattaforma: se il pavimento è molto oltre i 400 ms la
   conseguenza cambia l'architettura di entrambe le app, e scriverne una seconda prima
   di saperlo è lavoro a rischio.

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

### Dopo la misura

12. **Profilo di riferimento per l'avvio a freddo** (baseline profile): la leva più
    grossa sul numero a freddo, gratuita e senza cambiare logica. Va generata su un
    dispositivo, quindi subito dopo la misura (D32).
13. **Il foglio di condivisione** su entrambe le piattaforme: è il 90% di D31, e su
    Android è anche l'unica strada per Keep.
14. **Collegare l'archivio su Android** — driver SQLite di Android e `JournalIngest`
   all'avvio, così il giornale si svuota e le note vivono nel database.
15. **`iosApp` + `iosWidget`** — SwiftUI e WidgetKit sulla stessa facciata, con widget
    di blocco, Controllo e tasto Azione. **Serve un Mac con Xcode.**
16. **`core:ocr`** — Vision e ML Kit dietro un'unica interfaccia.
17. **`core:voice`** — registrazione e trascrizione sul dispositivo (D18). Va deciso
    allora se il giornale debba coprire anche l'audio.

### Prima di pubblicare

18. **Nome commerciale e schede degli store**, screenshot, testi, informativa sulla
    privacy. Contano più del codice per la scoperta, e il paywall va collegato.

## 10. Questioni ancora aperte

- **Nome commerciale e posizionamento nello store.** Contano più del codice per la
  scoperta: vanno decisi guardando le ricerche reali, non a intuito.
- **Quanti widget nel livello gratuito.** Uno è la proposta; va verificato che non
  renda il livello gratuito inutile e quindi l'app non recensita.
- **Prezzo effettivo del Pro**, per mercato.
- **Gesto della gomma** con dito e con pennino, che sono casi diversi.
- **Il recupero dal cestino.** Azzerare `deletedAt` non funziona: l'archivio non lo
  permette più (D26) e al primo sync la cancellazione vincerebbe comunque. Probabile
  soluzione: copiare la nota sotto un id nuovo, accettando di perdere lo storico.
- **Se il giornale debba coprire anche l'audio** (D25 lo lascia fuori per ora).
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
