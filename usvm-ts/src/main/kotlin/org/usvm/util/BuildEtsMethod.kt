package org.usvm.util

import org.jacodb.ets.dsl.ProgramBuilder
import org.jacodb.ets.dsl.program
import org.jacodb.ets.dsl.toBlockCfg
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.utils.toEtsBlockCfg

fun buildEtsMethod(
    name: String,
    enclosingClass: EtsClass,
    parameters: List<Pair<String, EtsType>>,
    returnType: EtsType,
    program: ProgramBuilder.() -> Unit,
): EtsMethod {
    val method = EtsMethodImpl(
        signature = EtsMethodSignature(
            enclosingClass = enclosingClass.signature,
            name = name,
            parameters = parameters.mapIndexed { index, (name, type) ->
                EtsMethodParameter(index, name, type)
            },
            returnType = returnType,
        )
    )

    val prog = program(program)
    val blockCfg = prog.toBlockCfg()
    val etsCfg = blockCfg.toEtsBlockCfg(method)
    method.body.cfg = etsCfg

    ((enclosingClass as EtsClassImpl).methods as MutableList).add(method)
    method.enclosingClass = enclosingClass

    return method
}
