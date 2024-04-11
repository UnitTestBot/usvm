package org.usvm.generated

import java.io.BufferedReader

class types_Term {

	var tilde: Boolean? = null
	var typ: Any? = null
}

fun read_types_Term(buffReader: BufferedReader, id: Int): types_Term {
	val res = types_Term()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_Term
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
    res.tilde = mapDec[readType]?.invoke(buffReader, id) as Boolean?

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
    res.typ = mapDec[readType]?.invoke(buffReader, id) as Any?

	buffReader.readLine()
	return res
}
