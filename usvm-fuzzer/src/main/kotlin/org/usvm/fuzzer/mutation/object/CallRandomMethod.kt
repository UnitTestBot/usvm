package org.usvm.fuzzer.mutation.`object`

import io.leangen.geantyref.GenericTypeReflector
import org.jacodb.api.JcClassType
import org.jacodb.api.ext.autoboxIfNeeded
import org.jacodb.impl.cfg.util.isPrimitive
import org.usvm.fuzzer.api.UTypedTestMethodCall
import org.usvm.fuzzer.generator.DataFactory
import org.usvm.fuzzer.mutation.Mutation
import org.usvm.fuzzer.mutation.MutationInfo
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.util.createJcTypeWrapper
import org.usvm.fuzzer.util.unroll
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.util.toJavaField
import org.usvm.instrumentation.util.toJavaMethod
import org.usvm.instrumentation.util.toJcType

class CallRandomMethod : Mutation() {
    override val mutationFun: DataFactory.(Seed) -> Pair<Seed?, MutationInfo>? = mutation@{ seed ->
        //TODO call method to arg random public field aka: arg.field.method(...)
        val argForMutation = seed.getArgForMutation()
        val randomMethodToCall =
            argForMutation.type.declaredMethods
                .filter { it.isPublic || it.isPackagePrivate }
                .filter { !it.method.isConstructor && !it.method.isClassInitializer && !it.isStatic }
                .randomOrNull() ?: return@mutation null
        val args =
            dataFactory.generateValuesForMethodInvocation(argForMutation.type, randomMethodToCall.method).map { it.second }
        val call = UTypedTestMethodCall(argForMutation.instance, randomMethodToCall.method, args.map { it.instance })
        val newArg = Seed.ArgumentDescriptor(
            argForMutation.instance,
            argForMutation.type,
            argForMutation.initialExprs + args.flatMap { it.initStmts } + call
        )
        return@mutation seed.mutate(argForMutation, newArg) to MutationInfo(argForMutation, null)

//        UTestMethodCall()
//        //TODO add field rating
//        val randomFieldToMutate =
//            seed.accessedFields
//                ?.filter { it?.type?.isPrimitive == false }
//                ?.randomOrNull() ?: return@lambda null
//        val fieldType =
//            randomFieldToMutate.type.toJcType(jcClasspath)?.autoboxIfNeeded() as? JcClassType ?: return@lambda null
//        val (arg, targetFieldGetExpr) = seed.getFieldsInTermsOfUTest(randomFieldToMutate).randomOrNull()
//            ?: return@lambda null
//        var curType = arg.type.actualJavaType
//        val randomAvailableFieldChain = targetFieldGetExpr.unroll().reversed()
//        println("FIELD CHAIN = ${randomAvailableFieldChain.map { it.field.name }}")
//        randomAvailableFieldChain.forEach {
//            val jcField = it.field
//            val jField = jcField.toJavaField(userClassLoader)
//            curType = GenericTypeReflector.getFieldType(jField, curType)
//        }
//        val fieldExpr = randomAvailableFieldChain.lastOrNull() ?: return@lambda null
//        val resolvedFieldType = curType.createJcTypeWrapper(jcClasspath)
//        val randomFunToInvoke =
//            fieldType.declaredMethods
//                .filter { it.name != "<init>" && it.name != "<clinit>" }
//                .randomOrNull()?.method ?: return@lambda null
//        println("CALLING ${randomFunToInvoke.name} to ${randomFieldToMutate.name} of type ${randomFieldToMutate.type}")
//        val argsForMethodInvocation =
//            dataFactory.generateValuesForMethodInvocation(resolvedFieldType, randomFunToInvoke).map { it.second }
//        val invocation = UTestMethodCall(
//            fieldExpr,
//            randomFunToInvoke,
//            argsForMethodInvocation.map { it.instance }
//        )
//        val newArg = with(arg) {
//            Seed.ArgumentDescriptor(
//                instance = instance,
//                type = type,
//                initialExprs = initialExprs + argsForMethodInvocation.flatMap { it.initStmts } + listOf(invocation)
//            )
//        }
//        return@lambda seed.mutate(arg, newArg) to MutationInfo(arg, randomFieldToMutate)
        return@mutation null
    }
}