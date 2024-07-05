package org.usvm.annotations

data class MemberDescriptorInfo(
    val nativeTypeName: String,
    val nativeMemberName: String,
    val javaMemberName: String,
)

const val MEMBER_DESCRIPTION_QUALNAME =
    "Lorg/usvm/machine/interpreters/symbolic/operations/descriptors/MemberDescriptor;"

enum class ObjectConverter(val repr: String) {
    StandardConverter("object_converter"),
    FrameConverter("frame_converter"),
    IntConverter("int_converter"),
    RefConverter("ref_converter"),
    ObjectWrapper("object_wrapper"),
    ArrayConverter("array_converter"),
    TupleConverter("tuple_converter"),
    StringConverter("string_converter"),
    ObjectIdConverter("object_id_converter"),
    NoConverter(""),
}

enum class CType(val repr: String) {
    PyObject("PyObject *"),
    PyObjectArray("PyObject **"),
    PyFrameObject("PyFrameObject *"),
    CInt("int"),
    CStr("const char *"),
    JObject("jobject"),
}

enum class JavaType(val repr: String, val call: String) {
    JObject("jobject", "Object"),
    JLong("jlong", "Long"),
    JInt("jint", "Int"),
    JBoolean("jboolean", "Boolean"),
    JObjectArray("jobjectArray", "Object"),
    NoType("", "Void"),
}

data class ArgumentDescription(
    val cType: CType,
    val javaType: JavaType,
    val converter: ObjectConverter,
)

data class CPythonFunctionDescription(
    val cName: String,
    val args: List<ArgumentDescription>,
    val result: ArgumentDescription,
    val failValue: String,
    val defaultValue: String,
    val addToSymbolicAdapter: Boolean,
)

data class DefinitionDescriptor(
    val cName: String,
    val javaName: String,
    val javaSignature: String,
)
