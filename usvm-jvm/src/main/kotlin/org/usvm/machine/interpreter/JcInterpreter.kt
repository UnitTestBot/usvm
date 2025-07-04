package org.usvm.machine.interpreter

import io.ksmt.utils.asExpr
import mu.KLogging
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcArgument
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcCatchInst
import org.jacodb.api.jvm.cfg.JcEnterMonitorInst
import org.jacodb.api.jvm.cfg.JcEqExpr
import org.jacodb.api.jvm.cfg.JcExitMonitorInst
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcImmediate
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcInstRef
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcSwitchInst
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.cfg.JcThrowInst
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.cfg.callExpr
import org.jacodb.api.jvm.ext.isEnum
import org.jacodb.api.jvm.ext.toType
import org.usvm.ForkCase
import org.usvm.StepResult
import org.usvm.StepScope
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UInterpreter
import org.usvm.USort
import org.usvm.api.allocateStaticRef
import org.usvm.api.evalTypeEquals
import org.usvm.api.targets.JcTarget
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.jvm.util.name
import org.usvm.jvm.util.outerClassInstanceField
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcConcreteMethodCallInst
import org.usvm.machine.JcContext
import org.usvm.machine.JcDynamicMethodCallInst
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.JcMethodApproximationResolver
import org.usvm.machine.JcMethodCall
import org.usvm.machine.JcMethodCallBaseInst
import org.usvm.machine.JcMethodEntrypointInst
import org.usvm.machine.JcVirtualMethodCallInst
import org.usvm.machine.mocks.mockMethod
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.addNewMethodCall
import org.usvm.machine.state.lastStmt
import org.usvm.machine.state.localIdx
import org.usvm.machine.state.localsCount
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.parametersWithThisCount
import org.usvm.machine.state.returnValue
import org.usvm.machine.state.throwExceptionAndDropStackFrame
import org.usvm.machine.state.throwExceptionWithoutStackFrameDrop
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue
import org.usvm.targets.UTargetsSet
import org.usvm.util.write
import org.usvm.utils.ensureSat
import org.usvm.utils.logAssertFailure
import org.usvm.utils.onStateDeath

typealias JcStepScope = StepScope<JcState, JcType, JcInst, JcContext>

/**
 * A JacoDB interpreter.
 */
