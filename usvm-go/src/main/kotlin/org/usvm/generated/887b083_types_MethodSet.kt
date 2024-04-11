package org.usvm.generated

import java.io.BufferedReader

class types_MethodSet {

	var list: List<types_Selection>? = null
}

fun read_types_MethodSet(buffReader: BufferedReader, id: Int): types_MethodSet {
	val res = types_MethodSet()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_MethodSet
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
    res.list = mapDec[readType]?.invoke(buffReader, id) as List<types_Selection>?

	buffReader.readLine()
	return res
}
