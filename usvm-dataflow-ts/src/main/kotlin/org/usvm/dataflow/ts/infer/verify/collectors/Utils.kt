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

import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsArrayType
import org.jacodb.ets.base.EtsTupleType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnionType
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.model.EtsScene
import org.usvm.dataflow.ts.infer.verify.EntityId

val EtsType.isUnresolved: Boolean
    get() = when (this) {
        is EtsAnyType -> true
        is EtsUnknownType -> true
        is EtsUnionType -> types.any { it.isUnresolved }
        is EtsTupleType -> types.any { it.isUnresolved }
        is EtsArrayType -> elementType.isUnresolved
        else -> false
    }

fun collectSummary(scene: EtsScene): Map<EntityId, Set<EtsType>> {
    val collector = ClassSummaryCollector(hashMapOf())
    scene.projectAndSdkClasses.forEach {
        collector.collect(it)
    }
    return collector.typeSummary
}
