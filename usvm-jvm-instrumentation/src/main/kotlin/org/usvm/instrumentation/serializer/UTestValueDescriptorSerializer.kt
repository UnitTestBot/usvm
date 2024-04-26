package org.usvm.instrumentation.serializer

import com.jetbrains.rd.framework.*
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.ext.*
import org.usvm.instrumentation.util.stringType
import org.usvm.instrumentation.testcase.descriptor.*

class UTestValueDescriptorSerializer(private val ctx: SerializationContext) {

    val jcClasspath = ctx.jcClasspath

    fun serialize(buffer: AbstractBuffer, uTestValueDescriptor: UTestValueDescriptor) {
        buffer.serializeUTestValueDescriptor(uTestValueDescriptor)
        buffer.writeEnum(UTestValueDescriptorKind.SERIALIZED_DESCRIPTOR)
        buffer.writeInt(uTestValueDescriptor.id)
    }

    private fun AbstractBuffer.deserializeDescriptorFromBuffer(): UTestValueDescriptor {
        while (true) {
            val kind = readEnum<UTestValueDescriptorKind>()
            val id = readInt()
            val deserializedExpression =
                when (kind) {
                    UTestValueDescriptorKind.SERIALIZED_DESCRIPTOR -> {
                        return getUTestValueDescriptor(id)
                    }

                    UTestValueDescriptorKind.INT -> deserializeInt()
                    UTestValueDescriptorKind.OBJECT -> deserializeObject()
                    UTestValueDescriptorKind.BOOLEAN -> deserializeBoolean()
                    UTestValueDescriptorKind.BYTE -> deserializeByte()
                    UTestValueDescriptorKind.SHORT -> deserializeShort()
                    UTestValueDescriptorKind.LONG -> deserializeLong()
                    UTestValueDescriptorKind.DOUBLE -> deserializeDouble()
                    UTestValueDescriptorKind.FLOAT -> deserializeFloat()
                    UTestValueDescriptorKind.CHAR -> deserializeChar()
                    UTestValueDescriptorKind.NULL -> deserializeNull()
                    UTestValueDescriptorKind.STRING -> deserializeString()
                    UTestValueDescriptorKind.CYCLIC_REF -> deserializeCyclicRef()
                    UTestValueDescriptorKind.ARRAY -> deserializeArray()
                    UTestValueDescriptorKind.ENUM_VALUE -> deserializeEnumValue()
                    UTestValueDescriptorKind.CLASS -> deserializeClass()
                    UTestValueDescriptorKind.EXCEPTION -> deserializeException()
                }
            ctx.deserializedDescriptors[id] = deserializedExpression
        }

    }

    private fun AbstractBuffer.serializeUTestValueDescriptor(uTestValueDescriptor: UTestValueDescriptor) {
        if (ctx.serializedDescriptors.contains(uTestValueDescriptor)) return
        when (uTestValueDescriptor) {
            is UTestConstantDescriptor.Boolean -> serialize(uTestValueDescriptor)
            is UTestArrayDescriptor -> serialize(uTestValueDescriptor)
            is UTestConstantDescriptor.Byte -> serialize(uTestValueDescriptor)
            is UTestConstantDescriptor.Char -> serialize(uTestValueDescriptor)
            is UTestConstantDescriptor.Double -> serialize(uTestValueDescriptor)
            is UTestConstantDescriptor.Float -> serialize(uTestValueDescriptor)
            is UTestConstantDescriptor.Int -> serialize(uTestValueDescriptor)
            is UTestConstantDescriptor.Long -> serialize(uTestValueDescriptor)
            is UTestConstantDescriptor.Null -> serialize(uTestValueDescriptor)
            is UTestConstantDescriptor.Short -> serialize(uTestValueDescriptor)
            is UTestConstantDescriptor.String -> serialize(uTestValueDescriptor)
            is UTestObjectDescriptor -> serialize(uTestValueDescriptor)
            is UTestCyclicReferenceDescriptor -> serialize(uTestValueDescriptor)
            is UTestEnumValueDescriptor -> serialize(uTestValueDescriptor)
            is UTestClassDescriptor -> serialize(uTestValueDescriptor)
            is UTestExceptionDescriptor -> serialize(uTestValueDescriptor)
        }
    }

    private fun AbstractBuffer.serialize(uTestValueDescriptor: UTestArrayDescriptor) =
        serialize(
            uTestValueDescriptor = uTestValueDescriptor,
            kind = UTestValueDescriptorKind.ARRAY,
            serializeInternals = {
                value.forEach { serializeUTestValueDescriptor(it) }
            },
            serialize = {
                writeJcType(elementType)
                writeInt(refId)
                writeInt(length)
                writeList(value) { writeUTestValueDescriptor(it) }
            }
        )

