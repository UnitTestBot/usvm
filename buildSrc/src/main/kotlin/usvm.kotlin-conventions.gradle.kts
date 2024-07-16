import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    id("io.gitlab.arturbosch.detekt")
}

group = "org.usvm"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    implementation(Libs.kotlin_logging)
    implementation(Libs.kotlinx_coroutines_core)

    testImplementation(kotlin("test"))
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:all")
        options.compilerArgs.add("-Xlint:-options")
        options.compilerArgs.add("-Werror")
    }
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
            freeCompilerArgs += "-Xallow-result-return-type"
            freeCompilerArgs += "-Xsam-conversions=class"
            allWarningsAsErrors = true
        }
    }
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()

    maxHeapSize = "1G"

    testLogging {
        events("passed")
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/UnitTestBot/usvm")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

configureDetekt()
