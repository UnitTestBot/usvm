import groovy.json.JsonSlurper

plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
    application
}

dependencies {
    implementation(project(":usvm-ts"))
    implementation(Libs.jacodb_ets)
    implementation(Libs.clikt)
    implementation(Libs.kotlinx_serialization_json)

    testImplementation(Libs.logback)
}

val fastCheckAdapterDir = layout.projectDirectory.dir("fast-check-adapter")
val fastCheckAdapterPackageJson = fastCheckAdapterDir.file("package.json")
val fastCheckAdapterPackageLock = fastCheckAdapterDir.file("package-lock.json")
val fastCheckRuntimeProperty = "org.usvm.ts.pbt.fastcheck.runtime"
val generatedFastCheckRuntimeMetadataDirectory = layout.buildDirectory.dir(
    "generated/resources/fastCheckRuntimeMetadata",
)
val hostOperatingSystem = System.getProperty("os.name").lowercase()
val hostPlatform = when {
    hostOperatingSystem.contains("mac") -> "darwin"
    hostOperatingSystem.contains("linux") -> "linux"
    hostOperatingSystem.contains("windows") -> "win32"
    else -> error("Unsupported fast-check runtime operating system: $hostOperatingSystem")
}
val hostArchitecture = when (val architecture = System.getProperty("os.arch").lowercase()) {
    "aarch64", "arm64" -> "arm64"
    "amd64", "x86_64" -> "x64"
    "x86", "i386", "i686" -> "ia32"
    else -> error("Unsupported fast-check runtime architecture: $architecture")
}
val fastCheckRuntimeClassifier = "$hostPlatform-$hostArchitecture"
val npmExecutable = if (hostPlatform == "win32") "npm.cmd" else "npm"

val generateFastCheckRuntimeMetadata = tasks.register("generateFastCheckRuntimeMetadata") {
    inputs.file(fastCheckAdapterPackageLock)
    outputs.dir(generatedFastCheckRuntimeMetadataDirectory)

    doLast {
        val packageLock = JsonSlurper().parse(fastCheckAdapterPackageLock.asFile) as? Map<*, *>
            ?: error("Invalid fast-check adapter package lock")
        val packages = packageLock["packages"] as? Map<*, *>
            ?: error("Missing packages in fast-check adapter package lock")
        fun dependencyVersion(dependency: String): String {
            val metadata = packages["node_modules/$dependency"] as? Map<*, *>
                ?: error("Missing locked fast-check adapter dependency: $dependency")

            return (metadata["version"] as? String)
                ?.takeIf(String::isNotBlank)
                ?: error("Missing locked fast-check adapter dependency version: $dependency")
        }

        val metadataFile = generatedFastCheckRuntimeMetadataDirectory.get()
            .file("org/usvm/ts/pbt/fastcheck/runtime-dependencies.properties")
            .asFile
        metadataFile.parentFile.mkdirs()
        metadataFile.writeText(
            """
            fast-check.version=${dependencyVersion("fast-check")}
            c8.version=${dependencyVersion("c8")}
            """.trimIndent() + "\n",
            Charsets.UTF_8,
        )
    }
}

sourceSets.main {
    resources.srcDir(generatedFastCheckRuntimeMetadataDirectory)
}

tasks.processResources {
    dependsOn(generateFastCheckRuntimeMetadata)
}

val installFastCheckAdapter = tasks.register<Exec>("installFastCheckAdapter") {
    workingDir(fastCheckAdapterDir)
    commandLine(npmExecutable, "ci", "--ignore-scripts")
    inputs.files(
        fastCheckAdapterPackageJson,
        fastCheckAdapterPackageLock,
    )
    inputs.property("runtimeClassifier", fastCheckRuntimeClassifier)
    outputs.dir(fastCheckAdapterDir.dir("node_modules"))
}

val verifyFastCheckAdapterRuntime = tasks.register("verifyFastCheckAdapterRuntime") {
    dependsOn(installFastCheckAdapter)
    val nativeRuntime = fastCheckAdapterDir.dir("node_modules/@esbuild/$fastCheckRuntimeClassifier")

    inputs.dir(nativeRuntime)
    doLast {
        check(nativeRuntime.asFile.isDirectory) {
            "Missing esbuild runtime for $fastCheckRuntimeClassifier at ${nativeRuntime.asFile}"
        }
    }
}

val buildFastCheckAdapter = tasks.register<Exec>("buildFastCheckAdapter") {
    dependsOn(verifyFastCheckAdapterRuntime)
    workingDir(fastCheckAdapterDir)
    commandLine(npmExecutable, "run", "build")
    inputs.files(
        fastCheckAdapterDir.file("package.json"),
        fastCheckAdapterDir.file("package-lock.json"),
        fastCheckAdapterDir.file("tsconfig.json"),
    )
    inputs.dir(fastCheckAdapterDir.dir("src"))
    inputs.dir(fastCheckAdapterDir.dir("test"))
    outputs.dir(fastCheckAdapterDir.dir("dist"))
}

tasks.named<org.gradle.api.tasks.bundling.Zip>("distZip") {
    archiveClassifier.set(fastCheckRuntimeClassifier)
}

tasks.named<org.gradle.api.tasks.bundling.Tar>("distTar") {
    archiveClassifier.set(fastCheckRuntimeClassifier)
}

val testFastCheckAdapter = tasks.register<Exec>("testFastCheckAdapter") {
    dependsOn(buildFastCheckAdapter)
    workingDir(fastCheckAdapterDir)
    commandLine(npmExecutable, "run", "test:compiled")
    inputs.dir(fastCheckAdapterDir.dir("dist"))
}

tasks.test {
    dependsOn(buildFastCheckAdapter)
    systemProperty(fastCheckRuntimeProperty, fastCheckAdapterDir.asFile.absolutePath)
}

tasks.check {
    dependsOn(testFastCheckAdapter)
}

tasks.clean {
    delete(fastCheckAdapterDir.dir("dist"))
}

application {
    mainClass = "org.usvm.ts.pbt.cli.FastCheckCliKt"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8")
}

tasks.named<JavaExec>("run") {
    systemProperty(fastCheckRuntimeProperty, fastCheckAdapterDir.asFile.absolutePath)
}

distributions {
    main {
        contents {
            into("lib/fast-check-adapter") {
                from(fastCheckAdapterDir)
                include("dist/src/**")
                include("node_modules/**")
                include("package.json")
            }
        }
    }
}

listOf("run", "startScripts", "installDist", "distZip", "distTar").forEach { taskName ->
    tasks.named(taskName) {
        dependsOn(buildFastCheckAdapter)
    }
}
