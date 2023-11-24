plugins {
    id("usvm.kotlin-conventions")
}

val samples by sourceSets.creating {
    java {
        srcDir("src/samples/java")
    }
}

val `sample-approximations` by sourceSets.creating {
    java {
        srcDir("src/sample-approximations/java")
    }
}

val `usvm-api` by sourceSets.creating {
    java {
        srcDir("src/usvm-api/java")
    }
}

val approximations by configurations.creating
val approximationsRepo = "com.github.UnitTestBot.java-stdlib-approximations"
val approximationsVersion = "fdbed9a76b"

dependencies {
    implementation(project(":usvm-core"))

    implementation("org.jacodb:jacodb-core:${Versions.jcdb}")
    implementation("org.jacodb:jacodb-analysis:${Versions.jcdb}")

    implementation("org.jacodb:jacodb-approximations:${Versions.jcdb}")

    implementation(`usvm-api`.output)

    implementation("io.ksmt:ksmt-yices:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-cvc5:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-symfpu:${Versions.ksmt}")
    implementation("io.ksmt:ksmt-runner:${Versions.ksmt}")

    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${Versions.junitParams}")
    testImplementation("ch.qos.logback:logback-classic:${Versions.logback}")

    testImplementation(samples.output)

    // https://mvnrepository.com/artifact/org.burningwave/core
    // Use it to export all modules to all
    testImplementation("org.burningwave:core:12.62.7")

    approximations(approximationsRepo, "approximations", approximationsVersion)
    testImplementation(approximationsRepo, "tests", approximationsVersion)
}

val `usvm-apiCompileOnly`: Configuration by configurations.getting
dependencies {
    `usvm-apiCompileOnly`("org.jacodb:jacodb-api:${Versions.jcdb}")
}

val samplesImplementation: Configuration by configurations.getting

dependencies {
    samplesImplementation("org.projectlombok:lombok:${Versions.samplesLombok}")
    samplesImplementation("org.slf4j:slf4j-api:${Versions.samplesSl4j}")
    samplesImplementation("javax.validation:validation-api:${Versions.samplesJavaxValidation}")
    samplesImplementation("com.github.stephenc.findbugs:findbugs-annotations:${Versions.samplesFindBugs}")
    samplesImplementation("org.jetbrains:annotations:${Versions.samplesJetbrainsAnnotations}")
    testImplementation(project(":usvm-jvm-instrumentation"))
    // Use usvm-api in samples for makeSymbolic, assume, etc.
    samplesImplementation(`usvm-api`.output)
}

val `sample-approximationsCompileOnly`: Configuration by configurations.getting

dependencies {
    `sample-approximationsCompileOnly`(samples.output)
    `sample-approximationsCompileOnly`(`usvm-api`.output)
    `sample-approximationsCompileOnly`("org.jacodb:jacodb-api:${Versions.jcdb}")
    `sample-approximationsCompileOnly`("org.jacodb:jacodb-approximations:${Versions.jcdb}")
}

val `usvm-api-jar` = tasks.register<Jar>("usvm-api-jar") {
    archiveBaseName.set(`usvm-api`.name)
    from(`usvm-api`.output)
}

val testSamples by configurations.creating
val testSamplesWithApproximations by configurations.creating

dependencies {
    testSamples(samples.output)
    testSamples(`usvm-api`.output)

    testSamplesWithApproximations(samples.output)
    testSamplesWithApproximations(`usvm-api`.output)
    testSamplesWithApproximations(`sample-approximations`.output)
    testSamplesWithApproximations(approximationsRepo, "tests", approximationsVersion)
}

tasks.withType<Test> {
    dependsOn(`usvm-api-jar`)
    dependsOn(testSamples, testSamplesWithApproximations)

    val usvmApiJarPath = `usvm-api-jar`.get().outputs.files.singleFile
    val usvmApproximationJarPath = approximations.resolvedConfiguration.files.single()

    environment("usvm.jvm.api.jar.path", usvmApiJarPath.absolutePath)
    environment("usvm.jvm.approximations.jar.path", usvmApproximationJarPath.absolutePath)

    environment("usvm.jvm.test.samples", testSamples.asPath)
    environment("usvm.jvm.test.samples.approximations", testSamplesWithApproximations.asPath)
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

tasks.getByName("compileTestKotlin").finalizedBy("testJar")

tasks.withType<Test> {
    environment(
        "usvm-test-jar",
        buildDir
            .resolve("libs")
            .resolve("usvm-jvm-test.jar")
            .absolutePath
    )
    environment(
        "usvm-jvm-instrumentation-jar",
        project(":usvm-jvm-instrumentation")
            .buildDir
            .resolve("libs")
            .resolve("usvm-jvm-instrumentation-1.0.jar")
            .absolutePath
    )
    environment(
        "usvm-jvm-collectors-jar",
        project(":usvm-jvm-instrumentation")
            .buildDir
            .resolve("libs")
            .resolve("usvm-jvm-instrumentation-collectors.jar")
            .absolutePath
    )
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
        create<MavenPublication>("maven-api") {
            artifactId = "usvm-jvm-api"
            artifact(`usvm-api-jar`)
        }
    }
}
