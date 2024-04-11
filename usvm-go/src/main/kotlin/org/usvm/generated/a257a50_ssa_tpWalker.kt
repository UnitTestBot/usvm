package org.usvm.generated

import java.io.BufferedReader

class ssa_tpWalker {

	var mu: sync_Mutex? = null
	var seen: Map<Any, Boolean>? = null
}

fun read_ssa_tpWalker(buffReader: BufferedReader, id: Int): ssa_tpWalker {
	val res = ssa_tpWalker()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_tpWalker
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
    res.mu = mapDec[readType]?.invoke(buffReader, id) as sync_Mutex?

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
    res.seen = mapDec[readType]?.invoke(buffReader, id) as Map<Any, Boolean>?

	buffReader.readLine()
	return res
}
