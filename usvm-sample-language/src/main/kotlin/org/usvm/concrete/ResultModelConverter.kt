package org.usvm.concrete

import org.ksmt.expr.KBitVec32Value
import org.ksmt.utils.asExpr
import org.usvm.UAddressCounter.Companion.NULL_ADDRESS
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UBv32Sort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UMemoryBase
import org.usvm.UModelBase
import org.usvm.USort
import org.usvm.concrete.interpreter.typeToSort
import org.usvm.concrete.state.ExecutionState
import org.usvm.isTrue
import org.usvm.language.ArrayCreation
import org.usvm.language.ArrayType
import org.usvm.language.BooleanConst
import org.usvm.language.BooleanType
import org.usvm.language.Expr
import org.usvm.language.Field
import org.usvm.language.IntConst
import org.usvm.language.IntType
import org.usvm.language.Method
import org.usvm.language.Null
import org.usvm.language.SampleType
import org.usvm.language.StructCreation
import org.usvm.language.StructExpr
import org.usvm.language.StructType

class ResultModelConverter(
    private val ctx: UContext,
    private val method: Method<*>,
) {
    fun convert(state: ExecutionState): ProgramExecutionResult {
        val exceptionRegister = state.exceptionRegister
        if (exceptionRegister != null) {
            return UnsuccessfulExecutionResult(exceptionRegister)
        }

        @Suppress("UNCHECKED_CAST")
        val model = state.models.single() as UModelBase<Field<*>, SampleType>

        val inputScope = InputScope(ctx, model)

        val inputValues = method.argumentsTypes.mapIndexed { idx, type ->
            val sort = ctx.typeToSort(type)
            val uExpr = model.stack.eval(idx, sort)
            inputScope.resolveExpr(uExpr, type)
        }
        val inputModel = InputModel(inputValues)


        val returnUExpr = state.returnRegister
        val returnExpr = returnUExpr?.let { inputScope.resolveExpr(it, method.returnType!!) }
        val outputModel = OutputModel(returnExpr)

        return SuccessfulExecutionResult(inputModel, outputModel)
    }

    private class InputScope(
        private val ctx: UContext,
        private val model: UModelBase<Field<*>, SampleType>,
    ) {
        fun resolveExpr(expr: UExpr<out USort>, type: SampleType): Expr<SampleType> =
            when (type) {
                BooleanType -> resolveBoolExpr(expr.asExpr(ctx.boolSort))
                IntType -> resolveIntExpr(expr.asExpr(ctx.bv32Sort))
                is ArrayType<*> -> resolveArrayExpr(expr.asExpr(ctx.addressSort), type)
                is StructType -> resolveStructExpr(expr.asExpr(ctx.addressSort), type)
            }

        fun resolveBoolExpr(expr: UExpr<UBoolSort>) =
            BooleanConst(with(ctx) { model.eval(expr).asExpr(boolSort).isTrue })

        fun resolveIntExpr(expr: UExpr<UBv32Sort>) =
            IntConst(with(ctx) { (model.eval(expr).asExpr(bv32Sort) as KBitVec32Value).intValue })

        fun resolveStructExpr(
            ref: UExpr<UAddressSort>,
            type: StructType,
        ): StructExpr {
            if (ref == ctx.mkConcreteHeapRef(NULL_ADDRESS)) {
                return StructCreation(Null, emptyList())
            }
            val fieldValues = type.struct.fields.associateWith { field ->
                val sort = ctx.typeToSort(field.type)
                val fieldUExpr = model.heap.readField(ref, field, sort)
                resolveExpr(fieldUExpr, field.type)
            }
            return StructCreation(type.struct, fieldValues.toList())
        }

        fun resolveArrayExpr(
            ref: UExpr<UAddressSort>,
            type: ArrayType<*>,
        ): ArrayCreation<*> {
            if (ref == ctx.mkConcreteHeapRef(0)) {
                return ArrayCreation(StructType(Null), IntConst(0), emptyList())
            }
            val lengthUExpr = model.heap.readArrayLength(ref, type)
            val length = (resolveExpr(lengthUExpr, IntType) as IntConst).const
            val resolved = (0 until length).map { idx ->
                val indexUExpr = model.heap.readArrayIndex(ref, ctx.mkBv(idx), type, ctx.typeToSort(type.elementType))
                resolveExpr(indexUExpr, type.elementType)
            }
            return ArrayCreation(type.elementType, IntConst(length), resolved)
        }
    }
}
