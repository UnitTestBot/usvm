package org.usvm.generated

import java.io.BufferedReader

class typeutil_entry {

	var key: Any? = null
	var value: Any? = null
}

fun read_typeutil_entry(buffReader: BufferedReader, id: Int): typeutil_entry {
	val res = typeutil_entry()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as typeutil_entry
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
    res.key = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.value = mapDec[readType]?.invoke(buffReader, id) as Any?

	buffReader.readLine()
	return res
}
