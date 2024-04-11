package org.usvm.generated

import java.io.BufferedReader

class typeutil_Hasher {

	var memo: Map<Any, Long>? = null
	var ptrMap: Map<Any, Long>? = null
	var sigTParams: types_TypeParamList? = null
}

fun read_typeutil_Hasher(buffReader: BufferedReader, id: Int): typeutil_Hasher {
	val res = typeutil_Hasher()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as typeutil_Hasher
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
    res.memo = mapDec[readType]?.invoke(buffReader, id) as Map<Any, Long>?

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
    res.ptrMap = mapDec[readType]?.invoke(buffReader, id) as Map<Any, Long>?

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
    res.sigTParams = mapDec[readType]?.invoke(buffReader, id) as types_TypeParamList?

	buffReader.readLine()
	return res
}
