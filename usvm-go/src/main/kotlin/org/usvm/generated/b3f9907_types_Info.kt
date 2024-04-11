package org.usvm.generated

import java.io.BufferedReader

class types_Info {

	var Types: Map<Any, types_TypeAndValue>? = null
	var Instances: Map<ast_Ident, types_Instance>? = null
	var Defs: Map<ast_Ident, Any>? = null
	var Uses: Map<ast_Ident, Any>? = null
	var Implicits: Map<Any, Any>? = null
	var Selections: Map<ast_SelectorExpr, types_Selection>? = null
	var Scopes: Map<Any, types_Scope>? = null
	var InitOrder: List<types_Initializer>? = null
}

fun read_types_Info(buffReader: BufferedReader, id: Int): types_Info {
	val res = types_Info()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_Info
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
    res.Types = mapDec[readType]?.invoke(buffReader, id) as Map<Any, types_TypeAndValue>?

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
    res.Instances = mapDec[readType]?.invoke(buffReader, id) as Map<ast_Ident, types_Instance>?

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
    res.Defs = mapDec[readType]?.invoke(buffReader, id) as Map<ast_Ident, Any>?

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
    res.Uses = mapDec[readType]?.invoke(buffReader, id) as Map<ast_Ident, Any>?

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
    res.Implicits = mapDec[readType]?.invoke(buffReader, id) as Map<Any, Any>?

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
    res.Selections = mapDec[readType]?.invoke(buffReader, id) as Map<ast_SelectorExpr, types_Selection>?

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
    res.Scopes = mapDec[readType]?.invoke(buffReader, id) as Map<Any, types_Scope>?

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
    res.InitOrder = mapDec[readType]?.invoke(buffReader, id) as List<types_Initializer>?

	buffReader.readLine()
	return res
}
