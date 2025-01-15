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

package org.usvm.dataflow.ts.infer.verify.collectors

import org.jacodb.ets.base.EtsType
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.dataflow.ts.infer.verify.EntityId
import org.usvm.dataflow.ts.infer.verify.FieldId
import org.usvm.dataflow.ts.infer.verify.ParameterId
import org.usvm.dataflow.ts.infer.verify.ReturnId

interface SummaryCollector {
    val typeSummary: MutableMap<EntityId, MutableSet<EtsType>>

    fun yield(field: EtsFieldSignature) {
        if (!field.type.isUnresolved) {
            typeSummary
                .computeIfAbsent(FieldId(field)) { hashSetOf() }
                .add(field.type)
        }
    }

    fun yield(method: EtsMethodSignature) {
        if (!method.returnType.isUnresolved) {
            typeSummary
                .computeIfAbsent(ReturnId(method)) { hashSetOf() }
                .add(method.returnType)
        }
        method.parameters.forEach {
            if (!it.type.isUnresolved) {
                typeSummary
                    .computeIfAbsent(ParameterId(it, method)) { hashSetOf() }
                    .add(it.type)
            }
        }
    }
}
