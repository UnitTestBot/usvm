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

import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsValue
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.toBase
import org.usvm.dataflow.ts.infer.tryGetKnownType

data class TypeError(
    val value: EtsValue,
    val enclosingExpr: EtsEntity,
    val description: String,
)

data class EntityVerificationSummary(
    val types: MutableSet<EtsType>,
    val errors: MutableSet<TypeError>,
) {
    companion object {
        fun empty(): EntityVerificationSummary = EntityVerificationSummary(types = hashSetOf(), errors = hashSetOf())
    }
}

data class MethodVerificationSummary(
    val entitySummaries: MutableMap<AccessPathBase, EntityVerificationSummary> = mutableMapOf(),
)

interface SummaryCollector {
    val method: EtsMethod
    val verificationSummary: MethodVerificationSummary

    fun yield(parameter: EtsParameterRef) {
        val type = parameter.tryGetKnownType(method)
        if (!type.isUnresolved) {
            verificationSummary.entitySummaries
                .computeIfAbsent(AccessPathBase.Arg(parameter.index)) { EntityVerificationSummary.empty() }
                .types
                .add(type)
        }
    }

    fun yield(local: EtsLocal) {
        val type = local.type
        if (!type.isUnresolved) {
            verificationSummary.entitySummaries
                .computeIfAbsent(AccessPathBase.Local(local.name)) { EntityVerificationSummary.empty() }
                .types
                .add(type)
        }
    }

    fun yield(etsThis: EtsThis) {
        verificationSummary.entitySummaries
            .computeIfAbsent(AccessPathBase.This) { EntityVerificationSummary.empty() }
            .types
            .add(etsThis.tryGetKnownType(method))
    }

    fun requireObjectOrUnknown(value: EtsValue, enclosingExpr: EtsEntity) {
        val type = value.tryGetKnownType(method)
        if (!type.isUnresolved && type !is EtsRefType) {
            verificationSummary.entitySummaries
                .computeIfAbsent(value.toBase()) { EntityVerificationSummary.empty() }
                .errors
                .add(TypeError(value, enclosingExpr, "$value type should be a reference type, found $type"))
        }
    }
}
