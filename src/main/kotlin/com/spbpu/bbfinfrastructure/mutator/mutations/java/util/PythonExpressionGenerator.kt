package com.spbpu.bbfinfrastructure.mutator.mutations.java.util

import com.spbpu.bbfinfrastructure.util.getRandomVariableName
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeFirstWord
import kotlin.random.Random

class PythonExpressionGenerator: ExpressionGenerator() {
    public override fun gen(scope: List<ScopeComponent>, type: String): String? =
        when (type) {
            "bool" -> Random.nextBoolean().toString().capitalizeAsciiOnly()
            "int" -> Random.nextInt().toShort().toString()
            "float" -> Random.nextDouble().toFloat().toString() + "F"
            "double" -> Random.nextDouble().toString()
            "str" -> "\"${Random.getRandomVariableName(5)}\""
            else -> null
        }


    fun genConstant() : String? =
        when (Random.nextInt(6)) {
            0 -> Random.nextBoolean().toString().capitalizeAsciiOnly()
            1 -> Random.nextInt().toString()
            2 -> Random.nextDouble().toString()
            else -> "\"${Random.getRandomVariableName(5)}\""
        }


}