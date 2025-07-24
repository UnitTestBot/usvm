import kotlin.collections.plus
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    `java-library`
    `maven-publish`
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
            freeCompilerArgs += "-Xsam-conversions=class"
            allWarningsAsErrors = true
        }
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

setupJacodbGitHubPackagesRepository()

dependencies {
    compileOnly(Libs.jacodb_api_jvm)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
