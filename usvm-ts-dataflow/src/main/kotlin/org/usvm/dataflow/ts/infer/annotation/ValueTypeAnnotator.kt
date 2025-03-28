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

package org.usvm.dataflow.ts.infer.annotation

import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsValue
import org.usvm.dataflow.ts.infer.AccessPathBase

class ValueTypeAnnotator(
    private val scheme: MethodTypeScheme,
    private val thisType: EtsType?,
) : EtsValue.Visitor.Default<EtsValue> {

    private inline fun <V, reified T : EtsType> V.annotate(
        base: AccessPathBase,
        transform: V.(T) -> V,
    ): V {
        val type = scheme.typeOf(base) ?: return this
        if (type !is T) return this
        return transform(type)
    }

    override fun defaultVisit(value: EtsValue): EtsValue = value

    override fun visit(value: EtsLocal): EtsLocal =
        value.annotate<EtsLocal, EtsType>(AccessPathBase.Local(value.name)) { copy(type = it) }

    override fun visit(value: EtsThis): EtsValue = value
    // TODO: old code:
    //  (thisType as? EtsClassType)?.let { value.copy(type = it) }
    //      ?: value.annotate<EtsThis, EtsClassType>(AccessPathBase.This) { copy(type = it) }

    override fun visit(value: EtsParameterRef) = value
    // TODO: old code:
    //  value.annotate<EtsParameterRef, EtsType>(AccessPathBase.Arg(value.index)) { copy(type = it) }

    override fun visit(value: EtsArrayAccess): EtsArrayAccess {
        val arrayInferred = value.array.accept(this) as EtsLocal // safe cast
        val arrayType = arrayInferred.type as? EtsArrayType ?: return value
        val indexInferred = value.index.accept(this)
        return EtsArrayAccess(arrayInferred, indexInferred, arrayType.elementType)
    }

    // TODO: discuss (labeled with (Q))
    override fun visit(value: EtsInstanceFieldRef): EtsValue {
        val instance = visit(value.instance)
        return value.copy(instance = instance)
    }

    override fun visit(value: EtsStaticFieldRef): EtsValue = value
}
