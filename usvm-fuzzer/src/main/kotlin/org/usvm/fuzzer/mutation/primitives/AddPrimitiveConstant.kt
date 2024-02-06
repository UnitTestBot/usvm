package org.usvm.fuzzer.mutation.primitives

import org.jacodb.api.JcPrimitiveType
import org.usvm.fuzzer.generator.DataFactory
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.mutation.Mutation
import org.usvm.fuzzer.mutation.MutationInfo
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.instrumentation.testcase.api.*

class AddPrimitiveConstant : Mutation() {
    override val mutationFun: DataFactory.(Seed) -> Pair<Seed?, MutationInfo>? = lambda@{ seed ->
        //TODO add mutation for random field
        val argForMutation =
//            if (random.getTrueWithProb(50)) {
            seed.getArgForMutation { it.type.type is JcPrimitiveType } ?: return@lambda null
//            } else {
//                val fieldForMutation = Seed.fieldInfo.getBestField { it.type.isPrimitive } ?: return@lambda null
//                seed.getFieldsInTermsOfUTest(fieldForMutation).randomOrNull()
//            } ?: return@lambda null
        val newValue =
            when (val uTestForMutation = argForMutation.instance) {
                is UTestByteExpression -> UTestByteExpression(generateNewIntNumericValue(dataFactory, uTestForMutation.value.toInt()).toByte(), uTestForMutation.type)
                is UTestShortExpression -> UTestShortExpression(generateNewIntNumericValue(dataFactory, uTestForMutation.value.toInt()).toShort(), uTestForMutation.type)
                is UTestIntExpression -> UTestIntExpression(generateNewIntNumericValue(dataFactory, uTestForMutation.value), uTestForMutation.type)
                is UTestLongExpression -> UTestLongExpression(generateNewLongNumericValue(dataFactory, uTestForMutation.value), uTestForMutation.type)
                is UTestCharExpression -> UTestCharExpression(generateNewIntNumericValue(dataFactory, uTestForMutation.value.code).toChar(), uTestForMutation.type)
                is UTestFloatExpression -> UTestFloatExpression(generateNewDoubleNumericValue(dataFactory, uTestForMutation.value.toDouble()).toFloat(), uTestForMutation.type)
                is UTestDoubleExpression -> UTestDoubleExpression(generateNewDoubleNumericValue(dataFactory, uTestForMutation.value), uTestForMutation.type)
                else -> return@lambda null
            }
        val newArgDescriptor =
            Seed.ArgumentDescriptor(newValue, JcTypeWrapper(newValue.type!!, newValue.value::class.java), listOf())
        return@lambda seed.mutate(argForMutation, newArgDescriptor) to MutationInfo(argForMutation, null)
    }

    private fun generateNewIntNumericValue(dataFactory: DataFactory, oldValue: Int): Int = with(dataFactory) {
        if (random.getTrueWithProb(30)) {
            oldValue + 1
        } else if (random.getTrueWithProb(30)) {
            oldValue - 1
        } else if (random.getTrueWithProb(1)) {
            Int.MIN_VALUE
        } else if (random.getTrueWithProb(1)) {
            Int.MAX_VALUE
        } else {
            random.nextInt()
        }
    }

    private fun generateNewLongNumericValue(dataFactory: DataFactory, oldValue: Long): Long = with(dataFactory) {
        if (random.getTrueWithProb(30)) {
            oldValue + 1
        } else if (random.getTrueWithProb(30)) {
            oldValue - 1
        } else if (random.getTrueWithProb(1)) {
            Long.MIN_VALUE
        } else if (random.getTrueWithProb(1)) {
            Long.MAX_VALUE
        } else {
            random.nextLong()
        }
    }

    private fun generateNewDoubleNumericValue(dataFactory: DataFactory, oldValue: Double): Double = with(dataFactory) {
        if (random.getTrueWithProb(30)) {
            oldValue + 1
        } else if (random.getTrueWithProb(30)) {
            oldValue - 1
        } else if (random.getTrueWithProb(1)) {
            Double.MIN_VALUE
        } else if (random.getTrueWithProb(1)) {
            Double.MAX_VALUE
        } else {
            random.nextDouble()
        }
    }

}
//class AddPrimitiveConstant : Mutation() {
//
//    override fun mutate(seed: Seed, position: Int): Seed? {
//        return null
////        val pos = seed.positions[position]
////        val type = pos.field.type
////        val instance = pos.descriptor.instance
////        val jcClasspath = pos.descriptor.type.type.classpath
////        val jcField = pos.field
////        val utConstExpr =
////            when (type) {
////            jcClasspath.short.getTypename() -> UTestShortExpression(FuzzingContext.nextShort(), jcClasspath.short)
////            jcClasspath.byte.getTypename() -> UTestByteExpression(FuzzingContext.nextByte(), jcClasspath.byte)
////            jcClasspath.int.getTypename() -> UTestIntExpression(FuzzingContext.nextInt(), jcClasspath.int)
////            jcClasspath.long.getTypename() -> UTestLongExpression(FuzzingContext.nextLong(), jcClasspath.long)
////            jcClasspath.float.getTypename() -> UTestFloatExpression(FuzzingContext.nextFloat(), jcClasspath.float)
////            jcClasspath.double.getTypename() -> UTestDoubleExpression(FuzzingContext.nextDouble(), jcClasspath.double)
////            jcClasspath.char.getTypename() -> UTestCharExpression(FuzzingContext.nextChar(), jcClasspath.char)
////            jcClasspath.stringType().getTypename() -> UTestStringExpression(FuzzingContext.nextString(), jcClasspath.stringType())
////            else -> return null
////        }
////        val fieldValue =  UTestGetFieldExpression(instance, jcField)
////        val newFieldValue = UTestArithmeticExpression(ArithmeticOperationType.PLUS, fieldValue, utConstExpr, utConstExpr.type!!)
////        return seed.mutate(position, UTestSetFieldStatement(instance, jcField,  newFieldValue))
//    }
//}