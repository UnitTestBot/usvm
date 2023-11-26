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
import org.jacodb.go.api.GoChangeInterfaceExpr
import org.jacodb.go.api.GoChangeTypeExpr
import org.jacodb.go.api.GoConditionExpr
import org.jacodb.go.api.GoConvertExpr
import org.jacodb.go.api.GoDeferInst
import org.jacodb.go.api.GoDivExpr
import org.jacodb.go.api.GoEqlExpr
import org.jacodb.go.api.GoExtractExpr
import org.jacodb.go.api.GoFieldAddrExpr
import org.jacodb.go.api.GoFieldExpr
import org.jacodb.go.api.GoFloat32
import org.jacodb.go.api.GoFloat64
import org.jacodb.go.api.GoFreeVar
import org.jacodb.go.api.GoFunction
import org.jacodb.go.api.GoGeqExpr
import org.jacodb.go.api.GoGlobal
import org.jacodb.go.api.GoGtrExpr
import org.jacodb.go.api.GoIfInst
import org.jacodb.go.api.GoIndexAddrExpr
import org.jacodb.go.api.GoIndexExpr
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
import org.jacodb.go.api.GoMakeInterfaceExpr
import org.jacodb.go.api.GoMakeMapExpr
import org.jacodb.go.api.GoMakeSliceExpr
import org.jacodb.go.api.GoMapUpdateInst
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoModExpr
import org.jacodb.go.api.GoMulExpr
import org.jacodb.go.api.GoNeqExpr
import org.jacodb.go.api.GoNextExpr
import org.jacodb.go.api.GoNullConstant
import org.jacodb.go.api.GoNullInst
import org.jacodb.go.api.GoOrExpr
import org.jacodb.go.api.GoPanicInst
import org.jacodb.go.api.GoParameter
import org.jacodb.go.api.GoPhiExpr
import org.jacodb.go.api.GoRangeExpr
import org.jacodb.go.api.GoReturnInst
import org.jacodb.go.api.GoRunDefersInst
import org.jacodb.go.api.GoShlExpr
import org.jacodb.go.api.GoShrExpr
import org.jacodb.go.api.GoSliceExpr
import org.jacodb.go.api.GoSliceToArrayPointerExpr
import org.jacodb.go.api.GoStoreInst
import org.jacodb.go.api.GoStringConstant
import org.jacodb.go.api.GoSubExpr
import org.jacodb.go.api.GoType
import org.jacodb.go.api.GoTypeAssertExpr
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
import org.jacodb.go.api.NullType
import org.jacodb.go.api.OpaqueType
import org.jacodb.go.api.PointerType
import org.jacodb.go.api.SignatureType
import org.jacodb.go.api.SliceType
import org.jacodb.go.api.StructType
import org.jacodb.go.api.TupleType
import org.jacodb.go.api.TypeParam
import org.jacodb.go.api.UnionType
import org.usvm.GoPackage
import org.usvm.type.GoBasicTypes
import org.usvm.type.GoTypes

object Converter {
    private val typesMap: MutableMap<String, GoType> = mutableMapOf()

    fun unpack(pkg: Package): GoPackage {
        typesMap.putAll(pkg.types.mapValues { type -> unpack(pkg.types, type.value) })
        val methods = pkg.members.filterIsInstance<Member.Function>().map { function ->
            GoFunction(
                SignatureType(TupleType(function.parameters.map { getType(it.goType) }), TupleType(function.returnTypes.map(::getType))),
                function.parameters.mapIndexed(this::unpack),
                function.name,
                emptyList(),
                pkg.name,
                function.freeVars.map { unpack(it) as GoFreeVar },
                function.returnTypes.map(::getType),
            ).also {
                it.blocks = function.basicBlocks.map { block -> unpack(it, block) }
                if (function.recover != null) {
                    it.recover = unpack(it, function.recover)
                }
            }
        }
        val globals = pkg.members.filterIsInstance<Member.Global>().map { global ->
            GoGlobal(global.index, global.name, getType(global.goType))
        }
        return GoPackage(pkg.name, methods, globals, typesMap)
    }

    private fun unpack(index: Int, param: Value): GoParameter {
        return GoParameter(index, param.name, getType(param.goType))
    }

    private fun unpack(function: GoFunction, block: Member.BasicBlock): GoBasicBlock {
        return GoBasicBlock(block.index, block.next, block.prev, block.instructions.map { unpack(function, it) })
    }

