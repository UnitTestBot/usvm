package org.usvm.instrumentation.serializer

import com.jetbrains.rd.framework.AbstractBuffer
import com.jetbrains.rd.framework.FrameworkMarshallers
import com.jetbrains.rd.framework.RdId
import com.jetbrains.rd.framework.Serializers
import com.jetbrains.rd.framework.UniversalMarshaller
import com.jetbrains.rd.framework.getPlatformIndependentHash
import com.jetbrains.rd.framework.readEnum
import com.jetbrains.rd.framework.readList
import com.jetbrains.rd.framework.writeEnum
import com.jetbrains.rd.framework.writeList
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.char
import org.jacodb.api.jvm.ext.double
import org.jacodb.api.jvm.ext.float
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.long
import org.jacodb.api.jvm.ext.short
import org.usvm.instrumentation.testcase.api.ArithmeticOperationType
import org.usvm.instrumentation.testcase.api.ConditionType
import org.usvm.instrumentation.testcase.api.UTestAllocateMemoryCall
import org.usvm.instrumentation.testcase.api.UTestArithmeticExpression
import org.usvm.instrumentation.testcase.api.UTestArrayGetExpression
import org.usvm.instrumentation.testcase.api.UTestArrayLengthExpression
import org.usvm.instrumentation.testcase.api.UTestArraySetStatement
import org.usvm.instrumentation.testcase.api.UTestBinaryConditionExpression
import org.usvm.instrumentation.testcase.api.UTestBinaryConditionStatement
import org.usvm.instrumentation.testcase.api.UTestBooleanExpression
import org.usvm.instrumentation.testcase.api.UTestByteExpression
import org.usvm.instrumentation.testcase.api.UTestCastExpression
import org.usvm.instrumentation.testcase.api.UTestCharExpression
import org.usvm.instrumentation.testcase.api.UTestClassExpression
import org.usvm.instrumentation.testcase.api.UTestConstructorCall
import org.usvm.instrumentation.testcase.api.UTestCreateArrayExpression
import org.usvm.instrumentation.testcase.api.UTestDoubleExpression
import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.testcase.api.UTestFloatExpression
import org.usvm.instrumentation.testcase.api.UTestGetFieldExpression
import org.usvm.instrumentation.testcase.api.UTestGetStaticFieldExpression
import org.usvm.instrumentation.testcase.api.UTestGlobalMock
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestIntExpression
import org.usvm.instrumentation.testcase.api.UTestLongExpression
import org.usvm.instrumentation.testcase.api.UTestMethodCall
import org.usvm.instrumentation.testcase.api.UTestMockObject
import org.usvm.instrumentation.testcase.api.UTestNullExpression
import org.usvm.instrumentation.testcase.api.UTestSetFieldStatement
import org.usvm.instrumentation.testcase.api.UTestSetStaticFieldStatement
import org.usvm.instrumentation.testcase.api.UTestShortExpression
import org.usvm.instrumentation.testcase.api.UTestStatement
import org.usvm.instrumentation.testcase.api.UTestStaticMethodCall
import org.usvm.instrumentation.testcase.api.UTestStringExpression
import org.usvm.instrumentation.util.stringType

class UTestInstSerializer(private val ctx: SerializationContext) {

    private val jcClasspath = ctx.jcClasspath
    fun serialize(buffer: AbstractBuffer, uTestInst: UTestInst) {
        buffer.serializeUTestInst(uTestInst)
        buffer.writeEnum(UTestExpressionKind.SERIALIZED)
        buffer.writeInt(uTestInst.id)
    }

    private fun AbstractBuffer.serializeUTestInstList(uTestInstructions: List<UTestInst>) =
        uTestInstructions.forEach { serializeUTestInst(it) }

