package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Disabled
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.neq
import kotlin.test.Test

class TryCatch : TsMethodTestRunner() {
    private val tsPath = "/samples/lang/TryCatch.ts"

    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `empty try-catch`() {
        val method = getMethod("emptyTryCatch")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `simple try-catch`() {
        val method = getMethod("simpleTryCatch")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `catch thrown value`() {
        val method = getMethod("catchThrownValue")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `catch thrown value with finally`() {
        val method = getMethod("catchThrownValueWithFinally")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 11 },
            invariants = arrayOf(
                { r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `conditional throw try-catch`() {
        val method = getMethod("conditionalThrowTryCatch")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { shouldThrow, r ->
                // exception path
                shouldThrow.value && (r eq 2)
            },
            { shouldThrow, r ->
                // normal path
                !shouldThrow.value && (r eq 1)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Disabled("ArkIR for nested try-catch is broken")
    @Test
    fun `nested try-catch`() {
        val method = getMethod("nestedTryCatch")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { shouldThrow, r ->
                // inner catch path
                shouldThrow.value && (r eq 2)
            },
            { shouldThrow, r ->
                // normal path
                !shouldThrow.value && (r eq 1)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `try-finally no exception`() {
        val method = getMethod("tryFinallyNoException")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 11 },
            invariants = arrayOf(
                { r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `try-finally with exception`() {
        val method = getMethod("tryFinallyWithException")
        // ```ts
        // try { result = 1; throw ...; } finally { result = result + 10; } return -2;
        // ```
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException }, // finally executes but exception propagates
            invariants = arrayOf(
                { r -> r is TsTestValue.TsException },
            )
        )
    }

    @Test
    fun `try-catch-finally`() {
        val method = getMethod("tryCatchFinally")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { shouldThrow, r ->
                // exception path: catch sets to 2, finally adds 10
                shouldThrow.value && (r eq 12)
            },
            { shouldThrow, r ->
                // normal path: try sets to 1, finally adds 10
                !shouldThrow.value && (r eq 11)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `catch with return`() {
        val method = getMethod("catchWithReturn")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `finally overrides return`() {
        val method = getMethod("finallyOverridesReturn")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { shouldThrow, r ->
                // exception path: finally overrides catch return
                shouldThrow.value && (r eq 100)
            },
            { shouldThrow, r ->
                // normal path: finally overrides try return
                !shouldThrow.value && (r eq 100)
            },
            invariants = arrayOf(
                { _, r -> r eq 100 }, // finally always wins
            )
        )
    }

    @Disabled("Nested try-catch is broken in ArkIR")
    @Test
    fun `rethrow in catch`() {
        val method = getMethod("rethrowInCatch")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `catch different types`() {
        val method = getMethod("catchDifferentTypes")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { value, r ->
                // throw Error object
                value is TsTestValue.TsNumber && (value eq 1) && (r eq 1)
            },
            { value, r ->
                // throw string
                value is TsTestValue.TsNumber && (value eq 2) && (r eq 1)
            },
            { value, r ->
                // throw number
                value is TsTestValue.TsNumber && (value eq 3) && (r eq 1)
            },
            { value, r ->
                // throw boolean
                value is TsTestValue.TsNumber && (value eq 4) && (r eq 1)
            },
            { value, r ->
                // throw null
                value is TsTestValue.TsNumber && (value eq 5) && (r eq 1)
            },
            { value, r ->
                // throw undefined
                value is TsTestValue.TsNumber && (value eq 6) && (r eq 1)
            },
            { value, r ->
                // no throw
                value is TsTestValue.TsNumber && (value neq 1) && (value neq 2) && (value neq 3)
                    && (value neq 4) && (value neq 5) && (value neq 6) && (r eq 2)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `multiple returns in try`() {
        val method = getMethod("multipleReturnsInTry")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, r ->
                // x < 0
                x.number < 0 && (r eq 1)
            },
            { x, r ->
                // x === 0
                (x eq 0) && (r eq 2)
            },
            { x, r ->
                // x > 0
                x.number > 0 && (r eq 3)
            },
            { x, r ->
                // x is NaN
                x.number.isNaN() && (r eq 4)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `finally with throw`() {
        val method = getMethod("finallyWithThrow")
        discoverProperties<TsTestValue>(
            method = method,
            { r -> r is TsTestValue.TsException },
            invariants = arrayOf(
                { r -> r is TsTestValue.TsException },
            )
        )
    }

    @Test
    fun `catch without variable`() {
        val method = getMethod("catchWithoutVariable")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 },
            invariants = arrayOf(
                { r -> r.number > 0 },
            )
        )
    }

    @Disabled("Nested try-catch is broken in ArkIR")
    @Test
    fun `conditional rethrow`() {
        val method = getMethod("conditionalRethrow")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { shouldRethrow, r ->
                // rethrow path
                shouldRethrow.value && (r eq 2)
            },
            { shouldRethrow, r ->
                // handle path
                !shouldRethrow.value && (r eq 1)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `multiple catch paths`() {
        val method = getMethod("multipleCatchPaths")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, r ->
                // x === 0, throws -> caught, returns 3
                (x eq 0) && (r eq 3)
            },
            { x, r ->
                // x < 0, returns 1
                x.number < 0 && (r eq 1)
            },
            { x, r ->
                // x > 0, throws -> caught, returns 3
                x.number > 0 && (r eq 3)
            },
            { x, r ->
                // x is NaN, returns 2
                x.number.isNaN() && (r eq 2)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `finally modifies variable`() {
        val method = getMethod("finallyModifiesVariable")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { shouldThrow, r ->
                // exception path: x = 10, then x = 30 in catch, then x = 35 in finally
                shouldThrow.value && (r eq 35)
            },
            { shouldThrow, r ->
                // normal path: x = 10, then x = 20, then x = 25 in finally
                !shouldThrow.value && (r eq 25)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `early return in finally`() {
        val method = getMethod("earlyReturnInFinally")
        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { r -> r eq 1 }, // finally always returns 1
            invariants = arrayOf(
                { r -> r.number > 0 },
            )
        )
    }

    // Realistic scenarios combining try-catch with other constructs

    @Disabled("Loops are broken in ArkIR")
    @Test
    fun `try-catch in loop`() {
        val method = getMethod("tryCatchInLoop")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { n, r ->
                // n <= 0: loop doesn't execute
                n.number <= 0 && (r eq 0)
            },
            { n, r ->
                // n === 1: sum = 0
                (n eq 1) && (r eq 0)
            },
            { n, r ->
                // n === 2: sum = 0 + 1 = 1
                (n eq 2) && (r eq 1)
            },
            { n, r ->
                // n === 3: sum = 0 + 1 + 2 = 3
                (n eq 3) && (r eq 3)
            },
            { n, r ->
                // n === 4: sum = 0 + 1 + 2 + 100 = 103
                (n eq 4) && (r eq 103)
            },
            { n, r ->
                // n === 5: sum = 0 + 1 + 2 + 100 + 4 = 107
                (n eq 5) && (r eq 107)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `try-catch with object access`() {
        val method = getMethod("tryCatchWithObjectAccess")
        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            // TODO: 'object' input type can't be null/undefined
            // { obj, r ->
            //     // null check
            //     obj is TsTestValue.TsNull && (r eq 1)
            // },
            // { obj, r ->
            //     // undefined check
            //     obj is TsTestValue.TsUndefined && (r eq 1)
            // },
            { obj, r ->
                // valid object, value === 42
                obj !is TsTestValue.TsNull && obj !is TsTestValue.TsUndefined && (r eq 2)
            },
            { obj, r ->
                // valid object, value !== 42
                obj !is TsTestValue.TsNull && obj !is TsTestValue.TsUndefined && (r eq 3)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `try-catch with array access`() {
        val method = getMethod("tryCatchWithArrayAccess")
        discoverProperties<TsTestValue, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            // TODO: `number[]` input type can't be null/undefined
            // { arr, _, r ->
            //     // null/undefined array - throws
            //     (arr is TsTestValue.TsNull || arr is TsTestValue.TsUndefined) && (r eq 4)
            // },
            // TODO: `number` inside `number[]` can't be undefined
            // { arr, _, r ->
            //     // value is undefined
            //     arr !is TsTestValue.TsNull && arr !is TsTestValue.TsUndefined && (r eq 1)
            // },
            { arr, _, r ->
                // value > 10
                arr !is TsTestValue.TsNull && arr !is TsTestValue.TsUndefined && (r eq 2)
            },
            { arr, _, r ->
                // value <= 10
                arr !is TsTestValue.TsNull && arr !is TsTestValue.TsUndefined && (r eq 3)
            },
            invariants = arrayOf(
                { _, _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `try-catch with function call`() {
        val method = getMethod("tryCatchWithFunctionCall")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { shouldFail, r ->
                // function throws
                shouldFail.value && (r eq 3)
            },
            { shouldFail, r ->
                // function returns 42 > 40
                !shouldFail.value && (r eq 1)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `try-catch with multiple conditions`() {
        val method = getMethod("tryCatchWithMultipleConditions")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, y, r ->
                // both negative - throws
                x.number < 0 && y.number < 0 && (r eq 5)
            },
            { x, y, r ->
                // one negative (but not both)
                (x.number < 0 && y.number >= 0 || x.number >= 0 && y.number < 0) && (r eq 1)
            },
            { x, y, r ->
                // both zero
                (x eq 0) && (y eq 0) && (r eq 2)
            },
            { x, y, r ->
                // both positive
                x.number > 0 && y.number > 0 && (r eq 3)
            },
            { x, y, r ->
                // mixed (one zero, one positive)
                ((x eq 0) && y.number > 0 || x.number > 0 && (y eq 0)) && (r eq 4)
            },
            invariants = arrayOf(
                { _, _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `try-catch in conditional`() {
        val method = getMethod("tryCatchInConditional")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { flag, x, r ->
                // flag is false
                !flag.value && (r eq 4)
            },
            { flag, x, r ->
                // flag is true, x === 0, throws
                flag.value && (x eq 0) && (r eq 3)
            },
            { flag, x, r ->
                // flag is true, x > 0
                flag.value && x.number > 0 && (r eq 1)
            },
            { flag, x, r ->
                // flag is true, x < 0
                flag.value && x.number < 0 && (r eq 2)
            },
            invariants = arrayOf(
                { _, _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `try-catch with return`() {
        val method = getMethod("tryCatchWithReturn")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, r ->
                // x < 0
                x.number < 0 && (r eq 1)
            },
            { x, r ->
                // x === 0, throws and caught
                (x eq 0) && (r eq 3)
            },
            { x, r ->
                // x > 0
                x.number > 0 && (r eq 2)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `try-catch with logical ops`() {
        val method = getMethod("tryCatchWithLogicalOps")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { a, b, r ->
                // both true - throws - caught
                (a.value && b.value) && (r eq 3)
            },
            { a, b, r ->
                // at least one true
                (a.value || b.value) && (r eq 1)
            },
            { a, b, r ->
                // both false
                (!a.value && !b.value) && (r eq 2)
            },
            invariants = arrayOf(
                { _, _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `finally with side effects`() {
        val method = getMethod("finallyWithSideEffects")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, r ->
                // x < 0: counter = 0+1, then +10 in catch = 11
                x.number < 0 && (r eq 11)
            },
            { x, r ->
                // x === 0: counter = 0+1+1 = 2
                (x eq 0) && (r eq 2)
            },
            { x, r ->
                // x > 0: counter = 0+1+1+1 = 3
                x.number > 0 && (r eq 3)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `exception in complex expression`() {
        val method = getMethod("exceptionInComplexExpression")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, y, r ->
                // temp === 0, throws
                (x.number > 0 && y.number > 0 && x.number + y.number == 0.0 ||
                    !(x.number > 0 && y.number > 0) && x.number - y.number == 0.0) && (r eq 3)
            },
            { x, y, r ->
                // temp > 0
                (x.number > 0 && y.number > 0 && x.number + y.number > 0 ||
                    !(x.number > 0 && y.number > 0) && x.number - y.number > 0) && (r eq 1)
            },
            { x, y, r ->
                // temp < 0
                (x.number > 0 && y.number > 0 && x.number + y.number < 0 ||
                    !(x.number > 0 && y.number > 0) && x.number - y.number < 0) && (r eq 2)
            },
            invariants = arrayOf(
                { _, _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `try-catch with ternary`() {
        val method = getMethod("tryCatchWithTernary")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, r ->
                // x > 10: value = 1
                x.number > 10 && (r eq 1)
            },
            { x, r ->
                // 0 < x <= 10: value = 2, throws
                x.number > 0 && x.number <= 10 && (r eq 5)
            },
            { x, r ->
                // x < -10: value = 3
                x.number < -10 && (r eq 3)
            },
            { x, r ->
                // -10 <= x <= 0: value = 4
                x.number >= -10 && x.number <= 0 && (r eq 4)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `exception after multiple ops`() {
        val method = getMethod("exceptionAfterMultipleOps")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, r ->
                // (x + 5) * 2 > 20, i.e., x > 5
                (x.number + 5) * 2 > 20 && (r eq 100)
            },
            { x, r ->
                // (x + 5) * 2 <= 20, returns (x + 5) * 2 - 3
                (x.number + 5) * 2 <= 20 && (r eq ((x.number + 5) * 2 - 3))
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `try-catch with early exit`() {
        val method = getMethod("tryCatchWithEarlyExit")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, _, r ->
                // x === 0, early exit
                (x eq 0) && (r eq 1)
            },
            { x, y, r ->
                // x !== 0, y < 0, throws
                !(x eq 0) && y.number < 0 && (r eq 4)
            },
            { x, y, r ->
                // x !== 0, y === 0
                !(x eq 0) && (y eq 0) && (r eq 2)
            },
            { x, y, r ->
                // x !== 0, y > 0
                !(x eq 0) && y.number > 0 && (r eq 3)
            },
            invariants = arrayOf(
                { _, _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `finally does not catch exception`() {
        val method = getMethod("finallyDoesNotCatchException")
        discoverProperties<TsTestValue.TsBoolean, TsTestValue>(
            method = method,
            { shouldThrow, r ->
                // exception propagates
                shouldThrow.value && r is TsTestValue.TsException
            },
            { shouldThrow, r ->
                // normal return
                !shouldThrow.value && r is TsTestValue.TsNumber && (r eq 1)
            },
        )
    }

    @Test
    fun `multiple finally blocks`() {
        val method = getMethod("multipleFinallyBlocks")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, r ->
                // x < 0: result = 1, +10 in finally, +1000 in catch = 1011
                x.number < 0 && (r eq 1011)
            },
            { x, r ->
                // x >= 0: result = 1, then 2, +10 in finally, +100 after inner try = 112
                x.number >= 0 && (r eq 112)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }

    @Test
    fun `try-catch swallows exception`() {
        val method = getMethod("tryCatchSwallowsException")
        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { x, r ->
                // x < 0, exception swallowed
                x.number < 0 && (r eq 2)
            },
            { x, r ->
                // x >= 0, normal return
                x.number >= 0 && (r eq 1)
            },
            invariants = arrayOf(
                { _, r -> r.number > 0 },
            )
        )
    }
}
