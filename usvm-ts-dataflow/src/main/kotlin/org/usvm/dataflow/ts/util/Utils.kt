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

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsType
import org.usvm.dataflow.ts.infer.EtsTypeFact

fun EtsType.unwrapPromise(): EtsType {
    if (this is EtsClassType) {
        if (this.signature.name == "Promise" && this.typeParameters.isNotEmpty()) {
            return this.typeParameters[0]
        }
    }
    return this
}

/**
 * Ad-hoc utility to convert Any to Unknown. Use before `intersect`.
 *
 * This is necessary because the intersection with Any is Any.
 */
fun EtsTypeFact.fixAnyToUnknown(): EtsTypeFact =
    if (this is EtsTypeFact.AnyEtsTypeFact) {
        EtsTypeFact.UnknownEtsTypeFact
    } else {
        this
    }

/**
 * Convert a type to a string, but limit its length.
 */
fun <T> T.toStringLimited(): String {
    // TODO: customize the limits
    val s = toString()
    return if (s.length > 100) {
        s.substring(0, 50) + "..."
    } else {
        s
    }
}

val EtsClass.type: EtsClassType
    get() = EtsClassType(signature, typeParameters)
