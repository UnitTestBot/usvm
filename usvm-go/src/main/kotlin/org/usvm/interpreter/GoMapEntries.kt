package org.usvm.interpreter

/** A lossless witness when JVM Map equality would collapse distinct Go map entries. */
data class GoMapEntries(val entries: List<GoMapEntry>) {
    val size: Int get() = entries.size
}

/** Identity is stable across the before/after witnesses of one symbolic execution. */
data class GoMapEntry(val key: Any?, val value: Any?, val identity: String)

internal fun needsGoMapEntryIdentity(key: Any?): Boolean = when (key) {
    is Double -> key.isNaN() || key == 0.0 && key.toRawBits() < 0
    is Float -> key.isNaN() || key == 0.0f && key.toRawBits() < 0
    is GoPointer -> true
    is GoInterfaceValue -> needsGoMapEntryIdentity(key.value)
    is List<*> -> key.any(::needsGoMapEntryIdentity)
    is Map<*, *> -> key.values.any(::needsGoMapEntryIdentity)
    else -> false
}
