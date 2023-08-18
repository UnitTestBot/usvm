package org.usvm.instrumentation.testcase.descriptor

import org.jacodb.api.JcField
import org.jacodb.api.JcType

sealed class UTestValueDescriptor {
    abstract val type: JcType
    abstract fun structurallyEqual(other: UTestValueDescriptor): Boolean

    companion object {

        fun descriptorsAreEqual(descriptor1: UTestValueDescriptor, descriptor2: UTestValueDescriptor): Boolean {
            return if (descriptor1 is UTestConstantDescriptor) {
                descriptor1.structurallyEqual(descriptor2)
            } else {
                descriptor1 === descriptor2
            }
        }
    }
}

sealed interface UTestRefDescriptor {
    val refId: Int
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

    override fun structurallyEqual(other: UTestValueDescriptor): kotlin.Boolean = this == other

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
        override fun structurallyEqual(other: UTestValueDescriptor): Boolean =
            other is BooleanArray && other.value.contentEquals(value)
    }

    class ByteArray(
        elementType: JcType,
        length: Int,
        value: kotlin.ByteArray
    ) : UTestArrayDescriptor<kotlin.ByteArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
        override fun structurallyEqual(other: UTestValueDescriptor): Boolean =
            other is ByteArray && other.value.contentEquals(value)
    }

    class ShortArray(
        elementType: JcType,
        length: Int,
        value: kotlin.ShortArray
    ) : UTestArrayDescriptor<kotlin.ShortArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
        override fun structurallyEqual(other: UTestValueDescriptor): Boolean =
            other is ShortArray && other.value.contentEquals(value)
    }

    class IntArray(
        elementType: JcType,
        length: Int,
        value: kotlin.IntArray
    ) : UTestArrayDescriptor<kotlin.IntArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
        override fun structurallyEqual(other: UTestValueDescriptor): Boolean =
            other is IntArray && other.value.contentEquals(value)
    }

    class LongArray(
        elementType: JcType,
        length: Int,
        value: kotlin.LongArray
    ) : UTestArrayDescriptor<kotlin.LongArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
        override fun structurallyEqual(other: UTestValueDescriptor): Boolean =
            other is LongArray && other.value.contentEquals(value)
    }

    class FloatArray(
        elementType: JcType,
        length: Int,
        value: kotlin.FloatArray
    ) : UTestArrayDescriptor<kotlin.FloatArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
        override fun structurallyEqual(other: UTestValueDescriptor): Boolean =
            other is FloatArray && other.value.contentEquals(value)
    }

    class DoubleArray(
        elementType: JcType,
        length: Int,
        value: kotlin.DoubleArray
    ) : UTestArrayDescriptor<kotlin.DoubleArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
        override fun structurallyEqual(other: UTestValueDescriptor): Boolean =
            other is DoubleArray && other.value.contentEquals(value)
    }

    class CharArray(
        elementType: JcType,
        length: Int,
        value: kotlin.CharArray
    ) : UTestArrayDescriptor<kotlin.CharArray>(elementType, length, value) {
        override fun valueToString() = value.contentToString()
        override fun structurallyEqual(other: UTestValueDescriptor): Boolean =
            other is CharArray && other.value.contentEquals(value)
    }

    class Array(
        elementType: JcType,
        length: Int,
        value: List<UTestValueDescriptor>,
        override val refId: Int
    ) : UTestArrayDescriptor<List<UTestValueDescriptor>>(elementType, length, value), UTestRefDescriptor {
        override fun valueToString() = value.toString()
        override fun structurallyEqual(other: UTestValueDescriptor): Boolean {
            if (other !is Array) return false
            if (length != other.length) return false
            if (elementType != other.elementType) return false
            return value.zip(other.value).all { descriptorsAreEqual(it.first, it.second) }
        }
    }

    override fun toString(): String {
        return "UTestArrayDescriptor(elementType=$elementType, length=$length, value=${valueToString()})"
    }
}

data class UTestCyclicReferenceDescriptor(
    val refId: Int,
    override val type: JcType
) : UTestValueDescriptor() {
    override fun structurallyEqual(other: UTestValueDescriptor): Boolean = this == other
}

class UTestEnumValueDescriptor(
    override val type: JcType,
    val enumValueName: String,
    val fields: Map<JcField, UTestValueDescriptor>,
    override val refId: Int
) : UTestValueDescriptor(), UTestRefDescriptor {
    override fun structurallyEqual(other: UTestValueDescriptor): Boolean {
        if (other !is UTestEnumValueDescriptor) return false
        if (type != other.type) return false
        if (enumValueName != other.enumValueName) return false
        for ((key, value) in fields) {
            val otherFieldValue = other.fields[key] ?: return false
            if (!descriptorsAreEqual(value, otherFieldValue)) return false
        }
        return true
    }
}

class UTestClassDescriptor(
    val classType: JcType, override val type: JcType
): UTestValueDescriptor() {
    override fun structurallyEqual(other: UTestValueDescriptor): Boolean =
        other is UTestClassDescriptor && classType == other.classType

    override fun toString(): String =
        "UTestClassDescriptor(classType = ${classType.typeName})"
}

//TODO: Avoid recursion via DescriptorPrinter
class UTestObjectDescriptor(
    override val type: JcType,
    val fields: Map<JcField, UTestValueDescriptor>,
    override val refId: Int
) : UTestValueDescriptor(), UTestRefDescriptor {

    override fun toString(): String =
        "UTestObjectDescriptor(type=$type, fields:${fields.entries.joinToString(",") { "${it.key.name} to ${it.value}}" }}"

    override fun structurallyEqual(other: UTestValueDescriptor): Boolean {
        if (other !is UTestObjectDescriptor) return false
        if (type != other.type) return false
        if (fields.keys != other.fields.keys) return false
        for ((key, value) in fields) {
            val otherFieldValue = other.fields[key] ?: return false
            if (!descriptorsAreEqual(value, otherFieldValue)) return false
        }
        return true
    }

}

class UTestExceptionDescriptor(
    override val type: JcType,
    val message: String,
    val stackTrace: List<UTestValueDescriptor>,
    var raisedByUserCode: Boolean
) : UTestValueDescriptor() {

    override fun structurallyEqual(other: UTestValueDescriptor): Boolean {
        if (other !is UTestExceptionDescriptor) return false
        if (type != other.type) return false
        if (stackTrace.size != other.stackTrace.size) return false
        if (message != other.message) return false
        for (i in stackTrace.indices) {
            if (!descriptorsAreEqual(stackTrace[i], other.stackTrace[i])) {
                return false
            }
        }
        return true
    }

}