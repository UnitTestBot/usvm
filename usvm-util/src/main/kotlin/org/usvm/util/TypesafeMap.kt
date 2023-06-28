package org.usvm.util

class TypesafeMapKey<T>

class TypesafeMap {
    private val elements = mutableMapOf<Class<Any>, Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> addByClass(cls: Class<T>, value: T) {
        elements[cls as Class<Any>] = value as Any?
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getByClass(cls: Class<T>): T? {
        return elements[cls as Class<Any>] as T?
    }
}

inline fun <reified T> TypesafeMap.add(value: T) {
    this.addByClass(T::class.java, value)
}

inline fun <reified T> TypesafeMap.get(): T? {
    return this.getByClass(T::class.java)
}
