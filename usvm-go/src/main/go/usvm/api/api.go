package api

import (
	"go/constant"
	"go/token"
	"go/types"
	"strconv"

	"golang.org/x/tools/go/ssa"

	"usvm/graph"
	"usvm/sort"
	"usvm/util"
)

type Api interface {
	MkUnOp(inst *ssa.UnOp)
	MkBinOp(inst *ssa.BinOp)
	MkCall(inst *ssa.Call)
	MkCallBuiltin(inst *ssa.Call, name string)
	MkChangeInterface(inst *ssa.ChangeInterface)
	MkChangeType(inst *ssa.ChangeType)
	MkConvert(inst *ssa.Convert)
	MkSliceToArrayPointer(inst *ssa.SliceToArrayPointer)
	MkMakeInterface(inst *ssa.MakeInterface)
	MkStore(inst *ssa.Store)
	MkIf(inst *ssa.If)
	MkJump(inst *ssa.Jump)
	MkDefer(inst *ssa.Defer)
	MkGo(inst *ssa.Go)
	MkMakeChan(inst *ssa.MakeChan)
	MkAlloc(inst *ssa.Alloc)
	MkMakeSlice(inst *ssa.MakeSlice)
	MkMakeMap(inst *ssa.MakeMap)
	MkExtract(inst *ssa.Extract)
	MkSlice(inst *ssa.Slice)
	MkReturn(inst *ssa.Return)
	MkRunDefers(inst *ssa.RunDefers)
	MkPanic(inst *ssa.Panic)
	MkRange(inst *ssa.Range)
	MkNext(inst *ssa.Next)
	MkPointerFieldReading(inst *ssa.FieldAddr)
	MkFieldReading(inst *ssa.Field)
	MkPointerArrayReading(inst *ssa.IndexAddr)
	MkArrayReading(inst *ssa.Index)
	MkMapLookup(inst *ssa.Lookup)
	MkMapUpdate(inst *ssa.MapUpdate)
	MkTypeAssert(inst *ssa.TypeAssert)
	MkMakeClosure(inst *ssa.MakeClosure)
	MkPhi(inst *ssa.Phi)
	MkSelect(inst *ssa.Select)

	SetLastBlock(block int)
	WriteLastBlock()

	Log(values ...any)
}

func SetProgram(program *ssa.Program) {
	apiInstance.program = program
}

func NewApi(lastBlock int, buf *util.ByteBuffer) Api {
	apiInstance.lastBlock = lastBlock
	apiInstance.buf = buf
	return apiInstance
}

var apiInstance = &api{}

type Method byte

const (
	_ Method = iota
	MethodMkUnOp
	MethodMkBinOp
	MethodMkCall
	MethodMkCallBuiltin
	MethodMkChangeInterface
	MethodMkChangeType
	MethodMkConvert
	MethodMkSliceToArrayPointer
	MethodMkMakeInterface
	MethodMkStore
	MethodMkIf
	MethodMkJump
	MethodMkDefer
	MethodMkGo
	MethodMkMakeChan
	MethodMkAlloc
	MethodMkMakeSlice
	MethodMkMakeMap
	MethodMkExtract
	MethodMkSlice
	MethodMkReturn
	MethodMkRunDefers
	MethodMkPanic
	MethodMkVariable
	MethodMkRange
	MethodMkNext
	MethodMkPointerFieldReading
	MethodMkFieldReading
	MethodMkPointerArrayReading
	MethodMkArrayReading
	MethodMkMapLookup
	MethodMkMapUpdate
	MethodMkTypeAssert
	MethodMkMakeClosure
	MethodMkSelect
)

type UnOp byte

const (
	_ UnOp = iota
	UnOpRecv
	UnOpNeg
	UnOpDeref
	UnOpNot
	UnOpInv
)

var unOpMapping = []UnOp{
	token.ARROW: UnOpRecv,
	token.SUB:   UnOpNeg,
	token.MUL:   UnOpDeref,
	token.NOT:   UnOpNot,
	token.XOR:   UnOpInv,
}

type BinOp byte

const (
	_ BinOp = iota
	BinOpAdd
	BinOpSub
	BinOpMul
	BinOpDiv
	BinOpRem
	BinOpAnd
	BinOpOr
	BinOpXor
	BinOpShl
	BinOpShr
	BinOpAndNot
	BinOpEq
	BinOpLt
	BinOpGt
	BinOpNeq
	BinOpLe
	BinOpGe
)

