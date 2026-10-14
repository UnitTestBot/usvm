package org.usvm.machine.expr

import io.mockk.mockk
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.usvm.UIteExpr
import org.usvm.UExpr
import org.usvm.UMachineOptions
import org.usvm.USort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.TsComponents
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.operator.TsBinaryOperator
import org.usvm.machine.operator.TsUnaryOperator
import org.usvm.machine.state.TsState
import org.usvm.machine.types.EtsFakeType
import org.usvm.machine.types.TsTypeSystem
import org.usvm.machine.types.mkFakeValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.URegistersStack
import org.usvm.util.EtsHierarchy
import org.usvm.util.boolToFp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/** Prepared symbolic API contracts; source reachability and replay are separate concerns. */
class ExprUtilConditionalValueTest {
    private val scene = EtsScene(projectFiles = emptyList())
    private val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = EtsHierarchy(scene))
    private val components = TsComponents(typeSystem, options = UMachineOptions(useSolverForForks = false))
    private val ctx = TsContext(scene = scene, components = components)
    private val state = TsState(
        ctx = ctx,
        ownership = MutabilityOwnership(),
        entrypoint = mockk(),
        maxStringLength = 2,
    )

    init {
        state.memory.types.allocate(ctx.mkTsNullValue().address, EtsNullType)
    }

    private val scope = TsStepScope(originalState = state, forkBlackList = mockk())

    @AfterEach
    fun closeContext() {
        components.close()
        ctx.close()
    }

    @Test
    fun `joined operator wrappers preserve false and zero truthiness`() = with(ctx) {
        val selected = mkRegisterReading(idx = 0, sort = boolSort)
        val numeric = boolToFp(selected)

        val joined = with(TsBinaryOperator.And) { ctx.resolve(numeric, falseExpr, scope) }
        val conditional = assertNotNull(joined)
        assertIs<UIteExpr<*>>(conditional)

        val supported = scope.ensureTruthinessSupported(conditional)
        val truthy = mkTruthyExpr(conditional, scope)
        val negated = with(TsUnaryOperator.Not) { ctx.resolve(conditional, scope) }

        assertNotNull(supported)
        assertEquals(falseExpr, truthy)
        assertEquals(trueExpr, negated)
    }

    @Test
    fun `joined operator wrappers compare represented null instead of storage identity`() = with(ctx) {
        val selected = mkRegisterReading(idx = 0, sort = boolSort)
        val numeric = boolToFp(selected)

        val joined = with(TsBinaryOperator.Or) { ctx.resolve(numeric, mkTsNullValue(), scope) }
        val conditional = assertNotNull(joined)
        assertIs<UIteExpr<*>>(conditional)

        val nullish = mkNullishExpr(conditional, scope)

        assertEquals(mkIte(selected, falseExpr, trueExpr), nullish)
    }

    @Test
    fun `nested wrapper results retain truthiness nullish and numeric selection`() = with(ctx) {
        val outer = mkRegisterReading(idx = 0, sort = boolSort)
        val inner = mkRegisterReading(idx = 1, sort = boolSort)
        val booleanWrapper = state.mkFakeValue(
            scope = null,
            boolValue = trueExpr,
            valueType = EtsFakeType.mkBool(ctx),
        )
        val numberWrapper = state.mkFakeValue(
            scope = null,
            fpValue = mkFp64(0.0),
            valueType = EtsFakeType.mkFp(ctx),
        )
        val conditional = mkIte(outer, mkIte(inner, booleanWrapper, numberWrapper), mkTsNullValue())
        assertIs<UIteExpr<*>>(conditional)

        val supported = scope.ensureTruthinessSupported(conditional)
        val truthy = mkTruthyExpr(conditional, scope)
        val nullish = mkNullishExpr(conditional, scope)
        val numeric = mkNumericExpr(conditional, scope)

        assertNotNull(supported)
        assertEquals(mkIte(outer, mkIte(inner, trueExpr, falseExpr), falseExpr), truthy)
        assertEquals(mkIte(outer, falseExpr, trueExpr), nullish)
        assertEquals(mkIte(outer, mkIte(inner, mkFp64(1.0), mkFp64(0.0)), mkFp64(0.0)), numeric)
    }

    @Test
    fun `wrappers used only by a guard are not conditional result wrappers`() = with(ctx) {
        val wrapper = state.mkFakeValue(
            scope = null,
            fpValue = mkFp64(7.0),
            valueType = EtsFakeType.mkFp(ctx),
        )
        val guardPredicateName = "wrapperDependency"
        val guardPredicate = mkFuncDecl(guardPredicateName, boolSort, listOf(addressSort))
        val selected = mkApp(guardPredicate, listOf(wrapper))
        val conditional = mkIte(selected, mkTsNullValue(), mkUndefinedValue())
        assertIs<UIteExpr<*>>(conditional)

        val supported = scope.ensureTruthinessSupported(conditional)
        val truthy = mkTruthyExpr(conditional, scope)
        val nullish = mkNullishExpr(conditional, scope)
        val numeric = mkNumericExpr(conditional, scope)

        assertFalse(conditional.hasFakeValueBranch())
        assertNotNull(supported)
        assertEquals(falseExpr, truthy)
        assertEquals(trueExpr, nullish)
        assertEquals(mkIte(selected, mkFp64(0.0), mkFp64NaN()), numeric)
    }

    @Test
    fun `inactive unmodeled reference payload does not reject a numeric wrapper`() = with(ctx) {
        val number = mkFp64(7.0)
        val unmodeledString = state.mkInitializedStringConstant(value = "42")
        val wrapper = state.mkFakeValue(
            scope = null,
            fpValue = number,
            refValue = unmodeledString,
            valueType = EtsFakeType.mkFp(ctx),
        )

        val numeric = mkNumericExpr(wrapper, scope)

        assertEquals(number, numeric)
        assertNull(state.unsupportedReason)
        assertEquals(emptyList(), scope.stepResult().forkedStates.toList())
    }

    @Test
    fun `numeric conversion retains a supported conditional wrapper and records its unsupported sibling`() = with(ctx) {
        val selected = mkRegisterReading(idx = 0, sort = boolSort)
        val number = mkFp64(7.0)
        val unmodeledString = state.mkInitializedStringConstant(value = "42")
        val numberWrapper = state.mkFakeValue(scope = null, fpValue = number, valueType = EtsFakeType.mkFp(ctx))
        val stringWrapper = state.mkFakeValue(
            scope = null,
            refValue = unmodeledString,
            valueType = EtsFakeType.mkRef(ctx),
        )
        val conditional = mkIte(selected, numberWrapper, stringWrapper)
        assertIs<UIteExpr<*>>(conditional)

        val numeric = mkNumericExpr(conditional, scope)
        val unsupported = scope.stepResult().forkedStates.single()

        assertEquals(mkIte(selected, number, mkFp64NaN()), numeric)
        assertNull(state.unsupportedReason)
        assertEquals(
            "Numeric conversion needs a modeled StringToNumber or ToPrimitive conversion",
            unsupported.unsupportedReason,
        )
    }

    @Test
    fun `binary arithmetic retains the numeric result of joined operator wrappers`() = with(ctx) {
        val selected = mkRegisterReading(idx = 0, sort = boolSort)
        val unmodeledString = state.mkInitializedStringConstant(value = "42")
        val joined = with(TsBinaryOperator.Or) { ctx.resolve(boolToFp(selected), unmodeledString, scope) }
        val conditional = assertNotNull(joined)
        assertIs<UIteExpr<*>>(conditional)

        val resolved = with(TsBinaryOperator.Sub) { ctx.resolve(conditional, mkFp64(1.0), scope) }
        val result = assertNotNull(resolved)
        val unsupported = scope.stepResult().forkedStates.single()

        assertEquals(mkFp64(0.0), evaluateSelection(expr = result, selectedValue = true))
        assertNull(state.unsupportedReason)
        assertEquals(
            "Numeric conversion needs a modeled StringToNumber or ToPrimitive conversion",
            unsupported.unsupportedReason,
        )
    }

    @Test
    fun `binary arithmetic retains a numeric conditional right operand`() = with(ctx) {
        val selected = mkRegisterReading(idx = 0, sort = boolSort)
        val unmodeledString = state.mkInitializedStringConstant(value = "42")
        val joined = with(TsBinaryOperator.Or) { ctx.resolve(boolToFp(selected), unmodeledString, scope) }
        val conditional = assertNotNull(joined)
        assertIs<UIteExpr<*>>(conditional)

        val resolved = with(TsBinaryOperator.Sub) { ctx.resolve(mkFp64(10.0), conditional, scope) }
        val result = assertNotNull(resolved)
        val unsupported = scope.stepResult().forkedStates.single()

        assertEquals(mkFp64(9.0), evaluateSelection(expr = result, selectedValue = true))
        assertNull(state.unsupportedReason)
        assertEquals(
            "Numeric conversion needs a modeled StringToNumber or ToPrimitive conversion",
            unsupported.unsupportedReason,
        )
    }

    @Test
    fun `numeric binary operators ignore inactive populated reference payloads on either side`() = with(ctx) {
        val selected = mkRegisterReading(idx = 0, sort = boolSort)
        val unmodeledString = state.mkInitializedStringConstant(value = "42")
        val kinds = EtsFakeType(
            boolTypeExpr = selected,
            fpTypeExpr = mkNot(selected),
            refTypeExpr = falseExpr,
        )
        val wrapper = state.mkFakeValue(
            scope = null,
            boolValue = trueExpr,
            fpValue = mkFp64(7.0),
            refValue = unmodeledString,
            valueType = kinds,
        )
        val operators = listOf(TsBinaryOperator.Add, TsBinaryOperator.Lt, TsBinaryOperator.Gt)

        operators.forEach { operator ->
            val leftResult = with(operator) { ctx.resolve(wrapper, mkFp64(10.0), scope) }
            val rightResult = with(operator) { ctx.resolve(mkFp64(10.0), wrapper, scope) }
            val left = assertNotNull(leftResult)
            val right = assertNotNull(rightResult)

            val leftBooleanExpected = when (operator) {
                TsBinaryOperator.Add -> mkFp64(11.0)
                TsBinaryOperator.Lt -> trueExpr
                TsBinaryOperator.Gt -> falseExpr
                else -> error("Unexpected test operator")
            }
            val leftNumberExpected = when (operator) {
                TsBinaryOperator.Add -> mkFp64(17.0)
                TsBinaryOperator.Lt -> trueExpr
                TsBinaryOperator.Gt -> falseExpr
                else -> error("Unexpected test operator")
            }
            val rightBooleanExpected = when (operator) {
                TsBinaryOperator.Add -> mkFp64(11.0)
                TsBinaryOperator.Lt -> falseExpr
                TsBinaryOperator.Gt -> trueExpr
                else -> error("Unexpected test operator")
            }
            val rightNumberExpected = when (operator) {
                TsBinaryOperator.Add -> mkFp64(17.0)
                TsBinaryOperator.Lt -> falseExpr
                TsBinaryOperator.Gt -> trueExpr
                else -> error("Unexpected test operator")
            }

            assertEquals(leftBooleanExpected, evaluateSelection(expr = left, selectedValue = true))
            assertEquals(leftNumberExpected, evaluateSelection(expr = left, selectedValue = false))
            assertEquals(rightBooleanExpected, evaluateSelection(expr = right, selectedValue = true))
            assertEquals(rightNumberExpected, evaluateSelection(expr = right, selectedValue = false))
        }

        assertNull(state.unsupportedReason)
        assertEquals(emptyList(), scope.stepResult().forkedStates.toList())
    }

    @Test
    fun `addition retains symbolic numeric kinds while rejecting the selected reference kind`() = with(ctx) {
        val selected = mkRegisterReading(idx = 0, sort = boolSort)
        val unmodeledString = state.mkInitializedStringConstant(value = "42")
        val kinds = EtsFakeType(
            boolTypeExpr = falseExpr,
            fpTypeExpr = selected,
            refTypeExpr = mkNot(selected),
        )
        val wrapper = state.mkFakeValue(
            scope = null,
            fpValue = mkFp64(7.0),
            refValue = unmodeledString,
            valueType = kinds,
        )

        val resolved = with(TsBinaryOperator.Add) { ctx.resolve(wrapper, mkFp64(1.0), scope) }
        val result = assertNotNull(resolved)
        val unsupported = scope.stepResult().forkedStates.single()

        assertEquals(mkFp64(8.0), evaluateSelection(expr = result, selectedValue = true))
        assertNull(state.unsupportedReason)
        assertEquals(
            "Numeric conversion needs a modeled StringToNumber or ToPrimitive conversion",
            unsupported.unsupportedReason,
        )
    }

    @Test
    fun `loose equality compares the selected right numeric payload`() = with(ctx) {
        val leftBooleanSelected = mkRegisterReading(idx = 0, sort = boolSort)
        val leftKinds = EtsFakeType(
            boolTypeExpr = leftBooleanSelected,
            fpTypeExpr = mkNot(leftBooleanSelected),
            refTypeExpr = falseExpr,
        )
        val rightKinds = EtsFakeType(
            boolTypeExpr = mkNot(leftBooleanSelected),
            fpTypeExpr = leftBooleanSelected,
            refTypeExpr = falseExpr,
        )
        val left = state.mkFakeValue(
            scope = null,
            boolValue = trueExpr,
            fpValue = mkFp64(2.0),
            valueType = leftKinds,
        )
        val right = state.mkFakeValue(
            scope = null,
            boolValue = falseExpr,
            fpValue = mkFp64(1.0),
            valueType = rightKinds,
        )

        val resolved = with(TsBinaryOperator.Eq) { ctx.resolve(left, right, scope) }
        val result = assertNotNull(resolved)

        assertEquals(trueExpr, evaluateSelection(expr = result, selectedValue = true))
        assertEquals(falseExpr, evaluateSelection(expr = result, selectedValue = false))
    }

    @Test
    fun `numeric binary support failures stay under the enclosing active guard`() = with(ctx) {
        val selected = mkRegisterReading(idx = 0, sort = boolSort)
        val unmodeledString = state.mkInitializedStringConstant(value = "42")

        val resolved = with(TsBinaryOperator.Add) {
            ctx.resolve(unmodeledString, mkFp64(1.0), scope, activeGuard = selected)
        }
        val result = assertNotNull(resolved)
        val unsupported = scope.stepResult().forkedStates.single()

        assertEquals(mkFp64NaN(), evaluateSelection(expr = result, selectedValue = false))
        assertNull(state.unsupportedReason)
        assertEquals(
            "Numeric conversion needs a modeled StringToNumber or ToPrimitive conversion",
            unsupported.unsupportedReason,
        )
    }

    private fun <Sort : USort> evaluateSelection(expr: UExpr<Sort>, selectedValue: Boolean): UExpr<Sort> {
        val registers = URegistersStack()
        registers.push(registersCount = 1)
        registers.writeRegister(index = 0, value = if (selectedValue) ctx.trueExpr else ctx.falseExpr)
        val evaluationMemory = object : UReadOnlyMemory<EtsType> by state.memory {
            override val stack = registers
        }
        val composer = ctx.composer(memory = evaluationMemory, ownership = MutabilityOwnership())

        return composer.compose(expr)
    }
}
