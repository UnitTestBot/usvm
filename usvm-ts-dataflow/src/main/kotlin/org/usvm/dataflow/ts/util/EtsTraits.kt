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

package org.usvm.dataflow.ts.util

import org.jacodb.api.common.CommonMethodParameter
import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonCallExpr
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBinaryExpr
import org.jacodb.ets.model.EtsBooleanConstant
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsConstant
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsImmediate
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsUnaryExpr
import org.jacodb.ets.model.EtsValue
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.callExpr
import org.jacodb.ets.utils.getOperands
import org.jacodb.ets.utils.getValues
import org.jacodb.taint.configuration.ConstantBooleanValue
import org.jacodb.taint.configuration.ConstantIntValue
import org.jacodb.taint.configuration.ConstantStringValue
import org.jacodb.taint.configuration.ConstantValue
import org.jacodb.taint.configuration.TypeMatches
import org.usvm.dataflow.ifds.AccessPath
import org.usvm.dataflow.ifds.ElementAccessor
import org.usvm.dataflow.ifds.FieldAccessor
import org.usvm.dataflow.util.Traits

/**
 * ETS-specific extensions for analysis.
 */
class EtsTraits : Traits<EtsMethod, EtsStmt> {

    override fun convertToPathOrNull(expr: CommonExpr): AccessPath? {
        check(expr is EtsEntity)
        return expr.toPathOrNull()
    }

    override fun convertToPathOrNull(value: CommonValue): AccessPath? {
        check(value is EtsEntity)
        return value.toPathOrNull()
    }

    override fun convertToPath(value: CommonValue): AccessPath {
        check(value is EtsEntity)
        return value.toPath()
    }

    override fun getThisInstance(method: EtsMethod): EtsThis {
        return EtsThis(
            type = EtsClassType(method.signature.enclosingClass),
        )
    }

    override fun getArgument(param: CommonMethodParameter): EtsParameterRef {
        check(param is EtsMethodParameter)
        return EtsParameterRef(
            index = param.index,
            type = param.type,
        )
    }

    override fun getArgumentsOf(method: EtsMethod): List<EtsParameterRef> {
        return method.parameters.map { getArgument(it) }
    }

    override fun getCallee(callExpr: CommonCallExpr): EtsMethod {
        check(callExpr is EtsCallExpr)
        return EtsMethodImpl(callExpr.callee)
    }

    override fun getCallExpr(statement: EtsStmt): EtsCallExpr? {
        return statement.callExpr
    }

    override fun getValues(expr: CommonExpr): Set<CommonValue> {
        check(expr is EtsEntity)
        return expr.getValues().toSet()
    }

    override fun getOperands(statement: EtsStmt): List<CommonExpr> {
        return statement.getOperands().toList()
    }

    override fun getArrayAllocation(statement: EtsStmt): EtsEntity? {
        if (statement !is EtsAssignStmt) return null
        return statement.rhv as? EtsNewArrayExpr
    }

    override fun getArrayAccessIndex(statement: EtsStmt): EtsValue? {
        if (statement !is EtsAssignStmt) return null

        val lhv = statement.lhv
        if (lhv is EtsArrayAccess) return lhv.index

        val rhv = statement.rhv
        if (rhv is EtsArrayAccess) return rhv.index

        return null
    }

    override fun getBranchExprCondition(statement: EtsStmt): EtsEntity? {
        if (statement !is EtsIfStmt) return null
        return statement.condition
    }

    override fun isConstant(value: CommonValue): Boolean {
        check(value is EtsValue)
        return value is EtsConstant
    }

    override fun eqConstant(value: CommonValue, constant: ConstantValue): Boolean {
        check(value is EtsValue)
        return when (constant) {
            is ConstantBooleanValue -> {
                value is EtsBooleanConstant && value.value == constant.value
            }

            is ConstantIntValue -> {
                value is EtsNumberConstant && value.value == constant.value.toDouble()
            }

            is ConstantStringValue -> {
                value is EtsStringConstant && value.value == constant.value
            }
        }
    }

    override fun ltConstant(value: CommonValue, constant: ConstantValue): Boolean {
        check(value is EtsValue)
        return when (constant) {
            is ConstantIntValue -> {
                value is EtsNumberConstant && value.value < constant.value.toDouble()
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun gtConstant(value: CommonValue, constant: ConstantValue): Boolean {
        check(value is EtsValue)
        return when (constant) {
            is ConstantIntValue -> {
                value is EtsNumberConstant && value.value > constant.value.toDouble()
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun matches(value: CommonValue, pattern: String): Boolean {
        check(value is EtsValue)
        val s = value.toString()
        val re = pattern.toRegex()
        return re.matches(s)
    }

    override fun typeMatches(value: CommonValue, condition: TypeMatches): Boolean {
        check(value is EtsValue)
        TODO("Not yet implemented")
    }

    override fun isConstructor(method: EtsMethod): Boolean {
        return method.name == CONSTRUCTOR_NAME
    }

    override fun isLoopHead(statement: EtsStmt): Boolean {
        TODO("Not yet implemented")
    }

    override fun lineNumber(statement: EtsStmt): Int {
        TODO("Not yet implemented")
    }

    override fun locationFQN(statement: EtsStmt): String {
        TODO("Not yet implemented")
    }

    override fun taintFlowRhsValues(statement: CommonAssignInst): List<EtsEntity> {
        check(statement is EtsAssignStmt)
        return when (val rhv = statement.rhv) {
            is EtsBinaryExpr -> listOf(rhv.left, rhv.right)
            is EtsUnaryExpr -> listOf(rhv.arg)
            is EtsCastExpr -> listOf(rhv.arg)
            else -> listOf(rhv)
        }
    }

    override fun taintPassThrough(statement: EtsStmt): List<Pair<EtsValue, EtsValue>>? {
        return null
    }
}

fun EtsEntity.toPathOrNull(): AccessPath? = when (this) {
    is EtsImmediate -> AccessPath(this, emptyList())

    is EtsParameterRef -> AccessPath(this, emptyList())

    is EtsArrayAccess -> {
        array.toPathOrNull()?.let {
            it + ElementAccessor
        }
    }

    is EtsInstanceFieldRef -> {
        instance.toPathOrNull()?.let {
            it + FieldAccessor(field.name)
        }
    }

    is EtsStaticFieldRef -> {
        AccessPath(null, listOf(FieldAccessor(field.name, isStatic = true)))
    }

    is EtsCastExpr -> arg.toPathOrNull()

    else -> null
}

fun EtsEntity.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}
