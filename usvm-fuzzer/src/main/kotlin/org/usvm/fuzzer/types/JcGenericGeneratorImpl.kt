package org.usvm.fuzzer.types

import io.leangen.geantyref.GenericTypeReflector
import io.leangen.geantyref.TypeArgumentNotInBoundException
import io.leangen.geantyref.TypeFactory
import org.jacodb.api.*
import org.jacodb.api.ext.objectClass
import org.jacodb.api.ext.objectType
import org.jacodb.api.ext.toType
import org.usvm.fuzzer.util.createJcTypeWrapper
import org.usvm.fuzzer.util.simpleTypeName
import org.usvm.instrumentation.util.toJavaClass
import org.usvm.instrumentation.util.toJcClassOrInterface
import java.lang.IllegalArgumentException
import java.lang.reflect.*


class JcGenericGeneratorImpl(
    private val jcClasspath: JcClasspath,
    private val userClassLoader: ClassLoader
) : JcGenericGenerator {

    override fun resolveGenericParametersForType(
        type: JcTypeWrapper
    ): JcTypeWrapper = replaceGenericParametersForJType(type.actualJavaType, mutableMapOf())

    private fun replaceGenericParametersForJType(jType: Type, replacedGenerics: MutableMap<Type, Type>): JcTypeWrapper {
        if (jType is Class<*>) {
            val substitutions = jType.typeParameters.map { jGeneric ->
                replacedGenerics[jGeneric]?.let { return@map it }
                val bounds =
                    jGeneric.bounds.map { jcClasspath.findClassOrNull(it.simpleTypeName()) ?: jcClasspath.objectClass }
                        .ifEmpty { listOf(jType.toJcClassOrInterface(jcClasspath) ?: jcClasspath.objectClass) }
                val jBounds = jGeneric.bounds
                val replacement =
                    JcClassTable.getRandomTypeSuitableForBounds(listOf(), bounds)?.toType() ?: jcClasspath.objectType
                val jReplacement = replacement.toJavaClass(userClassLoader)
                if (jReplacement.typeParameters.isEmpty()) {
                    return@map jReplacement.also { replacedGenerics[jGeneric] = jReplacement }
                }
                val jFirstBound = jBounds.first()
                val jReplacedGeneric =
                    try {
                        GenericTypeReflector.getExactSubType(jFirstBound, jReplacement)
                    } catch (e: Throwable) {
                        println()
                        throw e
                    }
                if (jReplacedGeneric == null) {
                    println()
                    GenericTypeReflector.getExactSubType(jFirstBound, jReplacement)
                }
                replaceGenericParametersForJType(jReplacedGeneric, replacedGenerics).actualJavaType
                    .also { replacedGenerics[jGeneric] = it }
            }
            return try {
                TypeFactory.parameterizedClass(jType, *substitutions.toTypedArray()).createJcTypeWrapper(jcClasspath)
            } catch (e: TypeArgumentNotInBoundException) {
                println("Wrong replacement for generics chosen. Return object instead")
                java.lang.Object::class.java.createJcTypeWrapper(jcClasspath)
            } catch (e: Throwable) {
                println()
                throw e
            }
        } else if (jType is ParameterizedType) {
            val substitutions = jType.actualTypeArguments.map { jGeneric ->
                replaceTypeVariableOrWildCard(jGeneric, replacedGenerics)
            }
            val jTypeAsClass = jType.rawType as? Class<*> ?: return Object::class.java.createJcTypeWrapper(jcClasspath)
            return try {
                TypeFactory.parameterizedClass(jTypeAsClass, *substitutions.toTypedArray())
                    .createJcTypeWrapper(jcClasspath)
            } catch (e: TypeArgumentNotInBoundException) {
                println("Wrong replacement for generics chosen. Return object instead")
                java.lang.Object::class.java.createJcTypeWrapper(jcClasspath)
            }
        } else if (jType is TypeVariable<*>) {
            println("REPLACING GENERIC")
            return replaceTypeVariableOrWildCard(jType, replacedGenerics).createJcTypeWrapper(jcClasspath)
        } else if (jType is GenericArrayType) {
            val replacementForComponent = replaceGenericParametersForJType(jType.genericComponentType, replacedGenerics)
            return TypeFactory.arrayOf(replacementForComponent.actualJavaType).createJcTypeWrapper(jcClasspath)
        }
        throw IllegalArgumentException("Unexpected argument of type ${jType::class.java}")
    }

    private fun replaceTypeVariableOrWildCard(jGeneric: Type, replacedGenerics: MutableMap<Type, Type>): Type {
        if (jGeneric !is TypeVariable<*> && jGeneric !is WildcardType) return jGeneric
        replacedGenerics[jGeneric]?.let { return it }
        val upperBounds =
            when (jGeneric) {
                is TypeVariable<*> -> jGeneric.bounds
                is WildcardType -> jGeneric.upperBounds
                else -> arrayOf(jGeneric)
            }
        val lowerBounds =
            when (jGeneric) {
                is WildcardType -> jGeneric.lowerBounds
                else -> arrayOf()
            }
        //TODO lower bounds!!
        val jcUpperBounds =
            upperBounds.map { jcClasspath.findClassOrNull(it.simpleTypeName()) ?: jcClasspath.objectClass }
        val jcLowerBounds =
            lowerBounds.map { jcClasspath.findClassOrNull(it.simpleTypeName()) ?: jcClasspath.objectClass }
        val replacement = JcClassTable.getRandomTypeSuitableForBounds(jcLowerBounds, jcUpperBounds)?.toType()
            ?: jcClasspath.objectType
        val jReplacement = replacement.toJavaClass(userClassLoader)
        if (jReplacement.typeParameters.isEmpty()) {
            return jReplacement.also { replacedGenerics[jGeneric] = jReplacement }
        }
        val jFirstBound =
            if (upperBounds.size == 1 && upperBounds.first() == jcClasspath.objectClass && lowerBounds.isNotEmpty()) {
                lowerBounds.first()
            } else {
                upperBounds.first()
            }
        val jReplacedGeneric = GenericTypeReflector.getExactSubType(jFirstBound, jReplacement) ?: jReplacement
        return replaceGenericParametersForJType(jReplacedGeneric, replacedGenerics).actualJavaType
            .also { replacedGenerics[jGeneric] = it }
    }

    override fun resolveGenericParametersForMethod(
        resolvedClassType: JcTypeWrapper,
        method: Method
    ): Pair<JcTypeWrapper, List<JcTypeWrapper>> {
        val (resolvedMethodRetType, methodSubstitutions) = resolveMethodRetType(resolvedClassType, method)
        val concreteParameterTypes = resolveGenericParameterForExecutable(resolvedClassType, method, methodSubstitutions)
        return resolvedMethodRetType to concreteParameterTypes
    }

    override fun resolveGenericParametersForConstructor(
        resolvedClassType: JcTypeWrapper,
        constructor: Constructor<*>
    ): List<JcTypeWrapper> = resolveGenericParameterForExecutable(resolvedClassType, constructor, mutableMapOf())

    private fun resolveMethodRetType(resolvedClassType: JcTypeWrapper, method: Method): Pair<JcTypeWrapper, MutableMap<Type, Type>> {
        val methodTypeParameters = method.typeParameters
        val methodSubstitutions =
            methodTypeParameters
                .associate { it as Type to replaceGenericParametersForJType(it, mutableMapOf()).actualJavaType }
                .toMutableMap()
        val methodRetType = GenericTypeReflector.getReturnType(method, resolvedClassType.actualJavaType)
        val resolvedMethodRetType =
            if (methodRetType is ParameterizedType || methodRetType is TypeVariable<*> || methodRetType is GenericArrayType) {
                replaceGenericParametersForJType(methodRetType, methodSubstitutions)
            } else {
                methodRetType.createJcTypeWrapper(jcClasspath)
            }
        return resolvedMethodRetType to methodSubstitutions
    }

    override fun resolveMethodReturnType(resolvedClassType: JcTypeWrapper, method: Method): JcTypeWrapper =
        resolveMethodRetType(resolvedClassType, method).first

    private fun resolveGenericParameterForExecutable(
        resolvedClassType: JcTypeWrapper,
        executable: Executable,
        methodSubstitutions: MutableMap<Type, Type>
    ): List<JcTypeWrapper> {
        val actualParametersTypes = try {
            GenericTypeReflector.getParameterTypes(executable, resolvedClassType.actualJavaType)
        } catch (e: Throwable) {
            executable.genericParameterTypes
        }
        return actualParametersTypes.map {
                if (it is Class<*>) {
                    it.createJcTypeWrapper(jcClasspath)
                }
                else {
                    replaceGenericParametersForJType(it, methodSubstitutions)
                }
            }
    }

    override fun getFieldType(resolvedClassType: JcTypeWrapper, field: Field): JcTypeWrapper {
        val actualType = GenericTypeReflector.getFieldType(field, resolvedClassType.actualJavaType)
        return replaceGenericParametersForJType(actualType, mutableMapOf())
    }

    fun replaceGenericsForSubtypeOf(replacement: JcTypeWrapper, subtype: JcTypeWrapper): JcTypeWrapper {
        val jReplacement = replacement.actualJavaType as Class<*>
        val jSub = subtype.actualJavaType
        return try {
            val actualType =  GenericTypeReflector.getExactSubType(jSub, jReplacement)
            val concreteType = replaceGenericParametersForJType(actualType, mutableMapOf()).actualJavaType
            JcTypeWrapper(replacement.type, concreteType)
        } catch (e: Throwable) {
            //In case if bounds of subtype and the replacement didn't match
            resolveGenericParametersForType(replacement)
        }
    }

}