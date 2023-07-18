plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version "1.8.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":usvm-core"))

    implementation("org.jacodb:jacodb-core:${Versions.jcdb}")
    implementation("org.jacodb:jacodb-analysis:${Versions.jcdb}")

    implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-cvc5:${Versions.ksmt}")

    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
}

sourceSets {
    val samples by creating {
        java {
            srcDir("src/samples/java")
        }
    }

    test {
        compileClasspath += samples.output
        runtimeClasspath += samples.output
     }
}

val samplesImplementation: Configuration by configurations.getting

dependencies {
    samplesImplementation("org.projectlombok:lombok:${Versions.samplesLombok}")
    samplesImplementation("org.slf4j:slf4j-api:${Versions.samplesSl4j}")
    samplesImplementation("javax.validation:validation-api:${Versions.samplesJavaxValidation}")
    samplesImplementation("com.github.stephenc.findbugs:findbugs-annotations:${Versions.samplesFindBugs}")
    samplesImplementation("org.jetbrains:annotations:${Versions.samplesJetbrainsAnnotations}")
    samplesImplementation("org.jacodb:jacodb-core:${Versions.jcdb}")
    samplesImplementation("org.jacodb:jacodb-analysis:${Versions.jcdb}")
}

tasks {
    // Create a JAR file for jsonAggregator main function
    val jarMain by creating(Jar::class) {
        manifest {
            attributes["Main-Class"] = "org.usvm.JsonAggregatorKt"
        }

        from(sourceSets.main.get().output)
        from(sourceSets.test.get().output)
        from(java.sourceSets.getByName("samples").output)

        dependsOn.addAll(listOf(configurations.runtimeClasspath,
            configurations.testRuntimeClasspath,
            java.sourceSets.getByName("samples").runtimeClasspath))

        from({
            configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
        })
        from({
             configurations.testRuntimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
        })
        from({
            java.sourceSets.getByName("samples").runtimeClasspath.asFileTree.filter { it.name.endsWith("jar") }.map { zipTree(it) }
        })

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
