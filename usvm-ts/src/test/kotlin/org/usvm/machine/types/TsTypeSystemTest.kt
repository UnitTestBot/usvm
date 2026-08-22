package org.usvm.machine.types

import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.util.EtsHierarchy
import org.usvm.util.type
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TsTypeSystemTest {
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
