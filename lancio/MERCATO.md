# Instink — il mercato, e come diventa remunerativa

> Ricerca del 24 settembre 2026. La decisione che ne esce è in [`CLAUDE.md`](../CLAUDE.md),
> D68; i testi degli store in [`STORE.md`](STORE.md). I numeri vengono dai rapporti
> pubblici citati in fondo: sono mediane di migliaia di app, non promesse.

---

## 1. Cinque numeri che cambiano le decisioni

| Numero | Cosa vuol dire per noi |
|---|---|
| **L'app ad abbonamento mediana incassa 492 $ al mese**; il 59% non arriva mai a 1.000 $ in tutto, solo il 7% supera i 100.000 $ | Il mercato è concentrato in cima. Una buona app nella media non basta: servono ricerca nello store, recensioni e fedeltà sopra la media. |
| **Paywall duro contro freemium: 10,7% contro 2,1%** di chi scarica paga entro 35 giorni, con una fedeltà al primo anno quasi uguale | Far pagare subito rende cinque volte di più per download. **Ma** il freemium resta la scelta giusta quando gli utenti gratuiti portano il passaparola. Per un'app di cattura, che compete con una cattura gratuita già installata (Note, Keep), il paywall duro vuol dire non essere provati. |
| **Le prove lunghe convertono di più: 42,5% (17–32 giorni) contro 25,5% (meno di 4)** | Per un'app di abitudine la prova si misura in settimane, non in giorni. |
| **Produttività: il 77% degli abbonamenti è mensile, e il primo rinnovo annuale è il più basso di tutte le categorie (23%)** | Chi fa note non ama impegnarsi per un anno. Serve anche un'opzione **a vita**, e forse una mensile. |
| **iOS fa circa il 70% della spesa mondiale nelle app**, Android il 30%, con molti più utenti | I soldi stanno su iPhone e iPad. Android serve per il pubblico e il passaparola, non per l'incasso. |

Altri due, utili per i conti: **un cliente pagante vale in media 25 $ il primo anno in
Europa occidentale** (32 $ in Nord America), e le app **più care convertono i download in
prove quasi il doppio** di quelle economiche (8,9% contro 4,4%): un prezzo troppo basso
comunica poco valore.

## 2. I concorrenti e i loro prezzi

| App | Cosa fa | Prezzo |
|---|---|---|
| **Note di Apple** (Nota rapida) | cattura di testo dal Centro di Controllo | gratis, già installata |
| **Google Keep** | note e liste, widget | gratis, già installata |
| **Drafts** | "si apre su un foglio bianco", solo testo | 1,99 $/mese, **19,99 $/anno** |
| **Bear** | archivio in Markdown | 2,99 $/mese, **29,99 $/anno** |
| **Goodnotes** | quaderni a mano, iPad | **11,99 $/anno** o **29,99 $ una tantum** |
| **Notability** | quaderni a mano con audio, iPad | **14,99 $/anno** (11,99 il primo anno) |
| **Nebo** | scrittura a mano convertita in testo | 2,99 $/mese |
| **Tot** | sette appunti di testo | **19,99 $ una tantum** |
| **Just Press Record** | registratore con trascrizione | **6,99 $ una tantum** |

**Cosa si legge in questa tabella.**

1. **Il concorrente vero è gratis ed è già sul telefono.** Nessuno paga per catturare
   un'idea se Note lo fa gratis. La cattura di Instink deve restare gratuita, per sempre:
   è la condizione per essere provata (§1 di CLAUDE.md, D67).
2. **Chi paga per la scrittura a mano sta su iPad**, con l'Apple Pencil (Goodnotes,
   Notability): 12–30 $ l'anno, o 30 $ una volta. Instink nasce sul telefono, ma su iPad
   funziona già. È lì che c'è la gente abituata a pagare per la propria calligrafia.
3. **La fascia di prezzo è chiara:** 12–30 $ l'anno, 20–30 $ una volta. 14,99 € l'anno
   e 34,99 € a vita (D67) stanno dentro.
4. **Nessuno fa quello che facciamo noi:** scrivere a mano dalla home in meno di mezzo
   secondo e poi mandare la nota dove si tengono le note. È la ragione per essere
   scaricati. Non è ancora una ragione per pagare: quella deve arrivare dopo la cattura.

## 3. La risposta: gratis adesso, Pro quando porta cose nuove

**Gratis al lancio, e il più presto possibile.** Adesso contano solo tre cose: farsi
trovare, farsi usare ogni giorno, farsi recensire. Un paywall oggi ottimizzerebbe un
incasso di poche decine di euro e rallenterebbe tutte e tre.

**La regola che rende possibile far pagare dopo senza perdere la fiducia: "ciò che hai,
resta tuo".** Il danno di chi mette un pagamento dopo viene sempre dallo stesso errore:
togliere a chi c'era una cosa che aveva gratis. Quindi:

