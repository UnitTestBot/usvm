package main

import (
	"go/types"
	"log"
	"sort"

	"github.com/samber/lo"
	"golang.org/x/tools/go/ssa"
)

const (
	NamedConstMember = "NamedConst"
	GlobalMember     = "Global"
	FunctionMember   = "Function"
	TypeMember       = "Type"

	DebugRefInstruction            = "DebugRef"
	UnOpInstruction                = "UnOp"
	BinOpInstruction               = "BinOp"
	CallInstruction                = "Call"
	ChangeInterfaceInstruction     = "ChangeInterface"
	ChangeTypeInstruction          = "ChangeType"
	ConvertInstruction             = "Convert"
	SliceToArrayPointerInstruction = "SliceToArrayPointer"
	MakeInterfaceInstruction       = "MakeInterface"
	ExtractInstruction             = "Extract"
	SliceInstruction               = "Slice"
	ReturnInstruction              = "Return"
	RunDefersInstruction           = "RunDefers"
	PanicInstruction               = "Panic"
	SendInstruction                = "Send"
	StoreInstruction               = "Store"
	IfInstruction                  = "If"
	JumpInstruction                = "Jump"
	DeferInstruction               = "Defer"
	GoInstruction                  = "Go"
	MakeChanInstruction            = "MakeChan"
	AllocInstruction               = "Alloc"
	MakeSliceInstruction           = "MakeSlice"
	MakeMapInstruction             = "MakeMap"
	RangeInstruction               = "Range"
	NextInstruction                = "Next"
	FieldAddrInstruction           = "FieldAddr"
	FieldInstruction               = "Field"
	IndexAddrInstruction           = "IndexAddr"
	IndexInstruction               = "Index"
	LookupInstruction              = "Lookup"
	MapUpdateInstruction           = "MapUpdate"
	TypeAssertInstruction          = "TypeAssert"
	MakeClosureInstruction         = "MakeClosure"
	PhiInstruction                 = "Phi"
	SelectInstruction              = "Select"

	ConstValue     = "Const"
	GlobalValue    = "Global"
	ParameterValue = "Parameter"
	FreeVarValue   = "FreeVar"
	VarValue       = "Var"

	FunctionValue    = "Function"
	MakeClosureValue = "MakeClosure"
	BuiltinValue     = "Builtin"
)

type Package struct {
	Name    string   `yaml:"name" json:"name"`
	Members []Member `yaml:"members" json:"members"`
}

func PackPackage(in *ssa.Package) Package {
	return Package{
		Name:    in.Pkg.Path(),
		Members: PackMembers(in.Members),
	}
}

type Member interface {
	isMember()
}

func PackMembers(membersMap map[string]ssa.Member) []Member {
	members := lo.Values(membersMap)
	for _, member := range membersMap {
		if f, ok := member.(*ssa.Function); ok {
			members = append(members, lo.Map(f.AnonFuncs, func(f *ssa.Function, _ int) ssa.Member { return f })...)
		}
	}
	sort.Slice(members, func(i, j int) bool {
		return members[i].Name() < members[j].Name()
	})

	return lo.Map(members, PackMember)
}

func PackMember(in ssa.Member, _ int) Member {
	common := CommonMember{
		Name: in.Name(),
	}

	switch member := in.(type) {
	case *ssa.NamedConst:
		common.Type = NamedConstMember
		return NamedConst{
			CommonMember: common,
			Value: NamedConstValue{
				Type:  member.Value.Type().String(),
				Value: member.Value.Value.String(),
			},
		}
	case *ssa.Global:
		common.Type = GlobalMember
		return common
	case *ssa.Type:
		common.Type = TypeMember
		return common
	case *ssa.Function:
		common.Type = FunctionMember
		return Function{
			CommonMember: common,
			BasicBlocks:  lo.Map(member.Blocks, PackBasicBlock),
			Parameters:   lo.Map(member.Params, PackParameter),
			FreeVars:     lo.Map(member.FreeVars, func(v *ssa.FreeVar, _ int) Value { return PackValue(v) }),
			ReturnTypes:  PackReturnTypes(member),
		}
	}

	return nil
}

