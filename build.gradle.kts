/**
 * Il plugin Android sta qui, nella radice, **solo quando :androidApp entra nel build** (D58).
 *
 * Il plugin Kotlin lo carica la radice, per tutti i moduli; e il plugin Kotlin per Android ha
 * bisogno di vedere le classi del plugin Android. Se il plugin Android lo carica soltanto
 * :androidApp, finisce in un classloader figlio che il plugin Kotlin non vede, e Gradle si
 * ferma su `com/android/build/gradle/api/BaseVariant`. Messo qui sta nello stesso classloader.
 *
 * Solo quando serve, perché dove l'SDK non c'è — o dove il dominio di Google è bloccato —
 * il core deve compilarsi lo stesso (D23): lì il plugin Android non viene nemmeno cercato.
 */
buildscript {
    if ((gradle as ExtensionAware).extra.properties["inknote.androidApp"] == true) {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            // La stessa versione di `androidGradlePlugin` in gradle/libs.versions.toml.
            classpath("com.android.tools.build:gradle:8.7.3")
        }
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    // Lo stesso jar del plugin multipiattaforma, sotto un altro id. Va dichiarato qui con
    // la sua versione: altrimenti :androidApp lo trova già sul classpath "con versione
    // sconosciuta" e Gradle si ferma. Succedeva su Codemagic, dove l'SDK c'è (D57).
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.sqldelight) apply false
}
