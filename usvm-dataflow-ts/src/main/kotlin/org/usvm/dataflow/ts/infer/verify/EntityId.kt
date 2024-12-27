/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.usvm.dataflow.ts.infer.verify

import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature

sealed interface EntityId

data class ClassId(
    val signature: EtsClassSignature,
) : EntityId

data class MethodId(
    val name: String,
    val enclosingClass: ClassId,
) {
    constructor(signature: EtsMethodSignature)
        : this(signature.name, ClassId(signature.enclosingClass))
}

data class FieldId(
    val name: String,
    val enclosingClass: ClassId,
) : EntityId {
    constructor(signature: EtsFieldSignature)
        : this(signature.name, ClassId(signature.enclosingClass))
}

data class ParameterId(
    val index: Int,
    val method: MethodId,
) : EntityId {
    constructor(parameter: EtsMethodParameter, methodSignature: EtsMethodSignature)
        : this(parameter.index, MethodId(methodSignature))

    constructor(etsParameterRef: EtsParameterRef, methodSignature: EtsMethodSignature)
        : this(etsParameterRef.index, MethodId(methodSignature))
}

data class ReturnId(
    val method: MethodId,
) : EntityId {
    constructor(methodSignature: EtsMethodSignature)
        : this(MethodId(methodSignature))
}

data class LocalId(
    val name: String,
    val method: MethodId,
) : EntityId {
    constructor(local: EtsLocal, methodSignature: EtsMethodSignature)
        : this(local.name, MethodId(methodSignature))
}

data class ThisId(
    val method: MethodId,
) : EntityId {
    constructor(methodSignature: EtsMethodSignature)
        : this(MethodId(methodSignature))
}
