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

package org.usvm.dataflow.ts.infer.verify.collectors

import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.dataflow.ts.infer.verify.LocalId
import org.usvm.dataflow.ts.infer.verify.ParameterId
import org.usvm.dataflow.ts.infer.verify.ThisId

interface MethodSummaryCollector : SummaryCollector {
    val enclosingMethod: EtsMethodSignature

    fun yield(parameter: EtsParameterRef) {
        if (!parameter.type.isUnresolved) {
            typeSummary.getOrPut(ParameterId(parameter, enclosingMethod), ::mutableSetOf)
                .add(parameter.type)
        }
    }

    fun yield(local: EtsLocal) {
        if (!local.type.isUnresolved) {
            typeSummary.getOrPut(LocalId(local, enclosingMethod), ::mutableSetOf)
                .add(local.type)
        }
    }

    fun yield(etsThis: EtsThis) {
        typeSummary.getOrPut(ThisId(enclosingMethod), ::mutableSetOf)
            .add(etsThis.type)
    }
}
