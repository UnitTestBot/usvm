package org.usvm.solver

import io.ksmt.KContext
import io.ksmt.decl.KDecl
import io.ksmt.expr.KApp
import io.ksmt.expr.KConst
import io.ksmt.expr.KExpr
import io.ksmt.expr.transformer.KExprVisitResult
import io.ksmt.expr.transformer.KNonRecursiveVisitor
import io.ksmt.solver.KModel
import io.ksmt.sort.KSort
import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.set.UAnySetRegionId
import org.usvm.collection.set.URefSetRegionId
import org.usvm.collection.set.USetRegionId
import org.usvm.collection.set.length.UInputSetLength
import org.usvm.collection.set.length.UInputSetLengthId
import org.usvm.collection.set.length.UInputSetLengthReading
import org.usvm.collection.set.length.UPrimitiveSetLengthLazyModelRegion
import org.usvm.collection.set.length.URefSetLengthLazyModelRegion
import org.usvm.collection.set.length.USetLengthEagerModelRegion
import org.usvm.collection.set.length.USetLengthLValue
import org.usvm.collection.set.length.USetLengthRegionId
import org.usvm.collection.set.length.USymbolicSetIntersectionSize
import org.usvm.collection.set.primitive.UInputSet
import org.usvm.collection.set.primitive.UInputSetId
import org.usvm.collection.set.primitive.USetReadOnlyRegion
import org.usvm.collection.set.ref.UInputRefSetWithInputElements
import org.usvm.collection.set.ref.UInputRefSetWithInputElementsId
import org.usvm.collection.set.ref.URefSetReadOnlyRegion
import org.usvm.getIntValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGeExpr
import org.usvm.mkSizeGtExpr
import org.usvm.model.UMemory1DArray
import org.usvm.model.UModelBase
import org.usvm.regions.Region
import org.usvm.sizeSort

data class USetLengthSolverQuery(
    val kModel: KModel,
    val model: UModelBase<*>,
)

data class USetLengthSolverModel<USizeSort : USort>(
    val model: Map<USetLengthRegionId<*, USizeSort>, UReadOnlyMemoryRegion<*, USizeSort>>
)

data class USetLengthSolverUnsatResult<USizeSort : USort>(
    val setLengthLemmas: List<UBoolExpr>
) : UUnsatResult<USetLengthSolverModel<USizeSort>>()

