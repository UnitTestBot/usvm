package org.usvm.generated

import java.io.BufferedReader

class types_Initializer {

	var Lhs: List<types_Var>? = null
	var Rhs: Any? = null
}

fun read_types_Initializer(buffReader: BufferedReader, id: Int): types_Initializer {
	val res = types_Initializer()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_Initializer
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
    res.Lhs = mapDec[readType]?.invoke(buffReader, id) as List<types_Var>?

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
    res.Rhs = mapDec[readType]?.invoke(buffReader, id) as Any?

	buffReader.readLine()
	return res
}
