import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("conventions.kotlin-base")
}

kotlin {
    // Platform-neutral flags apply to every compilation (jvm, wasmJs, common metadata).
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Wextra",
            "-Xcollection-literals",
            "-Xconsistent-data-class-copy-visibility",
            "-Xexplicit-context-arguments",
            "-Xname-based-destructuring=complete",
            "-Xreturn-value-checker=check",
            "-Xintrinsic-const-evaluation",
        )
    }
    targets.withType<KotlinJvmTarget>().configureEach {
        compilerOptions.freeCompilerArgs.add("-Xjsr305=strict")
    }
}
