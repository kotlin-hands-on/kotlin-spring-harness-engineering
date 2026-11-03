import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("conventions.kotlin-multiplatform")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("web")
        browser()
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
    }
}

// The browser bundle is consumed by :backend, which packages it into the boot jar.
val webDistribution = configurations.create("webDistribution") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(webDistribution.name, tasks.named("wasmJsBrowserDistribution").map { it.outputs.files.singleFile })
}
