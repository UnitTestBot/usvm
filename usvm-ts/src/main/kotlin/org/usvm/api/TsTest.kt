package org.usvm.api

import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsMethod

data class TsTest(
    val method: EtsMethod,
    val before: TsParametersState,
    val after: TsParametersState,
    val returnValue: TsObject,
    val trace: List<EtsStmt>? = null,
)

data class TsParametersState(
    val thisInstance: TsObject?,
    val parameters: List<TsObject>,
    val globals: Map<EtsClass, List<GlobalFieldValue>>
)

data class GlobalFieldValue(val field: EtsField, val value: TsObject) // TODO is it right?????

open class TsMethodCoverage

object NoCoverage : TsMethodCoverage()

sealed interface TsObject {
    sealed interface TsNumber : org.usvm.api.TsObject {
        data class Integer(val value: Int) : TsNumber

        data class Double(val value: kotlin.Double) : TsNumber

        val number: kotlin.Double
            get() = when (this) {
                is Integer -> value.toDouble()
                is Double -> value
            }

        val truthyValue: Boolean
            get() = number != 0.0 && !number.isNaN()
    }

    data class TsString(val value: String) : org.usvm.api.TsObject

    data class TsBoolean(val value: Boolean) : org.usvm.api.TsObject {
        val number: Double
            get() = if (value) 1.0 else 0.0
    }

    data class TsBigInt(val value: String) : org.usvm.api.TsObject

    data class TsClass(val name: String, val properties: Map<String, org.usvm.api.TsObject>) :
        org.usvm.api.TsObject

    data object TsAny : org.usvm.api.TsObject

    data object TsUndefinedObject : org.usvm.api.TsObject

    data class TsArray(val values: List<org.usvm.api.TsObject>) :
        org.usvm.api.TsObject

    data class TsObject(val addr: Int) : org.usvm.api.TsObject

    data object TsUnknown : org.usvm.api.TsObject

    data object TsNull : org.usvm.api.TsObject

    data object TsException : org.usvm.api.TsObject
}
