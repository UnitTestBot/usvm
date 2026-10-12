package org.usvm.interpreter

/** A lossless witness when JVM Map equality would collapse distinct Go map entries. */
data class GoMapEntries(val entries: List<GoMapEntry>) {
    val size: Int get() = entries.size
}

/** Identity is stable across the before/after witnesses of one symbolic execution. */
data class GoMapEntry(val key: Any?, val value: Any?, val identity: String)
