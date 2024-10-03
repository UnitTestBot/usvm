package org.usvm.util

/**
 * A write-only list which converts all the elements added into it with [converter] and storing
 * all the converted elements into [baseList].
 */
class ProxyList<Src, Dst>(
    val baseList: MutableList<Dst>,
    val converter: (Src) -> Dst
): MutableList<Src> {
    override val size: Int
        get() = baseList.size

    override fun clear() {
        baseList.clear()
    }

    override fun addAll(elements: Collection<Src>): Boolean {
        elements.forEach(::add)
        return elements.isNotEmpty()
    }

    override fun addAll(index: Int, elements: Collection<Src>): Boolean {
        var idx = index
        for (element in elements) {
            add(idx++, element)
        }
        return elements.isNotEmpty()
    }

    override fun add(index: Int, element: Src) =
        baseList.add(index, converter(element))

    override fun add(element: Src): Boolean =
        baseList.add(converter(element))

    override fun get(index: Int): Src =
        error("Not designed for this operation")

    override fun isEmpty(): Boolean =
        baseList.isEmpty()

    override fun iterator(): MutableIterator<Src> =
        error("Not designed for this operation")

    override fun listIterator(): MutableListIterator<Src> =
        error("Not designed for this operation")

    override fun listIterator(index: Int): MutableListIterator<Src> =
        error("Not designed for this operation")

    override fun removeAt(index: Int): Src =
        error("Not designed for this operation")

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<Src> =
        ProxyList(baseList.subList(fromIndex, toIndex), converter)

    override fun set(index: Int, element: Src): Src =
        error("Not designed for this operation")

    override fun retainAll(elements: Collection<Src>): Boolean =
        baseList.retainAll(elements.map(converter))

    override fun removeAll(elements: Collection<Src>): Boolean =
        baseList.removeAll(elements.map(converter))

    override fun remove(element: Src): Boolean =
        baseList.remove(converter(element))

    override fun lastIndexOf(element: Src): Int =
        baseList.lastIndexOf(converter(element))

    override fun indexOf(element: Src): Int =
        baseList.indexOf(converter(element))

    override fun containsAll(elements: Collection<Src>): Boolean =
        elements.all(::contains)

    override fun contains(element: Src): Boolean =
        baseList.contains(converter(element))
}