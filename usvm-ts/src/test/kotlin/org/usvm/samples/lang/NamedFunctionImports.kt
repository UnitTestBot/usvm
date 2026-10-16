package org.usvm.samples.lang

import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.ANONYMOUS_METHOD_PREFIX
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NamedFunctionImports : TsMethodTestRunner() {
    override val scene: EtsScene = loadEtsProjectAutoConvert(
        getResourcePath(res = "/samples/lang/named-function-imports"),
        useArkAnalyzerTypeInference = null,
    )

    @Test
    fun `anonymous module functions retain their exact declaring file`() {
        val method = getMethod(methodName = "anonymousOwners")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 1122 },
            invariants = arrayOf({ result -> result eq 1122 }),
        )
    }

    @Test
    fun `unavailable function bodies remain explicit unsupported prefixes`() {
        for (anonymous in listOf(false, true)) {
            val caseScene = loadEtsProjectAutoConvert(
                getResourcePath(res = "/samples/lang/named-function-imports"),
                useArkAnalyzerTypeInference = null,
            )
            val owner = caseScene.projectClasses.single {
                it.signature.file.fileName.substringAfterLast('/') == "First.ts"
            }
            val target = owner.methods.single { method ->
                if (anonymous) method.name.startsWith(ANONYMOUS_METHOD_PREFIX) else method.name == "named"
            }
            // Model a producer that retained the function reference but could not supply its executable body.
            target.body.cfg = EtsBlockCfg.EMPTY
            val method = caseScene.projectClasses.single { it.name == "NamedFunctionImports" }
                .methods.single { it.name == "anonymousOwners" }
            val limitations = mutableListOf<TsRuntimeFeatureLimitationEvent>()
            val observer = object : TsInterpreterObserver {
                override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                    limitations += event
                }
            }

            val outcome = TsMachine(
                scene = caseScene,
                options = options.copy(stopOnCoverage = 0, throwExceptionOnStepFailure = true),
                tsOptions = TsOptions(),
                observer = observer,
            ).use { machine -> machine.analyzeWithOutcome(methods = listOf(method)) }

            assertTrue(outcome.states.isEmpty())
            assertTrue(outcome.infeasibleEntryPoints.isEmpty())
            assertTrue(outcome.unsupportedPaths.any { "Cannot uniquely resolve" in it }, outcome.toString())
            assertEquals(
                listOf(TsRuntimeFeatureLimitationReason.FUNCTION_REFERENCE_RESOLUTION),
                limitations.map { it.reason },
            )
        }
    }

    @Test
    fun `same named functions in different files retain their owners`() {
        val method = getMethod(methodName = "distinctOwners")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 12 },
            invariants = arrayOf({ result -> result eq 12 }),
        )
    }

    @Test
    fun `namespace import reads the current function while alias retains its original target`() {
        val method = getMethod(methodName = "namespaceLiveBinding")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 13 },
            invariants = arrayOf({ result -> result eq 13 }),
        )
    }

    @Test
    fun `namespace direct and optional calls explicitly report their unrepresented receiver`() {
        listOf("namespaceDirectCall", "namespaceOptionalCall").forEach { methodName ->
            val method = getMethod(methodName = methodName)
            val outcome = TsMachine(
                scene = scene,
                options = UMachineOptions(solverType = SolverType.YICES),
                tsOptions = TsOptions(),
            ).use { machine ->
                machine.analyzeWithOutcome(methods = listOf(method))
            }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
            assertTrue(outcome.states.isEmpty(), methodName)
            assertTrue(outcome.unsupportedPaths.any { "namespace.receiverIsUndefined" in it }, "$methodName: $outcome")
        }
    }
}
