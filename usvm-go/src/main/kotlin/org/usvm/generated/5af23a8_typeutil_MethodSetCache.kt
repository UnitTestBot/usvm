package org.usvm.generated

import java.io.BufferedReader

class typeutil_MethodSetCache {

	var mu: sync_Mutex? = null
	var named: Map<types_Named, generatedInlineStruct_001>? = null
	var others: Map<Any, types_MethodSet>? = null
}

fun read_typeutil_MethodSetCache(buffReader: BufferedReader, id: Int): typeutil_MethodSetCache {
	val res = typeutil_MethodSetCache()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as typeutil_MethodSetCache
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
    res.named = mapDec[readType]?.invoke(buffReader, id) as Map<types_Named, generatedInlineStruct_001>?

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
    res.others = mapDec[readType]?.invoke(buffReader, id) as Map<Any, types_MethodSet>?

	buffReader.readLine()
	return res
}
