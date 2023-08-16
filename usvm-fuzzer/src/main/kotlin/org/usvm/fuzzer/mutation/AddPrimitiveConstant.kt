package org.usvm.fuzzer.mutation

import org.jacodb.api.ext.*
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.util.FuzzingContext
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.util.getTypename
import org.usvm.instrumentation.util.stringType

class AddPrimitiveConstant: Mutation() {

    override fun mutate(seed: Seed, position: Int): Seed? {
        val pos = seed.positions[position]
        val type = pos.field.type
        val instance = pos.descriptor.instance
        val jcClasspath = pos.descriptor.type.classpath
        val jcField = pos.field
        val utConstExpr =
            when (type) {
            jcClasspath.short.getTypename() -> UTestShortExpression(FuzzingContext.nextShort(), jcClasspath.short)
            jcClasspath.byte.getTypename() -> UTestByteExpression(FuzzingContext.nextByte(), jcClasspath.byte)
            jcClasspath.int.getTypename() -> UTestIntExpression(FuzzingContext.nextInt(), jcClasspath.int)
            jcClasspath.long.getTypename() -> UTestLongExpression(FuzzingContext.nextLong(), jcClasspath.long)
            jcClasspath.float.getTypename() -> UTestFloatExpression(FuzzingContext.nextFloat(), jcClasspath.float)
            jcClasspath.double.getTypename() -> UTestDoubleExpression(FuzzingContext.nextDouble(), jcClasspath.double)
            jcClasspath.char.getTypename() -> UTestCharExpression(FuzzingContext.nextChar(), jcClasspath.char)
            jcClasspath.stringType().getTypename() -> UTestStringExpression(FuzzingContext.nextString(), jcClasspath.stringType())
            else -> return null
        }
        val fieldValue =  UTestGetFieldExpression(instance, jcField)
        val newFieldValue = UTestArithmeticExpression(ArithmeticOperationType.PLUS, fieldValue, utConstExpr, utConstExpr.type!!)
        return seed.mutate(position, UTestSetFieldStatement(instance, jcField,  newFieldValue))
    }
}