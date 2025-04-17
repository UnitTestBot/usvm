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

package org.usvm.dataflow.ts.infer.dto

import org.jacodb.ets.dto.ArrayRefDto
import org.jacodb.ets.dto.BooleanTypeDto
import org.jacodb.ets.dto.ConstantDto
import org.jacodb.ets.dto.InstanceFieldRefDto
import org.jacodb.ets.dto.LocalDto
import org.jacodb.ets.dto.NullTypeDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.ParameterRefDto
import org.jacodb.ets.dto.StaticFieldRefDto
import org.jacodb.ets.dto.StringTypeDto
import org.jacodb.ets.dto.ThisRefDto
import org.jacodb.ets.dto.UndefinedTypeDto
import org.jacodb.ets.dto.ValueDto
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsBooleanConstant
import org.jacodb.ets.model.EtsConstant
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsNullConstant
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsUndefinedConstant
import org.jacodb.ets.model.EtsValue

fun EtsValue.toDto(): ValueDto = accept(EtsValueToDto)

private object EtsValueToDto : EtsValue.Visitor<ValueDto> {
    override fun visit(value: EtsLocal): ValueDto {
        return LocalDto(
            name = value.name,
            type = value.type.toDto(),
        )
    }

    override fun visit(value: EtsConstant): ValueDto {
        return ConstantDto(
            value = value.toString(),
            type = value.type.toDto(),
        )
    }

    override fun visit(value: EtsStringConstant): ValueDto {
        return ConstantDto(
            value = value.value,
            type = StringTypeDto,
        )
    }

    override fun visit(value: EtsBooleanConstant): ValueDto {
        return ConstantDto(
            value = value.value.toString(),
            type = BooleanTypeDto,
        )
    }

    override fun visit(value: EtsNumberConstant): ValueDto {
        return ConstantDto(
            value = value.value.toString(),
            type = NumberTypeDto,
        )
    }

    override fun visit(value: EtsNullConstant): ValueDto {
        return ConstantDto(
            value = "null",
            type = NullTypeDto,
        )
    }

    override fun visit(value: EtsUndefinedConstant): ValueDto {
        return ConstantDto(
            value = "undefined",
            type = UndefinedTypeDto,
        )
    }

    override fun visit(value: EtsThis): ValueDto {
        return ThisRefDto(
            type = value.type.toDto(),
        )
    }

    override fun visit(value: EtsParameterRef): ValueDto {
        return ParameterRefDto(
            index = value.index,
            type = value.type.toDto(),
        )
    }

    override fun visit(value: EtsArrayAccess): ValueDto {
        return ArrayRefDto(
            array = value.array.toDto(),
            index = value.index.toDto(),
            type = value.type.toDto(),
        )
    }

    override fun visit(value: EtsInstanceFieldRef): ValueDto {
        return InstanceFieldRefDto(
            instance = value.instance.toDto(),
            field = value.field.toDto(),
        )
    }

    override fun visit(value: EtsStaticFieldRef): ValueDto {
        return StaticFieldRefDto(
            field = value.field.toDto(),
        )
    }
}
