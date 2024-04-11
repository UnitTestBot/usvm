package org.usvm.generated

import java.io.BufferedReader

class ast_BlockStmt {

	var Lbrace: Long? = null
	var List: List<Any>? = null
	var Rbrace: Long? = null
}

fun read_ast_BlockStmt(buffReader: BufferedReader, id: Int): ast_BlockStmt {
	val res = ast_BlockStmt()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_BlockStmt
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
    res.Lbrace = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.List = mapDec[readType]?.invoke(buffReader, id) as List<Any>?

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
    res.Rbrace = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
