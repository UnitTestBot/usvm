package org.usvm.fuzzer.mutation.`object`

import org.usvm.fuzzer.generator.DataFactory
import org.usvm.fuzzer.mutation.Mutation
import org.usvm.fuzzer.mutation.MutationInfo
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.util.createJcTypeWrapper
import org.usvm.instrumentation.testcase.api.*

class RandomValueRegenerator : Mutation() {
    override val mutationFun: DataFactory.(Seed) -> Pair<Seed?, MutationInfo>? = mutFun@{ seed ->
        //TODO replace to seed.args.random()
        val argForMutation = seed.args.randomOrNull() ?: return@mutFun null
        val argInitialExpr = argForMutation.initialExprs
        val randomUTest = argInitialExpr.randomOrNull() ?: return@mutFun null
        val (_, newUTestInst, initExpr) = mutateInst(randomUTest) ?: return@mutFun null
        val newArg = seed.replace(argForMutation, randomUTest, newUTestInst, initExpr)
        return@mutFun seed.mutate(argForMutation, newArg) to MutationInfo(argForMutation, null)
    }

    private fun mutateInst(uTestInst: UTestInst): MutationCallback? {
        return when (uTestInst) {
            is UTestSetFieldStatement -> mutateUTestSetFieldStatement(uTestInst)
            is UTestArraySetStatement -> mutateUTestArraySetStatement(uTestInst)
            is UTestCreateArrayExpression -> mutateUTestCreateArrayExpression(uTestInst)
            else -> null
        }
    }

    private fun mutateUTestSetFieldStatement(
        uTestSetFieldStatement: UTestSetFieldStatement,
    ): MutationCallback? {
        val fieldType =
            uTestSetFieldStatement.value.type?.createJcTypeWrapper(dataFactory.userClassLoader) ?: return null
        val newFieldValue = dataFactory.generateValueOfType(fieldType)
        return MutationCallback(uTestSetFieldStatement.value, newFieldValue.instance, newFieldValue.initStmts)
    }

    private fun mutateUTestArraySetStatement(
        uTestArraySetStatement: UTestArraySetStatement,
    ): MutationCallback? {
        val expr = uTestArraySetStatement.setValueExpression
        val exprType = expr.type?.createJcTypeWrapper(dataFactory.userClassLoader) ?: return null
        val newExpr = dataFactory.generateValueOfType(exprType)
        val newStmt =
            UTestArraySetStatement(uTestArraySetStatement.arrayInstance, uTestArraySetStatement.index, newExpr.instance)
        return MutationCallback(expr, newStmt, newExpr.initStmts)
    }

    private fun mutateUTestCreateArrayExpression(
        uTestCreateArrayExpression: UTestCreateArrayExpression
    ): MutationCallback {
        val arrayElementType = uTestCreateArrayExpression.elementType.createJcTypeWrapper(dataFactory.userClassLoader)
        val newArray = dataFactory.generateValueOfType(arrayElementType)
        return MutationCallback(uTestCreateArrayExpression, newArray.instance, newArray.initStmts)
    }

    private data class MutationCallback(
        val mutatedExpression: UTestExpression,
        val newInst: UTestInst,
        val newInitialInstructions: List<UTestInst>
    )
}