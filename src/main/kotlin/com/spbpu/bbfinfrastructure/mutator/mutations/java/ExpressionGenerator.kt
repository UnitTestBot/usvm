package com.spbpu.bbfinfrastructure.mutator.mutations.java

import com.spbpu.bbfinfrastructure.util.*
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.random.Random

class ExpressionGenerator {

    private val MAX_TRIES = 3

    fun generateExpressionOfType(
        scope: List<JavaScopeCalculator.JavaScopeComponent>,
        type: String
    ):String? {
        repeat(MAX_TRIES) {
            gen(scope, type)?.let { return it }
        }
        return null
    }

    fun generateLiteral(type: String): String? =
        when (type) {
            "boolean" -> Random.nextBoolean().toString()
            "Boolean" -> Random.nextBoolean().toString()
            "short" -> Random.nextInt().toShort().toString()
            "Short" -> Random.nextInt().toShort().toString()
            "Byte" -> Random.nextInt().toByte().toString()
            "byte" -> Random.nextInt().toByte().toString()
            "int" -> Random.nextInt().toShort().toString()
            "Integer" -> Random.nextBoolean().toString()
            "long" -> Random.nextLong().toString()
            "Long" -> Random.nextLong().toString()
            "float" -> Random.nextDouble().toFloat().toString()
            "Float" -> Random.nextDouble().toFloat().toString()
            "double" -> Random.nextDouble().toString()
            "Double" -> Random.nextDouble().toString()
            "char" -> Random.nextInt().toChar().toString()
            "Character" -> Random.nextInt().toChar().toString()
            "String" -> Random.getRandomVariableName(5)
            else -> null
        }

    private fun gen(
        scope: List<JavaScopeCalculator.JavaScopeComponent>,
        type: String
    ): String? {
        val neededType = JavaTypeMappings.mappings[type] ?: type
        var randomScopeVarType: Class<*>? = null
        var randomScopeVarName: String? = null
        for (i in 0 until 3) {
            try {
                scope.randomOrNull()?.let { randomVar ->
                    randomScopeVarName = randomVar.name
                    randomScopeVarType = FuzzClassLoader().loadClass(randomVar.type)
                }
            } catch (e: Throwable) {
                randomScopeVarType = null
                randomScopeVarName = null
            }
            if (randomScopeVarType != null) break
        }
        if (randomScopeVarType == null) return null
        val randomSuitableField =
            randomScopeVarType!!.declaredFields
                .filter { Modifier.isPublic(it.modifiers) && it.hasGetter() }
                .filter { it.type.name == neededType }
                .randomOrNull()
        val randomSuitableMethod =
            randomScopeVarType!!.declaredMethods
                .filter { Modifier.isPublic(it.modifiers) }
                .filterNot { it.isDeprecated() }
                .filter { it.returnType.name == neededType }
                .filter { it.canBeInvokedWithScopeVariables(scope) }
                .randomOrNull()
        if (randomSuitableField != null && Random.getTrue(30)) {
            val invocation = randomSuitableField.constructFieldInvocation() ?: return null
            return "$randomScopeVarName.$invocation"
        }
        if (randomSuitableMethod != null) {
            return "$randomScopeVarName.${randomSuitableMethod.constructMethodInvocation(scope)}"
        }
        return null
    }

    private fun Method.canBeInvokedWithScopeVariables(scope: List<JavaScopeCalculator.JavaScopeComponent>): Boolean =
        parameters.isEmpty() ||
        parameterTypes.all { parameterType -> scope.any { it.type == parameterType.name } }

    private fun Field.hasGetter(): Boolean {
        val getterName = "get${name.capitalizeAsciiOnly()}"
        val getterMethod = declaringClass.methods.find {
            it.name == getterName && it.returnType == type && it.parameterCount == 0
        }
        return getterMethod != null
    }

    private fun Field.constructFieldInvocation(): String? {
        if (Modifier.isPublic(modifiers)) return name
        val getterName = "get${name.capitalizeAsciiOnly()}"
        return declaringClass.methods.find {
            it.name == getterName && it.returnType == type && it.parameterCount == 0
        }?.name
    }

    private fun Method.constructMethodInvocation(scope: List<JavaScopeCalculator.JavaScopeComponent>): String? {
        return parameterTypes
            .map { pt -> scope.filter { it.type == pt.name }.randomOrNull()?.name ?: return null }
            .joinToString(separator = ",", prefix = "$name(", postfix = ")")
    }
}