class JcInterpreter(
    private val ctx: JcContext,
    private val applicationGraph: JcApplicationGraph,
    private val options: JcMachineOptions,
    private val observer: JcInterpreterObserver? = null,
    var forkBlackList: UForkBlackList<JcState, JcInst> = UForkBlackList.createDefault(),
) : UInterpreter<JcState>() {

    companion object {
        val logger = object : KLogging() {}.logger
    }

    fun getInitialState(method: JcMethod, targets: List<JcTarget> = emptyList()): JcState {
        val initOwnership = MutabilityOwnership()
        val state = JcState(ctx, initOwnership, method, targets = UTargetsSet.from(targets))
        val typedMethod = with(applicationGraph) { method.typed }

        val entrypointArguments = mutableListOf<Pair<JcRefType, UHeapRef>>()
        val enclosingType = typedMethod.enclosingType
        if (!method.isStatic) {
            with(ctx) {
                val thisLValue = URegisterStackLValue(addressSort, 0)
                val ref = state.memory.read(thisLValue).asExpr(addressSort)
                state.pathConstraints += mkEq(ref, nullRef).not()

                // TODO support virtual entrypoints https://github.com/UnitTestBot/usvm/issues/93
                val thisTypeConstraints = if (enclosingType.jcClass.isAbstract) {
                    state.memory.types.evalIsSubtype(ref, enclosingType)
                } else {
                    state.memory.types.evalTypeEquals(ref, enclosingType)
                }
                state.pathConstraints += thisTypeConstraints

                entrypointArguments += enclosingType to ref
            }
        }

        typedMethod.parameters.forEachIndexed { idx, typedParameter ->
            with(ctx) {
                val type = typedParameter.type
                if (type is JcRefType) {
                    val argumentLValue = URegisterStackLValue(typeToSort(type), method.localIdx(idx))
                    val ref = state.memory.read(argumentLValue).asExpr(addressSort)
                    state.pathConstraints += mkIsSubtypeExpr(ref, type)

                    entrypointArguments += type to ref
                }
            }
        }

        val solver = ctx.solver<JcType>()

        val model = solver.check(state.pathConstraints).ensureSat().model
        state.models = listOf(model)

        val entrypointInst = JcMethodEntrypointInst(method, entrypointArguments)
        state.callStack.push(method, returnSite = null)
        state.memory.stack.push(method.parametersWithThisCount, method.localsCount)
        state.newStmt(entrypointInst)
        return state
    }

    override fun step(state: JcState): StepResult<JcState> {
        val stmt = state.lastStmt

        logger.debug("Step: {}", stmt)

        val scope = StepScope(state, forkBlackList)

        // handle exception firstly
        val result = state.methodResult
        if (result is JcMethodResult.JcException) {
            handleException(scope, result, stmt)
            return scope.stepResult()
        }

        when (stmt) {
            is JcMethodCallBaseInst -> visitMethodCall(scope, stmt)
            is JcAssignInst -> visitAssignInst(scope, stmt)
            is JcIfInst -> visitIfStmt(scope, stmt)
            is JcReturnInst -> visitReturnStmt(scope, stmt)
            is JcGotoInst -> visitGotoStmt(scope, stmt)
            is JcCatchInst -> visitCatchStmt(scope, stmt)
            is JcSwitchInst -> visitSwitchStmt(scope, stmt)
            is JcThrowInst -> visitThrowStmt(scope, stmt)
            is JcCallInst -> visitCallStmt(scope, stmt)
            is JcEnterMonitorInst -> visitMonitorEnterStmt(scope, stmt)
            is JcExitMonitorInst -> visitMonitorExitStmt(scope, stmt)
            else -> error("Unknown stmt: $stmt")
        }

        return scope.stepResult()
    }

    private fun handleException(
        scope: JcStepScope,
        exception: JcMethodResult.JcException,
        lastStmt: JcInst,
    ) {
        val catchStatements = applicationGraph.successors(lastStmt).filterIsInstance<JcCatchInst>().toList()

        val typeConstraintsNegations = mutableListOf<UBoolExpr>()
        val catchForks = mutableListOf<Pair<UBoolExpr, JcState.() -> Unit>>()

        val blockToFork: (JcCatchInst) -> (JcState) -> Unit = { catchInst: JcCatchInst ->
            block@{ state: JcState ->
                val lValue = exprResolverWithScope(scope).resolveLValue(catchInst.throwable) ?: return@block
                val exceptionResult = state.methodResult as JcMethodResult.JcException

                state.memory.write(lValue, exceptionResult.address)

                state.methodResult = JcMethodResult.NoCall
                state.newStmt(catchInst.nextStmt)
            }
        }

        catchStatements.forEach { catchInst ->
            val throwableTypes = catchInst.throwableTypes

            val typeConstraint = scope.calcOnState {
                val currentTypeConstraints = throwableTypes.map { memory.types.evalIsSubtype(exception.address, it) }
                val result = ctx.mkAnd(typeConstraintsNegations + ctx.mkOr(currentTypeConstraints))

                typeConstraintsNegations += currentTypeConstraints.map { ctx.mkNot(it) }

                result
            }

            catchForks += typeConstraint to blockToFork(catchInst)
        }

        val typeConditionToMiss = ctx.mkAnd(typeConstraintsNegations)
        val functionBlockOnMiss = block@{ _: JcState ->
            scope.calcOnState { throwExceptionAndDropStackFrame() }
        }

        val catchSectionMiss = typeConditionToMiss to functionBlockOnMiss

        // TODO observer?.onCatchStatement

        scope.forkMulti(catchForks + catchSectionMiss)
    }

    private val typeSelector = JcFixedInheritorsNumberTypeSelector()

    private fun callMethod(
        scope: JcStepScope,
        stmt: JcMethodCallBaseInst,
        exprResolver: JcExprResolver
    ) {
        val simpleValueResolver = exprResolver.simpleValueResolver
        val method = stmt.method
        when (stmt) {
            is JcMethodEntrypointInst -> {
                observer?.onEntryPoint(simpleValueResolver, stmt, scope)

                // Run static initializer for all enum arguments of the entrypoint
                for ((type, ref) in stmt.entrypointArguments) {
                    exprResolver.ensureExprCorrectness(ref, type) ?: return
                }

                handleInnerClassMethodCall(
                    scope,
                    method.enclosingClass.toType(),
                    method,
                    outerClassInstanceConstructorArgument = {
                        // Implicit first argument is `this`, an instance of the outer class would be second
                        stmt.entrypointArguments[1].second
                    },
                    thisInstanceMethodArgument = {
                        // For methods, we need to extract `this`
                        stmt.entrypointArguments.first().second
                    },
                )

                val entryPoint = applicationGraph.entryPoints(method).singleOrNull()
                    ?: error("Entrypoint method $method has no entry points")

                scope.doWithState {
                    newStmt(entryPoint)
                }
            }

            is JcConcreteMethodCallInst -> {
                observer?.onMethodCallWithResolvedArguments(simpleValueResolver, stmt, scope)
                if (approximateMethod(scope, stmt)) {
                    return
                }

                val entryPoint = applicationGraph.entryPoints(method).singleOrNull()

                if (method.isNative || entryPoint == null) {
                    mockMethod(scope, stmt, applicationGraph)
                    return
                }

                handleInnerClassMethodCall(
                    scope,
                    method.enclosingClass.toType(),
                    method,
                    outerClassInstanceConstructorArgument = {
                        // Implicit first argument is `this`, an instance of the outer class would be second
                        stmt.arguments[1].asExpr(ctx.addressSort)
                    },
                    thisInstanceMethodArgument = {
                        // For methods, we need to extract `this`
                        stmt.arguments.first().asExpr(ctx.addressSort)
                    },
                )

                scope.doWithState {
                    addNewMethodCall(stmt, entryPoint)
                }
            }

            is JcVirtualMethodCallInst -> {
                observer?.onMethodCallWithResolvedArguments(simpleValueResolver, stmt, scope)

                if (approximateMethod(scope, stmt)) {
                    return
                }

                if (method.isFinal) {
                    // Case for approximated interfaces
                    with (stmt) {
                        scope.doWithState {
                            newStmt(JcConcreteMethodCallInst(location, method, arguments, returnSite))
                        }
                    }
                    return
                }

                resolveVirtualInvoke(stmt, scope)
            }

            is JcDynamicMethodCallInst -> {
                observer?.onMethodCallWithResolvedArguments(simpleValueResolver, stmt, scope)

                if (approximateMethod(scope, stmt)) {
                    return
                }

                mockMethod(scope, stmt, stmt.dynamicCall.callSiteReturnType)
            }
        }
    }

    private fun visitMethodCall(scope: JcStepScope, stmt: JcMethodCallBaseInst) {
        callMethod(scope, stmt, exprResolverWithScope(scope))
    }

    private inline fun handleInnerClassMethodCall(
        scope: JcStepScope,
        enclosingType: JcClassType,
        method: JcMethod,
        outerClassInstanceConstructorArgument: () -> UHeapRef,
        thisInstanceMethodArgument: () -> UHeapRef,
    ) {
        if (method.isStatic) {
            return
        }

        val outerType = enclosingType.outerType
            ?: return

        // TODO For now, it's the only way to differentiate inner and static nested classes - wait for updates in jacodb
        //  for more appropriate approach
        val outerClassField = enclosingType.outerClassInstanceField
            ?: return
        if (method.isConstructor) {
            // For constructors of inner classes, we need to ensure that its first argument
            // (which is an instance of the outer class) is not null
            // (because it's impossible to create an inner class with null outer class)
            ensureOuterClassRefCorrectnessForTheInnerClass(
                scope,
                enclosingType,
                outerType,
                outerClassInstanceConstructorArgument()
            )
        } else {
            // For methods of inner classes, we need to ensure correctness of the instance of the outer class
            // (which is stored in the `this$0` field) - not-null and with correct type
            with(ctx) {
                val outerClassFieldLValue = UFieldLValue(
                    addressSort,
                    thisInstanceMethodArgument(),
                    outerClassField.field
                )
                val outerClassRef = scope.calcOnState { memory.read(outerClassFieldLValue).asExpr(addressSort) }
                ensureOuterClassRefCorrectnessForTheInnerClass(scope, enclosingType, outerType, outerClassRef)
            }
        }
    }

    private fun ensureOuterClassRefCorrectnessForTheInnerClass(
        scope: JcStepScope,
        enclosingType: JcClassType,
        outerType: JcClassType,
        outerClassRef: UHeapRef
    ) {
        with(ctx) {
            scope.assert(mkEq(outerClassRef, nullRef).not())
                ?: error("Outer class ref cannot be null for the inner type ${enclosingType.name}")

            val typeConstraint = scope.calcOnState { memory.types.evalIsSubtype(outerClassRef, outerType) }
            scope.assert(typeConstraint)
                ?: error("Outer class ref of the inner type ${enclosingType.name} must have the corresponding type ${outerType.name}")
        }
    }

    private fun visitAssignInst(scope: JcStepScope, stmt: JcAssignInst) {
        val exprResolver = exprResolverWithScope(scope)

        stmt.callExpr?.let {
            val methodResult = scope.calcOnState { methodResult }

            when (methodResult) {
                is JcMethodResult.NoCall -> observer?.onMethodCallWithUnresolvedArguments(
                    exprResolver.simpleValueResolver,
                    it,
                    scope
                )

                is JcMethodResult.Success -> observer?.onAssignStatement(exprResolver.simpleValueResolver, stmt, scope)
                is JcMethodResult.JcException -> error("Exceptions must be processed earlier")
            }
        } ?: observer?.onAssignStatement(exprResolver.simpleValueResolver, stmt, scope)

        val lvalue = exprResolver.resolveLValue(stmt.lhv) ?: return
        val expr = exprResolver.resolveJcExpr(stmt.rhv, stmt.lhv.type) ?: return

        checkArrayStoreException(lvalue, expr, exprResolver, scope) ?: return

        scope.doWithState {
            val nextStmt = stmt.nextStmt
            memory.write(lvalue, expr)
            newStmt(nextStmt)
        }
    }

    // Returns `trueExpr` if ArrayStoreException is impossible
    private fun checkArrayStoreException(
        lvalue: ULValue<*, *>,
        rvalue: UExpr<out USort>,
        exprResolver: JcExprResolver,
        scope: JcStepScope
    ): Unit? {
        if (lvalue !is UArrayIndexLValue<*, *, *>) {
            return Unit
        }

        // ArrayStoreException is possible only for references
        if (rvalue.sort != ctx.addressSort) {
            return Unit
        }

        check(lvalue.sort == rvalue.sort) {
            "Writing $rvalue with sort ${rvalue.sort} to the array with different sort ${lvalue.sort} by lvalue $lvalue found"
        }

        val rvalueRef = rvalue.asExpr(ctx.addressSort)

        // ArrayStoreException happens if we write a value that is not a subtype of the element type
        val isRvalueSubtypeOf = exprResolver.checkIsArrayRvalueSubtypeOf(lvalue.ref, rvalueRef)

        return if (options.forkOnImplicitExceptions) {
            scope.fork(
                isRvalueSubtypeOf,
                blockOnFalseState = exprResolver.allocateException(ctx.arrayStoreExceptionType)
            )
        } else {
            scope.assert(isRvalueSubtypeOf)
                .logAssertFailure { "Jc implicit exception: Check ArrayStoreException" }
        }
    }

    private fun visitIfStmt(scope: JcStepScope, stmt: JcIfInst) {
        val exprResolver = exprResolverWithScope(scope)

        observer?.onIfStatement(exprResolver.simpleValueResolver, stmt, scope)

        val boolExpr = exprResolver
            .resolveJcExpr(stmt.condition)
            ?.asExpr(ctx.boolSort)
            ?: return

        val instList = stmt.location.method.instList
        val (posStmt, negStmt) = instList[stmt.trueBranch.index] to instList[stmt.falseBranch.index]

        scope.forkWithBlackList(
            boolExpr,
            posStmt,
            negStmt,
            blockOnTrueState = { newStmt(posStmt) },
            blockOnFalseState = { newStmt(negStmt) }
        )
        scope.onStateDeath { logger.info { "State death on fork with blacklist" } }
    }

    private fun visitReturnStmt(scope: JcStepScope, stmt: JcReturnInst) {
        val exprResolver = exprResolverWithScope(scope)

        observer?.onReturnStatement(exprResolver.simpleValueResolver, stmt, scope)

        val method = requireNotNull(scope.calcOnState { callStack.lastMethod() })
        val returnType = with(applicationGraph) { method.typed }.returnType

        val valueToReturn = stmt.returnValue
            ?.let { exprResolver.resolveJcExpr(it, returnType) ?: return }
            ?: ctx.mkVoidValue()

        scope.doWithState {
            returnValue(valueToReturn)
        }
    }

    private fun visitGotoStmt(scope: JcStepScope, stmt: JcGotoInst) {
        val exprResolver = exprResolverWithScope(scope)

        observer?.onGotoStatement(exprResolver.simpleValueResolver, stmt, scope)

        val nextStmt = stmt.location.method.instList[stmt.target.index]
        scope.doWithState { newStmt(nextStmt) }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun visitCatchStmt(scope: JcStepScope, stmt: JcCatchInst) {
        error("The catch instruction must be unfolded during processing of the instructions led to it. Encountered inst: $stmt")
    }

    private fun visitSwitchStmt(scope: JcStepScope, stmt: JcSwitchInst) {
        val exprResolver = exprResolverWithScope(scope)

        observer?.onSwitchStatement(exprResolver.simpleValueResolver, stmt, scope)

        val switchKey = stmt.key
        // Note that the switch key can be an rvalue, for example, a simple int constant.
        val instList = stmt.location.method.instList

        with(ctx) {
            val cases = stmt.branches.map { (caseValue, caseTargetStmt) ->
                val nextStmt = instList[caseTargetStmt]
                val jcEqExpr = JcEqExpr(cp.boolean, switchKey, caseValue)
                val caseCondition = exprResolver.resolveJcExpr(jcEqExpr)?.asExpr(boolSort) ?: return

                ForkCase<JcState, JcInst>(
                    caseCondition,
                    nextStmt,
                    block = { newStmt(nextStmt) }
                )
            }

            val defaultStmt = instList[stmt.default]
            // To make the default case possible, we need to ensure that all case labels are unsatisfiable
            val defaultCase =
                ForkCase<JcState, JcInst>(
                    mkAnd(cases.map { it.condition.not() }),
                    defaultStmt,
                    block = { newStmt(defaultStmt) }
                )

            scope.forkMultiWithBlackList(cases + defaultCase)
            scope.onStateDeath { logger.info { "State death on fork with blacklist" } }
        }
    }

    private fun visitThrowStmt(scope: JcStepScope, stmt: JcThrowInst) {
        val exprResolver = exprResolverWithScope(scope)

        observer?.onThrowStatement(exprResolver.simpleValueResolver, stmt, scope)

        val address = exprResolver.resolveJcExpr(stmt.throwable)?.asExpr(ctx.addressSort) ?: return

        // Throwing `null` leads to NPE
        exprResolver.checkNullPointer(address) ?: return

        scope.calcOnState {
            throwExceptionWithoutStackFrameDrop(address, stmt.throwable.type)
        }
    }

    private fun visitCallStmt(scope: JcStepScope, stmt: JcCallInst) {
        val exprResolver = exprResolverWithScope(scope)
        val callExpr = stmt.callExpr
        val methodResult = scope.calcOnState { methodResult }

        when (methodResult) {
            is JcMethodResult.NoCall -> observer?.onMethodCallWithUnresolvedArguments(
                exprResolver.simpleValueResolver,
                callExpr,
                scope
            )

            is JcMethodResult.Success -> observer?.onCallStatement(exprResolver.simpleValueResolver, stmt, scope)
            is JcMethodResult.JcException -> error("Exceptions must be processed earlier")
        }

        exprResolver.resolveJcExpr(callExpr) ?: return

        scope.doWithState {
            val nextStmt = stmt.nextStmt
            newStmt(nextStmt)
        }
    }

    private fun visitMonitorEnterStmt(scope: JcStepScope, stmt: JcEnterMonitorInst) {
        val exprResolver = exprResolverWithScope(scope)
        exprResolver.resolveJcNotNullRefExpr(stmt.monitor, stmt.monitor.type) ?: return

        observer?.onEnterMonitorStatement(exprResolver.simpleValueResolver, stmt, scope)

        // Monitor enter makes sense only in multithreaded environment

        scope.doWithState {
            newStmt(stmt.nextStmt)
        }
    }

    private fun visitMonitorExitStmt(scope: JcStepScope, stmt: JcExitMonitorInst) {
        val exprResolver = exprResolverWithScope(scope)
        exprResolver.resolveJcNotNullRefExpr(stmt.monitor, stmt.monitor.type) ?: return

        observer?.onExitMonitorStatement(exprResolver.simpleValueResolver, stmt, scope)

        // Monitor exit makes sense only in multithreaded environment

        scope.doWithState {
            newStmt(stmt.nextStmt)
        }
    }

    private fun createExprResolver(
        ctx: JcContext,
        scope: JcStepScope,
        options: JcMachineOptions,
        localToIdx: (JcMethod, JcImmediate) -> Int,
        mkTypeRef: (JcState, JcType) -> Pair<UConcreteHeapRef, Boolean>,
        mkStringConstRef: (JcState, String, Boolean) -> Pair<UConcreteHeapRef, Boolean>,
        classInitializerAnalysisAlwaysRequiredForType: (JcRefType) -> Boolean,
    ): JcExprResolver {
        return JcExprResolver(
            ctx,
            scope,
            options,
            localToIdx,
            mkTypeRef,
            mkStringConstRef,
            classInitializerAnalysisAlwaysRequiredForType
        )
    }

    private fun exprResolverWithScope(scope: JcStepScope) =
        createExprResolver(
            ctx,
            scope,
            options,
            ::mapLocalToIdxMapper,
            ::typeInstanceAllocator,
            ::stringConstantAllocator,
            ::classInitializerAlwaysAnalysisRequiredForType
        )

    private val localVarToIdx = mutableMapOf<JcMethod, MutableMap<String, Int>>() // (method, localName) -> idx

    // TODO: now we need to explicitly evaluate indices of registers, because we don't have specific ULValues
    private fun mapLocalToIdxMapper(method: JcMethod, local: JcImmediate) =
        when (local) {
            is JcLocalVar -> localVarToIdx
                .getOrPut(method) { mutableMapOf() }
                .run {
                    getOrPut(local.name) { method.parametersWithThisCount + size }
                }

            is JcThis -> 0
            is JcArgument -> method.localIdx(local.index)
            else -> error("Unexpected local: $local")
        }

    private val JcInst.nextStmt get() = location.method.instList[location.index + 1]
    private operator fun JcInstList<JcInst>.get(instRef: JcInstRef): JcInst = this[instRef.index]

    private fun allocateString(): UConcreteHeapRef {
        // Allocate globally unique ref with a negative address
        return ctx.allocateStaticRef()
    }

    // TODO: make this region! (like interningPool)
    private val initializedRefs = hashSetOf<UConcreteHeapRef>()

    // Equal string constants must have equal references
    private fun stringConstantAllocator(
        state: JcState,
        value: String,
        initialize: Boolean = true
    ): Pair<UConcreteHeapRef, Boolean> {
        val memory = state.memory
        val interningPool = memory.getRegion(JcStringInterningRegionId) as JcStringInterningRegion
        var alreadyInitialized = false
        val (address, region) = interningPool.getOrPut(value) {
            allocateString()
        }
        memory.setRegion(JcStringInterningRegionId, region)

        alreadyInitialized = alreadyInitialized || initializedRefs.contains(address)

        if (initialize)
            initializedRefs.add(address)

        return address to alreadyInitialized
    }

    private fun allocateTypeInstance(): UConcreteHeapRef {
        // Allocate globally unique ref with a negative address
        return ctx.allocateStaticRef()
    }

    private fun typeInstanceAllocator(
        state: JcState,
        type: JcType,
        initialize: Boolean = true
    ): Pair<UConcreteHeapRef, Boolean> {
        val memory = state.memory
        val interningPool = memory.getRegion(JcClassInterningRegionId) as JcClassInterningRegion
        var alreadyInitialized = false
        val (address, region) = interningPool.getOrPut(type) {
            allocateTypeInstance()
        }
        memory.setRegion(JcClassInterningRegionId, region)

        alreadyInitialized = alreadyInitialized || initializedRefs.contains(address)

        if (initialize)
            initializedRefs.add(address)

        return address to alreadyInitialized
    }

    private fun classInitializerAlwaysAnalysisRequiredForType(type: JcRefType): Boolean {
        // Always analyze a static initializer for enums
        return type.jcClass.isEnum
    }

    private fun resolveVirtualInvoke(
        methodCall: JcVirtualMethodCallInst,
        scope: JcStepScope,
    ): Unit = resolveVirtualInvoke(ctx, methodCall, scope, typeSelector, options.forkOnRemainingTypes)

    private val approximationResolver = JcMethodApproximationResolver(ctx, applicationGraph)

    private fun approximateMethod(scope: JcStepScope, methodCall: JcMethodCall): Boolean {
        val exprResolver = exprResolverWithScope(scope)
        return approximationResolver.approximate(scope, exprResolver, methodCall)
    }
}
