@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.usvm.fuzzer.types

import io.leangen.geantyref.GenericTypeReflector
import org.jacodb.api.*
import org.jacodb.api.ext.constructors
import org.jacodb.api.ext.objectType
import org.usvm.fuzzer.util.createJcTypeWrapper
import org.usvm.fuzzer.util.simpleTypeName
import org.usvm.instrumentation.util.toJavaMethod
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class JcTypeWrapper(
    val type: JcType,
    val actualJavaType: Type
) {
    private val cp = type.classpath

    val constructors: List<JcTypedMethod> by lazy {
        if (type is JcClassType) {
            type.constructors
        } else {
            listOf()
        }
    }

    val declaredMethods: List<JcTypedMethod> by lazy {
        if (type is JcClassType) {
            type.declaredMethods
        } else {
            listOf()
        }
    }

    val declaredFields: List<JcTypedField> by lazy {
        if (type is JcClassType) {
            type.declaredFields
        } else {
            listOf()
        }
    }

    val typeArguments: List<JcTypeWrapper> by lazy {
        if (type is JcClassType && actualJavaType is ParameterizedType) {
            type.typeArguments
                .zip(actualJavaType.actualTypeArguments)
                .map {
                    val jcType = cp.findTypeOrNull(it.second.simpleTypeName())
                        ?: error("cant find type ${it.second.typeName}")
                    JcTypeWrapper(jcType, it.second)
                }
        } else {
            listOf()
        }
    }

    fun getArrayElementType(): JcTypeWrapper? {
        if (type !is JcArrayType) return null
        val jElementType =
            when (actualJavaType) {
                is GenericArrayType -> actualJavaType.genericComponentType
                is Class<*> -> actualJavaType.componentType
                else -> error("Not expected array component type")
            }
        val jcElementType = type.elementType
        return JcTypeWrapper(jcElementType, jElementType)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcTypeWrapper

        if (type != other.type) return false
        if (actualJavaType != other.actualJavaType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + actualJavaType.hashCode()
        return result
    }

}