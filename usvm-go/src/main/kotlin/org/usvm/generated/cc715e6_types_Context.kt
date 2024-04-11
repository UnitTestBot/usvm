package org.usvm.generated

import java.io.BufferedReader

class types_Context {

	var mu: sync_Mutex? = null
	var typeMap: Map<String, List<types_ctxtEntry>>? = null
	var nextID: Long? = null
	var originIDs: Map<Any, Long>? = null
}

fun read_types_Context(buffReader: BufferedReader, id: Int): types_Context {
	val res = types_Context()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_Context
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
    res.typeMap = mapDec[readType]?.invoke(buffReader, id) as Map<String, List<types_ctxtEntry>>?

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
    res.nextID = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.originIDs = mapDec[readType]?.invoke(buffReader, id) as Map<Any, Long>?

	buffReader.readLine()
	return res
}
