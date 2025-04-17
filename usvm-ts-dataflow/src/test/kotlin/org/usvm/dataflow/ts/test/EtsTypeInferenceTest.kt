/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.usvm.dataflow.ts.test

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import mu.KotlinLogging
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.toEtsFile
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.getLocals
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.EnabledIf
import org.usvm.dataflow.ts.getResourcePath
import org.usvm.dataflow.ts.getResourcePathOrNull
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeGuesser
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.annotation.InferredTypeScheme
import org.usvm.dataflow.ts.infer.annotation.annotateWithTypes
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.infer.toType
import org.usvm.dataflow.ts.loadEtsProjectFromResources
import org.usvm.dataflow.ts.testFactory
import org.usvm.dataflow.ts.util.EtsTraits
import org.usvm.dataflow.ts.util.sortedBy
import org.usvm.dataflow.ts.util.sortedByBase
import org.usvm.dataflow.ts.util.toStringLimited
import java.io.File
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.toPath
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

class EtsTypeInferenceTest {

    companion object {
        private fun load(path: String): EtsFile {
            return loadEtsFileAutoConvert(getResourcePath(path), useArkAnalyzerTypeInference = null)
        }
    }

    @Test
    fun `type inference for microphone`() {
        val name = "microphone"
        val file = load("/ts/$name.ts")
        val project = EtsScene(listOf(file), sdkFiles = emptyList())
        val graph = createApplicationGraph(project)

        val entrypoints = project.projectClasses
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = TypeInferenceManager(EtsTraits(), graph)
        val result = manager.analyze(entrypoints, doAddKnownTypes = false)
        val types = result.inferredTypes

        run {
            val m = types.keys.first { it.name == "getMicrophoneUuid" }

            // arg0 = 'devices'
            val devices = types.getValue(m).getValue(AccessPathBase.Arg(0))
            assertIs<EtsTypeFact.ObjectEtsTypeFact>(devices)

            val devicesCls = devices.cls
            assertEquals("VirtualDevices", devicesCls?.typeName)

            assertContains(devices.properties, "microphone")
            val microphone = devices.properties.getValue("microphone")
            assertIs<EtsTypeFact.ObjectEtsTypeFact>(microphone)

            assertContains(microphone.properties, "uuid")
            val uuid = microphone.properties.getValue("uuid")
            assertIs<EtsTypeFact.StringEtsTypeFact>(uuid)
        }
    }

    @Test
    fun `type inference for types`() {
        val name = "types"
        val file = load("/ts/$name.ts")
        val project = EtsScene(listOf(file), sdkFiles = emptyList())
        val graph = createApplicationGraph(project)

        val entrypoints = project.projectClasses
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = TypeInferenceManager(EtsTraits(), graph)
        manager.analyze(entrypoints)
    }

    @Test
    fun `type inference for data`() {
        val name = "data"
        val file = load("/ts/$name.ts")
        val project = EtsScene(listOf(file), sdkFiles = emptyList())
        val graph = createApplicationGraph(project)

        val entrypoints = project.projectClasses
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = TypeInferenceManager(EtsTraits(), graph)
        manager.analyze(entrypoints)
    }

    @Test
    fun `type inference for call`() {
        val name = "call"
        val file = load("/ts/$name.ts")
        val project = EtsScene(listOf(file), sdkFiles = emptyList())
        val graph = createApplicationGraph(project)

        val entrypoints = project.projectClasses
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = TypeInferenceManager(EtsTraits(), graph)
        manager.analyze(entrypoints)
    }

    @Test
    fun `type inference for nested_init`() {
        val name = "nested_init"
        val file = load("/ts/$name.ts")
        val project = EtsScene(listOf(file), sdkFiles = emptyList())
        val graph = createApplicationGraph(project)

        val entrypoints = project.projectClasses
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = TypeInferenceManager(EtsTraits(), graph)
        manager.analyze(entrypoints)
    }

    private fun resourceAvailable(dirName: String): Boolean =
        object {}::class.java.getResource(dirName) != null

    private fun testHapSet(setPath: String) {
        val abcDir = object {}::class.java.getResource(setPath)?.toURI()?.toPath()
            ?: error("Resource not found: $setPath")
        val haps = abcDir.toFile().listFiles()?.toList() ?: emptyList()
        processAllHAPs(haps)
    }