var binOpMapping = []BinOp{
	token.ADD:     BinOpAdd,
	token.SUB:     BinOpSub,
	token.MUL:     BinOpMul,
	token.QUO:     BinOpDiv,
	token.REM:     BinOpRem,
	token.AND:     BinOpAnd,
	token.OR:      BinOpOr,
	token.XOR:     BinOpXor,
	token.SHL:     BinOpShl,
	token.SHR:     BinOpShr,
	token.AND_NOT: BinOpAndNot,
	token.EQL:     BinOpEq,
	token.LSS:     BinOpLt,
	token.GTR:     BinOpGt,
	token.NEQ:     BinOpNeq,
	token.LEQ:     BinOpLe,
	token.GEQ:     BinOpGe,
}

type VarKind byte

const (
	_ VarKind = iota
	VarKindConst
	VarKindParameter
	VarKindFreeVariable
	VarKindLocal
)

type api struct {
	lastBlock int
	buf       *util.ByteBuffer
	program   *ssa.Program
}

func (a *api) MkUnOp(inst *ssa.UnOp) {
	a.buf.Write(byte(MethodMkUnOp))
	a.writeVar(inst)
	a.buf.Write(byte(unOpMapping[inst.Op]))
	a.writeVar(inst.X)
}

func (a *api) MkBinOp(inst *ssa.BinOp) {
	a.buf.Write(byte(MethodMkBinOp))
	a.writeVar(inst)
	a.writeVar(inst.X)
	a.buf.Write(byte(binOpMapping[inst.Op]))
	a.writeVar(inst.Y)
}

func (a *api) MkCall(inst *ssa.Call) {
	a.buf.Write(byte(MethodMkCall))
	a.buf.WriteInt32(resolveRegister(inst))
	a.writeCall(inst.Common())
}

type BuiltinFunc byte

const (
	_ BuiltinFunc = iota
	Append
	Copy
	Close
	Delete
	Print
	Println
	Len
	Cap
	Min
	Max
	Real
	Imag
	Complex
	Panic
	Recover
	SsaWrapNilCheck
)

var Builtins = map[string]BuiltinFunc{
	"append":         Append,
	"copy":           Copy,
	"close":          Close,
	"delete":         Delete,
	"print":          Print,
	"println":        Println,
	"len":            Len,
	"cap":            Cap,
	"min":            Min,
	"max":            Max,
	"real":           Real,
	"imag":           Imag,
	"complex":        Complex,
	"panic":          Panic,
	"recover":        Recover,
	"ssa:wrapnilchk": SsaWrapNilCheck,
}

func (a *api) MkCallBuiltin(inst *ssa.Call, name string) {
	a.buf.Write(byte(MethodMkCallBuiltin))
	a.writeVar(inst)
	a.buf.Write(byte(Builtins[name]))

	args := inst.Call.Args
	a.buf.WriteInt32(int32(len(args)))
	for i := range args {
		a.writeVar(args[i])
	}

	switch name {
	case "append", "copy":
		a.buf.WriteSliceElementSort(inst)
	case "recover":
		method := inst.Parent().Parent()
		a.buf.WriteUintptr(util.ToPointer(method))
		a.buf.WriteUintptr(util.ToPointer(&method.Recover.Instrs[0]))
	}
}

func (a *api) MkChangeInterface(inst *ssa.ChangeInterface) {
	a.buf.Write(byte(MethodMkChangeInterface))
	a.writeVar(inst)
	a.writeVar(inst.X)
}

func (a *api) MkChangeType(inst *ssa.ChangeType) {
	a.buf.Write(byte(MethodMkChangeType))
	a.writeVar(inst)
	a.writeVar(inst.X)
}

func (a *api) MkConvert(inst *ssa.Convert) {
	a.buf.Write(byte(MethodMkConvert))
	a.writeVar(inst)
	a.writeVar(inst.X)
}

