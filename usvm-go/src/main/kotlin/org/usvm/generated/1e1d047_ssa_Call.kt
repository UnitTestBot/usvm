package org.usvm.generated

import java.io.BufferedReader
import org.usvm.jacodb.*
import org.usvm.jacodb.GoInstLocationImpl
class ssa_Call : ssaToJacoInst, ssaToJacoValue, ssaToJacoExpr {

	var register: ssa_register? = null
	var Call: ssa_CallCommon? = null

	var CallExpr: GoCallExpr? = null
	override fun createJacoDBInst(parent: GoMethod): GoCallInst {
        if (CallExpr == null) {
            CallExpr = ssa_CallExpr(this).createJacoDBExpr()
        }
        return GoCallInst(
            GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                Call!!.pos!!.toInt(),
                parent,
            ),
            parent,
            CallExpr!!
        )
    }
	
	override fun createJacoDBValue(): GoValue {
        if (CallExpr == null) {
            CallExpr = ssa_CallExpr(this).createJacoDBExpr()
        }
        return CallExpr!!
    }

	override fun createJacoDBExpr(): GoExpr {
        return createJacoDBValue()
    }
}

fun read_ssa_Call(buffReader: BufferedReader, id: Int): ssa_Call {
	val res = ssa_Call()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_Call
        }
        ptrMap[id] = res
		structToPtrMap[res] = id
    }
    var line: String
    var split: List<String>
    var id: Int
    var readType: String

	line = buffReader.readLine()
	if (line == "end") {
        return res
    }
    split = line.split(" ")
    readType = split[1]
    id = -1
    if (split.size > 2) {
        id = split[2].toInt()
    }
    res.register = mapDec[readType]?.invoke(buffReader, id) as ssa_register?

	line = buffReader.readLine()
	if (line == "end") {
        return res
    }
    split = line.split(" ")
    readType = split[1]
    id = -1
    if (split.size > 2) {
        id = split[2].toInt()
    }
    res.Call = mapDec[readType]?.invoke(buffReader, id) as ssa_CallCommon?

	buffReader.readLine()
	return res
}
