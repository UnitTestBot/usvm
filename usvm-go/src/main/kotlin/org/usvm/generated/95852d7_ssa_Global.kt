package org.usvm.generated

import java.io.BufferedReader
import org.usvm.jacodb.*
class ssa_Global : ssaToJacoExpr, ssaToJacoValue {

	var name: String? = null
	var Object: Any? = null
	var typ: Any? = null
	var pos: Long? = null
	var Pkg: ssa_Package? = null

	override fun createJacoDBExpr(): GoGlobal {
        return GoGlobal(
            pos!!.toInt(),
            name!!,
            typ!! as GoType
        )
    }
	override fun createJacoDBValue(): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoGlobal
        }
        return createJacoDBExpr()
    }

}

fun read_ssa_Global(buffReader: BufferedReader, id: Int): ssa_Global {
	val res = ssa_Global()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_Global
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
    res.Object = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.typ = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.pos = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.Pkg = mapDec[readType]?.invoke(buffReader, id) as ssa_Package?

	buffReader.readLine()
	return res
}
