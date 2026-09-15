plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(21)

    // jvm() serve a far girare i test del core su qualsiasi macchina (anche in CI
    // Linux) ed è anche l'artefatto che consuma l'app Android: da qui il bytecode 11,
    // che D8 digerisce senza discutere.
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    // I target iOS si compilano solo su macOS con Xcode; qui si configurano e basta.
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
