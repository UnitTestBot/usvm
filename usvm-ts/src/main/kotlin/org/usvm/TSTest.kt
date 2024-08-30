package org.usvm

import org.jacodb.ets.base.EtsStmt

class TSTest(
    val parameters: List<Any>,
    val resultValue: Any?,
    val trace: List<EtsStmt>? = null,
)

open class TSMethodCoverage

object NoCoverage : TSMethodCoverage()

sealed interface TSObject {
    sealed interface TSNumber : TSObject {
        data class Integer(val value: Int) : TSNumber

        data class Double(val value: kotlin.Double) : TSNumber

        val number: kotlin.Double
            get() = when (this) {
                is Integer -> value.toDouble()
                is Double -> value
            }

        val boolean: kotlin.Boolean
            get() = number == 1.0
    }

    data class String(val value: kotlin.String) : TSObject

    data class Boolean(val value: kotlin.Boolean) : TSObject {
        val number: Double
            get() = if (value) 1.0 else 0.0
    }


    data class Class(val name: String, val properties: Map<String, TSObject>) : TSObject

    data object AnyObject : TSObject

    data object UndefinedObject : TSObject

    data class Array(val values: List<TSObject>) : TSObject
}
