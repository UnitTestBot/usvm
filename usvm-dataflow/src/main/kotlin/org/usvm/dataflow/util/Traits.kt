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

package org.usvm.dataflow.util

import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.CommonMethodParameter
import org.jacodb.api.common.cfg.CommonArgument
import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonCallExpr
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonThis
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.taint.configuration.ConstantValue
import org.jacodb.taint.configuration.TypeMatches
import org.usvm.dataflow.ifds.AccessPath

/**
 * Extensions for analysis.
 */
interface Traits<out Method, out Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    fun convertToPathOrNull(expr: CommonExpr): AccessPath?
    fun convertToPathOrNull(value: CommonValue): AccessPath?
    fun convertToPath(value: CommonValue): AccessPath

    fun getThisInstance(method: @UnsafeVariance Method): CommonThis
    fun getArgument(param: CommonMethodParameter): CommonArgument?
    fun getArgumentsOf(method: @UnsafeVariance Method): List<CommonArgument>
    fun getCallee(callExpr: CommonCallExpr): Method
    fun getCallExpr(statement: @UnsafeVariance Statement): CommonCallExpr?
    fun getValues(expr: CommonExpr): Set<CommonValue>
    fun getOperands(statement: @UnsafeVariance Statement): List<CommonExpr>
    fun getArrayAllocation(statement: @UnsafeVariance Statement): CommonExpr?
    fun getArrayAccessIndex(statement: @UnsafeVariance Statement): CommonValue?
    fun getBranchExprCondition(statement: @UnsafeVariance Statement): CommonExpr?

    fun isConstant(value: CommonValue): Boolean
    fun eqConstant(value: CommonValue, constant: ConstantValue): Boolean
    fun ltConstant(value: CommonValue, constant: ConstantValue): Boolean
    fun gtConstant(value: CommonValue, constant: ConstantValue): Boolean
    fun matches(value: CommonValue, pattern: String): Boolean
    fun typeMatches(value: CommonValue, condition: TypeMatches): Boolean

    fun isConstructor(method: @UnsafeVariance Method): Boolean
    fun isLoopHead(statement: @UnsafeVariance Statement): Boolean
    fun lineNumber(statement: @UnsafeVariance Statement): Int
    fun locationFQN(statement: @UnsafeVariance Statement): String
    fun taintFlowRhsValues(statement: CommonAssignInst): List<CommonExpr>
    fun taintPassThrough(statement: @UnsafeVariance Statement): List<Pair<CommonValue, CommonValue>>?
}