func (a *api) MkSliceToArrayPointer(inst *ssa.SliceToArrayPointer) {
	a.buf.Write(byte(MethodMkSliceToArrayPointer))

	arrayType := inst.Type().Underlying().(*types.Pointer).Elem().Underlying().(*types.Array)
	a.buf.WriteInt32(resolveRegister(inst))
	a.buf.WriteType(arrayType)
	a.buf.Write(byte(sort.MapSort(arrayType.Elem(), false)))
	a.buf.WriteInt64(arrayType.Len())
	a.writeVar(inst.X)
}

func (a *api) MkMakeInterface(inst *ssa.MakeInterface) {
	a.buf.Write(byte(MethodMkMakeInterface))
	a.writeVar(inst)
	a.writeVar(inst.X)
}

func (a *api) MkStore(inst *ssa.Store) {
	a.buf.Write(byte(MethodMkStore))
	a.writeVar(inst.Addr)
	a.writeVar(inst.Val)
}

func (a *api) MkIf(inst *ssa.If) {
	a.buf.Write(byte(MethodMkIf))
	a.writeVar(inst.Cond)
	a.buf.WriteUintptr(util.ToPointer(&inst.Block().Succs[0].Instrs[0]))
	a.buf.WriteUintptr(util.ToPointer(&inst.Block().Succs[1].Instrs[0]))
}

func (a *api) MkJump(_ *ssa.Jump) {
	a.buf.Write(byte(MethodMkJump))
}

func (a *api) MkDefer(inst *ssa.Defer) {
	a.buf.Write(byte(MethodMkDefer))
	a.writeCall(inst.Common())
}

func (a *api) MkGo(_ *ssa.Go) {
	a.buf.Write(byte(MethodMkGo))
}

func (a *api) MkMakeChan(_ *ssa.MakeChan) {
	a.buf.Write(byte(MethodMkMakeChan))
}

func (a *api) MkAlloc(inst *ssa.Alloc) {
	a.buf.Write(byte(MethodMkAlloc))
	a.buf.WriteInt32(resolveRegister(inst))
	a.buf.Write(byte(sort.GetSort(inst, true)))
	a.buf.WriteValueUnderlyingType(inst)

	e := inst.Type().Underlying().(*types.Pointer).Elem().Underlying()
	if arrayType, ok := e.(*types.Array); ok {
		a.buf.WriteInt64(arrayType.Len())
		a.buf.WriteType(types.NewSlice(arrayType.Elem()))
	}
}

func (a *api) MkMakeSlice(inst *ssa.MakeSlice) {
	a.buf.Write(byte(MethodMkMakeSlice))
	a.buf.WriteInt32(resolveRegister(inst))
	a.buf.WriteValueUnderlyingType(inst)
	a.writeVar(inst.Len)
}

func (a *api) MkMakeMap(inst *ssa.MakeMap) {
	a.buf.Write(byte(MethodMkMakeMap))
	a.buf.WriteInt32(resolveRegister(inst))
	a.buf.WriteValueUnderlyingType(inst)
	a.writeVar(inst.Reserve)
}

func (a *api) MkExtract(inst *ssa.Extract) {
	a.buf.Write(byte(MethodMkExtract))
	a.writeVar(inst)
	a.writeVar(inst.Tuple)
	a.buf.WriteInt32(int32(inst.Index))
}

func (a *api) MkSlice(inst *ssa.Slice) {
	a.buf.Write(byte(MethodMkSlice))
	a.writeVar(inst)
	a.buf.WriteSliceElementSort(inst)
	a.writeVar(inst.X)
	a.writeVar(inst.Low)
	a.writeVar(inst.High)
}

func (a *api) MkReturn(inst *ssa.Return) {
	a.buf.Write(byte(MethodMkReturn))
	a.buf.WriteInt32(int32(len(inst.Results)))

	switch len(inst.Results) {
	case 0:
		a.writeNil()
		return
	case 1:
		a.writeVar(inst.Results[0])
	default:
		for _, v := range inst.Results {
			a.writeVar(v)
		}
	}
}

func (a *api) MkRunDefers(_ *ssa.RunDefers) {
	a.buf.Write(byte(MethodMkRunDefers))
}

func (a *api) MkPanic(inst *ssa.Panic) {
	a.buf.Write(byte(MethodMkPanic))
	switch v := inst.X.(type) {
	case *ssa.MakeInterface:
		a.writeVar(v.X)
	default:
		a.writeVar(ssa.NewConst(constant.MakeString(inst.X.String()), types.Typ[types.String]))
	}
}

