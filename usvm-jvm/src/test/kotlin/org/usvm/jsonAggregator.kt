package org.usvm

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import mu.KLogging
import java.io.File
import java.net.URLClassLoader
import kotlin.io.path.Path
import org.usvm.samples.JavaMethodTestRunner
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction

val logger = object : KLogging() {}.logger

fun recursiveLoad(currentDir: File, classes: MutableList<Class<*>>, classLoader: ClassLoader, path: String) {
    currentDir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            recursiveLoad(file, classes, classLoader, "${path}${if (path == "") "" else "."}${file.name}")
        }
        if (file.isFile && file.extension == "java") {
            try {
                classes.add(classLoader.loadClass("${path}.${file.nameWithoutExtension}"))
            } catch (e: Exception) {
                logger.debug("Error when loading", e)
            }
        }
    }
}

private class MainTestRunner : JavaMethodTestRunner() {
    override var options = UMachineOptions().copy(
        exceptionsPropagation = false,
        timeoutMs = 20000,
        stepLimit = 1500u,
        pathSelectionStrategies = listOf(PathSelectionStrategy.INFERENCE_WITH_LOGGING)
    )

    fun runTest(method: KFunction<*>) {
        runner(method, options)
    }
}

fun main(args: Array<String>) {
    val testRunner = MainTestRunner()
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
        MainConfig.algorithm = Algorithm.valueOf((options.getOrDefault("algortihm",
            JsonPrimitive(MainConfig.algorithm.name)) as JsonPrimitive).content)
    }
    val samplesDir = File(MainConfig.samplesPath)
    val classLoader = URLClassLoader(arrayOf(samplesDir.toURI().toURL()))
    val classes = mutableListOf<Class<*>>()
    recursiveLoad(samplesDir, classes, classLoader, "")
    logger.debug("\nLOADING COMPLETE\n")
    runBlocking(Dispatchers.IO) {
        val tests = mutableListOf<Job>()
        classes.forEach { cls ->
            cls.methods.forEach loop@{ method ->
                if (method.declaringClass != cls) {
                    return@loop
                }
                val test = launch {
                    try {
                        logger.debug("Running test ${method.name}")
                        method.kotlinFunction?.let { testRunner.runTest(it) }
                    } catch (e: Exception) {
                        logger.debug("Exception during test ${method.name}", e)
                    } catch (e: NotImplementedError) {
                        logger.debug("Test not implemented ${method.name}", e)
                    }
                }
                tests.add(test)
            }
        }
        tests.joinAll()
    }

    val resultDirname = MainConfig.dataPath
    val dirname = Path(resultDirname, "jsons").toString()
    val resultFilename = "current_dataset.json"
    val jsons = mutableListOf<JsonElement>()

    File(dirname).listFiles()?.forEach { file ->
        if (!file.isFile || file.extension != "json") {
            return@forEach
        }
        val json = Json.decodeFromString<JsonElement>(file.readText())
        jsons.add(buildJsonObject {
            put("methodHash", file.nameWithoutExtension.hashCode())
            put("json", json)
            put("methodName", file.nameWithoutExtension)
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
                }
            }
        }
    }

    val resultFile = Path(resultDirname, resultFilename).toFile()
    resultFile.parentFile.mkdirs()
    resultFile.writeText(Json.encodeToString(bigJson))
}
