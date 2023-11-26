package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class ssa_RunDefers : ssaToJacoInst {

	var anInstruction: ssa_anInstruction? = null

	override fun createJacoDBInst(parent: GoMethod): GoRunDefersInst {
        return GoRunDefersInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                0,
                parent,
            ),
        )
    }
}

fun read_ssa_RunDefers(buffReader: BufferedReader, id: Int): ssa_RunDefers {
	val res = ssa_RunDefers()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_RunDefers
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

	buffReader.readLine()
	return res
}
