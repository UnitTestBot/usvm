package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsValue
import org.jacodb.ets.model.EtsVoidType
import org.jacodb.ets.utils.callExpr
import org.jacodb.ets.utils.getDeclaredLocals
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UInterpreter
import org.usvm.UIteExpr
import org.usvm.api.allocateArrayInitialized
import org.usvm.api.evalTypeEquals
import org.usvm.api.makeSymbolicPrimitive
import org.usvm.api.makeSymbolicRefUntyped
import org.usvm.api.targets.TsTarget
import org.usvm.api.typeStreamOf
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsConcreteMethodCallStmt
import org.usvm.machine.TsContext
import org.usvm.machine.TsGraph
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsOptions
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.machine.expr.TsExprResolver
import org.usvm.machine.expr.TsUnresolvedSort
import org.usvm.machine.expr.mkTruthyExpr
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.parametersWithThisCount
import org.usvm.machine.state.returnValue
import org.usvm.machine.types.EtsAuxiliaryType
import org.usvm.machine.types.mkFakeValue
import org.usvm.machine.types.toAuxiliaryType
import org.usvm.sizeSort
import org.usvm.targets.UTargetsSet
import org.usvm.types.TypesResult
import org.usvm.types.single
import org.usvm.util.EtsPropertyResolution
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.resolveEtsField
import org.usvm.util.resolveEtsMethods
import org.usvm.util.type
import org.usvm.utils.ensureSat

private val logger = KotlinLogging.logger {}

typealias TsStepScope = StepScope<TsState, EtsType, EtsStmt, TsContext>