type CommonMember struct {
	Type string `yaml:"type" json:"type"`
	Name string `yaml:"name" json:"name"`
}

func (CommonMember) isMember() {}

type NamedConst struct {
	CommonMember `yaml:",inline"`
	Value        NamedConstValue `yaml:"value" json:"value"`
}

type NamedConstValue struct {
	Type  string `yaml:"type" json:"type"`
	Value string `yaml:"value" json:"value"`
}

type Function struct {
	CommonMember `yaml:",inline"`
	BasicBlocks  []BasicBlock `yaml:"basic_blocks" json:"basic_blocks"`
	Parameters   []Parameter  `yaml:"parameters" json:"parameters"`
	FreeVars     []Value      `yaml:"free_vars" json:"free_vars"`
	ReturnTypes  []string     `yaml:"return_types" json:"return_types"`
}

type BasicBlock struct {
	Index        int           `yaml:"index" json:"index"`
	Instructions []Instruction `yaml:"instructions" json:"instructions"`
	Prev         []int         `yaml:"prev,flow" json:"prev"`
	Next         []int         `yaml:"next,flow" json:"next"`
}

func PackBasicBlock(in *ssa.BasicBlock, _ int) BasicBlock {
	return BasicBlock{
		Index:        in.Index,
		Instructions: lo.Map(in.Instrs, PackInstruction),
		Prev:         lo.Map(in.Preds, func(b *ssa.BasicBlock, _ int) int { return b.Index }),
		Next:         lo.Map(in.Succs, func(b *ssa.BasicBlock, _ int) int { return b.Index }),
	}
}

func PackParameter(in *ssa.Parameter, index int) Parameter {
	return Parameter{
		CommonValue: CommonValue{
			Type:   ParameterValue,
			GoType: in.Type().String(),
			Name:   in.Name(),
		},
		Index: index,
	}
}

func PackReturnTypes(in *ssa.Function) []string {
	results := in.Signature.Results()
	returnTypes := make([]string, 0, results.Len())
	for i := 0; i < results.Len(); i++ {
		returnTypes = append(returnTypes, results.At(i).Type().String())
	}

	return returnTypes
}

type Instruction interface {
	isInstruction()
}

