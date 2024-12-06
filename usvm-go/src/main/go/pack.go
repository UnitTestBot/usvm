package main

import (
	"go/types"
	"log"
	"sort"
	"strings"

	"github.com/samber/lo"
	"golang.org/x/tools/go/ssa"
)

func PackPackage(in *ssa.Package) Package {
	p := Package{
		Name:    in.Pkg.Path(),
		Types:   make(map[string]Type),
		program: in.Prog,
	}
	p.PackMembers(in.Members)
	sort.Slice(p.Members, func(i, j int) bool {
		return p.Members[i].name() < p.Members[j].name()
	})
	return p
}

func (p *Package) AddType(typ types.Type) {
	name := typ.String()
	if p.Types[name] != nil {
		return
	}

	common := CommonType{
		Name: name,
	}

	switch t := typ.(type) {
	case *types.Alias:
		common.Type = AliasType
		p.Types[name] = Alias{
			CommonType: common,
			From:       types.Unalias(t).String(),
		}
		p.AddType(types.Unalias(t))
	case *types.Array:
		common.Type = ArrayType
		p.Types[name] = Array{
			CommonType: common,
			Len:        t.Len(),
			Elem:       t.Elem().String(),
		}
		p.AddType(t.Elem())
	case *types.Basic:
		common.Type = BasicType
		p.Types[name] = common
	case *types.Chan:
		common.Type = ChanType
		p.Types[name] = Chan{
			CommonType: common,
			Dir:        int(t.Dir()),
			Elem:       t.Elem().String(),
		}
		p.AddType(t.Elem())
	case *types.Interface:
		common.Type = InterfaceType
		p.Types[name] = Interface{
			CommonType: common,
			Methods:    p.PackMethods(t),
		}
	case *types.Map:
		common.Type = MapType
		p.Types[name] = Map{
			CommonType: common,
			Key:        t.Key().String(),
			Elem:       t.Elem().String(),
		}
		p.AddType(t.Key())
		p.AddType(t.Elem())
	case *types.Named:
		common.Type = NamedType
		p.Types[name] = Named{
			CommonType: common,
			Underlying: t.Underlying().String(),
			Methods:    p.PackMethods(t),
		}
		// add members after adding type to avoid stack overflow
		for i := 0; i < t.NumMethods(); i++ {
			p.Members = append(p.Members, p.PackMember(p.program.FuncValue(t.Method(i))))
		}
		p.AddType(t.Underlying())
	case *types.Pointer:
		common.Type = PointerType
		p.Types[name] = Pointer{
			CommonType: common,
			Elem:       t.Elem().String(),
		}
		p.AddType(t.Elem())
	case *types.Signature:
		common.Type = SignatureType
		p.Types[name] = Signature{
			CommonType: common,
			Params:     p.PackTypeTuple(t.Params()),
			Results:    p.PackTypeTuple(t.Results()),
		}
	case *types.Slice:
		common.Type = SliceType
		p.Types[name] = Slice{
			CommonType: common,
			Elem:       t.Elem().String(),
		}
		p.AddType(t.Elem())
	case *types.Struct:
		fields := make(map[string]string)
		for i := 0; i < t.NumFields(); i++ {
			field := t.Field(i)
			fields[field.Name()] = field.Type().String()
			p.AddType(field.Type())
		}
		common.Type = StructType
		p.Types[name] = Struct{
			CommonType: common,
			Fields:     fields,
		}
	case *types.Tuple:
		common.Type = TupleType
		p.Types[name] = Tuple{
			CommonType: common,
			Elems:      p.PackTypeTuple(t),
		}
	case *types.TypeParam:
		common.Type = TypeParamType
		p.Types[name] = common
	case *types.Union:
		common.Type = UnionType
		p.Types[name] = common
	default:
		common.Type = OpaqueType
		p.Types[name] = common
	}
}

func (p *Package) PackMembers(membersMap map[string]ssa.Member) {
	members := lo.Values(membersMap)
	for _, member := range membersMap {
		if f, ok := member.(*ssa.Function); ok {
			members = append(members, lo.Map(f.AnonFuncs, func(f *ssa.Function, _ int) ssa.Member { return f })...)
		}
	}

	for _, member := range members {
		p.Members = append(p.Members, p.PackMember(member))
	}
}

