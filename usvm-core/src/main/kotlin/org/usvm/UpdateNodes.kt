package org.usvm

import java.util.*

/**
 * Represents the result of memory write operation.
 *
 * Note that it should not be extended by external users. Otherwise, it would require
 * changes in processing of such nodes inside a core since we expect to have only two implementations:
 * [UPinpointUpdateNode] and [URangedUpdateNode].
 */
sealed interface UUpdateNode<Key, ValueSort : USort> {
    /**
     * @returns True if the address [key] got overwritten by this write operation in *any* possible concrete state.
     */
    fun includesConcretely(key: Key): Boolean

    /**
     * @return Symbolic condition expressing that the address [key] got overwritten by this memory write operation.
     * If [includesConcretely] returns true, then this method is obligated to return [UTrue].
     * Returned condition must imply [guard].
     */
    fun includesSymbolically(key: Key): UBoolExpr

    /**
     * @see [UMemoryRegion.split]
     */
    fun split(
        key: Key,
        predicate: (UExpr<ValueSort>) -> Boolean,
        matchingWrites: LinkedList<Pair<UBoolExpr, UExpr<ValueSort>>>,
        guardBuilder: GuardBuilder
    ): UUpdateNode<Key, ValueSort>?

    /**
     * @return Value which has been written into the address [key] during this memory write operation.
     */
    fun value(key: Key): UExpr<ValueSort>

    /**
     * Returns a mapped update node using [keyMapper] and [composer].
     * It is used in [UComposer] for composition.
     */
    fun <Field, Type> map(keyMapper: (Key) -> Key, composer: UComposer<Field, Type>): UUpdateNode<Key, ValueSort>

    /**
     * Guard is a symbolic condition for this update. That is, this update is done only in states satisfying this guard.
     */
    val guard: UBoolExpr

    /**
     * Returns node with updated [guard] condition.
     */
    fun guardWith(guard: UBoolExpr): UUpdateNode<Key, ValueSort>
}

/**
 * Represents a single write of [value] into a memory address [key]
 */
class UPinpointUpdateNode<Key, ValueSort : USort>(
    val key: Key,
    internal val value: UExpr<ValueSort>,
    private val keyEqualityComparer: (Key, Key) -> UBoolExpr,
    override val guard: UBoolExpr = value.ctx.trueExpr
) : UUpdateNode<Key, ValueSort> {
    override fun includesConcretely(key: Key) = this.key == key && guard == guard.ctx.trueExpr

    override fun includesSymbolically(key: Key): UBoolExpr =
        guard.ctx.mkAnd(keyEqualityComparer(this.key, key), guard) // TODO: use simplifying and!

    override fun value(key: Key): UExpr<ValueSort> = this.value

    override fun split(
        key: Key,
        predicate: (UExpr<ValueSort>) -> Boolean,
        matchingWrites: LinkedList<Pair<UBoolExpr, UExpr<ValueSort>>>,
        guardBuilder: GuardBuilder
    ): UUpdateNode<Key, ValueSort>? {
        val keyEq = keyEqualityComparer(key, this.key)
        val ctx = value.ctx
        val keyDiseq = ctx.mkNot(keyEq)

        if (predicate(value)) {
            val guard = ctx.mkAnd(guardBuilder.nonMatchingUpdatesGuard, keyEq)
            matchingWrites.add(Pair(guard, value))
            guardBuilder.matchingUpdatesGuard = ctx.mkAnd(guardBuilder.matchingUpdatesGuard, keyDiseq)
            return null
        }

        val hadNonMatchingUpdates = guardBuilder.nonMatchingUpdatesGuard != ctx.trueExpr
        guardBuilder.nonMatchingUpdatesGuard = ctx.mkAnd(guardBuilder.nonMatchingUpdatesGuard, ctx.mkNot(keyEq))

        return if (hadNonMatchingUpdates) this.guardWith(guardBuilder.matchingUpdatesGuard) else this
    }

    override fun <Field, Type> map(
        keyMapper: (Key) -> Key,
        composer: UComposer<Field, Type>
    ): UPinpointUpdateNode<Key, ValueSort> {
        val mappedKey = keyMapper(key)
        val mappedValue = composer.compose(value)
        val mappedGuard = composer.compose(guard)

        // If nothing changed, return this value
        if (mappedKey === key && mappedValue === value && mappedGuard === guard) {
            return this
        }

        // Otherwise, construct a new one update node
        return UPinpointUpdateNode(mappedKey, mappedValue, keyEqualityComparer, mappedGuard)
    }

    override fun guardWith(guard: UBoolExpr): UUpdateNode<Key, ValueSort> =
        if (guard == guard.ctx.trueExpr) {
            this
        } else {
            val guardExpr = guard.ctx.mkAnd(this.guard, guard)
            UPinpointUpdateNode(key, value, keyEqualityComparer, guardExpr)
        }

    override fun equals(other: Any?): Boolean =
        other is UPinpointUpdateNode<*, *> && this.key == other.key  // Ignores value

    override fun hashCode(): Int = key.hashCode()  // Ignores value

    override fun toString(): String = "{$key <- $value}"
}

