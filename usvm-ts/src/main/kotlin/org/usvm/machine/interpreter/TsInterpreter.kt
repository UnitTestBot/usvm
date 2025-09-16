package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import mu.KotlinLogging
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLValue
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.callExpr
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UExpr
import org.usvm.UInterpreter
import org.usvm.UIteExpr
import org.usvm.api.evalTypeEquals
import org.usvm.api.initializeArray
import org.usvm.api.mockMethodCall
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
import org.usvm.machine.expr.handleAssignToArrayIndex
import org.usvm.machine.expr.handleAssignToInstanceField
import org.usvm.machine.expr.handleAssignToLocal
import org.usvm.machine.expr.handleAssignToStaticField
import org.usvm.machine.expr.mkTruthyExpr
import org.usvm.machine.expr.readGlobal
import org.usvm.machine.expr.writeGlobal
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.parametersWithThisCount
import org.usvm.machine.state.returnValue
import org.usvm.machine.types.mkFakeValue
import org.usvm.machine.types.toAuxiliaryType
import org.usvm.sizeSort
import org.usvm.targets.UTargetsSet
import org.usvm.types.TypesResult
import org.usvm.types.first
import org.usvm.types.single
import org.usvm.util.mkArrayIndexLValue
import org.usvm.util.mkArrayLengthLValue
import org.usvm.util.mkFieldLValue
import org.usvm.util.mkRegisterStackLValue
import org.usvm.util.resolveEtsMethods
import org.usvm.util.type
import org.usvm.utils.ensureSat

private val logger = KotlinLogging.logger {}

typealias TsStepScope = StepScope<TsState, EtsType, EtsStmt, TsContext>

