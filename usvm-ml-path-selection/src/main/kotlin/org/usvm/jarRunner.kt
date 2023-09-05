package org.usvm

import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.packageName
import org.usvm.machine.OtherJcMachine
import org.usvm.ps.FeatureLoggingPathSelector
import org.usvm.samples.OtherJacoDBContainer
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.system.measureTimeMillis

fun jarLoad(jars: Set<String>, classes: MutableMap<String, MutableList<JcClassOrInterface>>) {
    jars.forEach { filePath ->
        val file = Path(filePath).toFile()
        val container = OtherJacoDBContainer(key = filePath, classpath = listOf(file))
        val classNames = container.db.locations.flatMap { it.jcLocation?.classNames ?: listOf() }
        classes[filePath] = mutableListOf()
        classNames.forEach { className ->
            container.cp.findClassOrNull(className)?.let {
                classes[filePath]?.add(it)
            }
        }
    }
}

private class MainTestRunner(
    pathSelectionStrategies: List<OtherPathSelectionStrategy> = listOf(OtherPathSelectionStrategy.MACHINE_LEARNING),
    timeoutMs: Long? = 20000
) {
    var options = OtherUMachineOptions().copy(
        exceptionsPropagation = false,
        timeoutMs = timeoutMs,
        stepLimit = 1500u,
        pathSelectionStrategies = pathSelectionStrategies,
        solverType = SolverType.Z3
    )

    fun runTest(method: JcMethod, jarKey: String) {
        OtherJcMachine(OtherJacoDBContainer(jarKey).cp, options).use { jcMachine ->
            jcMachine.analyze(method)
        }
    }
}

fun getName(method: JcMethod): String {
    return method.toString().dropWhile { it != ')' }.drop(1)
}

private val prettyJson = Json { prettyPrint = true }

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
fun calculate() {
    val pathSelectorSets = if (MLConfig.mode == Mode.Test)
        listOf(
            listOf(OtherPathSelectionStrategy.MACHINE_LEARNING),
            listOf(OtherPathSelectionStrategy.FEATURE_LOGGING),
        )
    else listOf(listOf(OtherPathSelectionStrategy.MACHINE_LEARNING))
    val timeLimits = if (MLConfig.mode == Mode.Test)
        listOf<Long?>(
            1000,
            5000,
            20000,
        )
    else listOf<Long?>(20000)

    val jarClasses = mutableMapOf<String, MutableList<JcClassOrInterface>>()
    jarLoad(MLConfig.inputJars.keys, jarClasses)
    println("\nLOADING COMPLETE\n")

    val blacklist = Path(MLConfig.gameEnvPath, "blacklist.txt").toFile().let {
        it.createNewFile()
        it.readLines()
    }

    val tests = mutableListOf<Job>()
    var finishedTestsCount = 0

    jarClasses.forEach { (key, classesList) ->
        println("RUNNING TESTS FOR $key")
        val allMethods = classesList.filter { cls ->
            !cls.isAnnotation && !cls.isInterface &&
                    MLConfig.inputJars.getValue(key).any { cls.packageName.contains(it) } &&
                    !cls.name.contains("Test")
        }.flatMap { cls ->
            cls.declaredMethods.filter { method ->
                method.enclosingClass == cls && getName(method) !in blacklist && !method.isConstructor
            }
        }.sortedBy { getName(it).hashCode() }.distinctBy { getName(it) }
        val orderedMethods = if (MLConfig.shuffleTests) allMethods.shuffled() else allMethods

        timeLimits.forEach { timeLimit ->
            println("  RUNNING TESTS WITH ${timeLimit}ms TIME LIMIT")
            pathSelectorSets.forEach { pathSelectors ->
                println("    RUNNING TESTS WITH ${pathSelectors.joinToString("|")} PATH SELECTOR")
                val statisticsFile = Path(
                    MLConfig.dataPath,
                    "statistics",
                    Path(key).nameWithoutExtension,
                    "${timeLimit}ms",
                    "${pathSelectors.joinToString(separator = "|") { it.toString() }}.json"
                ).toFile()
                statisticsFile.parentFile.mkdirs()
                statisticsFile.createNewFile()
                statisticsFile.writeText("")

                val testRunner = MainTestRunner(pathSelectors, timeLimit)
                runBlocking(Dispatchers.IO.limitedParallelism(MLConfig.maxConcurrency)) {
                    orderedMethods.take((orderedMethods.size * MLConfig.dataConsumption / 100).toInt())
                        .forEach { method ->
                            val test = launch {
                                try {
                                    println("      Running test ${method.name}")
                                    val time = measureTimeMillis {
                                        testRunner.runTest(method, key)
                                    }
                                    println("      Test ${method.name} finished after ${time}ms")
                                    finishedTestsCount += 1
                                } catch (e: Exception) {
                                    println("      $e")
                                } catch (e: NotImplementedError) {
                                    println("      $e")
                                }
                            }
                            tests.add(test)
                        }
                    tests.joinAll()
                }

                prettyJson.encodeToStream(CoverageCounter.getStatistics(), statisticsFile.outputStream())
                CoverageCounter.reset()
            }
        }
    }

    println("\nALL $finishedTestsCount TESTS FINISHED\n")
}

