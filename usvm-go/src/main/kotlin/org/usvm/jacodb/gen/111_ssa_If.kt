package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class ssa_If : ssaToJacoInst {

	var anInstruction: ssa_anInstruction? = null
	var Cond: Any? = null

	override fun createJacoDBInst(parent: GoMethod): GoIfInst {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoIfInst
        }


        var cond: GoConditionExpr

        val trueConst = ssa_Const()
        trueConst.Value = true
        val type = types_Basic()
        type.kind = 1
        type.info = 1
        type.name = "bool"
        trueConst.typ = type

        if (Cond!! is ssa_BinOp) {
            val parsed = (Cond!! as ssa_BinOp).createJacoDBExpr(parent)
            if (parsed is GoConditionExpr) {
                cond = parsed
            } else {
                cond = GoEqlExpr(
                    lhv = trueConst.createJacoDBExpr(parent),
                    rhv = parsed as GoValue,
                    type = (type as ssaToJacoType).createJacoDBType(),
					name = "<if statement>",
                    location = GoInstLocationImpl(
                        -1, 0, parent
                    ),
                )
            }
        } else {
            cond = GoEqlExpr(
                lhv = trueConst.createJacoDBExpr(parent),
                rhv = (Cond!! as ssaToJacoValue).createJacoDBValue(parent),
                type = (type as ssaToJacoType).createJacoDBType(),
				name = "<if statement>",
                location = GoInstLocationImpl(
                    -1, 0, parent
                ),
            )
        }

        val res = GoIfInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                0,
                parent,
            ),
            cond,
            GoInstRef(
                anInstruction!!.block!!.Succs!![0].Index!!.toInt()
            ),
            GoInstRef(
                anInstruction!!.block!!.Succs!![1].Index!!.toInt()
            ),
        )
		if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }
        return res
    }
}

fun read_ssa_If(buffReader: BufferedReader, id: Int): ssa_If {
	val res = ssa_If()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_If
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
    res.anInstruction = mapDec[readType]?.invoke(buffReader, id) as ssa_anInstruction?

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
    res.Cond = mapDec[readType]?.invoke(buffReader, id) as Any?

	buffReader.readLine()
	return res
}