package org.usvm.fuzzer.mutation.`object`

import org.usvm.fuzzer.api.*
import org.usvm.fuzzer.generator.DataFactory
import org.usvm.fuzzer.mutation.Mutation
import org.usvm.fuzzer.mutation.MutationInfo
import org.usvm.fuzzer.seed.Seed
import org.usvm.instrumentation.testcase.api.*

class RandomValueRegenerator : Mutation() {
    override val mutationFun: DataFactory.(Seed) -> Pair<Seed?, MutationInfo>? = mutFun@{ seed ->
        //TODO replace to seed.args.random()
        val argForMutation = seed.args.filter { it.initialExprs.isNotEmpty() }.randomOrNull() ?: return@mutFun null
        val argInitialExpr = argForMutation.initialExprs
        val randomUTest = argInitialExpr.randomOrNull() ?: return@mutFun null
        val (mutatedInst, newUTestInst, initExpr) = mutateInst(randomUTest) ?: return@mutFun null
        val newArg = seed.replace(argForMutation, mutatedInst, newUTestInst, initExpr)
        return@mutFun seed.mutate(argForMutation, newArg) to MutationInfo(argForMutation, null)
    }

    private fun mutateInst(uTestInst: UTypedTestInst): MutationCallback? {
        return when (uTestInst) {
            is UTypedTestSetFieldStatement -> mutateUTestSetFieldStatement(uTestInst)
            is UTypedTestArraySetStatement -> mutateUTestArraySetStatement(uTestInst)
            is UTypedTestCreateArrayExpression -> mutateUTestCreateArrayExpression(uTestInst)
            is UTypedTestMethodCall -> mutateUTestMethodCall(uTestInst)
            else -> null
        }
    }

    private fun mutateUTestMethodCall(
        uTestMethodCall: UTypedTestMethodCall
    ): MutationCallback? {
        val randomArg = uTestMethodCall.args.randomOrNull() ?: return null
        val randomArgType = randomArg.type ?: return null
        val newArgValue = dataFactory.generateValueOfType(randomArgType)
        return MutationCallback(randomArg, newArgValue.instance, newArgValue.initStmts)
    }


    private fun mutateUTestSetFieldStatement(
        uTestSetFieldStatement: UTypedTestSetFieldStatement,
    ): MutationCallback? {
        val fieldType = uTestSetFieldStatement.value.type ?: return null
        val newFieldValue = dataFactory.generateValueOfType(fieldType)
        return MutationCallback(uTestSetFieldStatement, newFieldValue.instance, newFieldValue.initStmts)
    }

    private fun mutateUTestArraySetStatement(
        uTestArraySetStatement: UTypedTestArraySetStatement,
    ): MutationCallback? {
        val expr = uTestArraySetStatement.setValueExpression
        val exprType = expr.type ?: return null
        val newExpr = dataFactory.generateValueOfType(exprType)
        val newStmt =
            UTypedTestArraySetStatement(
                uTestArraySetStatement.arrayInstance,
                uTestArraySetStatement.index,
                newExpr.instance
            )
        return MutationCallback(uTestArraySetStatement, newStmt, newExpr.initStmts)
    }

    private fun mutateUTestCreateArrayExpression(
        uTestCreateArrayExpression: UTypedTestCreateArrayExpression
    ): MutationCallback {
        val arrayElementType = uTestCreateArrayExpression.elementType
        val newArray = dataFactory.generateValueOfType(arrayElementType)
        return MutationCallback(uTestCreateArrayExpression, newArray.instance, newArray.initStmts)
    }

    private data class MutationCallback(
        val mutatedInst: UTypedTestInst,
        val newInst: UTypedTestInst,
        val newInitialInstructions: List<UTypedTestInst>
    )
}