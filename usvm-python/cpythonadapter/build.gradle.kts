import org.gradle.internal.jvm.Jvm
import groovy.json.JsonSlurper

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

fun generateHandlerDefs(): String {
    val handlerDefs by extra {
        @Suppress("unchecked_cast")
        JsonSlurper().parse(file("${projectDir.path}/src/main/json/handler_defs.json")) as List<Map<String, String>>
    }
    val jmethodIDMacro = handlerDefs.fold("#define HANDLERS_DEFS ") { acc, handler ->
        acc + "jmethodID handle_${handler["c_name"]!!}; "
    }
    val nameMacros = handlerDefs.map { "#define handle_name_${it["c_name"]!!} \"${it["java_name"]!!}\"" }
    val sigMacros = handlerDefs.map { "#define handle_sig_${it["c_name"]!!} \"${it["sig"]!!}\"" }

    val registrations = handlerDefs.fold("#define DO_REGISTRATIONS(dist, env) ") { acc, handler ->
        val name = handler["c_name"]!!
        acc + "dist->handle_$name = (*env)->GetStaticMethodID(env, dist->cpython_adapter_cls, handle_name_$name, handle_sig_$name);"
    }

    return """
            $jmethodIDMacro
            ${nameMacros.joinToString("\n            ")}
            ${sigMacros.joinToString("\n            ")}
            $registrations
    """.trimIndent()
}

val adapterHeaderPath = "${project.buildDir.path}/adapter_include"

val headers = tasks.register("generateHeaders") {
    File(adapterHeaderPath).mkdirs()
    val file = File("$adapterHeaderPath/CPythonAdapterMethods.h")
    file.createNewFile()
    file.writeText(generateHandlerDefs())
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

        compileTask.includes.from(adapterHeaderPath)
        compileTask.includes.from("$cpythonBuildPath/include/python3.11")
        compileTask.source.from(fileTree("src/main/c"))
        compileTask.compilerArgs.addAll(listOf("-x", "c", "-std=c11", "-L$cpythonBuildPath/lib", "-lpython3.11"))

        compileTask.dependsOn(cpython)
        compileTask.dependsOn(headers)
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