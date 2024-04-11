package org.usvm.generated

import java.io.BufferedReader

class types__TypeSet {

	var methods: List<types_Func>? = null
	var terms: List<types_term>? = null
	var comparable: Boolean? = null
}

fun read_types__TypeSet(buffReader: BufferedReader, id: Int): types__TypeSet {
	val res = types__TypeSet()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types__TypeSet
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
    res.methods = mapDec[readType]?.invoke(buffReader, id) as List<types_Func>?

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
    res.terms = mapDec[readType]?.invoke(buffReader, id) as List<types_term>?

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
    res.comparable = mapDec[readType]?.invoke(buffReader, id) as Boolean?

	buffReader.readLine()
	return res
}
