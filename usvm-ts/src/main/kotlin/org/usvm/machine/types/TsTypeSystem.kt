package org.usvm.machine.types

import org.jacodb.ets.model.EtsAliasType
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsIntersectionType
import org.jacodb.ets.model.EtsLiteralType
import org.jacodb.ets.model.EtsNeverType
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsPrimitiveType
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsVoidType
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import org.usvm.util.EtsFieldName
import org.usvm.util.EtsHierarchy
import org.usvm.util.EtsMethodName
import org.usvm.util.getAllFields
import org.usvm.util.getAllProperties
import org.usvm.util.getAllPropertiesCombined
import org.usvm.util.type
import kotlin.time.Duration

// TODO this is draft, should be replaced with real implementation
class TsTypeSystem(
    val scene: EtsScene,
    override val typeOperationsTimeout: Duration,
    val hierarchy: EtsHierarchy,
) : UTypeSystem<EtsType> {
    private fun unwrapAlias(type: EtsType): EtsType = when (type) {
        is EtsAliasType -> unwrapAlias(type.originalType)
        else -> type
    }

    override fun isSupertype(supertype: EtsType, type: EtsType): Boolean {
        val unwrappedSupertype = unwrapAlias(supertype)
        val unwrappedType = unwrapAlias(type)

        // In JS/TS, any reference type inherits from Object
        if (unwrappedSupertype is EtsClassType
            && unwrappedSupertype.signature == EtsHierarchy.OBJECT_CLASS.signature
            && unwrappedType is EtsRefType
        ) {
            return true
        }

        // "any" is the universal supertype: any value can be assigned to any.
        if (unwrappedSupertype is EtsAnyType) return true
        // "unknown" is the safe supertype: every value is assignable to unknown.
        if (unwrappedSupertype is EtsUnknownType) return true

        // "any" is the universal subtype: any can be assigned to any type.
        if (unwrappedType is EtsAnyType) return true
        // "never" is the universal subtype: never can be assigned to any type.
        if (unwrappedType is EtsNeverType) return true

        // When "never" is in supertype position, only never <: never.
        if (unwrappedSupertype is EtsNeverType) return type is EtsNeverType

        // Treat null/undefined as bottom-like: they can flow to any type.
        if (unwrappedType is EtsNullType) return true // TS (non-strict) allows null to any
        if (unwrappedType is EtsUndefinedType) return true // TS (non-strict) allows undefined to any

        // As supertype, null/undefined only accept their own or any/unknown.
        if (unwrappedSupertype is EtsNullType) {
            // null supertype rules
            return unwrappedType is EtsUnknownType
        }

        if (unwrappedSupertype is EtsUndefinedType) {
            // undefined supertype rules
            return unwrappedType is EtsUnknownType
        }

        // Primitive types

        // Identical primitive types are mutually assignable (e.g., number to number).
        if (unwrappedSupertype is EtsPrimitiveType && unwrappedType is EtsPrimitiveType) {
            if (unwrappedType is EtsLiteralType) {
                // Literal types are subtypes of their base primitive (e.g., 'foo' <: string).
                return when (unwrappedType.literalTypeName) {
                    "String" -> unwrappedSupertype is EtsStringType
                    "Number" -> unwrappedSupertype is EtsNumberType
                    "Boolean" -> unwrappedSupertype is EtsBooleanType
                    else -> error("Unexpected literal type name ${unwrappedType.literalTypeName}")
                }
            }

            return unwrappedSupertype == unwrappedType
        }

        // Union types

        // For S = A|B, a type T is assignable to S if it is assignable to at least one member.
        if (unwrappedSupertype is EtsUnionType) {
            return unwrappedSupertype.types.any { isSupertype(it, unwrappedType) }
        }

        // For T = A|B, T is subtype of S if all branches are assignable to S.
        if (unwrappedType is EtsUnionType) {
            return unwrappedType.types.all { isSupertype(supertype, it) }
        }

        // Intersection types

        // For S = A&B, T must be assignable to all branches.
        if (unwrappedSupertype is EtsIntersectionType) {
            return unwrappedSupertype.types.all { isSupertype(it, unwrappedType) }
        }
        // For T = A&B, all parts must be assignable to S.
        if (unwrappedType is EtsIntersectionType) {
            return unwrappedType.types.all { isSupertype(unwrappedSupertype, it) }
        }

        // Function types

        if (unwrappedSupertype is EtsFunctionType && unwrappedType is EtsFunctionType) {
            TODO("Unsupported")
        }

        // Array types

        // Arrays are unsoundly covariant in TS: ElementType<T>[] <: ElementType<S>[] if T <: S
        if (unwrappedSupertype is EtsArrayType && unwrappedType is EtsArrayType) {
            // array covariance (JS unsound)
            return isSupertype(unwrappedSupertype.elementType, unwrappedType.elementType)
        }

        // Tuple types

        if (unwrappedSupertype is EtsTupleType && unwrappedType is EtsTupleType) {
            if (unwrappedSupertype.types.size != unwrappedType.types.size) return false

            return unwrappedSupertype.types.zip(unwrappedType.types).all { (supertype, type) ->
                isSupertype(supertype, type)
            }
        }

        // Class and structural types

        require(unwrappedType !is EtsFakeType && unwrappedSupertype !is EtsFakeType) {
            "Fake types should not occur in type constraints"
        }

        if (unwrappedSupertype is EtsAuxiliaryType && unwrappedType is EtsAuxiliaryType) {
            return unwrappedType.properties.all { it in unwrappedSupertype.properties }
        }

        if (unwrappedSupertype is EtsAuxiliaryType) {
            if (unwrappedType is EtsArrayType) {
                if (unwrappedSupertype.properties == setOf("length")) {
                    // Special case: array length is a structural property
                    return true
                }
            }

            if (unwrappedType !is EtsClassType) return false // TODO arrays?

            val classes = hierarchy.classesForType(unwrappedType)
            if (classes.isEmpty()) return false // TODO log

            return classes.any { cls ->
                val properties = cls.getAllPropertiesCombined(hierarchy)

                return properties.containsAll(unwrappedSupertype.properties)
            }
        }

        if (unwrappedType is EtsAuxiliaryType) {
            if (unwrappedSupertype !is EtsClassType) return false // TODO arrays?

            val superClasses = hierarchy.classesForType(unwrappedSupertype)
            if (superClasses.isEmpty()) return false // TODO log

            return superClasses.any { cls ->
                val properties = cls.getAllPropertiesCombined(hierarchy)

                return properties.containsAll(unwrappedType.properties)
            }
        }

        if (unwrappedSupertype is EtsClassType || unwrappedSupertype is EtsUnclearRefType) {
            if (unwrappedType is EtsClassType || unwrappedType is EtsUnclearRefType) {
                val classes = hierarchy.classesForType(unwrappedType)
                val superClasses = hierarchy.classesForType(unwrappedSupertype)

                if (classes.isEmpty() || superClasses.isEmpty()) return false // TODO log

                return classes.any { cls ->
                    val ancestors = hierarchy.getAncestors(cls)
                    superClasses.any { superClass ->
                        superClass in ancestors
                    }
                }
            }
        }

        return false
    }

    override fun hasCommonSubtype(type: EtsType, types: Collection<EtsType>): Boolean {
        val t = unwrapAlias(type)
        return when (t) {
            is EtsAuxiliaryType -> true  // structural types can always be refined
            is EtsPrimitiveType -> types.isEmpty() // primitive has no subtypes, so only when no other constraints
            is EtsClassType -> true  // classes can always have subclasses
            is EtsUnclearRefType -> true  // unknown references may get refined
            is EtsArrayType -> true  // arrays can always be specialized (element types)
            is EtsTupleType -> true  // tuples can always be specialized per element
            is EtsFunctionType -> true  // function types can always be specialized
            is EtsUnionType -> true  // unions can always drop a branch
            is EtsIntersectionType -> true  // intersections can always add a constraint
            is EtsAnyType,
            is EtsUnknownType -> true  // top types can always have subtypes
            else -> false
        }
    }

    override fun isFinal(type: EtsType): Boolean {
        val t = unwrapAlias(type)
        return when (t) {
            is EtsNullType,
            is EtsUndefinedType,
            is EtsNeverType,
            is EtsVoidType,
            is EtsNumberType,
            is EtsStringType,
            is EtsBooleanType,
            is EtsLiteralType -> true  // primitives/literals always final
            is EtsClassType -> false
            else -> false  // others can always be specialized
        }
    }

    override fun isInstantiable(type: EtsType): Boolean {
        val t = unwrapAlias(type)
        return when (t) {
            is EtsNeverType -> false  // no runtime value
            is EtsAnyType,
            is EtsUnknownType -> true   // may represent some value
            is EtsLiteralType,
            is EtsNullType,
            is EtsUndefinedType,
            is EtsPrimitiveType -> true   // literals/primitives have values
            is EtsUnionType -> t.types.any { isInstantiable(it) }  // union has some branch value
            is EtsIntersectionType -> t.types.all { isInstantiable(it) }  // intersection if all parts have values
            is EtsArrayType -> isInstantiable(t.elementType)         // array if elements instantiable
            is EtsTupleType -> t.types.all { isInstantiable(it) }    // tuple if each element instantiable
            is EtsFunctionType -> true   // function literals always possible
            is EtsClassType -> true   // class instantiation possible
            else -> false
        }
    }

    override fun findSubtypes(type: EtsType): Sequence<EtsType> {
        val t = unwrapAlias(type)
        return when (t) {
            is EtsPrimitiveType -> emptySequence()
            is EtsAnyType,
            is EtsUnknownType ->
                scene.projectAndSdkClasses
                    .asSequence()
                    .map { it.type }
                    .plus(sequenceOf(EtsNumberType, EtsBooleanType, EtsStringType))

            is EtsAuxiliaryType ->
                scene.projectAndSdkClasses
                    .asSequence()
                    .filter { cls ->
                        cls.getAllFields(hierarchy)
                            .map { it.name }
                            .containsAll(t.properties)
                    }
                    .map { it.type }

            is EtsArrayType ->
                findSubtypes(t.elementType).map { child ->
                    if (child is EtsArrayType) {
                        EtsArrayType(child.elementType, child.dimensions + 1)
                    } else {
                        EtsArrayType(child, dimensions = 1)
                    }
                }

            is EtsUnclearRefType,
            is EtsClassType ->
                if ((t as? EtsClassType)?.signature == EtsHierarchy.OBJECT_CLASS.signature) { // TODO change it
                    scene.projectAndSdkClasses.asSequence().map { it.type } + EtsStringType + EtsAnyType
                } else {
                    hierarchy.classesForType(t)
                        .asSequence()
                        .flatMap { hierarchy.getInheritors(it).asSequence() }
                        .map { it.type }
                }

            else -> emptySequence()
        }
    }

    private val topTypeStream by lazy {
        USupportTypeStream.from(this, EtsHierarchy.OBJECT_CLASS)
    }

    override fun topTypeStream(): UTypeStream<EtsType> = topTypeStream
}

// TODO support unclear ref type
fun EtsClassType.toAuxiliaryType(hierarchy: EtsHierarchy): EtsAuxiliaryType? {
    val classes = hierarchy.classesForType(this)

    if (classes.isEmpty()) return null

    val methods = mutableListOf<Set<EtsMethodName>>()
    val fields = mutableListOf<Set<EtsFieldName>>()

    classes.forEach {
        val (curFields, curMethods) = it.getAllProperties(hierarchy)
        fields.add(curFields)
        methods.add(curMethods)
    }

    val methodsIntersection = methods.reduceOrNull { acc, set -> acc.intersect(set) } ?: emptySet()
    val fieldsIntersection = fields.reduceOrNull { acc, set -> acc.intersect(set) } ?: emptySet()

    return EtsAuxiliaryType(
        properties = fieldsIntersection + methodsIntersection
    )
}
