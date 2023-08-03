package org.usvm

import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import java.io.File
import java.net.URLClassLoader
import kotlin.io.path.Path
import org.usvm.samples.JavaMethodTestRunner
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction
import kotlin.system.measureTimeMillis

val jsonsDirname = Path(MainConfig.dataPath, "jsons").toString()

fun recursiveLoad(currentDir: File, classes: MutableList<Class<*>>, classLoader: ClassLoader, path: String) {
    currentDir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            recursiveLoad(file, classes, classLoader, "${path}${if (path == "") "" else "."}${file.name}")
        }
        if (file.isFile && file.extension == "java") {
            try {
                classes.add(classLoader.loadClass("${path}.${file.nameWithoutExtension}"))
            } catch (e: Exception) {
                println(e)
            }
        }
    }
}

private class MainTestRunner : JavaMethodTestRunner() {
    override var options = UMachineOptions().copy(
        exceptionsPropagation = false,
        timeoutMs = 20000,
        stepLimit = 1500u,
        pathSelectionStrategies = listOf(PathSelectionStrategy.INFERENCE_WITH_LOGGING),
        solverType = SolverType.Z3
    )

    fun runTest(method: KFunction<*>) {
        runner(method, options)
    }
}

fun getName(method: Method): String {
    return "${method.declaringClass.name}#${method.name}(${method.parameters.joinToString { it.type.typeName }})"
}

fun calculate() {
    val testRunner = MainTestRunner()
    val samplesDir = File(MainConfig.samplesPath)
    val classLoader = URLClassLoader(arrayOf(samplesDir.toURI().toURL()))
    val classes = mutableListOf<Class<*>>()
    recursiveLoad(samplesDir, classes, classLoader, "")
    println("\nLOADING COMPLETE\n")

    val longFile = Path(MainConfig.gameEnvPath, "long-methods.txt").toFile()
    longFile.parentFile.mkdirs()
    longFile.createNewFile()
    val badFile = Path(MainConfig.gameEnvPath, "bad-methods.txt").toFile()
    badFile.parentFile.mkdirs()
    badFile.createNewFile()

    val oldLongMethods = longFile.readLines()
    val oldBadMethods = badFile.readLines()

    val badMethods = mutableListOf<String>()
    val tests = mutableListOf<Job>()
    runBlocking(Dispatchers.IO) {
        classes.forEach { cls ->
            cls.methods.filter { it.declaringClass === cls && getName(it) !in oldLongMethods && getName(it) !in oldBadMethods }.forEach { method ->
                val test = launch {
                    try {
                        //                        logger.debug("Running test ${method.name}")
                        println("Running test ${method.declaringClass.name}#${method.name}(${method.parameters.joinToString { it.type.typeName }})")
                        val time = measureTimeMillis {
                            method.kotlinFunction?.let { testRunner.runTest(it) }
                        }
                        println("Test ${method.name} finished after ${time}ms")
                        if (time > 50000) {
                            MainConfig.longTests.add("${method.declaringClass.name}#${method.name}(${method.parameters.joinToString { it.type.typeName }})")
                        }
                    } catch (e: Exception) {
                        badMethods.add("${method.declaringClass.name}#${method.name}(${method.parameters.joinToString { it.type.typeName }})")
                        println(e)
                    } catch (e: NotImplementedError) {
                        badMethods.add("${method.declaringClass.name}#${method.name}(${method.parameters.joinToString { it.type.typeName }})")
                        println(e)
                    }
                }
                tests.add(test)
            }
        }
        tests.joinAll()
    }

    println("\nALL TESTS FINISHED\n")
    if (MainConfig.longTests.isNotEmpty()) {
        longFile.appendText(MainConfig.longTests.joinToString("\n", prefix = "\n") { it })
    }
    if (badMethods.isNotEmpty()) {
        badFile.appendText(badMethods.joinToString("\n", prefix = "\n") { it })
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun aggregate() {
    val resultDirname = MainConfig.dataPath
    val resultFilename = "current_dataset.json"
    val jsons = mutableListOf<JsonElement>()

    File(jsonsDirname).listFiles()?.forEach { file ->
        if (!file.isFile || file.extension != "json") {
            return@forEach
        }
        val json = Json.decodeFromString<JsonElement>(file.readText())
        jsons.add(buildJsonObject {
            put("json", json)
            put("methodName", file.nameWithoutExtension)
            put("methodHash", file.nameWithoutExtension.hashCode())
        })
        file.delete()
    }
    jsons.sortBy { it.jsonObject["methodName"].toString() }

    if (jsons.isEmpty()) {
        return
    }
    val bigJson = buildJsonObject {
        put("scheme", jsons.first().jsonObject["json"]!!.jsonObject["scheme"]!!)
        putJsonArray("paths") {
            jsons.forEach {
                addJsonArray {
                    add(it.jsonObject["methodHash"]!!)
                    add(it.jsonObject["json"]!!.jsonObject["path"]!!)
                    add(it.jsonObject["methodName"]!!)
                    add(it.jsonObject["json"]!!.jsonObject["statementsCount"]!!)
                }
            }
        }
    }

    val resultFile = Path(resultDirname, resultFilename).toFile()
    resultFile.parentFile.mkdirs()
    Json.encodeToStream(bigJson, resultFile.outputStream())
}

fun main(args: Array<String>) {
    val options = args.getOrNull(0)?.let { File(it) }?.readText()?.let {
        Json.decodeFromString<JsonObject>(it)
    }
    if (options != null) {
        MainConfig.samplesPath =
            (options.getOrDefault("samplesPath", JsonPrimitive(MainConfig.samplesPath)) as JsonPrimitive).content
        MainConfig.gameEnvPath =
            (options.getOrDefault("gameEnvPath", JsonPrimitive(MainConfig.gameEnvPath)) as JsonPrimitive).content
        MainConfig.dataPath =
            (options.getOrDefault("dataPath", JsonPrimitive(MainConfig.dataPath)) as JsonPrimitive).content
        MainConfig.postprocessing = Postprocessing.valueOf((options.getOrDefault("postprocessing",
            JsonPrimitive(MainConfig.postprocessing.name)) as JsonPrimitive).content)
        MainConfig.mode = Mode.valueOf((options.getOrDefault("mode",
            JsonPrimitive(MainConfig.mode.name)) as JsonPrimitive).content)
        MainConfig.inputShape =
            (options.getOrDefault("inputShape", JsonArray(MainConfig.inputShape
                .map { JsonPrimitive(it) })) as JsonArray).map { (it as JsonPrimitive).content.toLong() }
        MainConfig.maxAttentionLength =
            (options.getOrDefault("maxAttentionLength",
                JsonPrimitive(MainConfig.maxAttentionLength)) as JsonPrimitive).content.toInt()
    }

    println("OPTIONS:")
    println("POSTPROCESSING: ${MainConfig.postprocessing}")
    println("INPUT SHAPE: ${MainConfig.inputShape}")
    println("MAX ATTENTION LENGTH: ${MainConfig.maxAttentionLength}")
    println()

    if (MainConfig.mode == Mode.Calculation || MainConfig.mode == Mode.Both) {
        try {
            calculate()
        } catch (e: Throwable) {
            File(jsonsDirname).listFiles()?.forEach { file ->
                file.delete()
            }
            println(e)
        }
    }

    if (MainConfig.mode == Mode.Aggregation || MainConfig.mode == Mode.Both) {
        try {
            aggregate()
        } catch (e: Throwable) {
            File(jsonsDirname).listFiles()?.forEach { file ->
                file.delete()
            }
            println(e)
        }
    }
}
