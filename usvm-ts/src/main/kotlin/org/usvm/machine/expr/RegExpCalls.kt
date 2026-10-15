package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.initializeArray
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.call.hasBuiltinGlobalOwner
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsState
import org.usvm.sizeSort
import org.usvm.util.copyStringRange
import org.usvm.util.markDenseArrayOwnSlots
import org.usvm.util.markDenseInputArray
import org.usvm.util.mkFieldLValue
import org.usvm.util.stringLength

private val regExpMatchArrayType = EtsArrayType(EtsStringType, dimensions = 1)
private val regExpFlagProperties = setOf(
    "global",
    "ignoreCase",
    "multiline",
    "dotAll",
    "unicode",
    "sticky",
    "hasIndices",
    "unicodeSets",
)
private val regExpReadOnlyProperties = regExpFlagProperties + setOf("source", "flags")

internal fun TsExprResolver.allocateRegExpLiteral(type: EtsClassType): UConcreteHeapRef = scope.calcOnState {
    val reference = memory.allocConcrete(type)
    regularExpressions += reference to null
    reference
}

/** Core runtime semantics: available independently of the optional unknown-call model catalog. */
internal fun TsExprResolver.tryApproximateRegExpCall(stmt: TsVirtualMethodCallStmt): TsExprApproximationResult {
    val target = stmt.args.firstOrNull() as? UConcreteHeapRef
    val targetsRegExp = target != null && scope.calcOnState { target in regularExpressions }
    val reflectiveBuiltin = hasBuiltinGlobalOwner(stmt.call.instance, stmt.call.callee, expectedName = "Object") ||
        hasBuiltinGlobalOwner(stmt.call.instance, stmt.call.callee, expectedName = "Reflect")
    if (targetsRegExp && reflectiveBuiltin) {
        return unsupportedRegExp("Reflective operations on intrinsic RegExp objects are unsupported")
    }

    val receiver = stmt.instance as? UConcreteHeapRef
    val intrinsicRegExp = receiver != null && scope.calcOnState { receiver in regularExpressions }
    if (intrinsicRegExp) {
        val reference = checkNotNull(receiver)
        val method = stmt.call.callee.name
        val overridden = scope.calcOnState { (reference to method) in writtenConcreteFields }
        if (overridden) return TsExprApproximationResult.NoApproximation
        return when (method) {
            CONSTRUCTOR_NAME -> initializeRegExpLiteral(reference, stmt)
            "test" -> testRegExp(reference, stmt)
            else -> unsupportedRegExp("Only intrinsic RegExp.test and String.match are supported")
        }
    }

    if (stmt.call.callee.name != "match") return TsExprApproximationResult.NoApproximation
    val expression = stmt.args.firstOrNull() as? UConcreteHeapRef
    val knownExpression = expression != null && scope.calcOnState { expression in regularExpressions }
    if (!knownExpression) return TsExprApproximationResult.NoApproximation
    val backedReceiver = stmt.instance.sort == ctx.addressSort && scope.calcOnState {
        stmt.instance.asExpr(ctx.addressSort) in boundedStringBackingRefs
    }
    if (!backedReceiver) return TsExprApproximationResult.NoApproximation

    return matchRegExp(checkNotNull(expression), stmt)
}

private fun TsExprResolver.initializeRegExpLiteral(
    reference: UConcreteHeapRef,
    stmt: TsVirtualMethodCallStmt,
): TsExprApproximationResult {
    if (stmt.args.size != 2) return unsupportedRegExp("RegExp literal requires constant pattern and flags")
    val patternRef = stmt.args[0] as? UConcreteHeapRef
    val flagsRef = stmt.args[1] as? UConcreteHeapRef
    val pattern = patternRef?.let(ctx::getStringConstantValue)
    val flags = flagsRef?.let(ctx::getStringConstantValue)
    if (pattern == null || flags != "") {
        return unsupportedRegExp("Only constant RegExp patterns without flags are supported")
    }
    if (parseRegExpPattern(pattern) == null) {
        return unsupportedRegExp("RegExp pattern requires unsupported syntax or backtracking: $pattern")
    }

    scope.doWithState {
        regularExpressions += reference to pattern
        initializeRegExpField(reference = reference, name = "lastIndex", value = ctx.mkFp64(0.0))
    }
    return TsExprApproximationResult.SuccessfulApproximation(reference)
}

private fun TsExprResolver.testRegExp(
    reference: UConcreteHeapRef,
    stmt: TsVirtualMethodCallStmt,
): TsExprApproximationResult {
    val string = stmt.args.firstOrNull()
    val result = computeRegExpMatch(reference, string) ?: return TsExprApproximationResult.ResolveFailure
    return TsExprApproximationResult.SuccessfulApproximation(result.success)
}

private fun TsExprResolver.matchRegExp(
    reference: UConcreteHeapRef,
    stmt: TsVirtualMethodCallStmt,
): TsExprApproximationResult = with(ctx) {
    val result = computeRegExpMatch(reference, stmt.instance) ?: return TsExprApproximationResult.ResolveFailure
    val matched = scope.calcOnState { models.first().eval(result.success).isTrue }
    val selectedBranch = if (matched) result.success else mkNot(result.success)
    scope.fork(selectedBranch) ?: return TsExprApproximationResult.ResolveFailure
    if (!matched) return TsExprApproximationResult.SuccessfulApproximation(mkTsNullValue())

    val string = stmt.instance.asExpr(addressSort)
    val array = scope.calcOnState { createRegExpMatchArray(string, result) }
    TsExprApproximationResult.SuccessfulApproximation(array)
}

