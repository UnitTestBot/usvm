package org.usvm.instrumentation.serializer

import com.jetbrains.rd.framework.*
import org.jacodb.api.*
import org.jacodb.api.ext.*
import org.usvm.instrumentation.util.stringType
import org.usvm.instrumentation.testcase.api.*
import readJcClass
import readJcField
import readJcMethod
import readJcType
import writeJcClass
import writeJcField
import writeJcMethod
import writeJcType

class UTestExpressionSerializer(private val ctx: SerializationContext) {

    private val jcClasspath = ctx.jcClasspath
    fun serialize(buffer: AbstractBuffer, uTestExpression: UTestExpression) {
        buffer.serializeUTestExpression(uTestExpression)
        buffer.writeEnum(UTestExpressionKind.SERIALIZED)
        buffer.writeInt(uTestExpression.id)
    }

    private fun AbstractBuffer.serializeUTestExpressionList(uTestExpressions: List<UTestExpression>) =
        uTestExpressions.forEach { serializeUTestExpression(it) }

    private fun AbstractBuffer.serializeUTestExpression(uTestExpression: UTestExpression) {
        if (ctx.serializedUTestExpressions.contains(uTestExpression)) return
        when (uTestExpression) {
            is UTestArrayLengthExpression -> serialize(uTestExpression)
            is UTestArrayGetExpression -> serialize(uTestExpression)
            is UTestAllocateMemoryCall -> serialize(uTestExpression)
            is UTestConstructorCall -> serialize(uTestExpression)
            is UTestMethodCall -> serialize(uTestExpression)
            is UTestStaticMethodCall -> serialize(uTestExpression)
            is UTestCastExpression -> serialize(uTestExpression)
            is UTestNullExpression -> serialize(uTestExpression)
            is UTestStringExpression -> serialize(uTestExpression)
            is UTestGetFieldExpression -> serialize(uTestExpression)
            is UTestGetStaticFieldExpression -> serialize(uTestExpression)
            is UTestMockObject -> serialize(uTestExpression)
            is UTestGlobalMock -> serialize(uTestExpression)
            is UTestConditionExpression -> serialize(uTestExpression)
            is UTestSetFieldStatement -> serialize(uTestExpression)
            is UTestSetStaticFieldStatement -> serialize(uTestExpression)
            is UTestArraySetStatement -> serialize(uTestExpression)
            is UTestCreateArrayExpression -> serialize(uTestExpression)
            is UTestBooleanExpression -> serialize(uTestExpression)
            is UTestByteExpression -> serialize(uTestExpression)
            is UTestCharExpression -> serialize(uTestExpression)
            is UTestDoubleExpression -> serialize(uTestExpression)
            is UTestFloatExpression -> serialize(uTestExpression)
            is UTestIntExpression -> serialize(uTestExpression)
            is UTestLongExpression -> serialize(uTestExpression)
            is UTestShortExpression -> serialize(uTestExpression)
            is UTestArithmeticExpression -> serialize(uTestExpression)
        }

    }

