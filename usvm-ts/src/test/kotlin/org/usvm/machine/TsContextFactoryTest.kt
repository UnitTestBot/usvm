package org.usvm.machine

import io.mockk.mockk
import org.jacodb.ets.model.EtsAliasType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanLiteralType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsIntersectionType
import org.jacodb.ets.model.EtsNumberLiteralType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsRawType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.usvm.UIteExpr
import org.usvm.UMachineOptions
import org.usvm.api.initializeArrayLength
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsState
import org.usvm.machine.types.EtsFakeType
import org.usvm.machine.types.TsTypeSystem
import org.usvm.machine.types.mkFakeValue
import org.usvm.sizeSort
import org.usvm.util.EtsHierarchy
import org.usvm.util.copyArrayElements
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Prepared factory/storage contracts; source reachability and replay are separate concerns. */
class TsContextFactoryTest {
    private val scene = EtsScene(projectFiles = emptyList())
    private val typeSystem = TsTypeSystem(scene, typeOperationsTimeout = 1.seconds, hierarchy = EtsHierarchy(scene))
    private val components = TsComponents(typeSystem, options = UMachineOptions(useSolverForForks = false))
    private val ctx = TsContext(scene = scene, components = components)
    private val state = TsState(
        ctx = ctx,
        ownership = MutabilityOwnership(),
        entrypoint = mockk(),
        maxStringLength = 0,
    )
    private val scope = TsStepScope(originalState = state, forkBlackList = mockk())

    @AfterEach
    fun closeContext() {
        components.close()
        ctx.close()
    }

    @Test
    fun `fake provenance is separate from concrete address magnitude`() = with(ctx) {
        val firstAddressAboveFormerThreshold = 1_000_001
        val highOrdinaryRef = mkConcreteHeapRef(firstAddressAboveFormerThreshold)
        state.memory.types.allocate(highOrdinaryRef.address, EtsNumberType)

        val payload = mkFp64(7.0)
        val fakeRef = state.mkFakeValue(scope = null, fpValue = payload)
        val nextOrdinaryRef = state.memory.allocConcrete(EtsNumberType)

        assertFalse(highOrdinaryRef.isFakeObject())
        assertFalse(nextOrdinaryRef.isFakeObject())
        assertTrue(fakeRef.isFakeObject())
        assertNotEquals(fakeRef, nextOrdinaryRef)
        assertEquals(payload, fakeRef.extractFp(state.memory))
        assertEquals(trueExpr, fakeRef.getFakeType(state.memory).fpTypeExpr)

        assertFailsWith<IllegalArgumentException> { getIntermediateBoolLValue(highOrdinaryRef.address) }
        assertFailsWith<IllegalArgumentException> { getIntermediateFpLValue(highOrdinaryRef.address) }
        assertFailsWith<IllegalArgumentException> { getIntermediateRefLValue(highOrdinaryRef.address) }
    }

    @Test
    fun `boxing conditional number wrappers retains numeric payload and kind`() = with(ctx) {
        val selected = mkRegisterReading(idx = 0, sort = boolSort)
        val trueNumber = mkFp64(7.0)
        val falseNumber = mkFp64(11.0)
        val trueWrapper = state.mkFakeValue(scope = null, fpValue = trueNumber, valueType = EtsFakeType.mkFp(ctx))
        val falseWrapper = state.mkFakeValue(scope = null, fpValue = falseNumber, valueType = EtsFakeType.mkFp(ctx))
        val conditional = mkIte(selected, trueWrapper, falseWrapper)
        assertIs<UIteExpr<*>>(conditional)

        val boxed = conditional.toFakeObject(scope)
        val kind = boxed.getFakeType(state.memory)
        val payload = boxed.extractFp(state.memory)

        assertEquals(mkIte(selected, trueNumber, falseNumber), payload)
        assertEquals(trueExpr, kind.fpTypeExpr)
        assertEquals(falseExpr, kind.boolTypeExpr)
        assertEquals(falseExpr, kind.refTypeExpr)
    }

