package org.usvm.instrumentation.serializer

import com.jetbrains.rd.framework.*
import org.jacodb.api.*
import org.jacodb.api.ext.*
import org.usvm.instrumentation.jacodb.util.stringType
import org.usvm.instrumentation.testcase.statement.*
import readJcClass
import readJcField
import readJcMethod
import readJcType
import writeJcClass
import writeJcField
import writeJcMethod
import writeJcType
import java.util.*

class UTestExpressionSerializer(val buffer: AbstractBuffer, val jcdbClasspath: JcClasspath) {

    private val serializedUTestExpressions = IdentityHashMap<UTestExpression, Int>()
    private val deserializerCache: MutableMap<Int, UTestExpression> = hashMapOf()

    fun serialize(uTestExpression: UTestExpression) {
        serializeUTestExpression(uTestExpression)
        buffer.writeEnum(UTestExpressionKind.SERIALIZED)
        buffer.writeInt(uTestExpression.id)
    }

    private fun serializeList(uTestExpressions: List<UTestExpression>) = uTestExpressions.forEach { serialize(it) }

    private fun serializeUTestExpression(uTestExpression: UTestExpression) {
        if (serializedUTestExpressions.contains(uTestExpression)) return
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
        }

    }

    fun deserializeUTestExpression(): UTestExpression {
        while (true) {
            val kind = buffer.readEnum<UTestExpressionKind>()
            val id = buffer.readInt()
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
                }
            deserializerCache[id] = deserializedExpression
        }
    }

    private fun serialize(uTestBooleanExpression: UTestBooleanExpression) {
        serialize(uTestBooleanExpression, UTestExpressionKind.BOOL) {
            writeBoolean(uTestBooleanExpression.value)
        }
    }

    private fun deserializeBoolean() = with(buffer) {
        val value = readBoolean()
        UTestBooleanExpression(value, jcdbClasspath.boolean)
    }

    private fun serialize(uTestByteExpression: UTestByteExpression) {
        serialize(uTestByteExpression, UTestExpressionKind.BYTE) {
            writeByte(uTestByteExpression.value)
        }
    }

    private fun deserializeByte() = with(buffer) {
        val value = readByte()
        UTestByteExpression(value, jcdbClasspath.byte)
    }

    private fun serialize(uTestShortExpression: UTestShortExpression) {
        serialize(uTestShortExpression, UTestExpressionKind.SHORT) {
            writeShort(uTestShortExpression.value)
        }
    }

    private fun deserializeShort() = with(buffer) {
        val value = readShort()
        UTestShortExpression(value, jcdbClasspath.short)
    }

    private fun serialize(uTestIntExpression: UTestIntExpression) {
        serialize(uTestIntExpression, UTestExpressionKind.INT) {
            writeInt(uTestIntExpression.value)
        }
    }

    private fun deserializeInt() = with(buffer) {
        val value = readInt()
        UTestIntExpression(value, jcdbClasspath.int)
    }

    private fun serialize(uTestLongExpression: UTestLongExpression) {
        serialize(uTestLongExpression, UTestExpressionKind.LONG) {
            writeLong(uTestLongExpression.value)
        }
    }

    private fun deserializeLong() = with(buffer) {
        val value = readLong()
        UTestLongExpression(value, jcdbClasspath.long)
    }

    private fun serialize(uTestFloatExpression: UTestFloatExpression) {
        serialize(uTestFloatExpression, UTestExpressionKind.FLOAT) {
            writeFloat(uTestFloatExpression.value)
        }
    }

    private fun deserializeFloat() = with(buffer) {
        val value = readFloat()
        UTestFloatExpression(value, jcdbClasspath.float)
    }

    private fun serialize(uTestDoubleExpression: UTestDoubleExpression) {
        serialize(uTestDoubleExpression, UTestExpressionKind.DOUBLE) {
            writeDouble(uTestDoubleExpression.value)
        }
    }

    private fun deserializeDouble() = with(buffer) {
        val value = readDouble()
        UTestDoubleExpression(value, jcdbClasspath.double)
    }

    private fun serialize(uTestCharExpression: UTestCharExpression) {
        serialize(uTestCharExpression, UTestExpressionKind.CHAR) {
            writeChar(uTestCharExpression.value)
        }
    }

    private fun deserializeChar() = with(buffer) {
        val value = readChar()
        UTestCharExpression(value, jcdbClasspath.char)
    }

    private fun serialize(uTestArrayLengthExpression: UTestArrayLengthExpression) {
        serialize(uTestArrayLengthExpression.arrayInstance)
        serialize(uTestArrayLengthExpression, UTestExpressionKind.ARRAY_LENGTH) {
            serialize(uTestArrayLengthExpression.arrayInstance)
        }
    }

    private fun deserializeUTestArrayLengthExpression() = with(buffer) {
        val instance = readUTestExpression()
        UTestArrayLengthExpression(instance)
    }

    private fun serialize(uTestArrayGetExpression: UTestArrayGetExpression) {
        serialize(uTestArrayGetExpression.arrayInstance)
        serialize(uTestArrayGetExpression.index)
        serialize(uTestArrayGetExpression, UTestExpressionKind.ARRAY_GET) {
            writeUTestExpression(uTestArrayGetExpression.arrayInstance)
            writeUTestExpression(uTestArrayGetExpression.index)
        }
    }

    private fun deserializeUTestArrayGetExpression() = with(buffer) {
        val instance = readUTestExpression()
        val index = readUTestExpression()
        UTestArrayGetExpression(instance, index)
    }

    private fun serialize(uTestAllocateMemoryCall: UTestAllocateMemoryCall) {
        serialize(uTestAllocateMemoryCall, UTestExpressionKind.ALLOCATE_MEMORY_CALL) {
            writeJcClass(uTestAllocateMemoryCall.clazz)
        }
    }

    private fun deserializeUTestAllocateMemoryCall() = with(buffer) {
        UTestAllocateMemoryCall(readJcClass(jcdbClasspath))
    }

    private fun serialize(uTestStaticMethodCall: UTestStaticMethodCall) {
        serializeList(uTestStaticMethodCall.args)
        serialize(uTestStaticMethodCall, UTestExpressionKind.STATIC_METHOD_CALL) {
            writeUTestExpressionList(uTestStaticMethodCall.args)
            writeJcMethod(uTestStaticMethodCall.method)
        }
    }

    private fun deserializeUTestStaticMethodCall() = with(buffer) {
        val args = readUTestExpressionList()
        val method = readJcMethod(jcdbClasspath)
        UTestStaticMethodCall(method, args)
    }


    private fun serialize(uTestCastExpression: UTestCastExpression) {
        serialize(uTestCastExpression.expr)
        serialize(uTestCastExpression, UTestExpressionKind.CAST) {
            writeUTestExpression(uTestCastExpression.expr)
            writeJcType(uTestCastExpression.type)
        }
    }

    private fun deserializeUTestCastExpression() = with(buffer) {
        val instance = readUTestExpression()
        val type = readJcType(jcdbClasspath)!!
        UTestCastExpression(instance, type)
    }


    private fun serialize(uTestNullExpression: UTestNullExpression) {
        serialize(uTestNullExpression, UTestExpressionKind.NULL) {
            writeJcType(uTestNullExpression.type)
        }
    }

    private fun deserializeUTestNullExpression() = with(buffer) {
        UTestNullExpression(readJcType(jcdbClasspath)!!)
    }


    private fun serialize(uTestStringExpression: UTestStringExpression) {
        serialize(uTestStringExpression, UTestExpressionKind.STRING) {
            writeString(uTestStringExpression.value)
        }
    }

    private fun deserializeUTestStringExpression() = with(buffer) {
        UTestStringExpression(readString(), jcdbClasspath.stringType())
    }

    private fun serialize(uTestGetFieldExpression: UTestGetFieldExpression) {
        serialize(uTestGetFieldExpression.instance)
        serialize(uTestGetFieldExpression, UTestExpressionKind.GET_FIELD) {
            writeJcField(uTestGetFieldExpression.field)
            writeUTestExpression(uTestGetFieldExpression.instance)
        }
    }

    private fun deserializeUTestGetFieldExpression() = with(buffer) {
        val jcField = readJcField(jcdbClasspath)
        val instance = readUTestExpression()
        UTestGetFieldExpression(instance, jcField)
    }


    private fun serialize(uTestGetStaticFieldExpression: UTestGetStaticFieldExpression) {
        serialize(uTestGetStaticFieldExpression, UTestExpressionKind.GET_STATIC_FIELD) {
            writeJcField(uTestGetStaticFieldExpression.field)
        }
    }
    private fun deserializeUTestGetStaticFieldExpression() = with(buffer) {
        UTestGetStaticFieldExpression(readJcField(jcdbClasspath))
    }

    private fun serialize(uTestMockObject: UTestMockObject) {
        val fieldsToExpr = uTestMockObject.fields.entries
        val methodsToExpr = uTestMockObject.methods.entries
        fieldsToExpr.map { serialize(it.value) }
        methodsToExpr.map { serialize(it.value) }
        serialize(uTestMockObject, UTestExpressionKind.MOCK_OBJECT) {
            writeInt(fieldsToExpr.size)
            fieldsToExpr.map {
                writeJcField(it.key)
                writeUTestExpression(it.value)
            }
            writeInt(methodsToExpr.size)
            methodsToExpr.map {
                writeJcMethod(it.key)
                writeUTestExpression(it.value)
            }
            writeJcType(uTestMockObject.type)
        }
    }

    private fun deserializeUTestMockObject() = with(buffer) {
        val fieldsToExpr = mutableMapOf<JcField, UTestExpression>()
        val methodsToExpr = mutableMapOf<JcMethod, UTestExpression>()
        repeat(readInt()) {
            fieldsToExpr[readJcField(jcdbClasspath)] = readUTestExpression()
        }
        repeat(readInt()) {
            methodsToExpr[readJcMethod(jcdbClasspath)] = readUTestExpression()
        }
        val type = readJcType(jcdbClasspath)
        UTestMockObject(type, fieldsToExpr, methodsToExpr)
    }

    private fun serialize(uTestConditionExpression: UTestConditionExpression) {
        serialize(uTestConditionExpression.lhv)
        serialize(uTestConditionExpression.rhv)
        serializeList(uTestConditionExpression.trueBranch)
        serializeList(uTestConditionExpression.elseBranch)
        serialize(uTestConditionExpression, UTestExpressionKind.CONDITION) {
            writeUTestExpression(uTestConditionExpression.lhv)
            writeUTestExpression(uTestConditionExpression.rhv)
            writeUTestStatementList(uTestConditionExpression.trueBranch)
            writeUTestStatementList(uTestConditionExpression.elseBranch)
            writeEnum(uTestConditionExpression.conditionType)
        }
    }

    private fun deserializeUTestConditionExpression(): UTestConditionExpression = with(buffer) {
        val lhv = readUTestExpression()
        val rhv = readUTestExpression()
        val trueBranch = readUTestStatementList()
        val elseBranch = readUTestStatementList()
        val conditionType = readEnum<ConditionType>()
        UTestConditionExpression(conditionType, lhv, rhv, trueBranch, elseBranch)
    }

    private fun serialize(uTestSetFieldStatement: UTestSetFieldStatement) {
        serialize(uTestSetFieldStatement.instance)
        serialize(uTestSetFieldStatement.value)
        serialize(uTestSetFieldStatement, UTestExpressionKind.SET_FIELD) {
            writeJcField(uTestSetFieldStatement.field)
            writeUTestExpression(uTestSetFieldStatement.instance)
            writeUTestExpression(uTestSetFieldStatement.value)
        }
    }

    private fun deserializeUTestSetFieldStatement(): UTestSetFieldStatement = with(buffer) {
        val field = readJcField(jcdbClasspath)
        val instance = readUTestExpression()
        val value = readUTestExpression()
        UTestSetFieldStatement(instance, field, value)
    }

    private fun serialize(uTestSetStaticFieldStatement: UTestSetStaticFieldStatement) {
        serialize(uTestSetStaticFieldStatement.value)
        serialize(uTestSetStaticFieldStatement, UTestExpressionKind.SET_STATIC_FIELD) {
            writeJcField(uTestSetStaticFieldStatement.field)
            writeUTestExpression(uTestSetStaticFieldStatement.value)
        }
    }

    private fun deserializeUTestSetStaticFieldStatement(): UTestSetStaticFieldStatement = with(buffer) {
        val field = readJcField(jcdbClasspath)
        val value = readUTestExpression()
        UTestSetStaticFieldStatement(field, value)
    }

    private fun serialize(uTestArraySetStatement: UTestArraySetStatement) {
        serialize(uTestArraySetStatement.arrayInstance)
        serialize(uTestArraySetStatement.setValueExpression)
        serialize(uTestArraySetStatement.index)
        serialize(uTestArraySetStatement, UTestExpressionKind.ARRAY_SET) {
            writeUTestExpression(uTestArraySetStatement.arrayInstance)
            writeUTestExpression(uTestArraySetStatement.setValueExpression)
            writeUTestExpression(uTestArraySetStatement.index)
        }
    }

    private fun deserializeUTestArraySetStatement(): UTestArraySetStatement = with(buffer) {
        val instance = readUTestExpression()
        val setValueExpr = readUTestExpression()
        val index = readUTestExpression()
        UTestArraySetStatement(instance, setValueExpr, index)
    }

    private fun serialize(uTestCreateArrayExpression: UTestCreateArrayExpression) {
        serialize(uTestCreateArrayExpression.size)
        serialize(uTestCreateArrayExpression, UTestExpressionKind.CREATE_ARRAY) {
            writeJcType(uTestCreateArrayExpression.elementType)
            writeUTestExpression(uTestCreateArrayExpression.size)
        }
    }

    private fun deserializeUTestCreateArrayExpression(): UTestCreateArrayExpression = with(buffer) {
        val type = readJcType(jcdbClasspath)!!
        val size = readUTestExpression()
        UTestCreateArrayExpression(type, size)
    }


    private fun deserializeMethodCall(): UTestMethodCall = with(buffer) {
        val jcMethod = readJcMethod(jcdbClasspath)
        val instance = readUTestExpression()
        val args = readUTestExpressionList()
        UTestMethodCall(instance, jcMethod, args)
    }

    private fun serialize(uConstructorCall: UTestConstructorCall) {
        uConstructorCall.args.forEach { serializeUTestExpression(it) }
        serialize(uConstructorCall, UTestExpressionKind.CONSTRUCTOR_CALL) {
            writeJcMethod(uConstructorCall.constructor)
            writeUTestExpressionList(uConstructorCall.args)
        }
    }

    private fun deserializeConstructorCall(): UTestConstructorCall = with(buffer) {
        val jcMethod = readJcMethod(jcdbClasspath)
        val args = readUTestExpressionList()
        UTestConstructorCall(jcMethod, args)
    }

    private fun serialize(uMethodCall: UTestMethodCall) {
        serializeUTestExpression(uMethodCall.instance)
        uMethodCall.args.forEach { serializeUTestExpression(it) }
        serialize(uMethodCall, UTestExpressionKind.METHOD_CALL) {
            writeJcMethod(uMethodCall.method)
            writeUTestExpression(uMethodCall.instance)
            writeUTestExpressionList(uMethodCall.args)
        }
    }


    private inline fun serialize(
        uTestExpression: UTestExpression,
        kind: UTestExpressionKind,
        body: AbstractBuffer.() -> Unit
    ) {
        val id = serializedUTestExpressions.size
        if (serializedUTestExpressions.putIfAbsent(uTestExpression, id) != null) return
        buffer.writeEnum(kind)
        buffer.writeInt(id)
        buffer.body()
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

    private fun AbstractBuffer.readUTestStatementList() = readIntArray().map { getUTestExpression(it) as UTestStatement }

    private fun getUTestExpression(id: Int) = deserializerCache[id] ?: error("deserialization failed")

    private val UTestExpression.id
        get() = serializedUTestExpressions[this] ?: error("serialization failed")

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
        NULL,
        SERIALIZED,
        SET_FIELD,
        SET_STATIC_FIELD,
        SHORT,
        STATIC_METHOD_CALL,
        STRING,
    }

    companion object {

        private val marshallerIdHash: Int by lazy {
            // convert to Int here since [FrameworkMarshallers.create] accepts an Int for id
            UTestExpression::class.simpleName.getPlatformIndependentHash().toInt()
        }

        val marshallerId: RdId by lazy {
            RdId(marshallerIdHash.toLong())
        }


        fun marshaller(jcdbClasspath: JcClasspath) =
            FrameworkMarshallers.create<UTestExpression>(
                writer = { buffer, uTestExpression ->
                    UTestExpressionSerializer(buffer, jcdbClasspath).serialize(uTestExpression)
                },
                reader = { buffer ->
                    UTestExpressionSerializer(buffer, jcdbClasspath).deserializeUTestExpression()
                },
                predefinedId = marshallerIdHash
            )

        fun Serializers.registerUTestExpressionSerializer(jcdbClasspath: JcClasspath) {
            register(marshaller(jcdbClasspath))
        }
    }


}
