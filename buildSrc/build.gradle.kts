plugins {
    `kotlin-dsl`
}

val kotlinVersion = "2.1.0"
val detektVersion = "1.23.5"
val gjavahVersion = "0.3.1"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:$detektVersion")
    implementation("org.glavo:gjavah:$gjavahVersion")
}
