# iosApp — InkNote per iPhone e iPad

La prima tappa iOS (D48): il foglio, il giornale, l'archivio, il widget per la home e
per la schermata di blocco, il pulsante del Centro di Controllo (iOS 18) e l'azione per
il tasto Azione e Comandi rapidi. La calligrafia arriva dal core Kotlin, identica ad
Android (D6).

**Scritta senza un Mac: non l'ha ancora compilata nessuno.** Il primo build è il tuo, e
qualche errore è da mettere in conto: mandamelo così com'è.

## Una volta sola

```sh
brew install xcodegen           # genera il progetto Xcode da project.yml
```

Serve anche il JDK 21 per Gradle, che compila il core: `brew install openjdk@21`, oppure
quello di Android Studio (`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`).

## Costruire

```sh
cd iosApp
xcodegen generate               # crea InkNote.xcodeproj
open InkNote.xcodeproj
```

In Xcode: seleziona il target **InkNote** → *Signing & Capabilities* → la tua squadra;
lo stesso per **InkNoteWidgets**. Poi scegli il tuo iPhone come destinazione ed esegui.

Il primo build è lento: la fase "Compila il core Kotlin" costruisce il framework
`InkNoteKit` con Gradle. I successivi sono rapidi.

## Cosa provare

1. **Il foglio** — "Write" nell'archivio. Il tratto col dito e con l'Apple Pencil, se ce
   l'hai. In basso compare il misuratore (solo nelle build di debug): `pronto freddo: …`
   e `tocco→inchiostro …`.
2. **Il widget** — tieni premuto sulla home → + → InkNote → "Blank sheet". Toccalo.
3. **La schermata di blocco** — personalizza la schermata di blocco → aggiungi il widget
   InkNote. Toccalo: iOS chiede Face ID, poi il foglio.
4. **Il Centro di Controllo** (iOS 18) — modifica → aggiungi un controllo → InkNote.
5. **Il tasto Azione** (iPhone 15 Pro e successivi) — Impostazioni → Tasto Azione →
   Comando rapido → InkNote → Write.
6. **La privacy** — scrivi e vai alla home senza "Fatto": nel selettore delle app deve
   comparire carta bianca, non la nota, e riaprendo l'app il foglio è chiuso.

## Cosa manca ancora

Tastiera e foto sul foglio, la nota aperta, "Manda a…", il riconoscimento della
scrittura. Sono la tappa successiva, dopo che questa gira sul tuo iPhone.
