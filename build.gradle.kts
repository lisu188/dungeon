plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "pl.andrz.dungeon"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("pl.andrz.dungeon.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
