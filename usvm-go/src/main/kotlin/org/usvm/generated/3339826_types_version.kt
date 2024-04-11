package org.usvm.generated

import java.io.BufferedReader

class types_version {

	var major: Long? = null
	var minor: Long? = null
}

fun read_types_version(buffReader: BufferedReader, id: Int): types_version {
	val res = types_version()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_version
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
    res.major = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.minor = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
