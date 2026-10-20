package org.usvm.experiments

import io.ksmt.expr.KBitVec32Value
import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcClasspath
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.JcMethodCall
import org.usvm.machine.JcMethodEntrypointInst
import org.usvm.machine.JcResidualCallPolicy
import org.usvm.machine.interpreter.JcSimpleValueResolver
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.samples.invokes.ResidualCallProfiles
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class JavaFourProfileSmokeTest {
    @Test
    fun `all four modes retain prefixes or reach native call successors explicitly`() = runBlocking {
        val samples = File(ResidualCallProfiles::class.java.protectionDomain.codeSource.location.toURI())

        for (modelsEnabled in listOf(false, true)) {
            withPilotClasspath(libraries = listOf(samples), approximationsEnabled = modelsEnabled) { classpath ->
                val classpathFiles = classpath.locations.map { it.jarOrFolder.canonicalFile }.toSet()
                for (name in listOf("usvm.jvm.api.jar.path", "usvm.jvm.approximations.jar.path")) {
                    val artifact = File(requireNotNull(System.getenv(name))).canonicalFile
                    assertEquals(modelsEnabled, artifact in classpathFiles, name)
                }

                for (policy in JcResidualCallPolicy.entries) {
                    assertProfile(classpath = classpath, modelsEnabled = modelsEnabled, policy = policy)
                }
            }
        }
    }

    private fun assertProfile(classpath: JcClasspath, modelsEnabled: Boolean, policy: JcResidualCallPolicy) {
        val owner = requireNotNull(classpath.findClassOrNull(ResidualCallProfiles::class.java.name))
        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            solverType = SolverType.YICES,
            timeout = 5.seconds,
            throwExceptionOnStepFailure = true,
        )
        var approximationAttempts = 0
        var modeledArraycopyApplications = 0
        val observer = object : JcInterpreterObserver {
            override fun onEntryPoint(
                simpleValueResolver: JcSimpleValueResolver,
                stmt: JcMethodEntrypointInst,
                stepScope: JcStepScope,
            ) = Unit

            override fun onMethodApproximation(call: JcMethodCall, applied: Boolean, elapsedNanos: Long, failure: String?) {
                approximationAttempts++
                val appliedArraycopy = applied && call.method.enclosingClass.name == "java.lang.System" &&
                    call.method.name == "arraycopy"
                if (appliedArraycopy) modeledArraycopyApplications++
            }
        }

        val methodNames = listOf(
            "successor", "voidSuccessor", "independentResults", "primitiveBounds", "modeledNative", "ordinaryBody",
        )
        for (methodName in methodNames) {
            val method = owner.declaredMethods.single { it.name == methodName }

            val states = JcMachine(
                cp = classpath,
                options = options,
                jcMachineOptions = JcMachineOptions(
                    forkOnImplicitExceptions = false,
                    enableMethodApproximations = modelsEnabled,
                    residualCallPolicy = policy,
                ),
                interpreterObserver = observer,
            ).use { machine -> machine.analyze(method) }

            assertTrue(states.isNotEmpty(), "$modelsEnabled/$policy/$methodName")
            val methodHasNoResidualCalls = methodName == "ordinaryBody" || methodName == "modeledNative" && modelsEnabled
            val stopsAtNativeCall = policy == JcResidualCallPolicy.STOP && !methodHasNoResidualCalls
            if (stopsAtNativeCall) {
                assertTrue(states.all { it.residualCallStop != null })
                assertTrue(states.all { it.methodResult == JcMethodResult.NoCall })
                assertTrue(states.all { it.callStack.isNotEmpty() })
                assertTrue(states.all { it.clone().residualCallStop == it.residualCallStop })
                continue
            }

            assertTrue(states.all { it.methodResult is JcMethodResult.Success })
            assertFalse(states.any { it.residualCallStop != null })
            val values = states.map(::returnValue).toSet()
            val expected = when (methodName) {
                "independentResults" -> setOf(1, 2)
                "primitiveBounds" -> setOf(1)
                "modeledNative" -> if (modelsEnabled) setOf(17) else setOf(0)
                else -> setOf(17)
            }
            assertEquals(expected, values, "$modelsEnabled/$policy/$methodName")
        }

        if (modelsEnabled) {
            assertTrue(approximationAttempts > 0)
            assertTrue(modeledArraycopyApplications > 0)
        } else {
            assertEquals(0, approximationAttempts)
            assertEquals(0, modeledArraycopyApplications)
        }
    }

    private fun returnValue(state: JcState): Int {
        val result = state.methodResult as JcMethodResult.Success
        val concrete = state.models.first().eval(result.value) as KBitVec32Value
        return concrete.intValue
    }
}
