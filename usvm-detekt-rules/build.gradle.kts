import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

group = "org.usvm"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

val detektVersion = "1.23.5"

dependencies {
    compileOnly("io.gitlab.arturbosch.detekt:detekt-api:$detektVersion")

    testImplementation("io.gitlab.arturbosch.detekt:detekt-test:$detektVersion")
    testImplementation(Libs.junit_jupiter_api)
    testRuntimeOnly(Libs.junit_jupiter_engine)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        allWarningsAsErrors.set(true)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
