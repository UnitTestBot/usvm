package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class ast_FuncLit {

	var Type: ast_FuncType? = null
	var Body: ast_BlockStmt? = null
}

fun read_ast_FuncLit(buffReader: BufferedReader, id: Int): ast_FuncLit {
	val res = ast_FuncLit()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_FuncLit
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
    res.Type = mapDec[readType]?.invoke(buffReader, id) as ast_FuncType?

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
    res.Body = mapDec[readType]?.invoke(buffReader, id) as ast_BlockStmt?

	buffReader.readLine()
	return res
}