# androidApp — prova di velocità, installabile

Questa non è ancora l'app. È la **prova di velocità** della decisione D19, in forma
installabile: serve a leggere su un telefono vero il tempo che passa dal tocco al
primo tratto disegnabile, e a verificare che l'inchiostro sopravviva alla morte del
processo.

Non è codice da buttare: è la base su cui crescerà la cattura definitiva.

## Compilare

Serve Android Studio (o l'SDK da riga di comando). All'apertura del progetto Android
Studio scrive `local.properties` con il percorso dell'SDK, e da quel momento
`:androidApp` entra nel build da sé — non c'è niente da modificare.

```sh
./gradlew :androidApp:installDebug     # installa sul telefono collegato
./gradlew :androidApp:assembleDebug    # solo l'APK, in androidApp/build/outputs/apk/
```

Senza SDK il modulo resta fuori dal build e il core si compila e si testa comunque
(`./gradlew jvmTest`): è voluto, perché il plugin Android non si può mettere sul
classpath senza SDK.

## Cosa verificare, in quest'ordine

Il protocollo completo, con cosa mandare indietro, sta in [`GUIDA.md`](../GUIDA.md).
In breve:

1. **Il numero.** In alto a sinistra compare il misuratore appena tracci il primo
   segno: `attrito freddo: 310ms (entro 400ms) · visibile 340ms · superficie 240ms ·
   1° fotogramma 280ms`. Verde entro il tetto, rosso oltre. **A freddo:** arresto
   forzato dalle impostazioni (il foglio non compare fra le recenti, D34), poi riapri e
   traccia subito. **A caldo:** OK, riapri, traccia. Il numero a caldo è ottimista: parte
   da `onCreate` (D32).
2. **Il tratto.** Lo spessore deve vivere — sottile nei movimenti rapidi, più pieno
   dove rallenti. Con un pennino attivo deve seguire la pressione.
3. **La sopravvivenza.** Due o tre tratti, niente OK, home, arresto forzato. Riapri e
   tocca il misuratore: l'elenco del giornale deve contenere i tratti.
4. **Il riquadro rapido a telefono bloccato** (D33): il foglio compare sopra il blocco
   senza chiedere lo sblocco.
5. **La privacy** (D34): schermo spento senza OK → alla riaccensione si vede il blocco,
   non la nota.

## Cosa non c'è ancora

- L'archivio SQLite. `JournalIngest` esiste ed è testato, ma collegarlo richiede il
  driver SQLite di Android: è il passo successivo. Per ora il giornale accumula, e
  l'elenco lo legge direttamente da lì.
- La barra delle punte, i colori, la voce, l'OCR.
- Il misuratore e la scorciatoia all'elenco compaiono **solo nelle build di debug**:
  all'apertura il foglio deve essere nudo (D21).

## Il rischio noto di questo build

I moduli del core sono Kotlin Multiplatform **senza target Android**, perché
aggiungerlo richiederebbe il plugin Android anche su di loro, e il core non si
compilerebbe più dove l'SDK non c'è. L'app chiede quindi esplicitamente la variante
`jvm` dei moduli, in `androidApp/build.gradle.kts`:

```kotlin
dependency.attributes {
    attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
}
```

È puro Kotlin, senza API specifiche della JVM, e il bytecode è 11: su Android gira.
**Questa riga non è stata provata su una macchina con l'SDK**, perché nell'ambiente in
cui è stata scritta il dominio di Google è bloccato. Se Gradle si lamenta della
risoluzione delle varianti, la strada alternativa è aggiungere `androidTarget()` ai
moduli del core:

```kotlin
// in core/*/build.gradle.kts, con l'SDK presente
plugins { alias(libs.plugins.androidLibrary) }
kotlin { androidTarget() }
android { namespace = "app.inknote.core.<modulo>"; compileSdk = 35; defaultConfig { minSdk = 27 } }
```

e togliere il blocco `attributes` da qui. Funziona, ma lega il build del core
all'SDK: da preferire solo se la prima strada non regge.
