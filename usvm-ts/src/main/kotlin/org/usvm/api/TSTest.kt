package org.usvm.api

import org.jacodb.ets.base.EtsStmt

data class TSTest(
    val parameters: List<TSObject>,
    val returnValue: TSObject,
    val trace: List<EtsStmt>? = null,
)

open class TSMethodCoverage

object NoCoverage : TSMethodCoverage()

sealed interface TSObject {
    sealed interface TSNumber : org.usvm.api.TSObject {
        data class Integer(val value: Int) : TSNumber

        data class Double(val value: kotlin.Double) : TSNumber

        val number: kotlin.Double
            get() = when (this) {
                is Integer -> value.toDouble()
                is Double -> value
            }

        val truthyValue: Boolean
            get() = number != 0.0 && !number.isNaN()
    }

    data class TSString(val value: String) : org.usvm.api.TSObject

    data class TSBoolean(val value: Boolean) : org.usvm.api.TSObject {
        val number: Double
            get() = if (value) 1.0 else 0.0
    }

    data class TSBigInt(val value: String) : org.usvm.api.TSObject

    data class TSClass(val name: String, val properties: Map<String, org.usvm.api.TSObject>) :
        org.usvm.api.TSObject

    data object TSAny : org.usvm.api.TSObject

    data object TSUndefinedObject : org.usvm.api.TSObject

    data class TSArray(val values: List<org.usvm.api.TSObject>) :
        org.usvm.api.TSObject

    data class TSObject(val addr: Int) : org.usvm.api.TSObject

    data object TSUnknown : org.usvm.api.TSObject

    data object TSNull : org.usvm.api.TSObject
}
