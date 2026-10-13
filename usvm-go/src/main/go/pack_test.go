package main

import (
	"go/ast"
	"go/parser"
	"go/token"
	"go/types"
	"testing"
)

func TestMethodSignaturesPreserveVariadicParameters(t *testing.T) {
	const source = `package signatures
        type Variadic interface { Count(...int) int }
        type Slice interface { Count([]int) int }
        type SliceReceiver struct{}
        func (SliceReceiver) Count(values []int) int { return len(values) }
        type VariadicReceiver struct{}
        func (VariadicReceiver) Count(values ...int) int { return len(values) }
    `
	files := token.NewFileSet()
	file, err := parser.ParseFile(files, "signatures.go", source, 0)
	if err != nil {
		t.Fatal(err)
	}
	pkg, err := new(types.Config).Check("signatures", files, []*ast.File{file}, nil)
	if err != nil {
		t.Fatal(err)
	}
	variadic := pkg.Scope().Lookup("Variadic").Type().Underlying().(*types.Interface)
	slice := pkg.Scope().Lookup("Slice").Type().Underlying().(*types.Interface)
	sliceReceiver := pkg.Scope().Lookup("SliceReceiver").Type().(*types.Named)
	variadicReceiver := pkg.Scope().Lookup("VariadicReceiver").Type().(*types.Named)
	exporter := new(Package)

	cases := []struct {
		name     string
		receiver *types.Named
		contract *types.Interface
	}{
		{"slice versus variadic", sliceReceiver, variadic},
		{"variadic versus slice", variadicReceiver, slice},
		{"matching slice", sliceReceiver, slice},
		{"matching variadic", variadicReceiver, variadic},
	}
	for _, test := range cases {
		t.Run(test.name, func(t *testing.T) {
			actual := exporter.PackMethods(test.receiver)[0] == exporter.PackMethods(test.contract)[0]
			expected := types.Implements(test.receiver, test.contract)

			if actual != expected {
				t.Fatalf("exported method identity agrees=%v, Go interface implementation=%v", actual, expected)
			}
		})
	}
}

func TestTypeIdentitiesPreserveDistinctLocalDeclarations(t *testing.T) {
	pkg := types.NewPackage("records", "records")
	first := types.NewNamed(
		types.NewTypeName(token.Pos(1), pkg, "record", nil),
		types.NewStruct([]*types.Var{types.NewVar(token.NoPos, pkg, "number", types.Typ[types.Int])}, nil),
		nil,
	)
	second := types.NewNamed(
		types.NewTypeName(token.Pos(2), pkg, "record", nil),
		types.NewStruct([]*types.Var{types.NewVar(token.NoPos, pkg, "pointer", types.NewPointer(types.Typ[types.Int]))}, nil),
		nil,
	)
	exporter := Package{Types: make(map[string]Type)}

	exporter.AddType(first)
	exporter.AddType(second)
	firstName, secondName := exporter.TypeName(first), exporter.TypeName(second)
	if firstName == secondName {
		t.Fatal("distinct local named types share an exported identity")
	}
	firstRecord := exporter.Types[exporter.Types[firstName].(Named).Underlying].(Struct)
	secondRecord := exporter.Types[exporter.Types[secondName].(Named).Underlying].(Struct)
	if firstRecord.Fields[0] != "int" || secondRecord.Fields[0] != "*int" {
		t.Fatalf("local record fields changed: first=%v, second=%v", firstRecord.Fields, secondRecord.Fields)
	}
	if exporter.TypeName(types.NewPointer(first)) == exporter.TypeName(types.NewPointer(second)) {
		t.Fatal("pointers to distinct local types share an exported identity")
	}
	if exporter.TypeName(types.NewPointer(first)) != exporter.TypeName(types.NewPointer(first)) {
		t.Fatal("identical pointer types have different exported identities")
	}
}
