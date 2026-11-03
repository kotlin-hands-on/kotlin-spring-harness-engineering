import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("conventions.kotlin-base")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(libs.findVersion("jdk").get().requiredVersion)
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Wextra",
            "-Xcollection-literals",
            "-Xconsistent-data-class-copy-visibility",
            "-Xexplicit-context-arguments",
            "-Xname-based-destructuring=complete",
            "-Xreturn-value-checker=check",
            "-Xintrinsic-const-evaluation",
        )
    }
}
