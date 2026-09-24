import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

/**
 * InkNoteKit: il core in un framework per l'app iOS (D48).
 *
 * Esporta i moduli del core così come sono — Swift vede `Note`, `mergeNotes`,
 * `NoteExport` — più una facciata, [app.inknote.kit.InkSheet], che traduce la cattura
 * in chiamate semplici da un `UIView`: tocco, movimento, sollevamento, contorni da
 * riempire. La calligrafia resta tutta dal lato Kotlin (D6): Swift riempie poligoni.
 *
 * Framework statico: si incorpora nell'app senza doverlo firmare a parte, e il linker
 * toglie quello che Swift non usa.
 */
kotlin {
    jvmToolchain(21)

    // Il target jvm c'è per i test della facciata su qualunque macchina.
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "InkNoteKit"
            isStatic = true
            export(project(":core:model"))
            export(project(":core:ink"))
            export(project(":core:geometry"))
            export(project(":core:capture"))
            export(project(":core:store"))
            // Il driver SQLite di SQLDelight su iOS usa la libreria di sistema.
            linkerOpts("-lsqlite3")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:ink"))
            api(project(":core:geometry"))
            api(project(":core:capture"))
            api(project(":core:store"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}
