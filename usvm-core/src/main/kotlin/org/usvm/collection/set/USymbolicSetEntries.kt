package org.usvm.collection.set

/**
 * All symbolic set entries (added and removed).
 * Set entries marked as [isInput] when:
 * 1. The original set is input
 * 2. The original set is concrete, but has been united with an input set.
 * */
class USymbolicSetEntries<Entry> {
    private val _entries: MutableSet<Entry> = hashSetOf()
    val entries: Set<Entry>
        get() = _entries

    var isInput: Boolean = false
        private set

    fun add(entry: Entry) {
        _entries.add(entry)
    }

    fun markAsInput() {
        isInput = true
    }
}
