package org.usvm.dataflow.ts.test

import org.jacodb.ets.graph.EtsApplicationGraphImpl
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethodImpl
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsApplicationGraphWithExplicitEntryPoint
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.test.utils.loadEtsFileFromResource
import org.usvm.dataflow.ts.util.EtsTraits

class EtsTypeInferenceTest {

    companion object {
        private fun load(path: String): EtsFile {
            return loadEtsFileFromResource("/$path")
        }
    }

    @Test
    fun `test type inference on microphone`() {
        val name = "microphone_ctor"
        val arkFile = load("ir/$name.ts.json")
        // val arkFile = load("abcir/$name.abc.json")
        val graph = EtsApplicationGraphImpl(arkFile)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = arkFile.classes
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
    fun `test type inference on types`() {
        val name = "types"
        val arkFile = load("ir/$name.ts.json")
        val graph = EtsApplicationGraphImpl(arkFile)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = arkFile.classes
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
    fun `test type inference on data`() {
        val name = "data"
        val arkFile = load("ir/$name.ts.json")
        val graph = EtsApplicationGraphImpl(arkFile)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = arkFile.classes
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
    fun `test type inference on call`() {
        val name = "call"
        val arkFile = load("ir/$name.ts.json")
        val graph = EtsApplicationGraphImpl(arkFile)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = arkFile.classes
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
    fun `test type inference on applications_settings_data SettingsDBHelper`() {
        val arkFile = load("ir/applications_settings_data/Utils/SettingsDBHelper.ets.json")
        val graph = EtsApplicationGraphImpl(arkFile)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = arkFile.classes
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

        val objects = inferred.values
            .asSequence()
            .flatMap { it.types.values.asSequence() }
            .filterIsInstance<EtsTypeFact.ObjectEtsTypeFact>()
            .toSet()
        println()
        println("Objects: (${objects.size})")
        for (obj in objects) {
            println("obj = $obj")
        }

        println()
        println("Classes: (${arkFile.classes.size})")
        for (clazz in arkFile.classes) {
            println("clazz '${clazz.name}'")
            println("  ${clazz.fields.size} fields: ${clazz.fields.map { it.name }}")
            println("  ${clazz.methods.size} methods: ${clazz.methods.map { it.name }}")
        }

        // val allMethods =
    }

    @Test
    fun `test type inference on applications_settings_data GlobalContext`() {
        val arkFile = load("ir/applications_settings_data/Utils/GlobalContext.ets.json")
        val graph = EtsApplicationGraphImpl(arkFile)
        val graphWithExplicitEntryPoint = EtsApplicationGraphWithExplicitEntryPoint(graph)

        val entrypoints = arkFile.classes
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
    }
}