@Suppress("UNUSED_PARAMETER")
class TsInterpreter(
    private val ctx: TsContext,
    private val graph: TsGraph,
    private val options: TsOptions,
    private val observer: TsInterpreterObserver? = null,
) : UInterpreter<TsState>() {

    private val forkBlackList: UForkBlackList<TsState, EtsStmt> = UForkBlackList.createDefault()

    override fun step(state: TsState): StepResult<TsState> {
        val stmt = state.lastStmt
        val scope = StepScope(state, forkBlackList)

        logger.info {
            "Step ${stmt.location.index} in ${state.lastEnteredMethod.name}: $stmt"
        }

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
                "Exception: $e\n${e.stackTrace.take(5).joinToString("\n") { "    $it" }}"
            }
            return StepResult(forkedStates = emptySequence(), originalStateAlive = false)
        }

        return scope.stepResult()
    }

    private fun visitVirtualMethodCall(scope: TsStepScope, stmt: TsVirtualMethodCallStmt) = with(ctx) {

        // NOTE: USE '.callee' INSTEAD OF '.method' !!!

        val instance = stmt.instance

        val unwrappedInstance = if (instance.isFakeObject()) {
            // TODO support primitives calls
            // We ignore the possibility of method call on primitives.
            // Therefore, the fake object should be unwrapped.
            scope.assert(instance.getFakeType(scope).refTypeExpr)
            instance.extractRef(scope)
        } else {
            instance.asExpr(addressSort)
        }

        val concreteMethods: MutableList<EtsMethod> = mutableListOf()

        if (isAllocatedConcreteHeapRef(unwrappedInstance)) {
            val type = scope.calcOnState { memory.typeStreamOf(unwrappedInstance) }.single()
            if (type is EtsClassType) {
                val classes = graph.hierarchy.classesForType(type)
                if (classes.isEmpty()) {
                    logger.warn { "Could not resolve class: ${type.typeName}" }
                    if (stmt.callee.name == CONSTRUCTOR_NAME) {
                        // Approximate unresolved constructor:
                        scope.doWithState {
                            methodResult = TsMethodResult.Success.MockedCall(unwrappedInstance, stmt.callee)
                            newStmt(stmt.returnSite)
                        }
                        return
                    }
                    scope.assert(falseExpr)
                    return
                }
                if (classes.size > 1) {
                    logger.warn { "Multiple (${classes.size}) classes with name '${type.typeName}'" }
                    // scope.assert(falseExpr)
                    // return
                    for (cls in classes) {
                        val suitableMethods = cls.methods.filter { it.name == stmt.callee.name }
                        concreteMethods += suitableMethods
                    }
                } else {
                    val cls = classes.single()
                    val suitableMethods = cls.methods.filter { it.name == stmt.callee.name }
                    concreteMethods += suitableMethods
                }
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

        val possibleTypes = scope.calcOnState {
            memory.typeStreamOf(unwrappedInstance).take(scene.projectAndSdkClasses.size)
        }

        if (possibleTypes !is TypesResult.SuccessfulTypesResult) {
            error("TODO") // is it right?
        }

        val possibleTypesSet = possibleTypes.types.toSet()

        if (possibleTypesSet.singleOrNull() == EtsAnyType) {
            mockMethodCall(scope, stmt.callee)
            scope.doWithState { newStmt(stmt.returnSite) }
            return
        }

        val filteredPossibleTypesSet = possibleTypesSet - EtsAnyType

        val methodsDeclaringClasses = concreteMethods.mapNotNull { it.enclosingClass } // is it right?
        val typeSystem = typeSystem<EtsType>()

        // take only possible classes with a corresponding method
        val intersectedTypes = filteredPossibleTypesSet.filter { possibleType ->
            methodsDeclaringClasses.any { typeSystem.isSupertype(it.type, possibleType) }
        }

        //
        val chosenMethods = intersectedTypes.flatMap { clazz ->
            graph.hierarchy.classesForType(clazz as EtsRefType)
                .asSequence()
                // TODO wrong order, ancestors are unordered
                .map { graph.hierarchy.getAncestors(it) }
                .mapNotNull { it.firstOrNull { it.methods.any { it.name == stmt.callee.name } } }
                .map { clazz to it.methods.first { it.name == stmt.callee.name } }
        }.toList().take(10) // TODO check it

        // logger.info {
        // "Preparing to fork on ${limitedConcreteMethods.size} methods out of ${concreteMethods.size}: ${
        //     limitedConcreteMethods.map { "${it.signature.enclosingClass.name}::${it.name}" }
        // }"
        // }

        val conditionsWithBlocks = chosenMethods.mapIndexed { i, (clazz, method) ->
            val concreteCall = stmt.toConcrete(method)
            val block = { state: TsState -> state.newStmt(concreteCall) }
            val type = requireNotNull(method.enclosingClass).type

            val constraint = scope.calcOnState {
                val ref = stmt.instance.asExpr(addressSort)
                    .takeIf { !it.isFakeObject() }
                    ?: unwrappedInstance.asExpr(addressSort)

                // TODO: adhoc: "expand" ITE
                if (ref is UIteExpr<*>) {
                    val trueBranch = ref.trueBranch
                    val falseBranch = ref.falseBranch
                    if (trueBranch.isFakeObject() || falseBranch.isFakeObject()) {
                        val unwrappedTrueExpr = trueBranch.asExpr(addressSort).unwrapRefWithPathConstraint(scope)
                        val unwrappedFalseExpr = falseBranch.asExpr(addressSort).unwrapRefWithPathConstraint(scope)
                        return@calcOnState mkIte(
                            condition = ref.condition,
                            trueBranch = memory.types.evalIsSubtype(unwrappedTrueExpr, type),
                            falseBranch = memory.types.evalIsSubtype(unwrappedFalseExpr, type),
                        )
                    }
                }

                // TODO mistake, should be separated into several hierarchies
                //      or evalTypeEqual with several concrete types
                mkAnd(
                    memory.types.evalIsSubtype(ref, clazz),
                    memory.types.evalIsSupertype(ref, type)
                )
            }
            constraint to block
        }

        if (conditionsWithBlocks.isEmpty()) {
            logger.warn {
                "No suitable methods found for call: ${stmt.callee} with instance: $unwrappedInstance"
            }
            mockMethodCall(scope, stmt.callee)
            scope.doWithState { newStmt(stmt.returnSite) }
            return
        }

        scope.forkMulti(conditionsWithBlocks)
    }

    private fun visitConcreteMethodCall(scope: TsStepScope, stmt: TsConcreteMethodCallStmt) {

        // NOTE: USE '.callee' INSTEAD OF '.method' !!!

        // TODO: observer

        if (stmt.callee.signature.enclosingClass.name == "Log") {
            mockMethodCall(scope, stmt.callee.signature)
            scope.doWithState { newStmt(stmt.returnSite) }
            return
        }

        val entryPoint = graph.entryPoints(stmt.callee).singleOrNull()
        if (entryPoint == null) {
            // logger.warn { "No entry point for method: ${stmt.callee}, mocking the call" }
            // If the method doesn't have entry points,
            // we go through it, we just mock the call
            mockMethodCall(scope, stmt.callee.signature)
            scope.doWithState { newStmt(stmt.returnSite) }
            return
        }

        scope.doWithState {
            registerCallee(stmt.returnSite, stmt.callee.cfg)

            val args = mutableListOf<UExpr<*>>()
            val numActual = stmt.args.size
            val numFormal = stmt.callee.parameters.size

            args += stmt.instance

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
                    val descriptor = ctx.arrayDescriptorOf(type)

                    val address = memory.allocConcrete(descriptor)
                    memory.initializeArray(
                        arrayHeapRef = address,
                        type = descriptor,
                        sort = ctx.addressSort,
                        sizeSort = ctx.sizeSort,
                        contents = content.asSequence(),
                    )
                    address
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

            check(args.size - 1 == numFormal) {
                "Expected $numFormal arguments, got ${args.size - 1} (not counting 'this')"
            }

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

    private fun assignTo(
        scope: TsStepScope,
        lhv: EtsLValue,
        expr: UExpr<*>,
    ): Unit? {
        val exprResolver = exprResolverWithScope(scope)
        return when (lhv) {
            is EtsLocal -> {
                exprResolver.handleAssignToLocal(lhv, expr)
            }

            is EtsArrayAccess -> {
                exprResolver.handleAssignToArrayIndex(lhv, expr)
            }

            is EtsInstanceFieldRef -> {
                exprResolver.handleAssignToInstanceField(lhv, expr)
            }

            is EtsStaticFieldRef -> {
                exprResolver.handleAssignToStaticField(lhv, expr)
            }

            else -> TODO("Not yet implemented")
        }
    }

    private fun assignToInDfltDflt(
        scope: TsStepScope,
        lhv: EtsLValue,
        expr: UExpr<*>,
    ): Unit? = with(ctx) {
        val exprResolver = exprResolverWithScope(scope)
        val file = scope.calcOnState { lastEnteredMethod.enclosingClass!!.declaringFile!! }
        when (lhv) {
            is EtsLocal -> {
                val name = lhv.name
                if (name.startsWith("%") || name.startsWith("_tmp") || name == "this") {
                    // Normal local variable
                    exprResolver.handleAssignToLocal(lhv, expr)
                } else {
                    // Global variable
                    logger.info {
                        "Assigning to a global variable in %dflt: $name in $file"
                    }
                    writeGlobal(scope, file, name, expr)
                }
            }

            is EtsArrayAccess -> {
                val name = lhv.array.name
                if (name.startsWith("%") || name.startsWith("_tmp") || name == "this") {
                    // Normal local array variable
                    exprResolver.handleAssignToArrayIndex(lhv, expr)
                } else {
                    // Global array variable
                    logger.info {
                        "Assigning to an element of a global array variable in dflt: $name[${lhv.index}] in $file"
                    }
                    val array = readGlobal(scope, file, name) ?: return null
                    check(array.sort == addressSort) {
                        "Expected address sort for the array, got: ${array.sort}"
                    }
                    val arrayRef = array.asExpr(addressSort)
                    val resolvedIndex = exprResolver.resolve(lhv.index) ?: return null
                    val index = resolvedIndex.asExpr(fp64Sort)
                    val bvIndex = mkFpToBvExpr(
                        roundingMode = fpRoundingModeSortDefaultValue(),
                        value = index,
                        bvSize = 32,
                        isSigned = true,
                    ).asExpr(sizeSort)
                    val arrayType = if (isAllocatedConcreteHeapRef(array)) {
                        scope.calcOnState { memory.typeStreamOf(array).first() }
                    } else {
                        lhv.array.type
                    }
                    check(arrayType is EtsArrayType) {
                        "Expected EtsArrayType, got: ${lhv.array.type}"
                    }
                    val elementSort = typeToSort(arrayType.elementType)
                    val elementLValue = mkArrayIndexLValue(
                        sort = elementSort,
                        ref = arrayRef,
                        index = bvIndex.asExpr(sizeSort),
                        type = arrayType,
                    )
                    scope.doWithState {
                        memory.write(elementLValue, expr.cast(), guard = trueExpr)
                    }
                }
            }

            is EtsInstanceFieldRef -> {
                val name = lhv.instance.name
                if (name.startsWith("%") || name.startsWith("_tmp") || name == "this") {
                    // Normal local instance variable
                    exprResolver.handleAssignToInstanceField(lhv, expr)
                } else {
                    // Global instance variable
                    logger.info {
                        "Assigning to a field of a global variable in dflt: $name.${lhv.field.name} in $file"
                    }
                    val instance = readGlobal(scope, file, name) ?: return null
                    check(instance.sort == addressSort) {
                        "Expected address sort for the instance, got: ${instance.sort}"
                    }
                    val fieldLValue = mkFieldLValue(expr.sort, instance.asExpr(addressSort), lhv.field)
                    scope.doWithState {
                        memory.write(fieldLValue, expr.cast(), guard = trueExpr)
                    }
                }
            }

            else -> {
                error("LHV of type ${lhv::class.java} is not supported in %dflt::%dflt: $lhv")
            }
        }
    }

    private fun visitAssignStmt(scope: TsStepScope, stmt: EtsAssignStmt) = with(ctx) {
        val exprResolver = exprResolverWithScope(scope)

        val callExpr = stmt.callExpr
        if (callExpr == null) {
            observer?.onAssignStatement(exprResolver.simpleValueResolver, stmt, scope)
        } else {
            val methodResult = scope.calcOnState { methodResult }

            when (methodResult) {
                is TsMethodResult.NoCall -> {
                    observer?.onCallWithUnresolvedArguments(exprResolver.simpleValueResolver, callExpr, scope)
                }

                is TsMethodResult.Success -> {
                    observer?.onAssignStatement(exprResolver.simpleValueResolver, stmt, scope)
                }

                is TsMethodResult.TsException -> {
                    error("Exceptions must be processed earlier")
                }
            }

            if (!options.interproceduralAnalysis && methodResult == TsMethodResult.NoCall) {
                mockMethodCall(scope, callExpr.callee)
                scope.doWithState { newStmt(stmt) }
                return
            }
        }

        val expr = exprResolver.resolve(stmt.rhv) ?: return

        check(expr.sort != unresolvedSort) {
            "A value of the unresolved sort should never be returned from `resolve` function"
        }

        // Assignments in %dflt::%dflt are *special*...
        val isDflt = stmt.location.method.name == DEFAULT_ARK_METHOD_NAME &&
            stmt.location.method.enclosingClass?.name == DEFAULT_ARK_CLASS_NAME
        if (isDflt) {
            assignToInDfltDflt(scope, stmt.lhv, expr) ?: return
        } else {
            assignTo(scope, stmt.lhv, expr) ?: return
        }

        val nextStmt = stmt.nextStmt ?: return
        scope.doWithState { newStmt(nextStmt) }
    }

    private fun visitCallStmt(scope: TsStepScope, stmt: EtsCallStmt) {
        if (scope.calcOnState { methodResult } is TsMethodResult.Success) {
            scope.doWithState { methodResult = TsMethodResult.NoCall }
            val nextStmt = stmt.nextStmt ?: return
            scope.doWithState { newStmt(nextStmt) }
            return
        }

        if (options.interproceduralAnalysis) {
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
        val exprResolver = exprResolverWithScope(scope)

        observer?.onThrowStatement(exprResolver.simpleValueResolver, stmt, scope)

        val exception = exprResolver.resolve(stmt.exception)

        // Pop the call stack to return to the caller
        scope.doWithState {
            memory.stack.pop()
        }

        if (exception != null) {
            val exceptionType: EtsType = when (exception.sort) {
                ctx.addressSort -> {
                    // If it's an object reference, try to determine its type
                    val ref = exception.asExpr(ctx.addressSort)
                    // For now, assume it's a generic error type
                    EtsStringType // TODO: improve type detection
                }

                ctx.fp64Sort -> EtsNumberType

                ctx.boolSort -> EtsBooleanType

                else -> EtsStringType
            }

            scope.doWithState {
                methodResult = TsMethodResult.TsException(exception, exceptionType)
            }
        } else {
            scope.doWithState {
                // If we couldn't resolve the exception value, throw a generic exception
                methodResult = TsMethodResult.TsException(ctx.mkUndefinedValue(), EtsStringType)
            }
        }
    }

    private fun visitNopStmt(scope: TsStepScope, stmt: EtsNopStmt) {
        // Do nothing
    }

    private fun exprResolverWithScope(scope: TsStepScope): TsExprResolver =
        TsExprResolver(
            ctx = ctx,
            scope = scope,
            options = options,
            hierarchy = graph.hierarchy,
        )

    fun getInitialState(method: EtsMethod, targets: List<TsTarget>): TsState = with(ctx) {
        val state = TsState(
            ctx = ctx,
            ownership = MutabilityOwnership(),
            entrypoint = method,
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

        state.pathConstraints += mkNot(mkHeapRefEq(thisRef, mkTsNullValue()))
        state.pathConstraints += mkNot(mkHeapRefEq(thisRef, mkUndefinedValue()))

        // TODO not equal but subtype for abstract/interfaces
        state.pathConstraints += state.memory.types.evalTypeEquals(thisRef, method.enclosingClass!!.type)

        method.parameters.forEachIndexed { i, param ->
            val idx = i + 1 // +1 because 0 is reserved for `this`

            val ref by lazy {
                val lValue = mkRegisterStackLValue(addressSort, idx)
                state.memory.read(lValue).asExpr(addressSort)
            }

            val parameterType = param.type
            if (parameterType is EtsRefType) run {
                state.pathConstraints += mkNot(mkHeapRefEq(ref, mkTsNullValue()))
                state.pathConstraints += mkNot(mkHeapRefEq(ref, mkUndefinedValue()))

                if (parameterType is EtsArrayType) {
                    state.pathConstraints += state.memory.types.evalIsSubtype(ref, parameterType)

                    val lengthLValue = mkArrayLengthLValue(ref, parameterType)
                    val length = state.memory.read(lengthLValue).asExpr(sizeSort)
                    state.pathConstraints += mkBvSignedGreaterOrEqualExpr(length, mkBv(0))
                    state.pathConstraints += mkBvSignedLessOrEqualExpr(length, mkBv(options.maxArraySize))

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
            if (parameterType == EtsNullType) {
                state.pathConstraints += mkHeapRefEq(ref, mkTsNullValue())
            }
            if (parameterType == EtsUndefinedType) {
                state.pathConstraints += mkHeapRefEq(ref, mkUndefinedValue())
            }
            if (parameterType == EtsStringType) {
                state.pathConstraints += mkNot(mkHeapRefEq(ref, mkTsNullValue()))
                state.pathConstraints += mkNot(mkHeapRefEq(ref, mkUndefinedValue()))

                state.pathConstraints += state.memory.types.evalTypeEquals(ref, EtsStringType)
            }

            val parameterSort = typeToSort(parameterType)
            if (parameterSort is TsUnresolvedSort) {
                // If the parameter type is unresolved, we create a fake object for it
                val bool = mkRegisterReading(idx, boolSort)
                val fp = mkRegisterReading(idx, fp64Sort)
                val ref = mkRegisterReading(idx, addressSort)
                val fakeObject = state.mkFakeValue(null, bool, fp, ref)
                val lValue = mkRegisterStackLValue(addressSort, idx)
                state.memory.write(lValue, fakeObject.asExpr(addressSort), guard = trueExpr)
                state.saveSortForLocal(idx, addressSort)
            } else {
                state.saveSortForLocal(idx, parameterSort)
            }
        }

        val solver = solver<EtsType>()
        val model = solver.check(state.pathConstraints).ensureSat().model
        state.models = listOf(model)

        state
    }

    // TODO: expand with interpreter implementation
    private val EtsStmt.nextStmt: EtsStmt?
        get() = graph.successors(this).firstOrNull()
}
