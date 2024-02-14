package org.usvm.fuzzer.mutation.primitives

import org.jacodb.api.JcField
import org.usvm.fuzzer.api.*
import org.usvm.fuzzer.generator.DataFactory
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.mutation.Mutation
import org.usvm.fuzzer.mutation.MutationInfo
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.util.createJcTypeWrapper
import org.usvm.fuzzer.util.findSetter
import org.usvm.fuzzer.util.unroll
import org.usvm.instrumentation.testcase.api.UTestGetFieldExpression
import org.usvm.instrumentation.testcase.api.UTestMethodCall
import org.usvm.instrumentation.testcase.api.UTestSetFieldStatement
import org.usvm.instrumentation.testcase.api.UTestSetStaticFieldStatement
import org.usvm.instrumentation.util.toJcType

class FieldFiller : Mutation() {
    override val mutationFun: DataFactory.(Seed) -> Pair<Seed?, MutationInfo>? = mutation@{ seed ->
        val randomFieldForMutation =
            if (random.getTrueWithProb(75)) {
                val accessedFields = seed.accessedFields
                if (!accessedFields.isNullOrEmpty()) {
                    Seed.fieldInfo.getBestField { jcField: JcField -> jcField in accessedFields }!!
                } else {
                    Seed.fieldInfo.getBestField().jcField
                }
            } else {
                Seed.fieldInfo.getBestField().jcField
            }
        if (randomFieldForMutation.isStatic) {
            return@mutation mutateStaticField(this, randomFieldForMutation, seed)
        }
        val fieldDescriptor =
            seed.getFieldInTermsOfUTest(randomFieldForMutation, dataFactory.userClassLoader, dataFactory.jcClasspath)
                ?: return@mutation null
        val newFieldValue = dataFactory.generateValueOfType(fieldDescriptor.actualType)
        val fieldParentUTestInstance =
            when (val uTestFieldInstance = fieldDescriptor.instance) {
                is UTypedTestGetFieldExpression -> uTestFieldInstance.instance
                is UTypedTestMethodCall -> uTestFieldInstance.instance
                else -> return@mutation null
            }
        val newUTestInst =
            if (randomFieldForMutation.isPublic || randomFieldForMutation.isPackagePrivate) {
                UTypedTestSetFieldStatement(fieldParentUTestInstance, randomFieldForMutation, newFieldValue.instance)
            } else {
                val setter = randomFieldForMutation.findSetter() ?: return@mutation null
                UTypedTestMethodCall(fieldParentUTestInstance, setter, listOf(newFieldValue.instance))
            }

        val previousModificationUTest =
            fieldDescriptor.parentArgument.initialExprs
                .find { uTest ->
                    val instance =
                        when (uTest) {
                            is UTypedTestSetFieldStatement -> uTest.instance
                            is UTypedTestMethodCall -> uTest.instance
                            else -> return@find false
                        }
                    if (instance == fieldParentUTestInstance) {
                        true
                    } else if (instance is UTypedTestGetFieldExpression && fieldParentUTestInstance is UTypedTestGetFieldExpression) {
                        instance.field == fieldParentUTestInstance.field && instance.unroll().size == fieldParentUTestInstance.unroll().size
                    } else if (instance is UTypedTestMethodCall && fieldParentUTestInstance is UTypedTestMethodCall) {
                        instance.method == fieldParentUTestInstance.method && instance.unroll().size == fieldParentUTestInstance.unroll().size
                    } else {
                        false
                    }
                }
        val newArg =
            if (previousModificationUTest != null) {
                seed.replace(
                    fieldDescriptor.parentArgument,
                    previousModificationUTest,
                    newUTestInst,
                    newFieldValue.initStmts
                )
            } else {
                Seed.ArgumentDescriptor(
                    fieldDescriptor.parentArgument.instance,
                    fieldDescriptor.parentArgument.type,
                    fieldDescriptor.parentArgument.initialExprs + newFieldValue.initStmts + newUTestInst
                )
            }
        return@mutation seed.mutate(
            fieldDescriptor.parentArgument,
            newArg
        ) to MutationInfo(fieldDescriptor.parentArgument, randomFieldForMutation)
    }

    private fun mutateStaticField(dataFactory: DataFactory, jcField: JcField, seed: Seed): Pair<Seed?, MutationInfo>? =
        with(dataFactory) {
            val fieldType = jcField.type.toJcType(jcClasspath)?.createJcTypeWrapper(userClassLoader) ?: return@with null
            val newFieldValue = dataFactory.generateValueOfType(fieldType)
            val arg = seed.args.first()
            val previousUTestSameFieldStatement =
                arg.initialExprs.find { it is UTypedTestSetStaticFieldStatement && it.field == jcField }
            val newUTestSetStatement = UTypedTestSetStaticFieldStatement(jcField, newFieldValue.instance)
            val newArg =
                if (previousUTestSameFieldStatement != null) {
                    seed.replace(
                        arg,
                        previousUTestSameFieldStatement,
                        newUTestSetStatement,
                        newFieldValue.initStmts
                    )
                } else {
                    Seed.ArgumentDescriptor(
                        arg.instance,
                        arg.type,
                        arg.initialExprs + newFieldValue.initStmts + newUTestSetStatement
                    )
                }
            return@with seed.mutate(arg, newArg) to MutationInfo(null, jcField)
        }
}