    @Test
    fun `boxing mixed wrappers retains complementary kinds and represented reference`() = with(ctx) {
        val selected = mkRegisterReading(idx = 0, sort = boolSort)
        val number = mkFp64(7.0)
        val zero = mkFp64(0.0)
        val undefined = mkUndefinedValue()
        val reference = mkTsNullValue()
        val numberWrapper = state.mkFakeValue(
            scope = null,
            boolValue = falseExpr,
            fpValue = number,
            refValue = undefined,
            valueType = EtsFakeType.mkFp(ctx),
        )
        val referenceWrapper = state.mkFakeValue(
            scope = null,
            boolValue = falseExpr,
            fpValue = zero,
            refValue = reference,
            valueType = EtsFakeType.mkRef(ctx),
        )
        val conditional = mkIte(selected, numberWrapper, referenceWrapper)
        assertIs<UIteExpr<*>>(conditional)

        val boxed = conditional.toFakeObject(scope)
        val kind = boxed.getFakeType(state.memory)
        val numberPayload = boxed.extractFp(state.memory)
        val referencePayload = boxed.extractRef(state.memory)

        assertEquals(selected, kind.fpTypeExpr)
        assertEquals(mkNot(selected), kind.refTypeExpr)
        assertEquals(falseExpr, kind.boolTypeExpr)
        assertEquals(mkIte(selected, number, zero), numberPayload)
        assertEquals(mkIte(selected, undefined, reference), referencePayload)
        assertFalse(referencePayload.hasFakeValueBranch())
    }

    @Test
    fun `boxing nested wrappers preserves one-sided payload guards`() = with(ctx) {
        val outer = mkRegisterReading(idx = 0, sort = boolSort)
        val inner = mkRegisterReading(idx = 1, sort = boolSort)
        val number = mkFp64(7.0)
        val zero = mkFp64(0.0)
        val undefined = mkUndefinedValue()
        val reference = mkTsNullValue()
        val numberWrapper = state.mkFakeValue(
            scope = null,
            boolValue = falseExpr,
            fpValue = number,
            refValue = undefined,
            valueType = EtsFakeType.mkFp(ctx),
        )
        val boolWrapper = state.mkFakeValue(
            scope = null,
            boolValue = trueExpr,
            fpValue = zero,
            refValue = undefined,
            valueType = EtsFakeType.mkBool(ctx),
        )
        val conditional = mkIte(outer, mkIte(inner, numberWrapper, boolWrapper), reference)
        assertIs<UIteExpr<*>>(conditional)

        val boxed = conditional.toFakeObject(scope)
        val kind = boxed.getFakeType(state.memory)
        val numberPayload = boxed.extractFp(state.memory)
        val referencePayload = boxed.extractRef(state.memory)

        assertEquals(mkIte(outer, inner, falseExpr), kind.fpTypeExpr)
        assertEquals(mkIte(outer, mkNot(inner), falseExpr), kind.boolTypeExpr)
        assertEquals(mkNot(outer), kind.refTypeExpr)
        assertEquals(mkIte(inner, number, zero), numberPayload)
        assertEquals(mkIte(outer, undefined, reference), referencePayload)
        assertFalse(referencePayload.hasFakeValueBranch())
    }

    @Test
    fun `boxing a direct wrapper preserves its identity`() = with(ctx) {
        val wrapper = state.mkFakeValue(
            scope = null,
            fpValue = mkFp64(7.0),
            valueType = EtsFakeType.mkFp(ctx),
        )

        val boxed = wrapper.toFakeObject(scope)

        assertSame(wrapper, boxed)
    }

    @Test
    fun `boxing ordinary references ignores wrappers used only in the condition`() = with(ctx) {
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

        val boxed = conditional.toFakeObject(scope)
        val kind = boxed.getFakeType(state.memory)
        val payload = boxed.extractRef(state.memory)

        assertEquals(conditional, payload)
        assertEquals(trueExpr, kind.refTypeExpr)
        assertEquals(falseExpr, kind.fpTypeExpr)
        assertEquals(falseExpr, kind.boolTypeExpr)
    }

    @Test
    fun `literal and alias arrays use their primitive physical descriptors`() {
        val numberLiteral = EtsNumberLiteralType(value = 7.0)
        val numberAlias = alias(name = "Numeric", originalType = numberLiteral)
        val nestedNumberAlias = alias(name = "WrappedNumeric", originalType = numberAlias)
        val booleanAlias = alias(name = "Boolean", originalType = EtsBooleanType)
        val elementTypes = listOf(
            EtsBooleanType,
            EtsNumberType,
            EtsBooleanLiteralType(value = true),
            numberLiteral,
            booleanAlias,
            nestedNumberAlias,
        )
        val booleanDescriptor = EtsArrayType(elementType = EtsBooleanType, dimensions = 1)
        val numberDescriptor = EtsArrayType(elementType = EtsNumberType, dimensions = 1)
        val expectedDescriptors = listOf(
            booleanDescriptor,
            numberDescriptor,
            booleanDescriptor,
            numberDescriptor,
            booleanDescriptor,
            numberDescriptor,
        )

        val descriptors = elementTypes.map { component ->
            ctx.arrayDescriptorOf(EtsArrayType(elementType = component, dimensions = 1))
        }

        assertEquals(expectedDescriptors, descriptors)
    }

