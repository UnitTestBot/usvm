package org.usvm.util

import org.jacodb.ets.dsl.CustomValue
import org.jacodb.ets.dsl.Expr
import org.jacodb.ets.dsl.ProgramBuilder
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNumberType

fun ProgramBuilder.callNumberIsNaN(arg: EtsLocal): Expr {
    val call = EtsInstanceCallExpr(
        instance = EtsLocal("Number"),
        callee = EtsMethodSignature(
            enclosingClass = EtsClassSignature(
                name = "Number",
                file = EtsFileSignature.UNKNOWN,
            ),
            name = "isNaN",
            parameters = listOf(EtsMethodParameter(0, "value", EtsAnyType)),
            returnType = EtsNumberType,
        ),
        args = listOf(arg),
        type = EtsNumberType,
    )
    return CustomValue(call)
}
