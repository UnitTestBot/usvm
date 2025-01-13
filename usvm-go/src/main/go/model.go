package main

import (
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
	MultiConvertInstruction        = "MultiConvert"

	ConstValue     = "Const"
	GlobalValue    = "Global"
	ParameterValue = "Parameter"
	FreeVarValue   = "FreeVar"
	VarValue       = "Var"

	FunctionValue    = "Function"
	MakeClosureValue = "MakeClosure"
	BuiltinValue     = "Builtin"

	AliasType     = "Alias"
	ArrayType     = "Array"
	BasicType     = "Basic"
	ChanType      = "Chan"
	InterfaceType = "Interface"
	MapType       = "Map"
	NamedType     = "Named"
	OpaqueType    = "Opaque"
	PointerType   = "Pointer"
	SignatureType = "Signature"
	SliceType     = "Slice"
	StructType    = "Struct"
	TupleType     = "Tuple"
	TypeParamType = "TypeParam"
	UnionType     = "Union"
)

type Package struct {
	Name    string          `yaml:"name" json:"name"`
	Members []Member        `yaml:"members" json:"members"`
	Types   map[string]Type `yaml:"types" json:"types"`

	program *ssa.Program
}

type Member interface {
	name() string
	isMember()
}

type CommonMember struct {
	Type string `yaml:"type" json:"type"`
	Name string `yaml:"name" json:"name"`
}

func (m CommonMember) name() string {
	return m.Name
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

type MemberGlobal struct {
	CommonMember `yaml:",inline"`
	Index        int    `yaml:"index" json:"index"`
	GoType       string `yaml:"go_type" json:"go_type"`
}

type Function struct {
	CommonMember `yaml:",inline"`
	BasicBlocks  []BasicBlock `yaml:"basic_blocks" json:"basic_blocks"`
	Parameters   []Parameter  `yaml:"parameters" json:"parameters"`
	FreeVars     []Value      `yaml:"free_vars" json:"free_vars"`
	ReturnTypes  []string     `yaml:"return_types" json:"return_types"`
	Recover      *BasicBlock  `yaml:"recover,omitempty" json:"recover,omitempty"`
}

type BasicBlock struct {
	Index        int           `yaml:"index" json:"index"`
	Instructions []Instruction `yaml:"instructions" json:"instructions"`
	Prev         []int         `yaml:"prev,flow" json:"prev"`
	Next         []int         `yaml:"next,flow" json:"next"`
}

type Instruction interface {
	isInstruction()
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
	Method            string  `yaml:"method" json:"method"`
	Args              []Value `yaml:"args" json:"args"`
}

type ChangeInterface struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Value             Value  `yaml:"value" json:"value"`
}

type ChangeType struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Value             Value  `yaml:"value" json:"value"`
}

type Convert struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Value             Value  `yaml:"value" json:"value"`
}

type SliceToArrayPointer struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Value             Value  `yaml:"value" json:"value"`
}

type MakeInterface struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Value             Value  `yaml:"value" json:"value"`
}

type Extract struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Tuple             Value  `yaml:"tuple" json:"tuple"`
	Index             int    `yaml:"index" json:"index"`
}

type SliceInst struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Collection        Value  `yaml:"collection" json:"collection"`
	Low               Value  `yaml:"low" json:"low"`
	High              Value  `yaml:"high" json:"high"`
	Max               Value  `yaml:"max" json:"max"`
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
	Value             Value `yaml:"value" json:"value"`
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
	Value             Value   `yaml:"value" json:"value"`
	Method            string  `yaml:"method" json:"method"`
	Args              []Value `yaml:"args" json:"args"`
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
	Comment           string `yaml:"comment" json:"comment"`
}

type MakeSlice struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Len               Value  `yaml:"len" json:"len"`
	Cap               Value  `yaml:"cap" json:"cap"`
}

type MakeMap struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Reserve           Value  `yaml:"reserve" json:"reserve"`
}

type Range struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Collection        Value  `yaml:"collection" json:"collection"`
}

type Next struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Iter              Value  `yaml:"iter" json:"iter"`
	IsString          bool   `yaml:"is_string" json:"is_string"`
}

type FieldAddr struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Struct            Value  `yaml:"struct" json:"struct"`
	Field             int    `yaml:"field" json:"field"`
}

type Field struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Struct            Value  `yaml:"struct" json:"struct"`
	Field             int    `yaml:"field" json:"field"`
}

type IndexAddr struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Collection        Value  `yaml:"collection" json:"collection"`
	Index             Value  `yaml:"index" json:"index"`
}

type Index struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Collection        Value  `yaml:"collection" json:"collection"`
	Index             Value  `yaml:"index" json:"index"`
}

type Lookup struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Map               Value  `yaml:"map" json:"map"`
	Key               Value  `yaml:"key" json:"key"`
	CommaOk           bool   `yaml:"comma_ok" json:"comma_ok"`
}

type MapUpdate struct {
	CommonInstruction `yaml:",inline"`
	Map               Value `yaml:"map" json:"map"`
	Key               Value `yaml:"key" json:"key"`
	Value             Value `yaml:"value" json:"value"`
}

type TypeAssert struct {
	CommonInstruction `yaml:",inline"`
	GoType            string `yaml:"go_type" json:"go_type"`
	Register          string `yaml:"register" json:"register"`
	Value             Value  `yaml:"value" json:"value"`
	AssertedType      string `yaml:"asserted_type" json:"asserted_type"`
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

type Type interface {
	isType()
}

type CommonType struct {
	Type string `yaml:"type" json:"type"`
	Name string `yaml:"name" json:"name"`
}

func (CommonType) isType() {}

type Alias struct {
	CommonType `yaml:"-,inline"`
	From       string `yaml:"from" json:"from"`
}

type Array struct {
	CommonType `yaml:"-,inline"`
	Len        int64  `yaml:"len" json:"len"`
	Elem       string `yaml:"elem" json:"elem"`
}

type Chan struct {
	CommonType `yaml:"-,inline"`
	Dir        int    `yaml:"dir" json:"dir"`
	Elem       string `yaml:"elem" json:"elem"`
}

type Interface struct {
	CommonType `yaml:"-,inline"`
	Methods    []string `yaml:"methods" json:"methods"`
}

type Map struct {
	CommonType `yaml:"-,inline"`
	Key        string `yaml:"key" json:"key"`
	Elem       string `yaml:"elem" json:"elem"`
}

type Named struct {
	CommonType `yaml:"-,inline"`
	Underlying string   `yaml:"underlying" json:"underlying"`
	Methods    []string `yaml:"methods" json:"methods"`
}

type Pointer struct {
	CommonType `yaml:"-,inline"`
	Elem       string `yaml:"elem" json:"elem"`
}

type Signature struct {
	CommonType `yaml:"-,inline"`
	Params     []string `yaml:"params" json:"params"`
	Results    []string `yaml:"results" json:"results"`
}

type Slice struct {
	CommonType `yaml:"-,inline"`
	Elem       string `yaml:"elem" json:"elem"`
}

type Struct struct {
	CommonType `yaml:"-,inline"`
	Fields     []string `yaml:"fields" json:"fields"`
}

type Tuple struct {
	CommonType `yaml:"-,inline"`
	Elems      []string `yaml:"elems" json:"elems"`
}
