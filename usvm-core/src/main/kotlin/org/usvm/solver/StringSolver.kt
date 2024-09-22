package org.usvm.solver

import io.ksmt.expr.KExpr
import io.ksmt.expr.transformer.KExprVisitResult
import io.ksmt.expr.transformer.KNonRecursiveVisitor
import io.ksmt.expr.transformer.visitExpr
import io.ksmt.sort.KFpSort
import io.ksmt.sort.KSort
import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UEqExpr
import org.usvm.UExpr
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UIsSubtypeExpr
import org.usvm.UIsSupertypeExpr
import org.usvm.UNullRef
import org.usvm.URegisterReading
import org.usvm.USort
import org.usvm.UTrackedSymbol
import org.usvm.UTransformer
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
import org.usvm.getIntValue
import org.usvm.language.UFormalLanguage
import org.usvm.language.URegularLanguage
import org.usvm.logger
import org.usvm.model.UModel
import org.usvm.regions.Region
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
        if (query.isEmpty)
            return USatResult(mapOf())
        return UUnknownResult()
    }

}

// TODO: use KNonRecursiveVisitor?
abstract class UStringExprVisitor<T : Any, Type, USizeSort : USort>(
    ctx: UContext<*>
) : KNonRecursiveVisitor<T>(ctx), UTransformer<Type, USizeSort> {
    abstract fun visit(expr: UStringExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringLiteralExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringFromArrayExpr<Type, USizeSort>): KExprVisitResult<T>
    abstract fun visit(expr: UStringFromLanguageExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringConcatExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringSliceExpr<USizeSort>): KExprVisitResult<T>
    abstract fun visit(expr: UStringFromIntExpr<USizeSort>): KExprVisitResult<T>
    abstract fun <UFloatSort : KFpSort> visit(expr: UStringFromFloatExpr<UFloatSort>): KExprVisitResult<T>
    abstract fun visit(expr: UStringRepeatExpr<USizeSort>): KExprVisitResult<T>
    abstract fun visit(expr: UStringToUpperExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringToLowerExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringReverseExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringReplaceFirstExpr): KExprVisitResult<T>
    abstract fun visit(expr: UStringReplaceAllExpr): KExprVisitResult<T>
    abstract fun visit(expr: URegexReplaceFirstExpr): KExprVisitResult<T>
    abstract fun visit(expr: URegexReplaceAllExpr): KExprVisitResult<T>

    override fun <Sort : USort> transform(expr: URegisterReading<Sort>) = err()
    override fun <Field, Sort : USort> transform(expr: UInputFieldReading<Field, Sort>) = err()
    override fun <Sort : USort> transform(expr: UAllocatedArrayReading<Type, Sort, USizeSort>) = err()
    override fun <Sort : USort> transform(expr: UInputArrayReading<Type, Sort, USizeSort>) = err()
    override fun transform(expr: UInputArrayLengthReading<Type, USizeSort>) = err()
    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UAllocatedMapReading<Type, KeySort, Sort, Reg>
    ) = err()

    override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(
        expr: UInputMapReading<Type, KeySort, Sort, Reg>
    ) = err()

    override fun <Sort : USort> transform(expr: UAllocatedRefMapWithInputKeysReading<Type, Sort>) = err()
    override fun <Sort : USort> transform(expr: UInputRefMapWithAllocatedKeysReading<Type, Sort>) = err()
    override fun <Sort : USort> transform(expr: UInputRefMapWithInputKeysReading<Type, Sort>) = err()
    override fun transform(expr: UInputMapLengthReading<Type, USizeSort>) = err()
    override fun <ElemSort : USort, Reg : Region<Reg>> transform(expr: UAllocatedSetReading<Type, ElemSort, Reg>) =
        err()

    override fun <ElemSort : USort, Reg : Region<Reg>> transform(expr: UInputSetReading<Type, ElemSort, Reg>) = err()
    override fun transform(expr: UAllocatedRefSetWithInputElementsReading<Type>) = err()
    override fun transform(expr: UInputRefSetWithAllocatedElementsReading<Type>) = err()
    override fun transform(expr: UInputRefSetWithInputElementsReading<Type>) = err()
    override fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>) = err()
    override fun <Sort : USort> transform(expr: UTrackedSymbol<Sort>) = err()
    override fun transform(expr: UIsSubtypeExpr<Type>) = err()
    override fun transform(expr: UIsSupertypeExpr<Type>) = err()
    override fun transform(expr: UConcreteHeapRef) = err()
    override fun transform(expr: UNullRef) = err()

    override fun transform(expr: UStringLengthExpr<USizeSort>) = err()
    override fun transform(expr: UCharAtExpr<USizeSort>) = err()
    override fun transform(expr: UStringHashCodeExpr<USizeSort>) = err()
    override fun transform(expr: UConcreteStringHashCodeBv32Expr) = err()
    override fun transform(expr: UConcreteStringHashCodeIntExpr) = err()
    override fun transform(expr: UStringLtExpr) = err()
    override fun transform(expr: UStringLeExpr) = err()
    override fun transform(expr: UIntFromStringExpr<USizeSort>) = err()
    override fun <UFloatSort : KFpSort> transform(expr: UFloatFromStringExpr<UFloatSort>) = err()
    override fun transform(expr: UCharToUpperExpr) = err()
    override fun transform(expr: UCharToLowerExpr) = err()
    override fun transform(expr: UStringIndexOfExpr<USizeSort>) = err()
    override fun transform(expr: URegexMatchesExpr) = err()


    override fun transform(expr: UStringLiteralExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringFromArrayExpr<Type, USizeSort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringFromLanguageExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringConcatExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringSliceExpr<USizeSort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringFromIntExpr<USizeSort>) = visitExpr(expr, ::visit)
    override fun <UFloatSort : KFpSort> transform(expr: UStringFromFloatExpr<UFloatSort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringRepeatExpr<USizeSort>) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringToUpperExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringToLowerExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringReverseExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringReplaceFirstExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: UStringReplaceAllExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: URegexReplaceFirstExpr) = visitExpr(expr, ::visit)
    override fun transform(expr: URegexReplaceAllExpr) = visitExpr(expr, ::visit)

    private fun err(): Nothing = error("This should not be called")
}

