package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class ssa_Const : ssaToJacoExpr, ssaToJacoValue {

	var typ: Any? = null
	var Value: Any? = null

	override fun createJacoDBExpr(parent: GoMethod): GoConst {
        val innerVal = Value
        val name: String
		val type = (typ!! as ssaToJacoType).createJacoDBType()

        when (innerVal) {
            is Long -> {
                name = GoLong(
                    innerVal,
                    type
                ).toString()
            }
            is Boolean -> {
                name = GoBool(
                    innerVal,
                    type
                ).toString()
            }
            is Double -> {
                name = GoDouble(
                    innerVal,
                    type
                ).toString()
            }
            is String -> {
                name = GoStringConstant(
                    innerVal,
                    type
                ).toString()
            }
            is constant_intVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    type
                ).toString()
            }
            is constant_stringVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    type
                ).toString()
            }
            is constant_ratVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    type
                ).toString()
            }
			is constant_floatVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    type
                ).toString()
            }
			is constant_complexVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    type
                ).toString()
            }
            else -> {
                name = GoNullConstant().toString()
            }
        }

        return GoConst(
            0,
            name,
            type
        )
    }
	override fun createJacoDBValue(parent: GoMethod): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoConst
        }
        return createJacoDBExpr(parent)
    }

}

fun read_ssa_Const(buffReader: BufferedReader, id: Int): ssa_Const {
	val res = ssa_Const()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_Const
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
    res.Value = mapDec[readType]?.invoke(buffReader, id) as Any?

	buffReader.readLine()
	return res
}