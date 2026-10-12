package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsGenericType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsTupleType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.usvm.api.evalTypeEquals
import org.usvm.api.targets.TsTarget
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.TsContext
import org.usvm.machine.TsGraph
import org.usvm.machine.TsOptions
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.mkNotNullOrUndefined
import org.usvm.machine.state.TsState
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.parametersWithThisCount
import org.usvm.machine.state.symbolicStringBackingConstraint
import org.usvm.machine.types.initialInputType
import org.usvm.machine.types.mkFakeValue
import org.usvm.machine.types.toAuxiliaryType
import org.usvm.sizeSort
import org.usvm.solver.UUnknownResult
import org.usvm.targets.UTargetsSet
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.type
import org.usvm.utils.ensureSat

private val logger = KotlinLogging.logger {}

internal fun createInitialState(
    ctx: TsContext,
    graph: TsGraph,
    options: TsOptions,
    method: EtsMethod,
    targets: List<TsTarget>,
    configureState: TsState.() -> Unit = {},
): TsState = with(ctx) {
    val state = TsState(
        ctx = ctx,
        ownership = MutabilityOwnership(),
        entrypoint = method,
        maxStringLength = options.maxArraySize,
        inputPropertyPresence = options.inputPropertyPresence,
        targets = UTargetsSet.from(targets),
    )

    state.callStack.push(method, returnSite = null)
    state.memory.stack.push(method.parametersWithThisCount, method.localsCount)
    state.newStmt(method.cfg.instructions.first())

    state.memory.types.allocate(mkTsNullValue().address, EtsNullType)

    // TODO check for statics
    val thisIdx = 0
    val thisInstanceRef = mkRegisterStackLValue(addressSort, thisIdx)
    val thisRef = state.memory.read(thisInstanceRef).asExpr(addressSort)

    state.pathConstraints += mkNotNullOrUndefined(thisRef)

    // TODO not equal but subtype for abstract/interfaces
    val enclosingClass = requireNotNull(method.enclosingClass) { "Initial state requires a method owner" }
    state.pathConstraints += state.memory.types.evalTypeEquals(thisRef, enclosingClass.type)

    val genericParameters = method.typeParameters.filterIsInstance<EtsGenericType>().associateBy { it.typeName }
    method.parameters.forEachIndexed { i, param ->
        val idx = i + 1 // +1 because 0 is reserved for `this`

        val ref by lazy {
            val lValue = mkRegisterStackLValue(addressSort, idx)
            state.memory.read(lValue).asExpr(addressSort)
        }

        val parameterType = param.type.initialInputType(genericParameters)
        if (parameterType is EtsRefType) {
            run {
                state.pathConstraints += mkNotNullOrUndefined(ref)

                if (parameterType is EtsArrayType) {
                    state.pathConstraints += state.memory.types.evalIsSubtype(ref, parameterType)

                    val lengthLValue = mkArrayLengthLValue(ref, parameterType)
                    val length = state.memory.read(lengthLValue).asExpr(sizeSort)
                    state.pathConstraints += mkBvSignedGreaterOrEqualExpr(length, mkBv(0))
                    state.pathConstraints += mkBvSignedLessOrEqualExpr(length, mkBv(options.maxArraySize))

                    return@run
                }

                // Tuple inputs are materialized as fixed-size arrays by domain-aware initial-state configurators.
                if (parameterType is EtsTupleType) {
                    return@run
                }

                val resolvedParameterType = graph.hierarchy.classesForType(parameterType)

                if (resolvedParameterType.isEmpty()) {
                    logger.error("Cannot resolve class for parameter type: $parameterType")
                    return@run // TODO should be an error
                }

                // Because of structural equality in TS we cannot determine the exact type
                // Therefore, we create information about the fields the type must consist
                val types = resolvedParameterType.mapNotNull { it.type.toAuxiliaryType(graph.hierarchy) }
                val auxiliaryType = EtsUnionType(types) // TODO error
                state.pathConstraints += state.memory.types.evalIsSubtype(ref, auxiliaryType)
            }
        }
        if (parameterType == EtsNullType) {
            state.pathConstraints += mkHeapRefEq(ref, mkTsNullValue())
        }
        if (parameterType == EtsUndefinedType) {
            state.pathConstraints += mkHeapRefEq(ref, mkUndefinedValue())
        }
        if (parameterType == EtsStringType) {
            state.pathConstraints += mkNotNullOrUndefined(ref)

            state.pathConstraints += state.memory.types.evalTypeEquals(ref, EtsStringType)

            state.pathConstraints += state.symbolicStringBackingConstraint(ref)
            state.boundedStringBackingRefs += ref to state.maxStringLength
        }

        val parameterSort = typeToSort(parameterType)
        if (parameterSort is TsUnresolvedSort) {
            // If the parameter type is unresolved, we create a fake object for it
            val bool = mkRegisterReading(idx, boolSort)
            val fp = mkRegisterReading(idx, fp64Sort)
            val referenceValue = mkRegisterReading(idx, addressSort)
            val fakeObject = state.mkFakeValue(
                scope = null,
                boolValue = bool,
                fpValue = fp,
                refValue = referenceValue,
            )
            val lValue = mkRegisterStackLValue(addressSort, idx)
            state.memory.write(lValue, fakeObject.asExpr(addressSort), guard = trueExpr)
            state.saveSortForLocal(idx, addressSort)
        } else {
            state.saveSortForLocal(idx, parameterSort)
        }
    }

    state.configureState()

    state.initializeModel(method)

    state
}

private fun TsState.initializeModel(method: EtsMethod) {
    val solver = ctx.solver<EtsType>()
    val solverResult = solver.check(pathConstraints)
    if (solverResult is UUnknownResult) {
        throw TsInitialStateSolverUnknownException(method)
    }

    val model = solverResult.ensureSat().model
    models = listOf(model)
}
