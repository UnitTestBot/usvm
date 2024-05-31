package org.usvm.machine

import io.ksmt.expr.KExpr
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.ext.findMethodOrNull
import org.jacodb.api.jvm.ext.toType
import org.jacodb.panda.dynamic.api.PandaArrayType
import org.jacodb.panda.dynamic.api.PandaClassType
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaPhiValue
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.api.PandaTypedMethod
import org.jacodb.panda.dynamic.api.PandaValue
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.state.PandaState
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory
import org.usvm.uctx

@Suppress("UNCHECKED_CAST")
fun UWritableMemory<*>.write(lValue: ULValue<*, *>, expr: KExpr<out USort>) {
    write(lValue as ULValue<*, USort>, expr as UExpr<USort>, guard = expr.uctx.trueExpr)
}

fun PandaState.newStmt(stmt: PandaInst) {
    pathNode += stmt
}

fun PandaPhiValue.valueFromBB(bbId: Int): PandaValue {
    val idx = basicBlockIds.indexOf(bbId).takeIf { it != -1 }
        ?: error("No basic block with id $bbId in Phi with input basic blocks [${basicBlockIds.joinToString()}]")

    return inputs[idx]
}

val PandaInst.nextStmt: PandaInst?
    get() = location.let { it.method.instructions.getOrNull(it.index + 1) }
