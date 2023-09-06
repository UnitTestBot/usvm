package org.usvm.machine

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstLocation
import org.jacodb.api.cfg.JcInstVisitor
import org.jacodb.impl.cfg.JcInstLocationImpl
import org.usvm.UExpr
import org.usvm.USort

sealed interface UMethodCallBaseJcInst : JcInst {
    val method: JcMethod

    override val operands: List<JcExpr>
        get() = emptyList()

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        error("Auxiliary instruction")
    }
}

data class UMethodEntrypointJcInst(
    override val method: JcMethod
) : UMethodCallBaseJcInst {
    override val location: JcInstLocation
        get() = JcInstLocationImpl(method, 0, 0)
}

sealed interface UJcMethodCall {
    val location: JcInstLocation
    val method: JcMethod
    val arguments: List<UExpr<out USort>>
    val returnSite: JcInst
}

data class UConcreteMethodCallJcInst(
    override val location: JcInstLocation,
    override val method: JcMethod,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: JcInst
) : UMethodCallBaseJcInst, UJcMethodCall

data class UVirtualMethodCallJcInst(
    override val location: JcInstLocation,
    override val method: JcMethod,
    override val arguments: List<UExpr<out USort>>,
    override val returnSite: JcInst
) : UMethodCallBaseJcInst, UJcMethodCall
