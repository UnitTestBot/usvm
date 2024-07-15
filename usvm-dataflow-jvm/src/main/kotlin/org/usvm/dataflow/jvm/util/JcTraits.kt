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

package org.usvm.dataflow.jvm.util

import org.jacodb.api.common.CommonMethodParameter
import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonCallExpr
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcParameter
import org.jacodb.api.jvm.cfg.JcArgument
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBinaryExpr
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcCallExpr
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcConstant
import org.jacodb.api.jvm.cfg.JcDynamicCallExpr
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcImmediate
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcNegExpr
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcStringConstant
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.cfg.values
import org.jacodb.api.jvm.ext.cfg.callExpr
import org.jacodb.api.jvm.ext.isAssignable
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.cfg.util.loops
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
 * JVM-specific extensions for analysis.
 */
class JcTraits(
    val cp: JcClasspath,
) : Traits<JcMethod, JcInst> {

    override fun convertToPathOrNull(expr: CommonExpr): AccessPath? {
        check(expr is JcExpr)
        return expr.toPathOrNull()
    }

    override fun convertToPathOrNull(value: CommonValue): AccessPath? {
        check(value is JcValue)
        return value.toPathOrNull()
    }

    override fun convertToPath(value: CommonValue): AccessPath {
        check(value is JcValue)
        return value.toPath()
    }

    override fun getThisInstance(method: JcMethod): JcThis {
        return method.thisInstance
    }

    override fun isConstructor(method: JcMethod): Boolean {
        return method.isConstructor
    }

    override fun getArgument(param: CommonMethodParameter): JcArgument? {
        check(param is JcParameter)
        return cp.getArgument(param)
    }

    override fun getArgumentsOf(method: JcMethod): List<JcArgument> {
        return cp.getArgumentsOf(method)
    }

    override fun getCallee(callExpr: CommonCallExpr): JcMethod {
        check(callExpr is JcCallExpr)
        return callExpr.callee
    }

    override fun getCallExpr(statement: JcInst): JcCallExpr? {
        return statement.callExpr
    }

    override fun getValues(expr: CommonExpr): Set<JcValue> {
        check(expr is JcExpr)
        return expr.values
    }

    override fun getOperands(statement: JcInst): List<JcExpr> {
        return statement.operands
    }

    override fun getArrayAllocation(statement: JcInst): JcExpr? {
        if (statement !is JcAssignInst) return null
        return statement.rhv as? JcNewArrayExpr
    }

    override fun getArrayAccessIndex(statement: JcInst): JcValue? {
        if (statement !is JcAssignInst) return null

        val lhv = statement.lhv
        if (lhv is JcArrayAccess) return lhv.index

        val rhv = statement.rhv
        if (rhv is JcArrayAccess) return rhv.index

        return null
    }

    override fun getBranchExprCondition(statement: JcInst): JcExpr? {
        if (statement !is JcIfInst) return null
        return statement.condition
    }

    override fun isConstant(value: CommonValue): Boolean {
        check(value is JcValue)
        return value is JcConstant
    }

    override fun eqConstant(value: CommonValue, constant: ConstantValue): Boolean {
        check(value is JcValue)
        return when (constant) {
            is ConstantBooleanValue -> {
                value is JcBool && value.value == constant.value
            }

            is ConstantIntValue -> {
                value is JcInt && value.value == constant.value
            }

            is ConstantStringValue -> {
                // TODO: if 'value' is not string, convert it to string and compare with 'constant.value'
                value is JcStringConstant && value.value == constant.value
            }
        }
    }

    override fun ltConstant(value: CommonValue, constant: ConstantValue): Boolean {
        check(value is JcValue)
        return when (constant) {
            is ConstantIntValue -> {
                value is JcInt && value.value < constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun gtConstant(value: CommonValue, constant: ConstantValue): Boolean {
        check(value is JcValue)
        return when (constant) {
            is ConstantIntValue -> {
                value is JcInt && value.value > constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun matches(value: CommonValue, pattern: String): Boolean {
        check(value is JcValue)
        val s = value.toString()
        val re = pattern.toRegex()
        return re.matches(s)
    }

    override fun typeMatches(value: CommonValue, condition: TypeMatches): Boolean {
        check(value is JcValue)
        return value.type.isAssignable(condition.type)
    }

    override fun isLoopHead(statement: JcInst): Boolean {
        val loops = statement.location.method.flowGraph().loops
        return loops.any { loop -> statement == loop.head }
    }

    override fun lineNumber(statement: JcInst): Int {
        return statement.lineNumber
    }

    override fun locationFQN(statement: JcInst): String {
        val method = statement.location.method
        return "${method.enclosingClass.name}#${method.name}"
    }

    override fun taintFlowRhsValues(statement: CommonAssignInst): List<JcExpr> {
        check(statement is JcAssignInst)
        return when (val rhv = statement.rhv) {
            is JcBinaryExpr -> listOf(rhv.lhv, rhv.rhv)
            is JcNegExpr -> listOf(rhv.operand)
            is JcCastExpr -> listOf(rhv.operand)
            else -> listOf(rhv)
        }
    }

    override fun taintPassThrough(statement: JcInst): List<Pair<JcValue, JcValue>>? {
        if (statement !is JcAssignInst) return null

        // FIXME: handle taint pass-through on invokedynamic-based String concatenation:
        val callExpr = statement.rhv as? JcDynamicCallExpr ?: return null
        if (callExpr.callee.enclosingClass.name != "java.lang.invoke.StringConcatFactory") return null

        return callExpr.args.map { it to statement.lhv }
    }
}

val JcMethod.thisInstance: JcThis
    get() = JcThis(enclosingClass.toType())

val JcCallExpr.callee: JcMethod
    get() = method.method

fun JcExpr.toPathOrNull(): AccessPath? = when (this) {
    is JcValue -> toPathOrNull()
    is JcCastExpr -> operand.toPathOrNull()
    else -> null
}

fun JcValue.toPathOrNull(): AccessPath? = when (this) {
    is JcImmediate -> AccessPath(this, emptyList())

    is JcArrayAccess -> {
        array.toPathOrNull()?.let {
            it + ElementAccessor
        }
    }

    is JcFieldRef -> {
        val instance = instance
        if (instance == null) {
            require(field.isStatic) { "Expected static field" }
            AccessPath(null, listOf(FieldAccessor(field.name, isStatic = true)))
        } else {
            instance.toPathOrNull()?.let {
                it + FieldAccessor(field.name)
            }
        }
    }

    else -> null
}

fun JcValue.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}

fun JcClasspath.getArgument(param: JcParameter): JcArgument? {
    val t = findTypeOrNull(param.type.typeName) ?: return null
    return JcArgument.of(param.index, param.name, t)
}

fun JcClasspath.getArgumentsOf(method: JcMethod): List<JcArgument> {
    return method.parameters.map { getArgument(it)!! }
}
