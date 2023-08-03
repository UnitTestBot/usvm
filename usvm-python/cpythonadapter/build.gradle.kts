import org.gradle.internal.jvm.Jvm
import groovy.json.JsonSlurper

plugins {
    `cpp-library`
}

val cpythonPath = "${projectDir.path}/cpython"
val cpythonBuildPath = "${project.buildDir.path}/cpython_build"
val cpythonTaskGroup = "cpython"

val configCPythonDebug = tasks.register<Exec>("CPythonBuildConfigurationDebug") {
    group = cpythonTaskGroup
    workingDir = File(cpythonPath)
    outputs.file("$cpythonPath/Makefile")
    commandLine(
        "$cpythonPath/configure",
        "--enable-shared",
        "--without-static-libpython",
        "--with-ensurepip=no",
        "--prefix=$cpythonBuildPath",
        "--disable-test-modules",
        "--with-assertions"
    )
}

val configCPythonRelease = tasks.register<Exec>("CPythonBuildConfigurationRelease") {
    group = cpythonTaskGroup
    workingDir = File(cpythonPath)
    outputs.file("$cpythonPath/Makefile")
    commandLine(
        "$cpythonPath/configure",
        "--enable-shared",
        "--without-static-libpython",
        "--with-ensurepip=no",
        "--prefix=$cpythonBuildPath",
        "--disable-test-modules",
        "--enable-optimizations"
    )
}

val cpythonBuildDebug = tasks.register<Exec>("CPythonBuildDebug") {
    group = cpythonTaskGroup
    dependsOn(configCPythonDebug)
    inputs.dir(cpythonPath)
    outputs.dirs("$cpythonBuildPath/lib", "$cpythonBuildPath/include", "$cpythonBuildPath/bin")
    workingDir = File(cpythonPath)
    commandLine("make")
    commandLine("make", "install")
}

val cpythonBuildRelease = tasks.register<Exec>("CPythonBuildRelease") {
    group = cpythonTaskGroup
    dependsOn(configCPythonRelease)
    inputs.dir(cpythonPath)
    outputs.dirs("$cpythonBuildPath/lib", "$cpythonBuildPath/include", "$cpythonBuildPath/bin")
    workingDir = File(cpythonPath)
    commandLine("make")
    commandLine("make", "install")
}

@Suppress("unchecked_cast")
fun generateSymbolicAdapterMethod(description: Map<String, Any>): String {
    val cName = description["c_name"] as String
    val cReturnType = description["c_return_type"] as String
    val numberOfArgs = description["nargs"] as Int
    val cArgTypes = description["c_arg_types"] as List<String>
    val cArgs = List(numberOfArgs) { index -> "${cArgTypes[index]} arg_$index" }
    val javaArgTypes = description["java_arg_types"] as List<String>
    val argConverters = description["argument_converters"] as List<String>
    val failValue = description["fail_value"] as String
    val defaultValue = description["default_value"] as String
    val javaArgsCreation = List(numberOfArgs) { index ->
        """
            ${javaArgTypes[index]} java_arg_$index = ${argConverters[index]}(ctx, arg_$index, &fail); if (fail) return $defaultValue;
        """.trimIndent()
    }
    val javaReturnType = description["java_return_type"] as String
    val javaReturnDescr: String = when (javaReturnType) {
        "jobject" -> "Object"
        "void" -> "Void"
        else -> error("Incorrect handler definition")
    }
    val javaArgs = (listOf("ctx->context") + List(numberOfArgs) { "java_arg_$it" }).joinToString(", ")
    val returnValueCreation =
        if (javaReturnType != "void")
            "$javaReturnType java_return = (*ctx->env)->CallStatic${javaReturnDescr}Method(ctx->env, ctx->cpython_adapter_cls, ctx->handle_$cName, $javaArgs);"
        else
            "(*ctx->env)->CallStaticVoidMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_$cName, $javaArgs);"
    val returnConverted = description["result_converter"] as String
    val returnStmt =
        if (javaReturnType == "void")
            "return $defaultValue;"
        else
            "return $returnConverted(ctx, java_return);"
    return """
        static $cReturnType
        $cName(${(listOf("void *arg") + cArgs).joinToString(", ")}) {
            // printf("INSIDE $cName!\n"); fflush(stdout);
            ConcolicContext *ctx = (ConcolicContext *) arg;
            int fail = 0;
            ${javaArgsCreation.joinToString("\n            ")}
            // printf("CALLING JAVA METHOD IN $cName!\n"); fflush(stdout);
            $returnValueCreation
            CHECK_FOR_EXCEPTION(ctx, $failValue)
            $returnStmt
        }
    """.trimIndent()
}

fun generateSymbolicAdapterMethods(): String {
    val handlerDefs by extra {
        @Suppress("unchecked_cast")
        JsonSlurper().parse(file("${projectDir.path}/src/main/json/adapter_method_defs.json")) as List<Map<String, Any>>
    }
    val defs = handlerDefs.joinToString("\n\n", transform = ::generateSymbolicAdapterMethod)
    val registration = """
        static void
        REGISTER_ADAPTER_METHODS(SymbolicAdapter *adapter) {
            ${
                handlerDefs.joinToString(" ") {
                    val name = it["c_name"]!!
                    "adapter->$name = $name;"
                }
            }
        }
    """.trimIndent()
    return defs + "\n\n" + registration
}

tasks.register<Exec>("testGenerateTask") {
    commandLine("echo", generateSymbolicAdapterMethods())
}

fun generateCPythonAdapterDefs(): String {
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
    val fileForCPythonAdapterMethods = File("$adapterHeaderPath/CPythonAdapterMethods.h")
    fileForCPythonAdapterMethods.createNewFile()
    fileForCPythonAdapterMethods.writeText(generateCPythonAdapterDefs())
    val fileForSymbolicAdapterMethods = File("$adapterHeaderPath/SymbolicAdapterMethods.h")
    fileForSymbolicAdapterMethods.createNewFile()
    fileForSymbolicAdapterMethods.writeText(generateSymbolicAdapterMethods())
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
        compileTask.includes.from("src/main/c/include")
        compileTask.source.from(fileTree("src/main/c"))
        compileTask.compilerArgs.addAll(listOf("-x", "c", "-std=c11", "-L$cpythonBuildPath/lib", "-lpython3.11", "-Werror", "-Wall"))

        compileTask.dependsOn(headers)
        if (!compileTask.isOptimized) {
            compileTask.dependsOn(cpythonBuildDebug)
        } else {
            compileTask.dependsOn(cpythonBuildRelease)
        }
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
    dependsOn(cpythonBuildDebug)
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