@Suppress("UNUSED_PARAMETER")
class TsInterpreter(
    private val ctx: TsContext,
    private val graph: TsGraph,
    private val tsOptions: TsOptions,
    private val observer: TsInterpreterObserver? = null,
) : UInterpreter<TsState>() {

    private val forkBlackList: UForkBlackList<TsState, EtsStmt> = UForkBlackList.createDefault()

    override fun step(state: TsState): StepResult<TsState> {
        val stmt = state.lastStmt
        val scope = StepScope(state, forkBlackList)

        val result = state.methodResult
        if (result is TsMethodResult.TsException) {
            // TODO catch processing
            scope.doWithState {
                val returnSite = callStack.pop()

                if (callStack.isNotEmpty()) {
                    memory.stack.pop()
                }

                if (returnSite != null) {
                    newStmt(returnSite)
                }
            }

            return scope.stepResult()
        }

        // TODO: rewrite the main loop (step) as follows:
        //  check methodResult
        //  if success, reset the call (NoCall), return the result
        //  if exception, see above
        //  if no call, visit

        try {
            when (stmt) {
                is TsVirtualMethodCallStmt -> visitVirtualMethodCall(scope, stmt)
                is TsConcreteMethodCallStmt -> visitConcreteMethodCall(scope, stmt)
                is EtsIfStmt -> visitIfStmt(scope, stmt)
                is EtsReturnStmt -> visitReturnStmt(scope, stmt)
                is EtsAssignStmt -> visitAssignStmt(scope, stmt)
                is EtsCallStmt -> visitCallStmt(scope, stmt)
                is EtsThrowStmt -> visitThrowStmt(scope, stmt)
                is EtsNopStmt -> visitNopStmt(scope, stmt)
                else -> {
                    logger.error { "Unknown stmt: $stmt" }
                    scope.doWithState {
                        newStmt(graph.successors(stmt).single())
                    }
                }
            }
        } catch (e: Exception) {
            logger.error {
                "Exception: $e\n${e.stackTrace.take(3).joinToString("\n") { "    $it" }}"
            }
            return StepResult(forkedStates = emptySequence(), originalStateAlive = false)
            // todo are exceptional states properly removed?
        }

        return scope.stepResult()
    }

    private fun visitVirtualMethodCall(scope: TsStepScope, stmt: TsVirtualMethodCallStmt) = with(ctx) {

        // NOTE: USE '.callee' INSTEAD OF '.method' !!!

        val instance = requireNotNull(stmt.instance) { "Virtual code invocation with null as an instance" }
        val concreteRef = scope.calcOnState { models.first().eval(instance) }

        val uncoveredInstance = if (concreteRef.isFakeObject()) {
            // TODO support primitives calls
            // We ignore the possibility of method call on primitives.
            // Therefore, the fake object should be unwrapped.
            scope.assert(concreteRef.getFakeType(scope).refTypeExpr)
            concreteRef.extractRef(scope)
        } else {
            concreteRef
        }

        // Evaluate uncoveredInstance in a model to avoid too wide type streams later
        val resolvedInstance = scope.calcOnState { models.first().eval(uncoveredInstance) }

        val concreteMethods: MutableList<EtsMethod> = mutableListOf()

        // TODO: handle 'instance.isFakeObject()'

        if (isAllocatedConcreteHeapRef(resolvedInstance)) {
            val type = scope.calcOnState { memory.typeStreamOf(resolvedInstance) }.single()
            if (type is EtsClassType) {
                val classes = graph.hierarchy.classesForType(type)
                if (classes.isEmpty()) {
                    logger.warn { "Could not resolve class: ${type.typeName}" }
                    scope.assert(falseExpr)
                    return
                }
                if (classes.size > 1) {
                    logger.warn { "Multiple classes with name: ${type.typeName}" }
                    scope.assert(falseExpr)
                    return
                }
                val cls = classes.single()
                val method = cls.methods
                    .singleOrNull { it.name == stmt.callee.name }
                    ?: run {
                        TODO("Overloads are not supported yet")
                    }
                concreteMethods += method
            } else {
                logger.warn {
                    "Could not resolve method: ${stmt.callee} on type: $type"
                }
                scope.assert(falseExpr)
                return
            }
        } else {
            val methods = resolveEtsMethods(stmt.callee)
            if (methods.isEmpty()) {
                if (stmt.callee.name !in listOf("then")) {
                    logger.warn { "Could not resolve method: ${stmt.callee}" }
                }
                scope.assert(falseExpr)
                return
            }
            concreteMethods += methods
        }


        val possibleTypes = scope.calcOnState { models.first().typeStreamOf(resolvedInstance as UConcreteHeapRef) }
            .take(1000)

        if (possibleTypes !is TypesResult.SuccessfulTypesResult) {
            error("TODO")// is it right?
        }

        val possibleTypesSet = possibleTypes.types.toSet()
        val methodsDeclaringClasses = concreteMethods.mapNotNull { it.enclosingClass } // is it right?
        val typeSystem = typeSystem<EtsType>()

        // take only possible classes with a corresponding method
        val intersectedTypes = possibleTypesSet.filter { possibleType ->
            methodsDeclaringClasses.any { typeSystem.isSupertype(it.type, possibleType) }
        }

        //
        val chosenMethods = intersectedTypes.flatMap {
            graph.hierarchy.classesForType(it as EtsRefType)
                .asSequence()
                // TODO wrong order, ancestors are unordered
                .map { graph.hierarchy.getAncestors(it) }
                .map { it.first { it.methods.any { it.name == stmt.callee.name } } }
                .map { it.methods.first { it.name == stmt.callee.name } }
        }.toList().take(10) // TODO check it

        // logger.info {
        // "Preparing to fork on ${limitedConcreteMethods.size} methods out of ${concreteMethods.size}: ${
        //     limitedConcreteMethods.map { "${it.signature.enclosingClass.name}::${it.name}" }
        // }"
        // }

        val conditionsWithBlocks = chosenMethods.mapIndexed { i, method ->
            val concreteCall = stmt.toConcrete(method)
            val block = { state: TsState -> state.newStmt(concreteCall) }
            val type = requireNotNull(method.enclosingClass).type

            val constraint = scope.calcOnState {
                val ref = stmt.instance.asExpr(addressSort)
                    .takeIf { !it.isFakeObject() }
                    ?: uncoveredInstance.asExpr(addressSort)

                // TODO: adhoc: "expand" ITE
                if (ref is UIteExpr<*>) {
                    val trueBranch = ref.trueBranch
                    val falseBranch = ref.falseBranch
                    if (trueBranch.isFakeObject() || falseBranch.isFakeObject()) {
                        val unwrappedTrueExpr = if (trueBranch.isFakeObject()) {
                            scope.assert(trueBranch.getFakeType(scope).refTypeExpr)
                            trueBranch.extractRef(scope)
                        } else {
                            trueBranch.asExpr(addressSort)
                        }
                        val unwrappedFalseExpr = if (falseBranch.isFakeObject()) {
                            scope.assert(falseBranch.getFakeType(scope).refTypeExpr)
                            falseBranch.extractRef(scope)
                        } else {
                            falseBranch.asExpr(addressSort)
                        }
                        return@calcOnState mkIte(
                            condition = ref.condition,
                            trueBranch = memory.types.evalIsSubtype(unwrappedTrueExpr, type),
                            falseBranch = memory.types.evalIsSubtype(unwrappedFalseExpr, type),
                        )
                    }
                }

                // TODO mistake, should be separated into several hierarchies
                //      or evalTypeEqual with several concrete types
                memory.types.evalIsSubtype(ref, type)
            }
            constraint to block
        }
        scope.forkMulti(conditionsWithBlocks)
    }

    private fun visitConcreteMethodCall(scope: TsStepScope, stmt: TsConcreteMethodCallStmt) {

        // NOTE: USE '.callee' INSTEAD OF '.method' !!!

        // TODO: observer

        val entryPoint = graph.entryPoints(stmt.callee).singleOrNull()

        if (entryPoint == null) {
            // logger.warn { "No entry point for method: ${stmt.callee}, mocking the call" }
            // If the method doesn't have entry points,
            // we go through it, we just mock the call
            mockMethodCall(scope, stmt.callee.signature)
            scope.doWithState {
                newStmt(stmt.returnSite)
            }
            return
        }

        scope.doWithState {
            val args = mutableListOf<UExpr<*>>()
            val numActual = stmt.args.size
            val numFormal = stmt.callee.parameters.size

            // vararg call:
            // function f(x: any, ...args: any[]) {}
            //   f(1, 2, 3) -> f(1, [2, 3])
            //   f(1, 2) -> f(1, [2])
            //   f(1) -> f(1, [])
            //   f() -> f(undefined, [])

            // normal call:
            // function g(x: any, y: any) {}
            //   g(1, 2) -> g(1, 2)
            //   g(1) -> g(1, undefined)
            //   g() -> g(undefined, undefined)
            //   g(1, 2, 3) -> g(1, 2)

            if (stmt.callee.parameters.isNotEmpty() && stmt.callee.parameters.last().isRest) {
                // vararg call

                // first n-1 args are normal
                val numNormal = numFormal - 1
                args += stmt.args.take(numNormal)

                // fill missing normal args with undefined
                repeat(numNormal - numActual) {
                    args += ctx.mkUndefinedValue()
                }

                // wrap rest args in array
                val content = stmt.args.drop(numNormal).map {
                    with(ctx) { it.toFakeObject(scope) }
                }
                val array = scope.calcOnState {
                    // In a common case we cannot determine the type of the array
                    val type = EtsArrayType(EtsUnknownType, dimensions = 1)
                    memory.allocateArrayInitialized(
                        type = ctx.arrayDescriptorOf(type),
                        sort = ctx.addressSort,
                        sizeSort = ctx.sizeSort,
                        contents = content.asSequence(),
                    )
                }
                args += array
            } else {
                // normal call

                // ignore extra args
                args += stmt.args.take(numFormal)

                // fill missing args with undefined
                repeat(numFormal - numActual) {
                    args += ctx.mkUndefinedValue()
                }
            }

            check(args.size == numFormal) {
                "Expected $numFormal arguments, got ${args.size}"
            }

            args += stmt.instance!!

            // TODO: re-check push sorts for arguments
            pushSortsForActualArguments(args)
            callStack.push(stmt.callee, stmt.returnSite)
            memory.stack.push(args.toTypedArray(), stmt.callee.localsCount)
            newStmt(entryPoint)
        }
    }

    private fun visitIfStmt(scope: TsStepScope, stmt: EtsIfStmt) {
        val exprResolver = exprResolverWithScope(scope)

        val simpleValueResolver = exprResolver.simpleValueResolver
        observer?.onIfStatement(simpleValueResolver, stmt, scope)

        val expr = exprResolver.resolve(stmt.condition) ?: return

        val boolExpr = if (expr.sort == ctx.boolSort) {
            expr.asExpr(ctx.boolSort)
        } else {
            ctx.mkTruthyExpr(expr, scope)
        }

        observer?.onIfStatementWithResolvedCondition(simpleValueResolver, stmt, boolExpr, scope)

        val successors = graph.successors(stmt).take(2).toList()

        // We treat situations when if stmt doesn't have exactly two successors as a bug.
        // Kill the state with such error.
        if (successors.size != 2) {
            logger.error {
                "Incorrect CFG, an If stmt has only ${successors.size} successors, but 2 were expected"
            }
            scope.assert(ctx.falseExpr)
            return
        }

        val (posStmt, negStmt) = graph.successors(stmt).take(2).toList()

        scope.forkWithBlackList(
            boolExpr,
            posStmt,
            negStmt,
            blockOnTrueState = { newStmt(posStmt) },
            blockOnFalseState = { newStmt(negStmt) },
        )
    }

    private fun visitReturnStmt(scope: TsStepScope, stmt: EtsReturnStmt) {
        val exprResolver = exprResolverWithScope(scope)

        observer?.onReturnStatement(exprResolver.simpleValueResolver, stmt, scope)

        val valueToReturn = stmt.returnValue
            ?.let { exprResolver.resolve(it) ?: return }
            ?: ctx.mkUndefinedValue()

        scope.doWithState {
            returnValue(valueToReturn)
        }
    }

    private fun visitAssignStmt(scope: TsStepScope, stmt: EtsAssignStmt) = with(ctx) {
        val exprResolver = exprResolverWithScope(scope)

        stmt.callExpr?.let {
            val methodResult = scope.calcOnState { methodResult }

            when (methodResult) {
                is TsMethodResult.NoCall -> observer?.onCallWithUnresolvedArguments(
                    exprResolver.simpleValueResolver,
                    it,
                    scope
                )

                is TsMethodResult.Success -> observer?.onAssignStatement(
                    exprResolver.simpleValueResolver,
                    stmt,
                    scope
                )

                is TsMethodResult.TsException -> error("Exceptions must be processed earlier")
            }

            if (it is EtsPtrCallExpr) {
                mockMethodCall(scope, it.callee)
                return
            }

            if (!tsOptions.interproceduralAnalysis && methodResult == TsMethodResult.NoCall) {
                mockMethodCall(scope, it.callee)
                return
            }
        } ?: observer?.onAssignStatement(exprResolver.simpleValueResolver, stmt, scope)

        val expr = exprResolver.resolve(stmt.rhv) ?: return

        check(expr.sort != unresolvedSort) {
            "A value of the unresolved sort should never be returned from `resolve` function"
        }

        scope.doWithState {
            when (val lhv = stmt.lhv) {
                is EtsLocal -> {
                    val idx = mapLocalToIdx(lastEnteredMethod, lhv)

                    // If we found the corresponding index, process it as usual.
                    // Otherwise, process it as a field of the global object.
                    if (idx != null) {
                        saveSortForLocal(idx, expr.sort)

                        val lValue = mkRegisterStackLValue(expr.sort, idx)
                        memory.write(lValue, expr.asExpr(lValue.sort), guard = trueExpr)
                    } else {
                        val lValue = mkFieldLValue(expr.sort, globalObject, lhv.name)
                        addedArtificialLocals += lhv.name
                        memory.write(lValue, expr.asExpr(lValue.sort), guard = trueExpr)
                    }
                }

                is EtsArrayAccess -> {
                    val instance = exprResolver.resolve(lhv.array)?.asExpr(addressSort) ?: return@doWithState
                    exprResolver.checkUndefinedOrNullPropertyRead(instance) ?: return@doWithState

                    val index = exprResolver.resolve(lhv.index)?.asExpr(fp64Sort) ?: return@doWithState

                    // TODO fork on floating point field
                    val bvIndex = mkFpToBvExpr(
                        roundingMode = fpRoundingModeSortDefaultValue(),
                        value = index,
                        bvSize = 32,
                        isSigned = true
                    ).asExpr(sizeSort)

                    // We don't allow access by negative indices and treat is as an error.
                    exprResolver.checkNegativeIndexRead(bvIndex) ?: return@doWithState

                    // TODO: handle the case when `lhv.array.type` is NOT an array.
                    //  In this case, it could be created manually: `EtsArrayType(EtsUnknownType, 1)`.
                    val arrayType = lhv.array.type as? EtsArrayType
                        ?: error("Expected EtsArrayType, got: ${lhv.array.type}")
                    val lengthLValue = mkArrayLengthLValue(instance, arrayType)
                    val currentLength = memory.read(lengthLValue)

                    // We allow readings from the array only in the range [0, length - 1].
                    exprResolver.checkReadingInRange(bvIndex, currentLength) ?: return@doWithState

                    val elementSort = typeToSort(arrayType.elementType)

                    if (elementSort is TsUnresolvedSort) {
                        val fakeExpr = expr.toFakeObject(scope)

                        val lValue = mkArrayIndexLValue(
                            addressSort,
                            instance,
                            bvIndex.asExpr(sizeSort),
                            arrayType
                        )

                        lValuesToAllocatedFakeObjects += lValue to fakeExpr

                        memory.write(lValue, fakeExpr, guard = trueExpr)
                    } else {
                        val lValue = mkArrayIndexLValue(
                            elementSort,
                            instance,
                            bvIndex.asExpr(sizeSort),
                            arrayType
                        )

                        memory.write(lValue, expr.asExpr(elementSort), guard = trueExpr)
                    }
                }

                is EtsInstanceFieldRef -> {
                    val instance = exprResolver.resolve(lhv.instance)?.asExpr(addressSort) ?: return@doWithState
                    exprResolver.checkUndefinedOrNullPropertyRead(instance) ?: return@doWithState

                    val etsField = resolveEtsField(lhv.instance, lhv.field, graph.hierarchy)
                    scope.doWithState {
                        // If we access some field, we expect that the object must have this field.
                        // It is not always true for TS, but we decided to process it so.
                        val supertype = EtsAuxiliaryType(properties = setOf(lhv.field.name))
                        pathConstraints += memory.types.evalIsSubtype(instance, supertype)
                    }

                    // If there is no such field, we create a fake field for the expr
                    val sort = when (etsField) {
                        is EtsPropertyResolution.Empty -> unresolvedSort
                        is EtsPropertyResolution.Unique -> typeToSort(etsField.property.type)
                        is EtsPropertyResolution.Ambiguous -> unresolvedSort
                    }

                    if (sort == unresolvedSort) {
                        val fakeObject = expr.toFakeObject(scope)
                        val lValue = mkFieldLValue(addressSort, instance, lhv.field)

                        lValuesToAllocatedFakeObjects += lValue to fakeObject

                        memory.write(lValue, fakeObject, guard = trueExpr)
                    } else {
                        val lValue = mkFieldLValue(sort, instance, lhv.field)
                        if (lValue.sort != expr.sort) {
                            if (expr.isFakeObject()) {
                                val lhvType = lhv.type
                                val value = when (lhvType) {
                                    is EtsBooleanType -> {
                                        scope.calcOnState {
                                            pathConstraints += expr.getFakeType(scope).boolTypeExpr
                                            expr.extractBool(scope)
                                        }
                                    }

                                    is EtsNumberType -> {
                                        scope.calcOnState {
                                            pathConstraints += expr.getFakeType(scope).fpTypeExpr
                                            expr.extractFp(scope)
                                        }
                                    }

                                    else -> {
                                        scope.calcOnState {
                                            pathConstraints += expr.getFakeType(scope).refTypeExpr
                                            expr.extractRef(scope)
                                        }
                                    }
                                }

                                memory.write(lValue, value.asExpr(lValue.sort), guard = trueExpr)
                            } else {
                                TODO("Support enums fields")
                            }
                        } else {
                            memory.write(lValue, expr.asExpr(lValue.sort), guard = trueExpr)
                        }
                    }
                }

                is EtsStaticFieldRef -> {
                    val clazz = scene.projectAndSdkClasses.singleOrNull {
                        it.signature == lhv.field.enclosingClass
                    } ?: return@doWithState

                    val instance = scope.calcOnState { getStaticInstance(clazz) }

                    // TODO: initialize the static field first
                    //  Note: Since we are assigning to a static field, we can omit its initialization,
                    //        if it does not have any side effects.

                    val sort = run {
                        val fields = clazz.fields.filter { it.name == lhv.field.name }
                        if (fields.size == 1) {
                            val field = fields.single()
                            val sort = typeToSort(field.type)
                            return@run sort
                        }
                        unresolvedSort
                    }
                    if (sort == unresolvedSort) {
                        val lValue = mkFieldLValue(addressSort, instance, lhv.field.name)
                        val fakeObject = expr.toFakeObject(scope)

                        lValuesToAllocatedFakeObjects += lValue to fakeObject

                        memory.write(lValue, fakeObject, guard = trueExpr)
                    } else {
                        val lValue = mkFieldLValue(sort, instance, lhv.field.name)
                        memory.write(lValue, expr.asExpr(lValue.sort), guard = trueExpr)
                    }
                }

                else -> TODO("Not yet implemented")
            }

            val nextStmt = stmt.nextStmt ?: return@doWithState
            newStmt(nextStmt)
        }
    }

    private fun visitCallStmt(scope: TsStepScope, stmt: EtsCallStmt) {
        if (scope.calcOnState { methodResult } is TsMethodResult.Success) {
            scope.doWithState {
                methodResult = TsMethodResult.NoCall
                newStmt(stmt.nextStmt!!)
            }
            return
        }

        if (stmt.expr is EtsPtrCallExpr) {
            mockMethodCall(scope, stmt.expr.callee)
            return
        }

        if (tsOptions.interproceduralAnalysis) {
            val exprResolver = exprResolverWithScope(scope)
            exprResolver.resolve(stmt.expr) ?: return
            val nextStmt = stmt.nextStmt ?: return
            scope.doWithState { newStmt(nextStmt) }
            return
        }

        // intraprocedural analysis
        mockMethodCall(scope, stmt.expr.callee)
    }

    private fun visitThrowStmt(scope: TsStepScope, stmt: EtsThrowStmt) {
        // TODO do not forget to pop the sorts call stack in the state
        val exprResolver = exprResolverWithScope(scope)
        observer?.onThrowStatement(exprResolver.simpleValueResolver, stmt, scope)
        TODO()
    }

    private fun visitNopStmt(scope: TsStepScope, stmt: EtsNopStmt) {
        // Do nothing
    }

    private fun exprResolverWithScope(scope: TsStepScope): TsExprResolver =
        TsExprResolver(
            ctx = ctx,
            scope = scope,
            localToIdx = ::mapLocalToIdx,
            hierarchy = graph.hierarchy,
        )

    // (method, localName) -> idx
    private val localVarToIdx: MutableMap<EtsMethod, Map<String, Int>> = hashMapOf()

    private fun mapLocalToIdx(method: EtsMethod, local: EtsValue): Int? =
        // Note: below, 'n' means the number of arguments
        when (local) {
            // Note: locals have indices starting from (n+1)
            is EtsLocal -> {
                val map = localVarToIdx.getOrPut(method) {
                    method.getDeclaredLocals().mapIndexed { idx, local ->
                        val localIdx = idx + method.parametersWithThisCount
                        local.name to localIdx
                    }.toMap()
                }
                map[local.name]
            }

            // Note: 'this' has index 'n'
            is EtsThis -> method.parameters.size

            // Note: arguments have indices from 0 to (n-1)
            is EtsParameterRef -> local.index

            else -> error("Unexpected local: $local")
        }

    fun getInitialState(method: EtsMethod, targets: List<TsTarget>): TsState = with(ctx) {
        val state = TsState(
            ctx = ctx,
            ownership = MutabilityOwnership(),
            entrypoint = method,
            targets = UTargetsSet.from(targets),
        )

        state.memory.types.allocate(mkTsNullValue().address, EtsNullType)

        val solver = ctx.solver<EtsType>()

        // TODO check for statics
        val thisIdx = mapLocalToIdx(method, EtsThis(method.enclosingClass!!.type))
            ?: error("Cannot find index for 'this' in method: $method")
        val thisInstanceRef = mkRegisterStackLValue(addressSort, thisIdx)
        val thisRef = state.memory.read(thisInstanceRef).asExpr(addressSort)

        state.pathConstraints += mkNot(mkHeapRefEq(thisRef, mkTsNullValue()))
        state.pathConstraints += mkNot(mkHeapRefEq(thisRef, mkUndefinedValue()))

        // TODO not equal but subtype for abstract/interfaces
        state.pathConstraints += state.memory.types.evalTypeEquals(thisRef, method.enclosingClass!!.type)

        method.parameters.forEachIndexed { i, param ->
            val ref by lazy {
                val lValue = mkRegisterStackLValue(addressSort, i)
                state.memory.read(lValue).asExpr(addressSort)
            }

            val parameterType = param.type
            if (parameterType is EtsRefType) {
                val argLValue = mkRegisterStackLValue(addressSort, i)
                val ref = state.memory.read(argLValue).asExpr(addressSort)
                val resolvedParameterType = graph.hierarchy.classesForType(parameterType)

                if (resolvedParameterType.isEmpty()) {
                    logger.error("Cannot resolve class for parameter type: $parameterType")
                    return@forEachIndexed // TODO should be an error
                }

                // Because of structural equality in TS we cannot determine the exact type
                // Therefore, we create information about the fields the type must consist
                val types = resolvedParameterType.mapNotNull { it.type.toAuxiliaryType(graph.hierarchy) }
                val auxiliaryType = EtsUnionType(types) // TODO error

                state.pathConstraints += state.memory.types.evalIsSubtype(ref, auxiliaryType)

                state.pathConstraints += mkNot(mkHeapRefEq(ref, mkTsNullValue()))
                state.pathConstraints += mkNot(mkHeapRefEq(ref, mkUndefinedValue()))
            }
            if (parameterType == EtsNullType) {
                state.pathConstraints += mkHeapRefEq(ref, mkTsNullValue())
            }
            if (parameterType == EtsUndefinedType) {
                state.pathConstraints += mkHeapRefEq(ref, mkUndefinedValue())
            }
            if (parameterType == EtsStringType) {
                state.pathConstraints += state.memory.types.evalTypeEquals(ref, EtsStringType)

                state.pathConstraints += mkNot(mkHeapRefEq(ref, mkTsNullValue()))
                state.pathConstraints += mkNot(mkHeapRefEq(ref, mkUndefinedValue()))
            }
        }

        val model = solver.check(state.pathConstraints).ensureSat().model
        state.models = listOf(model)

        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(method.parametersWithThisCount, method.localsCount)
        state.newStmt(method.cfg.instructions.first())

        state.memory.types.allocate(mkTsNullValue().address, EtsNullType)

        state
    }

    private fun mockMethodCall(scope: TsStepScope, method: EtsMethodSignature) {
        scope.doWithState {
            if (method.returnType is EtsVoidType) {
                methodResult = TsMethodResult.Success.MockedCall(method, ctx.mkUndefinedValue())
                return@doWithState
            }

            val resultSort = ctx.typeToSort(method.returnType)
            val resultValue = when (resultSort) {
                is UAddressSort -> makeSymbolicRefUntyped()

                is TsUnresolvedSort -> ctx.mkFakeValue(
                    scope,
                    makeSymbolicPrimitive(ctx.boolSort),
                    makeSymbolicPrimitive(ctx.fp64Sort),
                    makeSymbolicRefUntyped()
                )

                else -> makeSymbolicPrimitive(resultSort)
            }
            methodResult = TsMethodResult.Success.MockedCall(method, resultValue)
        }
    }

    // TODO: expand with interpreter implementation
    private val EtsStmt.nextStmt: EtsStmt?
        get() = graph.successors(this).firstOrNull()
}
