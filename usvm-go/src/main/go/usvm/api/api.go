package api

import (
	"go/constant"
	"go/token"
	"go/types"
	"strconv"

	"golang.org/x/tools/go/ssa"

	"usvm/util"
)

type Api interface {
	MkUnOp(inst *ssa.UnOp)
	MkBinOp(inst *ssa.BinOp)
	MkIf(inst *ssa.If)
	MkReturn(inst *ssa.Return)
	MkVariable(name string, value ssa.Value)

	GetLastBlock() int
	SetLastBlock(block int)
	WriteLastBlock()

	Log(values ...any)
}

func NewApi(block int, buf []byte) Api {
	apiInstance.lastBlock = block
	apiInstance.buf = util.NewByteBuffer(buf)
	return apiInstance
}

var apiInstance = &api{}

type Method byte

const (
	_ Method = iota
	MethodMkUnOp
	MethodMkBinOp
	MethodMkIf
	MethodMkReturn
	MethodMkVariable
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
	BinOpMod
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
	token.REM:     BinOpMod,
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
	VarKindLocal
)

type Type byte

const (
	_ Type = iota
	TypeBool
	TypeInt8
	TypeUint8
	TypeInt16
	TypeUint16
	TypeInt32
	TypeUint32
	TypeInt64
	TypeUint64
	TypeFloat32
	TypeFloat64
)

var typeMapping = []Type{
	types.Bool:         TypeBool,
	types.UntypedBool:  TypeBool,
	types.Int8:         TypeInt8,
	types.Uint8:        TypeUint8,
	types.Int16:        TypeInt16,
	types.Uint16:       TypeUint16,
	types.Int:          TypeInt32,
	types.Uint:         TypeUint32,
	types.UntypedInt:   TypeInt32,
	types.Int32:        TypeInt32,
	types.Uint32:       TypeUint32,
	types.UntypedRune:  TypeInt32,
	types.Int64:        TypeInt64,
	types.Uint64:       TypeUint64,
	types.Uintptr:      TypeUint64,
	types.Float32:      TypeFloat32,
	types.Float64:      TypeFloat64,
	types.UntypedFloat: TypeFloat64,
}

type api struct {
	lastBlock int
	buf       *util.ByteBuffer
}

func (a *api) MkUnOp(inst *ssa.UnOp) {
	name := resolveRegister(inst.Name())
	u := byte(unOpMapping[inst.Op])
	t := byte(typeMapping[inst.Type().Underlying().(*types.Basic).Kind()])

	a.buf.Write(byte(MethodMkUnOp))
	a.buf.Write(u)
	a.buf.Write(t)
	a.buf.WriteInt32(name)
	a.writeVar(inst.X)
}

func (a *api) MkBinOp(inst *ssa.BinOp) {
	name := resolveRegister(inst.Name())
	b := byte(binOpMapping[inst.Op])
	t := byte(typeMapping[inst.Type().Underlying().(*types.Basic).Kind()])

	a.buf.Write(byte(MethodMkBinOp))
	a.buf.Write(b)
	a.buf.Write(t)
	a.buf.WriteInt32(name)
	a.writeVar(inst.X)
	a.writeVar(inst.Y)
}

func (a *api) MkIf(inst *ssa.If) {
	pos := int64(util.ToPointer(&inst.Block().Succs[0].Instrs[0]))
	neg := int64(util.ToPointer(&inst.Block().Succs[1].Instrs[0]))

	a.buf.Write(byte(MethodMkIf))
	a.writeVar(inst.Cond)
	a.buf.WriteInt64(pos)
	a.buf.WriteInt64(neg)
}

func (a *api) MkReturn(inst *ssa.Return) {
	var value ssa.Value
	switch len(inst.Results) {
	case 0:
		return
	case 1:
		value = inst.Results[0]
	default:
		return
	}

	a.buf.Write(byte(MethodMkReturn))
	a.writeVar(value)
}

func (a *api) MkVariable(name string, value ssa.Value) {
	nameI := resolveRegister(name)

	a.buf.Write(byte(MethodMkVariable))
	a.buf.WriteInt32(nameI)
	a.writeVar(value)
}

func (a *api) GetLastBlock() int {
	return a.lastBlock
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

func (a *api) writeVar(in ssa.Value) {
	switch in := in.(type) {
	case *ssa.Parameter:
		f := in.Parent()
		for i, p := range f.Params {
			if p == in {
				t := in.Type().Underlying().(*types.Basic)
				a.buf.Write(byte(VarKindParameter)).Write(byte(typeMapping[t.Kind()])).WriteInt32(int32(i))
			}
		}
	case *ssa.Const:
		a.buf.Write(byte(VarKindConst))
		a.writeConst(in)
	default:
		i := resolveRegister(in.Name())
		t := in.Type().Underlying().(*types.Basic)
		a.buf.Write(byte(VarKindLocal)).Write(byte(typeMapping[t.Kind()])).WriteInt32(i)
	}
}

func (a *api) writeConst(in *ssa.Const) {
	if t, ok := in.Type().Underlying().(*types.Basic); ok {
		a.buf.Write(byte(typeMapping[t.Kind()]))
		switch t.Kind() {
		case types.Bool, types.UntypedBool:
			a.buf.WriteBool(constant.BoolVal(in.Value))
		case types.Int8:
			a.buf.WriteInt8(int8(in.Int64()))
		case types.Int16:
			a.buf.WriteInt16(int16(in.Int64()))
		case types.Int, types.UntypedInt, types.Int32, types.UntypedRune:
			a.buf.WriteInt32(int32(in.Int64()))
		case types.Int64:
			a.buf.WriteInt64(in.Int64())
		case types.Uint8:
			a.buf.WriteUint8(uint8(in.Uint64()))
		case types.Uint16:
			a.buf.WriteUint16(uint16(in.Uint64()))
		case types.Uint, types.Uint32:
			a.buf.WriteUint32(uint32(in.Uint64()))
		case types.Uint64, types.Uintptr:
			a.buf.WriteUint64(in.Uint64())
		case types.Float32:
			a.buf.WriteFloat32(float32(in.Float64()))
		case types.Float64, types.UntypedFloat:
			a.buf.WriteFloat64(in.Float64())
		default:
		}
	}
}

func resolveRegister(in string) int32 {
	name, _ := strconv.ParseInt(in[1:], 10, 32)
	return int32(name)
}
