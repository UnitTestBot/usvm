import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    api(project(":usvm-dataflow"))

    api("${Versions.jacodbPackage}:jacodb-api-common:${Versions.jacodb}")
    api("${Versions.jacodbPackage}:jacodb-panda-dynamic:${Versions.jacodb}")
    implementation("${Versions.jacodbPackage}:jacodb-taint-configuration:${Versions.jacodb}")

    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")
    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
        allWarningsAsErrors = false
    }
}
