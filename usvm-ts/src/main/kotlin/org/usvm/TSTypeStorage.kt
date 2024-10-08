package org.usvm

import org.jacodb.ets.base.EtsType

/*
    This is a very basic implementation of type storage with memory and model objects interoperability.
    Currently, supports only stack register readings, but API-wise is finished.

    TODO: support other possibly untyped refs.
 */
class TSTypeStorage(
    private val ctx: TSContext,
    private val keyToTypes: MutableMap<Any, MutableSet<EtsType>> = mutableMapOf(),
) {

    fun storeSuggestedType(ref: UExpr<UAddressSort>, type: EtsType) {
        // TODO: finalize implementation and remove this.
        if (ref !is URegisterReading) error("Unsupported yet!")

        keyToTypes.getOrPut(ref.idx) {
            mutableSetOf()
        }.add(type)
    }

    fun getSuggestedType(key: Any): EtsType? = keyToTypes[key]?.first()

    fun clone(): TSTypeStorage = TSTypeStorage(ctx, keyToTypes.copy())
}
