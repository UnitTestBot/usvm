package org.usvm.machine

import io.ksmt.expr.KExpr
import io.ksmt.sort.KSortVisitor
import io.ksmt.sort.KUninterpretedSort
import io.ksmt.utils.DefaultValueSampler
import io.ksmt.utils.mkConst
import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaBoolType
import org.jacodb.panda.dynamic.api.PandaClass
import org.jacodb.panda.dynamic.api.PandaField
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaPrimitiveType
import org.jacodb.panda.dynamic.api.PandaRefType
import org.jacodb.panda.dynamic.api.PandaStringType
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.api.PandaTypeName
import org.jacodb.panda.dynamic.api.PandaUndefinedType
import org.jacodb.panda.dynamic.api.PandaVoidType
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.typeStreamOf
import org.usvm.collection.field.UFieldLValue
import org.usvm.memory.UMemory
import org.usvm.types.single

class PandaContext(components: PandaComponents) : UContext<PandaNumberSort>(components) {
    val anySort: PandaAnySort by lazy { PandaAnySort(this) }
    val voidSort: PandaVoidSort by lazy { PandaVoidSort(this) }
    val undefinedSort: PandaUndefinedSort by lazy { PandaUndefinedSort(this) }
    val stringSort: PandaStringSort by lazy { PandaStringSort(this) }

    val undefinedObject: UExpr<PandaUndefinedSort> = undefinedSort.mkConst("UndefinedObject") // TODO replace?

    fun typeToSort(type: PandaType): USort = when (type) {
        is PandaAnyType -> addressSort // TODO("?????????") can we replace it with address sort????
        is PandaVoidType -> voidSort
        is PandaUndefinedType -> undefinedSort
        is PandaRefType -> addressSort
        is PandaBoolType -> boolSort
        is PandaNumberType -> fp64Sort
        is PandaStringType -> stringSort
        else -> error("Unknown type: $type")
    }

    fun nonRefSortToType(sort: USort): PandaType = when (sort) {
        boolSort -> PandaBoolType
        fp64Sort -> PandaNumberType
        stringSort -> PandaStringType
        undefinedSort -> PandaUndefinedType
        else -> error("TODO")
    }

    private val auxiliaryClass by lazy {
        PandaClass(
            name = "#Number",
            superClassName = "GLOBAL",
            methods = emptyList()
        )
    }

    private val stringField = PandaField(
        name = "#stringValue",
        type = PandaStringType.typeNameInstance,
        signature = null, // TODO ?????
        _enclosingClass = auxiliaryClass
    )

    private val fields = mutableMapOf<USort, PandaField>()
    fun getField(sort: USort) = fields.getOrPut(
        sort
    ) {
        PandaField(
            name = "#value$sort",
            type = nonRefSortToType(sort).typeNameInstance,
            signature = null, // TODO ?????
            _enclosingClass = auxiliaryClass
        )
    }

    // TODO string?????????????????????
    fun constructAuxiliaryFieldLValue(ref: UHeapRef, sort: USort) =
        if (sort is PandaStringSort) {
            PandaStringFieldLValue(ref, stringField, ref.pctx.stringSort)
        } else {
            UFieldLValue(sort, ref, getField(sort))
        }

    private val pandaConcreteStringCache = mkAstInterner<PandaConcreteString>()
    fun mkConcreteString(value: String): PandaConcreteString =
        pandaConcreteStringCache.createIfContextActive {
            PandaConcreteString(this, value)
        }

    fun mkConcreteStringDecl(value: String): PandaConcreteStringDecl =
        PandaConcreteStringDecl(this, value)

    val PandaType.typeNameInstance: PandaTypeName
        get() = PandaTypeName(typeName)

    fun extractPrimitiveValueIfRequired(
        uExpr: UExpr<out USort>,
        scope: PandaStepScope,
    ): UExpr<out USort> {
        if (uExpr !is UConcreteHeapRef) {
            return uExpr
        }

        val type = scope.calcOnState { memory.typeStreamOf(uExpr) }.single()
        return when (type) {
            PandaNumberType -> scope.calcOnState { memory.read(constructAuxiliaryFieldLValue(uExpr, fp64Sort)) }
            PandaBoolType -> scope.calcOnState { memory.read(constructAuxiliaryFieldLValue(uExpr, boolSort)) }
            PandaStringType -> scope.calcOnState { memory.read(constructAuxiliaryFieldLValue(uExpr, stringSort)) }
            else -> uExpr
        }
    }

    override val uValueSampler: KSortVisitor<KExpr<*>> by lazy { mkUValueSampler() }

    override fun mkUValueSampler(): KPandaSortVisitor {
        return KPandaSortVisitor(this)
    }

    class KPandaSortVisitor(ctx: PandaContext) : DefaultValueSampler(ctx) {

        fun visit(sort: PandaUndefinedSort): KExpr<*> = (ctx as PandaContext).undefinedObject

    }

}

fun UMemory<PandaType, PandaMethod>.wrapField(value: UExpr<*>, type: PandaType): UConcreteHeapRef {
    require(value.sort != value.pctx.addressSort)

    val addr = allocConcrete(type)
    val lValue = value.pctx.constructAuxiliaryFieldLValue(addr, value.sort)
    write(lValue, value)
    return addr
}