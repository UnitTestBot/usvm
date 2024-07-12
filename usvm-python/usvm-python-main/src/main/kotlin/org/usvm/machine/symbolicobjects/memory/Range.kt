package org.usvm.machine.symbolicobjects.memory

import io.ksmt.sort.KIntSort
import org.usvm.UExpr
import org.usvm.api.writeField
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.symbolicobjects.RangeContents
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

fun UninterpretedSymbolicPythonObject.setRangeContent(
    ctx: ConcolicRunContext,
    start: UExpr<KIntSort>,
    stop: UExpr<KIntSort>,
    step: UExpr<KIntSort>
) = with(ctx.ctx) {
    require(ctx.curState != null)
    addSupertypeSoft(ctx, typeSystem.pythonRange)
    ctx.curState!!.memory.writeField(address, RangeContents.start, intSort, start, trueExpr)
    ctx.curState!!.memory.writeField(address, RangeContents.stop, intSort, stop, trueExpr)
    ctx.curState!!.memory.writeField(address, RangeContents.step, intSort, step, trueExpr)
    val lengthRValue = mkIte(
        step gt mkIntNum(0),
        mkIte(
            stop gt start,
            mkArithDiv(
                mkArithAdd(stop, mkArithUnaryMinus(start), step, mkIntNum(-1)),
                step
            ),
            mkIntNum(0)
        ),
        mkIte(
            start gt stop,
            mkArithDiv(
                mkArithAdd(start, mkArithUnaryMinus(stop), mkArithUnaryMinus(step), mkIntNum(-1)),
                mkArithUnaryMinus(step)
            ),
            mkIntNum(0)
        )
    )
    ctx.curState!!.memory.writeField(address, RangeContents.length, intSort, lengthRValue, trueExpr)
}
