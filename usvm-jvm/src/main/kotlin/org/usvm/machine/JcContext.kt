package org.usvm.machine

import io.ksmt.utils.cast
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedField
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.char
import org.jacodb.api.jvm.ext.double
import org.jacodb.api.jvm.ext.float
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.long
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.short
import org.jacodb.api.jvm.ext.toType
import org.jacodb.api.jvm.ext.void
import org.jacodb.impl.bytecode.JcFieldImpl
import org.jacodb.impl.types.FieldInfo
import org.usvm.UBv32Sort
import org.usvm.UContext
import org.usvm.USort
import org.usvm.machine.interpreter.JcLambdaCallSiteRegionId
import org.usvm.machine.interpreter.statics.JcStaticFieldReading
import org.usvm.machine.interpreter.statics.JcStaticFieldRegionId
import org.usvm.util.extractJcRefType

internal typealias USizeSort = UBv32Sort

class JcContext(
    val cp: JcClasspath,
    components: JcComponents,
) : UContext<USizeSort>(components) {
    val voidSort by lazy { JcVoidSort(this) }

    val longSort get() = bv64Sort
    val integerSort get() = bv32Sort
    val shortSort get() = bv16Sort
    val charSort get() = bv16Sort
    val byteSort get() = bv8Sort
    val booleanSort get() = boolSort

    val floatSort get() = fp32Sort
    val doubleSort get() = fp64Sort

    val voidValue by lazy { JcVoidValue(this) }

    val classType: JcRefType by lazy {
        cp.findTypeOrNull("java.lang.Class") as? JcRefType
            ?: error("No class type in classpath")
    }

    val stringType: JcRefType by lazy {
        cp.findTypeOrNull("java.lang.String") as? JcRefType
            ?: error("No string type in classpath")
    }

    private val enumType: JcRefType by lazy {
        cp.findTypeOrNull("java.lang.Enum") as? JcRefType
            ?: error("No enum type in classpath")
    }

    val lambdaCallSiteRegionId by lazy { JcLambdaCallSiteRegionId(this) }

    // TODO store it in JcComponents? Make it mutable?
    internal val useNegativeAddressesInStaticInitializer: Boolean = false

    fun mkVoidValue(): JcVoidValue = voidValue


    private val staticFieldReadings = mkAstInterner<JcStaticFieldReading<*>>()
    fun <Sort : USort> mkStaticFieldReading(
        regionId: JcStaticFieldRegionId<Sort>,
        field: JcField,
        sort: Sort,
    ): JcStaticFieldReading<Sort> = staticFieldReadings.createIfContextActive {
        JcStaticFieldReading(this, regionId, field, sort)
    }.cast()

    fun typeToSort(type: JcType) = when (type) {
        is JcRefType -> addressSort
        cp.void -> voidSort
        cp.long -> longSort
        cp.int -> integerSort
        cp.short -> shortSort
        cp.char -> charSort
        cp.byte -> byteSort
        cp.boolean -> booleanSort
        cp.float -> floatSort
        cp.double -> doubleSort
        else -> error("Unknown type: $type")
    }

    fun arrayDescriptorOf(type: JcArrayType): JcType =
        if (PredefinedPrimitives.matches(type.elementType.typeName)) {
            type.elementType
        } else {
            type.classpath.objectType
        }

    /**
     * Synthetic field to store allocated classes types.
     * */
    val classTypeSyntheticField: JcField by lazy {
        val info = FieldInfo(
            name = "__class_type__",
            signature = null,
            access = 0,
            type = cp.objectType.typeName,
            annotations = emptyList()
        )
        JcFieldImpl(classType.jcClass, info)
    }

    val stringValueField: JcTypedField by lazy {
        stringType.jcClass.toType().declaredFields.first { it.name == "value" }
    }

    // The `coder` field is not presented in java 8, so return null if is missed
    val stringCoderField: JcTypedField? by lazy {
        stringType.jcClass.toType().declaredFields.firstOrNull { it.name == "coder" }
    }

    // Do not use JcTypedField here as its type is not required, however JcTypedField does not have required overridden `equals` method
    val enumOrdinalField: JcField by lazy {
        enumType.jcClass.declaredFields.first { it.name == "ordinal" }
    }

    val primitiveTypes: Set<JcPrimitiveType> by lazy {
        setOf(
            cp.boolean,
            cp.byte,
            cp.short,
            cp.int,
            cp.long,
            cp.char,
            cp.float,
            cp.double,
        )
    }

    val arrayIndexOutOfBoundsExceptionType by lazy {
        extractJcRefType(IndexOutOfBoundsException::class)
    }

    val negativeArraySizeExceptionType by lazy {
        extractJcRefType(NegativeArraySizeException::class)
    }

    val arithmeticExceptionType by lazy {
        extractJcRefType(ArithmeticException::class)
    }

    val nullPointerExceptionType by lazy {
        extractJcRefType(NullPointerException::class)
    }

    val classCastExceptionType by lazy {
        extractJcRefType(ClassCastException::class)
    }

    val arrayStoreExceptionType by lazy {
        extractJcRefType(ArrayStoreException::class)
    }
}
