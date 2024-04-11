package org.usvm.generated

import java.io.BufferedReader

class types_TypeParamList {

	var tparams: List<types_TypeParam>? = null
}

fun read_types_TypeParamList(buffReader: BufferedReader, id: Int): types_TypeParamList {
	val res = types_TypeParamList()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_TypeParamList
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
    res.tparams = mapDec[readType]?.invoke(buffReader, id) as List<types_TypeParam>?

	buffReader.readLine()
	return res
}
