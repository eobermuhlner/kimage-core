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
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "-Xmx4g",
        "-Djava.awt.headless=true",
        "-Dawt.useSystemAAFontSettings=false",
        "-Dsun.font.fontfactory=LucidaSansFontFactory"
    )
}

tasks.withType<JavaExec> {
    jvmArgs("-Xmx4g")
}
kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("ch.obermuhlner.kimage.astro.platesolve.ComparePlateSolversKt")
    applicationName = "kimage-astro-process"
}