package GoToKotlin

const structField = `	var %s: %s? = null
`

const deserializeFunStart = `fun read_%s(buffReader: BufferedReader, id: Int): %s {
	val res = %s()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as %s
        }
        ptrMap[id] = res
		structToPtrMap[res] = id
    }
    var line: String
    var split: List<String>
    var id: Int
    var readType: String
`

const deserializeField = `
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
    res.%s = mapDec[readType]?.invoke(buffReader, id) as %s?
`

const deserializeEnd = `
	buffReader.readLine()
	return res
}
`

const readBaseTypes = `
fun readInteger(buffReader: BufferedReader, id: Int): Any {
    return buffReader.readLine().toLong()
}

fun readULong(buffReader: BufferedReader, id: Int): Any {
    return buffReader.readLine().toULong()
}

fun readString(buffReader: BufferedReader, id: Int): Any {
    return buffReader.readLine().drop(1).dropLast(1)
}

fun readBoolean(buffReader: BufferedReader, id: Int): Any {
    return buffReader.readLine() == "true"
}

fun readReal(buffReader: BufferedReader, id: Int): Any {
    return buffReader.readLine().toDouble()
}

fun readNil(buffReader: BufferedReader, id: Int): Any? {
    return null
}

fun readArray(buffReader: BufferedReader, id: Int): Any? {
    val res: MutableList<Any?> = mutableListOf()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id]
        }
        ptrMap[id] = res
    }
	var line = buffReader.readLine()
    while (line != "end") {
        var split: List<String>
        var id: Int
        split = line.split(" ")
        id = -1
        if (split.size > 1) {
            id = split[1].toInt()
        }
        res.add(mapDec[split[0]]?.invoke(buffReader, id))
        line = buffReader.readLine()
    }
    return res
}

fun readMap(buffReader: BufferedReader, id: Int): Any? {
    val res: MutableMap<Any?, Any?> = mutableMapOf()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id]
        }
        ptrMap[id] = res
    }
    var line = buffReader.readLine()
    while (line != "end") {
        var split: List<String>
        var id: Int
        split = line.split(" ")
        id = -1
        if (split.size > 1) {
            id = split[1].toInt()
        }
        val key = mapDec[split[0]]?.invoke(buffReader, id)
        line = buffReader.readLine()
        split = line.split(" ")
        id = -1
        if (split.size > 1) {
            id = split[1].toInt()
        }
        val value = mapDec[split[0]]?.invoke(buffReader, id)
        res[key] = value
        line = buffReader.readLine()
    }
    return res
}
`

const readerImports = `import java.io.BufferedReader
`

const entrypoint = `
fun StartDeserializer(buffReader: BufferedReader): Any? {
    val line = buffReader.readLine()
    val split = line.split(" ")
    val readType = split[0]
    var id = -1
    if (split.size > 1) {
        id = split[1].toInt()
    }
    return mapDec[readType]?.invoke(buffReader, id)
}
`

const kotlinConstants = `
val ptrMap: MutableMap<Int, Any> = mutableMapOf()
val structToPtrMap: MutableMap<Any, Int> = mutableMapOf()
val ptrToJacoMap: MutableMap<Int, Any> = mutableMapOf()
val mapDec: Map<String, (BufferedReader, Int)->Any?> = mapOf(
    "Int" to ::readInteger,
    "Short" to ::readInteger,
    "Long" to ::readInteger,
	"ULong" to ::readULong,
    "Float" to ::readReal,
    "Double" to ::readReal,
    "String" to ::readString,
    "Boolean" to ::readBoolean,
    "nil" to ::readNil,

    "array" to ::readArray,
    "map" to ::readMap`

const funcMapLine = `	"%s" to ::read_%s`
