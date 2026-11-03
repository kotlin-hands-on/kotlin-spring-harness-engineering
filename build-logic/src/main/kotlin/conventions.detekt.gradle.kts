import dev.detekt.gradle.Detekt

plugins {
    id("dev.detekt")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

detekt {
    toolVersion = libs.findVersion("detekt").get().requiredVersion
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    baseline = file("detekt-baseline.xml")
    // Every Kotlin source set (KMP included) plus the module's build script (ktlint formatting).
    source.setFrom(fileTree("src") { include("*/kotlin/**") }, files("build.gradle.kts"))
}

dependencies {
    add("detektPlugins", libs.findLibrary("detekt-rules-ktlint-wrapper").get())
}

// Registered after evaluation so it runs after io.spring.dependency-management's own eachDependency
// action (which would otherwise re-align Kotlin to the Boot-managed version).
afterEvaluate {
    configurations.named("detekt") {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion("2.4.10")
            }
        }
    }
}

tasks.withType<Detekt>().configureEach {
    reports {
        sarif.required.set(true)
        html.required.set(true)
    }
}
