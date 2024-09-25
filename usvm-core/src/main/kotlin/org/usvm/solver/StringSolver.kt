package org.usvm.solver

import dk.brics.automaton.Automaton
import io.ksmt.expr.KExpr
import io.ksmt.expr.transformer.KExprVisitResult
import io.ksmt.sort.KFpSort
import io.ksmt.sort.KSort
import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UEqExpr
import org.usvm.UExpr
import org.usvm.UExprVisitor
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UIsSubtypeExpr
import org.usvm.UIsSupertypeExpr
import org.usvm.UNullRef
import org.usvm.URegisterReading
import org.usvm.USort
import org.usvm.UTrackedSymbol
import org.usvm.collection.array.UAllocatedArrayReading
import org.usvm.collection.array.UInputArrayReading
import org.usvm.collection.array.length.UInputArrayLengthReading
import org.usvm.collection.field.UInputFieldReading
import org.usvm.collection.map.length.UInputMapLengthReading
import org.usvm.collection.map.primitive.UAllocatedMapReading
import org.usvm.collection.map.primitive.UInputMapReading
import org.usvm.collection.map.ref.UAllocatedRefMapWithInputKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithAllocatedKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithInputKeysReading
import org.usvm.collection.set.primitive.UAllocatedSetReading
import org.usvm.collection.set.primitive.UInputSetReading
import org.usvm.collection.set.ref.UAllocatedRefSetWithInputElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithAllocatedElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithInputElementsReading
import org.usvm.collection.string.UCharAtExpr
import org.usvm.collection.string.UCharExpr
import org.usvm.collection.string.UCharToLowerExpr
import org.usvm.collection.string.UCharToUpperExpr
import org.usvm.collection.string.UConcreteStringHashCodeBv32Expr
import org.usvm.collection.string.UConcreteStringHashCodeIntExpr
import org.usvm.collection.string.UFloatFromStringExpr
import org.usvm.collection.string.UIntFromStringExpr
import org.usvm.collection.string.URegexExpr
import org.usvm.collection.string.URegexMatchesExpr
import org.usvm.collection.string.URegexReplaceAllExpr
import org.usvm.collection.string.URegexReplaceFirstExpr
import org.usvm.collection.string.UStringConcatExpr
import org.usvm.collection.string.UStringExpr
import org.usvm.collection.string.UStringFromArrayExpr
import org.usvm.collection.string.UStringFromFloatExpr
import org.usvm.collection.string.UStringFromIntExpr
import org.usvm.collection.string.UStringFromLanguageExpr
import org.usvm.collection.string.UStringHashCodeExpr
import org.usvm.collection.string.UStringIndexOfExpr
import org.usvm.collection.string.UStringLeExpr
import org.usvm.collection.string.UStringLengthExpr
import org.usvm.collection.string.UStringLiteralExpr
import org.usvm.collection.string.UStringLtExpr
import org.usvm.collection.string.UStringRepeatExpr
import org.usvm.collection.string.UStringReplaceAllExpr
import org.usvm.collection.string.UStringReplaceFirstExpr
import org.usvm.collection.string.UStringReverseExpr
import org.usvm.collection.string.UStringSliceExpr
import org.usvm.collection.string.UStringSort
import org.usvm.collection.string.UStringToLowerExpr
import org.usvm.collection.string.UStringToUpperExpr
import org.usvm.collection.string.getLength
import org.usvm.getIntValue
import org.usvm.language.UFormalLanguage
import org.usvm.language.URegularLanguage
import org.usvm.logger
import org.usvm.model.UModel
import org.usvm.regions.Region
import org.usvm.uctx
import org.usvm.withSizeSort

