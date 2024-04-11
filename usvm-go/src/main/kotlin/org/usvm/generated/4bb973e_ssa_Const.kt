package org.usvm.generated

import java.io.BufferedReader
import org.usvm.jacodb.*
class ssa_Const : ssaToJacoExpr, ssaToJacoValue {

	var typ: Any? = null
	var Value: Any? = null

	override fun createJacoDBExpr(): GoConst {
        val innerVal = Value
        val name: String

        when (innerVal) {
            is Long -> {
                name = GoLong(
                    innerVal,
                    typ!! as GoType
                ).toString()
            }
            is Boolean -> {
                name = GoBool(
                    innerVal,
                    typ!! as GoType
                ).toString()
            }
            is Double -> {
                name = GoDouble(
                    innerVal,
                    typ!! as GoType
                ).toString()
            }
            is String -> {
                name = GoStringConstant(
                    innerVal,
                    typ!! as GoType
                ).toString()
            }
            is constant_intVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    typ!! as GoType
                ).toString()
            }
            is constant_stringVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    typ!! as GoType
                ).toString()
            }
            is constant_ratVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    typ!! as GoType
                ).toString()
            }
			is constant_floatVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    typ!! as GoType
                ).toString()
            }
			is constant_complexVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    typ!! as GoType
                ).toString()
            }
            else -> {
                name = GoNullConstant().toString()
            }
        }

        return GoConst(
            0,
            name,
            typ!! as GoType
        )
    }
	override fun createJacoDBValue(): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoConst
        }
        return createJacoDBExpr()
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
