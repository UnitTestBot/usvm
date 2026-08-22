package org.usvm.util

import org.jacodb.ets.model.EtsMethod

internal fun EtsMethod.executableOverloadImplementation(): EtsMethod {
    if (cfg.stmts.isNotEmpty()) return this

    return enclosingClass
        ?.methods
        ?.filter { candidate ->
            candidate.name == name &&
                candidate.isStatic == isStatic &&
                candidate.cfg.stmts.isNotEmpty()
        }
        ?.singleOrNull()
        ?: this
}

internal fun Iterable<EtsMethod>.canonicalizeExecutableOverloads(): List<EtsMethod> =
    map { it.executableOverloadImplementation() }.distinct()