private fun TsExprResolver.computeRegExpMatch(
    reference: UConcreteHeapRef,
    value: UExpr<*>?,
): RegExpMatch? = with(ctx) {
    val overriddenExec = scope.calcOnState { (reference to "exec") in writtenConcreteFields }
    if (overriddenExec) {
        unsupportedRegExp("RegExp matching through a custom exec callback is unsupported")
        return null
    }

    val source = scope.calcOnState { regularExpressions[reference] }
    val pattern = source?.let(::parseRegExpPattern)
    if (pattern == null) {
        unsupportedRegExp("RegExp literal has not been initialized")
        return null
    }
    val string = value?.takeIf { it.sort == ctx.addressSort && !it.isFakeObject() }?.asExpr(ctx.addressSort)
    val bound = string?.let { scope.calcOnState { boundedStringBackingRefs[it] } }
    if (string == null || bound == null) {
        unsupportedRegExp(
            "RegExp matching requires a primitive string with modeled UTF-16 backing; coercion is unsupported"
        )
        return null
    }

    val length = scope.calcOnState { stringLength(string) }
    ensureLengthBounds(scope = scope, length = length, maxLength = bound) ?: return null
    return scope.calcOnState { RegExpMatcher(state = this, string = string, bound = bound).match(pattern) }
}

private fun TsState.createRegExpMatchArray(string: UHeapRef, result: RegExpMatch): UConcreteHeapRef = with(ctx) {
    val fullMatch = copyStringRange(receiver = string, from = result.start, to = result.end)
    val captures = result.captures.toSortedMap().values.map { capture ->
        val text = copyStringRange(receiver = string, from = capture.start, to = capture.end)
        mkIte(capture.present, text, mkUndefinedValue())
    }
    val array = memory.allocConcrete(regExpMatchArrayType)
    memory.initializeArray(
        arrayHeapRef = array,
        type = arrayDescriptorOf(regExpMatchArrayType),
        sort = addressSort,
        sizeSort = sizeSort,
        contents = (listOf(fullMatch) + captures).asSequence(),
    )
    markDenseInputArray(array = array, type = regExpMatchArrayType)
    markDenseArrayOwnSlots(array = array, length = mkBv(captures.size + 1), capacity = captures.size + 1)
    val index = mkBvToFpExpr(fp64Sort, fpRoundingModeSortDefaultValue(), result.start.cast(), signed = true)
    initializeRegExpField(reference = array, name = "index", value = index)
    initializeRegExpField(reference = array, name = "input", value = string)
    initializeRegExpField(reference = array, name = "groups", value = mkUndefinedValue())
    array
}

private fun <Sort : USort> TsState.initializeRegExpField(
    reference: UConcreteHeapRef,
    name: String,
    value: UExpr<Sort>,
) = with(ctx) {
    memory.write(mkFieldLValue(value.sort, reference, name), value, guard = trueExpr)
    writtenConcreteFields += reference to name
    writtenObjectLiteralFieldSorts = writtenObjectLiteralFieldSorts.put(reference to name, value.sort, ownership)
}

/** Runtime RegExp properties are independent of unavailable structural SDK declarations. */
internal fun TsContext.readRegExpField(scope: TsStepScope, reference: UConcreteHeapRef, name: String): UExpr<*>? {
    val source = scope.calcOnState { regularExpressions[reference] }
    if (source == null) return null

    return when (name) {
        "source" -> scope.calcOnState { mkInitializedStringConstant(source.ifEmpty { "(?:)" }) }
        "flags" -> scope.calcOnState { mkInitializedStringConstant("") }
        in regExpFlagProperties -> falseExpr
        else -> {
            val sort = scope.calcOnState { writtenObjectLiteralFieldSorts[reference to name] }
            if (sort == null) {
                scope.doWithState {
                    terminateAsUnsupported(reason = "REGULAR_EXPRESSION_LITERAL: RegExp property $name is unsupported")
                }
                null
            } else {
                scope.calcOnState { memory.read(mkFieldLValue(sort, reference, name)) }
            }
        }
    }
}

internal fun TsContext.writeRegExpField(
    scope: TsStepScope,
    reference: UConcreteHeapRef,
    name: String,
    value: UExpr<*>,
): Unit? {
    if (name in regExpReadOnlyProperties || name == "__proto__") {
        scope.doWithState {
            terminateAsUnsupported(reason = "REGULAR_EXPRESSION_LITERAL: RegExp property write $name is unsupported")
        }
        return null
    }

    scope.doWithState { initializeRegExpField(reference = reference, name = name, value = value) }
    return Unit
}

private fun TsExprResolver.unsupportedRegExp(detail: String): TsExprApproximationResult {
    stopRuntimeFeatureLimitation(reason = TsRuntimeFeatureLimitationReason.REGULAR_EXPRESSION_LITERAL, detail = detail)
    return TsExprApproximationResult.ResolveFailure
}
