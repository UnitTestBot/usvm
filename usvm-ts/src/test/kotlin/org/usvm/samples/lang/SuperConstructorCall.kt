package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.io.TempDir
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.call.TsModelUnknownCallDispatcher
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallModelCatalog
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.assertNodeReplay
import org.usvm.util.eq
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SuperConstructorCall : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    override val scene: EtsScene = loadScene("/samples/lang/SuperConstructorCall.ts")

    @Test
    fun `implicit derived constructor calls the parent once`() {
        val method = getMethod("implicitSuper")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 7 },
            invariants = arrayOf({ result -> result eq 7 }),
        )
    }

    @Test
    fun `explicit super preserves arguments and derived receiver`() {
        val method = getMethod("explicitSuper")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 8 },
            invariants = arrayOf({ result -> result eq 8 }),
        )
    }

    @Test
    fun `allocated receiver initializes every declared superclass in order`() {
        val method = getMethod(methodName = "nestedSuper")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 71 },
            invariants = arrayOf({ result -> result eq 71 }),
        )
    }

    @Test
    fun `ordinary constructor property remains callable`() {
        val method = getMethod("shadowedConstructorProperty")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 17 },
            invariants = arrayOf({ result -> result eq 17 }),
        )
    }

    @Test
    fun `symbolic entry receiver calls each superclass constructor once`() {
        val method = getMethod(methodName = "constructor", className = "SymbolicConstructorChild")
        val constructorOptions = options.copy(timeout = 5.seconds, stepsFromLastCovered = 200L)

        val tests = runner(method, constructorOptions)

        assertTrue(tests.isNotEmpty())
        val results = tests.map { test -> assertIs<TsTestValue.TsClass>(test.returnValue) }
        results.forEach { result ->
            val value = assertIs<TsTestValue.TsNumber>(result.properties["value"])
            assertEquals(expected = 71.0, actual = value.number)
        }
        replayConstructor(className = "SymbolicConstructorChild", expectedValue = 71)
    }

    @Test
    fun `symbolic entry receiver forwards an implicit superclass constructor`() {
        val method = getMethod(methodName = "constructor", className = "SymbolicImplicitConstructorChild")
        val constructorOptions = options.copy(timeout = 5.seconds, stepsFromLastCovered = 200L)

        val tests = runner(method, constructorOptions)

        assertTrue(tests.isNotEmpty())
        val results = tests.map { test -> assertIs<TsTestValue.TsClass>(test.returnValue) }
        results.forEach { result ->
            val value = assertIs<TsTestValue.TsNumber>(result.properties["value"])
            assertEquals(expected = 13.0, actual = value.number)
        }
        replayConstructor(className = "SymbolicImplicitConstructorChild", expectedValue = 13)
    }

    @Test
    fun `missing superclass constructor reaches the unknown call boundary once`() {
        val missingBaseScene = loadScene("/samples/lang/MissingSuperclassConstructor.ts")
        val missingBaseClass = missingBaseScene.projectClasses.single { it.name == "MissingSuperclassConstructor" }
        val method = missingBaseClass.methods.single { it.name == "constructor" }
        val calls = mutableListOf<TsUnknownCall>()
        val fallback = TsModelUnknownCallDispatcher(
            models = TsUnknownCallModelCatalog(models = emptyList()),
            fallback = TsResidualCallPolicy.STOP_PATH,
        )
        val constructorOptions = options.copy(timeout = 5.seconds, stepsFromLastCovered = 200L)

        TsMachine(
            scene = missingBaseScene,
            options = constructorOptions,
            tsOptions = tsOptions,
            unknownCallDispatcher = { scope, call ->
                calls += call
                fallback.dispatch(scope, call)
            },
        ).use { machine ->
            val states = machine.analyze(listOf(method))
            assertTrue(states.isEmpty())
        }

        assertEquals(TsUnknownCallFailureReason.RECEIVER_CLASS_NOT_FOUND, calls.single().failureReason)
        assertEquals(expected = "constructor", actual = calls.single().callee.name)
    }

    private fun replayConstructor(className: String, expectedValue: Int) {
        val source = getResourcePath("/samples/lang/SuperConstructorCall.ts").readText()
        val script = source + "\nif (new $className().value !== $expectedValue) throw Error('constructor replay');"

        assertNodeReplay(
            source = script,
            directory = directory,
            name = className,
            timeoutMessage = "Constructor replay timed out for $className",
        )
    }
}
