package org.usvm.jacodb.gen

import org.jacodb.go.api.*

class ssa_CallExpr(init: ssa_Call, val callee: GoMethod? = null) : ssaToJacoExpr, ssaToJacoValue {
    val type = (init.register!!.typ!! as ssaToJacoType).createJacoDBType()
    val value = (init.Call!!.Value!! as ssaToJacoValue).createJacoDBValue(callee!!)
    val operands = init.Call!!.Args!!.map { (it as ssaToJacoValue).createJacoDBValue(callee!!) }.map { i ->
        if (i is GoAssignableInst) {
            GoFreeVar(
                i.location.index,
                i.name,
                i.type
            )
        } else {
            i
        }
    }
    val name = "t${init.register!!.num!!}"
    val location = GoInstLocationImpl(
        init.register!!.anInstruction!!.block!!.Index!!.toInt(),
        init.Call!!.pos!!.toInt(),
        callee!!,
    )

    override fun createJacoDBExpr(parent: GoMethod): GoCallExpr {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoCallExpr
        }

        val res = GoCallExpr(
            location,
            type,
            value,
            operands,
            callee,
            name,
        )
		if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }
        return res
    }
	override fun createJacoDBValue(parent: GoMethod): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoCallExpr
        }
        return createJacoDBExpr(parent)
    }

}
