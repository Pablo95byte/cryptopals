# Mockup delle schermate

Le sorgenti sono i file `.dc.html`: uno per artboard, più `canvas.json` che ne
definisce la disposizione e le annotazioni.

Il canvas pubblicato:
<https://claude.ai/code/artifact/226f7658-faf9-43dc-bd68-a9942e68261a>

Il file `inknote-schermate.html` non è in git: è il canvas assemblato, cioè un
prodotto di build che contiene l'editor per intero. Si rigenera dai sorgenti.

Tre pagine: **In home** (come appare dove conta), **Flusso principale** (il percorso
felice) e **Stati e servizio** (gli stati veri e le schermate che il prodotto richiede).

## Cosa mostrano

| Artboard | Schermata |
|---|---|
| `Main` | Home iOS con il widget grande |
| `Cattura` | Il foglio all'apertura: solo foglio e conferma (D21) |
| `CatturaStrumenti` | Dopo il primo tratto: punte, colori, correzioni |
| `Blocco` | Android senza sbloccare, foglio cieco (D17) |
| `HomeAndroid` | La finestra trasparente sopra il launcher |
| `Voce` | Cattura a voce, per le mani occupate (D18) |
| `Archivio` | Elenco e ricerca nel testo riconosciuto |
| `Widget` | I tre formati di widget |
| `Paywall` | Pagamento unico, come da decisione D4 |
| `DirezioneB` | Schizzo di direzione alternativa, ancora da valutare |
| `Vuoto` | Primo avvio: porta alla prima nota, non alla configurazione |
| `AggiungiWidget` | Come si mette in home. Nessuno lo scopre da solo |
| `Nota` | Una nota riaperta: strumenti, esportazione, eliminazione |
| `ArchivioStati` | Nota vocale, riconoscimento in corso, tratto perso |
| `ConfiguraWidget` | Cosa mostra il widget, formato, limite del gratuito |
| `Impostazioni` | Poche voci, col riconoscimento del testo spegnibile |
| `HomeMedio` | Widget medio iOS: cattura di lato, non in alto |
| `HomePiccolo` | Widget piccolo: nessun pulsante, si tocca tutto |
| `HomeGrandeScuro` | Widget grande su sfondo scuro: è lì che la carta si vede |
| `HomeVuoto` | Il primo giorno, senza note: il widget è solo una porta |
| `HomeMisto` | Una nota vocale fra quelle scritte |
| `BloccoiOS` | Schermata di blocco iOS: il gesto più corto che Apple concede |
| `HomeAndroidMedio` | Stesso widget, launcher Android |
| `HomeAndroidGrande` | Widget grande Android, cattura nella striscia in alto |

## Vincoli rispettati

- Nessuna barra di stato né tastiera disegnate: sul telefono quelle vere si
  sovrappongono al layout, e disegnarle fa sembrare tutto raddoppiato.
- Nessun bersaglio di tocco sotto i 44 punti. Il pallino del colore resta piccolo
  perché non pesi visivamente, ma la sua area di tocco arriva a 44: sono due cose
  diverse.
- `box-sizing: border-box` su tutto: altezze fisse più padding senza di esso fanno
  scavalcare gli elementi, e nei primi mockup era successo in tre punti.
- La scrittura nei mockup è resa con un font corsivo (Caveat): nell'app vera è
  inchiostro disegnato dai tratti, non testo.