func (a *api) MkRange(inst *ssa.Range) {
	a.buf.Write(byte(MethodMkRange))
	a.writeVar(inst)
	a.writeVar(inst.X)

	if m, ok := inst.X.Type().Underlying().(*types.Map); ok {
		a.buf.Write(byte(sort.MapSort(m.Key(), false)))
		a.buf.Write(byte(sort.MapSort(m.Elem(), false)))
	}
}

func (a *api) MkNext(inst *ssa.Next) {
	a.buf.Write(byte(MethodMkNext))
	a.writeVar(inst)
	a.writeVar(inst.Iter)
	a.buf.WriteBool(inst.IsString)

	keyType := inst.Type().(*types.Tuple).At(1).Type()
	if keyType != types.Typ[types.Invalid] {
		a.buf.Write(byte(sort.MapSort(keyType, false)))
	}
	valueType := inst.Type().(*types.Tuple).At(2).Type()
	if valueType != types.Typ[types.Invalid] {
		a.buf.Write(byte(sort.MapSort(valueType, false)))
	}
}

func (a *api) MkPointerFieldReading(inst *ssa.FieldAddr) {
	a.buf.Write(byte(MethodMkPointerFieldReading))
	a.mkFieldReading(inst, inst.X, inst.Field)
}

func (a *api) MkFieldReading(inst *ssa.Field) {
	a.buf.Write(byte(MethodMkFieldReading))
	a.mkFieldReading(inst, inst.X, inst.Field)
}

func (a *api) MkPointerArrayReading(inst *ssa.IndexAddr) {
	a.buf.Write(byte(MethodMkPointerArrayReading))
	a.mkArrayReading(inst, inst.X, inst.Index)
}

func (a *api) MkArrayReading(inst *ssa.Index) {
	a.buf.Write(byte(MethodMkArrayReading))
	a.mkArrayReading(inst, inst.X, inst.Index)
}

func (a *api) MkMapLookup(inst *ssa.Lookup) {
	a.buf.Write(byte(MethodMkMapLookup))
	a.writeVar(inst)
	a.writeVar(inst.X)
	a.writeVar(inst.Index)
	a.buf.Write(byte(sort.GetMapElemSort(inst.X)))
	a.buf.WriteBool(inst.CommaOk)
}

func (a *api) MkMapUpdate(inst *ssa.MapUpdate) {
	a.buf.Write(byte(MethodMkMapUpdate))
	a.writeVar(inst.Map)
	a.writeVar(inst.Key)
	a.writeVar(inst.Value)
}

func (a *api) MkTypeAssert(inst *ssa.TypeAssert) {
	a.buf.Write(byte(MethodMkTypeAssert))
	a.writeVar(inst)
	a.writeVar(inst.X)
	a.buf.WriteType(inst.AssertedType)
	a.buf.WriteBool(inst.CommaOk)
}

func (a *api) MkMakeClosure(inst *ssa.MakeClosure) {
	a.buf.Write(byte(MethodMkMakeClosure))
	a.writeVar(inst)

	a.buf.WriteUintptr(util.ToPointer(inst.Fn.(*ssa.Function)))
	a.buf.WriteInt32(int32(len(inst.Bindings)))
	for _, b := range inst.Bindings {
		a.writeVar(b)
	}
}

func (a *api) MkPhi(inst *ssa.Phi) {
	var edge ssa.Value
	for i, pred := range inst.Block().Preds {
		if a.lastBlock == pred.Index {
			edge = inst.Edges[i]
			break
		}
	}

	a.mkVariable(inst, edge)
}

func (a *api) MkSelect(_ *ssa.Select) {
	a.buf.Write(byte(MethodMkSelect))
}

func (a *api) SetLastBlock(block int) {
	a.lastBlock = block
}

func (a *api) WriteLastBlock() {
	a.buf.WriteInt32(int32(a.lastBlock))
}

func (a *api) Log(values ...any) {
	util.Log(values...)
}

func (a *api) mkFieldReading(inst, object ssa.Value, index int) {
	a.buf.WriteInt32(resolveRegister(inst))
	a.buf.WriteValueType(inst)
	a.buf.Write(byte(sort.GetSort(inst, true)))
	a.writeVar(object)
	a.buf.WriteInt32(int32(index))
}

