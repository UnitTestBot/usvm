package api

import (
	"go/token"
	"strconv"

	"golang.org/x/tools/go/ssa"

	"usvm/util"
)

type Api interface {
	MkBinOp(inst *ssa.BinOp)
	MkIf(inst *ssa.If)
	MkReturn(inst ssa.Value)
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
	MethodMkBinOp
	MethodMkIf
	MethodMkReturn
	MethodMkVariable
)

type BinOp byte

const (
	_ BinOp = iota

	BinOpEq
	BinOpNeq
	BinOpLt
	BinOpLe
	BinOpGt
	BinOpGe

	BinOpAdd
	BinOpSub
	BinOpMul
	BinOpDiv
	BinOpMod
)

var binOpMapping = map[token.Token]BinOp{
	token.EQL: BinOpEq,
	token.NEQ: BinOpNeq,
	token.LSS: BinOpLt,
	token.LEQ: BinOpLe,
	token.GTR: BinOpGt,
	token.GEQ: BinOpGe,
	token.ADD: BinOpAdd,
	token.SUB: BinOpSub,
	token.MUL: BinOpMul,
	token.QUO: BinOpDiv,
	token.REM: BinOpMod,
}

type VarKind byte

const (
	VarKindIllegal VarKind = iota
	VarKindConst
	VarKindParameter
	VarKindLocal
)

type api struct {
	lastBlock int
	buf       *util.ByteBuffer
}

func (a *api) MkBinOp(inst *ssa.BinOp) {
	name := resolveRegister(inst.Name())
	fstT, fst := resolveVar(inst.X)
	sndT, snd := resolveVar(inst.Y)
	t := binOpMapping[inst.Op]

	a.buf.Write(byte(MethodMkBinOp))
	a.buf.Write(byte(t))
	a.buf.WriteInt(name)
	a.buf.Write(byte(fstT))
	a.buf.WriteInt(fst)
	a.buf.Write(byte(sndT))
	a.buf.WriteInt(snd)
}

func (a *api) MkIf(inst *ssa.If) {
	_, exprC := resolveVar(inst.Cond)
	posC := int64(util.ToPointer(&inst.Block().Succs[0].Instrs[0]))
	negC := int64(util.ToPointer(&inst.Block().Succs[1].Instrs[0]))

	a.buf.Write(byte(MethodMkIf))
	a.buf.WriteInt(exprC)
	a.buf.WriteLong(posC)
	a.buf.WriteLong(negC)
}

func (a *api) MkReturn(value ssa.Value) {
	varT, varValue := resolveVar(value)

	a.buf.Write(byte(MethodMkReturn))
	a.buf.Write(byte(varT))
	a.buf.WriteInt(varValue)
}

func (a *api) MkVariable(name string, value ssa.Value) {
	nameI := resolveRegister(name)
	varT, varValue := resolveVar(value)

	a.buf.Write(byte(MethodMkVariable))
	a.buf.WriteInt(nameI)
	a.buf.Write(byte(varT))
	a.buf.WriteInt(varValue)
}

func (a *api) GetLastBlock() int {
	return a.lastBlock
}

func (a *api) SetLastBlock(block int) {
	a.lastBlock = block
}

func (a *api) WriteLastBlock() {
	a.buf.WriteInt(a.lastBlock)
}

func (a *api) Log(values ...any) {
	util.Log(values...)
}

func resolveVar(in ssa.Value) (VarKind, int) {
	switch in := in.(type) {
	case *ssa.Parameter:
		f := in.Parent()
		for i, p := range f.Params {
			if p == in {
				return VarKindParameter, i
			}
		}
	case *ssa.Const:
		return VarKindConst, int(in.Int64())
	default:
		i, _ := strconv.ParseInt(in.Name()[1:], 10, 32)
		return VarKindLocal, int(i)
	}
	return VarKindIllegal, -1
}

func resolveRegister(in string) int {
	name, _ := strconv.ParseInt(in[1:], 10, 32)
	return int(name)
}
