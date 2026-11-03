package org.usvm.util

import io.mockk.mockk
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsWellKnownSymbol
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.machine.TsContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EtsMemberIdentityTest {
    private val file = loadEtsFileAutoConvert(getResourcePath("/samples/types/ComputedClassMembers.ts"))
    private val scene = EtsScene(projectFiles = listOf(file))
    private val hierarchy = EtsHierarchy(scene)

    @Test
    fun `method resolution and inherited string lookup distinguish symbol declarations`() {
        val ctx = TsContext(scene = scene, components = mockk())
        val owner = scene.projectClasses.single { it.name == "IteratorCounter" }
        val stringMethods = owner.methods.filter { it.hasStringName("Symbol.iterator") }
        val symbolMethods = owner.methods.filter { it.signature.wellKnownSymbol == EtsWellKnownSymbol.ITERATOR }
        val stringMethod = stringMethods.single { !it.isStatic }

        assertEquals(stringMethods, ctx.resolveEtsMethods(stringMethod.signature))
        assertEquals(symbolMethods, ctx.resolveEtsMethods(symbolMethods.first().signature))
        assertEquals(stringMethod, hierarchy.nearestInstanceMethod(owner, methodName = "Symbol.iterator"))
    }

    @Test
    fun `field resolution keeps identity through exact inferred and fallback owner lookup`() {
        val ctx = TsContext(scene = scene, components = mockk())
        val owner = scene.projectClasses.single { it.name == "SymbolFieldCounter" }

        for (field in owner.fields) {
            val exact = ctx.resolveEtsField(instance = null, field = field.signature, hierarchy = hierarchy)
            val unknownOwner = field.signature.copy(enclosingClass = EtsClassSignature.UNKNOWN)
            val receiverTypes = listOf(
                EtsClassType(owner.signature),
                EtsUnclearRefType(name = owner.name, typeParameters = emptyList()),
            )

            assertEquals(TsResolutionResult.Unique(field), exact)
            for (type in receiverTypes) {
                val inferred = ctx.resolveEtsField(
                    instance = EtsLocal(name = "receiver", type = type),
                    field = unknownOwner,
                    hierarchy = hierarchy,
                )

                assertEquals(TsResolutionResult.Unique(field), inferred)
            }
            assertEquals(
                TsResolutionResult.Unique(field),
                ctx.resolveEtsField(instance = null, field = unknownOwner, hierarchy = hierarchy),
            )
        }
    }

    @Test
    fun `symbol-only members do not imply ordinary string properties`() {
        val owner = scene.projectClasses.single { it.name == "SymbolOnlyCounter" }
        val properties = owner.getAllPropertiesCombined(hierarchy)

        assertFalse("Symbol.iterator" in properties)
        assertNull(hierarchy.nearestInstanceMethod(owner, methodName = "Symbol.iterator"))
    }
}
