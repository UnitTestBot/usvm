package org.usvm.generated

import java.io.BufferedReader

class ssa_canonizer {

	var mu: sync_Mutex? = null
	var types: typeutil_Map? = null
	var lists: ssa_typeListMap? = null
}

fun read_ssa_canonizer(buffReader: BufferedReader, id: Int): ssa_canonizer {
	val res = ssa_canonizer()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_canonizer
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
    res.types = mapDec[readType]?.invoke(buffReader, id) as typeutil_Map?

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
    res.lists = mapDec[readType]?.invoke(buffReader, id) as ssa_typeListMap?

	buffReader.readLine()
	return res
}
