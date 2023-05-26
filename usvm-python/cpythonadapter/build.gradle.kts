import org.gradle.internal.jvm.Jvm

plugins {
    `cpp-library`
}

val cpythonPath = "${projectDir.path}/cpython"
val cpythonBuildPath = "${project.buildDir.path}/cpython_build"
val cpythonTaskGroup = "cpython"

val configCPython = tasks.register<Exec>("CPythonBuildConfiguration") {
    group = cpythonTaskGroup
    workingDir = File(cpythonPath)
    outputs.file("$cpythonPath/Makefile")
    commandLine(
        "$cpythonPath/configure",
        "--enable-shared",
        "--without-static-libpython",
        "--with-ensurepip=no",
        "--prefix=$cpythonBuildPath",
        "--disable-test-modules"
    )
}

val cpythonBuild = tasks.register<Exec>("CPythonBuild") {
    group = cpythonTaskGroup
    dependsOn(configCPython)
    inputs.dir(cpythonPath)
    workingDir = File(cpythonPath)
    commandLine("make")
}

val cpython = tasks.register<Exec>("CPythonInstall") {
    group = cpythonTaskGroup
    dependsOn(cpythonBuild)
    inputs.dir(cpythonPath)
    outputs.dirs("$cpythonBuildPath/lib", "$cpythonBuildPath/include", "$cpythonBuildPath/bin")
    workingDir = File(cpythonPath)
    commandLine("make", "install")
}

library {
    binaries.configureEach {
        val compileTask = compileTask.get()
        compileTask.includes.from("${Jvm.current().javaHome}/include")

        val osFamily = targetPlatform.targetMachine.operatingSystemFamily
        if (osFamily.isMacOs) {
            compileTask.includes.from("${Jvm.current().javaHome}/include/darwin")
        } else if (osFamily.isLinux) {
            compileTask.includes.from("${Jvm.current().javaHome}/include/linux")
        } else if (osFamily.isWindows) {
            compileTask.includes.from("${Jvm.current().javaHome}/include/win32")
        }

        compileTask.includes.from("$cpythonBuildPath/include/python3.11")
        compileTask.source.from(fileTree("src/main/c"))
        compileTask.compilerArgs.addAll(listOf("-x", "c", "-std=c11", "-L$cpythonBuildPath/lib", "-lpython3.11"))

        compileTask.dependsOn(cpython)
    }
}

val cpythonClean = tasks.register<Exec>("CPythonClean") {
    group = cpythonTaskGroup
    workingDir = File(cpythonPath)
    commandLine("make", "clean")
}

tasks.register<Exec>("CPythonDistclean") {
    group = cpythonTaskGroup
    workingDir = File(cpythonPath)
    commandLine("make", "distclean")
}

tasks.clean {
    dependsOn(cpythonClean)
}

tasks.register<Exec>("cpython_check_compile") {
    dependsOn(cpython)
    workingDir = File("${projectDir.path}/cpython_check")
    commandLine(
        "gcc",
        "-std=c11",
        "-I$cpythonBuildPath/include/python3.11",
        "sample_handler.c",
        "-o",
        "check",
        "-L$cpythonBuildPath/lib",
        "-lpython3.11"
    )
}