    private fun AbstractBuffer.deserializeUTestExpressionFromBuffer(): UTestExpression {
        while (true) {
            val kind = readEnum<UTestExpressionKind>()
            val id = readInt()
            val deserializedExpression =
                when (kind) {
                    UTestExpressionKind.SERIALIZED -> {
                        return getUTestExpression(id)
                    }
                    UTestExpressionKind.METHOD_CALL -> deserializeMethodCall()
                    UTestExpressionKind.CONSTRUCTOR_CALL -> deserializeConstructorCall()
                    UTestExpressionKind.CREATE_ARRAY -> deserializeUTestCreateArrayExpression()
                    UTestExpressionKind.ARRAY_SET -> deserializeUTestArraySetStatement()
                    UTestExpressionKind.SET_STATIC_FIELD -> deserializeUTestSetStaticFieldStatement()
                    UTestExpressionKind.SET_FIELD -> deserializeUTestSetFieldStatement()
                    UTestExpressionKind.CONDITION -> deserializeUTestConditionExpression()
                    UTestExpressionKind.MOCK_OBJECT -> deserializeUTestMockObject()
                    UTestExpressionKind.GLOBAL_MOCK -> deserializeUTestGlobalMock()
                    UTestExpressionKind.GET_STATIC_FIELD -> deserializeUTestGetStaticFieldExpression()
                    UTestExpressionKind.GET_FIELD -> deserializeUTestGetFieldExpression()
                    UTestExpressionKind.STRING -> deserializeUTestStringExpression()
                    UTestExpressionKind.NULL -> deserializeUTestNullExpression()
                    UTestExpressionKind.CAST -> deserializeUTestCastExpression()
                    UTestExpressionKind.STATIC_METHOD_CALL -> deserializeUTestStaticMethodCall()
                    UTestExpressionKind.ALLOCATE_MEMORY_CALL -> deserializeUTestAllocateMemoryCall()
                    UTestExpressionKind.ARRAY_GET -> deserializeUTestArrayGetExpression()
                    UTestExpressionKind.ARRAY_LENGTH -> deserializeUTestArrayLengthExpression()
                    UTestExpressionKind.BOOL -> deserializeBoolean()
                    UTestExpressionKind.BYTE -> deserializeByte()
                    UTestExpressionKind.CHAR -> deserializeChar()
                    UTestExpressionKind.DOUBLE -> deserializeDouble()
                    UTestExpressionKind.FLOAT -> deserializeFloat()
                    UTestExpressionKind.INT -> deserializeInt()
                    UTestExpressionKind.LONG -> deserializeLong()
                    UTestExpressionKind.SHORT -> deserializeShort()
                    UTestExpressionKind.ARITHMETIC -> deserializeUTestArithmeticExpression()
                }
            ctx.deserializerCache[id] = deserializedExpression
        }
    }

    fun deserializeUTestExpression(buffer: AbstractBuffer): UTestExpression =
        buffer.deserializeUTestExpressionFromBuffer()

    private fun AbstractBuffer.serialize(uTestBooleanExpression: UTestBooleanExpression) =
        serialize(
            uTestExpression = uTestBooleanExpression,
            kind = UTestExpressionKind.BOOL,
            serializeInternals = {},
            serialize = { writeBoolean(value) }
        )


    private fun AbstractBuffer.deserializeBoolean(): UTestBooleanExpression {
        val value = readBoolean()
        return UTestBooleanExpression(value, jcClasspath.boolean)
    }

    private fun AbstractBuffer.serialize(uTestByteExpression: UTestByteExpression) =
        serialize(
            uTestExpression = uTestByteExpression,
            kind = UTestExpressionKind.BYTE,
            serializeInternals = {},
            serialize = { writeByte(value) }
        )

    private fun AbstractBuffer.deserializeByte(): UTestByteExpression {
        val value = readByte()
        return UTestByteExpression(value, jcClasspath.byte)
    }

    private fun AbstractBuffer.serialize(uTestShortExpression: UTestShortExpression) =
        serialize(
            uTestExpression = uTestShortExpression,
            kind = UTestExpressionKind.SHORT,
            serializeInternals = {},
            serialize = { writeShort(value) }
        )

    private fun AbstractBuffer.deserializeShort(): UTestShortExpression {
        val value = readShort()
        return UTestShortExpression(value, jcClasspath.short)
    }

    private fun AbstractBuffer.serialize(uTestIntExpression: UTestIntExpression) =
        serialize(
            uTestExpression = uTestIntExpression,
            kind = UTestExpressionKind.INT,
            serializeInternals = {},
            serialize = { writeInt(value) }
        )

    private fun AbstractBuffer.deserializeInt(): UTestIntExpression {
        val value = readInt()
        return UTestIntExpression(value, jcClasspath.int)
    }