    private fun AbstractBuffer.deserializeArray(): UTestArrayDescriptor {
        val elementType = readJcType(jcClasspath) ?: jcClasspath.objectType
        val refId = readInt()
        val length = readInt()
        val values = readList { readUTestValueDescriptor() }
        return UTestArrayDescriptor(
            elementType = elementType,
            length = length,
            value = values,
            refId = refId
        )
    }

    private fun AbstractBuffer.serialize(uTestValueDescriptor: UTestCyclicReferenceDescriptor) =
        serialize(
            uTestValueDescriptor = uTestValueDescriptor,
            kind = UTestValueDescriptorKind.CYCLIC_REF,
            serializeInternals = {},
            serialize = {
                writeJcType(type)
                writeInt(refId)
            }
        )

    private fun AbstractBuffer.deserializeCyclicRef(): UTestCyclicReferenceDescriptor {
        val type = readJcType(jcClasspath) ?: error("deserialization error")
        val refId = readInt()
        return UTestCyclicReferenceDescriptor(refId, type)
    }

    private fun AbstractBuffer.serialize(uTestValueDescriptor: UTestConstantDescriptor.Boolean) =
        serialize(
            uTestValueDescriptor = uTestValueDescriptor,
            kind = UTestValueDescriptorKind.BOOLEAN,
            serializeInternals = {},
            serialize = { writeBoolean(value) }
        )

    private fun AbstractBuffer.deserializeBoolean(): UTestValueDescriptor {
        return UTestConstantDescriptor.Boolean(readBoolean(), jcClasspath.boolean)
    }

    private fun AbstractBuffer.serialize(uTestValueDescriptor: UTestConstantDescriptor.Byte) =
        serialize(
            uTestValueDescriptor = uTestValueDescriptor,
            kind = UTestValueDescriptorKind.BYTE,
            serializeInternals = {},
            serialize = { writeByte(value) }
        )

    private fun AbstractBuffer.deserializeByte(): UTestValueDescriptor {
        return UTestConstantDescriptor.Byte(readByte(), jcClasspath.byte)
    }

    private fun AbstractBuffer.serialize(uTestValueDescriptor: UTestConstantDescriptor.Short) =
        serialize(
            uTestValueDescriptor = uTestValueDescriptor,
            kind = UTestValueDescriptorKind.SHORT,
            serializeInternals = {},
            serialize = { writeShort(value) }
        )

    private fun AbstractBuffer.deserializeShort(): UTestValueDescriptor {
        return UTestConstantDescriptor.Short(readShort(), jcClasspath.short)
    }

    private fun AbstractBuffer.serialize(uTestValueDescriptor: UTestConstantDescriptor.Int) =
        serialize(
            uTestValueDescriptor = uTestValueDescriptor,
            kind = UTestValueDescriptorKind.INT,
            serializeInternals = {},
            serialize = { writeInt(value) }
        )

    private fun AbstractBuffer.deserializeInt(): UTestValueDescriptor {
        return UTestConstantDescriptor.Int(readInt(), jcClasspath.int)
    }

    private fun AbstractBuffer.serialize(uTestValueDescriptor: UTestConstantDescriptor.Long) =
        serialize(
            uTestValueDescriptor = uTestValueDescriptor,
            kind = UTestValueDescriptorKind.LONG,
            serializeInternals = {},
            serialize = { writeLong(value) }
        )

    private fun AbstractBuffer.deserializeLong(): UTestValueDescriptor {
        return UTestConstantDescriptor.Long(readLong(), jcClasspath.long)
    }

    private fun AbstractBuffer.serialize(uTestValueDescriptor: UTestConstantDescriptor.Float) =
        serialize(
            uTestValueDescriptor = uTestValueDescriptor,
            kind = UTestValueDescriptorKind.FLOAT,
            serializeInternals = {},
            serialize = { writeFloat(value) }
        )

    private fun AbstractBuffer.deserializeFloat(): UTestValueDescriptor {
        return UTestConstantDescriptor.Float(readFloat(), jcClasspath.float)
    }

    private fun AbstractBuffer.serialize(uTestValueDescriptor: UTestConstantDescriptor.Double) =
        serialize(
            uTestValueDescriptor = uTestValueDescriptor,
            kind = UTestValueDescriptorKind.DOUBLE,
            serializeInternals = {},
            serialize = { writeDouble(value) }
        )

