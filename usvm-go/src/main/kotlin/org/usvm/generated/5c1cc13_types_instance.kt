package org.usvm.generated

import java.io.BufferedReader

class types_instance {

	var orig: types_Named? = null
	var targs: types_TypeList? = null
	var expandedMethods: Long? = null
	var ctxt: types_Context? = null
}

fun read_types_instance(buffReader: BufferedReader, id: Int): types_instance {
	val res = types_instance()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_instance
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
    res.orig = mapDec[readType]?.invoke(buffReader, id) as types_Named?

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
    res.targs = mapDec[readType]?.invoke(buffReader, id) as types_TypeList?

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
    res.expandedMethods = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.ctxt = mapDec[readType]?.invoke(buffReader, id) as types_Context?

	buffReader.readLine()
	return res
}
