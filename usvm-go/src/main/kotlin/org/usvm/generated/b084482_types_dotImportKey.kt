package org.usvm.generated

import java.io.BufferedReader

class types_dotImportKey {

	var scope: types_Scope? = null
	var name: String? = null
}

fun read_types_dotImportKey(buffReader: BufferedReader, id: Int): types_dotImportKey {
	val res = types_dotImportKey()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_dotImportKey
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
    res.scope = mapDec[readType]?.invoke(buffReader, id) as types_Scope?

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
    res.name = mapDec[readType]?.invoke(buffReader, id) as String?

	buffReader.readLine()
	return res
}