/**
 * Poor man's solver for approximated strings. Uses dk.bricks.automaton lib for
 */
class URegularStringSolver<ArrayType, USizeSort : USort>(
    override val ctx: UContext<USizeSort>,
    private val statesLimit: Int = 1000
) : UStringExprVisitor<UFormalLanguage, ArrayType, USizeSort>(ctx) {
    private val exprToLanguage: MutableMap<UStringExpr, UFormalLanguage> = mutableMapOf()

    override fun visit(expr: UStringExpr): KExprVisitResult<UFormalLanguage> =
        error("Visitor for $expr is not implemented")

    override fun visit(expr: UStringLiteralExpr): KExprVisitResult<UFormalLanguage> =
        saveVisitResult(expr, URegularLanguage.singleton(expr.s))

    override fun visit(expr: UStringFromArrayExpr<ArrayType, USizeSort>): KExprVisitResult<UFormalLanguage> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: UStringFromLanguageExpr) =
        saveVisitResult(expr, URegularLanguage.anyString())

    override fun visit(expr: UStringConcatExpr) =
        visitExprAfterVisited(expr, expr.left, expr.right) { leftRes, rightRes ->
            leftRes.union(rightRes)
        }

    override fun visit(expr: UStringSliceExpr<USizeSort>): KExprVisitResult<UFormalLanguage> {
        val startIndex = ctx.getIntValue(expr.startIndex)
            ?: error("Value should be concrete, but got ${expr.startIndex}")
        val length = ctx.getIntValue(expr.length)
            ?: error("Value should be concrete, but got ${expr.length}")
        return visitExprAfterVisited(expr, expr.superString) { superstringLang ->
            TODO("Not implemented")
        }
    }

    override fun visit(expr: UStringFromIntExpr<USizeSort>): KExprVisitResult<UFormalLanguage> {
        TODO("Not yet implemented")
    }

    override fun <UFloatSort : KFpSort> visit(expr: UStringFromFloatExpr<UFloatSort>): KExprVisitResult<UFormalLanguage> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: UStringRepeatExpr<USizeSort>): KExprVisitResult<UFormalLanguage> {
        val times = ctx.getIntValue(expr.times)
            ?: error("Value should be concrete, but got ${expr.times}")
        return visitExprAfterVisited(expr, expr.string) { it.repeat(times) }
    }

    override fun visit(expr: UStringToUpperExpr): KExprVisitResult<UFormalLanguage> {
        TODO("Not yet implemented")
    }

    override fun visit(expr: UStringToLowerExpr): KExprVisitResult<UFormalLanguage> {
        TODO("Not yet implemented")
    }

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

    override fun <T : KSort> defaultValue(expr: KExpr<T>): UFormalLanguage {
        error("This should not be called")
    }

    override fun mergeResults(left: UFormalLanguage, right: UFormalLanguage): UFormalLanguage {
        error("This should not be called")
    }

}
