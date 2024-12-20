plugins {
    kotlin("jvm") version "2.0.10"
    application
}

group = "ch.obermuhlner.kimage"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("gov.nasa.gsfc.heasarc:nom-tam-fits:1.15.2")
    implementation("org.yaml:snakeyaml:2.3")


    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("ch.obermuhlner.kimage.astro.process.AstroProcessKt")
    applicationName = "kimage-astro-process"
}