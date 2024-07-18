package org.usvm.annotations.codegeneration

import org.usvm.annotations.CPythonFunctionDescription
import org.usvm.annotations.JavaType

fun generateCPythonFunction(description: CPythonFunctionDescription): Pair<String, String> {
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
        if (description.result.javaType != JavaType.NoType) {
            "$javaReturnType java_return = " +
                "(*ctx->env)->CallStatic${javaReturnDescr}Method(" +
                "ctx->env, " +
                "ctx->cpython_adapter_cls, " +
                "ctx->handle_$cName, $javaArgs" +
                ");"
        } else {
            "(*ctx->env)->CallStaticVoidMethod(ctx->env, ctx->cpython_adapter_cls, ctx->handle_$cName, $javaArgs);"
        }
    val returnConverter = description.result.converter.repr
    val returnStmt =
        if (description.result.javaType == JavaType.NoType) {
            "return $defaultValue;"
        } else {
            "return $returnConverter(ctx, java_return);"
        }
    val cReturnType = description.result.cType.repr
    val failValue = description.failValue
    val failLine = if (numberOfArgs > 0) "int fail = 0;" else ""
    val implementation = """
        $cReturnType
        $cName(${(listOf("void *arg") + cArgs).joinToString(", ")}) {
            // printf("INSIDE $cName!\n"); fflush(stdout);
            ConcolicContext *ctx = (ConcolicContext *) arg;
            $failLine
            ${javaArgsCreation.joinToString("\n            ")}
            // printf("CALLING JAVA METHOD IN $cName!\n"); fflush(stdout);
            $returnValueCreation
            CHECK_FOR_EXCEPTION(ctx, $failValue)
            $returnStmt
        }
    """.trimIndent()
    val header = "$cReturnType $cName(${(listOf("void *arg") + cArgs).joinToString(", ")});"

    return implementation to header
}

fun generateCPythonFunctionsImpls(descriptions: List<CPythonFunctionDescription>, headerName: String): String {
    val header = """
        #include "$headerName"
    """.trimIndent()
    val functions = descriptions.map { generateCPythonFunction(it).first }
    val registrations = descriptions.filter { it.addToSymbolicAdapter }.joinToString(separator = " ") {
        val name = it.cName
        "adapter->$name = $name;"
    }
    val registration = """
        void
        REGISTER_ADAPTER_METHODS(SymbolicAdapter *adapter) {
            $registrations
        }
    """.trimIndent()
    return header + "\n\n" + functions.joinToString("\n\n") + "\n\n" + registration
}

fun generateCPythonFunctionHeader(descriptions: List<CPythonFunctionDescription>): String {
    val header = """
        #include <jni.h>
        #include "Python.h"
        #include "converters.h"
    """.trimIndent()
    val functions = descriptions.map { generateCPythonFunction(it).second }
    val registration = "void REGISTER_ADAPTER_METHODS(SymbolicAdapter *adapter);"
    return header + "\n\n" + functions.joinToString("\n\n") + "\n\n" + registration
}
