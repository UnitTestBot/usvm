package org.usvm.fuzzer.mutation

import org.usvm.fuzzer.generator.DataGenerator
import org.usvm.fuzzer.seed.Seed
import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.testcase.api.UTestMethodCall
import org.usvm.instrumentation.testcase.api.UTestNullExpression
import org.usvm.instrumentation.util.toJcClassOrInterface
import org.usvm.instrumentation.util.toJcType

class CallRandomMethod : Mutation() {
    override fun mutate(seed: Seed, position: Int): Seed? {
        val pos = seed.positions[position]
        val type = pos.field.type
        val instance = pos.descriptor.instance
        val jcClasspath = pos.descriptor.type.classpath
        val jcClass = type.toJcClassOrInterface(jcClasspath) ?: return null
        val randomMethod = jcClass.declaredMethods.filter { !it.isStatic }.randomOrNull() ?: return null
        val dataGenerator = DataGenerator(jcClasspath)
        val initStmts = mutableListOf<UTestExpression>()
        val args = mutableListOf<UTestExpression>()
        randomMethod.parameters.map {
            val (inst, init) = dataGenerator.generateRandomParameterValue(it.type)
            args.add(inst ?: UTestNullExpression(it.type.toJcType(jcClasspath)!!))
            initStmts.addAll(init)
        }
        return seed.mutate(position, initStmts + UTestMethodCall(instance, randomMethod, args))
    }
}