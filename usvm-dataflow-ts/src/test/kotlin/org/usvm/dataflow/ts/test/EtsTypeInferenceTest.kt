package org.usvm.dataflow.ts.test

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
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsApplicationGraphWithExplicitEntryPoint
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.test.utils.loadEtsFileFromResource
import org.usvm.dataflow.ts.util.CONSTRUCTOR
import org.usvm.dataflow.ts.util.EtsTraits
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
        val inferredForwardTypes = manager.analyze(entrypoints)

        val inferMethods = project.classes
            .asSequence()
            .filter { it.name.startsWith("Case") }
            .flatMap { it.methods.asSequence() }
            .filter { it.name == "infer" }
            .toList()

        val expectedTypeString: Map<EtsMethod, String> = inferMethods
            .associateWith {
                for (inst in it.cfg.stmts) {
                    if (inst is EtsAssignStmt) {
                        val lhv = inst.lhv
                        if (lhv is EtsLocal && lhv.name == "EXPECTED_ARG_0") {
                            val rhv = inst.rhv
                            check(rhv is EtsStringConstant)
                            return@associateWith rhv.value
                        }
                    }
                }
                error("unreachable")
            }

        println("=".repeat(42))
        var numOk = 0
        var numBad = 0
        for (m in inferMethods) {
            val inferred = inferredForwardTypes[m]!!.types[AccessPathBase.Arg(0)]!!
            val expected = expectedTypeString[m]!!

            if (inferred.toString() == expected) {
                numOk++
                println("Correctly inferred type for '${m.enclosingClass.name}::${m.name} in ${m.enclosingClass.enclosingFile}': ${inferred.toPrettyString()}")
            } else {
                numBad++
                println("Incorrectly inferred type for '${m.enclosingClass.name}::${m.name} in ${m.enclosingClass.enclosingFile}': inferred='$inferred', expected=$expected")
            }
        }
        println("numOk = $numOk")
        println("numBad = $numBad")
        println("Success rate: %.1f%%".format(numOk / (numOk + numBad).toDouble() * 100.0))
    }
}
