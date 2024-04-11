package org.usvm.generated

import java.io.BufferedReader

class types_Builtin {

	var Object: types_object? = null
	var id: Long? = null
}

fun read_types_Builtin(buffReader: BufferedReader, id: Int): types_Builtin {
	val res = types_Builtin()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_Builtin
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
    res.Object = mapDec[readType]?.invoke(buffReader, id) as types_object?

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
    res.id = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
