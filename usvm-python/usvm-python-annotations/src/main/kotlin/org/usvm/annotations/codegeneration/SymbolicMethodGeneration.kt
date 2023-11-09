package org.usvm.annotations.codegeneration

import org.usvm.annotations.ids.SymbolicMethodId

fun generateSymbolicMethod(id: SymbolicMethodId): String {
    val cpythonFunctionInfo = CPythonFunctionDescription(
        id.cName!!,
        listOf(
            ArgumentDescription(
                CType.JObject,
                JavaType.JObject,
                ObjectConverter.ObjectIdConverter
            ),
            ArgumentDescription(
                CType.PyObject,
                JavaType.JObjectArray,
                ObjectConverter.TupleConverter
            )
        ),
        ArgumentDescription(
            CType.PyObject,
            JavaType.JObject,
            ObjectConverter.ObjectWrapper
        ),
        "0",
        "Py_None",
        addToSymbolicAdapter = false
    )
    return "static " + generateCPythonFunction(cpythonFunctionInfo).first
}

fun generateSymbolicMethodInitialization(): String {
    val clsName = SymbolicMethodId::class.java.canonicalName.replace('.', '/')
    val prefix = """
        jclass symbolicMethodIdCls = (*env)->FindClass(env, "$clsName");
        assert(!(*env)->ExceptionCheck(env));
        jfieldID symbolicMethodIdRefField = (*env)->GetFieldID(env, symbolicMethodIdCls, "cRef", "J");
        jfieldID symbolicMethodCurFieldID;
        jobject symbolicMethodCurObject;
        jlong symbolicMethodCurRef;
    """.trimIndent()
    val clsNameDescr = "L$clsName;"
    val items = SymbolicMethodId.values().map {
        require(it.cName != null)
        """
            symbolicMethodCurFieldID = (*env)->GetStaticFieldID(env, symbolicMethodIdCls, "${it.name}", "$clsNameDescr");
            symbolicMethodCurObject = (*env)->GetStaticObjectField(env, symbolicMethodIdCls, symbolicMethodCurFieldID);
            symbolicMethodCurRef = (jlong) ${it.cName};
            (*env)->SetLongField(env, symbolicMethodCurObject, symbolicMethodIdRefField, symbolicMethodCurRef);
        """.trimIndent()
    }
    return "#define SYMBOLIC_METHOD_INITIALIZATION \\\n" +
            prefix.replace("\n", "\\\n") + "\\\n" +
            items.joinToString("\n").replace("\n", "\\\n") + "\n"
}

fun generateMethodCheck(): String {
    val items = SymbolicMethodId.values().map {
        """
            if (ptr == ${it.cName})
                return ${it.cName};
        """.trimIndent()
    }

    return """
        static call_type find_symbolic_method(void *ptr) {
            ${items.joinToString("\n").replace("\n", "\n            ")}
            assert(0);  // not reachable
        }
    """.trimIndent()
}