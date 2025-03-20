package org.usvm.model

import org.jacodb.ets.base.UNKNOWN_CLASS_NAME
import org.jacodb.ets.base.UNKNOWN_FILE_NAME
import org.jacodb.ets.base.UNKNOWN_NAMESPACE_NAME
import org.jacodb.ets.base.UNKNOWN_PROJECT_NAME

data class TsFileSignature(
    val projectName: String,
    val fileName: String,
) {
    override fun toString(): String {
        // Remove ".d.ts" and ".ts" file ext:
        val name = fileName.replace(REGEX_TS_SUFFIX, "")
        return "@$projectName/$name"
    }

    companion object {
        /**
         * Precompiled [Regex] for `.d.ts` and `.ts` file extensions.
         */
        private val REGEX_TS_SUFFIX = """(\.d\.ts|\.ts)$""".toRegex()

        val UNKNOWN = TsFileSignature(
            projectName = UNKNOWN_PROJECT_NAME,
            fileName = UNKNOWN_FILE_NAME,
        )
    }
}

data class TsNamespaceSignature(
    val name: String,
    val file: TsFileSignature,
    val namespace: TsNamespaceSignature? = null,
) {
    override fun toString(): String {
        return if (namespace != null) {
            "$namespace::$name"
        } else {
            "$file: $name"
        }
    }

    companion object {
        val DEFAULT = TsNamespaceSignature(
            name = UNKNOWN_NAMESPACE_NAME,
            file = TsFileSignature.UNKNOWN,
        )
    }
}

data class TsClassSignature(
    val name: String,
    val file: TsFileSignature,
    val namespace: TsNamespaceSignature? = null, // TODO: TsNamespaceSignature
) {
    override fun toString(): String {
        return if (namespace != null) {
            "$namespace::$name"
        } else {
            "$file: $name"
        }
    }

    companion object {
        val UNKNOWN = TsClassSignature(
            name = UNKNOWN_CLASS_NAME,
            file = TsFileSignature.UNKNOWN,
        )
    }
}

data class TsFieldSignature(
    val enclosingClass: TsClassSignature,
    val name: String,
    val type: TsType,
) {
    override fun toString(): String {
        return "${enclosingClass.name}::$name: $type"
    }
}

data class TsMethodSignature(
    // TODO: rename to 'declaringClass' to distinguish from 'enclosingClass' in Method
    val enclosingClass: TsClassSignature,
    val name: String,
    val parameters: List<TsMethodParameter>,
    val returnType: TsType,
) {
    override fun toString(): String {
        val params = parameters.joinToString()
        return "${enclosingClass.name}::$name($params): $returnType"
    }
}

data class TsMethodParameter(
    val index: Int,
    val name: String,
    val type: TsType,
    val isOptional: Boolean = false,
    val isRest: Boolean = false,
) {
    override fun toString(): String {
        return "$name${if (isOptional) "?" else ""}: $type"
    }
}
