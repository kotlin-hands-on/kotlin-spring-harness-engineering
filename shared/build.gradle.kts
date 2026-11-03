import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("conventions.kotlin-multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

kotlin {
    jvm()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(project.dependencies.platform(libs.junit.bom))
            implementation("org.junit.jupiter:junit-jupiter")
            runtimeOnly("org.junit.platform:junit-platform-launcher")
        }
    }
}
