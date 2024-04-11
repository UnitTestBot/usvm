package org.usvm.generated

import java.io.BufferedReader

class types_importKey {

	var path: String? = null
	var dir: String? = null
}

fun read_types_importKey(buffReader: BufferedReader, id: Int): types_importKey {
	val res = types_importKey()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_importKey
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
    res.path = mapDec[readType]?.invoke(buffReader, id) as String?

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
    res.dir = mapDec[readType]?.invoke(buffReader, id) as String?

	buffReader.readLine()
	return res
}
