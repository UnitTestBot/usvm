package org.usvm.machine.types

import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnknownType
import org.junit.jupiter.api.Test
import org.usvm.machine.withCoreRuntimeSdk
import org.usvm.types.TypesResult
import org.usvm.util.EtsHierarchy
import org.usvm.util.type
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TsTypeSystemTest {
    @Test
    fun `runtime object constraint accepts references and excludes primitive or unresolved types`() {
        val scene = EtsScene(projectFiles = emptyList())
        val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = EtsHierarchy(scene))
        val references = listOf(
            EtsHierarchy.OBJECT_CLASS,
            EtsArrayType(EtsNumberType, dimensions = 1),
            EtsTupleType(emptyList()),
        )
        val nonObjects = listOf(
            EtsStringType,
            EtsNumberType,
            EtsBooleanType,
            EtsNullType,
            EtsUndefinedType,
            EtsAnyType,
            EtsUnknownType,
        )

        references.forEach { assertTrue(typeSystem.isSupertype(EtsObjectType, it), it.typeName) }
        nonObjects.forEach { assertFalse(typeSystem.isSupertype(EtsObjectType, it), it.typeName) }
    }

    @Test
    fun `core SDK prototype identities are not ordinary object witnesses`() {
        val scene = EtsScene(projectFiles = emptyList()).withCoreRuntimeSdk()
        val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = EtsHierarchy(scene))
        val prototypes = scene.sdkClasses.filter { it.name == "Function" || it.name == "Array" }

        assertTrue(typeSystem.isInstantiable(EtsHierarchy.OBJECT_CLASS))
        prototypes.forEach { assertFalse(typeSystem.isInstantiable(it.type), it.name) }
        val result = typeSystem.topTypeStream().take(n = 20) as TypesResult.SuccessfulTypesResult
        prototypes.forEach { assertFalse(it.type in result.types, it.name) }
    }

    @Test
    fun `core SDK bootstrap preserves explicitly loaded SDK declarations`() {
        val fileSignature = EtsFileSignature(projectName = "sdk", fileName = "function.ts")
        val functionClass = EtsClassImpl(
            signature = EtsClassSignature(name = "Function", file = fileSignature),
            fields = emptyList(),
            methods = emptyList(),
        )
        val file = EtsFile(signature = fileSignature, classes = listOf(functionClass), namespaces = emptyList())
        val scene = EtsScene(projectFiles = emptyList(), sdkFiles = listOf(file)).withCoreRuntimeSdk()
        val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = EtsHierarchy(scene))

        assertTrue(scene.sdkClasses.single { it.name == "Function" } === functionClass)
        assertTrue(typeSystem.isInstantiable(functionClass.type))
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
