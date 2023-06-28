@file:Suppress("unused", "UseWithIndex", "NOTHING_TO_INLINE")

package org.usvm.instrumentation.util

import java.util.*

fun <T> queueOf(vararg elements: T): Queue<T> = ArrayDeque(elements.toList())
fun <T> queueOf(elements: Collection<T>): Queue<T> = ArrayDeque(elements.toList())

fun <T> dequeOf(vararg elements: T): Deque<T> = ArrayDeque(elements.toList())
fun <T> dequeOf(elements: Collection<T>): Deque<T> = ArrayDeque(elements)

fun <T> Collection<T>.firstOrDefault(default: T): T = firstOrNull() ?: default
fun <T> Collection<T>.firstOrDefault(predicate: (T) -> Boolean, default: T): T = firstOrNull(predicate) ?: default

fun <T> Collection<T>.firstOrElse(action: () -> T): T = firstOrNull() ?: action()
fun <T> Collection<T>.firstOrElse(predicate: (T) -> Boolean, action: () -> T): T = firstOrNull(predicate) ?: action()

fun <T> Collection<T>.lastOrDefault(default: T): T = lastOrNull() ?: default
fun <T> Collection<T>.lastOrDefault(predicate: (T) -> Boolean, default: T): T = lastOrNull(predicate) ?: default

fun <T> Collection<T>.lastOrElse(action: () -> T): T = lastOrNull() ?: action()
fun <T> Collection<T>.lastOrElse(predicate: (T) -> Boolean, action: () -> T): T = lastOrNull(predicate) ?: action()

fun <T> MutableList<T>.replace(replace: T, replacement: T): Boolean {
    with(indexOf(replace)) {
        return if (this == -1)  {
            false
        } else {
            removeAt(this)
            add(this, replacement)
            true
        }
    }
}


