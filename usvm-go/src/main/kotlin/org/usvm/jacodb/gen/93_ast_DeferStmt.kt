package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class ast_DeferStmt {

	var Defer: Long? = null
	var Call: ast_CallExpr? = null
}

fun read_ast_DeferStmt(buffReader: BufferedReader, id: Int): ast_DeferStmt {
	val res = ast_DeferStmt()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_DeferStmt
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
    res.Defer = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.Call = mapDec[readType]?.invoke(buffReader, id) as ast_CallExpr?

	buffReader.readLine()
	return res
}
