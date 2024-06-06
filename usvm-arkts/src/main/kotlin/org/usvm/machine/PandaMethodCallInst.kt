package org.usvm.machine

import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.panda.dynamic.api.PandaExpr
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaInstLocation
import org.jacodb.panda.dynamic.api.PandaInstVisitor
import org.jacodb.panda.dynamic.api.PandaMethod
import org.usvm.UExpr
import org.usvm.USort

abstract class PandaTransparentInstruction : PandaInst() {
    abstract val originalInst: PandaInst
}

abstract class PandaMethodCallBaseInst : PandaTransparentInstruction() {
    abstract override val method: PandaMethod

    override val operands: List<PandaExpr>
        get() = emptyList()

    override fun <T> accept(visitor: CommonInst.Visitor<T>): T {
        error("Auxiliary instruction")
    }

    override fun <T> accept(visitor: PandaInstVisitor<T>): T {
        error("Auxiliary instruction")
    }
}

sealed interface PandaMethodCall {
    val location: PandaInstLocation
    val method: PandaMethod
    val arguments: List<UExpr<out USort>>
    val returnSite: PandaInst
}

data class PandaConcreteMethodCallInst(
    override val location: PandaInstLocation,
    override val method: PandaMethod,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: PandaInst,
) : PandaMethodCallBaseInst(), PandaMethodCall {

    override val originalInst: PandaInst = returnSite
}

data class PandaVirtualMethodCallInst(
    override val location: PandaInstLocation,
    override val method: PandaMethod,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: PandaInst,
) : PandaMethodCallBaseInst(), PandaMethodCall {
    fun toConcreteMethodCall(concreteMethod: PandaMethod): PandaConcreteMethodCallInst =
        PandaConcreteMethodCallInst(location, concreteMethod, arguments, returnSite)

    override val originalInst: PandaInst = returnSite
}