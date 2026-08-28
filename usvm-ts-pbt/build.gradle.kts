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
val fastCheckRuntimeProperty = "org.usvm.ts.pbt.fastcheck.runtime"
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

val installFastCheckAdapter = tasks.register<Exec>("installFastCheckAdapter") {
    workingDir(fastCheckAdapterDir)
    commandLine(npmExecutable, "ci", "--ignore-scripts")
    inputs.files(
        fastCheckAdapterDir.file("package.json"),
        fastCheckAdapterDir.file("package-lock.json"),
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
