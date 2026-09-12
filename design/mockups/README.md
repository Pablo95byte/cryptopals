# Mockup delle schermate

Le sorgenti sono i file `.dc.html`: uno per artboard, più `canvas.json` che ne
definisce la disposizione e le annotazioni.

Il canvas pubblicato:
<https://claude.ai/code/artifact/226f7658-faf9-43dc-bd68-a9942e68261a>

Il file `inknote-schermate.html` non è in git: è il canvas assemblato, cioè un
prodotto di build che contiene l'editor per intero. Si rigenera dai sorgenti.

## Cosa mostrano

| Artboard | Schermata |
|---|---|
| `Main` | Home iOS con il widget grande |
| `Cattura` | Il foglio: scrittura, punte, conferma |
| `Archivio` | Elenco e ricerca nel testo riconosciuto |
| `HomeAndroid` | La finestra trasparente sopra il launcher |
| `Widget` | I tre formati di widget |
| `Paywall` | Pagamento unico, come da decisione D4 |
| `DirezioneB` | Schizzo di direzione alternativa, ancora da valutare |

## Vincoli rispettati

- Nessuna barra di stato né tastiera disegnate: sul telefono quelle vere si
  sovrappongono al layout, e disegnarle fa sembrare tutto raddoppiato.
- Nessun bersaglio di tocco sotto i 44 punti.
- La scrittura nei mockup è resa con un font corsivo (Caveat): nell'app vera è
  inchiostro disegnato dai tratti, non testo.
