package org.usvm.annotations.codegeneration

enum class ObjectConverter(val repr: String) {
    StandardConverter("object_converter"),
    FrameConverter("frame_converter"),
    IntConverter("int_converter"),
    RefConverter("ref_converter"),
    ObjectWrapper("object_wrapper"),
    NoConverter("")
}

enum class CType(val repr: String) {
    PyObject("PyObject *"),
    PyFrameObject("PyFrameObject *"),
    CInt("int")
}

enum class JavaType(val repr: String, val call: String) {
    JObject("jobject", "Object"),
    JLong("jlong", "Long"),
    NoType("", "Void")
}

data class ArgumentDescription(
    val cType: CType,
    val javaType: JavaType,
    val converter: ObjectConverter
)

data class CPythonFunctionDescription(
    val cName: String,
    val args: List<ArgumentDescription>,
    val result: ArgumentDescription,
    val failValue: String,
    val defaultValue: String,
    val addToSymbolicAdapter: Boolean
)

fun generateCPythonFunction(description: CPythonFunctionDescription): String {
    val cName = description.cName
    val numberOfArgs = description.args.size
    val defaultValue = description.defaultValue
    val cArgs = description.args.mapIndexed { index, arg -> "${arg.cType.repr} arg_$index" }
    val javaArgsCreation = description.args.mapIndexed { index, arg ->
        """
            ${arg.javaType.repr} java_arg_$index = ${arg.converter.repr}(ctx, arg_$index, &fail); if (fail) return $defaultValue;
        """.trimIndent()
    }
    val javaReturnType = description.result.javaType.repr
    val javaReturnDescr = description.result.javaType.call
    val javaArgs = (listOf("ctx->context") + List(numberOfArgs) { "java_arg_$it" }).joinToString(", ")
    val returnValueCreation =
        if (description.result.javaType != JavaType.NoType)
            "$javaReturnType java_return = (*ctx->env)->CallStatic${javaReturnDescr}Method(ctx->env, ctx->cpython_adapter_cls, ctx->handle_$cName, $javaArgs);"
        else
            "(*ctx->env)->CallStaticVoidMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_$cName, $javaArgs);"
    val returnConverter = description.result.converter.repr
    val returnStmt =
        if (description.result.javaType == JavaType.NoType)
            "return $defaultValue;"
        else
            "return $returnConverter(ctx, java_return);"
    val cReturnType = description.result.cType.repr
    val failValue = description.failValue
    return """
        static $cReturnType
        $cName(${(listOf("void *arg") + cArgs).joinToString(", ")}) {
            // printf("INSIDE $cName!\n"); fflush(stdout);
            ConcolicContext *ctx = (ConcolicContext *) arg;
            int fail = 0;
            ${javaArgsCreation.joinToString("\n            ")}
            // printf("CALLING JAVA METHOD IN $cName!\n"); fflush(stdout);
            $returnValueCreation
            CHECK_FOR_EXCEPTION(ctx, $failValue)
            $returnStmt
        }
    """.trimIndent()
}

fun generateCPythonFunctions(descriptions: List<CPythonFunctionDescription>): String {
    val functions = descriptions.map(::generateCPythonFunction)
    val registrations = descriptions.filter { it.addToSymbolicAdapter }.joinToString {
        val name = it.cName
        "adapter->$name = $name;"
    }
    val registration = """
        static void
        REGISTER_ADAPTER_METHODS(SymbolicAdapter *adapter) {
            $registrations
        }
    """.trimIndent()
    return functions.joinToString("\n\n") + "\n\n" + registration
}