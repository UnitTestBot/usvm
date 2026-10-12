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
	sliceReceiver := pkg.Scope().Lookup("SliceReceiver").Type()
	variadicReceiver := pkg.Scope().Lookup("VariadicReceiver").Type()
	exporter := new(Package)

	cases := []struct {
		name     string
		receiver types.Type
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
