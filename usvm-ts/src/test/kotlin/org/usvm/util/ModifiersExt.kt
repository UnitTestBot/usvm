package org.usvm.util

import org.jacodb.ets.model.EtsModifier
import org.jacodb.ets.model.EtsModifiers

fun EtsModifiers.toStrings(): List<String> {
    return EtsModifier.entries.filter { hasModifier(it) }.map { it.string }
}
