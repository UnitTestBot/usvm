/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.usvm.dataflow.ts.test.utils

import org.jacodb.ets.base.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsScene

fun EtsScene.getEtsClassType(signature: EtsClassSignature): EtsClassType? {
    if (signature.name == DEFAULT_ARK_CLASS_NAME || signature.name.isBlank()) {
        return null
    }

    val clazz = projectAndSdkClasses.firstOrNull { it.signature == signature }
        ?: error("No class found in the classpath with signature $signature")
    return EtsClassType(clazz.signature)
}

fun <T> T.toStringLimited(): String {
    val s = toString()
    return if (s.length > 100) {
        s.substring(0, 50) + "..."
    } else {
        s
    }
}
