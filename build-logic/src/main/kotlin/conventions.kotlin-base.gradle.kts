import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

plugins {
    id("conventions.detekt")
    id("org.jetbrains.kotlin.plugin.power-assert")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<KotlinBaseExtension> {
    jvmToolchain(libs.findVersion("jdk").get().requiredVersion.toInt())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        // full assertion message incl. power-assert diagram - this is what agents and the test hook see
        exceptionFormat = TestExceptionFormat.FULL
    }
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf(
        "kotlin.test.assertEquals",
        "kotlin.test.assertNotEquals",
        "kotlin.test.assertTrue",
        "kotlin.test.assertFalse",
        "kotlin.test.assertNull",
        "kotlin.test.assertNotNull",
        "kotlin.test.assertIs",
        "kotlin.test.assertContains",
        "kotlin.test.assertContentEquals",
    )
}
