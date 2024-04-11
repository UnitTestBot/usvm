package org.usvm.generated

import org.usvm.jacodb.*

class ssa_CallExpr(init: ssa_Call) : ssaToJacoExpr, ssaToJacoValue {
    val type = init.register!!.typ!! as GoType
    val value = (init.Call!!.Value!! as ssaToJacoValue).createJacoDBValue()
    val operands = init.Call!!.Args!!.map { (it as ssaToJacoValue).createJacoDBValue() }

    override fun createJacoDBExpr(): GoCallExpr {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoCallExpr
        }

        val res = GoCallExpr(
            type,
            value,
            operands
        )
		if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }
        return res
    }
	override fun createJacoDBValue(): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoCallExpr
        }
        return createJacoDBExpr()
    }

}
