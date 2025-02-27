package org.usvm.machine

import io.ksmt.utils.mkConst
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.model.EtsFieldSignature
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.expr.tctx
import org.usvm.machine.interpreter.TsStaticFieldLValue
import org.usvm.machine.interpreter.TsStaticFieldReading
import org.usvm.machine.interpreter.TsStaticFieldRegionId
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelEvaluator
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder

interface TsTransformer : UTransformer<EtsType, TsSizeSort> {
    fun <Sort : USort> transform(expr: TsStaticFieldReading<Sort>): UExpr<Sort>
}

class TsComposer(
    ctx: UContext<TsSizeSort>,
    memory: UReadOnlyMemory<EtsType>,
    ownership: MutabilityOwnership,
) : UComposer<EtsType, TsSizeSort>(ctx, memory, ownership), TsTransformer {
    override fun <Sort : USort> transform(expr: TsStaticFieldReading<Sort>): UExpr<Sort> {
        return memory.read(TsStaticFieldLValue(expr.field, expr.sort))
    }
}

class TsExprTranslator(
    ctx: UContext<TsSizeSort>,
) : UExprTranslator<EtsType, TsSizeSort>(ctx), TsTransformer {
    override fun <Sort : USort> transform(expr: TsStaticFieldReading<Sort>): UExpr<Sort> {
        return getOrPutRegionDecoder(expr.regionId) {
            TsStaticFieldDecoder(expr.regionId, this)
        }.translate(expr)
    }
}

class TsStaticFieldDecoder<Sort : USort>(
    private val regionId: TsStaticFieldRegionId<Sort>,
    private val translator: UExprTranslator<*, *>,
) : URegionDecoder<TsStaticFieldLValue<Sort>, Sort> {
    private val translated = mutableMapOf<EtsFieldSignature, UExpr<Sort>>()

    fun translate(expr: TsStaticFieldReading<Sort>): UExpr<Sort> =
        translated.getOrPut(expr.field) {
            expr.sort.mkConst("${expr.field.enclosingClass}_${regionId.sort}_${expr.field.name}")
        }

    override fun decodeLazyRegion(
        model: UModelEvaluator<*>,
        assertions: List<UBoolExpr>,
    ): UReadOnlyMemoryRegion<TsStaticFieldLValue<Sort>, Sort> =
        TsStaticFieldModel(model, translated, translator)
}

class TsStaticFieldModel<Sort : USort>(
    private val model: UModelEvaluator<*>,
    private val translatedFields: Map<EtsFieldSignature, UExpr<Sort>>,
    private val translator: UExprTranslator<*, *>,
) : UReadOnlyMemoryRegion<TsStaticFieldLValue<Sort>, Sort> {
    override fun read(key: TsStaticFieldLValue<Sort>): UExpr<Sort> {
        val translated = translatedFields[key.field]
            ?: translator.translate(
                key.sort.tctx.mkStaticFieldReading(key.memoryRegionId as TsStaticFieldRegionId, key.field, key.sort)
            )
        return model.evalAndComplete(translated)
    }
}
