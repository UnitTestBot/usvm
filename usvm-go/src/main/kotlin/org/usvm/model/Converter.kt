package org.usvm.model

import org.jacodb.go.api.ArrayType
import org.jacodb.go.api.BasicType
import org.jacodb.go.api.ChanType
import org.jacodb.go.api.GoAddExpr
import org.jacodb.go.api.GoAllocExpr
import org.jacodb.go.api.GoAndExpr
import org.jacodb.go.api.GoAndNotExpr
import org.jacodb.go.api.GoBasicBlock
import org.jacodb.go.api.GoBinaryExpr
import org.jacodb.go.api.GoBool
import org.jacodb.go.api.GoBuiltin
import org.jacodb.go.api.GoCallExpr
import org.jacodb.go.api.GoConditionExpr
import org.jacodb.go.api.GoDivExpr
import org.jacodb.go.api.GoEqlExpr
import org.jacodb.go.api.GoExtractExpr
import org.jacodb.go.api.GoFloat32
import org.jacodb.go.api.GoFloat64
import org.jacodb.go.api.GoFreeVar
import org.jacodb.go.api.GoFunction
import org.jacodb.go.api.GoGeqExpr
import org.jacodb.go.api.GoGlobal
import org.jacodb.go.api.GoGtrExpr
import org.jacodb.go.api.GoIfInst
import org.jacodb.go.api.GoIndexAddrExpr
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
import org.jacodb.go.api.GoLookupExpr
import org.jacodb.go.api.GoLssExpr
import org.jacodb.go.api.GoMakeClosureExpr
import org.jacodb.go.api.GoMakeMapExpr
import org.jacodb.go.api.GoMakeSliceExpr
import org.jacodb.go.api.GoMapUpdateInst
import org.jacodb.go.api.GoModExpr
import org.jacodb.go.api.GoMulExpr
import org.jacodb.go.api.GoNeqExpr
import org.jacodb.go.api.GoNextExpr
import org.jacodb.go.api.GoNullConstant
import org.jacodb.go.api.GoNullInst
import org.jacodb.go.api.GoOrExpr
import org.jacodb.go.api.GoParameter
import org.jacodb.go.api.GoPhiExpr
import org.jacodb.go.api.GoRangeExpr
import org.jacodb.go.api.GoReturnInst
import org.jacodb.go.api.GoShlExpr
import org.jacodb.go.api.GoShrExpr
import org.jacodb.go.api.GoStoreInst
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
import org.jacodb.go.api.InterfaceType
import org.jacodb.go.api.MapType
import org.jacodb.go.api.NamedType
import org.jacodb.go.api.OpaqueType
import org.jacodb.go.api.PointerType
import org.jacodb.go.api.SignatureType
import org.jacodb.go.api.SliceType
import org.jacodb.go.api.StructType
import org.jacodb.go.api.TupleType
import org.jacodb.go.api.UnionType
import org.usvm.GoPackage
import org.usvm.type.GoTypes

object Converter {
    private lateinit var types: Map<String, GoType>

    fun unpackPackage(pkg: Package): GoPackage {
        types = pkg.types.mapValues { type -> unpackType(pkg.types, type.value) }
        val methods = pkg.members.filterIsInstance<Member.Function>().map { function ->
            GoFunction(
                SignatureType(TupleType(function.parameters.map { getType(it.goType) }), TupleType(function.returnTypes.map(::getType))),
                function.parameters.mapIndexed(::unpackParameter),
                function.name,
                emptyList(),
                pkg.name,
                function.freeVars.map { unpackValue(it) as GoFreeVar },
                function.returnTypes.map(::getType),
            ).also { it.blocks = function.basicBlocks.map { block -> unpackBasicBlock(it, block) } }
        }
        val globals = pkg.members.filterIsInstance<Member.Global>().map { global ->
            GoGlobal(global.index, global.name, getType(global.goType))
        }
        return GoPackage(pkg.name, methods, globals, types)
    }

    private fun unpackParameter(index: Int, param: Value): GoParameter {
        return GoParameter(index, param.name, getType(param.goType))
    }

    private fun unpackBasicBlock(function: GoFunction, block: Member.BasicBlock): GoBasicBlock {
        return GoBasicBlock(block.index, block.next, block.prev, block.instructions.map { unpackInstruction(function, it) })
    }