func PackInstruction(in ssa.Instruction, _ int) Instruction {
	common := CommonInstruction{
		Name:  in.String(),
		Block: in.Block().Index,
		Line:  FindInstructionIndex(in),
	}

	switch inst := in.(type) {
	case *ssa.DebugRef:
		common.Type = DebugRefInstruction
		return DebugRef{
			CommonInstruction: common,
		}
	case *ssa.UnOp:
		common.Type = UnOpInstruction
		return UnOp{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Op:                inst.Op.String(),
			Register:          inst.Name(),
			Argument:          PackValue(inst.X),
			CommaOk:           inst.CommaOk,
		}
	case *ssa.BinOp:
		common.Type = BinOpInstruction
		return BinOp{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Op:                inst.Op.String(),
			Register:          inst.Name(),
			First:             PackValue(inst.X),
			Second:            PackValue(inst.Y),
		}
	case *ssa.Call:
		common.Type = CallInstruction
		return Call{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Register:          inst.Name(),
			Value:             PackCallValue(inst.Call.Value),
			Args:              lo.Map(inst.Call.Args, PackValueIdx),
		}
	case *ssa.ChangeInterface:
		common.Type = ChangeInterfaceInstruction
		return ChangeInterface{
			CommonInstruction: common,
		}
	case *ssa.ChangeType:
		common.Type = ChangeTypeInstruction
		return ChangeType{
			CommonInstruction: common,
		}
	case *ssa.Convert:
		common.Type = ConvertInstruction
		return Convert{
			CommonInstruction: common,
		}
	case *ssa.SliceToArrayPointer:
		common.Type = SliceToArrayPointerInstruction
		return SliceToArrayPointer{
			CommonInstruction: common,
		}
	case *ssa.MakeInterface:
		common.Type = MakeInterfaceInstruction
		return MakeInterface{
			CommonInstruction: common,
		}
	case *ssa.Extract:
		common.Type = ExtractInstruction
		return Extract{
			CommonInstruction: common,
		}
	case *ssa.Slice:
		common.Type = SliceInstruction
		return Slice{
			CommonInstruction: common,
		}
	case *ssa.Return:
		common.Type = ReturnInstruction
		return Return{
			CommonInstruction: common,
			Results:           lo.Map(inst.Results, PackValueIdx),
		}
	case *ssa.RunDefers:
		common.Type = RunDefersInstruction
		return RunDefers{
			CommonInstruction: common,
		}
	case *ssa.Panic:
		common.Type = PanicInstruction
		return Panic{
			CommonInstruction: common,
		}
	case *ssa.Send:
		common.Type = SendInstruction
		return Send{
			CommonInstruction: common,
		}
	case *ssa.Store:
		common.Type = StoreInstruction
		return Store{
			CommonInstruction: common,
			Addr:              PackValue(inst.Addr),
			Value:             PackValue(inst.Val),
		}
	case *ssa.If:
		common.Type = IfInstruction
		return If{
			CommonInstruction: common,
			Condition:         PackValue(inst.Cond),
			TrueBranch:        inst.Block().Succs[0].Index,
			FalseBranch:       inst.Block().Succs[1].Index,
		}
	case *ssa.Jump:
		common.Type = JumpInstruction
		return Jump{
			CommonInstruction: common,
			Index:             inst.Block().Succs[0].Index,
		}
	case *ssa.Defer:
		common.Type = DeferInstruction
		return Defer{
			CommonInstruction: common,
		}
	case *ssa.Go:
		common.Type = GoInstruction
		return Go{
			CommonInstruction: common,
		}
	case *ssa.MakeChan:
		common.Type = MakeChanInstruction
		return MakeChan{
			CommonInstruction: common,
		}
	case *ssa.Alloc:
		common.Type = AllocInstruction
		return Alloc{
			CommonInstruction: common,
			GoType:            inst.Type().Underlying().(*types.Pointer).Elem().String(),
			Register:          inst.Name(),
		}
	case *ssa.MakeSlice:
		common.Type = MakeSliceInstruction
		return MakeSlice{
			CommonInstruction: common,
		}
	case *ssa.MakeMap:
		common.Type = MakeMapInstruction
		return MakeMap{
			CommonInstruction: common,
		}
	case *ssa.Range:
		common.Type = RangeInstruction
		return Range{
			CommonInstruction: common,
		}
	case *ssa.Next:
		common.Type = NextInstruction
		return Next{
			CommonInstruction: common,
		}
	case *ssa.FieldAddr:
		common.Type = FieldAddrInstruction
		return FieldAddr{
			CommonInstruction: common,
		}
	case *ssa.Field:
		common.Type = FieldInstruction
		return Field{
			CommonInstruction: common,
		}
	case *ssa.IndexAddr:
		common.Type = IndexAddrInstruction
		return IndexAddr{
			CommonInstruction: common,
		}
	case *ssa.Index:
		common.Type = IndexInstruction
		return Index{
			CommonInstruction: common,
		}
	case *ssa.Lookup:
		common.Type = LookupInstruction
		return Lookup{
			CommonInstruction: common,
		}
	case *ssa.MapUpdate:
		common.Type = MapUpdateInstruction
		return MapUpdate{
			CommonInstruction: common,
		}
	case *ssa.TypeAssert:
		common.Type = TypeAssertInstruction
		return TypeAssert{
			CommonInstruction: common,
		}
	case *ssa.MakeClosure:
		common.Type = MakeClosureInstruction
		return MakeClosure{
			CommonInstruction: common,
			Register:          inst.Name(),
			Function:          PackValue(inst.Fn),
			Bindings:          lo.Map(inst.Bindings, PackValueIdx),
		}
	case *ssa.Phi:
		common.Type = PhiInstruction
		return Phi{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Register:          inst.Name(),
			Edges:             lo.Map(inst.Edges, PackValueIdx),
		}
	case *ssa.Select:
		common.Type = SelectInstruction
		return Select{
			CommonInstruction: common,
		}
	default:
		log.Fatalf("unexpected instruction: %T\n", inst)
	}
	return nil
}

