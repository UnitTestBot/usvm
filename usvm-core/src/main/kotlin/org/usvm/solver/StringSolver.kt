package org.usvm.solver

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UContext
import org.usvm.UEqExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.string.UCharAtExpr
import org.usvm.collection.string.UCharExpr
import org.usvm.collection.string.UCharToLowerExpr
import org.usvm.collection.string.UCharToUpperExpr
import org.usvm.collection.string.UFloatFromStringExpr
import org.usvm.collection.string.UIntFromStringExpr
import org.usvm.collection.string.URegexExpr
import org.usvm.collection.string.URegexMatchesExpr
import org.usvm.collection.string.UStringExpr
import org.usvm.collection.string.UStringIndexOfExpr
import org.usvm.collection.string.UStringLeExpr
import org.usvm.collection.string.UStringLengthExpr
import org.usvm.collection.string.UStringLiteralExpr
import org.usvm.collection.string.UStringLtExpr
import org.usvm.collection.string.UStringSort
import org.usvm.getIntValue
import org.usvm.logger
import org.usvm.model.UModel
import org.usvm.uctx
import org.usvm.withSizeSort

data class UStringSolverQuery(
    private val charAtConstraints: MutableMap<Pair<UStringExpr, Int>, Char> = mutableMapOf(),
    private val lengthConstraints: MutableMap<UStringExpr, Int> = mutableMapOf(),
    private val stringEqConstraints: MutableMap<Pair<UStringExpr, UStringExpr>, Boolean> = mutableMapOf(),
    private val stringLeConstraints: MutableMap<Pair<UStringExpr, UStringExpr>, Boolean> = mutableMapOf(),
    private val stringLtConstraints: MutableMap<Pair<UStringExpr, UStringExpr>, Boolean> = mutableMapOf(),
    private val indexOfConstraint: MutableMap<Pair<UStringExpr, UStringExpr>, Int> = mutableMapOf(),
    private val regexMatchesConstraint: MutableMap<Pair<UStringExpr, URegexExpr>, Boolean> = mutableMapOf(),
    private val intFromStringConstraints: MutableMap<UStringExpr, Int> = mutableMapOf(),
    private val floatFromStringConstraints: MutableMap<UStringExpr, Number> = mutableMapOf(),
    private val charToUpperConstraint: MutableMap<UCharExpr, Char> = mutableMapOf(),
    private val charToLowerConstraint: MutableMap<UCharExpr, Char> = mutableMapOf(),
) {
    private var conflictDetected = false
    fun isConflicting() = conflictDetected

    private fun <Key, Value> put(map: MutableMap<Key, Value>, key: Key, value: Value) {
        val existingValue = map[key]
        if (existingValue != null) {
            if (existingValue != value) {
                logger.debug { "conflict detected while parsing string constraints!"}
                conflictDetected = true
            }
        } else {
            map[key] = value
        }
    }

    fun addBooleanConstraint(model: UModel, constraint: UBoolExpr, result: Boolean) {
        if (conflictDetected) return
        when (constraint) {
            is UEqExpr<*> -> {
                check(constraint.lhs.sort == constraint.uctx.stringSort)
                    { "Non-string equality came to string solver" }
                @Suppress("UNCHECKED_CAST")
                constraint as UEqExpr<UStringSort>
                val lhs = model.eval(constraint.lhs)
                val rhs = model.eval(constraint.rhs)
                logger.debug { "In fact, adding: $lhs ${if (result) "" else "!"}== $rhs" }
                put(stringEqConstraints, lhs to rhs, result)
            }
            is UStringLeExpr -> {
                val lhs = model.eval(constraint.left)
                val rhs = model.eval(constraint.right)
                logger.debug { "In fact, adding: $lhs ${if (result) "" else "!"}<= $rhs" }
                put(stringLeConstraints, lhs to rhs, result)
            }
            is UStringLtExpr -> {
                val lhs = model.eval(constraint.left)
                val rhs = model.eval(constraint.right)
                logger.debug { "In fact, adding: $lhs ${if (result) "" else "!"}< $rhs" }
                put(stringLtConstraints, lhs to rhs, result)
            }
            is URegexMatchesExpr -> {
                val string = model.eval(constraint.string)
                val pattern = model.eval(constraint.pattern)
                logger.debug { "In fact, adding: ${if (result) "" else "!"}$pattern.matches($string)" }
                put(regexMatchesConstraint, string to pattern, result)
            }
            else -> error("Unexpected string constraint $constraint")
        }
    }

    fun addCharConstraint(model: UModel, constraint: UCharExpr, result: Char) {
        if (conflictDetected) return
        when (constraint) {
            is UCharAtExpr<*> -> {
                val string = model.eval(constraint.string)
                val index = string.uctx.withSizeSort<USort>().getIntValue(model.eval(constraint.index).uncheckedCast())
                    ?: error("Unexpected index ${constraint.index}")
                logger.debug { "In fact, adding: $string.charAt($index) == $result" }
                put(charAtConstraints, string to index, result)
            }
            is UCharToUpperExpr -> {
                val char = model.eval(constraint.char)
                logger.debug { "In fact, adding: charToUpper($char) == $result" }
                put(charToUpperConstraint, char, result)
            }
            is UCharToLowerExpr -> {
                val char = model.eval(constraint.char)
                logger.debug { "In fact, adding: charToLower($char) == $result" }
                put(charToLowerConstraint, char, result)
            }
            else -> error("Unexpected string constraint $constraint")
        }
    }

    fun <USizeSort: USort> addIntConstraint(model: UModel, constraint: UExpr<USizeSort>, result: Int) {
        if (conflictDetected) return
        when (constraint) {
            is UStringLengthExpr<*> -> {
                val string = model.eval(constraint.string)
                logger.debug { "In fact, adding: length($string) == $result" }
                put(lengthConstraints, string, result)
            }
            is UStringIndexOfExpr<*> -> {
                val string = model.eval(constraint.string)
                val pattern = model.eval(constraint.pattern)
                logger.debug { "In fact, adding: indexOf($string, $pattern) == $result" }
                put(indexOfConstraint, string to pattern, result)
            }
            is UIntFromStringExpr<*> -> {
                val string = model.eval(constraint.string)
                logger.debug { "In fact, adding: stringToInt($string) == $result" }
                put(intFromStringConstraints, string, result)
            }
            else -> error("Unexpected string constraint $constraint")
        }
    }

    fun <UFloatSort: USort> addFloatConstraint(model: UModel, constraint: UExpr<UFloatSort>, result: Number) {
        if (conflictDetected) return
        when (constraint) {
            is UFloatFromStringExpr<*> -> {
                val string = model.eval(constraint.string)
                logger.debug { "In fact, adding: stringToFloat($string) == $result" }
                put(floatFromStringConstraints, string, result)
            }
            else -> error("Unexpected string constraint $constraint")
        }
    }

    val isEmpty
        get() =
            charAtConstraints.isEmpty() &&
            lengthConstraints.isEmpty() &&
            stringEqConstraints.isEmpty() &&
            stringLeConstraints.isEmpty() &&
            stringLtConstraints.isEmpty() &&
            indexOfConstraint.isEmpty() &&
            regexMatchesConstraint.isEmpty() &&
            intFromStringConstraints.isEmpty() &&
            floatFromStringConstraints.isEmpty() &&
            charToUpperConstraint.isEmpty() &&
            charToLowerConstraint.isEmpty()
}

typealias UStringModel = Map<UConcreteHeapAddress, UStringLiteralExpr>

interface UStringSolver {
    fun check(query: UStringSolverQuery): USolverResult<UStringModel>
}

/**
 * Solver that gives up tells unknown for every non-trivial string constraint. Fast but dumb.
 */
class UDumbStringSolver(private val ctx: UContext<*>): UStringSolver {
    override fun check(query: UStringSolverQuery): USolverResult<UStringModel> {
        if (query.isEmpty)
            return USatResult(mapOf())
        return UUnknownResult()
    }

}

/**
 * Poor man's solver for approximated strings. Uses dk.bricks.automaton lib for
 */
class URegularStringSolver(
    private val statesLimit: Int = 1000
) {
}