data class UStringSolverQuery(
    val model: UModel,
    val charAtConstraints: MutableMap<Pair<UStringExpr, Int>, Char> = mutableMapOf(),
    val lengthConstraints: MutableMap<UStringExpr, Int> = mutableMapOf(),
    val stringEqConstraints: MutableMap<Pair<UStringExpr, UStringExpr>, Boolean> = mutableMapOf(),
    val stringLeConstraints: MutableMap<Pair<UStringExpr, UStringExpr>, Boolean> = mutableMapOf(),
    val stringLtConstraints: MutableMap<Pair<UStringExpr, UStringExpr>, Boolean> = mutableMapOf(),
    val indexOfConstraint: MutableMap<Pair<UStringExpr, UStringExpr>, Int> = mutableMapOf(),
    val regexMatchesConstraint: MutableMap<Pair<UStringExpr, URegexExpr>, Boolean> = mutableMapOf(),

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
                logger.debug { "conflict detected while parsing string constraints!" }
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

    fun <USizeSort : USort> addIntConstraint(model: UModel, constraint: UExpr<USizeSort>, result: Int) {
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

    fun <UFloatSort : USort> addFloatConstraint(model: UModel, constraint: UExpr<UFloatSort>, result: Number) {
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
class UDumbStringSolver(private val ctx: UContext<*>) : UStringSolver {
    override fun check(query: UStringSolverQuery): USolverResult<UStringModel> {
        if (query.isConflicting())
            return UUnsatResult() // TODO: return unknown?
        if (query.isEmpty)
            return USatResult(mapOf())
        return UUnknownResult()
    }

}

/**
 * Caching converter of string expressions to over-approximating regular languages.
 */
abstract class UStringExprVisitor<Result: Any, ArrayType, USizeSort : USort>(
    override val ctx: UContext<USizeSort>,
): UExprVisitor<Result, ArrayType, USizeSort>(ctx) {
    override fun visit(expr: UStringExpr): KExprVisitResult<Result> =
        error("Visitor for $expr is not implemented")

    abstract override fun visit(expr: UStringLiteralExpr): KExprVisitResult<Result>
    abstract override fun visit(expr: UStringFromArrayExpr<ArrayType, USizeSort>): KExprVisitResult<Result>
    abstract override fun visit(expr: UStringFromLanguageExpr): KExprVisitResult<Result>
    abstract override fun visit(expr: UStringConcatExpr): KExprVisitResult<Result>
    abstract override fun visit(expr: UStringSliceExpr<USizeSort>): KExprVisitResult<Result>
    abstract override fun visit(expr: UStringFromIntExpr<USizeSort>): KExprVisitResult<Result>
    abstract override fun <UFloatSort : KFpSort> visit(expr: UStringFromFloatExpr<UFloatSort>): KExprVisitResult<Result>
    abstract override fun visit(expr: UStringRepeatExpr<USizeSort>): KExprVisitResult<Result>
    abstract override fun visit(expr: UStringToUpperExpr): KExprVisitResult<Result>

    abstract override fun visit(expr: UStringToLowerExpr): KExprVisitResult<Result>
    abstract override fun visit(expr: UStringReverseExpr): KExprVisitResult<Result>
    abstract override fun visit(expr: UStringReplaceFirstExpr): KExprVisitResult<Result>
    abstract override fun visit(expr: UStringReplaceAllExpr): KExprVisitResult<Result>
    abstract override fun visit(expr: URegexReplaceFirstExpr): KExprVisitResult<Result>
    abstract override fun visit(expr: URegexReplaceAllExpr): KExprVisitResult<Result>

    override fun <Sort : USort> visit(expr: URegisterReading<Sort>) = err()
    override fun <Field, Sort : USort> visit(expr: UInputFieldReading<Field, Sort>) = err()
    override fun <Method, Sort : USort> visit(expr: UIndexedMethodReturnValue<Method, Sort>) = err()
    override fun <Sort : USort> visit(expr: UTrackedSymbol<Sort>) = err()
    override fun visit(expr: UConcreteHeapRef) = err()
    override fun visit(expr: UNullRef) = err()
    override fun visit(expr: UConcreteStringHashCodeBv32Expr) = err()
    override fun visit(expr: UConcreteStringHashCodeIntExpr) = err()
    override fun visit(expr: UStringLtExpr) = err()
    override fun visit(expr: UStringLeExpr) = err()
    override fun <UFloatSort : KFpSort> visit(expr: UFloatFromStringExpr<UFloatSort>) = err()
    override fun visit(expr: UCharToUpperExpr) = err()
    override fun visit(expr: UCharToLowerExpr) = err()
    override fun visit(expr: URegexMatchesExpr) = err()
    override fun visit(expr: UStringIndexOfExpr<USizeSort>) = err()
    override fun visit(expr: UIntFromStringExpr<USizeSort>) = err()
    override fun visit(expr: UStringHashCodeExpr<USizeSort>) = err()
    override fun visit(expr: UCharAtExpr<USizeSort>) = err()
    override fun visit(expr: UStringLengthExpr<USizeSort>) = err()
    override fun visit(expr: UIsSupertypeExpr<ArrayType>) = err()
    override fun visit(expr: UIsSubtypeExpr<ArrayType>) = err()
    override fun visit(expr: UInputRefSetWithInputElementsReading<ArrayType>) = err()
    override fun visit(expr: UInputRefSetWithAllocatedElementsReading<ArrayType>) = err()
    override fun visit(expr: UAllocatedRefSetWithInputElementsReading<ArrayType>) = err()
    override fun <ElemSort : USort, Reg : Region<Reg>> visit(expr: UInputSetReading<ArrayType, ElemSort, Reg>) = err()
    override fun <ElemSort : USort, Reg : Region<Reg>> visit(expr: UAllocatedSetReading<ArrayType, ElemSort, Reg>) = err()
    override fun visit(expr: UInputMapLengthReading<ArrayType, USizeSort>) = err()
    override fun <Sort : USort> visit(expr: UInputRefMapWithInputKeysReading<ArrayType, Sort>) = err()
    override fun <Sort : USort> visit(expr: UInputRefMapWithAllocatedKeysReading<ArrayType, Sort>) = err()
    override fun <Sort : USort> visit(expr: UAllocatedRefMapWithInputKeysReading<ArrayType, Sort>) = err()
    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> visit(expr: UInputMapReading<ArrayType, KeySort, Sort, Reg>) = err()
    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> visit(expr: UAllocatedMapReading<ArrayType, KeySort, Sort, Reg>) = err()
    override fun visit(expr: UInputArrayLengthReading<ArrayType, USizeSort>) = err()
    override fun <Sort : USort> visit(expr: UInputArrayReading<ArrayType, Sort, USizeSort>) = err()
    override fun <Sort : USort> visit(expr: UAllocatedArrayReading<ArrayType, Sort, USizeSort>) = err()
    override fun <T : KSort> defaultValue(expr: KExpr<T>) = err()
    override fun mergeResults(left: Result, right: Result) = err()
    protected fun err(): Nothing = error("This should not be called")
}


/**
 * Caching converter of string expressions to over-approximating regular languages.
 * For example, given UStringToUpper(UStringFromLanguage(ab*)) will compute the automaton accepting AB*.
 */
private class UForwardApproximator<ArrayType, USizeSort : USort>(
    override val ctx: UContext<USizeSort>,
    val length: (UStringExpr) -> Int
): UStringExprVisitor<UFormalLanguage, ArrayType, USizeSort>(ctx) {
    override fun visit(expr: UStringExpr): KExprVisitResult<UFormalLanguage> =
        error("Visitor for $expr is not implemented")

    override fun visit(expr: UStringLiteralExpr): KExprVisitResult<UFormalLanguage> =
        saveVisitResult(expr, URegularLanguage.singleton(expr.s))

    override fun visit(expr: UStringFromArrayExpr<ArrayType, USizeSort>) = err()

    override fun visit(expr: UStringFromLanguageExpr) =
        saveVisitResult(expr, URegularLanguage.anyString())

    override fun visit(expr: UStringConcatExpr) =
        visitExprAfterVisited(expr, expr.left, expr.right) { leftRes, rightRes ->
            leftRes.union(rightRes)
        }

    override fun visit(expr: UStringSliceExpr<USizeSort>): KExprVisitResult<UFormalLanguage> {
        val startIndex = ctx.getIntValue(expr.startIndex)
            ?: error("Value should be concrete, but got ${expr.startIndex}")
        val sliceLength = ctx.getIntValue(expr.length)
            ?: error("Value should be concrete, but got ${expr.length}")
        return visitExprAfterVisited(expr, expr.superString) { superstringLang ->
            check(startIndex >= 0) { "Negative substring start index!" }
            check(sliceLength >= 0) { "Negative substring length!" }
            val superStringLength = length(expr.superString)
            superstringLang.trimCount(startIndex, superStringLength - startIndex - sliceLength)
        }
    }

    override fun visit(expr: UStringFromIntExpr<USizeSort>) =
        error("This should not be called!")

    override fun <UFloatSort : KFpSort> visit(expr: UStringFromFloatExpr<UFloatSort>) =
        error("This should not be called!")

    override fun visit(expr: UStringRepeatExpr<USizeSort>): KExprVisitResult<UFormalLanguage> {
        val times = ctx.getIntValue(expr.times)
            ?: error("Value should be concrete, but got ${expr.times}")
        return visitExprAfterVisited(expr, expr.string) { it.repeat(times) }
    }

    override fun visit(expr: UStringToUpperExpr): KExprVisitResult<UFormalLanguage> =
        visitExprAfterVisited(expr, expr.string) { it.toUpperCase() }

    override fun visit(expr: UStringToLowerExpr): KExprVisitResult<UFormalLanguage> =
        visitExprAfterVisited(expr, expr.string) { it.toLowerCase() }

    override fun visit(expr: UStringReverseExpr): KExprVisitResult<UFormalLanguage> =
        visitExprAfterVisited(expr, expr.string) { it.reverse() }

    override fun visit(expr: UStringReplaceFirstExpr): KExprVisitResult<UFormalLanguage> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: UStringReplaceAllExpr): KExprVisitResult<UFormalLanguage> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: URegexReplaceFirstExpr): KExprVisitResult<UFormalLanguage> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: URegexReplaceAllExpr): KExprVisitResult<UFormalLanguage> {
        TODO("Not yet implemented")
    }

    fun computeApproximation(expr: UStringExpr): UFormalLanguage =
        visit(expr).result
}

