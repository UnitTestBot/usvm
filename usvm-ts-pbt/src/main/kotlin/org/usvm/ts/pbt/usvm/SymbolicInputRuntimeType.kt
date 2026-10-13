package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.usvm.ts.pbt.model.ArrayDomain
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.ConstantDomain
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.NumberDomain
import org.usvm.ts.pbt.model.ObjectDomain
import org.usvm.ts.pbt.model.OptionalDomain
import org.usvm.ts.pbt.model.PropertyDomain
import org.usvm.ts.pbt.model.StringDomain
import org.usvm.ts.pbt.model.TupleDomain

internal fun PropertyDomain.symbolicInputRuntimeType(
    objectType: (ObjectDomain) -> EtsClassType,
): EtsType = when (this) {
    BooleanDomain -> {
        EtsBooleanType
    }
    is IntegerDomain, is NumberDomain -> {
        EtsNumberType
    }
    is StringDomain -> {
        EtsStringType
    }
    is ArrayDomain -> {
        val nestedType = element.symbolicInputRuntimeType(objectType)
        if (nestedType is EtsArrayType) {
            nestedType.copy(dimensions = nestedType.dimensions + 1)
        } else {
            EtsArrayType(elementType = nestedType, dimensions = 1)
        }
    }
    is TupleDomain -> {
        EtsTupleType(types = elements.map { it.symbolicInputRuntimeType(objectType) })
    }
    is OptionalDomain -> {
        val nestedType = value.symbolicInputRuntimeType(objectType)
        val nilType = ConstantDomain(nil).symbolicInputRuntimeType(objectType)
        EtsUnionType(types = listOf(nestedType, nilType))
    }
    is ObjectDomain -> {
        objectType(this)
    }
    is ConstantDomain -> {
        when (value) {
            is JsConcreteValue.Boolean -> EtsBooleanType
            is JsConcreteValue.Number -> EtsNumberType
            is JsConcreteValue.String -> EtsStringType
            JsConcreteValue.Null -> EtsNullType
            JsConcreteValue.Undefined -> EtsUndefinedType
            is JsConcreteValue.Array, is JsConcreteValue.Object -> {
                error("Structured constants are not primitive domains")
            }
        }
    }
}
