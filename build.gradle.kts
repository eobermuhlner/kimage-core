plugins {
    kotlin("jvm") version "2.0.10"
}

group = "ch.obermuhlner.kimage"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("gov.nasa.gsfc.heasarc:nom-tam-fits:1.15.2")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}