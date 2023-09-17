package org.usvm

import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.packageName
import org.usvm.machine.ModifiedJcMachine
import org.usvm.ps.GlobalStateFeatures
import org.usvm.ps.StateFeatures
import org.usvm.samples.JacoDBContainer
import org.usvm.util.getMethodFullName
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.system.measureTimeMillis

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

private class MainTestRunner(
    private val config: MLConfig,
    pathSelectionStrategies: List<ModifiedPathSelectionStrategy> =
        listOf(ModifiedPathSelectionStrategy.MACHINE_LEARNING),
    timeoutMs: Long? = 20000
) {
    val coverageCounter = CoverageCounter(config)

    val options = ModifiedUMachineOptions().copy(
        UMachineOptions().copy(
            exceptionsPropagation = false,
            timeoutMs = timeoutMs,
            stepLimit = 1500u,
            solverType = SolverType.Z3
        ),
        pathSelectionStrategies
    )

    fun runTest(method: JcMethod, jarKey: String) {
        ModifiedJcMachine(JacoDBContainer(jarKey).cp, options).use { jcMachine ->
            jcMachine.analyze(method, emptyList(), coverageCounter, config)
        }
    }
}

private val prettyJson = Json { prettyPrint = true }

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
fun calculate(config: MLConfig) {
    val pathSelectorSets = if (config.mode == Mode.Test)
        listOf(
            listOf(ModifiedPathSelectionStrategy.MACHINE_LEARNING),
            listOf(ModifiedPathSelectionStrategy.FEATURES_LOGGING),
        )
    else listOf(listOf(ModifiedPathSelectionStrategy.MACHINE_LEARNING))
    val timeLimits = if (config.mode == Mode.Test)
        listOf<Long?>(
            1000,
            5000,
            20000,
        )
    else listOf<Long?>(20000)

    val jarClasses = mutableMapOf<String, MutableList<JcClassOrInterface>>()
    jarLoad(config.inputJars.keys, jarClasses)
    println("\nLOADING COMPLETE\n")

    val blacklist = Path(config.gameEnvPath, "blacklist.txt").toFile().let {
        it.createNewFile()
        it.readLines()
    }

    val tests = mutableListOf<Job>()
    var finishedTestsCount = 0

    jarClasses.forEach { (key, classesList) ->
        println("RUNNING TESTS FOR $key")
        val allMethods = classesList.filter { cls ->
            !cls.isAnnotation && !cls.isInterface &&
                    config.inputJars.getValue(key).any { cls.packageName.contains(it) } &&
                    !cls.name.contains("Test")
        }.flatMap { cls ->
            cls.declaredMethods.filter { method ->
                method.enclosingClass == cls && getMethodFullName(method) !in blacklist && !method.isConstructor
            }
        }.sortedBy { getMethodFullName(it).hashCode() }.distinctBy { getMethodFullName(it) }
        val orderedMethods = if (config.shuffleTests) allMethods.shuffled() else allMethods

        timeLimits.forEach { timeLimit ->
            println("  RUNNING TESTS WITH ${timeLimit}ms TIME LIMIT")
            pathSelectorSets.forEach { pathSelectors ->
                println("    RUNNING TESTS WITH ${pathSelectors.joinToString("|")} PATH SELECTOR")
                val statisticsFile = Path(
                    config.dataPath,
                    "statistics",
                    Path(key).nameWithoutExtension,
                    "${timeLimit}ms",
                    "${pathSelectors.joinToString(separator = "|") { it.toString() }}.json"
                ).toFile()
                statisticsFile.parentFile.mkdirs()
                statisticsFile.createNewFile()
                statisticsFile.writeText("")

                val testRunner = MainTestRunner(config, pathSelectors, timeLimit)
                runBlocking(Dispatchers.IO.limitedParallelism(config.maxConcurrency)) {
                    orderedMethods.take((orderedMethods.size * config.dataConsumption / 100).toInt())
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

                prettyJson.encodeToStream(testRunner.coverageCounter.getStatistics(), statisticsFile.outputStream())
                testRunner.coverageCounter.reset()
            }
        }
    }

    println("\nALL $finishedTestsCount TESTS FINISHED\n")
}

fun getJsonSchemes(config: MLConfig): Pair<JsonArray, JsonArray> {
    val jsonFormat = Json {
        encodeDefaults = true
    }
    val jsonStateScheme: JsonArray = buildJsonArray {
        addJsonArray {
            jsonFormat.encodeToJsonElement(StateFeatures()).jsonObject.forEach { t, _ ->
                add(t)
            }
            jsonFormat.encodeToJsonElement(GlobalStateFeatures()).jsonObject.forEach { t, _ ->
                add(t)
            }
            if (config.useGnn) {
                (0 until config.gnnFeaturesCount).forEach {
                    add("gnnFeature$it")
                }
            }
            if (config.useRnn) {
                (0 until config.rnnFeaturesCount).forEach {
                    add("rnnFeature$it")
                }
            }
        }
        add("chosenStateId")
        add("reward")
        if (config.logGraphFeatures) {
            add("graphId")
            add("blockIds")
        }
    }
    val jsonTrajectoryScheme = buildJsonArray {
        add("hash")
        add("trajectory")
        add("name")
        add("statementsCount")
        if (config.logGraphFeatures) {
            add("graphFeatures")
            add("graphEdges")
        }
        add("probabilities")
    }
    return Pair(jsonStateScheme, jsonTrajectoryScheme)
}

@OptIn(ExperimentalSerializationApi::class)
fun aggregate(config: MLConfig) {
    val resultDirname = config.dataPath
    val resultFilename = "current_dataset.json"
    val schemesFilename = "schemes.json"
    val jsons = mutableListOf<JsonElement>()

    val (jsonStateScheme, jsonTrajectoryScheme) = getJsonSchemes(config)
    val schemesJson = buildJsonObject {
        put("stateScheme", jsonStateScheme)
        put("trajectoryScheme", jsonTrajectoryScheme)
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
                    if (config.logGraphFeatures) {
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

fun createConfig(options: JsonObject): MLConfig {
    val defaultConfig = MLConfig()
    val config = MLConfig(
        gameEnvPath = (options.getOrDefault(
            "gameEnvPath",
            JsonPrimitive(defaultConfig.gameEnvPath)
        ) as JsonPrimitive).content,
        dataPath = (options.getOrDefault(
            "dataPath",
            JsonPrimitive(defaultConfig.dataPath)
        ) as JsonPrimitive).content,
        defaultAlgorithm = Algorithm.valueOf(
            (options.getOrDefault(
                "defaultAlgorithm",
                JsonPrimitive(defaultConfig.defaultAlgorithm.name)
            ) as JsonPrimitive).content
        ),
        postprocessing = Postprocessing.valueOf(
            (options.getOrDefault(
                "postprocessing",
                JsonPrimitive(defaultConfig.postprocessing.name)
            ) as JsonPrimitive).content
        ),
        mode = Mode.valueOf(
            (options.getOrDefault(
                "mode",
                JsonPrimitive(defaultConfig.mode.name)
            ) as JsonPrimitive).content
        ),
        logFeatures = (options.getOrDefault(
            "logFeatures",
            JsonPrimitive(defaultConfig.logFeatures)
        ) as JsonPrimitive).content.toBoolean(),
        shuffleTests = (options.getOrDefault(
            "shuffleTests",
            JsonPrimitive(defaultConfig.shuffleTests)
        ) as JsonPrimitive).content.toBoolean(),
        discounts = (options.getOrDefault(
            "discounts", JsonArray(
                defaultConfig.discounts
                    .map { JsonPrimitive(it) })
        ) as JsonArray).map { (it as JsonPrimitive).content.toFloat() },
        inputShape = (options.getOrDefault(
            "inputShape", JsonArray(
                defaultConfig.inputShape
                    .map { JsonPrimitive(it) })
        ) as JsonArray).map { (it as JsonPrimitive).content.toLong() },
        maxAttentionLength = (options.getOrDefault(
            "maxAttentionLength",
            JsonPrimitive(defaultConfig.maxAttentionLength)
        ) as JsonPrimitive).content.toInt(),
        useGnn = (options.getOrDefault(
            "useGnn",
            JsonPrimitive(defaultConfig.useGnn)
        ) as JsonPrimitive).content.toBoolean(),
        dataConsumption = (options.getOrDefault(
            "dataConsumption",
            JsonPrimitive(defaultConfig.dataConsumption)
        ) as JsonPrimitive).content.toFloat(),
        hardTimeLimit = (options.getOrDefault(
            "hardTimeLimit",
            JsonPrimitive(defaultConfig.hardTimeLimit)
        ) as JsonPrimitive).content.toInt(),
        solverTimeLimit = (options.getOrDefault(
            "solverTimeLimit",
            JsonPrimitive(defaultConfig.solverTimeLimit)
        ) as JsonPrimitive).content.toInt(),
        maxConcurrency = (options.getOrDefault(
            "maxConcurrency",
            JsonPrimitive(defaultConfig.maxConcurrency)
        ) as JsonPrimitive).content.toInt(),
        graphUpdate = GraphUpdate.valueOf(
            (options.getOrDefault(
                "graphUpdate",
                JsonPrimitive(defaultConfig.graphUpdate.name)
            ) as JsonPrimitive).content
        ),
        logGraphFeatures = (options.getOrDefault(
            "logGraphFeatures",
            JsonPrimitive(defaultConfig.logGraphFeatures)
        ) as JsonPrimitive).content.toBoolean(),
        gnnFeaturesCount = (options.getOrDefault(
            "gnnFeaturesCount",
            JsonPrimitive(defaultConfig.gnnFeaturesCount)
        ) as JsonPrimitive).content.toInt(),
        useRnn = (options.getOrDefault(
            "useRnn",
            JsonPrimitive(defaultConfig.useRnn)
        ) as JsonPrimitive).content.toBoolean(),
        rnnStateShape = (options.getOrDefault(
            "rnnStateShape", JsonArray(
                defaultConfig.rnnStateShape
                    .map { JsonPrimitive(it) })
        ) as JsonArray).map { (it as JsonPrimitive).content.toLong() },
        rnnFeaturesCount = (options.getOrDefault(
            "rnnFeaturesCount",
            JsonPrimitive(defaultConfig.rnnFeaturesCount)
        ) as JsonPrimitive).content.toInt(),
        inputJars = (options.getOrDefault(
            "inputJars",
            JsonObject(defaultConfig.inputJars.mapValues { (_, value) ->
                JsonArray(value.map { JsonPrimitive(it) })
            })
        ) as JsonObject).mapValues { (_, value) ->
            (value as JsonArray).toList().map { (it as JsonPrimitive).content }
        }
    )

    println("OPTIONS:")
    println("  GAME ENV PATH: ${config.gameEnvPath}")
    println("  DATA PATH: ${config.dataPath}")
    println("  DEFAULT ALGORITHM: ${config.defaultAlgorithm}")
    println("  POSTPROCESSING: ${config.postprocessing}")
    println("  MODE: ${config.mode}")
    println("  LOG FEATURES: ${config.logFeatures}")
    println("  SHUFFLE TESTS: ${config.shuffleTests}")
    println("  INPUT SHAPE: ${config.inputShape}")
    println("  MAX ATTENTION LENGTH: ${config.maxAttentionLength}")
    println("  USE GNN: ${config.useGnn}")
    println("  DATA CONSUMPTION: ${config.dataConsumption}%")
    println("  HARD TIME LIMIT: ${config.hardTimeLimit}ms")
    println("  SOLVER TIME LIMIT: ${config.solverTimeLimit}ms")
    println("  MAX CONCURRENCY: ${config.maxConcurrency}")
    println("  GRAPH UPDATE: ${config.graphUpdate}")
    println("  LOG GRAPH FEATURES: ${config.logGraphFeatures}")
    println("  GNN FEATURES COUNT: ${config.gnnFeaturesCount}")
    println("  USE RNN: ${config.useRnn}")
    println("  RNN STATE SHAPE: ${config.rnnStateShape}")
    println("  RNN FEATURES COUNT: ${config.rnnFeaturesCount}")
    println("  INPUT JARS: ${config.inputJars}")
    println()

    return config
}

fun clear(dataPath: String) {
    Path(dataPath, "jsons").toFile().listFiles()?.forEach { file ->
        file.delete()
    }
}

fun main(args: Array<String>) {
    val options = args.getOrNull(0)?.let { File(it) }?.readText()?.let {
        Json.decodeFromString<JsonObject>(it)
    }
    val config = if (options != null) {
        createConfig(options)
    } else {
        MLConfig()
    }

    if (config.mode != Mode.Aggregation) {
        clear(config.dataPath)
    }

    if (config.mode in listOf(Mode.Calculation, Mode.Both, Mode.Test)) {
        try {
            calculate(config)
        } catch (e: Throwable) {
            e.printStackTrace()
            clear(config.dataPath)
        }
    }

    if (config.mode in listOf(Mode.Aggregation, Mode.Both)) {
        try {
            aggregate(config)
        } catch (e: Throwable) {
            e.printStackTrace()
            clear(config.dataPath)
        }
    }
}
