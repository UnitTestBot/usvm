package org.usvm.generated

import java.io.BufferedReader

class atomic_Int32 {

	var v: Long? = null
}

fun read_atomic_Int32(buffReader: BufferedReader, id: Int): atomic_Int32 {
	val res = atomic_Int32()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as atomic_Int32
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
    res.v = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
