import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
}

val samples by sourceSets.creating {
    java {
        srcDir("src/samples/java")
    }
}

dependencies {
    api(project(":usvm-dataflow"))

    implementation("${Versions.jacodbPackage}:jacodb-api-common:${Versions.jacodb}")
    implementation("${Versions.jacodbPackage}:jacodb-api-jvm:${Versions.jacodb}")
    implementation("${Versions.jacodbPackage}:jacodb-core:${Versions.jacodb}")
    implementation("${Versions.jacodbPackage}:jacodb-taint-configuration:${Versions.jacodb}")

    implementation("io.github.detekt.sarif4k", "sarif4k", Versions.sarif4k)

    api("io.github.microutils:kotlin-logging:${Versions.klogging}")

    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")

    testImplementation(samples.output)
    testImplementation(files("src/test/resources/pointerbench.jar"))
    testImplementation("joda-time:joda-time:2.12.5")
    testImplementation("com.github.UnitTestBot.juliet-java-test-suite:support:1.3.2")
    for (cweNum in listOf(89, 476, 563, 690)) {
        testImplementation("com.github.UnitTestBot.juliet-java-test-suite:cwe${cweNum}:1.3.2")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
