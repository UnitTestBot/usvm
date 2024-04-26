package org.usvm.api.util

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.usvm.api.StaticFieldValue
import org.usvm.api.decoder.DecoderApi
import org.usvm.api.util.Reflection.allocateInstance
import org.usvm.api.util.Reflection.getFieldValue
import org.usvm.api.util.Reflection.invoke
import org.usvm.api.util.Reflection.setFieldValue
import org.usvm.api.util.Reflection.toJavaClass
import org.usvm.machine.JcContext

class JcTestInterpreterDecoderApi(
    private val ctx: JcContext,
    private val classLoader: ClassLoader
) : DecoderApi<Any?> {
    private val _staticFields: MutableMap<JcClassOrInterface, MutableMap<JcField, StaticFieldValue>> = mutableMapOf()
    val staticFields: Map<JcClassOrInterface, List<StaticFieldValue>>
        get() = _staticFields.mapValues { it.value.values.toList() }

    override fun invokeMethod(method: JcMethod, args: List<Any?>): Any? =
        if (method.isStatic || method.isConstructor) {
            method.invoke(classLoader, null, args)
        } else {
            method.invoke(classLoader, args.first(), args.drop(1))
        }

    override fun getField(field: JcField, instance: Any?): Any? =
        field.getFieldValue(classLoader, instance)

    override fun setField(field: JcField, instance: Any?, value: Any?) {
        if (field.isStatic) {
            val fieldsForClass = _staticFields.getOrPut(field.enclosingClass) { mutableMapOf() }
            if (field in fieldsForClass) error("You can write the same field only once. Field: $field")

            fieldsForClass[field] = StaticFieldValue(field, value)

            return
        }

        field.setFieldValue(classLoader, instance, value)
    }

    override fun createBoolConst(value: Boolean): Any = value
    override fun createByteConst(value: Byte): Any = value
    override fun createShortConst(value: Short): Any = value
    override fun createIntConst(value: Int): Any = value
    override fun createLongConst(value: Long): Any = value
    override fun createFloatConst(value: Float): Any = value
    override fun createDoubleConst(value: Double): Any = value
    override fun createCharConst(value: Char): Any = value
    override fun createStringConst(value: String): Any = value

    override fun createClassConst(type: JcType): Any =
        type.toJavaClass(classLoader)

    override fun createNullConst(type: JcType): Any? = null

    override fun setArrayIndex(array: Any?, index: Any?, value: Any?) {
        Reflection.setArrayIndex(array!!, index as Int, value)
    }

    override fun getArrayIndex(array: Any?, index: Any?): Any? =
        Reflection.getArrayIndex(array!!, index as Int)

    override fun getArrayLength(array: Any?): Any =
        Reflection.getArrayLength(array)

    override fun createArray(elementType: JcType, size: Any?): Any =
        ctx.cp.arrayTypeOf(elementType).allocateInstance(classLoader, size as Int)

    override fun castClass(type: JcClassOrInterface, obj: Any?): Any? = obj
}
