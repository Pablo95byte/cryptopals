plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(21)

    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            // La dipendenza va in questa direzione e mai nell'altra: l'archivio sa
            // del giornale, il giornale non sa dell'archivio (decisione D20).
            api(project(":core:capture"))
            implementation(libs.sqldelight.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // Il driver JDBC serve solo ai test: fa girare l'archivio in memoria su
        // qualunque macchina, senza emulatori.
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

sqldelight {
    databases {
        create("InkNoteDatabase") {
            packageName.set("app.inknote.core.store.db")
            // Lo schema versionato finisce nel repository: è la base su cui le
            // migrazioni future vengono verificate, non un artefatto di build.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
