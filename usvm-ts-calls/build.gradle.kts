plugins {
    id("usvm.kotlin-conventions")
    kotlin("plugin.serialization") version Versions.kotlin
    application
}

dependencies {
    implementation(project(":usvm-core"))
    implementation(project(":usvm-ts"))
    implementation(project(":usvm-ts-pbt"))
    implementation(Libs.jacodb_ets)
    implementation(Libs.kotlinx_serialization_json)

    testImplementation(Libs.logback)
}

val replayAdapterDir = layout.projectDirectory.dir("source-replay-adapter")
val replayAdapterPackageJson = replayAdapterDir.file("package.json")
val replayAdapterPackageLock = replayAdapterDir.file("package-lock.json")
val replayRuntimeProperty = "org.usvm.ts.calls.replay.runtime"
val generatedBuildMetadataDirectory = layout.buildDirectory.dir("generated/resources/callsBuildMetadata")
val hostOperatingSystem = System.getProperty("os.name").lowercase()
val hostPlatform = when {
    hostOperatingSystem.contains("mac") -> "darwin"
    hostOperatingSystem.contains("linux") -> "linux"
    hostOperatingSystem.contains("windows") -> "win32"
    else -> error("Unsupported source replay operating system: $hostOperatingSystem")
}
val npmExecutable = if (hostPlatform == "win32") "npm.cmd" else "npm"

val toolRevision = providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map(String::trim)
val toolStatus = providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", "status", "--porcelain", "--untracked-files=all")
}.standardOutput.asText.map(String::trim)

val generateBuildMetadata = tasks.register("generateBuildMetadata") {
    inputs.property("toolRevision", toolRevision)
    inputs.property("toolStatus", toolStatus)
    outputs.dir(generatedBuildMetadataDirectory)

    doLast {
        val revision = toolRevision.get()
        val buildIdentity = if (toolStatus.get().isBlank()) revision else "$revision-dirty"
        val metadataFile = generatedBuildMetadataDirectory.get()
            .file("org/usvm/ts/calls/build.properties")
            .asFile
        metadataFile.parentFile.mkdirs()
        metadataFile.writeText("tool.revision=$buildIdentity\n", Charsets.UTF_8)
    }
}

sourceSets.main {
    resources.srcDir(generatedBuildMetadataDirectory)
}

tasks.processResources {
    dependsOn(generateBuildMetadata)
}

val installReplayAdapter = tasks.register<Exec>("installReplayAdapter") {
    workingDir(replayAdapterDir)
    commandLine(npmExecutable, "ci", "--ignore-scripts")
    inputs.files(replayAdapterPackageJson, replayAdapterPackageLock)
    outputs.dir(replayAdapterDir.dir("node_modules"))
}

val buildReplayAdapter = tasks.register<Exec>("buildReplayAdapter") {
    dependsOn(installReplayAdapter)
    workingDir(replayAdapterDir)
    commandLine(npmExecutable, "run", "build")
    inputs.files(replayAdapterPackageJson, replayAdapterPackageLock, replayAdapterDir.file("tsconfig.json"))
    inputs.dir(replayAdapterDir.dir("src"))
    inputs.dir(replayAdapterDir.dir("test"))
    outputs.dir(replayAdapterDir.dir("dist"))
}

val testReplayAdapter = tasks.register<Exec>("testReplayAdapter") {
    dependsOn(buildReplayAdapter)
    workingDir(replayAdapterDir)
    commandLine(npmExecutable, "run", "test:compiled")
    inputs.dir(replayAdapterDir.dir("dist"))
}

tasks.test {
    dependsOn(buildReplayAdapter)
    systemProperty(replayRuntimeProperty, replayAdapterDir.asFile.absolutePath)
}

tasks.check {
    dependsOn(testReplayAdapter)
}

tasks.clean {
    delete(replayAdapterDir.dir("dist"))
}

application {
    mainClass = "org.usvm.ts.calls.CallsExperimentCliKt"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8")
}

tasks.named<JavaExec>("run") {
    systemProperty(replayRuntimeProperty, replayAdapterDir.asFile.absolutePath)
    dependsOn(buildReplayAdapter)
}

distributions {
    main {
        contents {
            into("lib/source-replay-adapter") {
                from(replayAdapterDir)
                include("dist/src/**")
                include("node_modules/**")
                include("package.json")
            }
        }
    }
}

listOf("startScripts", "installDist", "distZip", "distTar").forEach { taskName ->
    tasks.named(taskName) {
        dependsOn(buildReplayAdapter)
    }
}
