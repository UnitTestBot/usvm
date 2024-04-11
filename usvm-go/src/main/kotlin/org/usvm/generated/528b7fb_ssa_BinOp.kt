package org.usvm.generated

import java.io.BufferedReader
import org.usvm.jacodb.*
class ssa_BinOp : ssaToJacoExpr, ssaToJacoValue {

	var register: ssa_register? = null
	var Op: Long? = null
	var X: Any? = null
	var Y: Any? = null

	override fun createJacoDBExpr(): GoBinaryExpr {
        val type = register!!.typ!! as GoType

        when (Op!!) {
            12L -> return GoAddExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            13L -> return GoSubExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            14L -> return GoMulExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            15L -> return GoDivExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            16L -> return GoModExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            17L -> return GoAndExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            18L -> return GoOrExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            19L -> return GoXorExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            20L -> return GoShlExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            21L -> return GoShrExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            22L -> return GoAndNotExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            39L -> return GoEqlExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            44L -> return GoNeqExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            40L -> return GoLssExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            45L -> return GoLeqExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            41L -> return GoGtrExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            46L -> return GoGeqExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(),
                type = type
            )
            else -> error("unexpected BinOp ${Op!!}")
        }
    }

	override fun createJacoDBValue(): GoValue {
        val res = createJacoDBExpr()
        if (res is GoValue) {
            return res
        }
        error("unexpected cast to Value $res")
    }
}

fun read_ssa_BinOp(buffReader: BufferedReader, id: Int): ssa_BinOp {
	val res = ssa_BinOp()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_BinOp
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
    res.Y = mapDec[readType]?.invoke(buffReader, id) as Any?

	buffReader.readLine()
	return res
}