    @Test
    fun `primitive intersections share storage and mixed or empty intersections stay unresolved`() {
        val numberIntersection = EtsIntersectionType(types = listOf(EtsNumberType, EtsNumberLiteralType(value = 7.0)))
        val booleanIntersection = EtsIntersectionType(
            types = listOf(EtsBooleanType, EtsBooleanLiteralType(value = true)),
        )
        val elementTypes = listOf(
            numberIntersection,
            alias(name = "Intersection", originalType = numberIntersection),
            booleanIntersection,
            EtsIntersectionType(types = listOf(EtsNumberType, EtsBooleanType)),
            EtsIntersectionType(types = emptyList()),
        )
        val numberDescriptor = EtsArrayType(elementType = EtsNumberType, dimensions = 1)
        val booleanDescriptor = EtsArrayType(elementType = EtsBooleanType, dimensions = 1)
        val referenceDescriptor = EtsArrayType(elementType = EtsUnknownType, dimensions = 1)
        val expectedDescriptors = listOf(
            numberDescriptor,
            numberDescriptor,
            booleanDescriptor,
            referenceDescriptor,
            referenceDescriptor,
        )

        val descriptors = elementTypes.map { component ->
            ctx.arrayDescriptorOf(EtsArrayType(elementType = component, dimensions = 1))
        }

        assertEquals(expectedDescriptors, descriptors)
    }

    @Test
    fun `unsupported and reference components retain nonthrowing reference descriptors`() {
        val raw = EtsRawType(kind = "unsupported")
        val constrained = EtsGenericType(typeName = "Constrained", constraint = EtsNumberType)
        val defaulted = EtsGenericType(typeName = "Defaulted", defaultType = EtsBooleanType)
        val elementTypes = listOf(
            raw,
            constrained,
            defaulted,
            alias(name = "Raw", originalType = raw),
            alias(name = "Constrained", originalType = constrained),
            alias(name = "Defaulted", originalType = defaulted),
            EtsIntersectionType(types = listOf(EtsNumberType, raw)),
            EtsIntersectionType(types = listOf(EtsBooleanType, defaulted)),
            EtsStringType,
            EtsUnknownType,
        )
        val expected = EtsArrayType(elementType = EtsUnknownType, dimensions = 1)

        val descriptors = elementTypes.map { component ->
            ctx.arrayDescriptorOf(EtsArrayType(elementType = component, dimensions = 1))
        }

        assertEquals(List(elementTypes.size) { expected }, descriptors)
    }

    @Test
    fun `logical array dimensions do not become physical payload dimensions`() {
        val rows = alias(
            name = "Rows",
            originalType = EtsArrayType(elementType = EtsNumberType, dimensions = 2),
        )
        val arrays = listOf(
            EtsArrayType(elementType = EtsNumberType, dimensions = 3),
            EtsArrayType(elementType = EtsBooleanLiteralType(value = true), dimensions = 2),
            EtsArrayType(elementType = rows, dimensions = 1),
        )
        val expected = EtsArrayType(elementType = EtsUnknownType, dimensions = 1)

        val descriptors = arrays.map(ctx::arrayDescriptorOf)

        assertEquals(List(arrays.size) { expected }, descriptors)
    }

    @Test
    fun `literal array copies use the base numeric payload and length regions`() = with(ctx) {
        val literalType = EtsArrayType(elementType = EtsNumberLiteralType(value = 7.0), dimensions = 1)
        val numberType = EtsArrayType(elementType = EtsNumberType, dimensions = 1)
        val source = state.memory.allocConcrete(literalType)
        val destination = state.memory.allocConcrete(numberType)
        val sourceIndex = mkBv(0)
        val destinationIndex = mkBv(1)
        val length = mkBv(1)
        val value = mkFp64(7.0)
        val sourceElement = mkArrayIndexLValue(fp64Sort, source, sourceIndex, literalType)
        state.memory.write(sourceElement, value, guard = trueExpr)
        state.memory.initializeArrayLength(source, arrayDescriptorOf(literalType), sizeSort, length)

        state.copyArrayElements(
            srcRef = source,
            dstRef = destination,
            arrayType = literalType,
            fromSrc = sourceIndex,
            fromDst = destinationIndex,
            length = length,
        )
        val copiedElement = mkArrayIndexLValue(fp64Sort, destination, destinationIndex, numberType)
        val copiedValue = state.memory.read(copiedElement)
        val sourceLength = state.memory.read(mkArrayLengthLValue(source, numberType))

        assertEquals(value, copiedValue)
        assertEquals(length, sourceLength)
    }

    private fun alias(name: String, originalType: EtsType): EtsAliasType =
        EtsAliasType(name = name, originalType = originalType, signature = mockk())
}
