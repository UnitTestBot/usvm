package org.usvm.fuzzer.mutation

import io.leangen.geantyref.GenericTypeReflector
import org.jacodb.api.JcClassType
import org.jacodb.api.ext.autoboxIfNeeded
import org.usvm.fuzzer.generator.SeedFactory
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.createJcTypeWrapper
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.util.toJavaField
import org.usvm.instrumentation.util.toJavaMethod
import org.usvm.instrumentation.util.toJcClassOrInterface
import org.usvm.instrumentation.util.toJcType

class CallRandomMethod : Mutation() {
    override val mutationFun: SeedFactory.(Seed) -> Seed? = lambda@{ seed ->
        val randomFieldToMutate = seed.accessedFields?.randomOrNull() ?: return@lambda null
        val fieldType =
            randomFieldToMutate.type.toJcType(jcClasspath)?.autoboxIfNeeded() as? JcClassType ?: return@lambda null
        val (arg, randomAvailableFieldChain) = seed.getFieldsInTermsOfUTest(randomFieldToMutate).randomOrNull()
            ?: return@lambda null
        var curType = arg.type.actualJavaType
        randomAvailableFieldChain.forEach {
            val jcField = it.field
            val jField = jcField.toJavaField(userClassLoader)
            curType = GenericTypeReflector.getFieldType(jField, curType)
        }
        val fieldExpr = randomAvailableFieldChain.lastOrNull() ?: return@lambda null
        val resolvedFieldType = curType.createJcTypeWrapper(jcClasspath)
        val randomFunToInvoke = fieldType.declaredMethods.randomOrNull()?.method ?: return@lambda null
        val argsForMethodInvocation =
            seedFactory.generateArgsForMethodInvocation(resolvedFieldType, randomFunToInvoke)
        val invocation = UTestMethodCall(
            fieldExpr,
            randomFunToInvoke,
            argsForMethodInvocation.map { it.instance }
        )
        return@lambda seed.copy()
    }

//    override fun mutate(seed: Seed, position: Int): Seed? {
//        val pos = seed.positions[position]
//        val type = pos.field.type
//        val instance = pos.descriptor.instance
//        val jcClasspath = pos.descriptor.type.type.classpath
//        val jcClass = type.toJcClassOrInterface(jcClasspath) ?: return null
//        val randomMethod = jcClass.declaredMethods
//            .filter { it.isPublic }
//            .filter { !it.isStatic }.randomOrNull() ?: return null
//        val args =
//            seedFactory.generateArgsForMethodInvocation(jcClass.createJcTypeWrapper(seedFactory.userClassLoader), randomMethod)
//        val initStmts = args.flatMap { it.initStmts }
//        val inst = args.map { it.instance }
//        return seed.mutate(position, initStmts + UTestMethodCall(instance, randomMethod, inst))
//    }
}