/**
 * Represents a synchronous overwriting the range of addresses [[fromKey] : [toKey]]
 * with values from memory region [region] read from range
 * of addresses [[keyConverter] ([fromKey]) : [keyConverter] ([toKey])]
 */
class URangedUpdateNode<Key, ValueSort : USort>(
    val fromKey: Key,
    val toKey: Key,
    val region: UMemoryRegion<*, Key, ValueSort>,
    private val concreteComparer: (Key, Key) -> Boolean,
    private val symbolicComparer: (Key, Key) -> UBoolExpr,
    private val keyConverter: (Key) -> Key,
    override val guard: UBoolExpr = region.sort.ctx.trueExpr
) : UUpdateNode<Key, ValueSort> {
    override fun includesConcretely(key: Key): Boolean =
        concreteComparer(fromKey, key) && concreteComparer(key, toKey) && guard == guard.ctx.trueExpr

    override fun includesSymbolically(key: Key): UBoolExpr {
        val leftIsLefter = symbolicComparer(fromKey, key)
        val rightIsRighter = symbolicComparer(key, toKey)
        val ctx = leftIsLefter.ctx

        return ctx.mkAnd(leftIsLefter, rightIsRighter, guard) // TODO: use simplifying and!
    }

    override fun value(key: Key): UExpr<ValueSort> = region.read(keyConverter(key))

    override fun <Field, Type> map(
        keyMapper: (Key) -> Key,
        composer: UComposer<Field, Type>
    ): URangedUpdateNode<Key, ValueSort> {
        val mappedFromKey = keyMapper(fromKey)
        val mappedToKey = keyMapper(toKey)
        val mappedRegion = region.map(keyMapper, composer)
        // TODO: keyConverter should be composable object. If it is lambda (like now), we can't recalculate lower bounds
        //       in lambda closure
        // TODO when you fix it, do not forget to change  org.usvm.MapCompositionTest.testRangeUpdateNodeWithoutCompositionEffect
        //      org.usvm.MapCompositionTest.testRangeUpdateNodeMapOperation
        // val mappedKeyConverter = keyConverter.map
        val mappedGuard = composer.compose(guard)

        // If nothing changed, return this
        if (mappedFromKey === fromKey
            && mappedToKey === toKey
            && mappedRegion === region
            /* && mappedKeyConverter === keyConverter */
            && mappedGuard === guard
        ) {
            return this
        }

        // Otherwise, construct a new one updated node
        return URangedUpdateNode(
            mappedFromKey,
            mappedToKey,
            mappedRegion,
            concreteComparer,
            symbolicComparer,
            /*mapped*/keyConverter,
            mappedGuard
        )
    }

    override fun guardWith(guard: UBoolExpr): UUpdateNode<Key, ValueSort> =
        if (guard == guard.ctx.trueExpr) {
            this
        } else {
            val guardExpr = guard.ctx.mkAnd(this.guard, guard)
            URangedUpdateNode(fromKey, toKey, region, concreteComparer, symbolicComparer, keyConverter, guardExpr)
        }

    override fun equals(other: Any?): Boolean =
        other is URangedUpdateNode<*, *> && this.fromKey == other.fromKey && this.toKey == other.toKey  // Ignores update

    override fun hashCode(): Int = 31 * fromKey.hashCode() + toKey.hashCode()  // Ignores update

    override fun split(
        key: Key, predicate: (UExpr<ValueSort>) -> Boolean,
        matchingWrites: LinkedList<Pair<UBoolExpr, UExpr<ValueSort>>>,
        guardBuilder: GuardBuilder
    ): UUpdateNode<Key, ValueSort> {
        val splittedRegion = region.split(key, predicate, matchingWrites, guardBuilder)
        if (splittedRegion === region) {
            return this
        }

        return URangedUpdateNode(fromKey, toKey, splittedRegion, concreteComparer, symbolicComparer, keyConverter)
    }
}

