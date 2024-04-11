package org.usvm.generated

import java.io.BufferedReader

class ast_ExprStmt {

	var X: Any? = null
}

fun read_ast_ExprStmt(buffReader: BufferedReader, id: Int): ast_ExprStmt {
	val res = ast_ExprStmt()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_ExprStmt
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
    res.X = mapDec[readType]?.invoke(buffReader, id) as Any?

	buffReader.readLine()
	return res
}