func (p *Package) PackMember(in ssa.Member) Member {
	common := CommonMember{
		Name: in.Name(),
	}

	switch member := in.(type) {
	case *ssa.NamedConst:
		p.AddType(member.Value.Type())
		common.Type = NamedConstMember
		return NamedConst{
			CommonMember: common,
			Value: NamedConstValue{
				Type:  member.Value.Type().String(),
				Value: member.Value.Value.String(),
			},
		}
	case *ssa.Global:
		p.AddType(member.Type())
		common.Type = GlobalMember
		return MemberGlobal{
			CommonMember: common,
			Index:        int(member.Pos()),
			GoType:       member.Type().String(),
		}
	case *ssa.Type:
		p.AddType(member.Type())
		common.Type = TypeMember
		return common
	case *ssa.Function:
		common.Type = FunctionMember
		return Function{
			CommonMember: common,
			BasicBlocks:  lo.Map(member.Blocks, p.PackBasicBlock),
			Parameters:   lo.Map(member.Params, p.PackParameter),
			FreeVars:     lo.Map(member.FreeVars, func(v *ssa.FreeVar, _ int) Value { return p.PackValue(v) }),
			ReturnTypes:  p.PackReturnTypes(member),
		}
	}

	return nil
}

func (p *Package) PackBasicBlock(in *ssa.BasicBlock, _ int) BasicBlock {
	return BasicBlock{
		Index:        in.Index,
		Instructions: lo.Map(in.Instrs, p.PackInstruction),
		Prev:         lo.Map(in.Preds, func(b *ssa.BasicBlock, _ int) int { return b.Index }),
		Next:         lo.Map(in.Succs, func(b *ssa.BasicBlock, _ int) int { return b.Index }),
	}
}

func (p *Package) PackParameter(in *ssa.Parameter, index int) Parameter {
	p.AddType(in.Type())
	return Parameter{
		CommonValue: CommonValue{
			Type:   ParameterValue,
			GoType: in.Type().String(),
			Name:   in.Name(),
		},
		Index: index,
	}
}

func (p *Package) PackReturnTypes(in *ssa.Function) []string {
	return p.PackTypeTuple(in.Signature.Results())
}

func (p *Package) PackTypeTuple(in *types.Tuple) []string {
	returnTypes := make([]string, 0, in.Len())
	for i := 0; i < in.Len(); i++ {
		p.AddType(in.At(i).Type())
		returnTypes = append(returnTypes, in.At(i).Type().String())
	}

	return returnTypes
}