func (a *api) mkArrayReading(inst, array, index ssa.Value) {
	a.buf.WriteInt32(resolveRegister(inst))
	a.buf.WriteValueType(inst)
	a.buf.Write(byte(sort.GetSort(inst, true)))

	a.writeVar(array)
	a.writeVar(index)
}

func (a *api) mkVariable(inst ssa.Value, value ssa.Value) {
	a.buf.Write(byte(MethodMkVariable))
	a.buf.WriteInt32(resolveRegister(inst))
	a.writeVar(value)
}

func (a *api) writeVar(v ssa.Value) {
	if v == nil {
		a.writeNil()
		return
	}

	switch in := v.(type) {
	case *ssa.Parameter:
		f := in.Parent()
		for i, p := range f.Params {
			if p != in {
				continue
			}
			a.buf.Write(byte(VarKindParameter)).
				WriteValueType(v).
				WriteValueUnderlyingType(v).
				Write(byte(sort.GetSort(in, false))).
				WriteInt32(int32(i))
		}

	case *ssa.FreeVar:
		f := in.Parent()
		for i, p := range f.FreeVars {
			if p != in {
				continue
			}
			a.buf.Write(byte(VarKindFreeVariable)).
				WriteValueType(v).
				WriteValueUnderlyingType(v).
				Write(byte(sort.GetSort(in, false))).
				WriteInt32(int32(i))
		}

	case *ssa.Const:
		a.buf.Write(byte(VarKindConst))
		a.writeConst(in)

	default:
		a.buf.Write(byte(VarKindLocal)).
			WriteValueType(v).
			WriteValueUnderlyingType(v).
			Write(byte(sort.GetSort(in, false))).
			WriteInt32(resolveRegister(in))
	}
}

func (a *api) writeConst(in *ssa.Const) {
	s := sort.GetSort(in, false)
	a.buf.WriteValueType(in)
	a.buf.WriteValueUnderlyingType(in)
	a.buf.Write(byte(s))

	switch s {
	case sort.Bool:
		a.buf.WriteBool(constant.BoolVal(in.Value))
	case sort.Int8:
		a.buf.WriteInt8(int8(in.Int64()))
	case sort.Int16:
		a.buf.WriteInt16(int16(in.Int64()))
	case sort.Int32:
		a.buf.WriteInt32(int32(in.Int64()))
	case sort.Int64:
		a.buf.WriteInt64(in.Int64())
	case sort.Uint8:
		a.buf.WriteUint8(uint8(in.Uint64()))
	case sort.Uint16:
		a.buf.WriteUint16(uint16(in.Uint64()))
	case sort.Uint32:
		a.buf.WriteUint32(uint32(in.Uint64()))
	case sort.Uint64:
		a.buf.WriteUint64(in.Uint64())
	case sort.Float32:
		a.buf.WriteFloat32(float32(in.Float64()))
	case sort.Float64:
		a.buf.WriteFloat64(in.Float64())
	case sort.String:
		a.buf.WriteString(constant.StringVal(in.Value))
	default:
	}
}

func (a *api) writeNil() {
	nilType := types.Typ[types.UntypedNil]
	a.buf.Write(byte(VarKindConst))
	a.buf.WriteType(nilType)
	a.buf.WriteType(nilType.Underlying())
	a.buf.Write(byte(sort.Void))
}

func (a *api) writeCall(call *ssa.CallCommon) {
	args := make([]ssa.Value, 0, len(call.Args)+1)
	if call.IsInvoke() {
		args = append(args, call.Value)
	}
	args = append(args, call.Args...)

	a.buf.WriteInt32(int32(len(args)))
	for i := range args {
		a.writeVar(args[i])
	}

	a.buf.WriteBool(call.IsInvoke())
	if call.IsInvoke() {
		a.buf.WriteUintptr(util.ToPointer(call.Method))
	} else {
		function := graph.Callee(a.program, call)
		a.buf.WriteUintptr(util.ToPointer(function))
		a.buf.WriteUintptr(util.ToPointer(&function.Blocks[0].Instrs[0]))
	}
}

func resolveRegister(in ssa.Value) int32 {
	register, _ := strconv.ParseInt(in.Name()[1:], 10, 32)
	return int32(register)
}
