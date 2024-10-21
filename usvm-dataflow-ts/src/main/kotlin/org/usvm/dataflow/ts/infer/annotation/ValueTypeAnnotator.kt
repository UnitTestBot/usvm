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

package org.usvm.dataflow.ts.infer.annotation

import org.jacodb.ets.base.*
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsScene
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.dto.toType

class ValueTypeAnnotator(
    private val types: Map<AccessPathBase, EtsTypeFact>,
    private val thisType: EtsTypeFact?,
    private val scene: EtsScene
) : EtsValue.Visitor.Default<EtsValue> {
    private inline fun <V, reified T: EtsType> V.infer(base: AccessPathBase, apply: V.(T) -> V): V {
        val type = types[base]?.toType() as? T ?: return this
        return apply.invoke(this, type)
    }

    override fun visit(value: EtsLocal): EtsLocal =
        value.infer<EtsLocal, EtsType>(AccessPathBase.Local(value.name)) { copy(type = it) }

    override fun visit(value: EtsThis): EtsValue =
        (thisType?.toType() as? EtsClassType)?.let { value.copy(type = it) }
            ?: value.infer<EtsThis, EtsClassType>(AccessPathBase.This) { copy(type = it) }

    override fun visit(value: EtsParameterRef) =
        value.infer<EtsParameterRef, EtsType>(AccessPathBase.Arg(value.index)) { copy(type = it) }

    override fun visit(value: EtsArrayAccess): EtsArrayAccess {
        val arrayInferred = value.array.accept(this)
        val arrayType = arrayInferred.type as? EtsArrayType ?: return value
        val indexInferred = value.index.accept(this)
        return EtsArrayAccess(arrayInferred, indexInferred, arrayType.elementType)
    }

    // TODO: discuss (labeled with (Q))
    override fun visit(value: EtsInstanceFieldRef): EtsValue {
        val instance = visit(value.instance)
        val name = value.field.name

        fun findInClass(signature: EtsClassSignature) = scene.classes
            .singleOrNull { it.signature == signature }
            ?.fields
            ?.singleOrNull { it.name == name }

        // Try determine field type using the scene
        // (Q) Do we really need this step?
        with(value.field) {
            val etsField = findInClass(enclosingClass) ?: return@with
            // Field was found in the scene

            // Check that inferred type is same with the declared one
            // (Q) How should we (do we should?) handle the check violation here?
            check(instance.type == EtsClassType(enclosingClass))

            return EtsInstanceFieldRef(instance = instance, field = etsField.signature)
        }

        // Field was not found by signature, then try infer instance type
        val instanceTypeInfo = types[AccessPathBase.Local(instance.name)] as? EtsTypeFact.ObjectEtsTypeFact
            // Instance type was neither specified in signature nor inferred, so no type info can be provided
            // (Q) Should we check special properties of primitives (like `string.length`)?
            ?: return value.copy(instance = instance)

        // Find field signature in inferred class fields
        (instanceTypeInfo.cls as? EtsClassType)?.run {
            val etsField = findInClass(classSignature) ?: return@run
            // Field was found in the inferred class
            return EtsInstanceFieldRef(instance = instance, field = etsField.signature)
        }

        // Find field type in inferred fields - we don't know precisely the base class, but can infer the field type
        instanceTypeInfo.properties[name]?.toType()?.let { fieldType ->
            val fieldSubSignature = value.field.sub.copy(type = fieldType)
            // (Q) General: Should we use our inferred type if there is a non-trivial type in the original scene?
            return EtsInstanceFieldRef(instance = instance, field = value.field.copy(sub = fieldSubSignature))
        }

        return value.copy(instance = instance)
    }

    override fun visit(value: EtsStaticFieldRef): EtsValue = value

    override fun defaultVisit(value: EtsValue): EtsValue = value
}
