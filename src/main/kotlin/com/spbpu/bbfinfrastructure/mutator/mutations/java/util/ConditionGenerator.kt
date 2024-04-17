package com.spbpu.bbfinfrastructure.mutator.mutations.java.util

import com.spbpu.bbfinfrastructure.util.getTrue
import kotlin.random.Random

class ConditionGenerator(
    private val scope: List<JavaScopeCalculator.JavaScopeComponent>
) {

    private val logicalOperators = listOf("&&", "||")
    private val binaryOperators = listOf("==", "!=", "<", "<=", ">", ">=")
    private val expressionGenerator = ExpressionGenerator()
    private fun getRandomType(): String =
        if (Random.getTrue(50)) {
            listOf("byte", "short", "int", "long", "float", "double", "char", "boolean", "String").random()
        } else {
            scope.random().type
        }


    fun generate(): String? {
        val binaryExpr = genBinaryExpr() ?: return null
        return if (Random.getTrue(50)) {
            val rightExpr = genBinaryExpr() ?: return binaryExpr
            "$binaryExpr ${logicalOperators.random()} $rightExpr"
        } else {
            binaryExpr
        }
    }

    private fun genBinaryExpr(): String? {
        repeat(5) {
            generateBinaryExpression()?.let { return it }
        }
        return null
    }

    private fun getValueOfTypeFromScopeOrGenerateNew(type: String): String? =
        if (Random.nextBoolean()) {
            scope.filter { it.type == type }.randomOrNull()?.name ?: expressionGenerator.generateExpressionOfType(
                scope,
                type
            )
        } else {
            expressionGenerator.generateExpressionOfType(scope, type)
        }

    private fun generateBinaryExpression(): String? {
        val leftExprType = getRandomType()
        val leftExpr = getValueOfTypeFromScopeOrGenerateNew(leftExprType) ?: return null
        val randomOperator =
            if (leftExprType in listOf("byte", "short", "int", "long", "float", "double", "char")) {
                binaryOperators.random()
            } else {
                listOf("==", "!=").random()
            }
        binaryOperators.random()
        var rightExpr: String? = null
        repeat(5) {
            val generatedExpr =
                if (Random.getTrue(20)) {
                    expressionGenerator.generateLiteral(leftExprType) ?: getValueOfTypeFromScopeOrGenerateNew(leftExprType)
                } else {
                    getValueOfTypeFromScopeOrGenerateNew(leftExprType)
                }
            if (generatedExpr != leftExpr) {
                rightExpr = generatedExpr
                return@repeat
            }
        }
        rightExpr ?: return null
        return when (randomOperator) {
            "==" -> if (Random.getTrue(75)) "$leftExpr.equals($rightExpr)" else "$leftExpr $randomOperator $rightExpr"
            "!=" -> if (Random.getTrue(75)) "!$leftExpr.equals($rightExpr)" else "$leftExpr $randomOperator $rightExpr"
            else -> "$leftExpr $randomOperator $rightExpr"
        }
    }


}