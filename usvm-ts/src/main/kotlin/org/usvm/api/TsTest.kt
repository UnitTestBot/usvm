package org.usvm.api

import org.usvm.model.TsClass
import org.usvm.model.TsField
import org.usvm.model.TsMethod
import org.usvm.model.TsStmt

data class TsTest(
    val method: TsMethod,
    val before: TsParametersState,
    val after: TsParametersState,
    val returnValue: TsTestValue,
    val trace: List<TsStmt>? = null,
)

data class TsParametersState(
    val thisInstance: TsTestValue?,
    val parameters: List<TsTestValue>,
    val globals: Map<TsClass, List<GlobalFieldValue>>,
)

data class GlobalFieldValue(val field: TsField, val value: TsTestValue) // TODO is it right?????

open class TsMethodCoverage

object NoCoverage : TsMethodCoverage()

sealed interface TsTestValue {
    data object TsAny : TsTestValue
    data object TsUnknown : TsTestValue
    data object TsNull : TsTestValue
    data object TsUndefined : TsTestValue
    data object TsException : TsTestValue

    data class TsBoolean(val value: Boolean) : TsTestValue
    data class TsString(val value: String) : TsTestValue
    data class TsBigInt(val value: String) : TsTestValue

    sealed interface TsNumber : TsTestValue {
        data class TsInteger(val value: Int) : TsNumber

        data class TsDouble(val value: Double) : TsNumber

        val number: Double
            get() = when (this) {
                is TsInteger -> value.toDouble()
                is TsDouble -> value
            }
    }

    data class TsObject(val addr: Int) : TsTestValue

    data class TsClass(
        val name: String,
        val properties: Map<String, TsTestValue>,
    ) : TsTestValue

    data class TsArray<T : TsTestValue>(
        val values: List<T>,
    ) : TsTestValue
}