    private val TEST_PROJECTS_PATH = "/projects/abcir/"
    private fun testProjectsAvailable() = resourceAvailable(TEST_PROJECTS_PATH)

    @Test
    @EnabledIf("testProjectsAvailable")
    fun `type inference for test projects`() = testHapSet(TEST_PROJECTS_PATH)

    @Test
    @Disabled("No project found")
    fun `test single HAP`() {
        val abcDirName = "/TestProjects/CertificateManager_240801_843398b"
        val projectDir = object {}::class.java.getResource(abcDirName)?.toURI()?.toPath()
            ?: error("Resource not found: $abcDirName")
        val (scene, result) = testHap(projectDir.toString())
        val scene2 = scene.annotateWithTypes(InferredTypeScheme(result))
        scene2.let {}
    }

    private fun processAllHAPs(haps: Collection<File>) {
        val succeed = mutableListOf<String>()
        val timeout = mutableListOf<String>()
        val failed = mutableListOf<String>()

        haps.forEach { project ->
            try {
                runBlocking {
                    withTimeout(60_000) {
                        runInterruptible {
                            testHap(project.path)
                        }
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

    private fun testHap(projectDir: String): Pair<EtsScene, TypeInferenceResult> {
        val dir = File(projectDir).takeIf { it.isDirectory } ?: error("Not found project dir $projectDir")
        println("Found project dir: '$dir'")

        val files = dir
            .walk()
            .filter { it.extension == "json" }
            .toList()
        println("Found files: (${files.size})")
        for (path in files) {
            println("  ${path.relativeTo(dir)}")
        }

        println("Processing ${files.size} files...")
        val etsFiles = files.map { EtsFileDto.loadFromJson(it.inputStream()).toEtsFile() }
        val project = EtsScene(etsFiles, sdkFiles = emptyList())
        val graph = createApplicationGraph(project)

        val entrypoints = project.projectClasses
            .flatMap { it.methods }
            .filter { it.isPublic || it.name == CONSTRUCTOR_NAME }
            .filter { !it.signature.enclosingClass.name.startsWith("AnonymousClass") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = TypeInferenceManager(EtsTraits(), graph)
        val result = manager.analyze(entrypoints)
        return Pair(project, result)
    }

    @Test
    fun `test if guesser does anything`() {
        val name = "testcases"
        val file = load("/ts/$name.ts")
        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)
        val guesser = TypeGuesser(project)

        val entrypoints = project.projectClasses
            .flatMap { it.methods }
            .filter { it.name == "entrypoint" }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = TypeInferenceManager(EtsTraits(), graph)
        val resultWithoutGuessed = manager.analyze(entrypoints)
        val resultWithGuessed = resultWithoutGuessed.withGuessedTypes(guesser)

        assertNotEquals(resultWithoutGuessed.inferredTypes, resultWithGuessed.inferredTypes)

        println("=".repeat(42))
        println("Inferred types WITHOUT guesser: ")
        for ((method, types) in resultWithoutGuessed.inferredTypes) {
            println(method.signature.enclosingClass.name to types)
        }

        println("=".repeat(42))
        println("Inferred types with guesser: ")
        for ((method, types) in resultWithGuessed.inferredTypes) {
            println(method.signature.enclosingClass.name to types)
        }
    }

    // TODO: support these complex tests
    private val disabledTests = setOf(
        "CaseAssignFieldToSelf",
        "CaseLoop",
        "CaseNew",
        "CaseRecursion",
    )

    @TestFactory
    fun `type inference on testcases`() = testFactory {
        val file = load("/ts/testcases.ts")
        val project = EtsScene(listOf(file), sdkFiles = emptyList())
        val graph = createApplicationGraph(project)

        val allCases = project.projectClasses.filter { it.name.startsWith("Case") }

        for (cls in allCases) {
            if (cls.name in disabledTests) continue
            test(name = cls.name) {
                logger.info { "Analyzing testcase: ${cls.name}" }

                val inferMethod = cls.methods.single { it.name == "infer" }
                logger.info { "Found infer: ${inferMethod.signature}" }

                val expectedTypeString = mutableMapOf<AccessPathBase, String>()
                var expectedReturnTypeString = ""
                for (inst in inferMethod.cfg.stmts) {
                    if (inst is EtsAssignStmt) {
                        val lhv = inst.lhv
                        if (lhv is EtsLocal) {
                            val rhv = inst.rhv
                            if (lhv.name.startsWith("EXPECTED_ARG_")) {
                                check(rhv is EtsStringConstant)
                                val arg = lhv.name.removePrefix("EXPECTED_ARG_").toInt()
                                val pos = AccessPathBase.Arg(arg)
                                expectedTypeString[pos] = rhv.value
                                logger.info { "Expected type for $pos: ${rhv.value}" }
                            } else if (lhv.name == "EXPECTED_RETURN") {
                                check(rhv is EtsStringConstant)
                                expectedReturnTypeString = rhv.value
                                logger.info { "Expected return type: ${rhv.value}" }
                            } else if (lhv.name.startsWith("EXPECTED")) {
                                logger.error { "Skipping unexpected local: $lhv" }
                            }
                        }
                    }
                }

                val entrypoint = cls.methods.single { it.name == "entrypoint" }
                logger.info { "Found entrypoint: ${entrypoint.signature}" }

                val manager = TypeInferenceManager(EtsTraits(), graph)
                val result = manager.analyze(listOf(entrypoint), doAddKnownTypes = false)

                val inferredTypes = result.inferredTypes[inferMethod]
                    ?: error(
                        "No inferred types for method ${
                            inferMethod.signature.enclosingClass.name
                        }::${inferMethod.name}"
                    )

                for ((position, expected) in expectedTypeString.sortedByBase()) {
                    val inferred = inferredTypes[position]
                    logger.info { "Inferred type for $position: $inferred" }
                    val passed = inferred.toString() == expected
                    assertTrue(
                        passed,
                        "Inferred type for $position does not match: inferred = $inferred, expected = $expected"
                    )
                }
                if (expectedReturnTypeString.isNotBlank()) {
                    val expected = expectedReturnTypeString
                    val inferred = result.inferredReturnType[inferMethod]
                    logger.info { "Inferred return type: $inferred" }
                    val passed = inferred.toString() == expected
                    assertTrue(
                        passed,
                        "Inferred return type does not match: inferred = $inferred, expected = $expected"
                    )
                }
            }
        }
    }

    @TestFactory
    fun `test type inference on projects`() = testFactory {
        val p = getResourcePathOrNull("/projects") ?: run {
            logger.warn { "No projects directory found in resources" }
            return@testFactory
        }
        val availableProjectNames = p.listDirectoryEntries().filter { it.isDirectory() }.map { it.name }.sorted()
        logger.info {
            buildString {
                appendLine("Found ${availableProjectNames.size} projects")
                for (name in availableProjectNames) {
                    appendLine("  - $name")
                }
            }
        }
        if (availableProjectNames.isEmpty()) {
            logger.warn { "No projects found" }
            return@testFactory
        }
        for (projectName in availableProjectNames) {
            // if (projectName != "...") continue
            test("infer types in $projectName") {
                logger.info { "Loading project: $projectName" }
                val projectPath = getResourcePath("/projects/$projectName")
                val etsirPath = projectPath / "etsir"
                if (!etsirPath.exists()) {
                    logger.warn { "No etsir directory found for project $projectName" }
                    return@test
                }
                val modules = etsirPath.listDirectoryEntries().filter { it.isDirectory() }.map { it.name }
                logger.info { "Found ${modules.size} modules: $modules" }
                if (modules.isEmpty()) {
                    logger.warn { "No modules found for project $projectName" }
                    return@test
                }
                val project = loadEtsProjectFromResources(modules, "/projects/$projectName/etsir")
                logger.info {
                    "Loaded project with ${
                        project.projectClasses.size
                    } classes and ${project.projectClasses.sumOf { it.methods.size }} methods"
                }
                for (cls in project.projectClasses.sortedBy { it.name }) {
                    logger.info {
                        buildString {
                            appendLine("Class ${cls.name} has ${cls.methods.size} methods")
                            for (method in cls.methods.sortedBy { it.name }) {
                                appendLine("- $method")
                            }
                        }
                    }
                }
                val graph = createApplicationGraph(project)

                val entrypoints = project.projectClasses
                    .flatMap { it.methods }
                    .filter { it.isPublic }
                logger.info { "Found ${entrypoints.size} entrypoints" }

                val manager = TypeInferenceManager(EtsTraits(), graph)
                val result = manager.analyze(entrypoints)

                logger.info {
                    buildString {
                        appendLine("Inferred types: ${result.inferredTypes.size}")
                        for ((method, types) in result.inferredTypes.sortedBy { it.key.toString() }) {
                            appendLine()
                            appendLine("- $method")
                            for ((pos, type) in types.sortedByBase()) {
                                appendLine("$pos: ${type.toStringLimited()}")
                            }
                        }
                    }
                }
                logger.info {
                    buildString {
                        appendLine(
                            "Inferred return types: ${
                                result.inferredReturnType.size
                            }"
                        )
                        val res = result.inferredReturnType.sortedBy { it.key.toString() }
                        for ((method, returnType) in res) {
                            appendLine(
                                "${
                                    method.signature.enclosingClass.name
                                }::${
                                    method.name
                                }: ${
                                    returnType.toStringLimited()
                                }"
                            )
                        }
                    }
                }
                logger.info {
                    buildString {
                        appendLine(
                            "Inferred combined this types: ${
                                result.inferredCombinedThisType.size
                            }"
                        )
                        val res = result.inferredCombinedThisType.sortedBy { it.key.toString() }
                        for ((clazz, thisType) in res) {
                            appendLine(
                                "${clazz.name} in ${clazz.file}: ${
                                    thisType.toStringLimited()
                                }"
                            )
                        }
                    }
                }

                var totalNumMatchedNormal = 0
                var totalNumMatchedUnknown = 0
                var totalNumMismatchedNormal = 0
                var totalNumLostNormal = 0
                var totalNumBetterThanUnknown = 0

                for ((method, inferredTypes) in result.inferredTypes) {
                    var numMatchedNormal = 0
                    var numMatchedUnknown = 0
                    var numMismatchedNormal = 0
                    var numLostNormal = 0
                    var numBetterThanUnknown = 0

                    for (local in method.getLocals()) {
                        val inferredType = inferredTypes[AccessPathBase.Local(local.name)]?.toType()
                        val verdict = if (inferredType != null) {
                            if (local.type.isUnknown()) {
                                if (inferredType.isUnknown()) {
                                    numMatchedUnknown++
                                    "Matched unknown"
                                } else {
                                    numBetterThanUnknown++
                                    "Better than unknown"
                                }
                            } else {
                                if (inferredType == local.type) {
                                    numMatchedNormal++
                                    "Matched normal"
                                } else {
                                    numMismatchedNormal++
                                    "Mismatched normal"
                                }
                            }
                        } else {
                            if (local.type.isUnknown()) {
                                numMatchedUnknown++
                                "Matched (lost) unknown"
                            } else {
                                numLostNormal++
                                "Lost normal"
                            }
                        }
                        logger.info {
                            "Local $local in $method, type: ${
                                local.type.toStringLimited()
                            }, inferred: ${
                                inferredType?.toStringLimited()
                            }, verdict: $verdict"
                        }
                    }

                    logger.info {
                        buildString {
                            appendLine(
                                "Local type matching for ${
                                    method.signature.enclosingClass.name
                                }::${method.name}:"
                            )
                            appendLine("  Matched normal: $numMatchedNormal")
                            appendLine("  Matched unknown: $numMatchedUnknown")
                            appendLine("  Mismatched normal: $numMismatchedNormal")
                            appendLine("  Lost normal: $numLostNormal")
                            appendLine("  Better than unknown: $numBetterThanUnknown")
                        }
                    }
                    totalNumMatchedNormal += numMatchedNormal
                    totalNumMatchedUnknown += numMatchedUnknown
                    totalNumMismatchedNormal += numMismatchedNormal
                    totalNumLostNormal += numLostNormal
                    totalNumBetterThanUnknown += numBetterThanUnknown
                }

                logger.info {
                    buildString {
                        appendLine("Total local type matching statistics:")
                        appendLine("  Matched normal: $totalNumMatchedNormal")
                        appendLine("  Matched unknown: $totalNumMatchedUnknown")
                        appendLine("  Mismatched normal: $totalNumMismatchedNormal")
                        appendLine("  Lost normal: $totalNumLostNormal")
                        appendLine("  Better than unknown: $totalNumBetterThanUnknown")
                    }
                }

                logger.info { "Done analyzing project: $projectName" }
            }
        }
    }
}

private fun EtsType.isUnknown(): Boolean =
    this == EtsUnknownType || this == EtsAnyType
