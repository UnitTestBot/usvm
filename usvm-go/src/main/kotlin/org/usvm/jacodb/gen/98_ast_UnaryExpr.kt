package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class ast_UnaryExpr {

	var OpPos: Long? = null
	var Op: Long? = null
	var X: Any? = null
}

fun read_ast_UnaryExpr(buffReader: BufferedReader, id: Int): ast_UnaryExpr {
	val res = ast_UnaryExpr()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_UnaryExpr
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
    res.OpPos = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.Op = mapDec[readType]?.invoke(buffReader, id) as Long?

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