    private fun AbstractBuffer.serialize(uTestLongExpression: UTestLongExpression) =
        serialize(
            uTestExpression = uTestLongExpression,
            kind = UTestExpressionKind.LONG,
            serializeInternals = {},
            serialize = { writeLong(value) }
        )

    private fun AbstractBuffer.deserializeLong(): UTestLongExpression {
        val value = readLong()
        return UTestLongExpression(value, jcClasspath.long)
    }

    private fun AbstractBuffer.serialize(uTestFloatExpression: UTestFloatExpression) =
        serialize(
            uTestExpression = uTestFloatExpression,
            kind = UTestExpressionKind.FLOAT,
            serializeInternals = {},
            serialize = { writeFloat(value) }
        )

    private fun AbstractBuffer.deserializeFloat(): UTestFloatExpression {
        val value = readFloat()
        return UTestFloatExpression(value, jcClasspath.float)
    }

    private fun AbstractBuffer.serialize(uTestDoubleExpression: UTestDoubleExpression) =
        serialize(
            uTestExpression = uTestDoubleExpression,
            kind = UTestExpressionKind.DOUBLE,
            serializeInternals = {},
            serialize = { writeDouble(value) }
        )


    private fun AbstractBuffer.deserializeDouble(): UTestDoubleExpression {
        val value = readDouble()
        return UTestDoubleExpression(value, jcClasspath.double)
    }

    private fun AbstractBuffer.serialize(uTestCharExpression: UTestCharExpression) =
        serialize(
            uTestExpression = uTestCharExpression,
            kind = UTestExpressionKind.CHAR,
            serializeInternals = {},
            serialize = { writeChar(value) }
        )

    private fun AbstractBuffer.deserializeChar(): UTestCharExpression {
        val value = readChar()
        return UTestCharExpression(value, jcClasspath.char)
    }

    private fun AbstractBuffer.serialize(uTestArrayLengthExpression: UTestArrayLengthExpression) =
        serialize(
            uTestExpression = uTestArrayLengthExpression,
            kind = UTestExpressionKind.ARRAY_LENGTH,
            serializeInternals = { serializeUTestExpression(arrayInstance) },
            serialize = { writeUTestExpression(arrayInstance) }
        )


    private fun AbstractBuffer.deserializeUTestArrayLengthExpression(): UTestArrayLengthExpression {
        val instance = readUTestExpression()
        return UTestArrayLengthExpression(instance)
    }

    private fun AbstractBuffer.serialize(uTestArrayGetExpression: UTestArrayGetExpression) =
        serialize(
            uTestExpression = uTestArrayGetExpression,
            kind = UTestExpressionKind.ARRAY_GET,
            serializeInternals = {
                serializeUTestExpression(arrayInstance)
                serializeUTestExpression(index)
            },
            serialize = {
                writeUTestExpression(arrayInstance)
                writeUTestExpression(index)
            }
        )


    private fun AbstractBuffer.deserializeUTestArrayGetExpression(): UTestArrayGetExpression {
        val instance = readUTestExpression()
        val index = readUTestExpression()
        return UTestArrayGetExpression(instance, index)
    }

    private fun AbstractBuffer.serialize(uTestAllocateMemoryCall: UTestAllocateMemoryCall) =
        serialize(
            uTestExpression = uTestAllocateMemoryCall,
            kind = UTestExpressionKind.ALLOCATE_MEMORY_CALL,
            serializeInternals = {},
            serialize = {
                writeJcClass(clazz)
            }
        )

    private fun AbstractBuffer.deserializeUTestAllocateMemoryCall() =
        UTestAllocateMemoryCall(readJcClass(jcClasspath))


    private fun AbstractBuffer.serialize(uTestStaticMethodCall: UTestStaticMethodCall) =
        serialize(
            uTestExpression = uTestStaticMethodCall,
            kind = UTestExpressionKind.STATIC_METHOD_CALL,
            serializeInternals = {
                serializeUTestExpressionList(args)
            },
            serialize = {
                writeUTestExpressionList(args)
                writeJcMethod(method)
            }
        )

