package org.usvm.solver

import io.ksmt.solver.KModel
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import io.ksmt.utils.asExpr
import io.ksmt.utils.cast
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteChar
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UFpSort
import org.usvm.USort
import org.usvm.character
import org.usvm.collection.string.UStringModelRegion
import org.usvm.collection.string.UStringRegionId
import org.usvm.constraints.UPathConstraints
import org.usvm.getFloatValue
import org.usvm.getIntValue
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.logger
import org.usvm.model.UModelBase
import org.usvm.model.UModelDecoder
import org.usvm.sizeSort
import org.usvm.withSizeSort
import kotlin.time.Duration

sealed interface USolverResult<out T>

open class USatResult<out Model>(
    val model: Model,
) : USolverResult<Model>

open class UUnsatResult<Model> : USolverResult<Model>

open class UUnknownResult<Model> : USolverResult<Model>

abstract class USolver<in Query, out Model> {
    abstract fun check(query: Query): USolverResult<Model>
}

open class USolverBase<Type>(
    protected val ctx: UContext<*>,
    protected val smtSolver: KSolver<*>,
    protected val typeSolver: UTypeSolver<Type>,
    protected val stringSolver: UStringSolver,
    protected val translator: UExprTranslator<Type, *>,
    protected val decoder: UModelDecoder<UModelBase<Type>>,
    // TODO this timeout must not exceed time budget for the MUT
    private val timeout: Duration
) : USolver<UPathConstraints<Type>, UModelBase<Type>>(), AutoCloseable {

    override fun check(query: UPathConstraints<Type>): USolverResult<UModelBase<Type>> =
        internalCheck(query, softConstraints = emptyList())

    fun checkWithSoftConstraints(
        pc: UPathConstraints<Type>,
        softConstraints: Iterable<UBoolExpr>
    ): USolverResult<UModelBase<Type>> = internalCheck(pc, softConstraints)

    private fun internalCheck(
        pc: UPathConstraints<Type>,
        softConstraints: Iterable<UBoolExpr>,
    ): USolverResult<UModelBase<Type>> {
        if (pc.isFalse) {
            return UUnsatResult()
        }

        smtSolver.withAssertionsScope {
            val assertions = pc.constraints(translator).toList()
            smtSolver.assert(assertions)

            val translatedSoftConstraints = softConstraints
                .asSequence()
                .map(translator::translate)
                .filterNot(UBoolExpr::isFalse)
                .toMutableList()

            // DPLL(T)-like solve procedure
            var iter = 0
            @Suppress("KotlinConstantConditions")
            do {
                iter++

                // first, get a model from the SMT solver
                val kModel = when (internalCheckWithSoftConstraints(translatedSoftConstraints)) {
                    KSolverStatus.SAT -> smtSolver.model().detach()
                    KSolverStatus.UNSAT -> return UUnsatResult()
                    KSolverStatus.UNKNOWN -> return UUnknownResult()
                }

                // second, decode it unto uModel
                var uModel = decoder.decode(kModel, assertions)


                // third, build a type solver query
                val isExprToInterpretation = kModel.declarations.mapNotNull { decl ->
                    translator.declToIsExpr[decl]?.let { isSubtypeExpr ->
                        val expr = decl.apply(emptyList())
                        isSubtypeExpr to kModel.eval(expr, isComplete = true).asExpr(ctx.boolSort).isTrue
                    }
                }
                val typeSolverQuery = TypeSolverQuery(
                    inputToConcrete = { uModel.eval(it) as UConcreteHeapRef },
                    inputRefToTypeRegion = pc.typeConstraints.inputRefToTypeRegion,
                    isExprToInterpretation = isExprToInterpretation,
                )

                // fourth, check it satisfies typeConstraints
                when (val typeResult = typeSolver.check(typeSolverQuery)) {
                    is USatResult -> {
                        uModel =
                            UModelBase(
                                ctx,
                                uModel.stack,
                                typeResult.model,
                                uModel.mocker,
                                uModel.regions,
                                uModel.nullRef
                            )
                    }

                    // in case of failure, assert reference disequality expressions
                    is UTypeUnsatResult<Type> -> {
                        typeResult.conflictLemmas
                            .map(translator::translate)
                            .let { smtSolver.assert(it) }
                        continue
                    }

                    is UUnknownResult -> return UUnknownResult()
                    is UUnsatResult -> return UUnsatResult()
                }

                // finally, check it satisfies string constraints
                when (val stringResult = stringSolver.check(buildStringSolverQuery(kModel, uModel))) {
                    is USatResult -> {
                        if (stringResult.model.isNotEmpty()) {
                            val stringRegion = uModel.getRegion(UStringRegionId(ctx)) as UStringModelRegion
                            stringRegion.add(stringResult.model)
                        }
                        return USatResult(uModel)
                    }

                    is UUnknownResult -> return UUnknownResult()
                    is UUnsatResult -> return UUnsatResult()
                }
            } while (iter < ITERATIONS_THRESHOLD || ITERATIONS_THRESHOLD == INFINITE_ITERATIONS)

            return UUnsatResult()
        }
    }

    private fun internalCheckWithSoftConstraints(
        softConstraints: MutableList<UBoolExpr>,
    ): KSolverStatus {
        var status: KSolverStatus
        if (softConstraints.isNotEmpty()) {
            status = smtSolver.checkWithAssumptions(softConstraints, timeout)

            while (status == KSolverStatus.UNSAT) {
                val unsatCore = smtSolver.unsatCore().toHashSet()
                if (unsatCore.isEmpty()) break
                softConstraints.removeAll { it in unsatCore }
                status = smtSolver.checkWithAssumptions(softConstraints, timeout)
            }
        } else {
            status = smtSolver.check(timeout)
        }
        return status
    }

    private fun buildStringSolverQuery(kModel: KModel, uModel: UModelBase<*>): UStringSolverQuery {
        val logger = lazy {
            logger.debug { "======== Query to string solver begin ========" }
            logger
        }

        val stringSolverQuery = UStringSolverQuery()

        // [stringSolverQuery] uses [uModel] to evaluate constraints. It is crucial for uModel to have
        // [UStringRegion] in non-completing state (see docs of [UStringRegion]) to allow partial evaluation
        // of string expressions.
        // Also, we clone uModel not to spoil its transformation caches.
        val uModelClone = UModelBase(ctx, uModel.stack, uModel.types, uModel.mocker, uModel.regions, uModel.nullRef)
        val stringRegion = uModelClone.getRegion(UStringRegionId(ctx)) as? UStringModelRegion
        stringRegion?.setCompletion(false)

        kModel.declarations.forEach { decl ->
            when (decl.sort) {
                ctx.boolSort -> {
                    translator.declToBoolStringExpr[decl]?.let { boolStringConstraint ->
                        logger.value.debug { "Asserting (boolean) $boolStringConstraint" }
                        val result = kModel.eval<UBoolSort>(decl.apply(listOf()).cast(), isComplete = true).isTrue
                        stringSolverQuery.addBooleanConstraint(uModelClone, boolStringConstraint, result)
                    }
                }

                ctx.sizeSort -> {
                    translator.declToIntStringExpr[decl]?.let { intStringConstraint ->
                        logger.value.debug { "Asserting (integer) $intStringConstraint" }
                        val result = ctx.withSizeSort<USort>()
                            .getIntValue(kModel.eval(decl.apply(listOf()).cast(), isComplete = true))
                            ?: error("Wasn't able to evaluate int value")
                        stringSolverQuery.addIntConstraint(uModelClone, intStringConstraint, result)
                    }
                }

                ctx.charSort -> {
                    translator.declToCharStringExpr[decl]?.let { charStringConstraint ->
                        logger.value.debug { "Asserting (char) $charStringConstraint" }
                        val result = (kModel.eval(decl.apply(listOf()), isComplete = true) as? UConcreteChar)?.character
                            ?: error("Wasn't able to evaluate char value")
                        stringSolverQuery.addCharConstraint(uModelClone, charStringConstraint, result)
                    }
                }

                is UFpSort -> {
                    translator.declToFloatStringExpr[decl]?.let { floatStringConstraint ->
                        logger.value.debug { "Asserting (float) $floatStringConstraint" }
                        val result = getFloatValue(kModel.eval(decl.apply(listOf()).cast(), true))
                            ?: error("Wasn't able to evaluate float value")
                        stringSolverQuery.addFloatConstraint(uModelClone, floatStringConstraint, result)
                    }
                }
            }
        }

        stringRegion?.setCompletion(true)

        if (logger.isInitialized()) {
            logger.value.debug { "======== Query to string solver end ========" }
        }
        return stringSolverQuery
    }

    private inline fun <T> KSolver<*>.withAssertionsScope(block: KSolver<*>.() -> T): T = try {
        push()
        block()
    } finally {
        pop()
    }

    fun emptyModel(): UModelBase<Type> =
        (check(UPathConstraints(ctx)) as USatResult<UModelBase<Type>>).model

    override fun close() {
        smtSolver.close()
    }

    companion object {
        // TODO: options
        /**
         * -1 means no threshold
         */
        val ITERATIONS_THRESHOLD = -1
        val INFINITE_ITERATIONS = -1
    }
}
