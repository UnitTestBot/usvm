package org.usvm.approximations

import com.jetbrains.rd.framework.UnsafeBuffer
import org.jacodb.api.jvm.JcClasspath
import org.usvm.instrumentation.serializer.SerializationContext
import org.usvm.instrumentation.serializer.UTestInstSerializer
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.UTestCall
import org.usvm.instrumentation.testcase.api.UTestInst
import kotlin.math.pow

class UTestSerializer {

    fun serialize(uTest: UTest, jcClasspath: JcClasspath): ByteArray {
        val buffer = UnsafeBuffer(2.0.pow(17).toLong())
        val ctx = SerializationContext(jcClasspath)
        val serializer = UTestInstSerializer(ctx)
        val initStmtsSize = uTest.initStatements.size
        buffer.writeInt(initStmtsSize)
        for (initStmt in uTest.initStatements) {
            serializer.serialize(buffer, initStmt)
        }
        serializer.serialize(buffer, uTest.callMethodExpression)
        val resultingByteArray = ByteArray(2.0.pow(16).toInt())
        buffer.position = 0
        buffer.readByteArrayRaw(resultingByteArray)
        return resultingByteArray
    }

    fun deserialize(byteArray: ByteArray, jcClasspath: JcClasspath): UTest {
        val buffer = UnsafeBuffer(byteArray)
        val ctx = SerializationContext(jcClasspath)
        val serializer = UTestInstSerializer(ctx)
        val initStmtsSize = buffer.readInt()
        val initStmts = mutableListOf<UTestInst>()
        for (i in 0 until initStmtsSize) {
            initStmts.add(serializer.deserializeUTestInst(buffer))
        }
        val call = serializer.deserializeUTestInst(buffer) as UTestCall
        return UTest(initStmts, call)
    }
}