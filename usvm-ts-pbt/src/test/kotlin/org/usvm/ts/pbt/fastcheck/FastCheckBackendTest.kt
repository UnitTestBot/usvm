package org.usvm.ts.pbt.fastcheck

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.ExecutionKind
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.JsNumber
import org.usvm.ts.pbt.model.JsNumberKind
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcesRoot
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FastCheckBackendTest {
    private val backend = FastCheckBackend(sourceRoots = listOf(testResourcesRoot()))

    @Test
    fun `executes TypeScript source without a user compilation step`() {
        val result = backend.run(
            property = property(predicate = "alwaysTrue"),
            configuration = configuration,
        )

        assertEquals(PropertyRunStatus.SUCCESS, result.status)
        assertEquals(20, result.numRuns)
    }

    @Test
    fun `passes multiple generated inputs to the predicate in declaration order`() {
        val definition = property(
            predicate = "sumIsCommutative",
            domains = listOf(
                IntegerDomain(min = -100, max = 100),
                IntegerDomain(min = -100, max = 100),
            ),
        )

        val result = backend.run(definition, configuration)

        assertEquals(PropertyRunStatus.SUCCESS, result.status)
    }

    @Test
    fun `replays a failing property from its reported seed and path`() {
        val first = backend.run(
            property = property(predicate = "isNegative"),
            configuration = configuration,
        )
        assertEquals(PropertyRunStatus.FAILURE, first.status)
        assertEquals(PropertyFailureKind.PROPERTY, first.failure?.kind)
        assertNotNull(first.counterexample)
        val replayPath = assertNotNull(first.replayPath)

        val replay = backend.run(
            property = property(predicate = "isNegative"),
            configuration = configuration.copy(seed = first.seed, replayPath = replayPath),
        )

        assertEquals(first.counterexample, replay.counterexample)
        assertEquals(first.replayPath, replay.replayPath)
    }

    @Test
    fun `supports asynchronous predicates and preconditions`() {
        val definition = property(
            predicate = "asyncAlwaysTrue",
            predicateKind = ExecutionKind.ASYNC,
            precondition = TypeScriptEntryPoint(
                module = MODULE,
                exportName = "asyncIsOne",
                executionKind = ExecutionKind.ASYNC,
            ),
            domain = IntegerDomain(min = 0, max = 1),
        )

        val result = backend.run(definition, configuration)

        assertEquals(PropertyRunStatus.SUCCESS, result.status)
        assertTrue(result.numSkips > 0)
    }

    @Test
    fun `explicit examples use the normal predicate and shrinking lifecycle`() {
        val seven = JsConcreteValue.number(7.0)

        val result = backend.run(
            property = property(
                predicate = "isNotSeven",
                domain = IntegerDomain(min = 0, max = 100),
            ),
            configuration = configuration.copy(examples = listOf(listOf(seven))),
        )

        assertEquals(PropertyRunStatus.FAILURE, result.status)
        assertEquals(listOf(seven), result.counterexample)
    }

    @Test
    fun `asynchronous timeout is a structured failure`() {
        val result = backend.run(
            property = property(predicate = "neverCompletes", predicateKind = ExecutionKind.ASYNC),
            configuration = configuration.copy(numRuns = 1, timeoutMillis = 20),
        )

        assertEquals(PropertyRunStatus.FAILURE, result.status)
        assertEquals(PropertyFailureKind.TIMEOUT, result.failure?.kind)
    }

    @Test
    fun `missing entry point is a typed backend error`() {
        val error = assertFailsWith<PbtBackendException> {
            backend.run(
                property = property(predicate = "alwaysTrue").copy(
                    predicate = TypeScriptEntryPoint(
                        module = "missing.ts",
                        exportName = "predicate",
                    ),
                ),
                configuration = configuration,
            )
        }

        assertEquals(BackendErrorKind.ENTRY_POINT, error.kind)
        assertEquals("entrypoint.module.not-found", error.code)
    }

    @Test
    fun `invalid explicit examples are rejected before Node starts`() {
        val missingNodeBackend = FastCheckBackend(
            sourceRoots = listOf(testResourcesRoot()),
            nodeExecutable = "definitely-not-a-node-executable",
        )

        val error = assertFailsWith<PbtBackendException> {
            missingNodeBackend.run(
                property = property(predicate = "alwaysTrue"),
                configuration = configuration.copy(examples = listOf(emptyList())),
            )
        }

        assertEquals(BackendErrorKind.INVALID_REQUEST, error.kind)
        assertEquals("backend.examples.arity", error.code)

        val invalidNumber = JsConcreteValue.Number(
            JsNumber(value = JsNumberKind.FINITE, bits = "invalid"),
        )
        val encodingError = assertFailsWith<PbtBackendException> {
            missingNodeBackend.run(
                property = property(predicate = "alwaysTrue"),
                configuration = configuration.copy(examples = listOf(listOf(invalidNumber))),
            )
        }

        assertEquals(BackendErrorKind.INVALID_REQUEST, encodingError.kind)
        assertEquals("backend.examples.value.invalid", encodingError.code)
        assertEquals("examples[0][0]", encodingError.path)
    }

    @Test
    fun `explicit examples outside recursive domains are rejected before Node starts`() {
        val missingNodeBackend = FastCheckBackend(
            sourceRoots = listOf(testResourcesRoot()),
            nodeExecutable = "definitely-not-a-node-executable",
        )
        val definition = property(
            predicate = "alwaysTrue",
            domain = ArrayDomain(
                element = IntegerDomain(min = 0, max = 1),
                minLength = 1,
                maxLength = 1,
            ),
        )
        val outOfRangeElement = JsConcreteValue.Array(
            elements = listOf(JsConcreteValue.number(2.0)),
        )

        val error = assertFailsWith<PbtBackendException> {
            missingNodeBackend.run(
                property = definition,
                configuration = configuration.copy(examples = listOf(listOf(outOfRangeElement))),
            )
        }

        assertEquals(BackendErrorKind.INVALID_REQUEST, error.kind)
        assertEquals("backend.examples.domain", error.code)
        assertEquals("examples[0][0]", error.path)
    }

    @Test
    fun `negative zero is rejected for integer domains before Node starts`() {
        val missingNodeBackend = FastCheckBackend(
            sourceRoots = listOf(testResourcesRoot()),
            nodeExecutable = "definitely-not-a-node-executable",
        )

        val error = assertFailsWith<PbtBackendException> {
            missingNodeBackend.run(
                property = property(predicate = "alwaysTrue"),
                configuration = configuration.copy(
                    examples = listOf(listOf(JsConcreteValue.number(-0.0))),
                ),
            )
        }

        assertEquals(BackendErrorKind.INVALID_REQUEST, error.kind)
        assertEquals("backend.examples.domain", error.code)
        assertEquals("examples[0][0]", error.path)
    }

    private fun property(
        predicate: String,
        predicateKind: ExecutionKind = ExecutionKind.SYNC,
        precondition: TypeScriptEntryPoint? = null,
        domain: PropertyDomain = IntegerDomain(min = -10, max = 10),
        domains: List<PropertyDomain> = listOf(domain),
    ) = PropertyDefinition(
        id = PropertyId("example.$predicate"),
        inputs = domains.mapIndexed { index, inputDomain ->
            PropertyInput(name = "argument$index", domain = inputDomain)
        },
        predicate = TypeScriptEntryPoint(
            module = MODULE,
            exportName = predicate,
            executionKind = predicateKind,
        ),
        precondition = precondition,
    )

    private companion object {
        const val MODULE = "properties/execution/ExecutionProperties.ts"

        val configuration = PropertyRunConfiguration(
            seed = 42,
            numRuns = 20,
            timeoutMillis = 1_000,
        )
    }
}
