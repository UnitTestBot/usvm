package ssa_helpers

import (
	"fmt"

	"GoToKotlin/constants"
)

const jacoImport = `import org.jacodb.go.api.*
`

const structDefinitionWithInterface = `class %s : %s {

`

const ssaToJacoExpr = `import org.jacodb.go.api.GoExpr
import org.jacodb.go.api.GoMethod

interface ssaToJacoExpr {
    fun createJacoDBExpr(parent: GoMethod): GoExpr
}
`

const ssaToJacoInst = `import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoMethod

interface ssaToJacoInst {
    fun createJacoDBInst(parent: GoMethod): GoInst
}
`

const ssaToJacoValue = `import org.jacodb.go.api.GoValue
import org.jacodb.go.api.GoMethod

interface ssaToJacoValue {
    fun createJacoDBValue(parent: GoMethod): GoValue
}
`

const ssaToJacoType = `import org.jacodb.go.api.GoType

interface ssaToJacoType {
    fun createJacoDBType(): GoType
}
`

const ssaToJacoProject = `import org.jacodb.go.api.GoProject

interface ssaToJacoProject {
    fun createJacoDBProject(): GoProject
}
`

const ssaToJacoMethod = `import org.jacodb.go.api.*

interface ssaToJacoMethod {
    fun createJacoDBMethod(fileSet: FileSet): GoMethod
}
`

const createValueFunc = `override fun createJacoDBValue(parent: GoMethod): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as %s
        }
        return createJacoDBExpr(parent)
    }
`

const checkUsed = `if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as %s
        }
`

const markUsed = `if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }
        return res`

var ssaCallExpr = fmt.Sprintf(`import org.jacodb.go.api.*

class ssa_CallExpr(init: ssa_Call, val callee: GoMethod? = null) : ssaToJacoExpr, ssaToJacoValue {
    val type = (init.register!!.typ!! as ssaToJacoType).createJacoDBType()
    val value = (init.Call!!.Value!! as ssaToJacoValue).createJacoDBValue(callee!!)
    val operands = init.Call!!.Args!!.map { (it as ssaToJacoValue).createJacoDBValue(callee!!) }.map { i ->
        if (i is GoAssignableInst) {
            GoFreeVar(
                i.location.index,
                i.name,
                i.type
            )
        } else {
            i
        }
    }
    val name = "t${init.register!!.num!!}"
    val location = GoInstLocationImpl(
        init.register!!.anInstruction!!.block!!.Index!!.toInt(),
        init.Call!!.pos!!.toInt(),
        callee!!,
    )

    override fun createJacoDBExpr(parent: GoMethod): GoCallExpr {
		%s
        val res = GoCallExpr(
            location,
            type,
            value,
            operands,
            callee,
            name,
        )
		%s
    }
	%s
}
`, fmt.Sprintf(checkUsed, "GoCallExpr"), markUsed, fmt.Sprintf(createValueFunc, "GoCallExpr"))

var functionExtra = fmt.Sprintf(`
	override fun createJacoDBMethod(fileSet: FileSet): GoFunction {
		%s

        val returns = mutableListOf<GoType>()

        if (Signature!!.results!!.vars != null) {
            for (ret in Signature!!.results!!.vars!!) {
                returns.add((ret.Object!!.typ!! as ssaToJacoType).createJacoDBType())
            }
        }

        val res =
            GoFunction(
                Signature!!.createJacoDBType(),
                listOf(),
                name!!,
                listOf(),
                returns, //TODO
                Pkg?.Pkg?.name ?: "null",
				fileSet,
            )

		if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }

        res.operands = Params!!.map { it.createJacoDBExpr(res) } // TODO
        res.blocks = Blocks!!.map { it.createJacoDBBasicBlock(res) }
        res.blocks.forEach { b ->
            b.insts = b.insts.map { i ->
                if (i is GoAssignableInst) {
                    i.toAssignInst()
                } else {
                    i
                }
            }
        }

		return res
    }
	
	override fun createJacoDBValue(parent: GoMethod): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoFunction
        }
        return createJacoDBMethod(parent.fileSet)
    }

	override fun createJacoDBExpr(parent: GoMethod): GoExpr {
        return createJacoDBValue(parent)
    }
`, fmt.Sprintf(checkUsed, "GoFunction"))

