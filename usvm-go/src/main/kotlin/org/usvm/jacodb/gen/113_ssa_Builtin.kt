package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class ssa_Builtin : ssaToJacoExpr, ssaToJacoValue {

	var name: String? = null
	var sig: types_Signature? = null

	override fun createJacoDBExpr(parent: GoMethod): GoBuiltin {
        return GoBuiltin(
            0,
            name!!,
            sig!!.createJacoDBType()
        )
    }
	override fun createJacoDBValue(parent: GoMethod): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoBuiltin
        }
        return createJacoDBExpr(parent)
    }

}

fun read_ssa_Builtin(buffReader: BufferedReader, id: Int): ssa_Builtin {
	val res = ssa_Builtin()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_Builtin
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
    res.name = mapDec[readType]?.invoke(buffReader, id) as String?

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
    res.sig = mapDec[readType]?.invoke(buffReader, id) as types_Signature?

	buffReader.readLine()
	return res
}