type CommonInstruction struct {
	Type  string `yaml:"type" json:"type"`
	Name  string `yaml:"name" json:"name"`
	Block int    `yaml:"block" json:"block"`
	Line  int    `yaml:"line" json:"line"`
}

func (CommonInstruction) isInstruction() {}

type DebugRef struct {
	CommonInstruction `yaml:",inline"`
}

type UnOp struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Op                string `yaml:"op" json:"op"`
	Register          string `yaml:"register" json:"register"`
	Argument          Value  `yaml:"argument" json:"argument"`
	CommaOk           bool   `yaml:"comma_ok" json:"comma_ok"`
}

type BinOp struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Op                string `yaml:"op" json:"op"`
	Register          string `yaml:"register" json:"register"`
	First             Value  `yaml:"first" json:"first"`
	Second            Value  `yaml:"second" json:"second"`
}

type Call struct {
	CommonInstruction `yaml:",inline"`
	GoType            string  `yaml:"go_type" json:"go_type"`
	Register          string  `yaml:"register" json:"register"`
	Value             Value   `yaml:"value" json:"value"`
	Args              []Value `yaml:"args" json:"args"`
}

type ChangeInterface struct {
	CommonInstruction `yaml:",inline"`
}

type ChangeType struct {
	CommonInstruction `yaml:",inline"`
}

type Convert struct {
	CommonInstruction `yaml:",inline"`
}

type SliceToArrayPointer struct {
	CommonInstruction `yaml:",inline"`
}

type MakeInterface struct {
	CommonInstruction `yaml:",inline"`
}

type Extract struct {
	CommonInstruction `yaml:",inline"`
}

type Slice struct {
	CommonInstruction `yaml:",inline"`
}

type Return struct {
	CommonInstruction `yaml:",inline"`
	Results           []Value `yaml:"results" json:"results"`
}

type RunDefers struct {
	CommonInstruction `yaml:",inline"`
}

type Panic struct {
	CommonInstruction `yaml:",inline"`
}

type Send struct {
	CommonInstruction `yaml:",inline"`
}

type Store struct {
	CommonInstruction `yaml:",inline"`
	Addr              Value `yaml:"addr" json:"addr"`
	Value             Value `yaml:"value" json:"value"`
}

type If struct {
	CommonInstruction `yaml:",inline"`
	Condition         Value `yaml:"condition" json:"condition"`
	TrueBranch        int   `yaml:"true_branch" json:"true_branch"`
	FalseBranch       int   `yaml:"false_branch" json:"false_branch"`
}

type Jump struct {
	CommonInstruction `yaml:",inline"`
	Index             int `yaml:"index" json:"index"`
}

type Defer struct {
	CommonInstruction `yaml:",inline"`
}

type Go struct {
	CommonInstruction `yaml:",inline"`
}

type MakeChan struct {
	CommonInstruction `yaml:",inline"`
}

type Alloc struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
}

type MakeSlice struct {
	CommonInstruction `yaml:",inline"`
}

type MakeMap struct {
	CommonInstruction `yaml:",inline"`
}

type Range struct {
	CommonInstruction `yaml:",inline"`
}

type Next struct {
	CommonInstruction `yaml:",inline"`
}

type FieldAddr struct {
	CommonInstruction `yaml:",inline"`
}

type Field struct {
	CommonInstruction `yaml:",inline"`
}

type IndexAddr struct {
	CommonInstruction `yaml:",inline"`
}

