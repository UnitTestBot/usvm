package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class ssa_Defer : ssaToJacoInst {

	var anInstruction: ssa_anInstruction? = null
	var Call: ssa_CallCommon? = null
	var pos: Long? = null

	override fun createJacoDBInst(parent: GoMethod): GoDeferInst {
        return GoDeferInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                pos!!.toInt(),
                parent,
            ),
			(Call!!.Value!! as ssaToJacoValue).createJacoDBValue(parent),
            Call!!.Args!!.map { (it as ssaToJacoValue).createJacoDBValue(parent) }
        )
    }
}

fun read_ssa_Defer(buffReader: BufferedReader, id: Int): ssa_Defer {
	val res = ssa_Defer()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_Defer
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
    res.Call = mapDec[readType]?.invoke(buffReader, id) as ssa_CallCommon?

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
    res.pos = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
