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
import org.jacodb.api.common.CommonProject
import org.jacodb.api.common.cfg.CommonArgument
import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonCallExpr
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.ets.base.EtsArrayAccess
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsBinaryExpr
import org.jacodb.ets.base.EtsCallExpr
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsConstant
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsImmediate
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsStaticFieldRef
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsUnaryExpr
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.utils.callExpr
import org.jacodb.taint.configuration.ConstantValue
import org.jacodb.taint.configuration.TypeMatches
import org.usvm.dataflow.ifds.AccessPath
import org.usvm.dataflow.ifds.ElementAccessor
import org.usvm.dataflow.ifds.FieldAccessor
import org.usvm.dataflow.ts.util.toPathOrNull
import org.usvm.dataflow.util.Traits
import org.jacodb.ets.utils.getOperands as _getOperands
import org.jacodb.ets.utils.getValues as _getValues
import org.usvm.dataflow.ts.util.toPath as _toPath
import org.usvm.dataflow.ts.util.toPathOrNull as _toPathOrNull

/**
 * ETS-specific extensions for analysis.
 *
 * ### Usage:
 * ```
 * class MyClass {
 *     companion object : EtsTraits
 * }
 * ```
 */
interface EtsTraits : Traits<EtsMethod, EtsStmt> {

    // Ensure that all methods are default-implemented in the interface itself:
    companion object : EtsTraits

    override val CommonCallExpr.callee: EtsMethod
        get() {
            check(this is EtsCallExpr)
            // return cp.getMethodBySignature(method) ?: error("Method not found: $method")
            return EtsMethodImpl(method)
        }

    override val EtsMethod.thisInstance: EtsThis
        get() = EtsThis(EtsClassType(enclosingClass))

    override val EtsMethod.isConstructor: Boolean
        get() = false

    override fun CommonExpr.toPathOrNull(): AccessPath? {
        check(this is EtsEntity)
        return this._toPathOrNull()
    }

    override fun CommonValue.toPathOrNull(): AccessPath? {
        check(this is EtsValue)
        return this._toPathOrNull()
    }

    override fun CommonValue.toPath(): AccessPath {
        check(this is EtsValue)
        return this._toPath()
    }

    override fun CommonProject.getArgument(param: CommonMethodParameter): EtsParameterRef {
        check(param is EtsMethodParameter)
        return EtsParameterRef(index = param.index, type = param.type)
    }

    override fun CommonProject.getArgumentsOf(method: EtsMethod): List<CommonArgument> {
        return method.parameters.map { getArgument(it) }
    }

    override fun CommonValue.isConstant(): Boolean {
        check(this is EtsEntity)
        return this is EtsConstant
    }

    override fun CommonValue.eqConstant(constant: ConstantValue): Boolean {
        TODO("Not yet implemented")
    }

    override fun CommonValue.ltConstant(constant: ConstantValue): Boolean {
        TODO("Not yet implemented")
    }

    override fun CommonValue.gtConstant(constant: ConstantValue): Boolean {
        TODO("Not yet implemented")
    }

    override fun CommonValue.matches(pattern: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun EtsStmt.getCallExpr(): EtsCallExpr? {
        return callExpr
    }

    override fun CommonExpr.getValues(): Set<EtsValue> {
        check(this is EtsEntity)
        return _getValues().toSet()
    }

    override fun EtsStmt.getOperands(): List<EtsEntity> {
        return _getOperands().toList()
    }

    override fun EtsStmt.getBranchExprCondition(): EtsEntity {
        TODO("Not yet implemented")
    }

    override fun EtsStmt.getArrayAllocation(): EtsEntity {
        TODO("Not yet implemented")
    }

    override fun EtsStmt.getArrayAccessIndex(): EtsValue {
        TODO("Not yet implemented")
    }

    override fun EtsStmt.isLoopHead(): Boolean {
        TODO("Not yet implemented")
    }

    override fun EtsStmt.lineNumber(): Int? {
        TODO("Not yet implemented")
    }

    override fun EtsStmt.locationFQN(): String? {
        TODO("Not yet implemented")
    }

    override fun CommonValue.typeMatches(condition: TypeMatches): Boolean {
        check(this is EtsValue)
        TODO("Not yet implemented")
    }

    override fun CommonAssignInst.taintFlowRhsValues(): List<EtsEntity> {
        check(this is EtsAssignStmt)
        return when (val rhv = rhv) {
            is EtsBinaryExpr -> listOf(rhv.left, rhv.right)
            is EtsUnaryExpr -> listOf(rhv.arg)
            else -> listOf(rhv) // FIXME: ???
        }
    }

    override fun EtsStmt.taintPassThrough(): List<Pair<EtsValue, EtsValue>>? {
        return null
    }
}

fun EtsEntity.toPathOrNull(): AccessPath? = when (this) {
    is EtsImmediate -> AccessPath(this, emptyList())

    is EtsThis -> AccessPath(this, emptyList())

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
