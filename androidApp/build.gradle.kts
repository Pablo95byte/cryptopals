import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "app.inknote.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.inknote.android"
        // 27 e non 26: `showWhenLocked` si dichiara nel manifest da API 27, e la
        // cattura sopra il blocco è il motivo per cui questa app esiste (D17).
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-prova-di-velocita"
    }

    buildTypes {
        // Firmata con la chiave di debug: serve a installarla su un telefono, non a
        // pubblicarla. La firma vera arriverà col primo rilascio.
        //
        // R8 acceso non per il peso dell'APK ma per l'avvio a freddo (D37): toglie dalla
        // libreria standard di Kotlin tutto quello che non usiamo, e meno classi da
        // caricare e verificare sono millisecondi in meno prima del foglio.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

/**
 * Una dipendenza esterna sola, e fuori dal percorso di cattura.
 *
 * Fino a D39 non ce n'era nessuna: questa app era la prova di velocità di D19, e ogni
 * libreria sul percorso di avvio è tempo che l'utente aspetta prima di poter scrivere.
 * La regola che resta, ed è quella che conta: **niente sul percorso di cattura, e
 * niente che registri un `ContentProvider`** (invariante 21). Il driver SQLite lo usa
 * solo l'archivio, e la cattura non carica nemmeno una sua classe.
 *
 * I moduli del core sono Kotlin Multiplatform **senza target Android**, perché
 * aggiungerlo richiederebbe il plugin Android anche su di loro, e allora il core non
 * si compilerebbe più dove l'SDK non c'è. Chiediamo quindi esplicitamente la
 * variante `jvm`: è puro Kotlin, senza API specifiche della JVM, e il bytecode è 11.
 */
dependencies {
    for (path in listOf(":core:model", ":core:ink", ":core:geometry", ":core:capture", ":core:store")) {
        val dependency = project(path)
        dependency.attributes {
            attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
        }
        implementation(dependency)
    }
    implementation(libs.sqldelight.android.driver)
}
