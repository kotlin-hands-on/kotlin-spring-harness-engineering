plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.power.assert.plugin)
    implementation(libs.detekt.gradle.plugin)
}