    private fun unpackInstruction(function: GoFunction, inst: Instruction): GoInst {
        val location = GoInstLocationImpl(function, inst.block, inst.line)
        return when (inst) {
            is Instruction.Alloc -> GoAllocExpr(location, getType(inst.goType), inst.register).toAssignInst()
            is Instruction.BinOp -> unpackBinOp(location, inst)
            is Instruction.Call -> unpackCall(location, inst)
            is Instruction.ChangeInterface -> TODO()
            is Instruction.ChangeType -> TODO()
            is Instruction.DebugRef -> TODO()
            is Instruction.Defer -> TODO()
            is Instruction.Extract -> unpackExtract(location, inst)
            is Instruction.Field -> TODO()
            is Instruction.FieldAddr -> TODO()
            is Instruction.Go -> TODO()
            is Instruction.If -> GoIfInst(location, unpackCondition(inst.condition), GoInstRef(inst.trueBranch), GoInstRef(inst.falseBranch))
            is Instruction.Index -> TODO()
            is Instruction.IndexAddr -> unpackIndexAddr(location, inst)
            is Instruction.Jump -> GoJumpInst(location, GoInstRef(inst.index))
            is Instruction.Lookup -> unpackLookup(location, inst)
            is Instruction.MakeChan -> TODO()
            is Instruction.MakeClosure -> unpackMakeClosure(location, inst)
            is Instruction.MakeInterface -> TODO()
            is Instruction.MakeMap -> GoMakeMapExpr(location, getType(inst.goType), unpackValue(inst.reserve), inst.register).toAssignInst()
            is Instruction.MakeSlice -> unpackMakeSlice(location, inst)
            is Instruction.MapUpdate -> GoMapUpdateInst(location, unpackValue(inst.map), unpackValue(inst.key), unpackValue(inst.value))
            is Instruction.Next -> GoNextExpr(location, getType(inst.goType), unpackValue(inst.iter), inst.register).toAssignInst()
            is Instruction.Panic -> TODO()
            is Instruction.Phi -> GoPhiExpr(location, getType(inst.goType), inst.edges.map(::unpackValue), inst.register).toAssignInst()
            is Instruction.Range -> GoRangeExpr(location, getType(inst.goType), unpackValue(inst.collection), inst.register).toAssignInst()
            is Instruction.Return -> GoReturnInst(location, inst.results.map(::unpackValue))
            is Instruction.RunDefers -> TODO()
            is Instruction.Select -> TODO()
            is Instruction.Send -> TODO()
            is Instruction.Slice -> TODO()
            is Instruction.SliceToArrayPointer -> TODO()
            is Instruction.Store -> GoStoreInst(location, unpackValue(inst.addr), unpackValue(inst.value))
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
                getType(binOp.goType),
                unpackValue(binOp.first),
                unpackValue(binOp.second),
                binOp.register
            ).toAssignInst()
        }

