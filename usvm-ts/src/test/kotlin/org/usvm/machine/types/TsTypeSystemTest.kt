package org.usvm.machine.types

import io.mockk.mockk
import org.jacodb.ets.model.EtsAliasType
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanLiteralType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsLiteralType
import org.jacodb.ets.model.EtsLocalSignature
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNeverType
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberLiteralType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsPrimitiveType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringLiteralType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsVoidType
import org.junit.jupiter.api.Test
import org.usvm.machine.TsContext
import org.usvm.machine.withCoreRuntimeSdk
import org.usvm.types.TypesResult
import org.usvm.types.USingleTypeStream
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeRegion
import org.usvm.util.EtsHierarchy
import org.usvm.util.type
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TsTypeSystemTest {
    @Test
    fun `array subtype candidates preserve root dimensions and satisfy the root constraint`() {
        val scene = EtsScene(projectFiles = emptyList())
        val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = EtsHierarchy(scene))
        val elementTypes = listOf(EtsNumberType, EtsBooleanType, EtsStringType)

        for (dimensions in listOf(1, 2, 4)) {
            val root = EtsArrayType(elementType = EtsUnknownType, dimensions = dimensions)
            val expectedTypes = elementTypes.map { EtsArrayType(elementType = it, dimensions = dimensions) }.toSet()

            val actualTypes = typeSystem.findSubtypes(root).toSet()

            assertEquals(expectedTypes, actualTypes)
            actualTypes.forEach { candidate ->
                assertTrue(typeSystem.isSupertype(supertype = root, type = candidate), candidate.typeName)
            }
        }
    }

    @Test
    fun `array subtype lifting accumulates dimensions supplied by an element alias`() {
        val scene = EtsScene(projectFiles = emptyList())
        val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = EtsHierarchy(scene))
        val fileSignature = EtsFileSignature(projectName = "test", fileName = "types.ts")
        val aliasSignature = EtsLocalSignature(
            name = "Rows",
            method = EtsMethodSignature(
                enclosingClass = EtsClassSignature(name = "Types", file = fileSignature),
                name = "useRows",
                parameters = emptyList(),
                returnType = EtsVoidType,
            ),
        )
        val alias = EtsAliasType(
            name = "Rows",
            originalType = EtsArrayType(elementType = EtsUnknownType, dimensions = 3),
            signature = aliasSignature,
        )
        val root = EtsArrayType(elementType = alias, dimensions = 2)
        val expectedTypes = setOf(
            EtsArrayType(elementType = EtsNumberType, dimensions = 5),
            EtsArrayType(elementType = EtsBooleanType, dimensions = 5),
            EtsArrayType(elementType = EtsStringType, dimensions = 5),
        )

        val actualTypes = typeSystem.findSubtypes(root).toSet()

        assertEquals(expectedTypes, actualTypes)
        actualTypes.forEach { candidate ->
            assertTrue(typeSystem.isSupertype(supertype = root, type = candidate), candidate.typeName)
        }
    }

    @Test
    fun `literal subtyping is reflexive without accepting distinct literals or their base type`() {
        val scene = EtsScene(projectFiles = emptyList())
        val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = EtsHierarchy(scene))
        val literalCases = listOf<Triple<EtsLiteralType, EtsPrimitiveType, EtsLiteralType>>(
            Triple(EtsStringLiteralType(value = "a"), EtsStringType, EtsStringLiteralType(value = "b")),
            Triple(EtsNumberLiteralType(value = 1.0), EtsNumberType, EtsNumberLiteralType(value = 2.0)),
            Triple(EtsBooleanLiteralType(value = true), EtsBooleanType, EtsBooleanLiteralType(value = false)),
        )

        for ((literal, primitive, otherLiteral) in literalCases) {
            val alias = EtsAliasType(name = "Literal", originalType = literal, signature = mockk())
            val array = EtsArrayType(elementType = literal, dimensions = 2)

            val filteredStream = USingleTypeStream(typeSystem, array).filterBySupertype(array)

            assertTrue(typeSystem.isSupertype(supertype = literal, type = literal))
            assertTrue(typeSystem.isSupertype(supertype = literal, type = alias))
            assertTrue(typeSystem.isSupertype(supertype = alias, type = literal))
            assertTrue(typeSystem.isSupertype(supertype = primitive, type = literal))
            assertFalse(typeSystem.isSupertype(supertype = literal, type = primitive))
            assertFalse(typeSystem.isSupertype(supertype = literal, type = otherLiteral))
            assertEquals(expected = false, actual = filteredStream.isEmpty)
        }
    }

    @Test
    fun `auxiliary comparisons consider compatible class alternatives after the first candidate`() {
        val files = listOf("first", "second").map { fieldName ->
            val fileSignature = EtsFileSignature(projectName = "test", fileName = "$fieldName.ts")
            val classSignature = EtsClassSignature(name = "Ambiguous", file = fileSignature)
            val clazz = EtsClassImpl(
                signature = classSignature,
                fields = listOf(
                    EtsFieldImpl(
                        EtsFieldSignature(enclosingClass = classSignature, name = fieldName, type = EtsNumberType),
                    ),
                ),
                methods = emptyList(),
            )

            EtsFile(signature = fileSignature, classes = listOf(clazz), namespaces = emptyList())
        }
        val scene = EtsScene(projectFiles = files)
        val hierarchy = EtsHierarchy(scene)
        val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = hierarchy)
        val ambiguousType = EtsClassType(EtsClassSignature(name = "Ambiguous", file = EtsFileSignature.UNKNOWN))
        val laterCandidate = hierarchy.classesForType(ambiguousType).last()
        val auxiliaryType = EtsAuxiliaryType(properties = setOf(laterCandidate.fields.single().name))
        val absentPropertyType = EtsAuxiliaryType(properties = setOf("missing"))

        val acceptsLaterClass = typeSystem.isSupertype(supertype = auxiliaryType, type = ambiguousType)
        val acceptsLaterShape = typeSystem.isSupertype(supertype = ambiguousType, type = auxiliaryType)

        assertTrue(acceptsLaterClass)
        assertTrue(acceptsLaterShape)
        assertFalse(typeSystem.isSupertype(supertype = absentPropertyType, type = ambiguousType))
        assertFalse(typeSystem.isSupertype(supertype = ambiguousType, type = absentPropertyType))
    }

    @Test
    fun `stronger auxiliary requirements are subtypes and remain in the refined region`() {
        val fileSignature = EtsFileSignature(projectName = "test", fileName = "types.ts")
        val classSignature = EtsClassSignature(name = "WithTwoFields", file = fileSignature)
        val clazz = EtsClassImpl(
            signature = classSignature,
            fields = listOf(
                EtsFieldImpl(EtsFieldSignature(enclosingClass = classSignature, name = "a", type = EtsNumberType)),
                EtsFieldImpl(EtsFieldSignature(enclosingClass = classSignature, name = "b", type = EtsNumberType)),
            ),
            methods = emptyList(),
        )
        val file = EtsFile(signature = fileSignature, classes = listOf(clazz), namespaces = emptyList())
        val scene = EtsScene(projectFiles = listOf(file))
        val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = EtsHierarchy(scene))
        val weakerType = EtsAuxiliaryType(properties = setOf("a"))
        val strongerType = EtsAuxiliaryType(properties = setOf("a", "b"))
        val unrelatedType = EtsAuxiliaryType(properties = setOf("c"))
        val region = UTypeRegion(typeSystem, USingleTypeStream(typeSystem, clazz.type))

        val refinedRegion = region.addSupertype(weakerType).addSupertype(strongerType)

        assertTrue(typeSystem.isSupertype(supertype = weakerType, type = strongerType))
        assertFalse(typeSystem.isSupertype(supertype = strongerType, type = weakerType))
        assertFalse(typeSystem.isSupertype(supertype = weakerType, type = unrelatedType))
        assertEquals(setOf(strongerType), refinedRegion.supertypes.toSet())
    }

    @Test
    fun `auxiliary type streams enumerate classes satisfying method-only requirements`() {
        val fileSignature = EtsFileSignature(projectName = "test", fileName = "types.ts")
        val classSignature = EtsClassSignature(name = "WithMethod", file = fileSignature)
        val method = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classSignature,
                name = "virtualMethod",
                parameters = emptyList(),
                returnType = EtsNumberType,
            ),
        )
        val clazz = EtsClassImpl(signature = classSignature, fields = emptyList(), methods = listOf(method))
        val file = EtsFile(signature = fileSignature, classes = listOf(clazz), namespaces = emptyList())
        val scene = EtsScene(projectFiles = listOf(file))
        val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = EtsHierarchy(scene))
        val auxiliaryType = EtsAuxiliaryType(properties = setOf("virtualMethod"))

        val candidates = typeSystem.findSubtypes(auxiliaryType).toList()
        val result = USupportTypeStream.from(typeSystem, auxiliaryType).take(n = 2) as TypesResult.SuccessfulTypesResult

        assertFalse(typeSystem.isInstantiable(auxiliaryType))
        assertTrue(typeSystem.isSupertype(supertype = auxiliaryType, type = clazz.type))
        assertEquals(listOf(clazz.type), candidates)
        assertEquals(setOf(clazz.type), result.types.toSet())
    }

    @Test
    fun `fake types are rejected in type constraints before subtype shortcuts`() {
        val scene = EtsScene(projectFiles = emptyList())
        val context = TsContext(scene = scene, components = mockk())
        val typeSystem = TsTypeSystem(
            scene = scene,
            typeOperationsTimeout = 1.seconds,
            hierarchy = EtsHierarchy(scene),
        )
        val fakeType = EtsFakeType.mkRef(context)
        val comparisonTypes = listOf(
            EtsAuxiliaryType(properties = setOf("style")),
            EtsAnyType,
            EtsUnknownType,
            EtsNeverType,
            EtsObjectType,
            fakeType,
        )

        for (type in comparisonTypes) {
            assertFailsWith<IllegalArgumentException> {
                typeSystem.isSupertype(supertype = type, type = fakeType)
            }
            assertFailsWith<IllegalArgumentException> {
                typeSystem.isSupertype(supertype = fakeType, type = type)
            }
        }
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
    fun `runtime object and nominal constraints refine consistently in both orders`() {
        val fileSignature = EtsFileSignature(projectName = "test", fileName = "types.ts")
        val classSignature = EtsClassSignature(name = "RuntimeMember", file = fileSignature)
        val clazz = EtsClassImpl(signature = classSignature, fields = emptyList(), methods = emptyList())
        val file = EtsFile(signature = fileSignature, classes = listOf(clazz), namespaces = emptyList())
        val scene = EtsScene(projectFiles = listOf(file)).withCoreRuntimeSdk()
        val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = EtsHierarchy(scene))
        val nominalType = EtsNominalType(clazz.type)
        val nominalObject = EtsNominalType(EtsHierarchy.OBJECT_CLASS)
        val region = UTypeRegion(typeSystem, typeSystem.topTypeStream())

        val objectFirst = region.addSupertype(EtsObjectType).addSupertype(nominalType)
        val nominalFirst = region.addSupertype(nominalType).addSupertype(EtsObjectType)
        val positiveThenNegative = objectFirst.excludeSupertype(nominalType)
        val singletonRegion = UTypeRegion(typeSystem, USingleTypeStream(typeSystem, clazz.type))
        val negativeThenPositive = singletonRegion.excludeSupertype(nominalType).addSupertype(EtsObjectType)

        assertTrue(typeSystem.isSupertype(supertype = EtsObjectType, type = nominalType))
        assertFalse(typeSystem.isSupertype(supertype = nominalType, type = EtsObjectType))
        assertTrue(typeSystem.isSupertype(supertype = nominalObject, type = EtsObjectType))
        assertFalse(objectFirst.isEmpty)
        assertFalse(nominalFirst.isEmpty)
        assertEquals(setOf(nominalType), objectFirst.supertypes.toSet())
        assertEquals(objectFirst.supertypes, nominalFirst.supertypes)
        val matchingTypes = objectFirst.typeStream.take(n = 10) as TypesResult.SuccessfulTypesResult
        assertEquals(setOf(clazz.type), matchingTypes.types.toSet())
        assertTrue(positiveThenNegative.isEmpty)
        assertTrue(negativeThenPositive.isEmpty)
    }

    @Test
    fun `core SDK prototype identities are not ordinary object witnesses`() {
        val scene = EtsScene(projectFiles = emptyList()).withCoreRuntimeSdk()
        val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = EtsHierarchy(scene))
        val prototypes = scene.sdkClasses.filter { it.name == "Function" || it.name == "Array" }

        val result = typeSystem.topTypeStream().take(n = 20) as TypesResult.SuccessfulTypesResult

        assertTrue(typeSystem.isInstantiable(EtsHierarchy.OBJECT_CLASS))
        prototypes.forEach { assertFalse(typeSystem.isInstantiable(it.type), it.name) }
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
                EtsFieldImpl(EtsFieldSignature(classSignature, name = "a", EtsNumberType)),
                EtsFieldImpl(EtsFieldSignature(classSignature, name = "b", EtsNumberType)),
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