    private fun AbstractBuffer.deserializeDouble(): UTestValueDescriptor {
        return UTestConstantDescriptor.Double(readDouble(), jcClasspath.double)
    }

    private fun AbstractBuffer.serialize(uTestValueDescriptor: UTestConstantDescriptor.Char) =
        serialize(
            uTestValueDescriptor = uTestValueDescriptor,
            kind = UTestValueDescriptorKind.CHAR,
            serializeInternals = {},
            serialize = { writeChar(value) }
        )

    private fun AbstractBuffer.deserializeChar(): UTestValueDescriptor {
        return UTestConstantDescriptor.Char(readChar(), jcClasspath.char)
    }

    private fun AbstractBuffer.serialize(uTestValueDescriptor: UTestConstantDescriptor.String) =
        serialize(
            uTestValueDescriptor = uTestValueDescriptor,
            kind = UTestValueDescriptorKind.STRING,
            serializeInternals = {},
            serialize = { writeString(value) }
        )

    private fun AbstractBuffer.deserializeString(): UTestValueDescriptor {
        return UTestConstantDescriptor.String(readString(), jcClasspath.stringType())
    }

    private fun AbstractBuffer.serialize(uTestValueDescriptor: UTestConstantDescriptor.Null) =
        serialize(
            uTestValueDescriptor = uTestValueDescriptor,
            kind = UTestValueDescriptorKind.NULL,
            serializeInternals = {},
            serialize = { writeJcType(type) }
        )

    private fun AbstractBuffer.deserializeNull(): UTestValueDescriptor {
        return UTestConstantDescriptor.Null(readJcType(jcClasspath) ?: jcClasspath.objectType)
    }


    private fun AbstractBuffer.serialize(uTestObjectDescriptor: UTestObjectDescriptor) =
        serialize(
            uTestValueDescriptor = uTestObjectDescriptor,
            kind = UTestValueDescriptorKind.OBJECT,
            serializeInternals = {
                fields.values.forEach { serializeUTestValueDescriptor(it) }
            },
            serialize = {
                writeJcType(type)
                writeInt(refId)
                val uTestInstId = ctx.deserializedUTestInstructions.entries.find {
                    it.value == originUTestExpr
                }?.key ?: -1
                writeInt(uTestInstId)
                val entries = fields.entries
                writeInt(entries.size)
                fields.entries.forEach {
                    writeJcField(it.key)
                    writeUTestValueDescriptor(it.value)
                }
            }
        )

    private fun AbstractBuffer.deserializeObject(): UTestValueDescriptor {
        val jcType = readJcType(jcClasspath) ?: jcClasspath.objectType
        val refId = readInt()
        val instId = readInt()
        val entriesSize = readInt()
        val fields = mutableMapOf<JcField, UTestValueDescriptor>()
        val originalUTestInst = ctx.serializedUTestInstructions.entries.find {
            it.value == instId
        }?.key
        repeat(entriesSize) {
            val field = readJcField(jcClasspath)
            val descriptor = readUTestValueDescriptor()
            fields[field] = descriptor
        }
        return UTestObjectDescriptor(jcType, fields, originalUTestInst, refId)
    }

    private fun AbstractBuffer.serialize(uTestExceptionDescriptor: UTestExceptionDescriptor) =
        serialize(
            uTestValueDescriptor = uTestExceptionDescriptor,
            kind = UTestValueDescriptorKind.EXCEPTION,
            serializeInternals = {
                stackTrace.forEach { serializeUTestValueDescriptor(it) }
            },
            serialize = {
                writeJcType(type)
                writeString(message)
                writeInt(stackTrace.size)
                stackTrace.forEach { writeUTestValueDescriptor(it) }
                writeBoolean(raisedByUserCode)
            }
        )

    private fun AbstractBuffer.deserializeException(): UTestExceptionDescriptor {
        val jcType = readJcType(jcClasspath) ?: jcClasspath.objectType
        val msg = readString()
        val stackTraceSize = readInt()
        val stackTrace = mutableListOf<UTestValueDescriptor>()
        repeat(stackTraceSize) { stackTrace.add(readUTestValueDescriptor()) }
        val raisedByUserCode = readBoolean()
        return UTestExceptionDescriptor(jcType, msg, stackTrace, raisedByUserCode)
    }

    private fun AbstractBuffer.serialize(uTestObjectDescriptor: UTestClassDescriptor) =
        serialize(
            uTestValueDescriptor = uTestObjectDescriptor,
            kind = UTestValueDescriptorKind.CLASS,
            serializeInternals = {},
            serialize = { writeJcType(classType) }
        )

