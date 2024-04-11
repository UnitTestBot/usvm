package org.usvm.generated

import java.io.BufferedReader

class ast_FuncType {

	var Func: Long? = null
	var TypeParams: ast_FieldList? = null
	var Params: ast_FieldList? = null
	var Results: ast_FieldList? = null
}

fun read_ast_FuncType(buffReader: BufferedReader, id: Int): ast_FuncType {
	val res = ast_FuncType()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_FuncType
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
    res.Func = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.TypeParams = mapDec[readType]?.invoke(buffReader, id) as ast_FieldList?

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
    res.Params = mapDec[readType]?.invoke(buffReader, id) as ast_FieldList?

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
    res.Results = mapDec[readType]?.invoke(buffReader, id) as ast_FieldList?

	buffReader.readLine()
	return res
}
