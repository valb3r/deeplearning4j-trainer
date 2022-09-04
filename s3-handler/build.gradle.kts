import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.0"
}

group = "com.valb3r.deeplearning4j_trainer"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    // Minio S3 connectivity
    implementation("com.amazonaws:aws-java-sdk-s3:1.11.538")
    implementation("com.amazonaws:aws-java-sdk-core:1.11.538")

    // Common
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.0")
    implementation("io.github.microutils:kotlin-logging-jvm:2.1.23")
    implementation("com.google.guava:guava:31.1-jre")



    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.3.2")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    languageVersion = "1.4"
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
    maxHeapSize = "1024m"
}