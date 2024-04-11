package org.usvm.generated

import java.io.BufferedReader

class ssa_typeListMap {

	var hasher: typeutil_Hasher? = null
	var buckets: Map<Long, List<List<Any>>>? = null
}

fun read_ssa_typeListMap(buffReader: BufferedReader, id: Int): ssa_typeListMap {
	val res = ssa_typeListMap()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_typeListMap
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
    res.hasher = mapDec[readType]?.invoke(buffReader, id) as typeutil_Hasher?

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
    res.buckets = mapDec[readType]?.invoke(buffReader, id) as Map<Long, List<List<Any>>>?

	buffReader.readLine()
	return res
}
