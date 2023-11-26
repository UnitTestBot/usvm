package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class ssa_anInstruction {

	var block: ssa_BasicBlock? = null
}

fun read_ssa_anInstruction(buffReader: BufferedReader, id: Int): ssa_anInstruction {
	val res = ssa_anInstruction()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_anInstruction
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
    res.block = mapDec[readType]?.invoke(buffReader, id) as ssa_BasicBlock?

	buffReader.readLine()
	return res
}
