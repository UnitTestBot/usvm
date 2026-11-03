package org.usvm.util

import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsMethod

internal fun EtsField.hasStringName(name: String): Boolean =
    signature.wellKnownSymbol == null && this.name == name

internal fun EtsMethod.hasStringName(name: String): Boolean =
    signature.wellKnownSymbol == null && this.name == name

internal fun EtsFieldSignature.requireStringKey() {
    if (wellKnownSymbol != null) {
        throw UnsupportedOperationException("Symbol-keyed field storage is not supported: $wellKnownSymbol")
    }
}
