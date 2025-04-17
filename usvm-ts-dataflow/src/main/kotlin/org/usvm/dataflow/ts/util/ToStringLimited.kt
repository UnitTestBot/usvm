/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.usvm.dataflow.ts.util

import org.jacodb.ets.model.EtsIntersectionType
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnionType
import org.usvm.dataflow.ts.infer.EtsTypeFact

fun EtsType.toStringLimited(): String {
    return accept(ToStringLimited)
}

private const val LIMIT_SOFT = 100
private const val LIMIT_HARD = 50

private fun String.limit(soft: Int = LIMIT_SOFT, hard: Int = LIMIT_HARD): String {
    return if (length <= soft) {
        this
    } else {
        substring(0, hard) + "…"
    }
}

private object ToStringLimited : EtsType.Visitor.Default<String> {
    override fun defaultVisit(type: EtsType): String {
        return type.toString().limit()
    }

    override fun visit(type: EtsUnionType): String {
        return type.types.joinToString(" | ") {
            it.accept(this)
        }.limit()
    }

    override fun visit(type: EtsIntersectionType): String {
        return type.types.joinToString(" | ") {
            it.accept(this)
        }.limit()
    }

    override fun visit(type: EtsTupleType): String {
        return type.types.joinToString(", ", prefix = "[", postfix = "]") {
            it.accept(this)
        }.limit()
    }
}

private const val MAX_TYPES = 5

fun EtsTypeFact.toStringLimited(): String = when (this) {
    is EtsTypeFact.UnionEtsTypeFact -> {
        if (types.size <= MAX_TYPES) {
            types.joinLimitedMaybeBraced(" | ")
        } else {
            types.take(MAX_TYPES).joinLimitedMaybeBraced(" | ") + " | ${types.size - MAX_TYPES} more…"
        }
    }

    is EtsTypeFact.IntersectionEtsTypeFact -> {
        if (types.size <= MAX_TYPES) {
            types.joinLimitedMaybeBraced(" & ")
        } else {
            types.take(MAX_TYPES).joinLimitedMaybeBraced(" & ") + " & ${types.size - MAX_TYPES} more…"
        }
    }

    else -> toString().limit()
}

private fun Iterable<EtsTypeFact>.joinLimitedMaybeBraced(separator: String): String {
    return joinToString(separator) {
        val s = it.toStringLimited()
        if (it is EtsTypeFact.UnionEtsTypeFact || it is EtsTypeFact.IntersectionEtsTypeFact) {
            "($s)"
        } else {
            s
        }
    }
}
