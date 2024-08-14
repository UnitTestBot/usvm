package org.usvm.dataflow.ts.test

import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.convertToEtsFile
import org.jacodb.ets.graph.EtsApplicationGraphImpl
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Assertions
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
import kotlin.io.path.toPath
import kotlin.io.path.walk

@OptIn(ExperimentalPathApi::class)
class EtsTypeInferenceTest {

    companion object {
        private fun load(path: String): EtsFile {
            return loadEtsFileFromResource("/$path")
        }
    }

    @Test
    fun `type inference for microphone`() {
        val name = "microphone_ctor"
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
            Assertions.assertTrue(devices is EtsTypeFact.ObjectEtsTypeFact)
            check(devices is EtsTypeFact.ObjectEtsTypeFact)

            val devicesCls = devices.cls
            Assertions.assertTrue(devicesCls?.typeName == "VirtualDevices")

            val devicesProps = devices.properties
            Assertions.assertTrue("microphone" in devicesProps)

            val microphone = devicesProps["microphone"]!!
            Assertions.assertTrue(microphone is EtsTypeFact.ObjectEtsTypeFact)
            check(microphone is EtsTypeFact.ObjectEtsTypeFact)

            val microphoneProps = microphone.properties
            Assertions.assertTrue("uuid" in microphoneProps)

            val uuid = microphoneProps["uuid"]!!
            Assertions.assertTrue(uuid is EtsTypeFact.StringEtsTypeFact)
            check(uuid is EtsTypeFact.StringEtsTypeFact)
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
            .filter {
                (it as EtsMethodImpl).modifiers.contains("PublicKeyword") || (!it.modifiers.contains("PrivateKeyword") && !it.modifiers.contains(
                    "ProtectedKeyword"
                )) || it.name == "constructor"
            }
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
            Assertions.assertTrue(arg0 is EtsTypeFact.ObjectEtsTypeFact)

            val arg0props = (arg0 as EtsTypeFact.ObjectEtsTypeFact).properties
            Assertions.assertTrue("user" in arg0props)
            Assertions.assertTrue("userSecure" in arg0props)
            Assertions.assertTrue("settings" in arg0props)

            val user = arg0props["user"]
            Assertions.assertTrue(user is EtsTypeFact.ObjectEtsTypeFact)
            val userProps = (user as EtsTypeFact.ObjectEtsTypeFact).properties
            Assertions.assertTrue("index" in userProps)
            Assertions.assertTrue("length" in userProps)

            val userSecure = arg0props["userSecure"]
            Assertions.assertTrue(userSecure is EtsTypeFact.ObjectEtsTypeFact)
            val userSecureProps = (userSecure as EtsTypeFact.ObjectEtsTypeFact).properties
            Assertions.assertTrue("index" in userSecureProps)
            Assertions.assertTrue("length" in userSecureProps)

            val settings = arg0props["settings"]
            Assertions.assertTrue(settings is EtsTypeFact.ObjectEtsTypeFact)
            val settingsProps = (settings as EtsTypeFact.ObjectEtsTypeFact).properties
            Assertions.assertTrue("index" in settingsProps)
            Assertions.assertTrue("length" in settingsProps)
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
            println("  $path")
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
}