- **Pro contiene solo cose nuove**, che al lancio gratuito non esistono: invio automatico a
  Notion e a una cartella (Obsidian, iCloud Drive), iPad e Mac sincronizzati, punte e
  colori, e più avanti il backup nostro.
- **I limiti del livello gratuito valgono solo per chi arriva dopo.** La ricerca
  gratuita sugli ultimi 30 giorni (D67) vale per i nuovi utenti; chi ha installato prima
  che Pro esistesse tiene la ricerca illimitata per sempre. Si fa senza server: StoreKit
  dice sul telefono **quando l'app è stata installata la prima volta**
  (`AppTransaction.originalPurchaseDate`).
- **Chi c'era dal principio è un "fondatore"**: sconto sul Pro a vita, e un grazie nella
  schermata di Pro. Costa poco — sono poche centinaia di persone — e sono quelle che ne
  parlano.

**Quando si accende Pro: quando tre cose sono vere insieme**, non a una data.

1. **Fedeltà:** almeno un utente su quattro scrive ancora una nota dopo 7 giorni (la
   soglia di D47), e uno su cinque dopo 30. La vede App Store Connect → Analisi.
2. **Reputazione:** voto medio da 4,5 in su, con almeno 50 valutazioni. Le stelle
   decidono il posto nella ricerca, e un paywall prima abbassa le stelle.
3. **Il primo pezzo di Pro è pronto**: il più richiesto dai tester, probabilmente l'invio
   automatico a Notion o la sincronizzazione con l'iPad.

Realisticamente: **due o tre mesi dopo il lancio**.

**Il prezzo, quando arriva** (aggiorna D67 con i dati sopra):

| | prezzo | perché |
|---|---|---|
| **Annuale** | 14,99 € con **14 giorni di prova** | la prova lunga converte di più; in mezzo alla fascia dei concorrenti |
| **A vita** | 34,99 € (fondatori: 24,99 €) | produttività = poca voglia di rinnovare: meglio incassare una volta |
| **Mensile** | 1,99 €, piccolo, sotto le altre due | il 77% della produttività è mensile: toglierlo lascia soldi sul tavolo |

## 4. Quanto può rendere, detto senza illusioni

Un pagante vale circa 25 € il primo anno in Europa. Con il **3% di chi scarica che paga**
(fra il 2% del freemium medio e il meglio della categoria):

| download l'anno | paganti | primo anno |
|---|---|---|
| 10.000 | 300 | circa 7.500 € |
| 40.000 | 1.200 | circa 30.000 € |
| 70.000 | 2.100 | circa 52.000 € |

Gli abbonati che rinnovano si sommano agli anni successivi. **Da 10.000 a 70.000 download
non si passa con le funzioni, ma con quattro leve**, in quest'ordine di resa:

1. **La ricerca nello store**: nome con le parole chiave, sottotitolo, screenshot che
   raccontano la sequenza (fatto, `STORE.md`), e la **richiesta di recensione** al
   momento giusto (fatto, D68).
2. **Il passaparola**: la riga "Scritta con Instink · instink.app" in fondo a ogni nota
   mandata fuori (fatto, D68). Ogni nota condivisa è una pubblicità a chi la riceve.
3. **La vetrina di Apple**: la candidatura in App Store Connect (`STORIA.md` §7). Una
   sola vetrina vale mesi di download.
4. **iPad e Apple Pencil**: il pubblico che paga già per scrivere a mano.

## 5. Android: il pubblico, non l'incasso

Android fa il 30% della spesa, ma porta utenti, recensioni e passaparola, e Google chiede
14 giorni di test chiuso con almeno 12 persone prima di pubblicare. **Il test va
cominciato adesso**, perché quei 14 giorni non si accorciano: così Android è pronto quando
lo è iOS. La guida è in `GUIDA.md`, §0nonies.

---

## Fonti

- RevenueCat, *State of Subscription Apps 2026* e *2025*: revenuecat.com/state-of-subscription-apps,
  revenuecat.com/state-of-subscription-apps-2025,
  revenuecat.com/blog/growth/subscription-app-trends-benchmarks-2026,
  revenuecat.com/blog/growth/average-subscription-renewal-rates-by-app-category
- Airbridge, *Subscription App Pricing by Category: 2026 Benchmarks*
- Prezzi: forums.getdrafts.com (Drafts Pro 19,99 $/anno), bear.app (Bear Pro),
  paperlike.com (Goodnotes e Notability), pen.tips (Nebo), tot.rocks, openplanetsoftware.com
  (Just Press Record)
- Spesa iOS/Android: adapty.io/blog/iphone-vs-android-users e altre statistiche 2025–2026
- Apple, *App Store Small Business Program*: developer.apple.com/app-store/small-business-program
- Google, *App testing requirements for new personal developer accounts*:
  support.google.com/googleplay/android-developer/answer/14151465
