package org.usvm.generated

import java.io.BufferedReader

class types_Nil {

	var Object: types_object? = null
}

fun read_types_Nil(buffReader: BufferedReader, id: Int): types_Nil {
	val res = types_Nil()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_Nil
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

	buffReader.readLine()
	return res
}
