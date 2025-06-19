@file:Suppress("PropertyName", "HasPlatformType")

plugins {
    id("usvm.kotlin-conventions")
}

val samples by sourceSets.creating {
    java {
        srcDir("src/samples/java")
    }
}

val `samples-jdk11` by sourceSets.creating {
    java {
        srcDir("src/samples-jdk11/java")
    }
}

val `sample-approximations` by sourceSets.creating {
    java {
        srcDir("src/sample-approximations/java")
    }
}

val approximations by configurations.creating
val approximationsRepo = "com.github.UnitTestBot.java-stdlib-approximations"
val approximationsVersion = "5f137507d6"

dependencies {
    implementation(project(":usvm-core"))
    implementation(project(":usvm-jvm-dataflow"))

    implementation(Libs.jacodb_api_jvm)
    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_approximations)

    implementation(project("usvm-jvm-api"))
    implementation(project("usvm-jvm-test-api"))
    implementation(project("usvm-jvm-util"))

    implementation(Libs.ksmt_runner)
    implementation(Libs.ksmt_yices)
    implementation(Libs.ksmt_cvc5)
    implementation(Libs.ksmt_symfpu)

    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.logback)

    testImplementation(samples.output)

    // https://mvnrepository.com/artifact/org.burningwave/core
    // Use it to export all modules to all
    testImplementation("org.burningwave:core:12.62.7")

    approximations(approximationsRepo, "approximations", approximationsVersion)
    testImplementation(approximationsRepo, "tests", approximationsVersion)
}

val samplesImplementation: Configuration by configurations.getting

dependencies {
    samplesImplementation("org.projectlombok:lombok:${Versions.Samples.lombok}")
    samplesImplementation("org.slf4j:slf4j-api:${Versions.Samples.slf4j}")
    samplesImplementation("javax.validation:validation-api:${Versions.Samples.javaxValidation}")
    samplesImplementation("com.github.stephenc.findbugs:findbugs-annotations:${Versions.Samples.findBugs}")
    samplesImplementation("org.jetbrains:annotations:${Versions.Samples.jetbrainsAnnotations}")

    // Use usvm-api in samples for makeSymbolic, assume, etc.
    samplesImplementation(project("usvm-jvm-api"))

    testImplementation(project(":usvm-jvm-instrumentation"))
}

val `sample-approximationsCompileOnly`: Configuration by configurations.getting

dependencies {
    `sample-approximationsCompileOnly`(samples.output)
    `sample-approximationsCompileOnly`(project("usvm-jvm-api"))
    `sample-approximationsCompileOnly`(Libs.jacodb_api_jvm)
    `sample-approximationsCompileOnly`(Libs.jacodb_approximations)
}

val testSamples by configurations.creating
val testSamplesWithApproximations by configurations.creating

val compileSamplesJdk11 = tasks.register<JavaCompile>("compileSamplesJdk11") {
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()

    source = `samples-jdk11`.java
    classpath = `samples-jdk11`.compileClasspath
    options.sourcepath = `samples-jdk11`.java
    destinationDirectory = `samples-jdk11`.java.destinationDirectory
}

dependencies {
    testSamples(samples.output)
    testSamples(project("usvm-jvm-api"))
    testSamples(files(`samples-jdk11`.java.destinationDirectory))

    testSamplesWithApproximations(samples.output)
    testSamplesWithApproximations(project("usvm-jvm-api"))
    testSamplesWithApproximations(`sample-approximations`.output)
    testSamplesWithApproximations(approximationsRepo, "tests", approximationsVersion)
}

val usvmApiJarConfiguration by configurations.creating
dependencies {
    usvmApiJarConfiguration(project("usvm-jvm-api"))
}

tasks.withType<Test> {
    dependsOn(compileSamplesJdk11, testSamples, testSamplesWithApproximations)

    val usvmApiJarPath = usvmApiJarConfiguration.resolvedConfiguration.files.single()
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
        layout
            .buildDirectory
            .file("libs/usvm-jvm-test.jar")
            .get().asFile.absolutePath
    )
    environment(
        "usvm-jvm-instrumentation-jar",
        project(":usvm-jvm-instrumentation")
            .layout
            .buildDirectory
            .file("libs/usvm-jvm-instrumentation-1.0.jar")
            .get().asFile.absolutePath
    )
    environment(
        "usvm-jvm-collectors-jar",
        project(":usvm-jvm-instrumentation")
            .layout
            .buildDirectory
            .file("libs/usvm-jvm-instrumentation-collectors.jar")
            .get().asFile.absolutePath
    )
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
