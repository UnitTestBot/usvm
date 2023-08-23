package org.usvm.memory

import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.util.Region

/**
 * Provides information about entities used as keys of symbolic collections.
 */
interface USymbolicCollectionKeyInfo<Key, Reg: Region<Reg>> {
    /**
     * Returns symbolic expression guaranteeing that [key1] is same as [key2].
     */
    fun eqSymbolic(ctx: UContext, key1: Key, key2: Key): UBoolExpr

    /**
     * Returns if [key1] is same as [key2] in all possible models.
     */
    fun eqConcrete(key1: Key, key2: Key): Boolean

    /**
     * Returns symbolic expression guaranteeing that [key1] is less or equal to [key2].
     * Assumes that [Key] domain is linearly ordered.
     */
    fun cmpSymbolic(ctx: UContext, key1: Key, key2: Key): UBoolExpr

    /**
     * Returns if [key1] is less or equal to [key2] in all possible models.
     * Assumes that [Key] domain is linearly ordered.
     */
    fun cmpConcrete(key1: Key, key2: Key): Boolean

    /**
     * Returns region that over-approximates the possible values of [key].
     */
    fun keyToRegion(key: Key): Reg

    /**
     * Returns region that over-approximates the range of indices [[from] .. [to]]
     */
    fun keyRangeRegion(from: Key, to: Key): Reg

    /**
     * Returns region that represents any possible key.
     */

    fun topRegion(): Reg

    /**
     * Returns region that represents empty set of keys.
     */
    fun bottomRegion(): Reg
}