    private fun unpack(function: GoFunction, inst: Instruction): GoInst {
        val loc = GoInstLocationImpl(function, inst.block, inst.line)
        return when (inst) {
            is Instruction.Alloc -> GoAllocExpr(loc, getType(inst.goType), inst.register, inst.comment).toAssignInst()
            is Instruction.BinOp -> unpack(loc, inst)
            is Instruction.Call -> unpack(loc, inst)
            is Instruction.ChangeInterface -> GoChangeInterfaceExpr(loc, getType(inst.goType), unpack(inst.value), inst.register).toAssignInst()
            is Instruction.ChangeType -> GoChangeTypeExpr(loc, getType(inst.goType), unpack(inst.value), inst.register).toAssignInst()
            is Instruction.Convert -> GoConvertExpr(loc, getType(inst.goType), unpack(inst.value), inst.register).toAssignInst()
            is Instruction.DebugRef -> unsupportedInstruction(function)
            is Instruction.Defer -> GoDeferInst(loc, unpack(inst.value), inst.args.map(::unpack))
            is Instruction.Extract -> unpack(loc, inst)
            is Instruction.Field -> GoFieldExpr(loc, getType(inst.goType), unpack(inst.struct), inst.field, inst.register).toAssignInst()
            is Instruction.FieldAddr -> GoFieldAddrExpr(loc, getType(inst.goType), unpack(inst.struct), inst.field, inst.register).toAssignInst()
            is Instruction.Go -> unsupportedInstruction(function)
            is Instruction.If -> GoIfInst(loc, unpack(inst.condition) as GoConditionExpr, GoInstRef(inst.trueBranch), GoInstRef(inst.falseBranch))
            is Instruction.Index -> unpack(loc, inst)
            is Instruction.IndexAddr -> unpack(loc, inst)
            is Instruction.Jump -> GoJumpInst(loc, GoInstRef(inst.index))
            is Instruction.Lookup -> unpack(loc, inst)
            is Instruction.MakeChan -> unsupportedInstruction(function)
            is Instruction.MakeClosure -> unpack(loc, inst)
            is Instruction.MakeInterface -> GoMakeInterfaceExpr(loc, getType(inst.goType), unpack(inst.value), inst.register).toAssignInst()
            is Instruction.MakeMap -> GoMakeMapExpr(loc, getType(inst.goType), unpack(inst.reserve), inst.register).toAssignInst()
            is Instruction.MakeSlice -> unpack(loc, inst)
            is Instruction.MapUpdate -> GoMapUpdateInst(loc, unpack(inst.map), unpack(inst.key), unpack(inst.value))
            is Instruction.MultiConvert -> unsupportedInstruction(function)
            is Instruction.Next -> GoNextExpr(loc, getType(inst.goType), unpack(inst.iter), inst.register).toAssignInst()
            is Instruction.Panic -> GoPanicInst(loc, unpack(inst.value))
            is Instruction.Phi -> GoPhiExpr(loc, getType(inst.goType), inst.edges.map(this::unpack), inst.register).toAssignInst()
            is Instruction.Range -> GoRangeExpr(loc, getType(inst.goType), unpack(inst.collection), inst.register).toAssignInst()
            is Instruction.Return -> GoReturnInst(loc, inst.results.map(this::unpack))
            is Instruction.RunDefers -> GoRunDefersInst(loc)
            is Instruction.Select -> unsupportedInstruction(function)
            is Instruction.Send -> unsupportedInstruction(function)
            is Instruction.Slice -> unpack(loc, inst)
            is Instruction.SliceToArrayPointer -> unpack(loc, inst)
            is Instruction.Store -> GoStoreInst(loc, unpack(inst.addr), unpack(inst.value))
            is Instruction.TypeAssert -> unpack(loc, inst)
            is Instruction.UnOp -> unpack(loc, inst)
        }
    }