func (p *Package) PackInstruction(in ssa.Instruction, _ int) Instruction {
	common := CommonInstruction{
		Name:  in.String(),
		Block: in.Block().Index,
		Line:  FindInstructionIndex(in),
	}
	if typed, ok := in.(Typed); ok {
		p.AddType(typed.Type())
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
			Argument:          p.PackValue(inst.X),
			CommaOk:           inst.CommaOk,
		}
	case *ssa.BinOp:
		common.Type = BinOpInstruction
		return BinOp{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Op:                inst.Op.String(),
			Register:          inst.Name(),
			First:             p.PackValue(inst.X),
			Second:            p.PackValue(inst.Y),
		}
	case *ssa.Call:
		common.Type = CallInstruction
		return Call{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Register:          inst.Name(),
			Value:             p.PackValue(inst.Call.Value),
			Args:              lo.Map(inst.Call.Args, p.PackValueIdx),
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
			GoType:            inst.Type().String(),
			Register:          inst.Name(),
			Value:             p.PackValue(inst.X),
		}
	case *ssa.Extract:
		common.Type = ExtractInstruction
		return Extract{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Register:          inst.Name(),
			Tuple:             p.PackValue(inst.Tuple),
			Index:             inst.Index,
		}
	case *ssa.Slice:
		common.Type = SliceInstruction
		return SliceInst{
			CommonInstruction: common,
		}
	case *ssa.Return:
		common.Type = ReturnInstruction
		return Return{
			CommonInstruction: common,
			Results:           lo.Map(inst.Results, p.PackValueIdx),
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
			Addr:              p.PackValue(inst.Addr),
			Value:             p.PackValue(inst.Val),
		}
	case *ssa.If:
		common.Type = IfInstruction
		return If{
			CommonInstruction: common,
			Condition:         p.PackValue(inst.Cond),
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
		goType := inst.Type().Underlying().(*types.Pointer).Elem()
		p.AddType(goType)
		return Alloc{
			CommonInstruction: common,
			GoType:            goType.String(),
			Register:          inst.Name(),
		}
	case *ssa.MakeSlice:
		common.Type = MakeSliceInstruction
		return MakeSlice{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Register:          inst.Name(),
			Len:               p.PackValue(inst.Len),
			Cap:               p.PackValue(inst.Cap),
		}
	case *ssa.MakeMap:
		common.Type = MakeMapInstruction
		return MakeMap{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Register:          inst.Name(),
			Reserve:           p.PackValue(inst.Reserve),
		}
	case *ssa.Range:
		common.Type = RangeInstruction
		return Range{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Register:          inst.Name(),
			Collection:        p.PackValue(inst.X),
		}
	case *ssa.Next:
		common.Type = NextInstruction
		return Next{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Register:          inst.Name(),
			Iter:              p.PackValue(inst.Iter),
			IsString:          inst.IsString,
		}
	case *ssa.FieldAddr:
		common.Type = FieldAddrInstruction
		return FieldAddr{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Register:          inst.Name(),
			Struct:            p.PackValue(inst.X),
			Field:             inst.Field,
		}
	case *ssa.Field:
		common.Type = FieldInstruction
		return Field{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Register:          inst.Name(),
			Struct:            p.PackValue(inst.X),
			Field:             inst.Field,
		}
	case *ssa.IndexAddr:
		common.Type = IndexAddrInstruction
		return IndexAddr{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Register:          inst.Name(),
			Array:             p.PackValue(inst.X),
			Index:             p.PackValue(inst.Index),
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
			GoType:            inst.Type().String(),
			Register:          inst.Name(),
			Map:               p.PackValue(inst.X),
			Key:               p.PackValue(inst.Index),
			CommaOk:           inst.CommaOk,
		}
	case *ssa.MapUpdate:
		common.Type = MapUpdateInstruction
		return MapUpdate{
			CommonInstruction: common,
			Map:               p.PackValue(inst.Map),
			Key:               p.PackValue(inst.Key),
			Value:             p.PackValue(inst.Value),
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
			Function:          p.PackValue(inst.Fn),
			Bindings:          lo.Map(inst.Bindings, p.PackValueIdx),
		}
	case *ssa.Phi:
		common.Type = PhiInstruction
		return Phi{
			CommonInstruction: common,
			GoType:            inst.Type().String(),
			Register:          inst.Name(),
			Edges:             lo.Map(inst.Edges, p.PackValueIdx),
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

func (p *Package) PackValue(in ssa.Value) Value {
	p.AddType(in.Type())
	common := CommonValue{
		GoType: in.Type().String(),
		Name:   in.Name(),
	}

	switch value := in.(type) {
	case *ssa.Const:
		common.Type = ConstValue
		v := "nil"
		if value.Value != nil {
			v = value.Value.String()
		}
		return Const{
			CommonValue: common,
			Value: NamedConstValue{
				Type:  value.Type().String(),
				Value: v,
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
		common.Type = VarValue
		return common
	}
}

func (p *Package) PackValueIdx(in ssa.Value, _ int) Value {
	return p.PackValue(in)
}

func (p *Package) PackMethods(in WithMethods) []string {
	methods := make([]string, 0)
	for i := 0; i < in.NumMethods(); i++ {
		method := in.Method(i)
		signature := method.Type().(*types.Signature)
		methods = append(methods, method.Name()+p.PackMethodParams(signature.Params())+p.PackMethodResults(signature.Results()))
	}

	return methods
}

func (p *Package) PackMethodParams(in *types.Tuple) string {
	return "(" + strings.Join(p.PackTupleTypes(in), ", ") + ")"
}

func (p *Package) PackMethodResults(in *types.Tuple) string {
	results := p.PackTupleTypes(in)
	out := strings.Join(results, ", ")
	if in.Len() > 0 {
		if in.Len() > 1 {
			out = "(" + out + ")"
		}
		out = " " + out
	}
	return out
}

func (p *Package) PackTupleTypes(in *types.Tuple) []string {
	out := make([]string, 0)
	for i := 0; i < in.Len(); i++ {
		out = append(out, in.At(i).Type().String())
	}
	return out
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

type Typed interface {
	Type() types.Type
}

type WithMethods interface {
	NumMethods() int
	Method(i int) *types.Func
}
