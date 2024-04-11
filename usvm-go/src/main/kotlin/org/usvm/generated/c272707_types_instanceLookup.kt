package org.usvm.generated

import java.io.BufferedReader

class types_instanceLookup {

	var buf: List<types_Named>? = null
	var m: Map<types_Named, List<types_Named>>? = null
}

fun read_types_instanceLookup(buffReader: BufferedReader, id: Int): types_instanceLookup {
	val res = types_instanceLookup()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_instanceLookup
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
    res.buf = mapDec[readType]?.invoke(buffReader, id) as List<types_Named>?

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
    res.m = mapDec[readType]?.invoke(buffReader, id) as Map<types_Named, List<types_Named>>?

	buffReader.readLine()
	return res
}
