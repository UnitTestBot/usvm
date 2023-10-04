package org.usvm.api.targets

import io.ksmt.utils.asExpr
import io.ksmt.utils.uncheckedCast
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcCallInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.configuration.Condition
import org.jacodb.configuration.ConstantTrue
import org.jacodb.configuration.TaintCleaner
import org.jacodb.configuration.TaintConfigurationFeature
import org.jacodb.configuration.TaintConfigurationItem
import org.jacodb.configuration.TaintMark
import org.jacodb.configuration.TaintMethodSink
import org.jacodb.configuration.TaintMethodSource
import org.jacodb.configuration.TaintPassThrough
import org.jacodb.configuration.taintConfigurationFeature
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.api.allocateConcreteRef
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.JcContext
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMethodCallBaseInst
import org.usvm.machine.JcMethodEntrypointInst
import org.usvm.machine.interpreter.JcSimpleValueResolver
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.statistics.UMachineObserver
import org.usvm.targets.UTargetController

class TaintAnalysis(
//    private val configuration: TaintConfiguration,
    override val targets: MutableCollection<TaintTarget> = mutableListOf(),
) : UTargetController, JcInterpreterObserver, UMachineObserver<JcState> {
    private val taintTargets: MutableMap<JcInst, MutableSet<TaintTarget>> = mutableMapOf()

    init {
        targets.forEach {
            exposeTargets(it, taintTargets)
        }
    }

    // TODO save mapping between initial targets and the states that reach them
    //      Replace with the corresponding observer-collector?
    val collectedStates: MutableList<JcState> = mutableListOf()

    private fun exposeTargets(target: TaintTarget, result: MutableMap<JcInst, MutableSet<TaintTarget>>) {
        result.getOrPut(target.location) { hashSetOf() }.add(target)

        target.children.forEach {
            val taintTarget = it as? TaintTarget
                ?: error("Taint targets must contain only taint targets as their children, but $it was found")
            exposeTargets(taintTarget, result)
        }
    }

    private var configurationFeature: TaintConfigurationFeature? = null

    private fun methodRules(method: JcMethod): List<TaintConfigurationItem> {
        if (configurationFeature == null) {
            configurationFeature = method.enclosingClass.classpath.taintConfigurationFeature()
        }
        return configurationFeature!!.getConfigForMethod(method)
    }

    private val marksAddresses: MutableMap<TaintMark, UConcreteHeapRef> = mutableMapOf()

    private fun getMarkAddress(mark: TaintMark, stepScope: JcStepScope): UConcreteHeapRef =
        marksAddresses.getOrPut(mark) {
            stepScope.calcOnState { memory.allocateConcreteRef() }
        }

    private fun writeMark(ref: UHeapRef, mark: TaintMark, guard: UBoolExpr, stepScope: JcStepScope) {
        stepScope.doWithState {
            memory.write(createLValue(ref, mark, stepScope), ctx.trueExpr, guard)
        }
    }

    private fun removeMark(ref: UHeapRef, mark: TaintMark, guard: UBoolExpr, stepScope: JcStepScope) {
        stepScope.doWithState {
            memory.write(createLValue(ref, mark, stepScope), ctx.falseExpr, guard)
        }
    }

    private fun readMark(ref: UHeapRef, mark: TaintMark, stepScope: JcStepScope): UBoolExpr =
        stepScope.calcOnState {
            memory.read(createLValue(ref, mark, stepScope))
        }

    private fun createLValue(
        ref: UHeapRef,
        mark: TaintMark,
        stepScope: JcStepScope,
    ): URefSetEntryLValue<TaintMark> = URefSetEntryLValue(ref, getMarkAddress(mark, stepScope), mark)


    fun addTarget(target: JcTarget): TaintAnalysis {
        require(target is TaintTarget)

        targets += target
        exposeTargets(target, taintTargets)

        return this
    }

    private fun findTaintTargets(stmt: JcInst, state: JcState): List<TaintTarget> =
        taintTargets[stmt]?.let { targets ->
            state.targets.filter { it in targets }
        }.orEmpty().toList().uncheckedCast()

    override fun onAssignStatement(exprResolver: JcSimpleValueResolver, stmt: JcAssignInst, stepScope: JcStepScope) {
        // Sinks are already processed at this moment since we resolved it on a call statement

        stmt.callExpr?.let { processTaintConfiguration(it, stepScope, exprResolver) }

        // TODO add fields processing
    }

    private fun processTaintConfiguration(
        callExpr: JcCallExpr,
        stepScope: JcStepScope,
        simpleValueResolver: JcSimpleValueResolver,
    ) {
        val ctx = stepScope.ctx
        val methodResult = stepScope.calcOnState { methodResult }
        val method = callExpr.method.method

        require(methodResult is JcMethodResult.Success) {
            "Other result statuses must be processed in `onMethodCallWithUnresolvedArguments`"
        }

        val callPositionResolver = createCallPositionResolver(ctx, callExpr, simpleValueResolver, methodResult)

        val conditionResolver = ConditionResolver(ctx, callPositionResolver, ::readMark)
        val actionResolver = TaintActionResolver(
            ctx,
            callPositionResolver,
            ::readMark,
            ::writeMark,
            ::removeMark,
            marksAddresses.keys
        )

        val configuration = methodRules(method)

        val sourceConfigurations = configuration.filterIsInstance<TaintMethodSource>()
        val currentStatement = stepScope.calcOnState { currentStatement }

        val sourceTargets = findTaintTargets(currentStatement, stepScope.state)
            .filterIsInstance<TaintMethodSourceTarget>()
            .associateBy { it.configurationRule }

        sourceConfigurations.forEach {
            val target = sourceTargets[it]

            val resolvedCondition =
                conditionResolver.resolve(it.condition, simpleValueResolver, stepScope) ?: ctx.trueExpr

            val targetCondition = target?.condition ?: ConstantTrue
            val resolvedTargetCondition =
                conditionResolver.resolve(targetCondition, simpleValueResolver, stepScope) ?: ctx.trueExpr

            val combinedCondition = ctx.mkAnd(resolvedTargetCondition, resolvedCondition)

            it.actionsAfter.forEach { actionResolver.resolve(it, stepScope, combinedCondition) }

            target?.propagate(stepScope.state)
        }

        val cleanerConfigurations = configuration.filterIsInstance<TaintCleaner>()
        cleanerConfigurations.forEach {
            val resolvedCondition = conditionResolver.resolve(it.condition, simpleValueResolver, stepScope)

            it.actionsAfter.forEach { actionResolver.resolve(it, stepScope, resolvedCondition) }
        }

        val passThroughConfigurations = configuration.filterIsInstance<TaintPassThrough>()
        passThroughConfigurations.forEach {
            val resolvedCondition = conditionResolver.resolve(it.condition, simpleValueResolver, stepScope)

            it.actionsAfter.forEach { actionResolver.resolve(it, stepScope, resolvedCondition) }
        }
    }

    private val JcStepScope.ctx get() = calcOnState { ctx }

    private fun createCallPositionResolver(
        ctx: JcContext,
        callExpr: JcCallExpr,
        simpleValueResolver: JcSimpleValueResolver,
        methodResult: JcMethodResult.Success?,
    ) = CallPositionResolver(
        resolveCallInstance(callExpr)?.accept(simpleValueResolver)?.asExpr(ctx.addressSort),
        callExpr.args.map { it.accept(simpleValueResolver) },
        methodResult?.value
    )

    override fun onEntryPoint(
        simpleValueResolver: JcSimpleValueResolver,
        stmt: JcMethodEntrypointInst,
        stepScope: JcStepScope,
    ) {
        // TODO entry point configuration
    }

    override fun onMethodCallWithUnresolvedArguments(
        simpleValueResolver: JcSimpleValueResolver,
        stmt: JcCallExpr,
        stepScope: JcStepScope,
    ) {
        val method = stmt.method.method

        val methodResult = stepScope.calcOnState { methodResult }
        require(methodResult is JcMethodResult.NoCall) { "This signal must be sent before the method call" }

        val ctx = stepScope.ctx

        val positionResolver = createCallPositionResolver(ctx, stmt, simpleValueResolver, methodResult = null)
        val conditionResolver = ConditionResolver(ctx, positionResolver, ::readMark)

        if (inTargetedMode) {
            val currentStatement = stepScope.calcOnState { currentStatement }
            val targets = findTaintTargets(currentStatement, stepScope.state)

            targets
                .filterIsInstance<TaintMethodSinkTarget>()
                .forEach {
                    processSink(it.configRule, it.condition, conditionResolver, simpleValueResolver, stepScope, it)
                }
        } else {
            val methodSinks = methodRules(method).filterIsInstance<TaintMethodSink>()

            methodSinks.forEach {
                processSink(it, ConstantTrue, conditionResolver, simpleValueResolver, stepScope)
            }
        }
    }

    private val JcStepScope.state get() = calcOnState { this }

    private val inTargetedMode: Boolean
        get() = targets.isNotEmpty()

    private fun processSink(
        methodSink: TaintMethodSink,
        sinkCondition: Condition,
        conditionResolver: ConditionResolver,
        simpleValueResolver: JcSimpleValueResolver,
        stepScope: JcStepScope,
        target: TaintMethodSinkTarget? = null,
    ) {
        val resolvedConfigCondition =
            conditionResolver.resolve(methodSink.condition, simpleValueResolver, stepScope) ?: return

        val resolvedSinkCondition = conditionResolver.resolve(sinkCondition, simpleValueResolver, stepScope) ?: return

        val resolvedCondition = stepScope.ctx.mkAnd(resolvedConfigCondition, resolvedSinkCondition)

        val (originalStateCopy, taintedStepScope) = stepScope.calcOnState {
            val originalStateCopy = clone()
            originalStateCopy to JcStepScope(originalStateCopy, UForkBlackList.createDefault())
        }

        taintedStepScope.assert(resolvedCondition)?.let {
            // TODO remove corresponding target
            collectedStates += originalStateCopy
            target?.propagate(taintedStepScope.state)
        }
    }

    override fun onMethodCallWithResolvedArguments(
        simpleValueResolver: JcSimpleValueResolver,
        stmt: JcMethodCallBaseInst,
        stepScope: JcStepScope,
    ) {
        // It is a redundant signal
    }

    override fun onCallStatement(simpleValueResolver: JcSimpleValueResolver, stmt: JcCallInst, stepScope: JcStepScope) {
        processTaintConfiguration(stmt.callExpr, stepScope, simpleValueResolver)
    }

    override fun onState(parent: JcState, forks: Sequence<JcState>) {
        propagateIntermediateTarget(parent)

        forks.forEach { propagateIntermediateTarget(it) }
    }

    private fun propagateIntermediateTarget(state: JcState) {
        val parent = state.pathLocation.parent ?: error("This is impossible by construction")
        val targets = findTaintTargets(parent.statement, state)

        targets.forEach {
            when (it) {
                is TaintIntermediateTarget -> it.propagate(state)
                is TaintMethodSourceTarget, is TaintMethodSinkTarget -> return@forEach
            }
        }
    }

    private fun resolveCallInstance(
        callExpr: JcCallExpr,
    ) = if (callExpr is JcInstanceCallExpr) callExpr.instance else null

    sealed class TaintTarget(override val location: JcInst) : JcTarget(location)

    class TaintMethodSourceTarget(
        location: JcInst,
        val condition: Condition,
        val configurationRule: TaintMethodSource,
    ) : TaintTarget(location)
    // TODO add field sources and sinks targets

    class TaintIntermediateTarget(location: JcInst) : TaintTarget(location)

    class TaintMethodSinkTarget(
        location: JcInst,
        val condition: Condition,
        val configRule: TaintMethodSink,
    ) : TaintTarget(location)
}


