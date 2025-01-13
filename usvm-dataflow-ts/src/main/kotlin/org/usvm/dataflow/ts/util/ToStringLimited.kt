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

import org.jacodb.ets.base.EtsTupleType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnionType
import org.usvm.dataflow.ts.infer.EtsTypeFact

fun EtsType.toStringLimited(): String {
    return accept(ToStringLimited)
}

private fun String.limit(): String {
    return if (length > 100) {
        substring(0, 50) + "..."
    } else {
        this
    }
}

private object ToStringLimited : EtsType.Visitor.Default<String> {
    override fun defaultVisit(type: EtsType): String {
        return type.toString().limit()
    }

    override fun visit(type: EtsUnionType): String {
        return type.types.joinToString(" | ") { it.accept(this) }.limit()
    }

    override fun visit(type: EtsTupleType): String {
        return type.types.joinToString(", ", prefix = "[", postfix = "]") { it.accept(this) }.limit()
    }
}

fun EtsTypeFact.toStringLimited(): String = when (this) {
    is EtsTypeFact.UnionEtsTypeFact -> types.joinToString(" | ") { it.toStringLimited() }
    is EtsTypeFact.IntersectionEtsTypeFact -> types.joinToString(" & ") { it.toStringLimited() }
    else -> toString().limit()
}
