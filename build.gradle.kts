import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
	kotlin("jvm") version "2.4.21"
	kotlin("plugin.spring") version "2.4.21"
	kotlin("plugin.power-assert") version "2.4.21"
	id("dev.detekt") version "2.0.0-alpha.6"
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
extra["junit-jupiter.version"] = "6.1.3"

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.junit.jupiter:junit-jupiter")
	testImplementation(kotlin("test"))
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.6")
}

detekt {
	toolVersion = "2.0.0-alpha.6"
	config.setFrom(rootProject.file("config/detekt/detekt.yml"))
	buildUponDefaultConfig = true
	baseline = file("detekt-baseline.xml")
}

configurations.named("detekt") {
	resolutionStrategy.eachDependency {
		if (requested.group == "org.jetbrains.kotlin") {
			useVersion("2.4.10")
		}
	}
}

kotlin {
	jvmToolchain(25)
	compilerOptions {
		jvmTarget = JvmTarget.JVM_25
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

tasks.withType<Test> {
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

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
	reports {
		sarif.required.set(true)
		html.required.set(true)
	}
}