class USetLengthSolver<USizeSort : USort>(
    val assertions: List<KExpr<UBoolSort>>,
    val translator: UExprTranslator<*, USizeSort>,
) : USolver<USetLengthSolverQuery, USetLengthSolverModel<USizeSort>>() {
    private val ctx: UContext<USizeSort> = translator.ctx
    private val setLengthConstraints: SetLengthConstraints<USizeSort> by lazy { collectSetLengthConstraints() }

    override fun check(query: USetLengthSolverQuery): USolverResult<USetLengthSolverModel<USizeSort>> {
        val modelSetRegionIds = hashSetOf<UAnySetRegionId<*, *>>()
        val modelSetLengthRegionIds = hashSetOf<UAnySetRegionId<*, *>>()
        modelSetLengthRegionIds.addAll(setLengthConstraints.lengthConstraints.keys)
        modelSetLengthRegionIds.addAll(setLengthConstraints.intersectionConstraints.keys)
        query.model.regions.keys.forEach { regionId ->
            when (regionId) {
                is UAnySetRegionId<*, *> -> modelSetRegionIds.add(regionId)
                is USetLengthRegionId<*, *> -> modelSetLengthRegionIds.add(regionId.setId)
            }
        }

        if (modelSetLengthRegionIds.isEmpty() && modelSetRegionIds.isEmpty()) {
            return USatResult(USetLengthSolverModel(emptyMap()))
        }

        val setLengthModel = hashMapOf<USetLengthRegionId<*, USizeSort>, UReadOnlyMemoryRegion<*, USizeSort>>()
        val setLengthLemmas = mutableListOf<UBoolExpr>()

        for (setId in modelSetRegionIds) {
            generateSetLengthModel(setLengthModel, setLengthLemmas, query, setId, setDefinedInCurrentModel = true)
        }

        val lengthWithoutSetModel = modelSetLengthRegionIds - modelSetRegionIds
        for (setId in lengthWithoutSetModel) {
            generateSetLengthModel(setLengthModel, setLengthLemmas, query, setId, setDefinedInCurrentModel = false)
        }

        if (setLengthLemmas.isNotEmpty()) {
            return USetLengthSolverUnsatResult(setLengthLemmas)
        }

        return USatResult(USetLengthSolverModel(setLengthModel))
    }

    private fun generateSetLengthModel(
        setLengthModel: MutableMap<USetLengthRegionId<*, USizeSort>, UReadOnlyMemoryRegion<*, USizeSort>>,
        setLengthLemmas: MutableList<UBoolExpr>,
        query: USetLengthSolverQuery,
        setId: UAnySetRegionId<*, *>,
        setDefinedInCurrentModel: Boolean
    ) {
        val setLengthId = USetLengthRegionId(ctx.sizeSort, setId)
        val regionLengthConstraints = setLengthConstraints.lengthConstraints[setId].orEmpty()
        val regionIntersectionConstraints = setLengthConstraints.intersectionConstraints[setId].orEmpty()
            .mapNotNull { (expr, constraint) ->
                val value = query.kModel.interpretation(expr.decl)?.default ?: return@mapNotNull null
                constraint to value
            }

        if (setDefinedInCurrentModel) {
            generateSetLengthModelFromSetModel(
                setLengthModel,
                setLengthLemmas,
                query.model,
                setId,
                setLengthId,
                regionLengthConstraints,
                regionIntersectionConstraints
            )
        } else {
            // Set not in the model -> will be completed with an empty set
            generateSetLengthModelForEmptySet(
                setLengthModel,
                setLengthLemmas,
                query.model,
                setId,
                setLengthId,
                regionLengthConstraints,
                regionIntersectionConstraints
            )
        }
    }

    private fun generateSetLengthModelFromSetModel(
        setLengthModel: MutableMap<USetLengthRegionId<*, USizeSort>, UReadOnlyMemoryRegion<*, USizeSort>>,
        setLengthLemmas: MutableList<UBoolExpr>,
        currentModel: UModelBase<*>,
        setId: UAnySetRegionId<*, *>,
        setLengthId: USetLengthRegionId<*, USizeSort>,
        lengthConstraints: List<UInputSetLengthReading<*, USizeSort>>,
        intersectionConstraints: List<Pair<USymbolicSetIntersectionSize<USizeSort>, UExpr<USizeSort>>>
    ) {
        setLengthModel[setLengthId] = setLengthModelFromSetModel(currentModel, setId, setLengthId)

        if (lengthConstraints.isEmpty() && intersectionConstraints.isEmpty()) {
            return
        }

        for (constraint in lengthConstraints) {
            val setRef = currentModel.eval(constraint.address)
            val currentSetLength = currentModel.read(USetLengthLValue(setRef, setId, ctx.sizeSort)).concreteModelValue
            val actualSetLength = currentModel.setOperation(
                setId,
                onRefSet = { setModel -> setModel.setEntries(setRef).entries.size },
                onPrimitiveSet = { setModel -> setModel.setEntries(setRef).entries.size }
            )

            if (currentSetLength == actualSetLength) {
                continue
            }

            val setLengthExpr = ctx.mkInputSetLengthReading(emptySetLengthCollection(setLengthId), constraint.address)

            if (currentSetLength < 0) {
                setLengthLemmas += with(ctx) {
                    mkSizeGeExpr(setLengthExpr, mkSizeExpr(0))
                }
                continue
            }

            if (currentSetLength > actualSetLength) {
                TODO("decrease set length or add more elements")
            } else {
                TODO("increase set length or remove elements")
            }
        }

        if (intersectionConstraints.isEmpty()) {
            return
        }

        TODO("intersection constraint")
    }

    private fun generateSetLengthModelForEmptySet(
        setLengthModel: MutableMap<USetLengthRegionId<*, USizeSort>, UReadOnlyMemoryRegion<*, USizeSort>>,
        setLengthLemmas: MutableList<UBoolExpr>,
        currentModel: UModelBase<*>,
        setId: UAnySetRegionId<*, *>,
        setLengthId: USetLengthRegionId<*, USizeSort>,
        lengthConstraints: List<UInputSetLengthReading<*, USizeSort>>,
        intersectionConstraints: List<Pair<USymbolicSetIntersectionSize<USizeSort>, UExpr<USizeSort>>>
    ) {
        if (lengthConstraints.isEmpty() && intersectionConstraints.isEmpty()) {
            setLengthModel[setLengthId] = emptySetLengthModel(setLengthId)
            return
        }

        for (constraint in lengthConstraints) {
            val setRef = currentModel.eval(constraint.address)
            val currentSetLength = currentModel.read(USetLengthLValue(setRef, setId, ctx.sizeSort)).concreteModelValue

            if (currentSetLength == 0) {
                continue
            }

            val setLengthExpr = ctx.mkInputSetLengthReading(emptySetLengthCollection(setLengthId), constraint.address)

            if (currentSetLength < 0) {
                setLengthLemmas += with(ctx) {
                    mkSizeGeExpr(setLengthExpr, mkSizeExpr(0))
                }
                continue
            }

            val setContainsOneElement: UBoolExpr = setOperation(
                setId,
                onRefSet = { refSetId ->
                    ctx.mkInputRefSetWithInputElementsReading(
                        emptyRefSetCollection(refSetId),
                        constraint.address,
                        ctx.mkFreshConst("set_length_solver_stub", ctx.addressSort)
                    )
                },
                onPrimitiveSet = { primitiveSetId ->
                    ctx.mkInputSetReading(
                        emptyPrimitiveSetCollection(primitiveSetId),
                        constraint.address,
                        ctx.mkFreshConst("set_length_solver_stub", primitiveSetId.elementSort)
                    )
                }
            )

            setLengthLemmas += with(ctx) {
                mkOr(
                    mkEq(setLengthExpr, mkSizeExpr(0)),
                    mkSizeGtExpr(setLengthExpr, mkSizeExpr(0)) and setContainsOneElement
                )
            }
        }

        if (intersectionConstraints.isEmpty()) {
            return
        }

        TODO("intersection constraint")
    }

    @Suppress("UNCHECKED_CAST")
    private fun setLengthModelFromSetModel(
        model: UModelBase<*>,
        setId: UAnySetRegionId<*, *>,
        setLengthId: USetLengthRegionId<*, USizeSort>,
    ): UReadOnlyMemoryRegion<*, USizeSort> = model.setOperation(
        setId,
        onRefSet = { setModel ->
            URefSetLengthLazyModelRegion(setLengthId as USetLengthRegionId<Any?, USizeSort>, setModel)
        },
        onPrimitiveSet = { setModel ->
            UPrimitiveSetLengthLazyModelRegion(setLengthId as USetLengthRegionId<Any?, USizeSort>, setModel)
        }
    )

    @Suppress("UNCHECKED_CAST")
    private inline fun <T> UReadOnlyMemory<*>.setOperation(
        setId: UAnySetRegionId<*, *>,
        onRefSet: (URefSetReadOnlyRegion<Any?>) -> T,
        onPrimitiveSet: (USetReadOnlyRegion<Any?, *, *>) -> T
    ): T = when (setId) {
        is URefSetRegionId<*> -> onRefSet(getRegion(setId) as URefSetReadOnlyRegion<Any?>)
        is USetRegionId<*, *, *> -> onPrimitiveSet(getRegion(setId) as USetReadOnlyRegion<Any?, *, *>)
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <T> setOperation(
        setId: UAnySetRegionId<*, *>,
        onRefSet: (URefSetRegionId<Any?>) -> T,
        onPrimitiveSet: (USetRegionId<Any?, USort, Nothing>) -> T
    ): T = when(setId) {
        is URefSetRegionId<*> -> onRefSet(setId as URefSetRegionId<Any?>)
        is USetRegionId<*, *, *> -> onPrimitiveSet(setId as USetRegionId<Any?, USort, Nothing>)
    }

    private fun <SetType> emptySetLengthModel(
        setLengthId: USetLengthRegionId<SetType, USizeSort>
    ): UReadOnlyMemoryRegion<*, USizeSort> = USetLengthEagerModelRegion(setLengthId, UMemory1DArray(ctx.mkSizeExpr(0)))

    private fun <SetType> emptyRefSetCollection(regionId: URefSetRegionId<SetType>): UInputRefSetWithInputElements<SetType> =
        UInputRefSetWithInputElementsId(regionId.setType, regionId.sort).emptyRegion()

    private fun <SetType, ESort : USort, Reg : Region<Reg>> emptyPrimitiveSetCollection(
        regionId: USetRegionId<SetType, ESort, Reg>
    ): UInputSet<SetType, ESort, Reg> =
        UInputSetId(regionId.elementSort, regionId.setType, regionId.elementInfo).emptyRegion()

    @Suppress("UNCHECKED_CAST")
    private fun emptySetLengthCollection(
        regionId: USetLengthRegionId<*, USizeSort>
    ): UInputSetLength<Any?, USizeSort> =
        UInputSetLengthId(regionId.setId as UAnySetRegionId<Any?, *>, ctx.sizeSort).emptyRegion()

    private val UExpr<USizeSort>.concreteModelValue: Int
        get() = this@USetLengthSolver.ctx.getIntValue(this)
            ?: error("Non concrete size value in model: $this")

    private fun collectSetLengthConstraints(): SetLengthConstraints<USizeSort> {
        val collector = SetLengthConstraintCollector(
            translator.ctx,
            translator.ctx.sizeSort,
            translator.declToSetIntersectionSizeExpr,
            translator.setLengthReadings.associateBy { translator.translate(it) }
        )

        assertions.forEach { collector.apply(it) }

        return SetLengthConstraints(
            collector.setIntersectionConstraints.groupBy { it.second.setId },
            collector.setLengthConstraints.groupBy { it.collection.collectionId.setId }
        )
    }

    private data class SetLengthConstraints<USizeSort : USort>(
        val intersectionConstraints: Map<UAnySetRegionId<*, *>, List<Pair<KApp<USizeSort, *>, USymbolicSetIntersectionSize<USizeSort>>>>,
        val lengthConstraints: Map<UAnySetRegionId<*, *>, List<UInputSetLengthReading<*, USizeSort>>>
    )

    private class SetLengthConstraintCollector<USizeSort : USort>(
        ctx: KContext,
        val sizeSort: USizeSort,
        val setIntersectionDecls: Map<KDecl<USizeSort>, USymbolicSetIntersectionSize<USizeSort>>,
        val setLengthReadings: Map<KExpr<USizeSort>, UInputSetLengthReading<*, USizeSort>>
    ) : KNonRecursiveVisitor<Unit>(ctx) {
        val setIntersectionConstraints =
            mutableListOf<Pair<KApp<USizeSort, *>, USymbolicSetIntersectionSize<USizeSort>>>()
        val setLengthConstraints = mutableListOf<UInputSetLengthReading<*, USizeSort>>()

        override fun <T : KSort> defaultValue(expr: KExpr<T>): Unit = Unit
        override fun mergeResults(left: Unit, right: Unit): Unit = Unit

        override fun <T : KSort> visitExpr(expr: KExpr<T>): KExprVisitResult<Unit> {
            if (expr.sort == sizeSort) {
                setLengthReadings[expr.uncheckedCast()]?.let { setLengthConstraints.add(it) }
            }
            return super.visitExpr(expr)
        }

        override fun <T : KSort> visit(expr: KConst<T>): KExprVisitResult<Unit> {
            if (expr.sort == sizeSort) {
                val typedExpr: KConst<USizeSort> = expr.uncheckedCast()
                setIntersectionDecls[typedExpr.decl]?.let { setIntersectionConstraints.add(typedExpr to it) }
            }
            return super.visit(expr)
        }
    }
}
