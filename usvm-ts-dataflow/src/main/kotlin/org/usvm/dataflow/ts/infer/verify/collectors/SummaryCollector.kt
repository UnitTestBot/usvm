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

import org.jacodb.ets.base.EtsAnnotationNamespaceType
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsRefType
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.model.EtsMethodSignature
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.toBase

data class TypeError(
    val value: EtsValue,
    val enclosingExpr: EtsEntity,
    val description: String
)

data class EntityVerificationSummary(
    val types: MutableSet<EtsType>,
    val errors: MutableSet<TypeError>
) {
    companion object {
        fun empty(): EntityVerificationSummary = EntityVerificationSummary(types = hashSetOf(), errors = hashSetOf())
    }
}

data class MethodVerificationSummary(
    val entitySummaries: MutableMap<AccessPathBase, EntityVerificationSummary> = mutableMapOf()
)

interface SummaryCollector {
    val method: EtsMethodSignature
    val verificationSummary: MethodVerificationSummary

    fun yield(parameter: EtsParameterRef) {
        if (!parameter.type.isUnresolved) {
            verificationSummary.entitySummaries
                .computeIfAbsent(AccessPathBase.Arg(parameter.index)) { EntityVerificationSummary.empty() }
                .types
                .add(parameter.type)
        }
    }

    fun yield(local: EtsLocal) {
        if (!local.type.isUnresolved) {
            verificationSummary.entitySummaries
                .computeIfAbsent(AccessPathBase.Local(local.name)) { EntityVerificationSummary.empty() }
                .types
                .add(local.type)
        }
    }

    fun yield(etsThis: EtsThis) {
        verificationSummary.entitySummaries
            .computeIfAbsent(AccessPathBase.This) { EntityVerificationSummary.empty() }
            .types
            .add(etsThis.type)
    }

    fun requireObjectOrUnknown(value: EtsValue, enclosingExpr: EtsEntity) {
        if (!value.type.isUnresolved && value.type !is EtsRefType && value.type !is EtsAnnotationNamespaceType) {
            verificationSummary.entitySummaries
                .computeIfAbsent(value.toBase()) { EntityVerificationSummary.empty() }
                .errors
                .add(TypeError(value, enclosingExpr, "$value type should be a reference type, found ${value.type}"))
        }
    }
}
