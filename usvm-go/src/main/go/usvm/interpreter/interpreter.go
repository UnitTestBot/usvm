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
	kNone
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

		switch inst.(type) {
		case *ssa.Phi, *ssa.If:
		default:
			api.SetLastBlock(block.Index)
		}
	}()

	switch i.visit(api, inst) {
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

func (i *Interpreter) visit(api api.Api, instr ssa.Instruction) continuation {
	switch inst := instr.(type) {
	case *ssa.DebugRef:
		// no-op

	case *ssa.UnOp:
		api.MkUnOp(inst)

	case *ssa.BinOp:
		api.MkBinOp(inst)

	case *ssa.Call:
		switch f := inst.Call.Value.(type) {
		case *ssa.Builtin:
			api.MkCallBuiltin(inst, f.Name())
		default:
			api.MkCall(inst)
		}

	case *ssa.ChangeInterface:

	case *ssa.ChangeType:

	case *ssa.Convert:

	case *ssa.SliceToArrayPointer:

	case *ssa.MakeInterface:

	case *ssa.Extract:
		api.MkExtract(inst)

	case *ssa.Slice:

	case *ssa.Return:
		api.MkReturn(inst)
		return kReturn

	case *ssa.RunDefers:

	case *ssa.Panic:
		api.MkPanic(inst)
		return kReturn

	case *ssa.Send:

	case *ssa.Store:
		api.MkStore(inst)

	case *ssa.If:
		api.MkIf(inst)
		return kNone

	case *ssa.Jump:
		return kJump

	case *ssa.Defer:

	case *ssa.Go:

	case *ssa.MakeChan:

	case *ssa.Alloc:
		api.MkAlloc(inst)

	case *ssa.MakeSlice:
		api.MkMakeSlice(inst)

	case *ssa.MakeMap:
		api.MkMakeMap(inst)

	case *ssa.Range:

	case *ssa.Next:

	case *ssa.FieldAddr:
		api.MkPointerFieldReading(inst)

	case *ssa.Field:
		api.MkFieldReading(inst)

	case *ssa.IndexAddr:
		api.MkPointerArrayReading(inst)

	case *ssa.Index:
		api.MkArrayReading(inst)

	case *ssa.Lookup:
		api.MkMapLookup(inst)

	case *ssa.MapUpdate:
		api.MkMapUpdate(inst)

	case *ssa.TypeAssert:

	case *ssa.MakeClosure:

	case *ssa.Phi:
		api.MkPhi(inst)

	case *ssa.Select:

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
