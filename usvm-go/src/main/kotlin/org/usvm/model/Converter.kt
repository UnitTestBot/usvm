package org.usvm.model

import org.jacodb.go.api.BasicType
import org.jacodb.go.api.GoBasicBlock
import org.jacodb.go.api.GoConditionExpr
import org.jacodb.go.api.GoFunction
import org.jacodb.go.api.GoGtrExpr
import org.jacodb.go.api.GoIfInst
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoInstLocation
import org.jacodb.go.api.GoInstLocationImpl
import org.jacodb.go.api.GoInstRef
import org.jacodb.go.api.GoJumpInst
import org.jacodb.go.api.GoNullInst
import org.jacodb.go.api.GoParameter
import org.jacodb.go.api.GoReturnInst
import org.jacodb.go.api.GoType
import org.jacodb.go.api.GoValue
import org.jacodb.go.api.GoVar
import org.usvm.GoPackage

object Converter {
    fun unpackPackage(pkg: Package): GoPackage {
        return GoPackage(pkg.name, pkg.members.filterIsInstance<Member.Function>().map { function ->
            GoFunction(
                BasicType(function.name),
                function.parameters.mapIndexed(::unpackParameter),
                function.name,
                emptyList(),
                pkg.name,
                function.returnTypes.map(::unpackReturnType),
            ).also { it.blocks = function.basicBlocks.map { block -> unpackBasicBlock(it, block) } }
        })
    }

    private fun unpackParameter(index: Int, param: Value): GoParameter {
        return GoParameter(index, param.name, BasicType(param.goType))
    }

    private fun unpackBasicBlock(function: GoFunction, block: Member.BasicBlock): GoBasicBlock {
        return GoBasicBlock(block.index, block.next, block.prev, block.instructions.map { unpackInstruction(function, it) })
    }

    private fun unpackInstruction(function: GoFunction, inst: Instruction): GoInst {
        val location = GoInstLocationImpl(function, -1, -1) // TODO real index and lineNumber
        return when (inst) {
            is Instruction.Alloc -> TODO()
            is Instruction.BinOp -> unpackBinOp(location, inst)
            is Instruction.Call -> TODO()
            is Instruction.ChangeInterface -> TODO()
            is Instruction.ChangeType -> TODO()
            is Instruction.DebugRef -> TODO()
            is Instruction.Defer -> TODO()
            is Instruction.Extract -> TODO()
            is Instruction.Field -> TODO()
            is Instruction.FieldAddr -> TODO()
            is Instruction.Go -> TODO()
            is Instruction.If -> GoIfInst(location, unpackCondition(inst.condition), GoInstRef(inst.trueBranch), GoInstRef(inst.falseBranch))
            is Instruction.Index -> TODO()
            is Instruction.IndexAddr -> TODO()
            is Instruction.Jump -> GoJumpInst(location, GoInstRef(inst.index))
            is Instruction.Lookup -> TODO()
            is Instruction.MakeChan -> TODO()
            is Instruction.MakeClosure -> TODO()
            is Instruction.MakeInterface -> TODO()
            is Instruction.MakeMap -> TODO()
            is Instruction.MakeSlice -> TODO()
            is Instruction.MapUpdate -> TODO()
            is Instruction.Next -> TODO()
            is Instruction.Panic -> TODO()
            is Instruction.Phi -> TODO()
            is Instruction.Range -> TODO()
            is Instruction.Return -> GoReturnInst(location, inst.results.map(::unpackValue))
            is Instruction.RunDefers -> TODO()
            is Instruction.Select -> TODO()
            is Instruction.Send -> TODO()
            is Instruction.Slice -> TODO()
            is Instruction.SliceToArrayPointer -> TODO()
            is Instruction.Store -> GoNullInst(location.method)
            is Instruction.TypeAssert -> TODO()
            is Instruction.UnOp -> unpackUnOp(location, inst)
        }
    }

    private fun unpackCondition(cond: Value): GoConditionExpr {
        return unpackValue(cond) as GoConditionExpr
    }

    private fun unpackValue(value: Value): GoValue {
        return when (value) {
            is Value.Const -> TODO()
            is Value.FreeVar -> TODO()
            is Value.Global -> TODO()
            is Value.Parameter -> GoParameter(value.index, value.name, BasicType(value.goType))
            is Value.Var -> GoVar(value.name, BasicType(value.goType))
        }
    }

    private fun unpackReturnType(type: String): GoType {
        return BasicType(type)
    }

    private fun unpackBinOp(location: GoInstLocation, binOp: Instruction.BinOp): GoInst {
        return when(binOp.op) {
            ">" -> GoGtrExpr(location, BasicType(binOp.goType), unpackValue(binOp.first), unpackValue(binOp.second), binOp.register).toAssignInst()
            else -> GoNullInst(location.method)
        }
    }

    private fun unpackUnOp(location: GoInstLocation, unOp: Instruction.UnOp): GoInst {
        return when(unOp.op) {
            else -> GoNullInst(location.method)
        }
    }
}