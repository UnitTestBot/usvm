package org.usvm.machine

import io.ksmt.expr.KExpr
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaPhiValue
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


