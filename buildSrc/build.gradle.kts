plugins {
    `kotlin-dsl`
}

val detektVersion = "1.23.5"
val kotlinVersion = "1.9.20"
val logback_version = "1.5.6"

repositories {
    mavenCentral()
    gradlePluginPortal()
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:$detektVersion")
    implementation("ch.qos.logback:logback-classic:$logback_version")
}