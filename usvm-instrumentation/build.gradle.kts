import com.jetbrains.rd.generator.gradle.RdGenExtension
import com.jetbrains.rd.generator.gradle.RdGenTask
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.sourceSets

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
    implementation("com.github.UnitTestBot.jacodb:jacodb-core:${Versions.jcdb}")
    implementation("com.github.UnitTestBot.jacodb:jacodb-analysis:${Versions.jcdb}")

    implementation("com.jetbrains.rd:rd-framework:${Versions.rd}")
    implementation("org.ini4j:ini4j:${Versions.ini4j}")
    implementation("com.jetbrains.rd:rd-core:${Versions.rd}")
    implementation("commons-cli:commons-cli:1.5.0")
//    rdgenModelsCompileClasspath("com.jetbrains.rd:rd-gen:${Versions.rd}")
    implementation("com.jetbrains.rd:rd-gen:${Versions.rd}")
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
        } // Provided we set it up in the application plugin configuration

        val contents = configurations.runtimeClasspath.get()
            .map { if (it.isDirectory) it else zipTree(it) }

        from(contents)
        with(jar.get() as CopySpec)
    }
    build {
        dependsOn(configurations.compileClasspath)
    }
}

tasks {
    register<Jar>("testJar") {
        group = "jar"
        archiveClassifier.set("test")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        val contents = sourceSets.getByName("samples").output

        from(contents)
//        with(jar.get() as CopySpec)
        dependsOn(getByName("compileSamplesJava"), configurations.testCompileClasspath)
        dependsOn(configurations.compileClasspath)
    }

}

tasks.withType<Test> {
    environment(
        "usvm-instrumentation-jar",
        buildDir.resolve("libs").resolve("usvm-instrumentation-1.0.jar").absolutePath
    )
}
