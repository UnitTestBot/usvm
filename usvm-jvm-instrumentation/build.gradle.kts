import com.jetbrains.rd.generator.gradle.RdGenExtension
import com.jetbrains.rd.generator.gradle.RdGenTask
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.sourceSets
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
    id("com.jetbrains.rdgen") version Versions.rd
    application
    java
}

val rdgenModelsCompileClasspath by configurations.creating {
    extendsFrom(configurations.compileClasspath.get())
}

sourceSets {
    val samples by creating {
        java {
            srcDir("src/samples/java")
        }
    }

    val collectors by creating {
        java {
            srcDir("src/collectors/java")
        }
    }

    test {
        compileClasspath += samples.output
        runtimeClasspath += samples.output
    }
}

kotlin {
    sourceSets.create("rdgenModels").apply {
        kotlin.srcDir("src/main/rdgen")
    }
}

dependencies {
    implementation("org.jacodb:jacodb-core:${Versions.jcdb}")
    implementation("org.jacodb:jacodb-analysis:${Versions.jcdb}")

    implementation("com.jetbrains.rd:rd-framework:${Versions.rd}")
    implementation("org.ini4j:ini4j:${Versions.ini4j}")
    implementation("com.jetbrains.rd:rd-core:${Versions.rd}")
    implementation("commons-cli:commons-cli:1.5.0")
    implementation("com.jetbrains.rd:rd-gen:${Versions.rd}")
    implementation(files(buildDir.resolve("libs").resolve("usvm-jvm-instrumentation-collectors.jar").absolutePath))
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            allWarningsAsErrors = false
        }
    }
}

val sourcesBaseDir = projectDir.resolve("src/main/kotlin")

val generatedPackage = "org.usvm.instrumentation.generated"
val generatedSourceDir = sourcesBaseDir.resolve(generatedPackage.replace('.', '/'))

val generatedModelsPackage = "$generatedPackage.models"
val generatedModelsSourceDir = sourcesBaseDir.resolve(generatedModelsPackage.replace('.', '/'))


val generateModels = tasks.register<RdGenTask>("generateProtocolModels") {
    dependsOn.addAll(listOf("compileKotlin"))
    val rdParams = extensions.getByName("params") as RdGenExtension
    val sourcesDir = projectDir.resolve("src/main/rdgen").resolve("org/usvm/instrumentation/models")

    group = "rdgen"
    rdParams.verbose = true
    rdParams.sources(sourcesDir)
    rdParams.hashFolder = buildDir.resolve("rdgen/hashes").absolutePath
    // where to search roots
    rdParams.packages = "org.usvm.instrumentation.models"

    rdParams.generator {
        language = "kotlin"
        transform = "symmetric"
        root = "org.usvm.instrumentation.models.InstrumentedProcessRoot"

        directory = generatedModelsSourceDir.absolutePath
        namespace = generatedModelsPackage
    }

    rdParams.generator {
        language = "kotlin"
        transform = "symmetric"
        root = "org.usvm.instrumentation.models.SyncProtocolRoot"

        directory = generatedModelsSourceDir.absolutePath
        namespace = generatedModelsPackage
    }

}


tasks {
    register<Jar>("instrumentationJar") {
        group = "jar"
        dependsOn.addAll(listOf("compileJava", "compileKotlin", "processResources"))
        archiveClassifier.set("1.0")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "org.usvm.instrumentation.rd.InstrumentedProcessKt",
                    "Premain-Class" to "org.usvm.instrumentation.agent.Agent",
                    "Can-Retransform-Classes" to "true",
                    "Can-Redefine-Classes" to "true"
                )
            )
        }

        val contents = configurations.runtimeClasspath.get()
            .map { if (it.isDirectory) it else zipTree(it) }

        from(contents)
        with(jar.get() as CopySpec)
    }
}


tasks {
    register<Jar>("testJar") {
        group = "jar"
        shouldRunAfter("compileTestKotlin")
        archiveClassifier.set("test")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        val contents = sourceSets.getByName("samples").output

        from(contents)
        dependsOn(getByName("compileSamplesJava"), configurations.testCompileClasspath)
        dependsOn(configurations.compileClasspath)
    }
}

tasks {
    register<Jar>("collectorsJar") {
        group = "jar"
        shouldRunAfter("compileKotlin")
        archiveClassifier.set("collectors")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        val contents = sourceSets.getByName("collectors").output

        from(contents)
        dependsOn(getByName("compileCollectorsJava"), configurations.compileClasspath)
        dependsOn(configurations.compileClasspath)
    }
}

tasks.withType<Test> {
    environment(
        "usvm-jvm-instrumentation-jar",
        buildDir.resolve("libs").resolve("usvm-jvm-instrumentation-1.0.jar").absolutePath
    )
    environment(
        "usvm-jvm-collectors-jar",
        buildDir.resolve("libs").resolve("usvm-jvm-instrumentation-collectors.jar").absolutePath
    )
}


tasks.getByName("compileKotlin").dependsOn("collectorsJar")//.mustRunAfter("collectorsJar")
tasks.getByName("compileKotlin").finalizedBy("instrumentationJar")
tasks.getByName("compileTestKotlin").finalizedBy("testJar")