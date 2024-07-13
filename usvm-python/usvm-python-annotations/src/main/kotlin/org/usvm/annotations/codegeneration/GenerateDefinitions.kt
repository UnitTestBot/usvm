package org.usvm.annotations.codegeneration

import org.usvm.annotations.DefinitionDescriptor

fun generateCPythonAdapterDefs(defs: List<DefinitionDescriptor>): String {
    val jmethodIDMacro = defs.fold("#define HANDLERS_DEFS ") { acc, def ->
        acc + "jmethodID handle_${def.cName}; "
    }
    val nameMacros = defs.map { "#define handle_name_${it.cName} \"${it.javaName}\"" }
    val sigMacros = defs.map { "#define handle_sig_${it.cName} \"${it.javaSignature}\"" }

    val registrations = defs.fold("#define DO_REGISTRATIONS(dist, env) ") { acc, def ->
        val name = def.cName
        acc + "dist->handle_$name = " +
            "(*env)->GetStaticMethodID(" +
            "env, " +
            "dist->cpython_adapter_cls, " +
            "handle_name_$name, " +
            "handle_sig_$name" +
            ");"
    }

    return """
            $jmethodIDMacro
            ${nameMacros.joinToString("\n            ")}
            ${sigMacros.joinToString("\n            ")}
            $registrations
    """.trimIndent()
}
