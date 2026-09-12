pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "inknote"

// Core condiviso (Kotlin Multiplatform). Nessun modulo conosce la UI.
include(":core:model")
include(":core:ink")
include(":core:geometry")
