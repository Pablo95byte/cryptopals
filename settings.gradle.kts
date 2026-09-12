import java.io.File

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // Il plugin Android sta qui. Elencarlo non scarica niente finché :androidApp
        // non entra nel build.
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "inknote"

// Core condiviso (Kotlin Multiplatform). Nessun modulo conosce la UI.
include(":core:model")
include(":core:ink")
include(":core:geometry")
include(":core:capture")
include(":core:store")

/**
 * `:androidApp` entra nel build solo dove c'è l'SDK Android.
 *
 * Non è una comodità: il plugin Android non si può nemmeno mettere sul classpath
 * senza l'SDK, e senza questa condizione il core non si compilerebbe più su una
 * macchina di sviluppo senza Android Studio, né in CI. Con l'SDK presente — Android
 * Studio scrive `local.properties` da sé al primo avvio — il modulo compare senza
 * che nessuno debba modificare questo file.
 */
val androidSdkDir: File? = sequenceOf(
    System.getenv("ANDROID_HOME"),
    System.getenv("ANDROID_SDK_ROOT"),
    File(rootDir, "local.properties")
        .takeIf { it.isFile }
        ?.readLines()
        ?.firstOrNull { it.trimStart().startsWith("sdk.dir=") }
        ?.substringAfter('=')
        ?.trim(),
).filterNotNull().map(::File).firstOrNull { it.isDirectory }

if (androidSdkDir != null) {
    include(":androidApp")
} else {
    println(
        "InkNote: SDK Android non trovato, :androidApp escluso dal build. " +
            "Il core si compila e si testa comunque (./gradlew jvmTest).",
    )
}
