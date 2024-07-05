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
import org.jacodb.api.common.CommonProject
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
import org.usvm.dataflow.jvm.util.JcTraits.Companion.getArgument
import org.usvm.dataflow.jvm.util.JcTraits.Companion.toPathOrNull
import org.usvm.dataflow.util.Traits
import org.jacodb.api.jvm.ext.cfg.callExpr as _callExpr
import org.usvm.dataflow.jvm.util.callee as _callee
import org.usvm.dataflow.jvm.util.getArgument as _getArgument
import org.usvm.dataflow.jvm.util.getArgumentsOf as _getArgumentsOf
import org.usvm.dataflow.jvm.util.thisInstance as _thisInstance
import org.usvm.dataflow.jvm.util.toPath as _toPath
import org.usvm.dataflow.jvm.util.toPathOrNull as _toPathOrNull

/**
 * JVM-specific extensions for analysis.
 *
 * ### Usage:
 * ```
 * class MyClass {
 *     companion object : JcTraits
 * }
 * ```
 */
interface JcTraits : Traits<JcMethod, JcInst> {

    // Ensure that all methods are default-implemented in the interface itself:
    companion object : JcTraits

    override val JcMethod.thisInstance: JcThis
        get() = _thisInstance

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override val JcMethod.isConstructor: Boolean
        get() = isConstructor

    override fun CommonExpr.toPathOrNull(): AccessPath? {
        check(this is JcExpr)
        return _toPathOrNull()
    }

    override fun CommonValue.toPathOrNull(): AccessPath? {
        check(this is JcValue)
        return _toPathOrNull()
    }

    override fun CommonValue.toPath(): AccessPath {
        check(this is JcValue)
        return _toPath()
    }

    override val CommonCallExpr.callee: JcMethod
        get() {
            check(this is JcCallExpr)
            return _callee
        }

    override fun CommonProject.getArgument(param: CommonMethodParameter): JcArgument? {
        check(this is JcClasspath)
        check(param is JcParameter)
        return _getArgument(param)
    }

    override fun CommonProject.getArgumentsOf(method: JcMethod): List<JcArgument> {
        check(this is JcClasspath)
        return _getArgumentsOf(method)
    }

    override fun CommonValue.isConstant(): Boolean {
        check(this is JcValue)
        return this is JcConstant
    }

    override fun CommonValue.eqConstant(constant: ConstantValue): Boolean {
        check(this is JcValue)
        return when (constant) {
            is ConstantBooleanValue -> {
                this is JcBool && value == constant.value
            }

            is ConstantIntValue -> {
                this is JcInt && value == constant.value
            }

            is ConstantStringValue -> {
                // TODO: if 'value' is not string, convert it to string and compare with 'constant.value'
                this is JcStringConstant && value == constant.value
            }
        }
    }

    override fun CommonValue.ltConstant(constant: ConstantValue): Boolean {
        check(this is JcValue)
        return when (constant) {
            is ConstantIntValue -> {
                this is JcInt && value < constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun CommonValue.gtConstant(constant: ConstantValue): Boolean {
        check(this is JcValue)
        return when (constant) {
            is ConstantIntValue -> {
                this is JcInt && value > constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun CommonValue.matches(pattern: String): Boolean {
        check(this is JcValue)
        val s = this.toString()
        val re = pattern.toRegex()
        return re.matches(s)
    }

    override fun JcInst.getCallExpr(): CommonCallExpr? {
        return _callExpr
    }

    override fun CommonExpr.getValues(): Set<CommonValue> {
        check(this is JcExpr)
        return values
    }

    override fun JcInst.getOperands(): List<JcExpr> {
        return operands
    }

    override fun JcInst.isLoopHead(): Boolean {
        val loops = location.method.flowGraph().loops
        return loops.any { loop -> this == loop.head }
    }

    override fun JcInst.getBranchExprCondition(): CommonExpr? {
        if (this !is JcIfInst) return null
        return condition
    }

    override fun JcInst.getArrayAllocation(): CommonExpr? {
        if (this !is JcAssignInst) return null
        return rhv as? JcNewArrayExpr
    }

    override fun JcInst.getArrayAccessIndex(): CommonValue? {
        if (this !is JcAssignInst) return null

        val lhv = this.lhv
        if (lhv is JcArrayAccess) return lhv.index

        val rhv = this.rhv
        if (rhv is JcArrayAccess) return rhv.index

        return null
    }

    override fun JcInst.lineNumber(): Int? = location.lineNumber

    override fun JcInst.locationFQN(): String? {
        val method = location.method
        return "${method.enclosingClass.name}#${method.name}"
    }

    override fun CommonValue.typeMatches(condition: TypeMatches): Boolean {
        check(this is JcValue)
        return this.type.isAssignable(condition.type)
    }

    override fun CommonAssignInst.taintFlowRhsValues(): List<CommonExpr> =
        when (val rhs = this.rhv as JcExpr) {
            is JcBinaryExpr -> listOf(rhs.lhv, rhs.rhv)
            is JcNegExpr -> listOf(rhs.operand)
            is JcCastExpr -> listOf(rhs.operand)
            else -> listOf(rhs)
        }

    override fun JcInst.taintPassThrough(): List<Pair<JcValue, JcValue>>? {
        if (this !is JcAssignInst) return null

        // FIXME: handle taint pass-through on invokedynamic-based String concatenation:
        val callExpr = rhv as? JcDynamicCallExpr ?: return null
        if (callExpr.callee.enclosingClass.name != "java.lang.invoke.StringConcatFactory") return null

        return callExpr.args.map { it to lhv }
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
