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

package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBinaryExpr
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.utils.callExpr
import org.usvm.dataflow.ifds.FlowFunction
import org.usvm.dataflow.ifds.FlowFunctions
import org.usvm.dataflow.ts.infer.BackwardTypeDomainFact.Zero
import org.usvm.dataflow.ts.infer.BackwardTypeDomainFact.TypedVariable
import org.usvm.dataflow.ts.util.fixAnyToUnknown

class BackwardFlowFunctions(
    val doAddKnownTypes: Boolean,
) : FlowFunctions<BackwardTypeDomainFact, EtsMethod, EtsStmt> {
    override fun obtainPossibleStartFacts(method: EtsMethod): Collection<BackwardTypeDomainFact> = listOf(Zero)

    override fun obtainSequentFlowFunction(
        current: EtsStmt,
        next: EtsStmt,
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        if (current is EtsAssignStmt) {
            val lhvPath = current.lhv.toPathOrNull()
            val rhvPath = current.rhv.toPathOrNull()
            if (lhvPath != null && rhvPath != null && lhvPath == rhvPath) {
                return@FlowFunction listOf(fact)
            }
        }
        when (fact) {
            Zero -> sequentZero(current)
            is TypedVariable -> sequentTypedVariable(current, fact).myFilter()
        }
    }

    companion object {
        private fun AccessPath?.isBase(): Boolean =
            this != null&& base !is AccessPathBase.Const && accesses.isEmpty()

        private fun AccessPath?.isField(): Boolean =
            this != null && accesses.size == 1

        private fun AccessPath?.isConst(): Boolean =
            this != null && base is AccessPathBase.Const && accesses.isEmpty()
    }

    private fun sequentZero(current: EtsStmt): List<BackwardTypeDomainFact> {
        if (current is EtsReturnStmt) {
            // Case `return x`
            // ∅ |= x:unknown
            val returnValue = current.returnValue ?: return listOf(Zero)
            val type = if (doAddKnownTypes) {
                val knownType = returnValue.tryGetKnownType(current.method)
                EtsTypeFact.from(knownType).fixAnyToUnknown()
            } else {
                EtsTypeFact.UnknownEtsTypeFact
            }
            return listOf(Zero, TypedVariable(returnValue.toPath(), type))
        }

        if (current !is EtsAssignStmt) return listOf(Zero)

        val lhv = current.lhv.toPath()
        val rhv = current.rhv.toPathOrNull()

        val result = mutableListOf<BackwardTypeDomainFact>(Zero)

        // When RHS is not const-like, handle possible new facts for RHV:
        if (rhv != null) {
            if (rhv.accesses.isEmpty()) {
                // Case `x... := y`
                // ∅ |= y:unknown
                val type = if (doAddKnownTypes) {
                    val knownType = current.rhv.tryGetKnownType(current.method)
                    EtsTypeFact.from(knownType).fixAnyToUnknown()
                } else {
                    EtsTypeFact.UnknownEtsTypeFact
                }
                result += TypedVariable(lhv, type)
                result += TypedVariable(rhv, type)
            } else {
                // Case `x := y.f`  OR  `x := y[i]`
                check(rhv.accesses.size == 1)
                result += TypedVariable(lhv, EtsTypeFact.UnknownEtsTypeFact)
                result += TypedVariable(rhv, EtsTypeFact.UnknownEtsTypeFact)
            }
        } else {
            when (val rhv = current.rhv) {
                // x := y + z
                is EtsAddExpr -> {
                    val numberOrString = EtsTypeFact.mkUnionType(
                        EtsTypeFact.NumberEtsTypeFact,
                        EtsTypeFact.StringEtsTypeFact,
                    )
                    val y_is_number_or_string = rhv.left.toPathOrNull()?.let {
                        TypedVariable(it, numberOrString)
                    }
                    val z_is_number_or_string = rhv.right.toPathOrNull()?.let {
                        TypedVariable(it, numberOrString)
                    }
                    return listOfNotNull(Zero, y_is_number_or_string, z_is_number_or_string)
                }

                // x := y * z
                is EtsBinaryExpr -> {
                    val y_is_number = rhv.left.toPathOrNull()?.let {
                        TypedVariable(it, EtsTypeFact.NumberEtsTypeFact)
                    }
                    val z_is_number = rhv.right.toPathOrNull()?.let {
                        TypedVariable(it, EtsTypeFact.NumberEtsTypeFact)
                    }
                    return listOfNotNull(Zero, y_is_number, z_is_number)
                }

                // x := y.foo(...) (call-to-return)
                is EtsInstanceCallExpr -> {
                    val y_foo_is_function = TypedVariable(rhv.instance.toPath(), EtsTypeFact.FunctionEtsTypeFact)
                    return listOf(Zero, y_foo_is_function)
                }

                else -> {
                    return listOf(Zero)
                }
            }
        }

        // Handle new possible facts for LHS:
        if (lhv.accesses.isNotEmpty()) {
            result += TypedVariable(lhv, EtsTypeFact.UnknownEtsTypeFact)
        }

        return result
    }

    private fun sequentTypedVariable(current: EtsStmt, fact: TypedVariable): List<TypedVariable> {
        if (current !is EtsAssignStmt) return emptyList()

        val lhv = current.lhv.toPath()
        val rhv = current.rhv.toPathOrNull()

        val result = mutableListOf(fact)

        // z: t --> { z: t }
        if (!fact.variable.startsWith(lhv)) {
            return result
        }

        if (rhv == null) {
            when (val rhv = current.rhv) {
                // x := y + z (addition operator can be string or numeric,
                // then we can infer arguments are numeric when result is numeric)
                is EtsAddExpr -> {
                     // x.*: t --> none
                    if (fact == TypedVariable(lhv, EtsTypeFact.NumberEtsTypeFact)) {
                        val y_is_number = rhv.left.toPathOrNull()?.let {
                            TypedVariable(it, EtsTypeFact.NumberEtsTypeFact)
                        }
                        val z_is_number = rhv.right.toPathOrNull()?.let {
                            TypedVariable(it, EtsTypeFact.NumberEtsTypeFact)
                        }
                        result += listOfNotNull(y_is_number, z_is_number)
                    }
                }

                // x := y * z (numeric operator)
                is EtsBinaryExpr -> {
                    // x.*: t --> none
                }

                // x := y.foo(...) (call-to-return)
                is EtsInstanceCallExpr -> {
                    // x.*: t --> none
                }

                else -> {
                    // x.*: t --> none
                }
            }
        } else {
            val tail = fact.variable.accesses

            when {
                // x := y
                lhv.isBase() && rhv.isBase() -> {
                    // x.*: t --> y.*: t
                    val y_is_t = TypedVariable(rhv + tail, fact.type)
                    result += y_is_t
                }

                // x := y.a
                lhv.isBase() && rhv.isField() -> {
                    // x.*: t --> y.a.*: t
                    val y_a_is_t = TypedVariable(rhv + tail, fact.type)
                    result += y_a_is_t
                }

                // x.a = y
                lhv.isField() && rhv.isBase() -> {
                    // x.a.*: t --> y.*: t
                    check(tail.isNotEmpty())
                    val y_is_t = TypedVariable(rhv + tail.drop(1), fact.type)
                    result += y_is_t
                }

                // x.* := const
                rhv.isConst() -> {
                    // x.*: t --> none
                }

                else -> {
                    // x.*: t --> none
                }
            }
        }

        return result
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: EtsStmt,
        returnSite: EtsStmt
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> callZero(callStatement)
            is TypedVariable -> call(callStatement, fact)
        }
    }

    private fun callZero(
        callStatement: EtsStmt,
    ): List<BackwardTypeDomainFact> {
        val result = mutableListOf<BackwardTypeDomainFact>(Zero)

        val callExpr = callStatement.callExpr ?: error("No call")

        if (callExpr is EtsInstanceCallExpr) {
            val instance = callExpr.instance
            val methodPath = instance.toPath() + FieldAccessor(callExpr.callee.name)
            result += TypedVariable(methodPath, EtsTypeFact.FunctionEtsTypeFact)
        }

        if (doAddKnownTypes) {
            // f(x:T) |= x:T, where T is the type of the argument in f's signature
            for ((index, arg) in callExpr.args.withIndex()) {
                val param = callExpr.callee.parameters.getOrNull(index) ?: continue
                val base = arg.toPath()
                val type = EtsTypeFact.from(param.type)
                result += TypedVariable(base, type)
            }
        }

        return result
    }

    private fun call(
        callStatement: EtsStmt,
        fact: TypedVariable,
    ): List<TypedVariable> {
        val result = mutableListOf<TypedVariable>()

        val callResult = (callStatement as? EtsAssignStmt)?.lhv?.toPath()
        if (callResult != null) {
            // If fact was for LHS, drop it as overridden
            if (fact.variable == callResult) return result
        }

        result += fact
        return result
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: EtsStmt,
        calleeStart: EtsStmt,
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> listOf(fact)
            is TypedVariable -> start(callStatement, calleeStart, fact)
        }
    }

    private fun start(
        callStatement: EtsStmt,
        calleeStart: EtsStmt,
        fact: TypedVariable,
    ): List<TypedVariable> {
        val callResult = (callStatement as? EtsAssignStmt)?.lhv?.toPath() ?: return emptyList()

        if (fact.variable != callResult) return emptyList()

        if (calleeStart is EtsThrowStmt) return emptyList() // TODO support throwStmt

        check(calleeStart is EtsReturnStmt)

        val exitValue = calleeStart.returnValue?.toPath() ?: return emptyList()

        return listOf(TypedVariable(exitValue, fact.type))
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
        exitStatement: EtsStmt
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> listOf(fact)
            is TypedVariable -> exit(callStatement, fact)
        }
    }

    private fun exit(
        callStatement: EtsStmt,
        fact: TypedVariable,
    ): List<TypedVariable> {
        val callExpr = callStatement.callExpr ?: error("No call")

        val tail = fact.variable.accesses

        when (fact.variable.base) {
            is AccessPathBase.This -> {
                if (callExpr !is EtsInstanceCallExpr) {
                    return emptyList()
                }

                val instance = callExpr.instance
                val instancePath = instance.toPath()
                return listOf(TypedVariable(instancePath + tail, fact.type))
            }

            is AccessPathBase.Arg -> {
                val arg = callExpr.args.getOrNull(fact.variable.base.index)?.toPath() ?: return emptyList()
                return listOf(TypedVariable(arg + tail, fact.type))
            }

            else -> return emptyList()
        }
    }
}

private const val ACCESSES_LIMIT = 5
private const val DUPLICATE_FIELDS_LIMIT = 3

private fun Iterable<TypedVariable>.myFilter(): List<TypedVariable> = filter {
    if (it.variable.accesses.size > ACCESSES_LIMIT) {
        // logger.warn { "Dropping too long fact: $it" }
        return@filter false
    }
    if (it.variable.accesses.hasDuplicateFields(DUPLICATE_FIELDS_LIMIT)) {
        // logger.warn { "Dropping fact with duplicate fields: $it" }
        return@filter false
    }
    true
}
