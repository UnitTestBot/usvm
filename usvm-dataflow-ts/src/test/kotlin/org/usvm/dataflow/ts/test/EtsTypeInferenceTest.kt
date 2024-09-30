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
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsStringConstant
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.convertToEtsFile
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.test.utils.getResourcePath
import org.jacodb.ets.test.utils.getResourcePathOrNull
import org.jacodb.ets.test.utils.loadEtsFileFromResource
import org.jacodb.ets.test.utils.loadEtsProjectFromResources
import org.jacodb.ets.test.utils.testFactory
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.EnabledIf
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.util.CONSTRUCTOR
import org.usvm.dataflow.ts.util.EtsTraits
import java.io.File
import kotlin.io.path.div
import kotlin.io.path.exists
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
            return loadEtsFileFromResource("/$path")
        }
    }

    @Test
    fun `type inference for microphone`() {
        val name = "microphone"
        val file = load("ir/$name.ts.json")
        // val file = load("abcir/$name.abc.json")
        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }
        val result = manager.analyze(entrypoints)
        val types = result.inferredTypes

        run {
            val m = types.keys.first { it.name == "getMicrophoneUuid" }

            // arg0 = 'devices'
            val devices = types[m]!![AccessPathBase.Arg(0)]!!
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
        val graph = createApplicationGraph(project)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }
        manager.analyze(entrypoints)
    }

    @Test
    fun `type inference for data`() {
        val name = "data"
        val file = load("ir/$name.ts.json")
        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }
        manager.analyze(entrypoints)
    }

    @Test
    fun `type inference for call`() {
        val name = "call"
        val file = load("ir/$name.ts.json")
        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }
        manager.analyze(entrypoints)
    }

    @Test
    fun `type inference for nested_init`() {
        val name = "nested_init"
        val file = load("ir/$name.ts.json")
        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }
        manager.analyze(entrypoints)
    }

    @Disabled("EtsIR-ABC is outdated")
    @Test
    fun `type inference for cast ABC`() {
        val name = "cast"
        val file = load("abcir/$name.abc.json")
        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name.startsWith("entrypoint") }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }
        manager.analyze(entrypoints)
    }

    private fun resourceAvailable(dirName: String) =
        object {}::class.java.getResource(dirName) != null

    private fun testHapSet(setPath: String) {
        val abcDir = object {}::class.java.getResource(setPath)?.toURI()?.toPath()
            ?: error("Resource not found: $setPath")
        val haps = abcDir.toFile().listFiles()?.toList() ?: emptyList()
        processAllHAPs(haps)
    }

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
        // val resource = "/projects/applications_app_samples/etsir/abc/$projectName"
        // val dir = object {}::class.java.getResource(resource)?.toURI()?.toPath()
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
        val graph = createApplicationGraph(project)

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
        val result = manager.analyze(entrypoints)
    }

    @Test
    fun `test if guesser does anything`() {
        val name = "testcases"
        val file = load("ir/$name.ts.json")
        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val entrypoints = project.classes
            .flatMap { it.methods }
            .filter { it.name == "entrypoint" }
        println("entrypoints: (${entrypoints.size})")
        entrypoints.forEach {
            println("  ${it.signature.enclosingClass.name}::${it.name}")
        }

        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }
        val resultWithoutGuessed = manager.analyze(entrypoints)
        val resultWithGuessed = resultWithoutGuessed.withGuessedTypes(graph)

        assertNotEquals(resultWithoutGuessed.inferredTypes, resultWithGuessed.inferredTypes)

        println("=".repeat(42))
        println("Inferred types WITHOUT guesser: ")
        for ((method, types) in resultWithoutGuessed.inferredTypes) {
            println(method.enclosingClass.name to types)
        }

        println("=".repeat(42))
        println("Inferred types with guesser: ")
        for ((method, types) in resultWithGuessed.inferredTypes) {
            println(method.enclosingClass.name to types)
        }
    }

    @TestFactory
    fun `type inference on testcases`() = testFactory {
        val file = load("ir/testcases.ts.json")
        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val allCases = project.classes.filter { it.name.startsWith("Case") }

        for (cls in allCases) {
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

                val manager = with(EtsTraits) {
                    TypeInferenceManager(graph)
                }
                val result = manager.analyze(listOf(entrypoint))

                val inferredTypes = result.inferredTypes[inferMethod]
                    ?: error("No inferred types for method ${inferMethod.enclosingClass.name}::${inferMethod.name}")

                for (position in expectedTypeString.keys.sortedBy {
                    when (it) {
                        is AccessPathBase.This -> -1
                        is AccessPathBase.Arg -> it.index
                        else -> 1_000_000
                    }
                }) {
                    val expected = expectedTypeString[position]!!
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
        val availableProjectNames = p.toFile().listFiles { f -> f.isDirectory }!!.map { it.name }
        logger.info {
            buildString {
                appendLine("Found projects: ${availableProjectNames.size}")
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
            test("load $projectName") {
                logger.info { "Loading project: $projectName" }
                val projectPath = getResourcePath("/projects/$projectName")
                val etsirPath = projectPath / "etsir"
                if (!etsirPath.exists()) {
                    logger.warn { "No etsir directory found for project $projectName" }
                    return@test
                }
                val modules = etsirPath.toFile().listFiles { f -> f.isDirectory }!!.map { it.name }
                logger.info { "Found ${modules.size} modules: $modules" }
                if (modules.isEmpty()) {
                    logger.warn { "No modules found for project $projectName" }
                    return@test
                }
                val project = loadEtsProjectFromResources(modules, "/projects/$projectName/etsir")
                logger.info {
                    buildString {
                        appendLine("Loaded project with ${project.classes.size} classes and ${project.classes.sumOf { it.methods.size }} methods")
                        for (cls in project.classes) {
                            appendLine("= ${cls.signature} with ${cls.methods.size} methods:")
                            for (method in cls.methods) {
                                appendLine("  - ${method.signature}")
                            }
                        }
                    }
                }
                val graph = createApplicationGraph(project)

                val entrypoints = project.classes
                    .flatMap { it.methods }
                    .filter { it.isPublic }
                logger.info { "Found ${entrypoints.size} entrypoints" }

                val manager = with(EtsTraits) {
                    TypeInferenceManager(graph)
                }
                val result = manager.analyze(entrypoints)

                logger.info { "Inferred types: ${result.inferredTypes}" }
                logger.info { "Inferred return types: ${result.inferredReturnType}" }
                logger.info { "Inferred combined this types: ${result.inferredCombinedThisType}" }
            }
        }
    }
}
