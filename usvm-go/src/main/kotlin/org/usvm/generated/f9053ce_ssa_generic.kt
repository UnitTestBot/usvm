package org.usvm.generated

import java.io.BufferedReader

class ssa_generic {

	var instancesMu: sync_Mutex? = null
	var instances: Map<List<Any>, ssa_Function>? = null
}

fun read_ssa_generic(buffReader: BufferedReader, id: Int): ssa_generic {
	val res = ssa_generic()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_generic
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
    res.instancesMu = mapDec[readType]?.invoke(buffReader, id) as sync_Mutex?

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
    res.instances = mapDec[readType]?.invoke(buffReader, id) as Map<List<Any>, ssa_Function>?

	buffReader.readLine()
	return res
}