    private fun AbstractBuffer.serializeUTestInst(uTestInst: UTestInst) {
        if (ctx.serializedUTestInstructions.contains(uTestInst)) return
        when (uTestInst) {
            is UTestArrayLengthExpression -> serialize(uTestInst)
            is UTestArrayGetExpression -> serialize(uTestInst)
            is UTestAllocateMemoryCall -> serialize(uTestInst)
            is UTestConstructorCall -> serialize(uTestInst)
            is UTestMethodCall -> serialize(uTestInst)
            is UTestStaticMethodCall -> serialize(uTestInst)
            is UTestCastExpression -> serialize(uTestInst)
            is UTestNullExpression -> serialize(uTestInst)
            is UTestStringExpression -> serialize(uTestInst)
            is UTestGetFieldExpression -> serialize(uTestInst)
            is UTestGetStaticFieldExpression -> serialize(uTestInst)
            is UTestMockObject -> serialize(uTestInst)
            is UTestGlobalMock -> serialize(uTestInst)
            is UTestBinaryConditionExpression -> serialize(uTestInst)
            is UTestBinaryConditionStatement -> serialize(uTestInst)
            is UTestSetFieldStatement -> serialize(uTestInst)
            is UTestSetStaticFieldStatement -> serialize(uTestInst)
            is UTestArraySetStatement -> serialize(uTestInst)
            is UTestCreateArrayExpression -> serialize(uTestInst)
            is UTestBooleanExpression -> serialize(uTestInst)
            is UTestByteExpression -> serialize(uTestInst)
            is UTestCharExpression -> serialize(uTestInst)
            is UTestDoubleExpression -> serialize(uTestInst)
            is UTestFloatExpression -> serialize(uTestInst)
            is UTestIntExpression -> serialize(uTestInst)
            is UTestLongExpression -> serialize(uTestInst)
            is UTestShortExpression -> serialize(uTestInst)
            is UTestArithmeticExpression -> serialize(uTestInst)
            is UTestClassExpression -> serialize(uTestInst)
        }

    }

    private fun AbstractBuffer.deserializeUTestInstFromBuffer(): UTestInst {
        while (true) {
            val kind = readEnum<UTestExpressionKind>()
            val id = readInt()
            val deserializedExpression =
                when (kind) {
                    UTestExpressionKind.SERIALIZED -> {
                        return getUTestInst(id)
                    }
                    UTestExpressionKind.METHOD_CALL -> deserializeMethodCall()
                    UTestExpressionKind.CONSTRUCTOR_CALL -> deserializeConstructorCall()
                    UTestExpressionKind.CREATE_ARRAY -> deserializeUTestCreateArrayExpression()
                    UTestExpressionKind.ARRAY_SET -> deserializeUTestArraySetStatement()
                    UTestExpressionKind.SET_STATIC_FIELD -> deserializeUTestSetStaticFieldStatement()
                    UTestExpressionKind.SET_FIELD -> deserializeUTestSetFieldStatement()
                    UTestExpressionKind.BINARY_CONDITION_EXPR -> deserializeUTestBinaryConditionExpression()
                    UTestExpressionKind.BINARY_CONDITION_STATEMENT -> deserializeUTestBinaryConditionStatement()
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
                    UTestExpressionKind.CLASS -> deserializeUTestClassExpression()

                }
            ctx.deserializedUTestInstructions[id] = deserializedExpression
        }
    }

