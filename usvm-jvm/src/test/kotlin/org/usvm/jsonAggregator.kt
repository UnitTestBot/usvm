package org.usvm

import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.packageName
import org.usvm.machine.JcMachine
import org.usvm.samples.JacoDBContainer
import java.io.File
import kotlin.io.path.Path
import org.usvm.samples.JavaMethodTestRunner
import kotlin.system.measureTimeMillis

//fun recursiveLoad(currentDir: File, classes: MutableList<Class<*>>, classLoader: ClassLoader, path: String) {
//    currentDir.listFiles()?.forEach { file ->
//        if (file.isDirectory) {
//            recursiveLoad(file, classes, classLoader, "${path}${if (path == "") "" else "."}${file.name}")
//        }
//        if (file.isFile && file.extension == "java") {
//            try {
//                classes.add(classLoader.loadClass("${path}.${file.nameWithoutExtension}"))
//            } catch (e: Exception) {
//                println(e)
//            }
//        }
//    }
//}

fun jarLoad(jars: Set<String>, classes: MutableMap<String, MutableList<JcClassOrInterface>>) {
    jars.forEach { filePath ->
        val file = Path(filePath).toFile()
        val container = JacoDBContainer(key = filePath, classpath = listOf(file))
        val classNames = container.db.locations.flatMap { it.jcLocation?.classNames ?: listOf() }
        classes[filePath] = mutableListOf()
        classNames.forEach { className ->
            container.cp.findClassOrNull(className)?.let {
                classes[filePath]?.add(it)
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

//    fun runTest(method: KFunction<*>) {
//        runnerAlternative(method, options)
//    }

    fun runTest(method: JcMethod, jarKey: String) {
        JcMachine(JacoDBContainer(jarKey).cp, options).use { jcMachine ->
            jcMachine.analyze(method)
        }
    }
}

//fun getName(method: Method): String {
//    return "${method.declaringClass.name}#${method.name}(${method.parameters.joinToString { it.type.typeName }})"
//}

fun getName(method: JcMethod): String {
    return method.toString().dropWhile { it != ')' }.drop(1)
}

fun calculate() {
    val testRunner = MainTestRunner()
    val jarClasses = mutableMapOf<String, MutableList<JcClassOrInterface>>()
    jarLoad(MainConfig.inputJars.keys, jarClasses)
    println("\nLOADING COMPLETE\n")

    val blacklist = Path(MainConfig.gameEnvPath, "blacklist.txt").toFile().let {
        it.createNewFile()
        it.readLines()
    }

    val tests = mutableListOf<Job>()
    var finishedTestsCount = 0

    jarClasses.forEach { (key, classesList) ->
        val allMethods = classesList.filter { cls ->
            !cls.isAnnotation && !cls.isInterface &&
                    MainConfig.inputJars.getValue(key).any { cls.packageName.contains(it) } &&
                    !cls.name.contains("Test")
        }.flatMap { cls -> cls.declaredMethods.filter { method ->
            method.enclosingClass == cls && getName(method) !in blacklist && !method.isConstructor
        } }
        val orderedMethods = if (MainConfig.shuffleTests) allMethods.shuffled() else
            allMethods.sortedBy { getName(it).hashCode() }
        runBlocking(Dispatchers.IO) {
            orderedMethods.take((orderedMethods.size * MainConfig.dataConsumption / 100).toInt()).forEach { method ->
                val test = launch {
                        try {
                            println("Running test ${method.name}")
                            val time = measureTimeMillis {
                                testRunner.runTest(method, key)
                            }
                            println("Test ${method.name} finished after ${time}ms")
                            finishedTestsCount += 1
                        } catch (e: Exception) {
                            println(e)
                        } catch (e: NotImplementedError) {
                            println(e)
                        }
                }
                tests.add(test)
            }
            tests.joinAll()
        }
    }

    println("\nALL $finishedTestsCount TESTS FINISHED\n")
}

@OptIn(ExperimentalSerializationApi::class)
fun aggregate() {
    val resultDirname = MainConfig.dataPath
    val resultFilename = "current_dataset.json"
    val schemesFilename = "schemes.json"
    val jsons = mutableListOf<JsonElement>()

    Path(resultDirname, "jsons").toFile().listFiles()?.forEach { file ->
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
        println("NO JSONS FOUND")
        return
    }
    val bigJson = buildJsonObject {
        put("stateScheme", jsons.first().jsonObject
            .getValue("json").jsonObject.getValue("stateScheme"))
        put("trajectoryScheme", jsons.first().jsonObject
            .getValue("json").jsonObject.getValue("trajectoryScheme"))
        putJsonArray("paths") {
            jsons.forEach {
                addJsonArray {
                    add(it.jsonObject.getValue("methodHash"))
                    add(it.jsonObject.getValue("json").jsonObject.getValue("path"))
                    add(it.jsonObject.getValue("methodName"))
                    add(it.jsonObject.getValue("json").jsonObject.getValue("statementsCount"))
                    if (MainConfig.logGraphFeatures) {
                        add(it.jsonObject.getValue("json").jsonObject.getValue("graphFeatures"))
                        add(it.jsonObject.getValue("json").jsonObject.getValue("graphEdges"))
                    }
                    add(it.jsonObject.getValue("json").jsonObject.getValue("probabilities"))
                }
            }
        }
    }
    val schemesJson = buildJsonObject {
        put("stateScheme", jsons.first().jsonObject
            .getValue("json").jsonObject.getValue("stateScheme"))
        put("trajectoryScheme", jsons.first().jsonObject
            .getValue("json").jsonObject.getValue("trajectoryScheme"))
    }

    val resultFile = Path(resultDirname, resultFilename).toFile()
    val schemesFile = Path(resultDirname, schemesFilename).toFile()
    resultFile.parentFile.mkdirs()
    schemesFile.parentFile.mkdirs()
    Json.encodeToStream(bigJson, resultFile.outputStream())
    Json.encodeToStream(schemesJson, schemesFile.outputStream())

    println("\nAGGREGATION FINISHED IN DIRECTORY $resultDirname\n")
}

fun updateConfig(options: JsonObject) {
    MainConfig.samplesPath =
        (options.getOrDefault("samplesPath", JsonPrimitive(MainConfig.samplesPath)) as JsonPrimitive).content
    MainConfig.gameEnvPath =
        (options.getOrDefault("gameEnvPath", JsonPrimitive(MainConfig.gameEnvPath)) as JsonPrimitive).content
    MainConfig.dataPath = (options.getOrDefault("dataPath",
        JsonPrimitive(MainConfig.dataPath)) as JsonPrimitive).content
    MainConfig.defaultAlgorithm = Algorithm.valueOf((options.getOrDefault("defaultAlgorithm",
        JsonPrimitive(MainConfig.defaultAlgorithm.name)) as JsonPrimitive).content)
    MainConfig.postprocessing = Postprocessing.valueOf((options.getOrDefault("postprocessing",
        JsonPrimitive(MainConfig.postprocessing.name)) as JsonPrimitive).content)
    MainConfig.mode = Mode.valueOf((options.getOrDefault("mode",
        JsonPrimitive(MainConfig.mode.name)) as JsonPrimitive).content)
    MainConfig.logFeatures = (options.getOrDefault("logFeatures",
        JsonPrimitive(MainConfig.logFeatures)) as JsonPrimitive).content.toBoolean()
    MainConfig.shuffleTests = (options.getOrDefault("shuffleTests",
        JsonPrimitive(MainConfig.shuffleTests)) as JsonPrimitive).content.toBoolean()
    MainConfig.inputShape = (options.getOrDefault("inputShape", JsonArray(MainConfig.inputShape
        .map { JsonPrimitive(it) })) as JsonArray).map { (it as JsonPrimitive).content.toLong() }
    MainConfig.maxAttentionLength = (options.getOrDefault("maxAttentionLength",
        JsonPrimitive(MainConfig.maxAttentionLength)) as JsonPrimitive).content.toInt()
    MainConfig.useGnn = (options.getOrDefault("useGnn",
        JsonPrimitive(MainConfig.useGnn)) as JsonPrimitive).content.toBoolean()
    MainConfig.dataConsumption = (options.getOrDefault("dataConsumption",
        JsonPrimitive(MainConfig.dataConsumption)) as JsonPrimitive).content.toFloat()
    MainConfig.hardTimeLimit = (options.getOrDefault("hardTimeLimit",
        JsonPrimitive(MainConfig.hardTimeLimit)) as JsonPrimitive).content.toInt()
    MainConfig.solverTimeLimit = (options.getOrDefault("solverTimeLimit",
        JsonPrimitive(MainConfig.solverTimeLimit)) as JsonPrimitive).content.toInt()
    MainConfig.maxConcurrency = (options.getOrDefault("maxConcurrency",
        JsonPrimitive(MainConfig.maxConcurrency)) as JsonPrimitive).content.toInt()
    MainConfig.graphUpdate = GraphUpdate.valueOf((options.getOrDefault("graphUpdate",
        JsonPrimitive(MainConfig.graphUpdate.name)) as JsonPrimitive).content)
    MainConfig.logGraphFeatures = (options.getOrDefault("logGraphFeatures",
        JsonPrimitive(MainConfig.logGraphFeatures)) as JsonPrimitive).content.toBoolean()
    MainConfig.gnnFeaturesCount = (options.getOrDefault("gnnFeaturesCount",
        JsonPrimitive(MainConfig.gnnFeaturesCount)) as JsonPrimitive).content.toInt()
    MainConfig.useRnn = (options.getOrDefault("useRnn",
        JsonPrimitive(MainConfig.useRnn)) as JsonPrimitive).content.toBoolean()
    MainConfig.rnnStateShape = (options.getOrDefault("rnnStateShape", JsonArray(MainConfig.rnnStateShape
        .map { JsonPrimitive(it) })) as JsonArray).map { (it as JsonPrimitive).content.toLong() }
    MainConfig.rnnFeaturesCount = (options.getOrDefault("rnnFeaturesCount",
        JsonPrimitive(MainConfig.rnnFeaturesCount)) as JsonPrimitive).content.toInt()
    MainConfig.inputJars = (options.getOrDefault("inputJars",
        JsonObject(MainConfig.inputJars.mapValues {
            (_, value) -> JsonArray(value.map { JsonPrimitive(it) })
        })) as JsonObject).mapValues {
            (_, value) -> (value as JsonArray).toList().map { (it as JsonPrimitive).content }
        }

    println("OPTIONS:")
    println("  SAMPLES PATH: ${MainConfig.samplesPath}")
    println("  GAME ENV PATH: ${MainConfig.gameEnvPath}")
    println("  DATA PATH: ${MainConfig.dataPath}")
    println("  DEFAULT ALGORITHM: ${MainConfig.defaultAlgorithm}")
    println("  POSTPROCESSING: ${MainConfig.postprocessing}")
    println("  MODE: ${MainConfig.mode}")
    println("  LOG FEATURES: ${MainConfig.logFeatures}")
    println("  SHUFFLE TESTS: ${MainConfig.shuffleTests}")
    println("  INPUT SHAPE: ${MainConfig.inputShape}")
    println("  MAX ATTENTION LENGTH: ${MainConfig.maxAttentionLength}")
    println("  USE GNN: ${MainConfig.useGnn}")
    println("  DATA CONSUMPTION: ${MainConfig.dataConsumption}%")
    println("  HARD TIME LIMIT: ${MainConfig.hardTimeLimit}ms")
    println("  SOLVER TIME LIMIT: ${MainConfig.solverTimeLimit}ms")
    println("  MAX CONCURRENCY: ${MainConfig.maxConcurrency}")
    println("  GRAPH UPDATE: ${MainConfig.graphUpdate}")
    println("  LOG GRAPH FEATURES: ${MainConfig.logGraphFeatures}")
    println("  GNN FEATURES COUNT: ${MainConfig.gnnFeaturesCount}")
    println("  USE RNN: ${MainConfig.useRnn}")
    println("  RNN STATE SHAPE: ${MainConfig.rnnStateShape}")
    println("  RNN FEATURES COUNT: ${MainConfig.rnnFeaturesCount}")
    println("  INPUT JARS: ${MainConfig.inputJars}")
    println()
}

fun clear() {
    Path(MainConfig.dataPath, "jsons").toFile().listFiles()?.forEach { file ->
        file.delete()
    }
}

fun main(args: Array<String>) {
    val options = args.getOrNull(0)?.let { File(it) }?.readText()?.let {
        Json.decodeFromString<JsonObject>(it)
    }
    if (options != null) {
        updateConfig(options)
    }

    clear()

    if (MainConfig.mode == Mode.Calculation || MainConfig.mode == Mode.Both) {
        try {
            calculate()
        } catch (e: Throwable) {
            println(e)
            clear()
        }
    }

    if (MainConfig.mode == Mode.Aggregation || MainConfig.mode == Mode.Both) {
        try {
            aggregate()
        } catch (e: Throwable) {
            println(e)
            clear()
        }
    }
}