@OptIn(ExperimentalSerializationApi::class)
fun aggregate() {
    val resultDirname = MLConfig.dataPath
    val resultFilename = "current_dataset.json"
    val schemesFilename = "schemes.json"
    val jsons = mutableListOf<JsonElement>()

    val schemesJson = buildJsonObject {
        put("stateScheme", FeatureLoggingPathSelector.jsonStateScheme)
        put("trajectoryScheme", FeatureLoggingPathSelector.jsonTrajectoryScheme)
    }
    val schemesFile = Path(resultDirname, schemesFilename).toFile()
    schemesFile.parentFile.mkdirs()
    prettyJson.encodeToStream(schemesJson, schemesFile.outputStream())

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
        put(
            "stateScheme", jsons.first().jsonObject
                .getValue("json").jsonObject.getValue("stateScheme")
        )
        put(
            "trajectoryScheme", jsons.first().jsonObject
                .getValue("json").jsonObject.getValue("trajectoryScheme")
        )
        putJsonArray("paths") {
            jsons.forEach {
                addJsonArray {
                    add(it.jsonObject.getValue("methodHash"))
                    add(it.jsonObject.getValue("json").jsonObject.getValue("path"))
                    add(it.jsonObject.getValue("methodName"))
                    add(it.jsonObject.getValue("json").jsonObject.getValue("statementsCount"))
                    if (MLConfig.logGraphFeatures) {
                        add(it.jsonObject.getValue("json").jsonObject.getValue("graphFeatures"))
                        add(it.jsonObject.getValue("json").jsonObject.getValue("graphEdges"))
                    }
                    add(it.jsonObject.getValue("json").jsonObject.getValue("probabilities"))
                }
            }
        }
    }

    val resultFile = Path(resultDirname, resultFilename).toFile()
    resultFile.parentFile.mkdirs()
    Json.encodeToStream(bigJson, resultFile.outputStream())

    println("\nAGGREGATION FINISHED IN DIRECTORY $resultDirname\n")
}

