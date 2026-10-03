package org.usvm.samples.types

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsMethodResult
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RuntimeInstanceofTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/types/RuntimeInstanceof.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `dynamic constructor value selects both outcomes`() {
        val method = getMethod(methodName = "dynamicConstructor", className = "RuntimeInstanceof")
        val outcome = analyze("dynamicConstructor")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        val cases = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(method, state)
            val input = assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
            val actual = assertIs<TsTestValue.TsNumber>(test.returnValue).number
            assertEquals(if (input) 1.0 else 0.0, actual)
            input to actual
        }
        assertEquals(setOf(false, true), cases.map { it.first }.toSet())

        replay(cases.map { (input, actual) ->
            "if (new RuntimeInstanceof().dynamicConstructor($input) !== ${actual.toInt()}) throw Error('dynamic $input');"
        })
    }

    @Test
    fun `returned class value keeps constructor identity`() {
        val method = getMethod(methodName = "returnedConstructor", className = "RuntimeInstanceof")
        val outcome = analyze("returnedConstructor")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        assertTrue(outcome.states.isNotEmpty())
        outcome.states.forEach { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(method, state)
            assertEquals(1.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
        }

        replay(listOf(
            "if (new RuntimeInstanceof().returnedConstructor() !== 1) throw Error('returned constructor');"
        ))
    }

    @Test
    fun `typeof recognizes constructor values stored in a local`() {
        val method = getMethod(methodName = "aliasedClassTypeof", className = "RuntimeInstanceof")
        val outcome = analyze("aliasedClassTypeof")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        val inputs = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(method, state)
            assertEquals(1.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
            assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
        }
        assertEquals(setOf(false, true), inputs.toSet())

        replay(inputs.map { input ->
            "if (new RuntimeInstanceof().aliasedClassTypeof($input) !== 1) throw Error('typeof $input');"
        })
    }

    @Test
    fun `direct and inherited checks use declared class identity`() {
        val expected = mapOf(
            "directConstructor" to 1.0,
            "castConstructor" to 1.0,
            "nonNullConstructor" to 1.0,
            "anyConstructor" to 1.0,
            "unknownConstructor" to 1.0,
            "objectConstructor" to 1.0,
            "unrelatedConstructor" to 0.0,
            "classTypeof" to 1.0,
            "primitiveLeft" to 0.0,
        )

        expected.forEach { (methodName, expectedResult) ->
            val method = getMethod(methodName = methodName, className = "RuntimeInstanceof")
            val outcome = analyze(methodName)

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.unsupportedPaths.isEmpty(), "$methodName: ${outcome.unsupportedPaths}")
            assertTrue(outcome.states.isNotEmpty(), "$methodName: ${outcome.unsupportedPaths}")
            outcome.states.forEach { state ->
                assertIs<TsMethodResult.Success>(state.methodResult, methodName)
                val test = TsTestResolver().resolve(method, state)
                assertEquals(expectedResult, assertIs<TsTestValue.TsNumber>(test.returnValue).number, methodName)
            }
        }

        replay(expected.map { (methodName, expectedResult) ->
            "if (new RuntimeInstanceof().$methodName() !== ${expectedResult.toInt()}) throw Error('$methodName');"
        })
    }

    @Test
    fun `nullish left operands are not class instances`() {
        val methods = listOf("undefinedLeft", "nullLeft")

        methods.forEach { methodName ->
            val method = getMethod(methodName = methodName, className = "RuntimeInstanceof")
            val outcome = analyze(methodName)

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.unsupportedPaths.isEmpty(), "$methodName: ${outcome.unsupportedPaths}")
            assertTrue(outcome.states.isNotEmpty(), methodName)
            outcome.states.forEach { state ->
                assertIs<TsMethodResult.Success>(state.methodResult)
                val test = TsTestResolver().resolve(method, state)
                assertEquals(0.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number, methodName)
            }
        }

        replay(methods.map { methodName ->
            "if (new RuntimeInstanceof().$methodName() !== 0) throw Error('$methodName');"
        })
    }

    @Test
    fun `any receiver can contain an instance of the checked class`() {
        val method = getMethod(methodName = "anyLeft", className = "RuntimeInstanceof")
        val outcome = analyze("anyLeft")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        val cases = outcome.states.map { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(method, state)
            val input = test.before.parameters.single()
            val actual = assertIs<TsTestValue.TsNumber>(test.returnValue).number
            input to actual
        }
        assertEquals(setOf(0.0, 1.0), cases.map { it.second }.toSet(), "$cases")

        val generatedAssertions = cases.mapIndexed { index, (input, actual) ->
            val argument = when (input) {
                is TsTestValue.TsClass -> "new ${input.name}()"
                is TsTestValue.TsBoolean -> input.value.toString()
                is TsTestValue.TsNumber -> input.number.toString()
                TsTestValue.TsNull -> "null"
                TsTestValue.TsUndefined -> "undefined"
                else -> error("Cannot replay generated instanceof input: $input")
            }

            "if (new RuntimeInstanceof().anyLeft($argument) !== ${actual.toInt()}) " +
                "throw Error('generated instanceof case $index');"
        }
        replay(generatedAssertions + listOf(
            "if (new RuntimeInstanceof().anyLeft(new InstanceA()) !== 1) throw Error('A instance');",
            "if (new RuntimeInstanceof().anyLeft(new InstanceB()) !== 0) throw Error('B instance');",
            "if (new RuntimeInstanceof().anyLeft(42) !== 0) throw Error('primitive');",
        ))
    }

    @Test
    fun `class constructor values are not instances of project classes`() {
        val methods = listOf("constructorLeft", "constructorAliasLeft", "anyConstructorLeft")

        methods.forEach { methodName ->
            val method = getMethod(methodName = methodName, className = "RuntimeInstanceof")
            val outcome = analyze(methodName)

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.unsupportedPaths.isEmpty(), "$methodName: ${outcome.unsupportedPaths}")
            assertTrue(outcome.states.isNotEmpty(), methodName)

            val tests = outcome.states.map { state ->
                assertIs<TsMethodResult.Success>(state.methodResult, methodName)
                val test = TsTestResolver().resolve(method, state)
                assertEquals(0.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number, methodName)
                test
            }

            if (methodName == "constructorAliasLeft") {
                val inputs = tests.map { test ->
                    assertIs<TsTestValue.TsBoolean>(test.before.parameters.single()).value
                }
                assertEquals(setOf(false, true), inputs.toSet())
            }
        }

        replay(listOf(
            "if (new RuntimeInstanceof().constructorLeft() !== 0) throw Error('direct ctor LHS');",
            "if (new RuntimeInstanceof().constructorAliasLeft(true) !== 0) throw Error('A ctor LHS');",
            "if (new RuntimeInstanceof().constructorAliasLeft(false) !== 0) throw Error('B ctor LHS');",
            "if (new RuntimeInstanceof().anyConstructorLeft() !== 0) throw Error('any ctor LHS');",
        ))
    }

    @Test
    fun `subclass instance belongs to declared parent constructor`() {
        val method = getMethod(methodName = "inheritedConstructor", className = "RuntimeInstanceof")
        val outcome = analyze("inheritedConstructor")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        assertTrue(outcome.states.isNotEmpty())
        outcome.states.forEach { state ->
            assertIs<TsMethodResult.Success>(state.methodResult)
            val test = TsTestResolver().resolve(method, state)
            assertEquals(1.0, assertIs<TsTestValue.TsNumber>(test.returnValue).number)
        }

        replay(listOf(
            "if (new RuntimeInstanceof().inheritedConstructor(new InstanceChild()) !== 1) throw Error('parent');"
        ))
    }

    @Test
    fun `non callable RHS throws a TypeError object`() {
        val methods = listOf(
            "nonCallableRight",
            "booleanRight",
            "stringRight",
            "nullRight",
            "undefinedRight",
            "objectRight",
            "classInstanceRight",
        )

        methods.forEach { methodName ->
            val method = getMethod(methodName = methodName, className = "RuntimeInstanceof")
            val outcome = analyze(methodName)

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.unsupportedPaths.isEmpty(), "$methodName: ${outcome.unsupportedPaths}")
            assertTrue(outcome.states.isNotEmpty(), methodName)
            outcome.states.forEach { state ->
                val exception = assertIs<TsMethodResult.TsException>(state.methodResult)
                assertEquals("TypeError", exception.type.typeName)
                assertTrue(isAllocatedConcreteHeapRef(exception.value))

                val test = TsTestResolver().resolve(method, state)
                val objectException = assertIs<TsTestValue.TsException.ObjectException>(test.returnValue)
                assertEquals("TypeError", assertIs<TsTestValue.TsClass>(objectException.value).name)
            }
        }

        replay(methods.map { methodName ->
            "let caught$methodName = false; try { new RuntimeInstanceof().$methodName(); } " +
                "catch (error) { caught$methodName = error instanceof TypeError; } " +
                "if (!caught$methodName) throw Error('$methodName TypeError');"
        })
    }

    @Test
    fun `custom hasInstance is explicitly unsupported`() {
        val custom = analyze("customHasInstance")
        val inherited = analyze("inheritedHasInstance")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, custom.stopReason)
        assertTrue(custom.states.isEmpty())
        assertTrue(custom.unsupportedPaths.any { "Symbol.hasInstance" in it }, "${custom.unsupportedPaths}")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, inherited.stopReason)
        assertTrue(inherited.states.isEmpty())
        assertTrue(inherited.unsupportedPaths.any { "Symbol.hasInstance" in it }, "${inherited.unsupportedPaths}")

        replay(listOf(
            "if (new RuntimeInstanceof().customHasInstance() !== false) throw Error('custom hasInstance');",
            "if (new RuntimeInstanceof().inheritedHasInstance(new InstanceHasInstanceChild()) !== false) " +
                "throw Error('inherited hasInstance');",
        ))
    }

    @Test
    fun `symbolic RHS stays explicitly unsupported until callability is modeled`() {
        val outcome = analyze("unresolvedRight")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.states.isEmpty())
        assertTrue(outcome.unsupportedPaths.any { "Unresolved instanceof RHS callability" in it },
            "${outcome.unsupportedPaths}")

        replay(listOf(
            "if (new RuntimeInstanceof().unresolvedRight(InstanceA) !== true) throw Error('callable RHS');",
            "let threwTypeError = false; try { new RuntimeInstanceof().unresolvedRight(42); } " +
                "catch (error) { threwTypeError = error instanceof TypeError; } " +
                "if (!threwTypeError) throw Error('non-callable RHS');",
        ))
    }

    @Test
    fun `constructor typed input has an explicit unsupported outcome`() {
        val outcome = analyze("constructorParameter")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.states.isEmpty())
        assertTrue(outcome.unsupportedPaths.any { "Constructor-typed parameter" in it },
            "${outcome.unsupportedPaths}")

        replay(listOf(
            "if (new RuntimeInstanceof().constructorParameter(InstanceA) !== true) throw Error('A input');",
            "if (new RuntimeInstanceof().constructorParameter(InstanceB) !== false) throw Error('B input');",
        ))
    }

    @Test
    fun `constructor containing input types have explicit unsupported outcomes`() {
        val methods = listOf(
            "constructorUnionParameter",
            "nullableConstructorParameter",
            "constructorAliasParameter",
            "constructorIntersectionParameter",
            "genericConstructorParameter",
        )

        methods.forEach { methodName ->
            val outcome = analyze(methodName)

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.states.isEmpty(), "$methodName: ${outcome.states}")
            assertTrue(outcome.unsupportedPaths.any { "Constructor-typed parameter" in it },
                "$methodName: ${outcome.unsupportedPaths}")
        }

        replay(listOf(
            "if (new RuntimeInstanceof().constructorUnionParameter(InstanceA) !== true) throw Error('union A');",
            "if (new RuntimeInstanceof().constructorUnionParameter(InstanceB) !== true) throw Error('union B');",
            "if (new RuntimeInstanceof().nullableConstructorParameter(null) !== true) throw Error('nullable null');",
            "if (new RuntimeInstanceof().nullableConstructorParameter(InstanceA) !== true) throw Error('nullable A');",
            "if (new RuntimeInstanceof().constructorAliasParameter(InstanceA) !== true) throw Error('alias');",
            "if (new RuntimeInstanceof().constructorIntersectionParameter(InstanceA) !== true) throw Error('intersection');",
            "if (new RuntimeInstanceof().genericConstructorParameter(InstanceA) !== true) throw Error('generic');",
        ))
    }

    @Test
    fun `constructor arrays have an explicit unsupported outcome`() {
        val methods = listOf("constructorArrayParameter", "constructorNestedArrayParameter")

        methods.forEach { methodName ->
            val outcome = analyze(methodName)

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.states.isEmpty(), "$methodName: ${outcome.states}")
            assertTrue(outcome.unsupportedPaths.any { "Constructor-typed parameter" in it },
                "$methodName: ${outcome.unsupportedPaths}")
        }

        replay(listOf(
            "if (new RuntimeInstanceof().constructorArrayParameter([InstanceA]) !== true) throw Error('array');",
            "if (new RuntimeInstanceof().constructorNestedArrayParameter([[InstanceA]]) !== true) " +
                "throw Error('nested array');",
        ))
    }

    @Test
    fun `constructor tuple has an explicit unsupported outcome`() {
        val outcome = analyze("constructorTupleParameter")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.states.isEmpty())
        assertTrue(outcome.unsupportedPaths.any { "Constructor-typed parameter" in it },
            "${outcome.unsupportedPaths}")

        replay(listOf(
            "if (new RuntimeInstanceof().constructorTupleParameter([InstanceA]) !== true) throw Error('tuple');"
        ))
    }

    @Test
    fun `constructor value in class type argument has an explicit unsupported outcome`() {
        val outcome = analyze("constructorBoxParameter")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.states.isEmpty(), "${outcome.states}")
        assertTrue(outcome.unsupportedPaths.any { "Constructor-typed parameter" in it },
            "${outcome.unsupportedPaths}")

        replay(listOf(
            "const box = new InstanceConstructorBox(); box.value = InstanceA; " +
                "if (new RuntimeInstanceof().constructorBoxParameter(box) !== true) throw Error('box');",
        ))
    }

    @Test
    fun `constructor values in object fields have explicit unsupported outcomes`() {
        val methods = listOf(
            "structuralConstructorParameter",
            "recursiveConstructorParameter",
            "inheritedConstructorParameter",
        )

        methods.forEach { methodName ->
            val outcome = analyze(methodName)

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.states.isEmpty(), "$methodName: ${outcome.states}")
            assertTrue(outcome.unsupportedPaths.any { "Constructor-typed parameter" in it },
                "$methodName: ${outcome.unsupportedPaths}")
        }

        replay(listOf(
            "if (new RuntimeInstanceof().structuralConstructorParameter({ ctor: InstanceA }) !== true) " +
                "throw Error('structural');",
            "const holder = new InstanceRecursiveHolder(); holder.ctor = InstanceA; " +
                "if (new RuntimeInstanceof().recursiveConstructorParameter(holder) !== true) throw Error('recursive');",
            "const inherited = new InstanceInheritedHolder(); inherited.ctor = InstanceA; " +
                "if (new RuntimeInstanceof().inheritedConstructorParameter(inherited) !== true) throw Error('inherited');",
        ))
    }

    @Test
    fun `inherited generic fields have explicit unsupported outcomes`() {
        val methods = listOf("inheritedGenericConstructorParameter", "inheritedGenericNumberParameter")

        methods.forEach { methodName ->
            val outcome = analyze(methodName)

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
            assertTrue(outcome.states.isEmpty(), "$methodName: ${outcome.states}")
            assertTrue(outcome.unsupportedPaths.any { "Unresolved inherited generic" in it },
                "$methodName: ${outcome.unsupportedPaths}")
        }

        replay(listOf(
            "const holder = new InstanceInheritedGenericConstructor(); holder.ctor = InstanceA; " +
                "if (new RuntimeInstanceof().inheritedGenericConstructorParameter(holder) !== true) " +
                "throw Error('inherited generic');",
            "const numberHolder = new InstanceInheritedGenericNumber(); numberHolder.ctor = 1; " +
                "if (new RuntimeInstanceof().inheritedGenericNumberParameter(numberHolder) !== true) " +
                "throw Error('inherited generic number');",
        ))
    }

    @Test
    fun `concrete inherited number field remains supported`() {
        val outcome = analyze("inheritedConcreteNumberParameter")

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.isEmpty(), "${outcome.unsupportedPaths}")
        assertTrue(outcome.states.isNotEmpty())

        replay(listOf(
            "const holder = new InstanceInheritedConcreteNumber(); holder.value = 1; " +
                "if (new RuntimeInstanceof().inheritedConcreteNumberParameter(holder) !== true) " +
                "throw Error('inherited concrete number');",
        ))
    }

    @Test
    fun `unsupported constructor input does not hide another entrypoint`() {
        val methods = listOf("constructorParameter", "directConstructor")
            .map { getMethod(methodName = it, className = "RuntimeInstanceof") }
        val outcome = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
            machine.analyzeWithOutcome(methods = methods)
        }

        assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
        assertTrue(outcome.unsupportedPaths.any { "constructorParameter" in it }, "${outcome.unsupportedPaths}")
        assertTrue(outcome.states.isNotEmpty())
        outcome.states.forEach { state -> assertIs<TsMethodResult.Success>(state.methodResult) }
    }

    private fun analyze(methodName: String) = TsMachine(scene, options = machineOptions, tsOptions = TsOptions()).use { machine ->
        machine.analyzeWithOutcome(methods = listOf(getMethod(methodName = methodName, className = "RuntimeInstanceof")))
    }

    private fun replay(assertions: List<String>) {
        val script = directory.resolve("runtime-instanceof.ts")
        val output = directory.resolve("runtime-instanceof.out")
        script.writeText(buildString {
            appendLine(getResourcePath(tsPath).readText())
            assertions.forEach(::appendLine)
        })

        val process = ProcessBuilder("node", "--experimental-strip-types", script.toString())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start()

        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Node replay timed out")
        assertEquals(0, process.exitValue(), output.readText())
    }

    private val machineOptions = UMachineOptions(
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        stopOnCoverage = 0,
        timeout = 30.seconds,
    )
}
