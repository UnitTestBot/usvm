package org.usvm.generated

import java.io.BufferedReader

class token_FileSet {

	var mutex: sync_RWMutex? = null
	var base: Long? = null
	var files: List<token_File>? = null
}

fun read_token_FileSet(buffReader: BufferedReader, id: Int): token_FileSet {
	val res = token_FileSet()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as token_FileSet
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
    res.mutex = mapDec[readType]?.invoke(buffReader, id) as sync_RWMutex?

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
    res.base = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.files = mapDec[readType]?.invoke(buffReader, id) as List<token_File>?

	buffReader.readLine()
	return res
}
