package org.usvm.ts.pbt.examples

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyAssertion
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.PropertyOperand
import org.usvm.ts.pbt.model.PropertySourceIdentity
import org.usvm.ts.pbt.model.PropertySourcePoint
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcesRoot
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RealSuiteRegistrationTest {
    @Test
    fun `original fast-check uniqueness oracle retains its assertion and failure`() {
        val source = testResourcesRoot().resolve(MODULE)
        val sourceHash = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(source))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        val definition = property(
            exportName = "originalUniqueOracle",
            sourceIdentity = PropertySourceIdentity(
                sourceSha256 = sourceHash,
                buildSha256 = sourceHash,
                buildScope = "direct TypeScript source input; transpiled output is not pinned",
            ),
        )
        val backend = FastCheckBackend(sourceRoots = listOf(testResourcesRoot()))
        val explicitExample = listOf(
            JsConcreteValue.Array(
                listOf(
                    JsConcreteValue.number(1.0),
                    JsConcreteValue.number(1.0),
                ),
            ),
        )

        val manifest = PropertyManifestJson.decode(PropertyManifestJson.encode(definition.toManifest()))
        val failed = backend.run(definition, PropertyRunConfiguration(examples = listOf(explicitExample), numRuns = 1))
        val passed = backend.run(
            property(exportName = "correctUniqueOracle", sourceIdentity = definition.sourceIdentity),
            PropertyRunConfiguration(seed = 42, numRuns = 30),
        )

        assertEquals("fast-check.array-bias.unique", manifest.propertyId)
        assertEquals("no-duplicates", manifest.assertions.single().id)
        assertEquals(
            listOf("filtered-length", "set-size"),
            manifest.assertions.single().operands.map(PropertyOperand::id),
        )
        assertEquals(sourceHash, manifest.sourceIdentity?.sourceSha256)
        assertEquals(PropertyRunStatus.FAILURE, failed.status)
        assertEquals(PropertyFailureKind.PROPERTY, failed.failure?.kind)
        assertNotNull(failed.counterexample)
        assertEquals(PropertyRunStatus.SUCCESS, passed.status)
    }

    private fun property(exportName: String, sourceIdentity: PropertySourceIdentity?) = PropertyDefinition(
        id = PropertyId("fast-check.array-bias.unique"),
        inputs = listOf(
            PropertyInput(
                name = "values",
                domain = ArrayDomain(IntegerDomain(min = 0, max = 10), minLength = 0, maxLength = 8),
                generatorId = "fast-check.array-integers",
            ),
        ),
        predicate = TypeScriptEntryPoint(module = MODULE, exportName = exportName),
        assertions = listOf(
            PropertyAssertion(
                id = "no-duplicates",
                source = PropertySourcePoint(module = MODULE, line = 14, column = 5),
                testedCall = PropertySourcePoint(module = MODULE, line = 13, column = 22),
                operands = listOf(
                    PropertyOperand(
                        id = "filtered-length",
                        source = PropertySourcePoint(module = MODULE, line = 14, column = 12),
                    ),
                    PropertyOperand(
                        id = "set-size",
                        source = PropertySourcePoint(module = MODULE, line = 14, column = 34),
                    ),
                ),
            ),
        ),
        sourceIdentity = sourceIdentity,
    )

    private companion object {
        const val MODULE = "properties/real/ArrayArbitraryProperty.ts"
    }
}
