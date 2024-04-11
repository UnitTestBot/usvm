package org.usvm.generated

import java.io.BufferedReader

class typeutil_Map {

	var hasher: typeutil_Hasher? = null
	var table: Map<Long, List<typeutil_entry>>? = null
	var length: Long? = null
}

fun read_typeutil_Map(buffReader: BufferedReader, id: Int): typeutil_Map {
	val res = typeutil_Map()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as typeutil_Map
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
    res.table = mapDec[readType]?.invoke(buffReader, id) as Map<Long, List<typeutil_entry>>?

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
    res.length = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
