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

import org.jacodb.ets.base.EtsType
import org.jacodb.ets.model.EtsScene
import org.usvm.dataflow.ts.infer.verify.collectors.ClassSummaryCollector

fun collectSummary(scene: EtsScene): Map<EntityId, Set<EtsType>> =
    ClassSummaryCollector(mutableMapOf()).apply {
        scene.projectAndSdkClasses.forEach { collect(it) }
    }.typeSummary

fun verify(scene: EtsScene) = VerificationResult.from(collectSummary(scene))