fun updateConfig(options: JsonObject) {
    MLConfig.gameEnvPath =
        (options.getOrDefault("gameEnvPath", JsonPrimitive(MLConfig.gameEnvPath)) as JsonPrimitive).content
    MLConfig.dataPath = (options.getOrDefault(
        "dataPath",
        JsonPrimitive(MLConfig.dataPath)
    ) as JsonPrimitive).content
    MLConfig.defaultAlgorithm = Algorithm.valueOf(
        (options.getOrDefault(
            "defaultAlgorithm",
            JsonPrimitive(MLConfig.defaultAlgorithm.name)
        ) as JsonPrimitive).content
    )
    MLConfig.postprocessing = Postprocessing.valueOf(
        (options.getOrDefault(
            "postprocessing",
            JsonPrimitive(MLConfig.postprocessing.name)
        ) as JsonPrimitive).content
    )
    MLConfig.mode = Mode.valueOf(
        (options.getOrDefault(
            "mode",
            JsonPrimitive(MLConfig.mode.name)
        ) as JsonPrimitive).content
    )
    MLConfig.logFeatures = (options.getOrDefault(
        "logFeatures",
        JsonPrimitive(MLConfig.logFeatures)
    ) as JsonPrimitive).content.toBoolean()
    MLConfig.shuffleTests = (options.getOrDefault(
        "shuffleTests",
        JsonPrimitive(MLConfig.shuffleTests)
    ) as JsonPrimitive).content.toBoolean()
    MLConfig.discounts = (options.getOrDefault(
        "discounts", JsonArray(
            MLConfig.discounts
                .map { JsonPrimitive(it) })
    ) as JsonArray).map { (it as JsonPrimitive).content.toFloat() }
    MLConfig.inputShape = (options.getOrDefault(
        "inputShape", JsonArray(
            MLConfig.inputShape
                .map { JsonPrimitive(it) })
    ) as JsonArray).map { (it as JsonPrimitive).content.toLong() }
    MLConfig.maxAttentionLength = (options.getOrDefault(
        "maxAttentionLength",
        JsonPrimitive(MLConfig.maxAttentionLength)
    ) as JsonPrimitive).content.toInt()
    MLConfig.useGnn = (options.getOrDefault(
        "useGnn",
        JsonPrimitive(MLConfig.useGnn)
    ) as JsonPrimitive).content.toBoolean()
    MLConfig.dataConsumption = (options.getOrDefault(
        "dataConsumption",
        JsonPrimitive(MLConfig.dataConsumption)
    ) as JsonPrimitive).content.toFloat()
    MLConfig.hardTimeLimit = (options.getOrDefault(
        "hardTimeLimit",
        JsonPrimitive(MLConfig.hardTimeLimit)
    ) as JsonPrimitive).content.toInt()
    MLConfig.solverTimeLimit = (options.getOrDefault(
        "solverTimeLimit",
        JsonPrimitive(MLConfig.solverTimeLimit)
    ) as JsonPrimitive).content.toInt()
    MLConfig.maxConcurrency = (options.getOrDefault(
        "maxConcurrency",
        JsonPrimitive(MLConfig.maxConcurrency)
    ) as JsonPrimitive).content.toInt()
    MLConfig.graphUpdate = GraphUpdate.valueOf(
        (options.getOrDefault(
            "graphUpdate",
            JsonPrimitive(MLConfig.graphUpdate.name)
        ) as JsonPrimitive).content
    )
    MLConfig.logGraphFeatures = (options.getOrDefault(
        "logGraphFeatures",
        JsonPrimitive(MLConfig.logGraphFeatures)
    ) as JsonPrimitive).content.toBoolean()
    MLConfig.gnnFeaturesCount = (options.getOrDefault(
        "gnnFeaturesCount",
        JsonPrimitive(MLConfig.gnnFeaturesCount)
    ) as JsonPrimitive).content.toInt()
    MLConfig.useRnn = (options.getOrDefault(
        "useRnn",
        JsonPrimitive(MLConfig.useRnn)
    ) as JsonPrimitive).content.toBoolean()
    MLConfig.rnnStateShape = (options.getOrDefault(
        "rnnStateShape", JsonArray(
            MLConfig.rnnStateShape
                .map { JsonPrimitive(it) })
    ) as JsonArray).map { (it as JsonPrimitive).content.toLong() }
    MLConfig.rnnFeaturesCount = (options.getOrDefault(
        "rnnFeaturesCount",
        JsonPrimitive(MLConfig.rnnFeaturesCount)
    ) as JsonPrimitive).content.toInt()
    MLConfig.inputJars = (options.getOrDefault("inputJars",
        JsonObject(MLConfig.inputJars.mapValues { (_, value) ->
            JsonArray(value.map { JsonPrimitive(it) })
        })
    ) as JsonObject).mapValues { (_, value) ->
        (value as JsonArray).toList().map { (it as JsonPrimitive).content }
    }

    println("OPTIONS:")
    println("  GAME ENV PATH: ${MLConfig.gameEnvPath}")
    println("  DATA PATH: ${MLConfig.dataPath}")
    println("  DEFAULT ALGORITHM: ${MLConfig.defaultAlgorithm}")
    println("  POSTPROCESSING: ${MLConfig.postprocessing}")
    println("  MODE: ${MLConfig.mode}")
    println("  LOG FEATURES: ${MLConfig.logFeatures}")
    println("  SHUFFLE TESTS: ${MLConfig.shuffleTests}")
    println("  INPUT SHAPE: ${MLConfig.inputShape}")
    println("  MAX ATTENTION LENGTH: ${MLConfig.maxAttentionLength}")
    println("  USE GNN: ${MLConfig.useGnn}")
    println("  DATA CONSUMPTION: ${MLConfig.dataConsumption}%")
    println("  HARD TIME LIMIT: ${MLConfig.hardTimeLimit}ms")
    println("  SOLVER TIME LIMIT: ${MLConfig.solverTimeLimit}ms")
    println("  MAX CONCURRENCY: ${MLConfig.maxConcurrency}")
    println("  GRAPH UPDATE: ${MLConfig.graphUpdate}")
    println("  LOG GRAPH FEATURES: ${MLConfig.logGraphFeatures}")
    println("  GNN FEATURES COUNT: ${MLConfig.gnnFeaturesCount}")
    println("  USE RNN: ${MLConfig.useRnn}")
    println("  RNN STATE SHAPE: ${MLConfig.rnnStateShape}")
    println("  RNN FEATURES COUNT: ${MLConfig.rnnFeaturesCount}")
    println("  INPUT JARS: ${MLConfig.inputJars}")
    println()
}

fun clear() {
    Path(MLConfig.dataPath, "jsons").toFile().listFiles()?.forEach { file ->
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

    if (MLConfig.mode != Mode.Aggregation) {
        clear()
    }

    if (MLConfig.mode in listOf(Mode.Calculation, Mode.Both, Mode.Test)) {
        try {
            calculate()
        } catch (e: Throwable) {
            e.printStackTrace()
            clear()
        }
    }

    if (MLConfig.mode in listOf(Mode.Aggregation, Mode.Both)) {
        try {
            aggregate()
        } catch (e: Throwable) {
            e.printStackTrace()
            clear()
        }
    }
}
