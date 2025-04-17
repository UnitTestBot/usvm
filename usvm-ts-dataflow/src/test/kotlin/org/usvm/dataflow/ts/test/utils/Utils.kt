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

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME

fun EtsScene.getEtsClassType(signature: EtsClassSignature): EtsClassType? {
    if (signature.name == DEFAULT_ARK_CLASS_NAME || signature.name.isBlank()) {
        return null
    }

    val clazz = projectAndSdkClasses.firstOrNull { it.signature == signature }
        ?: error("No class found in the classpath with signature $signature")
    return EtsClassType(clazz.signature)
}
