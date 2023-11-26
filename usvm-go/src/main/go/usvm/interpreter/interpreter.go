package interpreter

import (
	"bytes"
	"fmt"
	"go/token"
	"go/types"
	"log"
	"os"

	"golang.org/x/tools/go/packages"
	"golang.org/x/tools/go/ssa"
	"golang.org/x/tools/go/ssa/ssautil"

	"usvm/api"
)

type continuation int

const (
	kNext continuation = iota
	kReturn
	kJump
)

type Config struct {
	EnableTracing bool
	DumpSsa       bool
}

type Interpreter struct {
	program     *ssa.Program
	mainPackage *ssa.Package
	types       []types.Type
}

func NewInterpreter(file string, conf Config) (*Interpreter, error) {
	f, err := os.Open(file)
	if err != nil {
		return nil, fmt.Errorf("open file: %w", err)
	}
	if err = f.Close(); err != nil {
		return nil, fmt.Errorf("close file: %w", err)
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
	if conf.EnableTracing {
		cfg.Logf = log.Printf
	}
	initialPackages, err := packages.Load(cfg, file)
	if err != nil {
		return nil, err
	}
	if len(initialPackages) == 0 {
		return nil, fmt.Errorf("no packages were loaded")
	}

	if packages.PrintErrors(initialPackages) > 0 {
		return nil, fmt.Errorf("packages contain errors")
	}

	program, _ := ssautil.AllPackages(initialPackages, ssa.InstantiateGenerics|ssa.SanityCheckFunctions)
	program.Build()

	mainPackages := ssautil.MainPackages(program.AllPackages())
	if len(mainPackages) == 0 {
		return nil, fmt.Errorf("error: 0 packages")
	}
	mainPackage := mainPackages[0]
	if conf.DumpSsa {
		dump(mainPackage)
	}

	allTypes := make([]types.Type, 0)
	for _, pkg := range program.AllPackages() {
		for _, m := range pkg.Members {
			switch v := m.(type) {
			case *ssa.Type:
				allTypes = append(allTypes, v.Type())
			}
		}
	}

	return &Interpreter{
		mainPackage: mainPackage,
		program:     program,
		types:       allTypes,
	}, nil
}

func (i *Interpreter) Program() *ssa.Program {
	return i.program
}

func (i *Interpreter) Func(name string) *ssa.Function {
	return i.mainPackage.Func(name)
}

func (i *Interpreter) Types() []types.Type {
	return i.types
}

func (i *Interpreter) Step(api api.Api, inst ssa.Instruction) (out *ssa.Instruction) {
	block := inst.Block()

	defer func() {
		if out == nil {
			return
		}

		switch (inst).(type) {
		case *ssa.Phi, *ssa.If:
		default:
			api.SetLastBlock(block.Index)
		}
	}()

	switch visit(api, inst) {
	case kNext:
		for j := range block.Instrs {
			if block.Instrs[j] != inst {
				continue
			}
			if j+1 < len(block.Instrs) {
				return &block.Instrs[j+1]
			}
			if len(block.Succs) > 0 && len(block.Succs[0].Instrs) > 0 {
				return &block.Succs[0].Instrs[0]
			}
		}
		return nil
	case kJump:
		if len(block.Succs) == 0 || len(block.Succs[0].Instrs) == 0 {
			return nil
		}
		return &block.Succs[0].Instrs[0]
	default:
		return nil
	}
}

func visit(api api.Api, instr ssa.Instruction) continuation {
	switch inst := instr.(type) {
	case *ssa.DebugRef:
		// no-op

	case *ssa.UnOp:
		log.Println("UnOp")

	case *ssa.BinOp:
		api.Log("BinOp", inst.X.Name(), inst.Op.String(), inst.Y.Name(), api.GetLastBlock())

		api.MkBinOp(inst)

	case *ssa.Call:
		log.Println("Call")

	case *ssa.ChangeInterface:
		log.Println("ChangeInterface")

	case *ssa.ChangeType:
		log.Println("ChangeType")

	case *ssa.Convert:
		log.Println("Convert")

	case *ssa.SliceToArrayPointer:
		log.Println("SliceToArrayPointer")

	case *ssa.MakeInterface:
		log.Println("MakeInterface")

	case *ssa.Extract:
		log.Println("Extract")

	case *ssa.Slice:
		log.Println("Slice")

	case *ssa.Return:
		api.Log("Return", inst.Parent(), inst.Results)

		switch len(inst.Results) {
		case 0:
		case 1:
			api.MkReturn(inst.Results[0])
		default:
		}
		return kReturn

	case *ssa.RunDefers:
		log.Println("RunDefers")

	case *ssa.Panic:
		log.Println("Panic")

	case *ssa.Send:
		log.Println("Send")

	case *ssa.Store:
		log.Println("Store")

	case *ssa.If:
		api.Log("If",
			inst.String(),
			inst.Cond.String(),
			inst.Block().Succs[0].Instrs[0],
			inst.Block().Succs[1].Instrs[0],
		)

		api.MkIf(inst)
		return kReturn

	case *ssa.Jump:
		api.Log("Jump", inst, inst.Block().Index, api.GetLastBlock())

		return kJump

	case *ssa.Defer:
		log.Println("Defer")

	case *ssa.Go:
		log.Println("Go")

	case *ssa.MakeChan:
		log.Println("MakeChan")

	case *ssa.Alloc:
		log.Println("Alloc")

	case *ssa.MakeSlice:
		log.Println("MakeSlice")

	case *ssa.MakeMap:
		log.Println("MakeMap")

	case *ssa.Range:
		log.Println("Range")

	case *ssa.Next:
		log.Println("Next")

	case *ssa.FieldAddr:
		log.Println("FieldAddr")

	case *ssa.Field:
		log.Println("Field")

	case *ssa.IndexAddr:
		log.Println("IndexAddr")

	case *ssa.Index:
		log.Println("Index")

	case *ssa.Lookup:
		log.Println("Lookup")

	case *ssa.MapUpdate:
		log.Println("MapUpdate")

	case *ssa.TypeAssert:
		log.Println("TypeAssert")

	case *ssa.MakeClosure:
		log.Println("MakeClosure")

	case *ssa.Phi:
		api.Log("Phi",
			inst.Name(),
			inst.String(),
			inst.Edges,
			inst.Comment,
			api.GetLastBlock(),
		)

		var edge ssa.Value
		block := api.GetLastBlock()
		for i, pred := range inst.Block().Preds {
			if block == pred.Index {
				edge = inst.Edges[i]
				break
			}
		}

		api.MkVariable(inst.Name(), edge)

	case *ssa.Select:
		log.Println("Select")

	default:
		panic(fmt.Sprintf("unexpected instruction: %T", inst))
	}

	return kNext
}

func dump(mainPackage *ssa.Package) {
	out := bytes.Buffer{}
	ssa.WritePackage(&out, mainPackage)
	for _, object := range mainPackage.Members {
		if object.Token() == token.FUNC {
			ssa.WriteFunction(&out, mainPackage.Func(object.Name()))
		}
	}
	fmt.Print(out.String())
}
