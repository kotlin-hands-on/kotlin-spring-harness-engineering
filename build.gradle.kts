plugins {
    id("conventions.detekt")
}

detekt {
    // The root project only lints the build scripts it owns.
    source.setFrom(files("build.gradle.kts", "settings.gradle.kts"))
}
