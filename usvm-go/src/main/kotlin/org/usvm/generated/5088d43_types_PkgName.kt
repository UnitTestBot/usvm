package org.usvm.generated

import java.io.BufferedReader

class types_PkgName {

	var Object: types_object? = null
	var imported: types_Package? = null
	var used: Boolean? = null
}

fun read_types_PkgName(buffReader: BufferedReader, id: Int): types_PkgName {
	val res = types_PkgName()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_PkgName
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
    res.Object = mapDec[readType]?.invoke(buffReader, id) as types_object?

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
    res.imported = mapDec[readType]?.invoke(buffReader, id) as types_Package?

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
    res.used = mapDec[readType]?.invoke(buffReader, id) as Boolean?

	buffReader.readLine()
	return res
}
