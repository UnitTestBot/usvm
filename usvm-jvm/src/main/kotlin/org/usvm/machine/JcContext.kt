package org.usvm.machine

import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedField
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.char
import org.jacodb.api.ext.double
import org.jacodb.api.ext.float
import org.jacodb.api.ext.int
import org.jacodb.api.ext.long
import org.jacodb.api.ext.objectType
import org.jacodb.api.ext.short
import org.jacodb.api.ext.toType
import org.jacodb.api.ext.void
import org.jacodb.impl.bytecode.JcFieldImpl
import org.jacodb.impl.types.FieldInfo
import org.usvm.UContext
import org.usvm.machine.interpreter.CompositeJcInterpreterObserver
import org.usvm.util.extractJcRefType

class JcContext(
    val cp: JcClasspath,
    components: JcComponents,
) : UContext(components) {
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

    fun mkVoidValue(): JcVoidValue = voidValue

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
