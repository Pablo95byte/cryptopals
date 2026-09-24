plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    // Lo stesso jar del plugin multipiattaforma, sotto un altro id. Va dichiarato qui con
    // la sua versione: altrimenti :androidApp lo trova già sul classpath "con versione
    // sconosciuta" e Gradle si ferma. Succedeva su Codemagic, dove l'SDK c'è (D57).
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.sqldelight) apply false
}