    private fun AbstractBuffer.deserializeUTestStaticMethodCall(): UTestStaticMethodCall {
        val args = readUTestExpressionList()
        val method = readJcMethod(jcClasspath)
        return UTestStaticMethodCall(method, args)
    }


    private fun AbstractBuffer.serialize(uTestCastExpression: UTestCastExpression) =
        serialize(
            uTestExpression = uTestCastExpression,
            kind = UTestExpressionKind.CAST,
            serializeInternals = {
                serializeUTestExpression(expr)
            },
            serialize = {
                writeUTestExpression(expr)
                writeJcType(type)
            }
        )

    private fun AbstractBuffer.deserializeUTestCastExpression(): UTestCastExpression {
        val instance = readUTestExpression()
        val type = readJcType(jcClasspath)!!
        return UTestCastExpression(instance, type)
    }

    private fun AbstractBuffer.serialize(uTestNullExpression: UTestNullExpression) =
        serialize(
            uTestExpression = uTestNullExpression,
            kind = UTestExpressionKind.NULL,
            serializeInternals = {},
            serialize = { writeJcType(type) }
        )

    private fun AbstractBuffer.deserializeUTestNullExpression() =
        UTestNullExpression(readJcType(jcClasspath)!!)


    private fun AbstractBuffer.serialize(uTestStringExpression: UTestStringExpression) =
        serialize(
            uTestExpression = uTestStringExpression,
            kind = UTestExpressionKind.STRING,
            serializeInternals = {},
            serialize = { writeString(value) }
        )

    private fun AbstractBuffer.deserializeUTestStringExpression() =
        UTestStringExpression(readString(), jcClasspath.stringType())

    private fun AbstractBuffer.serialize(uTestGetFieldExpression: UTestGetFieldExpression) =
        serialize(
            uTestExpression = uTestGetFieldExpression,
            kind = UTestExpressionKind.GET_FIELD,
            serializeInternals = { serializeUTestExpression(instance) },
            serialize = {
                writeJcField(field)
                writeUTestExpression(instance)
            }
        )


    private fun AbstractBuffer.deserializeUTestGetFieldExpression(): UTestGetFieldExpression {
        val jcField = readJcField(jcClasspath)
        val instance = readUTestExpression()
        return UTestGetFieldExpression(instance, jcField)
    }


    private fun AbstractBuffer.serialize(uTestGetStaticFieldExpression: UTestGetStaticFieldExpression) =
        serialize(
            uTestExpression = uTestGetStaticFieldExpression,
            kind = UTestExpressionKind.GET_STATIC_FIELD,
            serializeInternals = {},
            serialize = { writeJcField(field) }
        )

    private fun AbstractBuffer.deserializeUTestGetStaticFieldExpression() =
        UTestGetStaticFieldExpression(readJcField(jcClasspath))

    private fun AbstractBuffer.serialize(uTestMockObject: UTestMockObject) =
        serialize(
            uTestExpression = uTestMockObject,
            kind = UTestExpressionKind.MOCK_OBJECT,
            serializeInternals = {
                fields.entries.map { serializeUTestExpression(it.value) }
                methods.entries.map { it.value.map { serializeUTestExpression(it) } }
            },
            serialize = {
                writeInt(fields.entries.size)
                fields.entries.map {
                    writeJcField(it.key)
                    writeUTestExpression(it.value)
                }
                writeInt(methods.entries.size)
                methods.entries.map {
                    writeJcMethod(it.key)
                    writeList(it.value) { writeUTestExpression(it) }
                }
                writeJcType(type)
            }
        )

