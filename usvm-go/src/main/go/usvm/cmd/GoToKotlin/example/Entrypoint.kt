package GoToJava

import java.io.BufferedReader

val ptrMap: MutableMap<Int, Any> = mutableMapOf()
val mapDec: Map<String, (BufferedReader, Int)->Any?> = mapOf(
    "Int" to ::readInteger,
    "Short" to ::readInteger,
    "Long" to ::readInteger,
    "Float" to ::readReal,
    "Double" to ::readReal,
    "String" to ::readString,
    "Boolean" to ::readBoolean,
    "nil" to ::readNil,

    "array" to ::readArray,
    "map" to ::readMap,
	"main_BigStruct" to ::read_main_BigStruct,
	"main_someStruct" to ::read_main_someStruct,
	"main_interfaceImpl" to ::read_main_interfaceImpl
)

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
