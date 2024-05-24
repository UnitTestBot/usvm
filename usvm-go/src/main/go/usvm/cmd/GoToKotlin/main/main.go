package main

import (
	"GoToKotlin"
	"compress/gzip"
	"flag"
	"fmt"
	"os"

	"golang.org/x/tools/go/packages"
	"golang.org/x/tools/go/ssa"
	"golang.org/x/tools/go/ssa/ssautil"
)

type nonEmptyInterface interface {
	SomeFunc() bool
}

type someStruct struct {
	fl1 string
}

type interfaceImpl struct {
	fl21 []string
}

func (l interfaceImpl) SomeFunc() bool {
	return true
}

type customType int16

type BigStruct struct {
	_ interfaceImpl

	Field1      int
	FieldString string
	field3      bool
	F4          byte
	f5          *int16
	f6          []uint
	f7          *[]uint
	f8          interface{}
	f85         interface{}
	f9          someStruct
	f10         nonEmptyInterface
	f11         map[string]bool
	f12         map[interface{}]interface{}
	f13         *map[someStruct]interfaceImpl
	f14         *map[*interfaceImpl]*someStruct
	f15         []someStruct
	f16         *[]*someStruct
	f17         *nonEmptyInterface
	f18         *someStruct
	f19         customType
	f20         *customType
	f21         []*customType
	f22         map[customType]*customType
	f23         *BigStruct
	f24         interface{}
}

var needToGen = flag.Bool("gen", true, "Is initial generation needed")

func main() {
	flag.Parse()
	fileName := "./ssa_prompt/g501/main.go"

	// Replace interface{} with any for this test.
	// Parse the source files.
	f, err := os.Open(fileName)
	if err != nil {
		fmt.Printf("open file: %s", err)
	}
	if err = f.Close(); err != nil {
		fmt.Printf("close file: %s", err)
	}

	mode := packages.NeedName |
		packages.NeedFiles |
		packages.NeedCompiledGoFiles |
		packages.NeedImports |
		packages.NeedDeps |
		packages.NeedExportFile |
		packages.NeedTypes |
		packages.NeedTypesSizes |
		packages.NeedTypesInfo |
		packages.NeedSyntax |
		packages.NeedModule |
		packages.NeedEmbedFiles |
		packages.NeedEmbedPatterns
	cfg := &packages.Config{Mode: mode}

	initialPackages, err := packages.Load(cfg, fileName) // Or by the package name: "k8s.io/client-go/kubernetes"
	if err != nil {
		fmt.Print(err)
	}
	if len(initialPackages) == 0 {
		fmt.Printf("no packages were loaded")
	}

	if packages.PrintErrors(initialPackages) > 0 {
		fmt.Printf("packages contain errors")
	}

	program, _ := ssautil.AllPackages(initialPackages, ssa.InstantiateGenerics|ssa.SanityCheckFunctions)
	program.Build()

	/*impl := interfaceImpl{}
	k := BigStruct{f85: 123, f10: impl}
	k.f9.fl1 = "123"
	k.f11 = make(map[string]bool)
	k.f11["lolol"] = false

	mp := make(map[someStruct]interfaceImpl)
	mp[someStruct{fl1: "key"}] = interfaceImpl{fl21: []string{"some", "value"}}
	k.f13 = &mp

	f10 := interfaceImpl{fl21: []string{"hello\nworld!", "hola", "привет"}}
	k.f10 = f10
	k.f23 = &k
	k.f24 = &k*/

	os.Mkdir("ssaExample", os.ModePerm)
	file, _ := os.Create("ssaExample/filled.gzip")
	defer file.Close()

	gzipWriter := gzip.NewWriter(file)
	defer gzipWriter.Close()

	conv := GoToKotlin.CreateConverter("ssaExample", true)

	if *needToGen {
		fmt.Printf("%v", conv.GenerateStructures(program))
	}
	fmt.Printf("%v", conv.FillStructures(gzipWriter, program))
}
