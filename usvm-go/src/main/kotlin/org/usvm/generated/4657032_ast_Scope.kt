package org.usvm.generated

import java.io.BufferedReader

class ast_Scope {

	var Outer: ast_Scope? = null
	var Objects: Map<String, ast_Object>? = null
}

fun read_ast_Scope(buffReader: BufferedReader, id: Int): ast_Scope {
	val res = ast_Scope()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_Scope
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
    res.Outer = mapDec[readType]?.invoke(buffReader, id) as ast_Scope?

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
    res.Objects = mapDec[readType]?.invoke(buffReader, id) as Map<String, ast_Object>?

	buffReader.readLine()
	return res
}
