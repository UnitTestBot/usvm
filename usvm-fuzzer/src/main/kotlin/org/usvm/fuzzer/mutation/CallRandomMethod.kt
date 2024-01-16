package org.usvm.fuzzer.mutation

import io.leangen.geantyref.GenericTypeReflector
import org.jacodb.api.JcClassType
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.ext.autoboxIfNeeded
import org.jacodb.impl.cfg.util.isPrimitive
import org.usvm.fuzzer.generator.DataFactory
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.util.createJcTypeWrapper
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.util.toJavaField
import org.usvm.instrumentation.util.toJcType

class CallRandomMethod : Mutation() {
    override val mutationFun: DataFactory.(Seed) -> Pair<Seed?, MutationInfo>? = lambda@{ seed ->
        //TODO add field rating
        val randomFieldToMutate =
            seed.accessedFields
                ?.filter { it?.type?.isPrimitive == false }
                ?.randomOrNull() ?: return@lambda null
        val fieldType =
            randomFieldToMutate.type.toJcType(jcClasspath)?.autoboxIfNeeded() as? JcClassType ?: return@lambda null
        val (arg, randomAvailableFieldChain) = seed.getFieldsInTermsOfUTest(randomFieldToMutate).randomOrNull()
            ?: return@lambda null
        var curType = arg.type.actualJavaType
        println("FIELD CHAIN = ${randomAvailableFieldChain.map { it.field.name }}")
        randomAvailableFieldChain.forEach {
            val jcField = it.field
            val jField = jcField.toJavaField(userClassLoader)
            curType = GenericTypeReflector.getFieldType(jField, curType)
        }
        val fieldExpr = randomAvailableFieldChain.lastOrNull() ?: return@lambda null
        val resolvedFieldType = curType.createJcTypeWrapper(jcClasspath)
        val randomFunToInvoke =
            fieldType.declaredMethods
                .filter { it.name != "<init>" && it.name != "<clinit>" }
                .randomOrNull()?.method ?: return@lambda null
        println("CALLING ${randomFunToInvoke.name} to ${randomFieldToMutate.name} of type ${randomFieldToMutate.type}")
        val argsForMethodInvocation =
            dataFactory.generateValuesForMethodInvocation(resolvedFieldType, randomFunToInvoke)
        val invocation = UTestMethodCall(
            fieldExpr,
            randomFunToInvoke,
            argsForMethodInvocation.map { it.instance }
        )
        val newArg = with(arg) {
            Seed.ArgumentDescriptor(
                instance = instance,
                type = type,
                initialExprs = initialExprs + argsForMethodInvocation.flatMap { it.initStmts } + listOf(invocation)
            )
        }
        return@lambda seed.mutate(arg, newArg) to MutationInfo(arg, randomFieldToMutate)
    }
}