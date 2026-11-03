plugins {
    id("conventions.kotlin-jvm")
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.exposed)
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
extra["junit-jupiter.version"] = libs.versions.junit.get()

// Browser bundle of :web, packaged into the boot jar only (backend tests and processResources never build it).
val webBundle = configurations.create("webBundle") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    webBundle(project(path = ":web", configuration = "webDistribution"))
    implementation(project(":shared"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(libs.exposed.spring.boot4.starter) {
        // DSL only (design D4): the DAO API is not used.
        exclude(group = "org.jetbrains.exposed", module = "exposed-dao")
    }
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.json)
    implementation(libs.exposed.money)
    implementation(libs.moneta)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-kotlinx-serialization-json")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.archunit.junit6)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

exposed {
    migrations {
        tablesPackage.set("com.example.harnessengineeringspring.persistence")
        testContainersImageName.set("postgres:18-alpine")
        fileDirectory.set(layout.projectDirectory.dir("src/main/resources/db/migration"))
    }
}

tasks.bootJar {
    from(webBundle) {
        into("BOOT-INF/classes/static")
    }
}
