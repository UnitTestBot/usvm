package com.spbpu.bbfinfrastructure.mutator.mutations.java.util

import kotlin.random.Random

class GoExpressionGenerator {
    fun genVariable(scope: List<ScopeComponent>, varType: String): String? {
        return when (varType) {
            "bool" -> Random.nextBoolean().toString()
            "int" -> Random.nextInt().toString()
            "float" -> Random.nextDouble().toString() + "F"
            "double" -> Random.nextDouble().toString()
            "string" -> "\"${getRandomString(5)}\""
            else -> null
        }
    }

    fun genConstant(): String {
        return when (Random.nextInt(6)) {
            0 -> Random.nextBoolean().toString()
            1 -> Random.nextInt().toString()
            2 -> Random.nextDouble().toString()
            else -> "\"${getRandomString(5)}\""
        }
    }

    private fun getRandomString(length: Int): String {
        val letters = ('a'..'z') + ('A'..'Z')
        return (1..length)
            .map { letters.random() }
            .joinToString("")
    }
}