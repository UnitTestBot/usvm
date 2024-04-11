package org.usvm.generated

import java.io.BufferedReader
import org.usvm.jacodb.GoType
class types_Signature : GoType {

	var rparams: types_TypeParamList? = null
	var tparams: types_TypeParamList? = null
	var scope: types_Scope? = null
	var recv: types_Var? = null
	var params: types_Tuple? = null
	var results: types_Tuple? = null
	var variadic: Boolean? = null

	override val typeName: String
        get(): String {
            var res = "func ("
            var paramsString = ""
            for (p in params!!.vars!!) {
                paramsString += p.Object!!.name + ", "
            }
            res += paramsString.removeSuffix(", ") + ") ("
            var resultsString = ""
            for (r in results!!.vars!!) {
                resultsString += r.Object!!.name + ", "
            }
            res += resultsString.removeSuffix(", ") + ")"
            return res
        }
}

fun read_types_Signature(buffReader: BufferedReader, id: Int): types_Signature {
	val res = types_Signature()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_Signature
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
    res.rparams = mapDec[readType]?.invoke(buffReader, id) as types_TypeParamList?

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
    res.tparams = mapDec[readType]?.invoke(buffReader, id) as types_TypeParamList?

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
    res.scope = mapDec[readType]?.invoke(buffReader, id) as types_Scope?

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
    res.recv = mapDec[readType]?.invoke(buffReader, id) as types_Var?

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
    res.params = mapDec[readType]?.invoke(buffReader, id) as types_Tuple?

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
    res.results = mapDec[readType]?.invoke(buffReader, id) as types_Tuple?

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
    res.variadic = mapDec[readType]?.invoke(buffReader, id) as Boolean?

	buffReader.readLine()
	return res
}