    private fun AbstractBuffer.deserializeClass(): UTestClassDescriptor {
        return UTestClassDescriptor(
            readJcType(jcClasspath)!!,
            jcClasspath.findTypeOrNull<Class<*>>() ?: jcClasspath.objectType
        )
    }

    private fun AbstractBuffer.serialize(uTestValueDescriptor: UTestEnumValueDescriptor) =
        serialize(
            uTestValueDescriptor = uTestValueDescriptor,
            kind = UTestValueDescriptorKind.ENUM_VALUE,
            serializeInternals = {
                fields.values.forEach { serializeUTestValueDescriptor(it) }
            },
            serialize = {
                writeJcType(type)
                writeInt(refId)
                writeString(enumValueName)
                val entries = fields.entries
                writeInt(entries.size)
                fields.entries.forEach {
                    writeJcField(it.key)
                    writeUTestValueDescriptor(it.value)
                }
            }
        )

    private fun AbstractBuffer.deserializeEnumValue(): UTestEnumValueDescriptor {
        val jcType = readJcType(jcClasspath) ?: jcClasspath.objectType
        val refId = readInt()
        val enumValueName = readString()
        val entriesSize = readInt()
        val fields = mutableMapOf<JcField, UTestValueDescriptor>()
        repeat(entriesSize) {
            val field = readJcField(jcClasspath)
            val descriptor = readUTestValueDescriptor()
            fields[field] = descriptor
        }
        return UTestEnumValueDescriptor(jcType, enumValueName, fields, refId)
    }

    private fun getUTestValueDescriptor(id: Int) = ctx.deserializedDescriptors[id] ?: error("deserialization failed")

    private val UTestValueDescriptor.id
        get() = ctx.serializedDescriptors[this]
            ?.also { check(it > 0) { "Unexpected cyclic reference?" } }
            ?: error("serialization failed")

    private enum class UTestValueDescriptorKind {
        SERIALIZED_DESCRIPTOR,
        INT,
        OBJECT,
        BOOLEAN,
        BYTE,
        SHORT,
        LONG,
        DOUBLE,
        FLOAT,
        CHAR,
        NULL,
        STRING,
        ARRAY,
        CYCLIC_REF,
        ENUM_VALUE,
        CLASS,
        EXCEPTION,
    }


    private inline fun <T : UTestValueDescriptor> AbstractBuffer.serialize(
        uTestValueDescriptor: T,
        kind: UTestValueDescriptorKind,
        serializeInternals: T.() -> Unit,
        serialize: T.() -> Unit
    ) {
        val id = ctx.serializedDescriptors.size + 1
        if (ctx.serializedDescriptors.putIfAbsent(uTestValueDescriptor, -id) != null) return
        uTestValueDescriptor.serializeInternals()
        ctx.serializedDescriptors[uTestValueDescriptor] = id
        writeEnum(kind)
        writeInt(id)
        uTestValueDescriptor.serialize()
    }

    private fun AbstractBuffer.writeUTestValueDescriptor(uTestValueDescriptor: UTestValueDescriptor) {
        writeInt(uTestValueDescriptor.id)
    }

    private fun AbstractBuffer.readUTestValueDescriptor() = getUTestValueDescriptor(readInt())

    fun deserializeUTestValueDescriptor(buffer: AbstractBuffer): UTestValueDescriptor =
        buffer.deserializeDescriptorFromBuffer()

    companion object {
        private val marshallerIdHash: Int by lazy {
            // convert to Int here since [FrameworkMarshallers.create] accepts an Int for id
            UTestValueDescriptor::class.simpleName.getPlatformIndependentHash().toInt()
        }

        val marshallerId: RdId by lazy {
            RdId(marshallerIdHash.toLong())
        }


        private fun marshaller(ctx: SerializationContext): UniversalMarshaller<UTestValueDescriptor> {
            val serializer = UTestValueDescriptorSerializer(ctx)
            return FrameworkMarshallers.create<UTestValueDescriptor>(
                writer = { buffer, uTestValueDescriptor ->
                    serializer.serialize(buffer, uTestValueDescriptor)
                },
                reader = { buffer ->
                    serializer.deserializeUTestValueDescriptor(buffer)
                },
                predefinedId = marshallerIdHash
            )
        }

        fun Serializers.registerUTestValueDescriptorSerializer(ctx: SerializationContext) {
            register(marshaller(ctx))
        }

    }


}