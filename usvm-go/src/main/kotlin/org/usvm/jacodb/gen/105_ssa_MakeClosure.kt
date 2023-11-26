package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class ssa_MakeClosure : ssaToJacoExpr, ssaToJacoValue {

	var register: ssa_register? = null
	var Fn: Any? = null
	var Bindings: List<Any>? = null

	override fun createJacoDBExpr(parent: GoMethod): GoMakeClosureExpr {
        return GoMakeClosureExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.num!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (Fn!! as ssa_Function).createJacoDBMethod(parent.fileSet),
			Bindings!!.map { (it as ssaToJacoValue).createJacoDBValue(parent) },
			"t${register!!.num!!.toInt()}",
        )
    }
	override fun createJacoDBValue(parent: GoMethod): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoMakeClosureExpr
        }
        return createJacoDBExpr(parent)
    }

}

fun read_ssa_MakeClosure(buffReader: BufferedReader, id: Int): ssa_MakeClosure {
	val res = ssa_MakeClosure()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_MakeClosure
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
    res.register = mapDec[readType]?.invoke(buffReader, id) as ssa_register?

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
    res.Fn = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.Bindings = mapDec[readType]?.invoke(buffReader, id) as List<Any>?

	buffReader.readLine()
	return res
}