    private fun AbstractBuffer.deserializeUTestMockObject(): UTestMockObject {
        val fieldsToExpr = mutableMapOf<JcField, UTestExpression>()
        val methodsToExpr = mutableMapOf<JcMethod, List<UTestExpression>>()
        repeat(readInt()) {
            fieldsToExpr[readJcField(jcClasspath)] = readUTestExpression()
        }
        repeat(readInt()) {
            methodsToExpr[readJcMethod(jcClasspath)] = readList { readUTestExpression() }
        }
        val type = readJcType(jcClasspath) ?: error("Type should be not null")
        return UTestMockObject(type, fieldsToExpr, methodsToExpr)
    }

    private fun AbstractBuffer.serialize(uTestMockObject: UTestGlobalMock) =
        serialize(
            uTestExpression = uTestMockObject,
            kind = UTestExpressionKind.GLOBAL_MOCK,
            serializeInternals = {
                fields.entries.map { serializeUTestExpression(it.value) }
                methods.entries.map { it.value.map { serializeUTestExpression(it) } }
            },
            serialize = {
                writeInt(fields.entries.size)
                fields.entries.map {
                    writeJcField(it.key)
                    writeUTestExpression(it.value)
                }
                writeInt(methods.entries.size)
                methods.entries.map {
                    writeJcMethod(it.key)
                    writeList(it.value) { writeUTestExpression(it) }
                }
                writeJcType(type)
            }
        )

    private fun AbstractBuffer.deserializeUTestGlobalMock(): UTestGlobalMock {
        val fieldsToExpr = mutableMapOf<JcField, UTestExpression>()
        val methodsToExpr = mutableMapOf<JcMethod, List<UTestExpression>>()
        repeat(readInt()) {
            fieldsToExpr[readJcField(jcClasspath)] = readUTestExpression()
        }
        repeat(readInt()) {
            methodsToExpr[readJcMethod(jcClasspath)] = readList { readUTestExpression() }
        }
        val type = readJcType(jcClasspath) ?: error("Type should be not null")
        return UTestGlobalMock(type, fieldsToExpr, methodsToExpr)
    }

    private fun AbstractBuffer.serialize(uTestConditionExpression: UTestConditionExpression) =
        serialize(
            uTestExpression = uTestConditionExpression,
            kind = UTestExpressionKind.CONDITION,
            serializeInternals = {
                serializeUTestExpression(lhv)
                serializeUTestExpression(rhv)
                serializeUTestExpressionList(uTestConditionExpression.trueBranch)
                serializeUTestExpressionList(uTestConditionExpression.elseBranch)
            },
            serialize = {
                writeUTestExpression(lhv)
                writeUTestExpression(rhv)
                writeUTestStatementList(trueBranch)
                writeUTestStatementList(elseBranch)
                writeEnum(conditionType)
            }
        )

    private fun AbstractBuffer.deserializeUTestConditionExpression(): UTestConditionExpression {
        val lhv = readUTestExpression()
        val rhv = readUTestExpression()
        val trueBranch = readUTestStatementList()
        val elseBranch = readUTestStatementList()
        val conditionType = readEnum<ConditionType>()
        return UTestConditionExpression(conditionType, lhv, rhv, trueBranch, elseBranch)
    }

    private fun AbstractBuffer.serialize(uTestArithmeticExpression: UTestArithmeticExpression) =
        serialize(
            uTestExpression = uTestArithmeticExpression,
            kind = UTestExpressionKind.ARITHMETIC,
            serializeInternals = {
                serializeUTestExpression(lhv)
                serializeUTestExpression(rhv)
            },
            serialize = {
                writeEnum(operationType)
                writeUTestExpression(lhv)
                writeUTestExpression(rhv)
                writeJcType(type)
            }
        )

    private fun AbstractBuffer.deserializeUTestArithmeticExpression(): UTestArithmeticExpression {
        val operationType = readEnum<ArithmeticOperationType>()
        val lhv = readUTestExpression()
        val rhv = readUTestExpression()
        val type = readJcType(jcClasspath) ?: error("Deserialization error")
        return UTestArithmeticExpression(operationType, lhv, rhv, type)
    }

