plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

val kotlinVersion = "1.7.10"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}