var programExtra = fmt.Sprintf(`
	override fun createJacoDBProject(): GoProject {
		%s

		val fSet = mutableListOf<File>()
        for (tokenFile in Fset!!.files!!) {
            fSet.add(File(
                tokenFile.name!!,
                tokenFile.base!!.toInt(),
                tokenFile.size!!.toInt(),
                tokenFile.lines!!.map { it.toInt() },
            ))
        }
        val fileSet = FileSet(fSet)

        val methods = mutableListOf<GoMethod>()
        for (pkg in packages!!) {
            for (member in pkg.value.Members!!) {
                if (member.value is ssa_Function) {
                    methods.add((member.value as ssa_Function).createJacoDBMethod(fileSet))
                }
            }
        }

        val res = GoProject(
            methods.toList(),
			fileSet,
        )
		%s
    }
`, fmt.Sprintf(checkUsed, "GoProject"), markUsed)

var basicBlockExtra = fmt.Sprintf(`
	fun createJacoDBBasicBlock(method: GoMethod): GoBasicBlock {
		%s

        val inst = mutableListOf<GoInst>()

        for (value in Instrs!!) {
            if (value is ssaToJacoInst) {
                inst.add(value.createJacoDBInst(method))
            } else {
                if (value is ssaToJacoExpr) {
                    val expr = value.createJacoDBExpr(method)
                    if (expr is GoAssignableInst) {
                        inst.add(expr.toAssignInst())
                    }
                }
            }
        }

        val res = GoBasicBlock(
            Index!!.toInt(),
            Succs!!.map { it.Index!!.toInt() },
            Preds!!.map { it.Index!!.toInt() },
            inst
        )
		%s
    }
`, fmt.Sprintf(checkUsed, "GoBasicBlock"), markUsed)

var jumpExtra = fmt.Sprintf(`
	override fun createJacoDBInst(parent: GoMethod): GoJumpInst {
		%s

        val res = GoJumpInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                0,
                parent,
            ),
            GoInstRef(
                anInstruction!!.block!!.Succs!![0].Index!!.toInt()
            )
        )
		%s
    }
`, fmt.Sprintf(checkUsed, "GoJumpInst"), markUsed)

var ifExtra = fmt.Sprintf(`
	override fun createJacoDBInst(parent: GoMethod): GoIfInst {
		%s

        var cond: GoConditionExpr

        val trueConst = ssa_Const()
        trueConst.Value = true
        val type = types_Basic()
        type.kind = 1
        type.info = 1
        type.name = "bool"
        trueConst.typ = type

        if (Cond!! is ssa_BinOp) {
            val parsed = (Cond!! as ssa_BinOp).createJacoDBExpr(parent)
            if (parsed is GoConditionExpr) {
                cond = parsed
            } else {
                cond = GoEqlExpr(
                    lhv = trueConst.createJacoDBExpr(parent),
                    rhv = parsed as GoValue,
                    type = (type as ssaToJacoType).createJacoDBType(),
					name = "<if statement>",
                    location = GoInstLocationImpl(
                        -1, 0, parent
                    ),
                )
            }
        } else {
            cond = GoEqlExpr(
                lhv = trueConst.createJacoDBExpr(parent),
                rhv = (Cond!! as ssaToJacoValue).createJacoDBValue(parent),
                type = (type as ssaToJacoType).createJacoDBType(),
				name = "<if statement>",
                location = GoInstLocationImpl(
                    -1, 0, parent
                ),
            )
        }

        val res = GoIfInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                0,
                parent,
            ),
            cond,
            GoInstRef(
                anInstruction!!.block!!.Succs!![0].Index!!.toInt()
            ),
            GoInstRef(
                anInstruction!!.block!!.Succs!![1].Index!!.toInt()
            ),
        )
		%s
    }
`, fmt.Sprintf(checkUsed, "GoIfInst"), markUsed)

