package org.usvm.generated

import java.io.BufferedReader

class sync_Mutex {

	var state: Long? = null
	var sema: Long? = null
}

fun read_sync_Mutex(buffReader: BufferedReader, id: Int): sync_Mutex {
	val res = sync_Mutex()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as sync_Mutex
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
    res.state = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.sema = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
