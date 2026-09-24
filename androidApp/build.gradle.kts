import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    // Senza versione: il plugin Android è già sul classpath della radice (D58), e chiederlo
    // con una versione farebbe fermare Gradle ("already on the classpath").
    id("com.android.application")
    alias(libs.plugins.kotlinAndroid)
}

/**
 * La chiave di caricamento sul Play Store (D53). Da dove arriva, in ordine:
 *
 * 1. le variabili che Codemagic imposta quando il workflow dichiara `android_signing`;
 * 2. `keystore.properties` nella radice, per firmare sulla propria macchina. Non si versiona.
 *
 * Se non c'è nessuna delle due, la build di rilascio si firma con la chiave di debug: si
 * installa sul telefono, ma lo store la rifiuta. Una chiave nel repository, invece, non
 * si toglie più da nessuna parte.
 */
val releaseKey: Map<String, String>? = run {
    val env = System.getenv()
    if (env["CM_KEYSTORE_PATH"] != null) {
        mapOf(
            "storeFile" to env.getValue("CM_KEYSTORE_PATH"),
            "storePassword" to env["CM_KEYSTORE_PASSWORD"].orEmpty(),
            "keyAlias" to env["CM_KEY_ALIAS"].orEmpty(),
            "keyPassword" to env["CM_KEY_PASSWORD"].orEmpty(),
        )
    } else {
        val file = rootProject.file("keystore.properties")
        if (!file.exists()) {
            null
        } else {
            val properties = Properties().apply { file.inputStream().use { load(it) } }
            listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                .associateWith { properties.getProperty(it).orEmpty() }
                .let { it + ("storeFile" to rootProject.file(it.getValue("storeFile")).path) }
        }
    }
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
        // Il numero della build lo dà il CI (D53): il Play Store rifiuta due caricamenti
        // con lo stesso numero, e contarli a mano è il modo di sbagliare il giorno del rilascio.
        versionCode = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1"
    }

    signingConfigs {
        releaseKey?.let { key ->
            create("release") {
                storeFile = file(key.getValue("storeFile"))
                storePassword = key.getValue("storePassword")
                keyAlias = key.getValue("keyAlias")
                keyPassword = key.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        // R8 acceso non per il peso dell'APK ma per l'avvio a freddo (D37): toglie dalla
        // libreria standard di Kotlin tutto quello che non usiamo, e meno classi da
        // caricare e verificare sono millisecondi in meno prima del foglio.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