type Index struct {
	CommonInstruction `yaml:",inline"`
}

type Lookup struct {
	CommonInstruction `yaml:",inline"`
}

type MapUpdate struct {
	CommonInstruction `yaml:",inline"`
}

type TypeAssert struct {
	CommonInstruction `yaml:",inline"`
}

type MakeClosure struct {
	CommonInstruction `yaml:",inline"`
	Register          string  `yaml:"register" json:"register"`
	Function          Value   `yaml:"function" json:"function"`
	Bindings          []Value `yaml:"bindings" json:"bindings"`
}

type Phi struct {
	CommonInstruction `yaml:",inline"`
	GoType            string  `yaml:"go_type" json:"go_type"`
	Register          string  `yaml:"register" json:"register"`
	Edges             []Value `yaml:"edges" json:"edges"`
}

type Select struct {
	CommonInstruction `yaml:",inline"`
}

type Value interface {
	isValue()
}

func PackValue(in ssa.Value) Value {
	common := CommonValue{
		GoType: in.Type().String(),
		Name:   in.Name(),
	}

	switch value := in.(type) {
	case *ssa.Const:
		common.Type = ConstValue
		return Const{
			CommonValue: common,
			Value: NamedConstValue{
				Type:  value.Type().String(),
				Value: value.Value.String(),
			},
		}
	case *ssa.Global:
		common.Type = GlobalValue
		return Global{
			CommonValue: common,
			Index:       int(value.Pos()),
		}
	case *ssa.Parameter:
		common.Type = ParameterValue
		return Parameter{
			CommonValue: common,
			Index:       FindParameterIndex(value),
		}
	case *ssa.FreeVar:
		common.Type = FreeVarValue
		return FreeVar{
			CommonValue: common,
			Index:       FindFreeVarIndex(value),
		}
	default:
		common.Type = VarValue
		return common
	}
}

func PackValueIdx(in ssa.Value, _ int) Value {
	return PackValue(in)
}

func PackCallValue(in ssa.Value) Value {
	common := CommonValue{
		GoType: in.Type().String(),
		Name:   in.Name(),
	}

	switch in.(type) {
	case *ssa.Function:
		common.Type = FunctionValue
		return common
	case *ssa.MakeClosure:
		common.Type = MakeClosureValue
		return common
	case *ssa.Builtin:
		common.Type = BuiltinValue
		return common
	default:
		return common
	}
}

type CommonValue struct {
	Type   string `yaml:"type" json:"type"`
	GoType string `yaml:"go_type" json:"go_type"`
	Name   string `yaml:"name" json:"name"`
}

func (CommonValue) isValue() {}

type Const struct {
	CommonValue `yaml:",inline"`
	Value       NamedConstValue `yaml:"value" json:"value"`
}

type Global struct {
	CommonValue `yaml:",inline"`
	Index       int `yaml:"index" json:"index"`
}

type Parameter struct {
	CommonValue `yaml:",inline"`
	Index       int `yaml:"index" json:"index"`
}

type FreeVar struct {
	CommonValue `yaml:",inline"`
	Index       int `yaml:"index" json:"index"`
}

func FindInstructionIndex(in ssa.Instruction) int {
	instructions := lo.FlatMap(in.Parent().Blocks, func(block *ssa.BasicBlock, _ int) []ssa.Instruction {
		return block.Instrs
	})
	_, index, _ := lo.FindIndexOf(instructions, func(other ssa.Instruction) bool {
		return other == in
	})
	return index
}

func FindParameterIndex(in *ssa.Parameter) int {
	_, index, _ := lo.FindIndexOf(in.Parent().Params, func(other *ssa.Parameter) bool {
		return other == in
	})
	return index
}

func FindFreeVarIndex(in *ssa.FreeVar) int {
	_, index, _ := lo.FindIndexOf(in.Parent().FreeVars, func(other *ssa.FreeVar) bool {
		return other == in
	})
	return index
}
