package org.usvm.instrumentation.serializer

import com.jetbrains.rd.framework.*
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.ext.*
import org.usvm.instrumentation.jacodb.util.stringType
import org.usvm.instrumentation.testcase.descriptor.UTestArrayDescriptor
import org.usvm.instrumentation.testcase.descriptor.UTestConstantDescriptor
import org.usvm.instrumentation.testcase.descriptor.UTestObjectDescriptor
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor
import org.usvm.instrumentation.testcase.statement.UTestExpression
import readJcField
import readJcType
import writeJcField
import writeJcType
import java.util.IdentityHashMap

class UTestValueDescriptorSerializer(val buffer: AbstractBuffer, val jcdbClasspath: JcClasspath) {

    private val serializedDescriptors = IdentityHashMap<UTestValueDescriptor, Int>()
    private val deserializedDescriptors = HashMap<Int, UTestValueDescriptor>()

    fun serialize(uTestValueDescriptor: UTestValueDescriptor) {
        serializeUTestValueDescriptor(uTestValueDescriptor)
        buffer.writeEnum(UTestValueDescriptorKind.SERIALIZED_DESCRIPTOR)
        buffer.writeInt(uTestValueDescriptor.id)
    }

    fun deserialize(): UTestValueDescriptor {
        while (true) {
            val kind = buffer.readEnum<UTestValueDescriptorKind>()
            val id = buffer.readInt()
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
                }
            deserializedDescriptors[id] = deserializedExpression
        }

    }

    private val UTestValueDescriptor.id
        get() = serializedDescriptors[this] ?: error("serialization failed")

    private fun getUTestValueDescriptor(id: Int) = deserializedDescriptors[id] ?: error("deserialization failed")

    private fun serializeUTestValueDescriptor(uTestValueDescriptor: UTestValueDescriptor) {
        if (serializedDescriptors.contains(uTestValueDescriptor)) return
        when (uTestValueDescriptor) {
            is UTestArrayDescriptor.Array -> TODO()
            is UTestArrayDescriptor.BooleanArray -> TODO()
            is UTestArrayDescriptor.ByteArray -> TODO()
            is UTestArrayDescriptor.CharArray -> TODO()
            is UTestArrayDescriptor.DoubleArray -> TODO()
            is UTestArrayDescriptor.FloatArray -> TODO()
            is UTestArrayDescriptor.IntArray -> TODO()
            is UTestArrayDescriptor.LongArray -> TODO()
            is UTestArrayDescriptor.ShortArray -> TODO()
            is UTestConstantDescriptor.Boolean -> serialize(uTestValueDescriptor)
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
        }
    }

    private fun serialize(uTestValueDescriptor: UTestConstantDescriptor.Boolean) {
        serialize(uTestValueDescriptor, UTestValueDescriptorKind.BOOLEAN) {
            writeBoolean(uTestValueDescriptor.value)
        }
    }

    private fun deserializeBoolean(): UTestValueDescriptor = with(buffer) {
        UTestConstantDescriptor.Boolean(readBoolean(), jcdbClasspath.boolean)
    }

    private fun serialize(uTestValueDescriptor: UTestConstantDescriptor.Byte) {
        serialize(uTestValueDescriptor, UTestValueDescriptorKind.BYTE) {
            writeByte(uTestValueDescriptor.value)
        }
    }

    private fun deserializeByte(): UTestValueDescriptor = with(buffer) {
        UTestConstantDescriptor.Byte(readByte(), jcdbClasspath.byte)
    }

    private fun serialize(uTestValueDescriptor: UTestConstantDescriptor.Short) {
        serialize(uTestValueDescriptor, UTestValueDescriptorKind.SHORT) {
            writeShort(uTestValueDescriptor.value)
        }
    }

    private fun deserializeShort(): UTestValueDescriptor = with(buffer) {
        UTestConstantDescriptor.Short(readShort(), jcdbClasspath.short)
    }

    private fun serialize(uTestValueDescriptor: UTestConstantDescriptor.Int) {
        serialize(uTestValueDescriptor, UTestValueDescriptorKind.INT) {
            writeInt(uTestValueDescriptor.value)
        }
    }

    private fun deserializeInt(): UTestValueDescriptor = with(buffer) {
        UTestConstantDescriptor.Int(readInt(), jcdbClasspath.int)
    }

    private fun serialize(uTestValueDescriptor: UTestConstantDescriptor.Long) {
        serialize(uTestValueDescriptor, UTestValueDescriptorKind.LONG) {
            writeLong(uTestValueDescriptor.value)
        }
    }

    private fun deserializeLong(): UTestValueDescriptor = with(buffer) {
        UTestConstantDescriptor.Long(readLong(), jcdbClasspath.long)
    }

    private fun serialize(uTestValueDescriptor: UTestConstantDescriptor.Float) {
        serialize(uTestValueDescriptor, UTestValueDescriptorKind.FLOAT) {
            writeFloat(uTestValueDescriptor.value)
        }
    }

    private fun deserializeFloat(): UTestValueDescriptor = with(buffer) {
        UTestConstantDescriptor.Float(readFloat(), jcdbClasspath.float)
    }

    private fun serialize(uTestValueDescriptor: UTestConstantDescriptor.Double) {
        serialize(uTestValueDescriptor, UTestValueDescriptorKind.DOUBLE) {
            writeDouble(uTestValueDescriptor.value)
        }
    }

    private fun deserializeDouble(): UTestValueDescriptor = with(buffer) {
        UTestConstantDescriptor.Double(readDouble(), jcdbClasspath.double)
    }

    private fun serialize(uTestValueDescriptor: UTestConstantDescriptor.Char) {
        serialize(uTestValueDescriptor, UTestValueDescriptorKind.CHAR) {
            writeChar(uTestValueDescriptor.value)
        }
    }

    private fun deserializeChar(): UTestValueDescriptor = with(buffer) {
        UTestConstantDescriptor.Char(readChar(), jcdbClasspath.char)
    }

    private fun serialize(uTestValueDescriptor: UTestConstantDescriptor.String) {
        serialize(uTestValueDescriptor, UTestValueDescriptorKind.STRING) {
            writeString(uTestValueDescriptor.value)
        }
    }

    private fun deserializeString(): UTestValueDescriptor = with(buffer) {
        UTestConstantDescriptor.String(readString(), jcdbClasspath.stringType())
    }

    private fun serialize(uTestValueDescriptor: UTestConstantDescriptor.Null) {
        serialize(uTestValueDescriptor, UTestValueDescriptorKind.NULL) {
            writeJcType(uTestValueDescriptor.type)
        }
    }

    private fun deserializeNull(): UTestValueDescriptor = with(buffer) {
        UTestConstantDescriptor.Null(readJcType(jcdbClasspath) ?: jcdbClasspath.objectType)
    }

    private fun serialize(uTestObjectDescriptor: UTestObjectDescriptor) {
        uTestObjectDescriptor.fields.values.forEach { serializeUTestValueDescriptor(it) }
        serialize(uTestObjectDescriptor, UTestValueDescriptorKind.OBJECT) {
            writeJcType(uTestObjectDescriptor.type)
            val entries = uTestObjectDescriptor.fields.entries
            writeInt(entries.size)
            uTestObjectDescriptor.fields.entries.forEach {
                writeJcField(it.key)
                writeUTestValueDescriptor(it.value)
            }
        }
    }

    private fun deserializeObject(): UTestValueDescriptor = with(buffer) {
        val jcType = readJcType(jcdbClasspath) ?: jcdbClasspath.objectType
        val entriesSize = readInt()
        val fields = mutableMapOf<JcField, UTestValueDescriptor>()
        repeat(entriesSize) {
            val field = readJcField(jcdbClasspath)
            val descriptor = readUTestValueDescriptor()
            fields[field] = descriptor
        }
        UTestObjectDescriptor(jcType, fields)
    }

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
        STRING
    }


    private inline fun serialize(
        uTestValueDescriptor: UTestValueDescriptor,
        kind: UTestValueDescriptorKind,
        body: AbstractBuffer.() -> Unit
    ) {
        val id = serializedDescriptors.size
        if (serializedDescriptors.putIfAbsent(uTestValueDescriptor, id) != null) return
        buffer.writeEnum(kind)
        buffer.writeInt(id)
        buffer.body()
    }

    private fun AbstractBuffer.writeUTestValueDescriptor(uTestValueDescriptor: UTestValueDescriptor) {
        writeInt(uTestValueDescriptor.id)
    }

    private fun AbstractBuffer.readUTestValueDescriptor() = getUTestValueDescriptor(readInt())

    companion object {
        private val marshallerIdHash: Int by lazy {
            // convert to Int here since [FrameworkMarshallers.create] accepts an Int for id
            UTestValueDescriptor::class.simpleName.getPlatformIndependentHash().toInt()
        }

        val marshallerId: RdId by lazy {
            RdId(marshallerIdHash.toLong())
        }


        fun marshaller(jcdbClasspath: JcClasspath) =
            FrameworkMarshallers.create<UTestValueDescriptor>(
                writer = { buffer, uTestValueDescriptor ->
                    UTestValueDescriptorSerializer(buffer, jcdbClasspath).serialize(uTestValueDescriptor)
                },
                reader = { buffer ->
                    UTestValueDescriptorSerializer(buffer, jcdbClasspath).deserialize()
                },
                predefinedId = marshallerIdHash
            )

        fun Serializers.registerUTestValueDescriptorSerializer(jcdbClasspath: JcClasspath) {
            register(marshaller(jcdbClasspath))
        }

    }


}