inline fun <A, reified B> Collection<A>.mapToArray(body: (A) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <A> Collection<A>.mapToBooleanArray(body: (A) -> Boolean): BooleanArray {
    val arr = BooleanArray(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A> Collection<A>.mapToCharArray(body: (A) -> Char): CharArray {
    val arr = CharArray(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A> Collection<A>.mapToByteArray(body: (A) -> Byte): ByteArray {
    val arr = ByteArray(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A> Collection<A>.mapToShortArray(body: (A) -> Short): ShortArray {
    val arr = ShortArray(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A> Collection<A>.mapToIntArray(body: (A) -> Int): IntArray {
    val arr = IntArray(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A> Collection<A>.mapToLongArray(body: (A) -> Long): LongArray {
    val arr = LongArray(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A> Collection<A>.mapToFloatArray(body: (A) -> Float): FloatArray {
    val arr = FloatArray(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A> Collection<A>.mapToDoubleArray(body: (A) -> Double): DoubleArray {
    val arr = DoubleArray(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A, reified B> Array<A>.mapToArray(body: (A) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> BooleanArray.mapToArray(body: (Boolean) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> CharArray.mapToArray(body: (Char) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> ShortArray.mapToArray(body: (Short) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> ByteArray.mapToArray(body: (Byte) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> IntArray.mapToArray(body: (Int) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> LongArray.mapToArray(body: (Long) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> FloatArray.mapToArray(body: (Float) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> DoubleArray.mapToArray(body: (Double) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) arr[i++] = body(e)
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}


inline fun <A, reified B> Collection<A>.mapIndexedToArray(body: (index: Int, element: A) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <A> Collection<A>.mapIndexedToBooleanArray(body: (index: Int, element: A) -> Boolean): BooleanArray {
    val arr = BooleanArray(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A> Collection<A>.mapIndexedToCharArray(body: (index: Int, element: A) -> Char): CharArray {
    val arr = CharArray(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A> Collection<A>.mapIndexedToByteArray(body: (index: Int, element: A) -> Byte): ByteArray {
    val arr = ByteArray(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A> Collection<A>.mapIndexedToShortArray(body: (index: Int, element: A) -> Short): ShortArray {
    val arr = ShortArray(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A> Collection<A>.mapIndexedToIntArray(body: (index: Int, element: A) -> Int): IntArray {
    val arr = IntArray(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A> Collection<A>.mapIndexedToLongArray(body: (index: Int, element: A) -> Long): LongArray {
    val arr = LongArray(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A> Collection<A>.mapIndexedToFloatArray(body: (index: Int, element: A) -> Float): FloatArray {
    val arr = FloatArray(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A> Collection<A>.mapIndexedToDoubleArray(body: (index: Int, element: A) -> Double): DoubleArray {
    val arr = DoubleArray(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr

}

inline fun <A, reified B> Array<A>.mapIndexedToArray(body: (index: Int, element: A) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> BooleanArray.mapIndexedToArray(body: (index: Int, element: Boolean) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> CharArray.mapIndexedToArray(body: (index: Int, element: Char) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> ShortArray.mapIndexedToArray(body: (index: Int, element: Short) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> ByteArray.mapIndexedToArray(body: (index: Int, element: Byte) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> IntArray.mapIndexedToArray(body: (index: Int, element: Int) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> LongArray.mapIndexedToArray(body: (index: Int, element: Long) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> FloatArray.mapIndexedToArray(body: (index: Int, element: Float) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}

inline fun <reified B> DoubleArray.mapIndexedToArray(body: (index: Int, element: Double) -> B): Array<B> {
    val arr = arrayOfNulls<B>(size)
    var i = 0
    for (e in this) {
        arr[i] = body(i, e)
        i++
    }
    @Suppress("UNCHECKED_CAST")
    return arr as Array<B>
}


inline fun <A, K, V, R : MutableMap<K, V>> Collection<A>.mapTo(result: R, body: (A) -> Pair<K, V>?): R {
    for (element in this) {
        val transformed = body(element) ?: continue
        result += transformed
    }
    return result
}

inline fun <A, K, V, R : MutableMap<K, V>> Collection<A>.mapNotNullTo(result: R, body: (A) -> Pair<K, V>?): R {
    for (element in this) {
        val transformed = body(element) ?: continue
        result += transformed
    }
    return result
}

inline fun <A, K, V, R : MutableMap<K, V>> Collection<A>.mapIndexedTo(result: R, body: (Int, A) -> Pair<K, V>?): R {
    for (element in this.withIndex()) {
        val transformed = body(element.index, element.value) ?: continue
        result += transformed
    }
    return result
}

inline fun <A, K, V, R : MutableMap<K, V>> Collection<A>.mapIndexedNotNullTo(
    result: R,
    body: (Int, A) -> Pair<K, V>?
): R {
    for (element in this.withIndex()) {
        val transformed = body(element.index, element.value) ?: continue
        result += transformed
    }
    return result
}

inline fun <A, B> Iterable<A>.zipToMap(that: Iterable<B>): Map<A, B> {
    val result = mutableMapOf<A, B>()
    val thisIt = this.iterator()
    val thatIt = that.iterator()
    while (thisIt.hasNext() && thatIt.hasNext()) {
        result[thisIt.next()] = thatIt.next()
    }
    return result
}

inline fun <A, B, C, D> Iterable<A>.zipTo(that: Iterable<B>, transform: (A, B) -> Pair<C, D>): Map<C, D> {
    val result = mutableMapOf<C, D>()
    val thisIt = this.iterator()
    val thatIt = that.iterator()
    while (thisIt.hasNext() && thatIt.hasNext()) {
        result += transform(thisIt.next(), thatIt.next())
    }
    return result
}

inline fun <A, B, R : MutableMap<A, B>> Iterable<A>.zipTo(that: Iterable<B>, result: R): R {
    val thisIt = this.iterator()
    val thatIt = that.iterator()
    while (thisIt.hasNext() && thatIt.hasNext()) {
        result[thisIt.next()] = thatIt.next()
    }
    return result
}

inline fun <A, B, C, D, R : MutableMap<C, D>> Iterable<A>.zipTo(
    that: Iterable<B>,
    result: R,
    transform: (A, B) -> Pair<C, D>
): R {
    val thisIt = this.iterator()
    val thatIt = that.iterator()
    while (thisIt.hasNext() && thatIt.hasNext()) {
        result += transform(thisIt.next(), thatIt.next())
    }
    return result
}

inline fun <A, B, R, C : MutableCollection<R>> Iterable<A>.zipTo(that: Iterable<B>, to: C, transform: (A, B) -> R): C {
    val thisIt = this.iterator()
    val thatIt = that.iterator()
    while (thisIt.hasNext() && thatIt.hasNext()) {
        to.add(transform(thisIt.next(), thatIt.next()))
    }
    return to
}

fun <T, R : Comparable<R>> List<T>.filterDuplicatesBy(f: (T) -> R): List<T> {
    val list1 = this.zip(this.map(f))
    val res = mutableListOf<Pair<T, R>>()
    for (i in 0 until size) {
        val item = list1[i].second
        if (res.all { it.second != item }) res.add(list1[i])
    }
    return res.map { it.first }
}
