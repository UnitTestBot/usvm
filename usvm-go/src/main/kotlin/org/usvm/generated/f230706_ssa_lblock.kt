package org.usvm.generated

import java.io.BufferedReader

class ssa_lblock {

	var _goto: ssa_BasicBlock? = null
	var _break: ssa_BasicBlock? = null
	var _continue: ssa_BasicBlock? = null
}

fun read_ssa_lblock(buffReader: BufferedReader, id: Int): ssa_lblock {
	val res = ssa_lblock()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_lblock
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
    res._goto = mapDec[readType]?.invoke(buffReader, id) as ssa_BasicBlock?

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
    res._break = mapDec[readType]?.invoke(buffReader, id) as ssa_BasicBlock?

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
    res._continue = mapDec[readType]?.invoke(buffReader, id) as ssa_BasicBlock?

	buffReader.readLine()
	return res
}
