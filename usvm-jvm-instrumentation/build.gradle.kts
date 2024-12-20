import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.jetbrains.rd.generator.gradle.RdGenExtension
import com.jetbrains.rd.generator.gradle.RdGenTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("usvm.kotlin-conventions")
    java
    id(Plugins.RdGen)
    id(Plugins.Shadow)
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
    implementation(Libs.jacodb_api_jvm) {
        // Unused dependencies
        exclude("javax.xml.bind", "jaxb-api")
        exclude("org.reactivestreams", "reactive-streams")
    }

    implementation(Libs.jacodb_core) {
        // Added above with exclusions
        exclude(Libs.jacodb_api_jvm)

        // Sqlite related dependencies. Unused because we use RAM persistence
        exclude("com.zaxxer", "HikariCP")
        exclude("org.xerial", "sqlite-jdbc")
    }

    implementation(Libs.jacodb_api_storage)
    implementation(Libs.jacodb_storage)

    implementation(Libs.rd_framework)
    implementation(Libs.ini4j)
    implementation(Libs.rd_core)
    implementation("commons-cli:commons-cli:1.5.0")

    rdgenModelsCompileClasspath(Libs.rd_gen)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        allWarningsAsErrors = false
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
    rdParams.hashFolder = layout.buildDirectory.file("rdgen/hashes").get().asFile.absolutePath
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
val runtimeClasspath = configurations.runtimeClasspath

val instrumentationRunnerJar = tasks.register<ShadowJar>("instrumentationJar") {
    group = "jar"
    dependsOn.addAll(listOf("compileJava", "compileKotlin", "processResources"))
    archiveBaseName.set("usvm-jvm-instrumentation-runner")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
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

    mergeServiceFiles()

    val contents = runtimeClasspath.get()
        .map { if (it.isDirectory) it else zipTree(it) }

    from(contents)
    with(tasks.jar.get() as CopySpec)
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

val collectorsJarTask = tasks.register<Jar>("collectorsJar") {
    group = "jar"
    shouldRunAfter("compileKotlin")
    archiveBaseName.set("usvm-jvm-instrumentation-collectors")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    val contents = sourceSets.getByName("collectors").output

    from(contents)
    dependsOn(tasks.getByName("compileCollectorsJava"), configurations.compileClasspath)
    dependsOn(configurations.compileClasspath)
}

sourceSets.main.get().compileClasspath += collectorsJarTask.get().outputs.files

tasks.withType<Test> {
    environment(
        "usvm-jvm-instrumentation-jar",
        instrumentationRunnerJar.get().outputs.files.single()
    )
    environment(
        "usvm-jvm-collectors-jar",
        collectorsJarTask.get().outputs.files.single()
    )
}


tasks.getByName("compileKotlin").dependsOn("collectorsJar")//.mustRunAfter("collectorsJar")
tasks.getByName("compileKotlin").finalizedBy("instrumentationJar")
tasks.getByName("compileTestKotlin").finalizedBy("testJar")

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
        create<MavenPublication>("maven-collectors") {
            artifactId = "usvm-jvm-instrumentation-collectors"
            artifact(collectorsJarTask.get())
        }

        create<MavenPublication>("maven-instrumentation-runner") {
            artifactId = "usvm-jvm-instrumentation-runner"
            artifact(instrumentationRunnerJar.get())
        }
    }
}
