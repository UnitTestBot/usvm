package org.usvm.api.util

@Suppress("UNCHECKED_CAST")
// NOTE: this prettifier DOES NOT cover all the possible cases
fun Any?.prettify(): String {
    return when {
        this == null -> toString()
        this::class.java.isArray -> {
            when (this::class.java.componentType) {
                Long::class.java -> (this as LongArray).contentToString()
                Int::class.java -> (this as IntArray).contentToString()
                Char::class.java -> (this as CharArray).contentToString()
                Short::class.java -> (this as ShortArray).contentToString()
                Byte::class.java -> (this as ByteArray).contentToString()
                Double::class.java -> (this as DoubleArray).contentToString()
                Float::class.java -> (this as FloatArray).contentToString()
                Boolean::class.java -> (this as BooleanArray).contentToString()
                else -> (this as Array<out Any>).contentDeepToString()
            }
        }
        this is Collection<*> -> joinToString(prefix = "[", postfix = "]") { it.prettify() }
        this is Map<*, *> -> entries.joinToString(prefix = "{", postfix = "}") {
            "${it.key.prettify()}: ${it.value.prettify()}"
        }
        this is Result<*> -> fold(
            onSuccess = { it.prettify() },
            onFailure = { it.prettify() },
        )
        else -> toString()
    }
}