/**
 * Inverts constraints on string expressions, propagating languages towards leafs.
 * For example,
 */
private class UBackwardApproximator<USizeSort: USort>(
    private val forward: UForwardApproximator<*, USizeSort>
) {
    private val queue = arrayListOf<Pair<UStringExpr, UFormalLanguage>>()
    private val result = mutableMapOf<UConcreteHeapAddress, UFormalLanguage>()
    private var contradiction = false

    private fun contradictionFound() {
        contradiction = true
        queue.clear()
        result.clear()
    }

    private fun strengthen(expr: UStringFromLanguageExpr, language: UFormalLanguage): Boolean {
        check(expr.ref is UConcreteHeapRef) { "${expr.ref} should be concrete after model eval!" }
        val currentLang = result.getOrDefault(expr.ref.address, expr.language)
        val intersection = currentLang.intersect(language)
        if (intersection.isEmpty)
            return false
        result.put(expr.ref.address, intersection)
        return true
    }

    fun propagateCharAtConstraint(expr: UStringExpr, index: Int, result: Char) {
        val length = forward.length(expr)
        val leftConcatLang = URegularLanguage.anyStringOfLength(index)
        val rightConcatLang = URegularLanguage.anyStringOfLength(length - index - 1)
        val charLang = URegularLanguage.singleton(result.toString())
        val lang = leftConcatLang.concat(leftConcatLang).concat(charLang).concat(rightConcatLang)
        queue.add(expr to lang)
    }

    fun propagateEqConstraint(left: UStringExpr, right: UStringExpr, equal: Boolean) {
        if (equal) {
            val leftLang = forward.computeApproximation(left)
            val rightLang = forward.computeApproximation(right)
            queue.add(left to rightLang)
            queue.add(right to leftLang)
        } else {
            when {
                left is UStringLiteralExpr -> {
                    val rightLang = URegularLanguage.singleton(left.s).complement()
                    queue.add(right to rightLang)
                }
                right is UStringLiteralExpr -> {
                    val leftLang = URegularLanguage.singleton(right.s).complement()
                    queue.add(left to leftLang)
                }
                else -> { } // skipping this constraint
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun propagateLtConstraint(left: UStringExpr, right: UStringExpr, isLess: Boolean) {
        // skipping this constraint for now: inconvenient to be modeled by automata.
        // TODO: do this at least for literal strings!
    }

    @Suppress("UNUSED_PARAMETER")
    fun propagateLeConstraint(left: UStringExpr, right: UStringExpr, isLe: Boolean) {
        // skipping this constraint for now: inconvenient to be modeled by automata.
        // TODO: do this at least for literal strings!
    }

    fun propagateIndexOfConstraint(string: UStringExpr, pattern: UStringExpr, index: Int) {
        if (index < 0) {
            when (pattern) {
                is UStringLiteralExpr -> {
                    val patternLang = URegularLanguage.singleton(pattern.s)
                    val sigmaStar = URegularLanguage.anyString()
                    val notPatternLang = sigmaStar.concat(patternLang).concat(sigmaStar).complement()
                    queue.add(string to notPatternLang)
                }
            }
        }
        val prefix = URegularLanguage.anyStringOfLength(index)
        val suffix = URegularLanguage.anyString()
        val patternLang = forward.computeApproximation(pattern)
        val lang = prefix.concat(patternLang).concat(suffix)
        queue.add(string to lang)
        // TODO: overapproximate pattern
    }

    fun propagateRegexMatchesConstraint(string: UStringExpr, pattern: UStringExpr, matches: Boolean) = when (pattern) {
        is UStringLiteralExpr -> {
            val regexLang = URegularLanguage.fromRegex(pattern.s)
            val lang = if (matches) regexLang else regexLang.complement()
            queue.add(string to lang).let {}
        }
        else -> {} // skipping this constraint...
    }

    fun compute() {
        while (queue.isNotEmpty()) {
            val (expr, lang) = queue.removeLast()
            when (expr) {
                is UStringLiteralExpr -> {
                    if (!lang.contains(expr.s))
                        return contradictionFound()
                }

                is UStringFromLanguageExpr -> {
                    if (!strengthen(expr, lang))
                        return contradictionFound()
                }

                is UStringConcatExpr -> {
                    val leftLen = forward.length(expr.left)
                    val rightLen = forward.length(expr.right)
                    val leftLang = lang.trimCount(0, rightLen)
                    val rightLang = lang.trimCount(leftLen, 0)
                    queue.add(expr.left to leftLang)
                    queue.add(expr.right to rightLang)
                }

                is UStringSliceExpr<*> -> {
                    val leftLength = forward.ctx.getIntValue(expr.startIndex.uncheckedCast())
                        ?: error("Invalid start index after model eval")
                    val sliceLength = forward.ctx.getIntValue(expr.length.uncheckedCast())
                        ?: error("Invalid slice length after model eval")
                    val superStringLength = forward.length(expr.superString)
                    val rightLength = superStringLength - sliceLength - leftLength
                    check(leftLength >= 0 &&  rightLength >= 0) { "Invalid string slice length value!" }
                    val leftConcatLang = URegularLanguage.anyStringOfLength(leftLength)
                    val rightConcatLang = URegularLanguage.anyStringOfLength(rightLength)
                    val superStringLang = leftConcatLang.concat(lang).concat(rightConcatLang)
                    queue.add(expr.superString to superStringLang)
                }

                is UStringRepeatExpr<*> -> {
                    val times = forward.ctx.getIntValue(expr.times.uncheckedCast()) ?:
                        error("Invalid times value after model eval!")
                    check(times >= 1) { "Invalid times value after model eval!" }
                    val oneTimeLength = forward.length(expr.string)
                    val oneTimeLang = lang.trimCount(0, (times - 1) * oneTimeLength)
                    queue.add(expr.string to oneTimeLang)
                }
                is UStringToUpperExpr -> {
                    queue.add(expr.string to lang.deUpperCase())
                }
                is UStringToLowerExpr -> {
                    queue.add(expr.string to lang.deLowerCase())
                }
                is UStringReverseExpr -> {
                    val reversedLang = lang.reverse()
                    queue.add(expr.string to reversedLang)
                }
                is UStringReplaceFirstExpr -> {
                    TODO()
                }
                is UStringReplaceAllExpr -> {
                    TODO()
                }
                is URegexReplaceFirstExpr -> {
                    TODO()
                }
                is URegexReplaceAllExpr -> {
                    TODO()
                }
                else -> error("Unexpected string expression $expr")
            }
        }
    }

    fun getResult(): Map<UConcreteHeapAddress, UFormalLanguage>? =
        if (contradiction) null
        else result
}

/**
 * Poor man's solver for approximated strings.
 *
 * Given constraints on string expressions, works in two stages:
 * 1. Builds over-approximated language of possible strings for every [UStringFromLanguageExpr].
 * 2. Samples limited amount of strings from these languages, checks that these strings satisfy all the constraints.
 *    If such strings are found, returns [USatResult]. Otherwise, returns [UUnknownResult].
 */
class URegularStringSolver<USizeSort : USort>(
    private val ctx: UContext<USizeSort>,
    /**
     * Maximal amount of states per one automaton appearing in the analysis.
     */
    private val statesLimit: Int = 1000,
    /**
     * Maximal amount of automata sampling attempts (per [check] call).
     */
    private val samplesLimit: Int = 100
): UStringSolver {

    init {
        Automaton.setStatesLimit(statesLimit)
    }

    override fun check(query: UStringSolverQuery): USolverResult<UStringModel> {
        if (query.isConflicting())
            return UUnsatResult() // TODO: return unknown?
        if (query.isEmpty)
            return USatResult(mapOf())
        val lengthGetter = { expr: UStringExpr ->
            val symbolicLength = getLength(ctx, expr)
            ctx.getIntValue(query.model.eval(symbolicLength)) ?: error("Unexpected length eval")
        }
        val forwardApproximator = UForwardApproximator<Any, USizeSort>(ctx, lengthGetter)
        val backwardApproximator = UBackwardApproximator(forwardApproximator)
        for (c in query.charAtConstraints.entries)
            backwardApproximator.propagateCharAtConstraint(c.key.first, c.key.second, c.value)
        for (c in query.stringEqConstraints.entries)
            backwardApproximator.propagateEqConstraint(c.key.first, c.key.second, c.value)
        for (c in query.stringLtConstraints.entries)
            backwardApproximator.propagateLtConstraint(c.key.first, c.key.second, c.value)
        for (c in query.stringLeConstraints.entries)
            backwardApproximator.propagateLeConstraint(c.key.first, c.key.second, c.value)
        for (c in query.indexOfConstraint.entries)
            backwardApproximator.propagateIndexOfConstraint(c.key.first, c.key.second, c.value)
        for (c in query.regexMatchesConstraint.entries)
            backwardApproximator.propagateRegexMatchesConstraint(c.key.first, c.key.second, c.value)
        backwardApproximator.compute()
        val modelApproximation = backwardApproximator.getResult() ?: return UUnsatResult() // TODO: return unknown?
        return sample(modelApproximation)
    }

    private fun sample(approx: Map<UConcreteHeapAddress, UFormalLanguage>): USolverResult<UStringModel> {
        TODO(approx.toString())
    }

}
