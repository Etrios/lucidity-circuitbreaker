import org.gradle.kotlin.dsl.test

plugins {
    jacoco
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.4.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.ganeshl"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

extra["springCloudVersion"] = "2024.0.1"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0-M1") // Use the latest stable version
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.0-M1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    // Mockito for mocking dependencies
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1") // Latest mockito-kotlin
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0") // Integration with JUnit 5

    // For testing instant/duration, helpful for time-based tests
    testImplementation("org.assertj:assertj-core:3.26.0")

    // Your application's main source sets (for CircuitBreaker classes)
    //implementation(project(":")) // Or whichever module contains your CB classe
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// JaCoCo Configuration
jacoco {
    toolVersion = "0.8.11" // Use the latest stable JaCoCo version
}

tasks.test {
    useJUnitPlatform()

    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    // Configure the report to generate HTML, XML, or CSV
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(true) // HTML report is very user-friendly
    }

    sourceSets(sourceSets.main.get()) // Ensure it points to your main source set

    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/main").get().asFile) {
            include("com/ganeshl/luciditycircuitbreaker/CB/**/*.class")
        },
        // Option 2 (if you have Java classes too):
        fileTree(layout.buildDirectory.dir("classes/java/main").get().asFile) {
            include("com/ganeshl/luciditycircuitbreaker/CB/**/*.class")
        }
    )
}