    fun deserializeUTestInst(buffer: AbstractBuffer): UTestInst =
        buffer.deserializeUTestInstFromBuffer()

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
            serializeInternals = { serializeUTestInst(arrayInstance) },
            serialize = { writeUTestInst(arrayInstance) }
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
                serializeUTestInst(arrayInstance)
                serializeUTestInst(index)
            },
            serialize = {
                writeUTestInst(arrayInstance)
                writeUTestInst(index)
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
                serializeUTestInstList(args)
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
                serializeUTestInst(expr)
            },
            serialize = {
                writeUTestInst(expr)
                writeJcType(type)
            }
        )

    private fun AbstractBuffer.deserializeUTestCastExpression(): UTestCastExpression {
        val instance = readUTestExpression()
        val type = readJcType(jcClasspath)!!
        return UTestCastExpression(instance, type)
    }

    private fun AbstractBuffer.serialize(uTestClassExpression: UTestClassExpression) =
        serialize(
            uTestExpression = uTestClassExpression,
            kind = UTestExpressionKind.CLASS,
            serializeInternals = {},
            serialize = {
                writeJcType(type)
            }
        )

    private fun AbstractBuffer.deserializeUTestClassExpression(): UTestClassExpression {
        val type = readJcType(jcClasspath)!!
        return UTestClassExpression(type)
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
            serializeInternals = { serializeUTestInst(instance) },
            serialize = {
                writeJcField(field)
                writeUTestInst(instance)
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
                fields.entries.map { serializeUTestInst(it.value) }
                methods.entries.map { it.value.map { serializeUTestInst(it) } }
            },
            serialize = {
                writeInt(fields.entries.size)
                fields.entries.map {
                    writeJcField(it.key)
                    writeUTestInst(it.value)
                }
                writeInt(methods.entries.size)
                methods.entries.map {
                    writeJcMethod(it.key)
                    writeList(it.value) { writeUTestInst(it) }
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
                fields.entries.map { serializeUTestInst(it.value) }
                methods.entries.map { it.value.map { serializeUTestInst(it) } }
            },
            serialize = {
                writeInt(fields.entries.size)
                fields.entries.map {
                    writeJcField(it.key)
                    writeUTestInst(it.value)
                }
                writeInt(methods.entries.size)
                methods.entries.map {
                    writeJcMethod(it.key)
                    writeList(it.value) { writeUTestInst(it) }
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

    private fun AbstractBuffer.serialize(uTestBinaryConditionExpression: UTestBinaryConditionExpression) =
        serialize(
            uTestExpression = uTestBinaryConditionExpression,
            kind = UTestExpressionKind.BINARY_CONDITION_EXPR,
            serializeInternals = {
                serializeUTestInst(lhv)
                serializeUTestInst(rhv)
                serializeUTestInst(uTestBinaryConditionExpression.trueBranch)
                serializeUTestInst(uTestBinaryConditionExpression.elseBranch)
            },
            serialize = {
                writeUTestInst(lhv)
                writeUTestInst(rhv)
                writeUTestInst(trueBranch)
                writeUTestInst(elseBranch)
                writeEnum(conditionType)
            }
        )

    private fun AbstractBuffer.deserializeUTestBinaryConditionExpression(): UTestBinaryConditionExpression {
        val lhv = readUTestExpression()
        val rhv = readUTestExpression()
        val trueBranch = readUTestExpression()
        val elseBranch = readUTestExpression()
        val conditionType = readEnum<ConditionType>()
        return UTestBinaryConditionExpression(conditionType, lhv, rhv, trueBranch, elseBranch)
    }

    private fun AbstractBuffer.serialize(uTestBinaryConditionStatement: UTestBinaryConditionStatement) =
        serialize(
            uTestExpression = uTestBinaryConditionStatement,
            kind = UTestExpressionKind.BINARY_CONDITION_STATEMENT,
            serializeInternals = {
                serializeUTestInst(lhv)
                serializeUTestInst(rhv)
                serializeUTestInstList(uTestBinaryConditionStatement.trueBranch)
                serializeUTestInstList(uTestBinaryConditionStatement.elseBranch)
            },
            serialize = {
                writeUTestInst(lhv)
                writeUTestInst(rhv)
                writeUTestStatementList(trueBranch)
                writeUTestStatementList(elseBranch)
                writeEnum(conditionType)
            }
        )

    private fun AbstractBuffer.deserializeUTestBinaryConditionStatement(): UTestBinaryConditionStatement {
        val lhv = readUTestExpression()
        val rhv = readUTestExpression()
        val trueBranch = readUTestStatementList()
        val elseBranch = readUTestStatementList()
        val conditionType = readEnum<ConditionType>()
        return UTestBinaryConditionStatement(conditionType, lhv, rhv, trueBranch, elseBranch)
    }

    private fun AbstractBuffer.serialize(uTestArithmeticExpression: UTestArithmeticExpression) =
        serialize(
            uTestExpression = uTestArithmeticExpression,
            kind = UTestExpressionKind.ARITHMETIC,
            serializeInternals = {
                serializeUTestInst(lhv)
                serializeUTestInst(rhv)
            },
            serialize = {
                writeEnum(operationType)
                writeUTestInst(lhv)
                writeUTestInst(rhv)
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
                serializeUTestInst(instance)
                serializeUTestInst(value)
            },
            serialize = {
                writeJcField(field)
                writeUTestInst(instance)
                writeUTestInst(value)
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
            kind = UTestExpressionKind.SET_STATIC_FIELD,
            serializeInternals = {
                serializeUTestInst(value)
            },
            serialize = {
                writeJcField(field)
                writeUTestInst(value)
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
                serializeUTestInst(arrayInstance)
                serializeUTestInst(setValueExpression)
                serializeUTestInst(index)
            },
            serialize = {
                writeUTestInst(arrayInstance)
                writeUTestInst(setValueExpression)
                writeUTestInst(index)
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
                serializeUTestInst(size)
            },
            serialize = {
                writeJcType(elementType)
                writeUTestInst(size)
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
                serializeUTestInstList(args)
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
            serializeUTestInst(instance)
            args.forEach { serializeUTestInst(it) }
        },
        serialize = {
            writeJcMethod(method)
            writeUTestInst(instance)
            writeUTestExpressionList(args)
        }
    )

    private fun AbstractBuffer.deserializeMethodCall(): UTestMethodCall {
        val jcMethod = readJcMethod(jcClasspath)
        val instance = readUTestExpression()
        val args = readUTestExpressionList()
        return UTestMethodCall(instance, jcMethod, args)
    }


    private inline fun <T : UTestInst> AbstractBuffer.serialize(
        uTestExpression: T,
        kind: UTestExpressionKind,
        serializeInternals: T.() -> Unit,
        serialize: T.() -> Unit
    ) {
        val id = ctx.serializedUTestInstructions.size + 1
        if (ctx.serializedUTestInstructions.putIfAbsent(uTestExpression, -id) != null) return
        uTestExpression.serializeInternals()
        ctx.serializedUTestInstructions[uTestExpression] = id
        writeEnum(kind)
        writeInt(id)
        uTestExpression.serialize()
    }

    private fun AbstractBuffer.writeUTestInst(uTestInst: UTestInst) {
        writeInt(uTestInst.id)
    }

    private fun AbstractBuffer.writeUTestExpressionList(uTestExpressions: List<UTestExpression>) {
        writeIntArray(uTestExpressions.map { it.id }.toIntArray())
    }

    private fun AbstractBuffer.writeUTestStatementList(uTestStatement: List<UTestStatement>) {
        writeIntArray(uTestStatement.map { it.id }.toIntArray())
    }

    private fun AbstractBuffer.readUTestExpression() = getUTestInst(readInt()) as UTestExpression

    private fun AbstractBuffer.readUTestExpressionList() = readIntArray().map { getUTestInst(it) as UTestExpression }

    private fun AbstractBuffer.readUTestStatementList() =
        readIntArray().map { getUTestInst(it) as UTestStatement }

    private fun getUTestInst(id: Int): UTestInst =
        ctx.deserializedUTestInstructions[id] ?: error("deserialization failed")

    private val UTestInst.id
        get() = ctx.serializedUTestInstructions[this]
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
        BINARY_CONDITION_EXPR,
        BINARY_CONDITION_STATEMENT,
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
        CLASS
    }

    companion object {

        private val marshallerIdHash: Int by lazy {
            // convert to Int here since [FrameworkMarshallers.create] accepts an Int for id
            UTestInst::class.simpleName.getPlatformIndependentHash().toInt()
        }

        val marshallerId: RdId by lazy {
            RdId(marshallerIdHash.toLong())
        }


        private fun marshaller(ctx: SerializationContext): UniversalMarshaller<UTestInst> {
            val serializer = UTestInstSerializer(ctx)
            return FrameworkMarshallers.create<UTestInst>(
                writer = { buffer, uTestInst ->
                    serializer.serialize(buffer, uTestInst)
                },
                reader = { buffer ->
                    serializer.deserializeUTestInst(buffer)
                },
                predefinedId = marshallerIdHash
            )
        }

        fun Serializers.registerUTestInstSerializer(ctx: SerializationContext) {
            register(marshaller(ctx))
        }
    }


}
