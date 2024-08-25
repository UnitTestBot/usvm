plugins {
    `kotlin-dsl`
}

val kotlinVersion = "1.9.20"
val detektVersion = "1.23.5"
val gjavahVersion = "0.3.1"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven {
        url = uri("https://jitpack.io")
        credentials {
            username = "IgorFilimonov"
            password = "jp_e35qr3m1iun510o1f4erns14d7"
        }
    }
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:$detektVersion")
    implementation("org.glavo:gjavah:$gjavahVersion")
}