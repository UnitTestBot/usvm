package org.usvm.generated

import java.io.BufferedReader

class types_declInfo {

	var file: types_Scope? = null
	var lhs: List<types_Var>? = null
	var vtyp: Any? = null
	var init: Any? = null
	var inherited: Boolean? = null
	var tdecl: ast_TypeSpec? = null
	var fdecl: ast_FuncDecl? = null
	var deps: Map<Any, Boolean>? = null
}

fun read_types_declInfo(buffReader: BufferedReader, id: Int): types_declInfo {
	val res = types_declInfo()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_declInfo
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
    res.file = mapDec[readType]?.invoke(buffReader, id) as types_Scope?

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
    res.lhs = mapDec[readType]?.invoke(buffReader, id) as List<types_Var>?

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
    res.vtyp = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.init = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.inherited = mapDec[readType]?.invoke(buffReader, id) as Boolean?

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
    res.tdecl = mapDec[readType]?.invoke(buffReader, id) as ast_TypeSpec?

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
    res.fdecl = mapDec[readType]?.invoke(buffReader, id) as ast_FuncDecl?

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
    res.deps = mapDec[readType]?.invoke(buffReader, id) as Map<Any, Boolean>?

	buffReader.readLine()
	return res
}
