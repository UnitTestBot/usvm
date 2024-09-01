package org.usvm.model

import org.jacodb.go.api.BasicType
import org.jacodb.go.api.GoAddExpr
import org.jacodb.go.api.GoAndExpr
import org.jacodb.go.api.GoAndNotExpr
import org.jacodb.go.api.GoBasicBlock
import org.jacodb.go.api.GoBinaryExpr
import org.jacodb.go.api.GoBool
import org.jacodb.go.api.GoCallExpr
import org.jacodb.go.api.GoCallInst
import org.jacodb.go.api.GoConditionExpr
import org.jacodb.go.api.GoDivExpr
import org.jacodb.go.api.GoEqlExpr
import org.jacodb.go.api.GoFloat32
import org.jacodb.go.api.GoFloat64
import org.jacodb.go.api.GoFreeVar
import org.jacodb.go.api.GoFunction
import org.jacodb.go.api.GoGeqExpr
import org.jacodb.go.api.GoGlobal
import org.jacodb.go.api.GoGtrExpr
import org.jacodb.go.api.GoIfInst
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoInstLocation
import org.jacodb.go.api.GoInstLocationImpl
import org.jacodb.go.api.GoInstRef
import org.jacodb.go.api.GoInt
import org.jacodb.go.api.GoInt16
import org.jacodb.go.api.GoInt32
import org.jacodb.go.api.GoInt64
import org.jacodb.go.api.GoInt8
import org.jacodb.go.api.GoJumpInst
import org.jacodb.go.api.GoLeqExpr
import org.jacodb.go.api.GoLssExpr
import org.jacodb.go.api.GoModExpr
import org.jacodb.go.api.GoMulExpr
import org.jacodb.go.api.GoNeqExpr
import org.jacodb.go.api.GoNullConstant
import org.jacodb.go.api.GoNullInst
import org.jacodb.go.api.GoOrExpr
import org.jacodb.go.api.GoParameter
import org.jacodb.go.api.GoPhiExpr
import org.jacodb.go.api.GoReturnInst
import org.jacodb.go.api.GoShlExpr
import org.jacodb.go.api.GoShrExpr
import org.jacodb.go.api.GoStringConstant
import org.jacodb.go.api.GoSubExpr
import org.jacodb.go.api.GoType
import org.jacodb.go.api.GoUInt
import org.jacodb.go.api.GoUInt16
import org.jacodb.go.api.GoUInt32
import org.jacodb.go.api.GoUInt64
import org.jacodb.go.api.GoUInt8
import org.jacodb.go.api.GoUnArrowExpr
import org.jacodb.go.api.GoUnMulExpr
import org.jacodb.go.api.GoUnNotExpr
import org.jacodb.go.api.GoUnSubExpr
import org.jacodb.go.api.GoUnXorExpr
import org.jacodb.go.api.GoUnaryExpr
import org.jacodb.go.api.GoValue
import org.jacodb.go.api.GoVar
import org.jacodb.go.api.GoXorExpr
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
        val location = GoInstLocationImpl(function, inst.block, inst.line)
        return when (inst) {
            is Instruction.Alloc -> TODO()
            is Instruction.BinOp -> unpackBinOp(location, inst)
            is Instruction.Call -> unpackCall(location, inst)
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
            is Instruction.Phi -> GoPhiExpr(location, BasicType(inst.goType), inst.edges.map(::unpackValue), inst.register).toAssignInst()
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

    private fun unpackBinOp(location: GoInstLocation, binOp: Instruction.BinOp): GoInst {
        val binaryExprConstructors = mapOf<String, Function5<GoInstLocation, GoType, GoValue, GoValue, String, GoBinaryExpr>>(
            "&&" to ::GoAndExpr,
            "&^" to ::GoAndNotExpr,
            "||" to ::GoOrExpr,
            "^" to ::GoXorExpr,
            "==" to ::GoEqlExpr,
            "!=" to ::GoNeqExpr,
            "<" to ::GoLssExpr,
            "<=" to ::GoLeqExpr,
            ">" to ::GoGtrExpr,
            ">=" to ::GoGeqExpr,
            "+" to ::GoAddExpr,
            "-" to ::GoSubExpr,
            "*" to ::GoMulExpr,
            "/" to ::GoDivExpr,
            "%" to ::GoModExpr,
            "<<" to ::GoShlExpr,
            ">>" to ::GoShrExpr,
        )

        if (binaryExprConstructors.containsKey(binOp.op)) {
            return binaryExprConstructors[binOp.op]!!(
                location,
                BasicType(binOp.goType),
                unpackValue(binOp.first),
                unpackValue(binOp.second),
                binOp.register
            ).toAssignInst()
        }

        return GoNullInst(location.method)
    }

    private fun unpackCall(location: GoInstLocation, call: Instruction.Call): GoInst {
        val expr = GoCallExpr(location, BasicType(call.goType), unpackValue(call.value), call.args.map(::unpackValue), null, call.register)
        return GoCallInst(location, expr, call.register, BasicType(call.goType)).toAssignInst()
    }

    private fun unpackCondition(cond: Value): GoConditionExpr {
        return unpackValue(cond) as GoConditionExpr
    }

    private fun unpackValue(value: Value): GoValue {
        return when (value) {
            is Value.Const -> unpackConst(value)
            is Value.FreeVar -> GoFreeVar(value.index, value.name, BasicType(value.goType))
            is Value.Global -> GoGlobal(value.index, value.name, BasicType(value.goType))
            is Value.Parameter -> GoParameter(value.index, value.name, BasicType(value.goType))
            is Value.Var -> GoVar(value.name, BasicType(value.goType))
            is Value.Builtin, is Value.Function, is Value.MakeClosure -> GoVar(value.name, BasicType(value.goType))
        }
    }

    private fun unpackReturnType(type: String): GoType {
        return BasicType(type)
    }

    private fun unpackUnOp(location: GoInstLocation, unOp: Instruction.UnOp): GoInst {
        val unaryExprConstructors = mapOf<String, Function4<GoInstLocation, GoType, GoValue, String, GoUnaryExpr>>(
            "*" to ::GoUnMulExpr,
            "!" to ::GoUnNotExpr,
            "-" to ::GoUnSubExpr,
            "^" to ::GoUnXorExpr,
        )

        val type = BasicType(unOp.goType)
        val arg = unpackValue(unOp.argument)
        val name = unOp.register

        return when {
            unaryExprConstructors.containsKey(unOp.op) -> unaryExprConstructors[unOp.op]!!(location, type, arg, name).toAssignInst()
            unOp.op == "<-" -> GoUnArrowExpr(location, type, arg, unOp.commaOk, name).toAssignInst()
            else -> GoNullInst(location.method)
        }
    }

    private fun unpackConst(value: Value.Const): GoValue {
        val const = value.value
        val string = const.value
        val type = BasicType(const.type)

        return when (const.type) {
            "bool" -> GoBool(string.toBooleanStrict(), type)
            "int" -> GoInt(string.toLong(), type)
            "int8" -> GoInt8(string.toByte(), type)
            "int16" -> GoInt16(string.toShort(), type)
            "int32" -> GoInt32(string.toInt(), type)
            "int64" -> GoInt64(string.toLong(), type)
            "uint" -> GoUInt(string.toULong(), type)
            "uint8" -> GoUInt8(string.toUByte(), type)
            "uint16" -> GoUInt16(string.toUShort(), type)
            "uint32" -> GoUInt32(string.toUInt(), type)
            "uint64" -> GoUInt64(string.toULong(), type)
            "float32" -> GoFloat32(string.toFloat(), type)
            "float64" -> GoFloat64(string.toDouble(), type)
            "string" -> GoStringConstant(string, type)
            else -> GoNullConstant()
        }
    }
}