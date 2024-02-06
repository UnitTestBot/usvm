package org.usvm.fuzzer.mutation.primitives

import org.jacodb.api.JcField
import org.usvm.fuzzer.generator.DataFactory
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.mutation.Mutation
import org.usvm.fuzzer.mutation.MutationInfo
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.util.createJcTypeWrapper
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
                is UTestGetFieldExpression -> uTestFieldInstance.instance
                is UTestMethodCall -> uTestFieldInstance.instance
                else -> return@mutation null
            }
        val newUTestInst =
            UTestSetFieldStatement(fieldParentUTestInstance, randomFieldForMutation, newFieldValue.instance)

        val previousUTestSetFieldStatement =
            fieldDescriptor.parentArgument.initialExprs.filterIsInstance<UTestSetFieldStatement>()
                .find { uTestSetFieldStatement ->
                    val instance = uTestSetFieldStatement.instance
                    if (instance == fieldParentUTestInstance) {
                        true
                    } else if (instance is UTestGetFieldExpression && fieldParentUTestInstance is UTestGetFieldExpression) {
                        instance.field == fieldParentUTestInstance.field && instance.unroll().size == fieldParentUTestInstance.unroll().size
                    } else if (instance is UTestMethodCall && fieldParentUTestInstance is UTestMethodCall) {
                        instance.method == fieldParentUTestInstance.method && instance.unroll().size == fieldParentUTestInstance.unroll().size
                    } else {
                        false
                    }
                }
        val newArg =
            if (previousUTestSetFieldStatement != null) {
                seed.replace(
                    fieldDescriptor.parentArgument,
                    previousUTestSetFieldStatement,
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
                arg.initialExprs.find { it is UTestSetStaticFieldStatement && it.field == jcField }
            val newUTestSetStatement = UTestSetStaticFieldStatement(jcField, newFieldValue.instance)
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