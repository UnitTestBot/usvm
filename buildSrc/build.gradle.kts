plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

val kotlinVersion = "1.8.22"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
}