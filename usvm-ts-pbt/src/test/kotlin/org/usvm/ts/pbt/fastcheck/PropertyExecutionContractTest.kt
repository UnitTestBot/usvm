package org.usvm.ts.pbt.fastcheck

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcesRoot
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PropertyExecutionContractTest {
    private val backend = FastCheckBackend(sourceRoots = listOf(testResourcesRoot()))

    @Test
    fun `true precondition admits the input`() {
        val result = backend.run(
            property = property(
                predicate = "alwaysTrue",
                precondition = "truePrecondition",
            ),
            configuration = configuration,
        )

        assertEquals(PropertyRunStatus.SUCCESS, result.status)
        assertEquals(0, result.numSkips)
    }

    @Test
    fun `false precondition exhaustion is not a property violation`() {
        val result = backend.run(
            property = property(
                predicate = "alwaysTrue",
                precondition = "falsePrecondition",
            ),
            configuration = configuration.copy(numRuns = 1),
        )

        assertEquals(PropertyRunStatus.FAILURE, result.status)
        assertEquals(PropertyFailureKind.PRECONDITION_EXHAUSTED, result.failure?.kind)
        assertEquals("PreconditionExhausted", result.failure?.errorName)
        assertNull(result.counterexample)
    }

    @Test
    fun `throwing and non-boolean preconditions are execution errors`() {
        val cases = listOf(
            ContractErrorCase(
                exportName = "throwingPrecondition",
                expectedCode = "entrypoint.precondition.threw",
                expectedPath = "manifest.precondition",
            ),
            ContractErrorCase(
                exportName = "nonBooleanPrecondition",
                expectedCode = "entrypoint.result.invalid",
                expectedPath = "manifest.precondition.result",
            ),
        )

        cases.forEach { case ->
            val error = assertFailsWith<PbtBackendException> {
                backend.run(
                    property = property(
                        predicate = "alwaysTrue",
                        precondition = case.exportName,
                    ),
                    configuration = configuration,
                )
            }

            assertEquals(BackendErrorKind.ENTRY_POINT, error.kind)
            assertEquals(case.expectedCode, error.code)
            assertEquals(case.expectedPath, error.path)
        }
    }

    @Test
    fun `false throwing and assertion predicates are property violations`() {
        listOf("falsePredicate", "throwingPredicate", "assertionPredicate").forEach { predicate ->
            val result = backend.run(
                property = property(predicate = predicate),
                configuration = configuration,
            )

            assertEquals(PropertyRunStatus.FAILURE, result.status)
            assertEquals(PropertyFailureKind.PROPERTY, result.failure?.kind)
            assertNotNull(result.counterexample)
        }
    }

    @Test
    fun `non-boolean predicate is an execution error`() {
        val error = assertFailsWith<PbtBackendException> {
            backend.run(
                property = property(predicate = "nonBooleanPredicate"),
                configuration = configuration,
            )
        }

        assertEquals(BackendErrorKind.ENTRY_POINT, error.kind)
        assertEquals("entrypoint.result.invalid", error.code)
        assertEquals("manifest.predicate.result", error.path)
    }

    @Test
    fun `a predicate may catch and classify an expected exception`() {
        val result = backend.run(
            property = property(predicate = "catchesExpectedException"),
            configuration = configuration,
        )

        assertEquals(PropertyRunStatus.SUCCESS, result.status)
    }

    @Test
    fun `special values retain their identity and argument order`() {
        val domains = listOf(
            ConstantDomain(value = JsConcreteValue.Undefined),
            ConstantDomain(value = JsConcreteValue.Null),
            ConstantDomain(value = JsConcreteValue.number(-0.0)),
            ConstantDomain(value = JsConcreteValue.number(Double.NaN)),
            ConstantDomain(value = JsConcreteValue.number(Double.POSITIVE_INFINITY)),
            ConstantDomain(value = JsConcreteValue.number(Double.NEGATIVE_INFINITY)),
        )

        val result = backend.run(
            property = property(
                predicate = "recognizesSpecialValues",
                domains = domains,
            ),
            configuration = configuration,
        )

        assertEquals(PropertyRunStatus.SUCCESS, result.status)
    }

    @Test
    fun `predicate mutation is isolated between examples and generated samples`() {
        val original = JsConcreteValue.Array(
            elements = listOf(JsConcreteValue.number(1.0)),
        )
        val domain = ArrayDomain(
            element = IntegerDomain(min = 1, max = 1),
            minLength = 1,
            maxLength = 1,
        )

        val result = backend.run(
            property = property(
                predicate = "isolatesPredicateMutation",
                domains = listOf(domain),
            ),
            configuration = configuration.copy(
                numRuns = 2,
                examples = listOf(listOf(original)),
            ),
        )

        assertEquals(PropertyRunStatus.SUCCESS, result.status)
        assertEquals(2, result.numRuns)
    }

    @Test
    fun `shrinking and replay retain the input before predicate mutation`() {
        val domain = ArrayDomain(
            element = IntegerDomain(min = -10, max = 10),
            minLength = 1,
            maxLength = 3,
        )
        val definition = property(
            predicate = "mutatesAndFails",
            domains = listOf(domain),
        )

        val first = backend.run(
            property = definition,
            configuration = configuration,
        )
        val counterexample = assertNotNull(first.counterexample)
        val replayPath = assertNotNull(first.replayPath)
        val replay = backend.run(
            property = definition,
            configuration = configuration.copy(
                seed = first.seed,
                replayPath = replayPath,
            ),
        )

        assertEquals(PropertyRunStatus.FAILURE, first.status)
        assertTrue(first.numShrinks > 0)
        assertNotEquals(
            JsConcreteValue.Array(elements = listOf(JsConcreteValue.number(999.0))),
            counterexample.single(),
        )
        assertEquals(counterexample, replay.counterexample)
    }

    private fun property(
        predicate: String,
        precondition: String? = null,
        domains: List<PropertyDomain> = listOf(IntegerDomain(min = 0, max = 0)),
    ): PropertyDefinition = PropertyDefinition(
        id = PropertyId("contract.$predicate"),
        inputs = domains.mapIndexed { index, domain ->
            PropertyInput(name = "argument$index", domain = domain)
        },
        predicate = TypeScriptEntryPoint(
            module = MODULE,
            exportName = predicate,
        ),
        precondition = precondition?.let { exportName ->
            TypeScriptEntryPoint(
                module = MODULE,
                exportName = exportName,
            )
        },
    )

    private data class ContractErrorCase(
        val exportName: String,
        val expectedCode: String,
        val expectedPath: String,
    )

    private companion object {
        const val MODULE = "properties/contract/PropertyExecutionContract.ts"

        val configuration = PropertyRunConfiguration(
            seed = 42,
            numRuns = 5,
            timeoutMillis = 1_000,
        )
    }
}
