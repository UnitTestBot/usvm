package org.usvm.machine.types

import io.mockk.mockk
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.machine.TsContext
import org.usvm.types.USingleTypeStream
import org.usvm.util.EtsHierarchy
import org.usvm.util.type
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TsTypeSystemTest {
    @Test
    fun `synthetic wrapper does not satisfy structural type constraints`() {
        val scene = EtsScene(projectFiles = emptyList())
        val context = TsContext(scene = scene, components = mockk())
        val typeSystem = TsTypeSystem(
            scene = scene,
            typeOperationsTimeout = 1.seconds,
            hierarchy = EtsHierarchy(scene),
        )
        val fakeType = EtsFakeType.mkRef(context)
        val structuralType = EtsAuxiliaryType(properties = setOf("style"))

        val fakeHasProperty = typeSystem.isSupertype(structuralType, fakeType)
        val propertyHasFakeType = typeSystem.isSupertype(fakeType, structuralType)

        assertFalse(fakeHasProperty)
        assertFalse(propertyHasFakeType)
    }

    @Test
    fun `fake type remains in a singleton stream after filtering by itself`() {
        val scene = EtsScene(projectFiles = emptyList())
        val context = TsContext(scene = scene, components = mockk())
        val typeSystem = TsTypeSystem(
            scene = scene,
            typeOperationsTimeout = 1.seconds,
            hierarchy = EtsHierarchy(scene),
        )
        val fakeType = EtsFakeType.mkRef(context)
        val stream = USingleTypeStream(typeSystem = typeSystem, singleType = fakeType)

        val filtered = stream.filterBySupertype(fakeType).filterBySubtype(fakeType)

        assertTrue(typeSystem.isSupertype(fakeType, fakeType))
        assertFalse(filtered.isEmpty ?: true)
    }

    @Test
    fun `auxiliary type is a subtype of a class containing its properties`() {
        val fileSignature = EtsFileSignature(projectName = "test", fileName = "types.ts")
        val classSignature = EtsClassSignature(name = "WithTwoFields", file = fileSignature)
        val clazz = EtsClassImpl(
            signature = classSignature,
            fields = listOf(
                EtsFieldImpl(EtsFieldSignature(classSignature, "a", EtsNumberType)),
                EtsFieldImpl(EtsFieldSignature(classSignature, "b", EtsNumberType)),
            ),
            methods = emptyList(),
        )
        val file = EtsFile(fileSignature, classes = listOf(clazz), namespaces = emptyList())
        val scene = EtsScene(projectFiles = listOf(file))
        val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, EtsHierarchy(scene))
        val auxiliaryType = EtsAuxiliaryType(properties = setOf("a"))

        assertTrue(typeSystem.isSupertype(clazz.type, auxiliaryType))
    }
}
