package org.usvm.machine.types

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringType
import org.junit.jupiter.api.Test
import org.usvm.util.EtsHierarchy
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TsInitialInputTypesTest {
    private val scene = EtsScene(projectFiles = emptyList())
    private val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = EtsHierarchy(scene))

    @Test
    fun `unconstrained generic array has instantiable primitive witnesses`() {
        val arrayType = EtsArrayType(EtsGenericType(typeName = "T"), dimensions = 1)

        val inputType = arrayType.initialInputType(genericParameters = emptyMap())

        assertTrue(typeSystem.isInstantiable(inputType))
        assertTrue(typeSystem.isSupertype(inputType, EtsArrayType(EtsNumberType, dimensions = 1)))
        assertTrue(typeSystem.isSupertype(inputType, EtsArrayType(EtsStringType, dimensions = 1)))
    }

    @Test
    fun `generic array preserves its declared upper bound`() {
        val generic = EtsGenericType(typeName = "T", constraint = EtsNumberType)
        val arrayType = EtsArrayType(EtsGenericType(typeName = "T"), dimensions = 1)

        val inputType = arrayType.initialInputType(genericParameters = mapOf("T" to generic))

        assertTrue(typeSystem.isInstantiable(inputType))
        assertTrue(typeSystem.isSupertype(inputType, EtsArrayType(EtsNumberType, dimensions = 1)))
        assertFalse(typeSystem.isSupertype(inputType, EtsArrayType(EtsStringType, dimensions = 1)))
    }

    @Test
    fun `generic default does not restrict an inferred input type`() {
        val generic = EtsGenericType(typeName = "T", defaultType = EtsNumberType)
        val arrayType = EtsArrayType(generic, dimensions = 1)

        val inputType = arrayType.initialInputType(genericParameters = mapOf("T" to generic))

        assertTrue(typeSystem.isInstantiable(inputType))
        assertTrue(typeSystem.isSupertype(inputType, EtsArrayType(EtsStringType, dimensions = 1)))
    }
}