    private fun unpack(location: GoInstLocation, binOp: Instruction.BinOp): GoInst {
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
                unpack(binOp.first),
                unpack(binOp.second),
                binOp.register
            ).toAssignInst()
        }

        return GoNullInst(location.method)
    }

    private fun unpack(location: GoInstLocation, call: Instruction.Call): GoInst {
        return GoCallExpr(
            location,
            getType(call.goType),
            unpack(call.value),
            call.args.map(this::unpack),
            if (call.method == "") null else functionAlias(call.method),
            call.register
        ).toAssignInst()
    }

    private fun unpack(location: GoInstLocation, extract: Instruction.Extract): GoInst {
        return GoExtractExpr(location, getType(extract.goType), unpack(extract.tuple), extract.index, extract.register).toAssignInst()
    }

    private fun unpack(location: GoInstLocation, index: Instruction.Index): GoInst {
        return GoIndexExpr(
            location,
            getType(index.goType),
            unpack(index.collection),
            unpack(index.index),
            index.register
        ).toAssignInst()
    }

    private fun unpack(loc: GoInstLocation, indexAddr: Instruction.IndexAddr): GoInst {
        return GoIndexAddrExpr(
            loc,
            getType(indexAddr.goType),
            unpack(indexAddr.collection),
            unpack(indexAddr.index),
            indexAddr.register
        ).toAssignInst()
    }

    private fun unpack(location: GoInstLocation, lookup: Instruction.Lookup): GoInst {
        return GoLookupExpr(
            location,
            getType(lookup.goType),
            unpack(lookup.map),
            unpack(lookup.key),
            lookup.register,
            lookup.commaOk
        ).toAssignInst()
    }

    private fun unpack(location: GoInstLocation, makeClosure: Instruction.MakeClosure): GoInst {
        return GoMakeClosureExpr(
            location,
            OpaqueType(makeClosure.name),
            functionAlias(makeClosure.function.name),
            makeClosure.bindings.map(this::unpack),
            makeClosure.register
        ).toAssignInst()
    }

    private fun unpack(location: GoInstLocation, makeSlice: Instruction.MakeSlice): GoInst {
        return GoMakeSliceExpr(
            location,
            getType(makeSlice.goType),
            unpack(makeSlice.len),
            unpack(makeSlice.cap),
            makeSlice.register
        ).toAssignInst()
    }

    private fun unpack(location: GoInstLocation, slice: Instruction.Slice): GoInst {
        return GoSliceExpr(
            location,
            getType(slice.goType),
            unpack(slice.collection),
            unpack(slice.low),
            unpack(slice.high),
            unpack(slice.max),
            slice.register
        ).toAssignInst()
    }

    private fun unpack(location: GoInstLocation, sliceToArrayPointerExpr: Instruction.SliceToArrayPointer): GoInst {
        return GoSliceToArrayPointerExpr(
            location,
            getType(sliceToArrayPointerExpr.goType),
            unpack(sliceToArrayPointerExpr.value),
            sliceToArrayPointerExpr.register
        ).toAssignInst()
    }

    private fun unpack(value: Value): GoValue {
        return when (value) {
            is Value.Const -> unpack(value)
            is Value.FreeVar -> GoFreeVar(value.index, value.name, getType(value.goType))
            is Value.Global -> GoGlobal(value.index, value.name, getType(value.goType))
            is Value.Parameter -> GoParameter(value.index, value.name, getType(value.goType))
            is Value.Var -> GoVar(value.name, getType(value.goType))
            is Value.MakeClosure -> GoVar(value.name, getType(value.goType))
            is Value.Function -> functionAlias(value.name)
            is Value.Builtin -> GoBuiltin(value.name, getType(value.goType))
        }
    }

    private fun unpack(location: GoInstLocation, typeAssert: Instruction.TypeAssert): GoInst {
        return GoTypeAssertExpr(
            location,
            getType(typeAssert.goType),
            unpack(typeAssert.value),
            getType(typeAssert.assertedType),
            typeAssert.register
        ).toAssignInst()
    }

    private fun unpack(location: GoInstLocation, unOp: Instruction.UnOp): GoInst {
        val unaryExprConstructors = mapOf<String, Function4<GoInstLocation, GoType, GoValue, String, GoUnaryExpr>>(
            "*" to ::GoUnMulExpr,
            "!" to ::GoUnNotExpr,
            "-" to ::GoUnSubExpr,
            "^" to ::GoUnXorExpr,
        )

        val type = getType(unOp.goType)
        val arg = unpack(unOp.argument)
        val name = unOp.register

        return when {
            unaryExprConstructors.containsKey(unOp.op) -> unaryExprConstructors[unOp.op]!!(location, type, arg, name).toAssignInst()
            unOp.op == "<-" -> GoUnArrowExpr(location, type, arg, unOp.commaOk, name).toAssignInst()
            else -> GoNullInst(location.method)
        }
    }

    private fun unpack(value: Value.Const): GoValue {
        val type = getType(value.value.type)
        if (type is NamedType) {
            return unpack(value, type.underlyingType, type)
        }
        return unpack(value, type, type)
    }

    private fun unpack(value: Value.Const, basicType: GoType, type: GoType): GoValue {
        val const = value.value
        val string = const.value

        return when (basicType) {
            GoBasicTypes.BOOL -> GoBool(string.toBooleanStrict(), type)
            GoBasicTypes.INT -> GoInt(string.toLong(), type)
            GoBasicTypes.INT8 -> GoInt8(string.toByte(), type)
            GoBasicTypes.INT16 -> GoInt16(string.toShort(), type)
            GoBasicTypes.INT32 -> GoInt32(string.toInt(), type)
            GoBasicTypes.INT64 -> GoInt64(string.toLong(), type)
            GoBasicTypes.UINT -> GoUInt(string.toULong(), type)
            GoBasicTypes.UINT8 -> GoUInt8(string.toUByte(), type)
            GoBasicTypes.UINT16 -> GoUInt16(string.toUShort(), type)
            GoBasicTypes.UINT32 -> GoUInt32(string.toUInt(), type)
            GoBasicTypes.UINT64, GoBasicTypes.UINTPTR -> GoUInt64(string.toULong(), type)
            GoBasicTypes.FLOAT32 -> GoFloat32(string.toFloat(), type)
            GoBasicTypes.FLOAT64 -> GoFloat64(string.toDouble(), type)
            GoBasicTypes.RUNE -> GoInt32(string.toInt(), type)
            GoBasicTypes.STRING -> GoStringConstant(unquote(string), type)
            else -> GoNullConstant(basicType)
        }
    }

    private fun unpack(typesMap: Map<String, Type>, types: Collection<String>): List<GoType> {
        return types.map { unpack(typesMap, it) }
    }

    private fun unpack(types: Map<String, Type>, type: Type): GoType = internType(when (type) {
        is Type.Alias -> unpack(types, type.from)
        is Type.Array -> ArrayType(type.len, unpack(types, type.elem))
        is Type.Basic -> unpack(type.name)
        is Type.Chan -> ChanType(type.dir.toLong(), unpack(types, type.elem))
        is Type.Interface -> InterfaceType(type.methods, type.name)
        is Type.Map -> MapType(unpack(types, type.key), unpack(types, type.elem))
        is Type.Named -> {
            val named = NamedType(NullType(), type.name, type.methods)
            typesMap[type.name] = named
            named.underlyingType = unpack(types, type.underlying)
            named
        }

        is Type.Opaque -> OpaqueType(type.name)
        is Type.Pointer -> PointerType(unpack(types, type.elem))
        is Type.Signature -> SignatureType(TupleType(unpack(types, type.params)), TupleType(unpack(types, type.results)))
        is Type.Slice -> SliceType(unpack(types, type.elem))
        is Type.Struct -> StructType(unpack(types, type.fields), null)
        is Type.Tuple -> TupleType(unpack(types, type.elems))
        is Type.TypeParam -> TypeParam(type.name)
        is Type.Union -> UnionType(emptyList()) // TODO(buraindo) proper type list when generics are supported
    })

    private fun unpack(types: Map<String, Type>, type: String): GoType {
        if (type in typesMap) {
            return typesMap[type]!!
        }
        return unpack(types, types[type]!!)
    }

    private fun unpack(name: String) = when (name) {
        GoTypes.BOOL -> GoBasicTypes.BOOL
        GoTypes.INT -> GoBasicTypes.INT
        GoTypes.INT8 -> GoBasicTypes.INT8
        GoTypes.INT16 -> GoBasicTypes.INT16
        GoTypes.INT32 -> GoBasicTypes.INT32
        GoTypes.INT64 -> GoBasicTypes.INT64
        GoTypes.UINT -> GoBasicTypes.UINT
        GoTypes.UINT8 -> GoBasicTypes.UINT8
        GoTypes.UINT16 -> GoBasicTypes.UINT16
        GoTypes.UINT32 -> GoBasicTypes.UINT32
        GoTypes.UINT64 -> GoBasicTypes.UINT64
        GoTypes.FLOAT32 -> GoBasicTypes.FLOAT32
        GoTypes.FLOAT64 -> GoBasicTypes.FLOAT64
        GoTypes.STRING -> GoBasicTypes.STRING
        GoTypes.BYTE -> GoBasicTypes.UINT8
        GoTypes.RUNE -> GoBasicTypes.RUNE
        GoTypes.UINTPTR -> GoBasicTypes.UINTPTR
        GoTypes.UNTYPED_BOOL -> GoBasicTypes.BOOL
        GoTypes.UNTYPED_INT -> GoBasicTypes.INT
        GoTypes.UNTYPED_RUNE -> GoBasicTypes.RUNE
        GoTypes.UNTYPED_FLOAT -> GoBasicTypes.FLOAT64
        GoTypes.UNTYPED_STRING -> GoBasicTypes.STRING
        GoTypes.UNSAFE_POINTER -> GoBasicTypes.UNSAFE_POINTER
        else -> BasicType("unknown")
    }

    private fun getType(type: String): GoType = typesMap[type]!!

    private fun functionAlias(name: String): GoFunction {
        return GoFunction(OpaqueType(name), emptyList(), name, emptyList(), "", emptyList(), emptyList())
    }

    private fun unsupportedInstruction(parent: GoMethod): GoInst {
        return GoNullInst(parent)
    }

    private val internTypeMap = mutableMapOf<GoType, GoType>()

    private fun internType(type: GoType): GoType {
        return internTypeMap.computeIfAbsent(type) { type }
    }

    private fun unquote(s: String): String {
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length - 1)
        }

        return s
    }
}