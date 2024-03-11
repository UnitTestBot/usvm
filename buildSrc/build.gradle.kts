plugins {
    `kotlin-dsl`
}

val kotlinVersion = "1.9.20"
val detektVersion = "1.23.5"

repositories {
    mavenCentral()
    gradlePluginPortal()
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:$detektVersion")
}