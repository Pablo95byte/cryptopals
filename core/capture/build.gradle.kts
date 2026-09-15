plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        // Nessuna dipendenza da :core:store, e non è una dimenticanza.
        //
        // Il percorso di cattura non deve poter toccare il database (decisione D20):
        // l'inchiostro va messo al sicuro in coda a un file, subito, mentre il
        // database è lento ad aprirsi e sta fuori dal percorso critico. Tenendo i
        // due moduli separati, la regola la fa rispettare il compilatore invece di
        // un commento che qualcuno prima o poi violerà.
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:ink"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
