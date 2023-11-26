package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class ssa_Panic : ssaToJacoInst {

	var anInstruction: ssa_anInstruction? = null
	var X: Any? = null
	var pos: Long? = null

	override fun createJacoDBInst(parent: GoMethod): GoPanicInst {
        return GoPanicInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                pos!!.toInt(),
                parent,
            ),
			(X!! as ssaToJacoValue).createJacoDBValue(parent),
        )
    }
}

fun read_ssa_Panic(buffReader: BufferedReader, id: Int): ssa_Panic {
	val res = ssa_Panic()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_Panic
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
    res.X = mapDec[readType]?.invoke(buffReader, id) as Any?

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
