package org.usvm.instrumentation.testcase.descriptor

import org.jacodb.api.JcField
import org.jacodb.api.JcType

sealed class UTestValueDescriptor {
    abstract val type: JcType
}

sealed class UTestConstantDescriptor : UTestValueDescriptor() {

    data class Null(override val type: JcType) : UTestConstantDescriptor()

    data class Boolean(val value: kotlin.Boolean, override val type: JcType) : UTestConstantDescriptor()

    data class Byte(val value: kotlin.Byte, override val type: JcType) : UTestConstantDescriptor()

    data class Short(val value: kotlin.Short, override val type: JcType) : UTestConstantDescriptor()

    data class Int(val value: kotlin.Int, override val type: JcType) : UTestConstantDescriptor()

    data class Long(val value: kotlin.Long, override val type: JcType) : UTestConstantDescriptor()

    data class Float(val value: kotlin.Float, override val type: JcType) : UTestConstantDescriptor()

    data class Double(val value: kotlin.Double, override val type: JcType) : UTestConstantDescriptor()

    data class Char(val value: kotlin.Char, override val type: JcType) : UTestConstantDescriptor()

    data class String(val value: kotlin.String, override val type: JcType) : UTestConstantDescriptor()


}

sealed class UTestArrayDescriptor<T>(
    val elementType: JcType,
    val length: Int,
    val value: T
) : UTestValueDescriptor() {

    override val type: JcType
        get() = elementType.classpath.arrayTypeOf(elementType)

    abstract fun valueToString(): String

    class BooleanArray(
        elementType: JcType,
        length: Int,
        value: kotlin.BooleanArray
    ) : UTestArrayDescriptor<kotlin.BooleanArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
    }

    class ByteArray(
        elementType: JcType,
        length: Int,
        value: kotlin.ByteArray
    ) : UTestArrayDescriptor<kotlin.ByteArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
    }

    class ShortArray(
        elementType: JcType,
        length: Int,
        value: kotlin.ShortArray
    ) : UTestArrayDescriptor<kotlin.ShortArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
    }

    class IntArray(
        elementType: JcType,
        length: Int,
        value: kotlin.IntArray
    ) : UTestArrayDescriptor<kotlin.IntArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
    }

    class LongArray(
        elementType: JcType,
        length: Int,
        value: kotlin.LongArray
    ) : UTestArrayDescriptor<kotlin.LongArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
    }

    class FloatArray(
        elementType: JcType,
        length: Int,
        value: kotlin.FloatArray
    ) : UTestArrayDescriptor<kotlin.FloatArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
    }

    class DoubleArray(
        elementType: JcType,
        length: Int,
        value: kotlin.DoubleArray
    ) : UTestArrayDescriptor<kotlin.DoubleArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
    }

    class CharArray(
        elementType: JcType,
        length: Int,
        value: kotlin.CharArray
    ) : UTestArrayDescriptor<kotlin.CharArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
    }

    class Array(
        elementType: JcType,
        length: Int,
        value: kotlin.Array<UTestValueDescriptor>
    ) : UTestArrayDescriptor<kotlin.Array<UTestValueDescriptor>>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
    }

    override fun toString(): String {
        return "UTestArrayDescriptor(elementType=$elementType, length=$length, value=${valueToString()})"
    }
}


//TODO: Avoid recursion via DescriptorPrinter
class UTestObjectDescriptor(
    override val type: JcType,
    val fields: Map<JcField, UTestValueDescriptor>
) : UTestValueDescriptor() {

    override fun toString(): String =
        "UTestObjectDescriptor(type=$type, fields:${fields.entries.joinToString(",") { "${it.key.name} to ${it.value}}" }}"

}