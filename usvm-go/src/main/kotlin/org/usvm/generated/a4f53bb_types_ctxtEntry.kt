package org.usvm.generated

import java.io.BufferedReader

class types_ctxtEntry {

	var orig: Any? = null
	var targs: List<Any>? = null
	var instance: Any? = null
}

fun read_types_ctxtEntry(buffReader: BufferedReader, id: Int): types_ctxtEntry {
	val res = types_ctxtEntry()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_ctxtEntry
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
    res.orig = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.targs = mapDec[readType]?.invoke(buffReader, id) as List<Any>?

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
    res.instance = mapDec[readType]?.invoke(buffReader, id) as Any?

	buffReader.readLine()
	return res
}