        return GoNullInst(location.method)
    }

    private fun unpackCall(location: GoInstLocation, call: Instruction.Call): GoInst {
        return GoCallExpr(
            location,
            getType(call.goType),
            unpackValue(call.value),
            call.args.map(::unpackValue),
            null,
            call.register
        ).toAssignInst()
    }

    private fun unpackExtract(location: GoInstLocation, extract: Instruction.Extract): GoInst {
        return GoExtractExpr(location, getType(extract.goType), unpackValue(extract.tuple), extract.index, extract.register).toAssignInst()
    }

    private fun unpackCondition(cond: Value): GoConditionExpr {
        return unpackValue(cond) as GoConditionExpr
    }

    private fun unpackIndexAddr(location: GoInstLocation, indexAddr: Instruction.IndexAddr): GoInst {
        return GoIndexAddrExpr(
            location,
            getType(indexAddr.goType),
            unpackValue(indexAddr.array),
            unpackValue(indexAddr.index),
            indexAddr.register
        ).toAssignInst()
    }

    private fun unpackLookup(location: GoInstLocation, lookup: Instruction.Lookup): GoInst {
        return GoLookupExpr(
            location,
            getType(lookup.goType),
            unpackValue(lookup.map),
            unpackValue(lookup.key),
            lookup.register,
            lookup.commaOk
        ).toAssignInst()
    }

    private fun unpackMakeClosure(location: GoInstLocation, makeClosure: Instruction.MakeClosure): GoInst {
        return GoMakeClosureExpr(
            location,
            OpaqueType(makeClosure.name),
            functionAlias(makeClosure.function.name),
            makeClosure.bindings.map(::unpackValue),
            makeClosure.register
        ).toAssignInst()
    }

    private fun unpackMakeSlice(location: GoInstLocation, makeSlice: Instruction.MakeSlice): GoInst {
        return GoMakeSliceExpr(
            location,
            getType(makeSlice.goType),
            unpackValue(makeSlice.len),
            unpackValue(makeSlice.cap),
            makeSlice.register
        ).toAssignInst()
    }

    private fun unpackValue(value: Value): GoValue {
        return when (value) {
            is Value.Const -> unpackConst(value)
            is Value.FreeVar -> GoFreeVar(value.index, value.name, getType(value.goType))
            is Value.Global -> GoGlobal(value.index, value.name, getType(value.goType))
            is Value.Parameter -> GoParameter(value.index, value.name, getType(value.goType))
            is Value.Var -> GoVar(value.name, getType(value.goType))
            is Value.MakeClosure -> GoVar(value.name, getType(value.goType))
            is Value.Function -> functionAlias(value.name)
            is Value.Builtin -> GoBuiltin(value.name, getType(value.goType))
        }
    }

    private fun unpackUnOp(location: GoInstLocation, unOp: Instruction.UnOp): GoInst {
        val unaryExprConstructors = mapOf<String, Function4<GoInstLocation, GoType, GoValue, String, GoUnaryExpr>>(
            "*" to ::GoUnMulExpr,
            "!" to ::GoUnNotExpr,
            "-" to ::GoUnSubExpr,
            "^" to ::GoUnXorExpr,
        )

        val type = getType(unOp.goType)
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
            GoTypes.BOOL -> GoBool(string.toBooleanStrict(), type)
            GoTypes.INT -> GoInt(string.toLong(), type)
            GoTypes.INT8 -> GoInt8(string.toByte(), type)
            GoTypes.INT16 -> GoInt16(string.toShort(), type)
            GoTypes.INT32 -> GoInt32(string.toInt(), type)
            GoTypes.INT64 -> GoInt64(string.toLong(), type)
            GoTypes.UINT -> GoUInt(string.toULong(), type)
            GoTypes.UINT8 -> GoUInt8(string.toUByte(), type)
            GoTypes.UINT16 -> GoUInt16(string.toUShort(), type)
            GoTypes.UINT32 -> GoUInt32(string.toUInt(), type)
            GoTypes.UINT64 -> GoUInt64(string.toULong(), type)
            GoTypes.FLOAT32 -> GoFloat32(string.toFloat(), type)
            GoTypes.FLOAT64 -> GoFloat64(string.toDouble(), type)
            GoTypes.STRING -> GoStringConstant(string, type)
            else -> GoNullConstant()
        }
    }

    private fun getType(type: String): GoType = types[type]!!

    private fun unpackTypes(typesMap: Map<String, Type>, types: Collection<String>): List<GoType> {
        return types.map { unpackType(typesMap, it) }
    }

    private fun unpackType(types: Map<String, Type>, type: Type): GoType = when (type) {
        is Type.Alias -> unpackType(types, type.from)
        is Type.Array -> ArrayType(type.len, unpackType(types, type.elem))
        is Type.Basic -> BasicType(type.name)
        is Type.Chan -> ChanType(type.dir.toLong(), unpackType(types, type.elem))
        is Type.Interface -> InterfaceType()
        is Type.Map -> MapType(unpackType(types, type.key), unpackType(types, type.elem))
        is Type.Named -> NamedType(unpackType(types, type.underlying), type.name)
        is Type.Opaque -> OpaqueType(type.name)
        is Type.Pointer -> PointerType(unpackType(types, type.elem))
        is Type.Signature -> SignatureType(TupleType(unpackTypes(types, type.params)), TupleType(unpackTypes(types, type.results)))
        is Type.Slice -> SliceType(unpackType(types, type.elem))
        is Type.Struct -> StructType(unpackTypes(types, type.fields.toSortedMap().keys), null)
        is Type.Tuple -> TupleType(unpackTypes(types, type.elems))
        is Type.TypeParam -> unpackType(types, type.name)
        is Type.Union -> UnionType(emptyList())
    }

    private fun unpackType(types: Map<String, Type>, type: String): GoType {
        return unpackType(types, types[type]!!)
    }

    private fun functionAlias(name: String): GoFunction {
        return GoFunction(OpaqueType(name), emptyList(), name, emptyList(), "", emptyList(), emptyList())
    }
}