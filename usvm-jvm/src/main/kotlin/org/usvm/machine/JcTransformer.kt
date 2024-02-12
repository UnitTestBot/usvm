package org.usvm.machine

import io.ksmt.expr.KExpr
import io.ksmt.solver.KModel
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.mkConst
import org.jacodb.api.JcField
import org.jacodb.api.JcType
import org.usvm.UComposer
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.machine.interpreter.statics.JcStaticFieldLValue
import org.usvm.machine.interpreter.statics.JcStaticFieldReading
import org.usvm.machine.interpreter.statics.JcStaticFieldRegionId
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.mapAddress
import org.usvm.solver.UExprTranslator
import org.usvm.solver.URegionDecoder
import org.usvm.solver.USoftConstraintsProvider

interface JcTransformer : UTransformer<JcType, USizeSort> {
    fun <Sort : USort> transform(expr: JcStaticFieldReading<Sort>): UExpr<Sort>
}

class JcComposer(
    ctx: UContext<USizeSort>,
    memory: UReadOnlyMemory<JcType>,
) : UComposer<JcType, USizeSort>(ctx, memory), JcTransformer {
    override fun <Sort : USort> transform(expr: JcStaticFieldReading<Sort>): UExpr<Sort> =
        memory.read(JcStaticFieldLValue(expr.field, expr.sort))
}

class JcExprTranslator(ctx: UContext<USizeSort>) : UExprTranslator<JcType, USizeSort>(ctx), JcTransformer {
    override fun <Sort : USort> transform(expr: JcStaticFieldReading<Sort>): UExpr<Sort> =
        getOrPutRegionDecoder(expr.regionId) {
            JcStaticFieldDecoder(expr.regionId, this)
        }.translate(expr)
}

class JcStaticFieldDecoder<Sort : USort>(
    private val regionId: JcStaticFieldRegionId<Sort>,
    private val translator: UExprTranslator<*, *>,
) : URegionDecoder<JcStaticFieldLValue<Sort>, Sort> {
    private val translated = mutableMapOf<JcField, UExpr<Sort>>()

    fun translate(expr: JcStaticFieldReading<Sort>): UExpr<Sort> =
        translated.getOrPut(expr.field) {
            expr.sort.mkConst("${expr.field.enclosingClass}_${regionId.sort}_${expr.field.name}")
        }

    override fun decodeLazyRegion(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>,
        assertions: List<KExpr<KBoolSort>>,
    ): UReadOnlyMemoryRegion<JcStaticFieldLValue<Sort>, Sort> =
        JcStaticFieldModel(model, mapping, translated, translator)
}

class JcStaticFieldModel<Sort : USort>(
    private val model: KModel,
    private val mapping: Map<UHeapRef, UConcreteHeapRef>,
    private val translatedFields: Map<JcField, UExpr<Sort>>,
    private val translator: UExprTranslator<*, *>
) : UReadOnlyMemoryRegion<JcStaticFieldLValue<Sort>, Sort> {
    override fun read(key: JcStaticFieldLValue<Sort>): UExpr<Sort> {
        val translated = translatedFields[key.field]
            ?: translator.translate(
                key.sort.jctx.mkStaticFieldReading(key.memoryRegionId as JcStaticFieldRegionId, key.field, key.sort)
            )
        return model.eval(translated, isComplete = true).mapAddress(mapping)
    }
}

class JcSoftConstraintsProvider(
    ctx: UContext<USizeSort>,
) : USoftConstraintsProvider<JcType, USizeSort>(ctx), JcTransformer {
    override fun <Sort : USort> transform(
        expr: JcStaticFieldReading<Sort>,
    ): UExpr<Sort> = transformExpr(expr)
}
