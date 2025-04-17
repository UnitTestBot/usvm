package org.usvm.api

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt

data class TsTest(
    val method: EtsMethod,
    val before: TsParametersState,
    val after: TsParametersState,
    val returnValue: TsValue,
    val trace: List<EtsStmt>? = null,
)

data class TsParametersState(
    val thisInstance: TsValue?,
    val parameters: List<TsValue>,
    val globals: Map<EtsClass, List<GlobalFieldValue>>,
)

data class GlobalFieldValue(val field: EtsField, val value: TsValue) // TODO is it right?????

open class TsMethodCoverage

object NoCoverage : TsMethodCoverage()

sealed interface TsValue {
    data object TsAny : TsValue
    data object TsUnknown : TsValue
    data object TsNull : TsValue
    data object TsUndefined : TsValue
    data object TsException : TsValue

    data class TsBoolean(val value: Boolean) : TsValue
    data class TsString(val value: String) : TsValue
    data class TsBigInt(val value: String) : TsValue

    sealed interface TsNumber : TsValue {
        data class TsInteger(val value: Int) : TsNumber

        data class TsDouble(val value: Double) : TsNumber

        val number: Double
            get() = when (this) {
                is TsInteger -> value.toDouble()
                is TsDouble -> value
            }
    }

    data class TsObject(val addr: Int) : TsValue

    data class TsClass(
        val name: String,
        val properties: Map<String, TsValue>,
    ) : TsValue

    data class TsArray<T : TsValue>(
        val values: List<T>,
    ) : TsValue
}
