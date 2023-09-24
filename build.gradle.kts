plugins {
    kotlin("jvm") version "1.9.0"
}

group = "net.mymai1208"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val ktor_version = "2.3.4"
    val exposed_version = "0.43.0"

    implementation(kotlin("stdlib"))

    implementation("io.ktor:ktor-client-java:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")

    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")

    implementation("dev.gustavoavila:java-android-websocket-client:2.0.2")

    implementation("org.postgresql:postgresql:42.6.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    testImplementation(kotlin("test"))
}

tasks.jar {
    manifest.attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(" ") { "libs/${it.name}" }
    manifest.attributes["Main-Class"] = "net.mymai1208.bot.MainKt"
}

tasks.build {
    copy {
        into("libs")
        from(configurations.runtimeClasspath)
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}