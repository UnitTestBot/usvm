plugins {
    `kotlin-dsl`
}

val kotlinVersion = "1.9.20"
val detektVersion = "1.23.5"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://jitpack.io")
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:$detektVersion")
    implementation("org.glavo:gjavah:0.3.1")
}