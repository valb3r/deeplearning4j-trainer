import Build_gradle.Constants.dl4jMasterVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("plugin.jpa") version "1.4.32"
    id("org.springframework.boot") version "2.7.2"
    id("io.spring.dependency-management") version "1.0.12.RELEASE"
    kotlin("plugin.spring") version "1.4.32"
    kotlin("jvm") version "1.7.0"
    id("com.bmuschko.docker-remote-api") version "6.7.0"
    antlr
}

group = "com.valb3r.deeplearning4j_trainer"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://maven.jzy3d.org/releases")
}

object Constants {
    const val dl4jMasterVersion = "1.0.0-M1.1" // Do not use M2 as there is a regression https://community.konduit.ai/t/computing-gradient-without-backpropagation/1887?u=valb3r
}

dependencies {
    // Spring
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.webjars:bootstrap:5.1.3")
    implementation("org.webjars:jquery:3.6.0")
    implementation("org.webjars.npm:echarts:5.3.3")
    implementation("org.webjars.npm:mermaid:8.13.8")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.session:spring-session-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-rest")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    implementation("io.github.microutils:kotlin-logging-jvm:2.1.23")
    implementation("org.flowable:flowable-spring-boot-starter-process:6.7.2")
    implementation("org.flywaydb:flyway-core:8.5.13")
    implementation("org.springdoc:springdoc-openapi-ui:1.6.9")
    implementation("org.springdoc:springdoc-openapi-data-rest:1.6.9")

    // ANTLR
    antlr("org.antlr:antlr4:4.7.1") // use ANTLR version 4

    // Deeplearning
    implementation("org.bytedeco:javacv-platform:1.5.5")
    implementation("org.jfree:jcommon:1.0.23")
    implementation("jfree:jfreechart:1.0.13")
    implementation("org.deeplearning4j:deeplearning4j-ui:${dl4jMasterVersion}")
    implementation("org.deeplearning4j:deeplearning4j-core:${dl4jMasterVersion}")
    implementation("org.deeplearning4j:deeplearning4j-datasets:${dl4jMasterVersion}")

    // GPU CUDA
    runtimeOnly("org.nd4j:nd4j-cuda-11.2:${dl4jMasterVersion}")

    // MacOs compat (for 1.0-M1.1 - 1.0-M2.0)
    runtimeOnly("org.nd4j:nd4j-native-platform:${dl4jMasterVersion}")
    runtimeOnly("org.nd4j:nd4j-native:${dl4jMasterVersion}:macosx-x86_64")

    // Minio S3 connectivity
    implementation("com.amazonaws:aws-java-sdk-s3:1.11.538")
    implementation("com.amazonaws:aws-java-sdk-core:1.11.538")

    // Common
    compileOnly("org.projectlombok:lombok:1.18.24")
    annotationProcessor("org.projectlombok:lombok:1.18.24")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.13.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.0")

    implementation("org.jzy3d:jzy3d-native-jogl-swing:2.1.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.awaitility:awaitility:4.2.0")
    testCompileOnly("org.projectlombok:lombok:1.18.24")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.24")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.3.2")
    testImplementation("org.testcontainers:postgresql:1.17.3")
}

var commitSha: String by extra
commitSha = Runtime
    .getRuntime()
    .exec("git rev-parse --short HEAD")
    .let { process ->
        process.waitFor()
        val output: String = (process.inputStream as java.io.InputStream).use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        }
        process.destroy()
        output.trim()
    }

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

// ANTLR config, need visitor:
tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments = arguments + listOf(
        "-visitor",
        "-long-messages",
        "-lib",
        "src/main/antlr/com/valb3r/deeplearning4j_trainer",
        "-package",
        "com.valb3r.deeplearning4j_trainer"
    )
}

tasks.withType<KotlinCompile> {
    dependsOn("generateGrammarSource")
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

tasks.getByName<BootJar>("bootJar") {
    enabled = true
}

tasks.getByName<Jar>("jar") {
    enabled = true
}

tasks.register("syncJar", Copy::class) {
    dependsOn("bootJar")
    val bootJar by tasks.getting(BootJar::class)
    val buildImage by tasks.getting(com.bmuschko.gradle.docker.tasks.image.DockerBuildImage::class)

    from(bootJar.archiveFile).into(buildImage.inputDir)
}

tasks.register("createDockerfile", com.bmuschko.gradle.docker.tasks.image.Dockerfile::class) {
    dependsOn("syncJar")
    val bootJar by tasks.getting(BootJar::class)

    val cmd = "/app/deeplearning4j_trainer-${commitSha}.jar"
    from("openjdk:11.0.8-jre-slim")
    copyFile(bootJar.archiveFileName.get(), cmd)
    entryPoint("java")
    defaultCommand("-jar", cmd)
    exposePort(8080)
}

tasks.register("buildImage", com.bmuschko.gradle.docker.tasks.image.DockerBuildImage::class) {
    dependsOn("createDockerfile", "syncJar")
    images.add("valb3r/deeplearning4j_trainer-trainer:${commitSha}")
    images.add("valb3r/deeplearning4j_trainer-trainer:latest")
}

tasks.register("printCommitSha") {
    println("COMMIT:${commitSha}")
}