const returnExtra = `
	override fun createJacoDBInst(parent: GoMethod): GoReturnInst {
        return GoReturnInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                pos!!.toInt(),
                parent,
            ),
            Results!!.map { (it as ssaToJacoValue).createJacoDBValue(parent) },
        )
    }
`

const runDefersExtra = `
	override fun createJacoDBInst(parent: GoMethod): GoRunDefersInst {
        return GoRunDefersInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                0,
                parent,
            ),
        )
    }
`

const panicExtra = `
	override fun createJacoDBInst(parent: GoMethod): GoPanicInst {
        return GoPanicInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                pos!!.toInt(),
                parent,
            ),
			(X!! as ssaToJacoValue).createJacoDBValue(parent),
        )
    }
`

const goExtra = `
	override fun createJacoDBInst(parent: GoMethod): GoGoInst {
        return GoGoInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                pos!!.toInt(),
                parent,
            ),
			(Call!!.Value!! as ssaToJacoValue).createJacoDBValue(parent),
            Call!!.Args!!.map { (it as ssaToJacoValue).createJacoDBValue(parent) }
        )
    }
`

const deferExtra = `
	override fun createJacoDBInst(parent: GoMethod): GoDeferInst {
        return GoDeferInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                pos!!.toInt(),
                parent,
            ),
			(Call!!.Value!! as ssaToJacoValue).createJacoDBValue(parent),
            Call!!.Args!!.map { (it as ssaToJacoValue).createJacoDBValue(parent) }
        )
    }
`

const sendExtra = `
	override fun createJacoDBInst(parent: GoMethod): GoSendInst {
        return GoSendInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                pos!!.toInt(),
                parent,
            ),
			(Chan!! as ssaToJacoValue).createJacoDBValue(parent),
			(X!! as ssaToJacoExpr).createJacoDBExpr(parent),
        )
    }
`

const storeExtra = `
	override fun createJacoDBInst(parent: GoMethod): GoStoreInst {
        return GoStoreInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                pos!!.toInt(),
                parent,
            ),
            (Addr!! as ssaToJacoValue).createJacoDBValue(parent),
            (Val!! as ssaToJacoValue).createJacoDBValue(parent)
        )
    }
`

const mapUpdateExtra = `
	override fun createJacoDBInst(parent: GoMethod): GoMapUpdateInst {
        return GoMapUpdateInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                pos!!.toInt(),
                parent,
            ),
			(Map!! as ssaToJacoValue).createJacoDBValue(parent),
			(Key!! as ssaToJacoExpr).createJacoDBExpr(parent),
			(Value!! as ssaToJacoExpr).createJacoDBExpr(parent),
        )
    }
`

const debugRefExtra = `
	override fun createJacoDBInst(parent: GoMethod): GoDebugRefInst {
        return GoDebugRefInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                pos!!.toInt(),
                parent,
            ),
        )
    }
`

var callExtra = fmt.Sprintf(`
	var CallExpr: GoCallExpr? = null
	override fun createJacoDBInst(parent: GoMethod): GoCallInst {
        if (CallExpr == null) {
            val callee: GoMethod = if (Call!!.Value!! is ssaToJacoMethod) {
                (Call!!.Value!! as ssaToJacoMethod).createJacoDBMethod(parent.fileSet)
            } else if (Call!!.Value!! is ssaToJacoExpr) {
                val value = (Call!!.Value!! as ssaToJacoExpr).createJacoDBExpr(parent)
                if (value is GoMakeClosureExpr) {
                    value.func
                } else {
                    parent
                }
            } else {
                parent
            }
            CallExpr = ssa_CallExpr(this, callee).createJacoDBExpr(parent)
        }
        return GoCallInst(
            GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                Call!!.pos!!.toInt(),
                parent,
            ),
            CallExpr!!,
			"t${register!!.num!!}",
            (register!!.typ!! as ssaToJacoType).createJacoDBType()
        )
    }
	
	override fun createJacoDBValue(parent: GoMethod): GoValue {
        if (CallExpr == null) {
            CallExpr = ssa_CallExpr(this, parent).createJacoDBExpr(parent)
        }
        return CallExpr!!
    }

	override fun createJacoDBExpr(parent: GoMethod): GoExpr {
        return createJacoDBValue(parent)
    }
`)

var freeVarExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoFreeVar {
        return GoFreeVar(
            pos!!.toInt(),
            name!!,
            (typ!! as ssaToJacoType).createJacoDBType()
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoFreeVar"))

// TODO() ??? parent is an existing field
var parameterExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent_: GoMethod): GoParameter {
        var index = -1
        for ((i, par) in parent!!.Params!!.withIndex()) {
            if (par.name!! == name!!) {
                index = i
                break
            }
        }
        return GoParameter(
            index,
            name!!,
            (typ!! as ssaToJacoType).createJacoDBType()
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoParameter"))

var constExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoConst {
        val innerVal = Value
        val name: String
		val type = (typ!! as ssaToJacoType).createJacoDBType()

        when (innerVal) {
            is Long -> {
                name = GoLong(
                    innerVal,
                    type
                ).toString()
            }
            is Boolean -> {
                name = GoBool(
                    innerVal,
                    type
                ).toString()
            }
            is Double -> {
                name = GoDouble(
                    innerVal,
                    type
                ).toString()
            }
            is String -> {
                name = GoStringConstant(
                    innerVal,
                    type
                ).toString()
            }
            is constant_intVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    type
                ).toString()
            }
            is constant_stringVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    type
                ).toString()
            }
            is constant_ratVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    type
                ).toString()
            }
			is constant_floatVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    type
                ).toString()
            }
			is constant_complexVal -> {
                name = GoStringConstant(
                    innerVal.toString(),
                    type
                ).toString()
            }
            else -> {
                name = GoNullConstant().toString()
            }
        }

        return GoConst(
            0,
            name,
            type
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoConst"))

const intValStub = constants.PackageLine + `class constant_intVal {}
`

const stringValStub = constants.PackageLine + `class constant_stringVal {}
`

const ratValStub = constants.PackageLine + `class constant_ratVal {}
`

const floatValStub = constants.PackageLine + `class constant_floatVal {}
`

const complexValStub = constants.PackageLine + `class constant_complexVal {}
`

const intValExtra = `
	override fun toString(): String {
        var num = Val!!.abs!!.joinToString { it.toString() }
        if (Val!!.neg!!) {
            num = "-$num"
        }
        return num
    }
`

const stringValExtra = `
    override fun toString(): String {
        var str = ""
        if (s != null) {
            str += s!!
        }
        if (l != null) {
            str += l!!.toString()
            str += r!!.toString()
        }
        return str
    }
`

const ratValExtra = `
    override fun toString(): String {
        return "${Val!!.a}/${Val!!.b}"
    }
`

const floatValExtra = `
    override fun toString(): String {
		var str = "2^${Val!!.exp!!}"
		val temp = ""
		for (w in Val!!.mant!!) {
			temp += w.toString()
		}
		str = "$temp * $str"
		if (Val!!.neg!!) {
			str = "-$str"
		}
        return str
    }
`

const complexValExtra = `
    override fun toString(): String {
        return "(${re!!} + ${im!!}i)"
    }
`

var globalExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoGlobal {
        return GoGlobal(
            pos!!.toInt(),
            name!!,
            (typ!! as ssaToJacoType).createJacoDBType()
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoGlobal"))

var builtinExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoBuiltin {
        return GoBuiltin(
            0,
            name!!,
            sig!!.createJacoDBType()
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoBuiltin"))

const arrayExtra = `
	override fun createJacoDBType(): GoType {
        return ArrayType(
            len!!,
            (elem!! as ssaToJacoType).createJacoDBType()
        )
    }
`

const basicExtra = `
	override fun createJacoDBType(): GoType {
        return BasicType(name!!)
    }
`

const chanExtra = `
	override fun createJacoDBType(): GoType {
        return ChanType(
            dir!!,
            (elem!! as ssaToJacoType).createJacoDBType()
        )
    }
`

const interfaceExtra = `
	override fun createJacoDBType(): GoType {
        return InterfaceType()
    }
`

const mapExtra = `
	override fun createJacoDBType(): GoType {
        return MapType(
            (key!! as ssaToJacoType).createJacoDBType(),
            (elem!! as ssaToJacoType).createJacoDBType()
        )
    }
`

const namedExtra = `
	override fun createJacoDBType(): GoType {
        if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoType
        }

        val res = NamedType(
            InterfaceType(),
            obj!!.Object!!.name!!,
        )

        if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }
        if (underlying != null) {
            res.underlyingType = (underlying!! as ssaToJacoType).createJacoDBType()
        } else {
            res.underlyingType = NullType()
        }

        return res
    }
`

const pointerExtra = `
	override fun createJacoDBType(): GoType {
        if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoType
        }

        val res = PointerType(
            InterfaceType()
        )

        if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }
        res.baseType = (base!! as ssaToJacoType).createJacoDBType()

        return res
    }
`

const signatureExtra = `
	override fun createJacoDBType(): GoType {
        return SignatureType(
            params!!.createJacoDBType() as TupleType,
            results!!.createJacoDBType() as TupleType
        )
    }
`

const sliceTypeExtra = `
	override fun createJacoDBType(): GoType {
        return SliceType(
            (elem!! as ssaToJacoType).createJacoDBType()
        )
    }
`

const structExtra = `
	override fun createJacoDBType(): GoType {
        return StructType(
            fields!!.map { (it.Object!!.typ!! as ssaToJacoType).createJacoDBType() },
            tags
        )
    }
`

const tupleExtra = `
	override fun createJacoDBType(): GoType {
        return TupleType(
            vars?.map { it.Object!!.name ?: "" } ?: listOf()
        )
    }
`

const typeParamExtra = `
	override fun createJacoDBType(): GoType {
        return TypeParam(obj!!.Object!!.name!!)
    }
`

const unionExtra = `
	override fun createJacoDBType(): GoType {
        return UnionType(
            terms!!.map { (it.typ!! as ssaToJacoType).createJacoDBType() }
        )
    }
`

const opaqueTypeExtra = `
	override fun createJacoDBType(): GoType {
        return OpaqueType(name!!)
    }
`

var allocExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoAllocExpr {
        return GoAllocExpr(
            GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
            (register!!.typ!! as ssaToJacoType).createJacoDBType(),
            "t${register!!.num!!.toInt()}"
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoAllocExpr"))

var phiExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoPhiExpr {
		%s

        val res = GoPhiExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
            (register!!.typ!! as ssaToJacoType).createJacoDBType(),
			listOf(),
            "t${register!!.num!!.toInt()}"
        )
        if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }
        
        res.edges = Edges!!.map { (it as ssaToJacoValue).createJacoDBValue(parent) }
		return res
    }
	%s
`, fmt.Sprintf(checkUsed, "GoPhiExpr"), fmt.Sprintf(createValueFunc, "GoPhiExpr"))

const binOpExtra = `
	override fun createJacoDBExpr(parent: GoMethod): GoBinaryExpr {
        val type = (register!!.typ!! as ssaToJacoType).createJacoDBType()
		val name = "t${register!!.num!!.toInt()}"
        val location = GoInstLocationImpl(
            register!!.anInstruction!!.block!!.Index!!.toInt(),
            register!!.pos!!.toInt(),
            parent,
        )

        when (Op!!) {
            12L -> return GoAddExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            13L -> return GoSubExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            14L -> return GoMulExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            15L -> return GoDivExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            16L -> return GoModExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            17L -> return GoAndExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            18L -> return GoOrExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            19L -> return GoXorExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            20L -> return GoShlExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            21L -> return GoShrExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            22L -> return GoAndNotExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            39L -> return GoEqlExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            44L -> return GoNeqExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            40L -> return GoLssExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            45L -> return GoLeqExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            41L -> return GoGtrExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            46L -> return GoGeqExpr(
                lhv = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                rhv = (Y!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
				name = name,
                location = location,
            )
            else -> error("unexpected BinOp ${Op!!}")
        }
    }

	override fun createJacoDBValue(parent: GoMethod): GoValue {
        return createJacoDBExpr(parent)
    }
`

var unOpExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoUnaryExpr {
        val type = (register!!.typ!! as ssaToJacoType).createJacoDBType()
		val name = "t${register!!.num!!.toInt()}"
        val location = GoInstLocationImpl(
            register!!.anInstruction!!.block!!.Index!!.toInt(),
            register!!.pos!!.toInt(),
            parent,
        )

        when (Op!!) {
            43L -> return GoUnNotExpr(
                value = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            13L -> return GoUnSubExpr(
                value = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            36L -> return GoUnArrowExpr(
                value = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                commaOk = CommaOk ?: false,
                name = name,
                location = location,
            )
            14L -> return GoUnMulExpr(
                value = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            19L -> return GoUnXorExpr(
                value = (X!! as ssaToJacoValue).createJacoDBValue(parent),
                type = type,
                name = name,
                location = location,
            )
            else -> error("unexpected UnOp ${Op!!}")
        }
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoUnaryExpr"))

var changeTypeExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoChangeTypeExpr {
        return GoChangeTypeExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
            (register!!.typ!! as ssaToJacoType).createJacoDBType(),
			(X!! as ssaToJacoValue).createJacoDBValue(parent),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoChangeTypeExpr"))

var convertExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoConvertExpr {
        return GoConvertExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
            (register!!.typ!! as ssaToJacoType).createJacoDBType(),
			(X!! as ssaToJacoValue).createJacoDBValue(parent),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoConvertExpr"))

var multiConvertExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoMultiConvertExpr {
        return GoMultiConvertExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
            (register!!.typ!! as ssaToJacoType).createJacoDBType(),
			(X!! as ssaToJacoValue).createJacoDBValue(parent),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoMultiConvertExpr"))

var changeInterfaceExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoChangeInterfaceExpr {
        return GoChangeInterfaceExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
            (register!!.typ!! as ssaToJacoType).createJacoDBType(),
			(X!! as ssaToJacoValue).createJacoDBValue(parent),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoChangeInterfaceExpr"))

var sliceToArrayPointerExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoSliceToArrayPointerExpr {
        return GoSliceToArrayPointerExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
            (register!!.typ!! as ssaToJacoType).createJacoDBType(),
			(X!! as ssaToJacoValue).createJacoDBValue(parent),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoSliceToArrayPointerExpr"))

var makeInterfaceExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoMakeInterfaceExpr {
        return GoMakeInterfaceExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
            (register!!.typ!! as ssaToJacoType).createJacoDBType(),
			(X!! as ssaToJacoValue).createJacoDBValue(parent),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoMakeInterfaceExpr"))

var makeClosureExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoMakeClosureExpr {
        return GoMakeClosureExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.num!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (Fn!! as ssa_Function).createJacoDBMethod(parent.fileSet),
			Bindings!!.map { (it as ssaToJacoValue).createJacoDBValue(parent) },
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoMakeClosureExpr"))

var makeMapExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoMakeMapExpr {
		val reserve = if (Reserve == null) {
            GoLong(0, LongType())
        } else {
            (Reserve!! as ssaToJacoValue).createJacoDBValue(parent)
        }

        return GoMakeMapExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            reserve,
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoMakeMapExpr"))

var makeChanExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoMakeChanExpr {
        return GoMakeChanExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (Size!! as ssaToJacoValue).createJacoDBValue(parent),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoMakeChanExpr"))

var makeSliceExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoMakeSliceExpr {
        return GoMakeSliceExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (Len!! as ssaToJacoValue).createJacoDBValue(parent),
			(Cap!! as ssaToJacoValue).createJacoDBValue(parent),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoMakeSliceExpr"))

var sliceExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoSliceExpr {
		%s

		val low: GoValue = if (Low == null) {
            GoNullConstant()
        } else {
            (Low!! as ssaToJacoValue).createJacoDBValue(parent)
        }
        val high: GoValue = if (High == null) {
            GoNullConstant()
        } else {
            (High!! as ssaToJacoValue).createJacoDBValue(parent)
        }
        val max: GoValue = if (Max == null) {
            GoNullConstant()
        } else {
            (Max!! as ssaToJacoValue).createJacoDBValue(parent)
        }

		val res = GoSliceExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (X!! as ssaToJacoValue).createJacoDBValue(parent),
			low,
			high,
			max,
			"t${register!!.num!!.toInt()}",
        )
		%s
    }
	%s
`, fmt.Sprintf(checkUsed, "GoSliceExpr"), markUsed, fmt.Sprintf(createValueFunc, "GoSliceExpr"))

var fieldAddrExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoFieldAddrExpr {
        return GoFieldAddrExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (X!! as ssaToJacoValue).createJacoDBValue(parent),
			Field!!.toInt(),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoFieldAddrExpr"))

var fieldExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoFieldExpr {
        return GoFieldExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (X!! as ssaToJacoValue).createJacoDBValue(parent),
			Field!!.toInt(),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoFieldExpr"))

var indexAddrExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoIndexAddrExpr {
        return GoIndexAddrExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (X!! as ssaToJacoValue).createJacoDBValue(parent),
			(Index!! as ssaToJacoValue).createJacoDBValue(parent),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoIndexAddrExpr"))

var indexExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoIndexExpr {
        return GoIndexExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (X!! as ssaToJacoValue).createJacoDBValue(parent),
			(Index!! as ssaToJacoValue).createJacoDBValue(parent),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoIndexExpr"))

var lookupExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoLookupExpr {
        return GoLookupExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (X!! as ssaToJacoValue).createJacoDBValue(parent),
			(Index!! as ssaToJacoValue).createJacoDBValue(parent),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoLookupExpr"))

var selectExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoSelectExpr {
        val chan = mutableListOf<GoValue>()
        val send = mutableListOf<GoValue>()
        if (States != null) {
            States!!.map {
                chan.add((it.Chan!! as ssaToJacoValue).createJacoDBValue(parent))
                send.add(
                    if (it.Send == null) {
                        GoNullConstant()
                    } else {
                        (it.Send!! as ssaToJacoValue).createJacoDBValue(parent)
                    }
                )
            }
        }

        return GoSelectExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            chan,
			send,
			Blocking!!,
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoSelectExpr"))

var rangeExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoRangeExpr {
        return GoRangeExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (X!! as ssaToJacoValue).createJacoDBValue(parent),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoRangeExpr"))

var nextExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoNextExpr {
        return GoNextExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (Iter!! as ssaToJacoValue).createJacoDBValue(parent),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoNextExpr"))

var typeAssertExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoTypeAssertExpr {
        return GoTypeAssertExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (X!! as ssaToJacoValue).createJacoDBValue(parent),
			(AssertedType!! as ssaToJacoType).createJacoDBType(),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoTypeAssertExpr"))

var extractExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(parent: GoMethod): GoExtractExpr {
        return GoExtractExpr(
			GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
			(register!!.typ!! as ssaToJacoType).createJacoDBType(),
            (Tuple!! as ssaToJacoValue).createJacoDBValue(parent),
			Index!!.toInt(),
			"t${register!!.num!!.toInt()}",
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoExtractExpr"))

/*var callCommonExtra = fmt.Sprintf(`
	override fun createJacoDBExpr(): GoCallExpr {
        return GoCallExpr(
            (Method!!.Object!!.typ!! as ssaToJacoType).createJacoDBType(),
            (Value!! as ssaToJacoValue).createJacoDBValue(),
            Args!!.map { (it as ssaToJacoValue).createJacoDBValue() }
        )
    }
	%s
`, fmt.Sprintf(createValueFunc, "GoCallExpr"))*/
