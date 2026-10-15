package org.usvm.machine.types

import io.mockk.mockk
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.usvm.UIteExpr
import org.usvm.UMachineOptions
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.TsComponents
import org.usvm.machine.TsContext
import org.usvm.machine.state.TsState
import org.usvm.util.EtsHierarchy
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/** Prepared-state extraction contracts; source reachability and replay are separate concerns. */
class FakeExprUtilTest {
    private val scene = EtsScene(projectFiles = emptyList())
    private val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = EtsHierarchy(scene))
    private val components = TsComponents(typeSystem, options = UMachineOptions())
    private val ctx = TsContext(scene = scene, components = components)
    private val state = TsState(
        ctx = ctx,
        ownership = MutabilityOwnership(),
        entrypoint = mockk(),
        maxStringLength = 0,
    )

    @AfterEach
    fun closeContext() {
        components.close()
        ctx.close()
    }

    @Test
    fun `conditional number wrappers retain numeric payloads and kind`() = with(ctx) {
        val selected = mkRegisterReading(idx = 0, sort = boolSort)
        val trueNumber = mkFp64(7.0)
        val falseNumber = mkFp64(11.0)
        val trueWrapper = state.mkFakeValue(
            scope = null,
            fpValue = trueNumber,
            valueType = EtsFakeType.mkFp(ctx),
        )
        val falseWrapper = state.mkFakeValue(
            scope = null,
            fpValue = falseNumber,
            valueType = EtsFakeType.mkFp(ctx),
        )
        val conditional = mkIte(selected, trueWrapper, falseWrapper)

        val (number, numberKind) = state.extractValue(conditional, fp64Sort, ::getIntermediateFpLValue)
        val (_, referenceKind) = state.extractValue(conditional, addressSort, ::getIntermediateRefLValue)

        assertEquals(mkIte(selected, trueNumber, falseNumber), number)
        assertEquals(trueExpr, numberKind)
        assertEquals(falseExpr, referenceKind)
    }

    @Test
    fun `mixed wrappers retain complementary kinds without wrapper reference payloads`() = with(ctx) {
        val selected = mkRegisterReading(idx = 0, sort = boolSort)
        val numberWrapper = state.mkFakeValue(
            scope = null,
            fpValue = mkFp64(7.0),
            valueType = EtsFakeType.mkFp(ctx),
        )
        val reference = mkTsNullValue()
        val referenceWrapper = state.mkFakeValue(
            scope = null,
            refValue = reference,
            valueType = EtsFakeType.mkRef(ctx),
        )
        val conditional = mkIte(selected, numberWrapper, referenceWrapper)

        val (_, numberKind) = state.extractValue(conditional, fp64Sort, ::getIntermediateFpLValue)
        val (ref, referenceKind) = state.extractValue(conditional, addressSort, ::getIntermediateRefLValue)

        assertEquals(selected, numberKind)
        assertEquals(mkNot(selected), referenceKind)
        assertFalse(assertNotNull(ref).hasFakeValueBranch())
    }

    @Test
    fun `one available payload remains protected by the selected branch guard`() = with(ctx) {
        val selected = mkRegisterReading(idx = 0, sort = boolSort)
        val number = mkFp64(7.0)
        val wrapper = state.mkFakeValue(
            scope = null,
            fpValue = number,
            valueType = EtsFakeType.mkFp(ctx),
        )
        val reference = mkTsNullValue()
        val trueNumber = mkIte(selected, wrapper, reference)
        val falseNumber = mkIte(selected, reference, wrapper)

        val (truePayload, trueKind) = state.extractValue(trueNumber, fp64Sort, ::getIntermediateFpLValue)
        val (falsePayload, falseKind) = state.extractValue(falseNumber, fp64Sort, ::getIntermediateFpLValue)
        val (_, boolKind) = state.extractValue(trueNumber, boolSort, ::getIntermediateBoolLValue)

        assertEquals(number, truePayload)
        assertEquals(selected, trueKind)
        assertEquals(number, falsePayload)
        assertEquals(mkNot(selected), falseKind)
        assertEquals(falseExpr, boolKind)
    }

    @Test
    fun `nested conditional wrappers preserve every selector condition`() = with(ctx) {
        val outer = mkRegisterReading(idx = 0, sort = boolSort)
        val inner = mkRegisterReading(idx = 1, sort = boolSort)
        val number = mkFp64(7.0)
        val numberWrapper = state.mkFakeValue(
            scope = null,
            fpValue = number,
            valueType = EtsFakeType.mkFp(ctx),
        )
        val boolWrapper = state.mkFakeValue(
            scope = null,
            boolValue = trueExpr,
            valueType = EtsFakeType.mkBool(ctx),
        )
        val reference = mkTsNullValue()
        val conditional = mkIte(outer, mkIte(inner, numberWrapper, boolWrapper), reference)

        val (_, numberKind) = state.extractValue(conditional, fp64Sort, ::getIntermediateFpLValue)
        val (_, boolKind) = state.extractValue(conditional, boolSort, ::getIntermediateBoolLValue)
        val (ref, referenceKind) = state.extractValue(conditional, addressSort, ::getIntermediateRefLValue)

        assertEquals(mkIte(outer, inner, falseExpr), numberKind)
        assertEquals(mkIte(outer, mkNot(inner), falseExpr), boolKind)
        assertEquals(mkNot(outer), referenceKind)
        assertFalse(assertNotNull(ref).hasFakeValueBranch())
    }

    @Test
    fun `ordinary reference alternatives ignore wrappers used only by the guard`() = with(ctx) {
        val wrapper = state.mkFakeValue(
            scope = null,
            fpValue = mkFp64(7.0),
            valueType = EtsFakeType.mkFp(ctx),
        )
        val guardPredicateName = "wrapperGuard"
        val guardPredicate = mkFuncDecl(guardPredicateName, boolSort, listOf(addressSort))
        val selected = mkApp(guardPredicate, listOf(wrapper))
        val conditional = mkIte(selected, mkTsNullValue(), mkUndefinedValue())
        assertIs<UIteExpr<*>>(conditional)

        val (ref, referenceKind) = state.extractValue(conditional, addressSort, ::getIntermediateRefLValue)
        val (number, numberKind) = state.extractValue(conditional, fp64Sort, ::getIntermediateFpLValue)

        assertEquals(conditional, ref)
        assertEquals(trueExpr, referenceKind)
        assertNull(number)
        assertEquals(falseExpr, numberKind)
    }
}
