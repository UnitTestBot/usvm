package org.usvm.dataflow.ts.test

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsStringConstant
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.convertToEtsFile
import org.jacodb.ets.graph.EtsApplicationGraphImpl
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsApplicationGraphWithExplicitEntryPoint
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.test.utils.loadEtsFileFromResource
import org.usvm.dataflow.ts.util.CONSTRUCTOR
import org.usvm.dataflow.ts.util.EtsTraits
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.relativeTo
import kotlin.io.path.toPath
import kotlin.io.path.walk
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

@OptIn(ExperimentalPathApi::class)
class EtsTypeInferenceTest {

    companion object {
        private fun load(path: String): EtsFile {
            return loadEtsFileFromResource("/$path")
        }
    }

    @Test
    fun `type inference for microphone`() {
        val name = "microphone"
        val file = load("ir/$name.ts.json")
        // val file = load("abcir/$name.abc.json")
        val project = EtsScene(listOf(file))
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }
        val types = manager.analyze(entrypoints)

        run {
            val m = types.keys.first { it.name == "getMicrophoneUuid" }

            // arg0 = 'devices'
            val devices = types[m]!!.types[AccessPathBase.Arg(0)]!!
            assertIs<EtsTypeFact.ObjectEtsTypeFact>(devices)

            val devicesCls = devices.cls
            assertEquals("VirtualDevices", devicesCls?.typeName)

            assertContains(devices.properties, "microphone")
            val microphone = devices.properties["microphone"]!!
            assertIs<EtsTypeFact.ObjectEtsTypeFact>(microphone)

            assertContains(microphone.properties, "uuid")
            val uuid = microphone.properties["uuid"]!!
            assertIs<EtsTypeFact.StringEtsTypeFact>(uuid)
        }
    }

    @Test
    fun `type inference for types`() {
        val name = "types"
        val file = load("ir/$name.ts.json")
        val project = EtsScene(listOf(file))
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }
        manager.analyze(entrypoints)
    }

    @Test
    fun `type inference for data`() {
        val name = "data"
        val file = load("ir/$name.ts.json")
        val project = EtsScene(listOf(file))
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }
        manager.analyze(entrypoints)
    }

    @Test
    fun `type inference for call`() {
        val name = "call"
        val file = load("ir/$name.ts.json")
        val project = EtsScene(listOf(file))
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }
        manager.analyze(entrypoints)
    }

    @Test
    fun `type inference for nested_init`() {
        val name = "nested_init"
        val file = load("ir/$name.ts.json")
        val project = EtsScene(listOf(file))
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }
        manager.analyze(entrypoints)
    }

    @Disabled("EtsIR-ABC is outdated")
    @Test
    fun `type inference for cast ABC`() {
        val name = "cast"
        val file = load("abcir/$name.abc.json")
        val project = EtsScene(listOf(file))
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }
        manager.analyze(entrypoints)
    }

    @Test
    fun `type inference for applications_settings_data`() {
        val res = "/projects/applications_settings_data/etsir/ast"
        val dir = object {}::class.java.getResource(res)?.toURI()?.toPath()
            ?: error("Resource not found: $res")
        println("Found project dir: '$dir'")

        val files = dir
            .walk()
            .map { convertToEtsFile(EtsFileDto.loadFromJson(it.inputStream())) }
            .toList()
        val project = EtsScene(files)
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = project.classes
            .asSequence()
            .flatMap { it.methods + it.ctor }
            .filter { it.isPublic || it.name == "constructor" }
            .toList()
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }
        val inferred = manager.analyze(entrypoints)

        run {
            val m = inferred.keys.first { it.name == "loadTableData" }

            val arg0 = inferred[m]!!.types[AccessPathBase.Arg(0)]!!
            assertIs<EtsTypeFact.ObjectEtsTypeFact>(arg0)

            assertContains(arg0.properties, "user")
            assertContains(arg0.properties, "userSecure")
            assertContains(arg0.properties, "settings")

            val user = arg0.properties["user"]!!
            assertIs<EtsTypeFact.ObjectEtsTypeFact>(user)
            val userProps = user.properties
            assertContains(userProps, "index")
            assertContains(userProps, "length")

            val userSecure = arg0.properties["userSecure"]!!
            assertIs<EtsTypeFact.ObjectEtsTypeFact>(userSecure)
            val userSecureProps = userSecure.properties
            assertContains(userSecureProps, "index")
            assertContains(userSecureProps, "length")

            val settings = arg0.properties["settings"]!!
            assertIs<EtsTypeFact.ObjectEtsTypeFact>(settings)
            val settingsProps = settings.properties
            assertContains(settingsProps, "index")
            assertContains(settingsProps, "length")
        }

        // val objects = inferred.values
        //     .asSequence()
        //     .flatMap { it.types.values.asSequence() }
        //     .filterIsInstance<EtsTypeFact.ObjectEtsTypeFact>()
        //     .toSet()
        // println()
        // println("Objects: (${objects.size})")
        // for (obj in objects) {
        //     println("obj = $obj")
        // }
        //
        // println()
        // println("Classes: (${arkFile.classes.size})")
        // for (clazz in arkFile.classes) {
        //     println("clazz '${clazz.name}'")
        //     println("  ${clazz.fields.size} fields: ${clazz.fields.map { it.name }}")
        //     println("  ${clazz.methods.size} methods: ${clazz.methods.map { it.name }}")
        // }
    }

    @Test
    fun `type inference for Calc`() {
        val resource = "/projects/applications_app_samples/etsir/ast/ArkTSDistributedCalc"
        val dir = object {}::class.java.getResource(resource)?.toURI()?.toPath()
            ?: error("Resource not found: $resource")
        println("Found project dir: '$dir'")

        val files = dir
            .walk()
            .filter { it.extension == "json" }
            // .filter { it.name.startsWith("Calculator") }
            // .filter { it.name.startsWith("ImageList") }
            // .filter { it.name.startsWith("KvStoreModel") }
            // .filter { it.name.startsWith("RemoteDeviceModel") }
            // .filter { it.name.startsWith("Index") }
            .toList()
        println("Found files: (${files.size})")
        for (path in files) {
            println("  ${path.relativeTo(dir)}")
        }

        println("Processing ${files.size} files...")
        val etsFiles = files.map { convertToEtsFile(EtsFileDto.loadFromJson(it.inputStream())) }
        val project = EtsScene(etsFiles)

        val graphOrig = EtsApplicationGraphImpl(project)
        val graph = EtsApplicationGraphWithExplicitEntryPoint(graphOrig)

        val entrypoints = project.classes
            .flatMap { it.methods + it.ctor }
            // .filter { it.enclosingClass.name == "Index" && (it.name == "build" || it.name == CONSTRUCTOR) }
            .filter { it.isPublic || it.name == CONSTRUCTOR }
            .filter { !it.enclosingClass.name.startsWith("AnonymousClass") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }
        val inferred = manager.analyze(entrypoints)
    }

    private fun resourceAvailable(dirName: String) =
        object {}::class.java.getResource(dirName) != null

    private fun testHapSet(setPath: String) {
        val abcDir = object {}::class.java.getResource(setPath)?.toURI()?.toPath()
            ?: error("Resource not found: $setPath")
        val haps = abcDir.toFile().listFiles()?.toList() ?: emptyList()
        processAllHAPs(haps)
    }

    private val APP_SAMPLES_PATH = "/projects/applications_app_samples/etsir/abc/"
    private fun appSamplesAvailable() = resourceAvailable(APP_SAMPLES_PATH)

    @Test
    @EnabledIf("appSamplesAvailable")
    fun `type inference for app samples`() = testHapSet(APP_SAMPLES_PATH)


    private val TEST_PROJECTS_PATH = "/TestProjects/"
    private fun testProjectsAvailable() = resourceAvailable(TEST_PROJECTS_PATH)

    @Test
    @EnabledIf("testProjectsAvailable")
    fun `type inference for test projects`() = testHapSet(TEST_PROJECTS_PATH)

    @Test
    fun `test single HAP`() {
        val abcDirName = "/TestProjects/CertificateManager_240801_843398b"
        val projectDir = object {}::class.java.getResource(abcDirName)?.toURI()?.toPath()
            ?: error("Resource not found: $abcDirName")
        testHap(projectDir.toString())
    }

    private fun processAllHAPs(haps: Collection<File>) {
        val succeed = mutableListOf<String>()
        val timeout = mutableListOf<String>()
        val failed = mutableListOf<String>()

        haps.forEach { project ->
            try {
                runBlocking {
                    withTimeout(60_000) {
                        runInterruptible { testHap(project.path) }
                    }
                }
                succeed += project.name
                println("$project  -  SUCCESS")
            } catch (_: TimeoutCancellationException) {
                timeout += project.name
                println("$project  -  TIMEOUT")
            } catch (e: SerializationException) {
                e.printStackTrace()
                error("Serialization exception")
            } catch (e: Throwable) {
                failed += project.name
                println("$project  -  FAILED")
                e.printStackTrace()
            }
            println("%%%")
            println(project.name)
            println("%%%")
        }

        println("Total: ${haps.size} HAPs")
        println("Succeed: ${succeed.size}")
        println("Timeout: ${timeout.size}")
        println("Failed: ${failed.size}")

        println("PASSED")
        succeed.forEach {
            println(it)
        }
        println("TIMEOUT")
        timeout.forEach {
            println(it)
        }
        println("FAILED")
        failed.forEach {
            println(it)
        }
    }

    private fun testHap(projectDir: String) {
        //val resource = "/projects/applications_app_samples/etsir/abc/$projectName"
        //val dir = object {}::class.java.getResource(resource)?.toURI()?.toPath()
        //    ?: error("Resource not found: $resource")

        val dir = File(projectDir).takeIf { it.isDirectory } ?: error("Not found project dir $projectDir")
        println("Found project dir: '$dir'")

        val files = dir
            .walk()
            .filter { it.extension == "json" }
            // .filter { it.name.startsWith("Calculator") }
            // .filter { it.name.startsWith("ImageList") }
            // .filter { it.name.startsWith("KvStoreModel") }
            // .filter { it.name.startsWith("RemoteDeviceModel") }
            // .filter { it.name.startsWith("Index") }
            .toList()
        println("Found files: (${files.size})")
        for (path in files) {
            println("  ${path.relativeTo(dir)}")
        }

        println("Processing ${files.size} files...")
        val etsFiles = files.map { convertToEtsFile(EtsFileDto.loadFromJson(it.inputStream())) }
        val project = EtsScene(etsFiles)

        val graphOrig = EtsApplicationGraphImpl(project)
        val graph = EtsApplicationGraphWithExplicitEntryPoint(graphOrig)

        val entrypoints = project.classes
            .flatMap { it.methods + it.ctor }
            // .filter { it.enclosingClass.name == "Index" && (it.name == "build" || it.name == CONSTRUCTOR) }
            .filter { it.isPublic || it.name == CONSTRUCTOR }
            .filter { !it.enclosingClass.name.startsWith("AnonymousClass") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }
        val inferred = manager.analyze(entrypoints)
    }

    @Test
    fun `test if guesser does anything`() {
        val name = "testcases"
        val file = load("ir/$name.ts.json")
        val project = EtsScene(listOf(file))
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name == "entrypoint" }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }
        val inferredTypesWithoutGuessed = manager.analyze(entrypoints, guessUniqueTypes = false)
        val inferredTypesWithGuessed = manager.analyze(entrypoints, guessUniqueTypes = true)

        assertNotEquals(inferredTypesWithoutGuessed, inferredTypesWithGuessed)

        println("=".repeat(42))
        println("Inferred types WITHOUT guesser: ")
        for (m in inferredTypesWithoutGuessed) {
            println(m.key.enclosingClass.name to m.value.types)
        }

        println("=".repeat(42))
        println("Inferred types with guesser: ")
        for (m in inferredTypesWithGuessed) {
            println(m.key.enclosingClass.name to m.value.types)
        }
    }

    @Test
    fun `type inference for testcases`() {
        val name = "testcases"
        val file = load("ir/$name.ts.json")
        val project = EtsScene(listOf(file))
        val graph = EtsApplicationGraphImpl(project)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name == "entrypoint" }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graphWithExplicitEntryPoint)
        }
        val inferredTypes = manager.analyze(entrypoints)

        val inferMethods = project.classes
            .asSequence()
            .filter { it.name.startsWith("Case") }
            .flatMap { it.methods.asSequence() }
            .filter { it.name == "infer" }
            .toList()

        val expectedTypeString: Map<EtsMethod, Map<AccessPathBase, String>> = inferMethods
            .associateWith {
                val accessPathToValue = mutableMapOf<AccessPathBase, String>()
                for (inst in it.cfg.stmts) {
                    if (inst is EtsAssignStmt) {
                        val lhv = inst.lhv
                        if (lhv is EtsLocal) {
                            val rhv = inst.rhv
                            if (lhv.name == "EXPECTED_ARG_0") {
                                check(rhv is EtsStringConstant)
                                accessPathToValue[AccessPathBase.Arg(0)] = rhv.value
                            }
                            if (lhv.name == "EXPECTED_ARG_1") {
                                check(rhv is EtsStringConstant)
                                accessPathToValue[AccessPathBase.Arg(1)] = rhv.value
                            }
                            if (lhv.name == "EXPECTED_RETURN") {
                                check(rhv is EtsStringConstant)
                                accessPathToValue[AccessPathBase.Return] = rhv.value
                            }
                        }
                    }
                }
                return@associateWith accessPathToValue
            }

        println("=".repeat(42))
        var numOk = 0
        var numBad = 0
        val testResults = mutableListOf<Triple<String, Boolean, Pair<String, String>>>()

        for (m in inferMethods) {
            for (position in listOf(AccessPathBase.Arg(0), AccessPathBase.Arg(1), AccessPathBase.Return)) {
                val expected = (expectedTypeString[m]
                    ?: error("No inferred types for method ${m.enclosingClass.name}::${m.name}"))[position]
                    ?: continue
                val inferred = (inferredTypes[m]
                    ?: error("No inferred types for method ${m.enclosingClass.name}::${m.name}"))
                    .types[position]
                // ?: error("No inferred type for position $position")

                val passed = inferred.toString() == expected
                testResults.add(Triple("${m.enclosingClass.name}::${m.name}", passed, inferred.toString() to expected))

                if (passed) {
                    numOk++
                    println("Correctly inferred type for $position in '${m.enclosingClass.name}::${m.name}': ${inferred?.toPrettyString()}")
                } else {
                    numBad++
                    println("Incorrectly inferred type for $position in '${m.enclosingClass.name}::${m.name}':\n  inferred: $inferred\n  expected: $expected")
                }
            }
        }

        println("numOk = $numOk")
        println("numBad = $numBad")
        println("Success rate: %.1f%%".format(numOk / (numOk + numBad).toDouble() * 100.0))

        exportResultsToCSV(testResults, "test_results.csv")
    }

    private fun exportResultsToCSV(testResults: List<Triple<String, Boolean, Pair<String, String>>>, filePath: String) {
        val file = File(filePath)
        val writer = BufferedWriter(FileWriter(file, false))

        if (file.length() == 0L) {
            writer.write("Name,Status,Inferred,Expected\n")
        }

        testResults.forEach { (testName, passed, inferredExpected) ->
            val (inferred, expected) = inferredExpected
            writer.write("$testName,${if (passed) "Passed" else "Failed"},$inferred,$expected\n")
        }

        writer.close()
    }
}