    private fun AbstractBuffer.serialize(uTestSetFieldStatement: UTestSetFieldStatement) =
        serialize(
            uTestExpression = uTestSetFieldStatement,
            kind = UTestExpressionKind.SET_FIELD,
            serializeInternals = {
                serializeUTestExpression(instance)
                serializeUTestExpression(value)
            },
            serialize = {
                writeJcField(field)
                writeUTestExpression(instance)
                writeUTestExpression(value)
            }
        )

    private fun AbstractBuffer.deserializeUTestSetFieldStatement(): UTestSetFieldStatement {
        val field = readJcField(jcClasspath)
        val instance = readUTestExpression()
        val value = readUTestExpression()
        return UTestSetFieldStatement(instance, field, value)
    }

    private fun AbstractBuffer.serialize(uTestSetStaticFieldStatement: UTestSetStaticFieldStatement) =
        serialize(
            uTestExpression = uTestSetStaticFieldStatement,
            kind = UTestExpressionKind.SET_FIELD,
            serializeInternals = {
                serializeUTestExpression(value)
            },
            serialize = {
                writeJcField(field)
                writeUTestExpression(value)
            }
        )

    private fun AbstractBuffer.deserializeUTestSetStaticFieldStatement(): UTestSetStaticFieldStatement {
        val field = readJcField(jcClasspath)
        val value = readUTestExpression()
        return UTestSetStaticFieldStatement(field, value)
    }

    private fun AbstractBuffer.serialize(uTestArraySetStatement: UTestArraySetStatement) =
        serialize(
            uTestExpression = uTestArraySetStatement,
            kind = UTestExpressionKind.ARRAY_SET,
            serializeInternals = {
                serializeUTestExpression(arrayInstance)
                serializeUTestExpression(setValueExpression)
                serializeUTestExpression(index)
            },
            serialize = {
                writeUTestExpression(arrayInstance)
                writeUTestExpression(setValueExpression)
                writeUTestExpression(index)
            }
        )

    private fun AbstractBuffer.deserializeUTestArraySetStatement(): UTestArraySetStatement {
        val instance = readUTestExpression()
        val setValueExpr = readUTestExpression()
        val index = readUTestExpression()
        return UTestArraySetStatement(instance, index, setValueExpr)
    }

    private fun AbstractBuffer.serialize(uTestCreateArrayExpression: UTestCreateArrayExpression) =
        serialize(
            uTestExpression = uTestCreateArrayExpression,
            kind = UTestExpressionKind.CREATE_ARRAY,
            serializeInternals = {
                serializeUTestExpression(size)
            },
            serialize = {
                writeJcType(elementType)
                writeUTestExpression(size)
            }
        )

    private fun AbstractBuffer.deserializeUTestCreateArrayExpression(): UTestCreateArrayExpression {
        val type = readJcType(jcClasspath)!!
        val size = readUTestExpression()
        return UTestCreateArrayExpression(type, size)
    }


    private fun AbstractBuffer.serialize(uConstructorCall: UTestConstructorCall) =
        serialize(
            uTestExpression = uConstructorCall,
            kind = UTestExpressionKind.CONSTRUCTOR_CALL,
            serializeInternals = {
                serializeUTestExpressionList(args)
            },
            serialize = {
                writeJcMethod(method)
                writeUTestExpressionList(args)
            }
        )

    private fun AbstractBuffer.deserializeConstructorCall(): UTestConstructorCall {
        val jcMethod = readJcMethod(jcClasspath)
        val args = readUTestExpressionList()
        return UTestConstructorCall(jcMethod, args)
    }

    private fun AbstractBuffer.serialize(uMethodCall: UTestMethodCall) = serialize(
        uTestExpression = uMethodCall,
        kind = UTestExpressionKind.METHOD_CALL,
        serializeInternals = {
            serializeUTestExpression(instance)
            args.forEach { serializeUTestExpression(it) }
        },
        serialize = {
            writeJcMethod(method)
            writeUTestExpression(instance)
            writeUTestExpressionList(args)
        }
    )

