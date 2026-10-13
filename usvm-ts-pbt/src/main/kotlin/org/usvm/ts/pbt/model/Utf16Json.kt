package org.usvm.ts.pbt.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Encodes JSON for UTF-8 transport without replacing JavaScript's isolated UTF-16 surrogates. */
inline fun <reified T> Json.encodeToUtf8SafeString(value: T): String =
    escapeJsonSurrogates(encodeToString(value))

/**
 * Escapes surrogate code units in an already encoded JSON document. All other JSON text is unchanged.
 * Escaping paired units too preserves their value and avoids making transport depend on UTF-8 error handling.
 * Keep this at the text boundary: JsonElement string contents must retain their original UTF-16 value.
 */
fun escapeJsonSurrogates(json: String): String {
    if (json.none { it.isSurrogate() }) return json

    return buildString {
        json.forEach { unit ->
            if (unit.isSurrogate()) {
                append("\\u")
                append(unit.code.toString(radix = 16).padStart(length = 4, padChar = '0'))
            } else {
                append(unit)
            }
        }
    }
}
