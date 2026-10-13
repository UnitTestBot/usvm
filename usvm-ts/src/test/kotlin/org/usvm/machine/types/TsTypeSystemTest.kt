package org.usvm.machine.types

import io.mockk.mockk
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
import org.usvm.machine.TsContext
import org.usvm.machine.withCoreRuntimeSdk
import org.usvm.types.TypesResult
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
    fun `core SDK uses bundled TypeScript declarations with their members`() {
        val scene = EtsScene(projectFiles = emptyList()).withCoreRuntimeSdk()
        val objectClass = scene.sdkClasses.single { it.name == "Object" }
        val functionClass = scene.sdkClasses.single { it.name == "Function" }
        val arrayClass = scene.sdkClasses.single { it.name == "Array" }

        assertTrue(objectClass.signature == EtsHierarchy.OBJECT_CLASS.signature)
        assertTrue(objectClass.methods.any { it.name == "hasOwnProperty" })
        assertTrue(functionClass.methods.any { it.name == "apply" })
        assertTrue(arrayClass.methods.any { it.name == "slice" })
        assertTrue(scene.sdkClasses.all { it.signature.file.fileName == "lib.es5.d.ts" })
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