    private fun AbstractBuffer.deserializeMethodCall(): UTestMethodCall {
        val jcMethod = readJcMethod(jcClasspath)
        val instance = readUTestExpression()
        val args = readUTestExpressionList()
        return UTestMethodCall(instance, jcMethod, args)
    }


    private inline fun <T : UTestExpression> AbstractBuffer.serialize(
        uTestExpression: T,
        kind: UTestExpressionKind,
        serializeInternals: T.() -> Unit,
        serialize: T.() -> Unit
    ) {
        val id = ctx.serializedUTestExpressions.size + 1
        if (ctx.serializedUTestExpressions.putIfAbsent(uTestExpression, -id) != null) return
        uTestExpression.serializeInternals()
        ctx.serializedUTestExpressions[uTestExpression] = id
        writeEnum(kind)
        writeInt(id)
        uTestExpression.serialize()
    }

    private fun AbstractBuffer.writeUTestExpression(uTestExpression: UTestExpression) {
        writeInt(uTestExpression.id)
    }

    private fun AbstractBuffer.writeUTestExpressionList(uTestExpressions: List<UTestExpression>) {
        writeIntArray(uTestExpressions.map { it.id }.toIntArray())
    }

    private fun AbstractBuffer.writeUTestStatementList(uTestStatement: List<UTestStatement>) {
        writeIntArray(uTestStatement.map { it.id }.toIntArray())
    }

    private fun AbstractBuffer.readUTestExpression() = getUTestExpression(readInt())

    private fun AbstractBuffer.readUTestExpressionList() = readIntArray().map { getUTestExpression(it) }

    private fun AbstractBuffer.readUTestStatementList() =
        readIntArray().map { getUTestExpression(it) as UTestStatement }

    private fun getUTestExpression(id: Int): UTestExpression =
        ctx.deserializerCache[id] ?: error("deserialization failed")

    private val UTestExpression.id
        get() = ctx.serializedUTestExpressions[this]
            ?.also { check(it > 0) { "Unexpected cyclic reference?" } }
            ?: error("serialization failed")

    private enum class UTestExpressionKind {
        ALLOCATE_MEMORY_CALL,
        ARRAY_GET,
        ARRAY_LENGTH,
        ARRAY_SET,
        BOOL,
        BYTE,
        CAST,
        CHAR,
        CONDITION,
        CONSTRUCTOR_CALL,
        CREATE_ARRAY,
        DOUBLE,
        FLOAT,
        GET_FIELD,
        GET_STATIC_FIELD,
        INT,
        LONG,
        METHOD_CALL,
        MOCK_OBJECT,
        GLOBAL_MOCK,
        NULL,
        SERIALIZED,
        SET_FIELD,
        SET_STATIC_FIELD,
        SHORT,
        STATIC_METHOD_CALL,
        STRING,
        ARITHMETIC,
    }

    companion object {

        private val marshallerIdHash: Int by lazy {
            // convert to Int here since [FrameworkMarshallers.create] accepts an Int for id
            UTestExpression::class.simpleName.getPlatformIndependentHash().toInt()
        }

        val marshallerId: RdId by lazy {
            RdId(marshallerIdHash.toLong())
        }


        private fun marshaller(ctx: SerializationContext): UniversalMarshaller<UTestExpression> {
            val serializer = UTestExpressionSerializer(ctx)
            return FrameworkMarshallers.create<UTestExpression>(
                writer = { buffer, uTestExpression ->
                    serializer.serialize(buffer, uTestExpression)
                },
                reader = { buffer ->
                    serializer.deserializeUTestExpression(buffer)
                },
                predefinedId = marshallerIdHash
            )
        }

        fun Serializers.registerUTestExpressionSerializer(ctx: SerializationContext) {
            register(marshaller(ctx))
        }
    }


}
