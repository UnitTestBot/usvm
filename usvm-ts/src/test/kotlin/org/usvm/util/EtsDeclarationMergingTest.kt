package org.usvm.util

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.CoverageZone
import org.usvm.StateCollectionStrategy
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EtsDeclarationMergingTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        projectFiles = listOf(
            loadEtsFileAutoConvert(
                path = getResourcePath("/models/DeclarationMergedEntry.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )

    @Test
    fun `a source class augmented by an interface starts and executes its own method`() {
        val declarations = scene.projectClasses.filter { it.name == "DeclarationMergedEntry" }
        val runtime = declarations.single { it.category == EtsClassCategory.CLASS }
        val augmentation = declarations.single { it.category == EtsClassCategory.INTERFACE }
        val method = runtime.methods.single { it.name == "classify" }

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { _, value -> value eq 1 },
            { _, value -> value eq 2 },
            invariants = arrayOf({ _, value -> (value eq 1) || (value eq 2) }),
        )

        assertEquals(2, declarations.size)
        assertSame(runtime, EtsHierarchy(scene).classesForType(runtime.type).single())
        assertTrue(augmentation.fields.any { it.name == "additional" })
        assertTrue(augmentation in scene.projectClasses)
    }

    @Test
    fun `runtime lookup is independent of class and augmentation declaration order`() {
        val declarations = scene.projectClasses.filter { it.name == "DeclarationMergedEntry" }
        val runtime = declarations.single { it.category == EtsClassCategory.CLASS }
        for (ordered in listOf(declarations, declarations.reversed())) {
            val file = EtsFile(signature = runtime.signature.file, classes = ordered, namespaces = emptyList())
            val orderedScene = EtsScene(projectFiles = listOf(file))

            val resolved = EtsHierarchy(orderedScene).classesForType(runtime.type)

            assertEquals(1, resolved.size)
            assertSame(runtime, resolved.single())
            assertEquals(2, orderedScene.projectClasses.size)
        }
    }

    @Test
    fun `an explicit class coverage target selects the merged source runtime class`() {
        val declarations = scene.projectClasses.filter { it.name == "DeclarationMergedEntry" }
        val runtime = declarations.single { it.category == EtsClassCategory.CLASS }
        val method = runtime.methods.single { it.name == "classify" }
        val classOptions = options.copy(
            coverageZone = CoverageZone.CLASS,
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
        )

        val result = TsMachine(
            scene = scene,
            options = classOptions,
            tsOptions = tsOptions.copy(classCoverageTargets = setOf(runtime.signature)),
        ).use { machine ->
            machine.analyzeWithMetadata(methods = listOf(method))
        }

        assertFalse(result.engineFailed)
        assertEquals(2, result.states.size)
        assertTrue(result.states.all { it.entrypoint == method })
        assertEquals(2, scene.projectClasses.count { it.signature == runtime.signature })
    }

    @Test
    fun `two runtime classes with the same signature remain an error`() {
        val signature = EtsClassSignature(
            name = "DuplicatedRuntimeClass",
            file = EtsFileSignature(projectName = "test", fileName = "duplicates.ts"),
        )
        val first = declaration(signature = signature, category = EtsClassCategory.CLASS)
        val second = declaration(signature = signature, category = EtsClassCategory.CLASS)
        val augmentation = declaration(signature = signature, category = EtsClassCategory.INTERFACE)
        val file = EtsFile(
            signature = signature.file,
            classes = listOf(first, augmentation, second),
            namespaces = emptyList(),
        )
        val duplicatedScene = EtsScene(projectFiles = listOf(file))

        assertFailsWith<IllegalArgumentException> {
            EtsHierarchy(duplicatedScene).classesForType(first.type)
        }
    }

    private fun declaration(signature: EtsClassSignature, category: EtsClassCategory): EtsClass = EtsClassImpl(
        signature = signature,
        fields = emptyList(),
        methods = emptyList(),
        category = category,
    )
}
