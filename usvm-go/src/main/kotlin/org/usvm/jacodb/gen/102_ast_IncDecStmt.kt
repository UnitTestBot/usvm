package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class ast_IncDecStmt {

	var X: Any? = null
	var TokPos: Long? = null
	var Tok: Long? = null
}

fun read_ast_IncDecStmt(buffReader: BufferedReader, id: Int): ast_IncDecStmt {
	val res = ast_IncDecStmt()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_IncDecStmt
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
    res.TokPos = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.Tok = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
