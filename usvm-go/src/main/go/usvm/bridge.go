package main

/*
#include <jni.h>
*/
import "C"

import (
	"fmt"
	"go/types"
	"reflect"
	"unsafe"

	"golang.org/x/tools/go/callgraph"
	"golang.org/x/tools/go/callgraph/cha"
	"golang.org/x/tools/go/callgraph/vta"
	"golang.org/x/tools/go/ssa"
	"golang.org/x/tools/go/ssa/ssautil"

	"usvm/api"
	"usvm/interpreter"
	"usvm/util"
)

const (
	bufSize = 1 << 16
)

var anyType = types.Type(types.NewInterfaceType(nil, nil).Complete())

var bridge = &Bridge{}

type Bridge struct {
	interpreter *interpreter.Interpreter
	callgraph   *callgraph.Graph
}

func (b *Bridge) init(file string, debug bool) error {
	util.Debug = debug

	var err error
	b.interpreter, err = interpreter.NewInterpreter(file, interpreter.Config{
		EnableTracing: false,
		DumpSsa:       debug,
	})
	if err != nil {
		return fmt.Errorf("new interpreter: %w", err)
	}

	program := b.interpreter.Program()
	b.callgraph = vta.CallGraph(ssautil.AllFunctions(program), cha.CallGraph(program))
	b.callgraph.DeleteSyntheticNodes()

	return nil
}

func (b *Bridge) step(api api.Api, inst ssa.Instruction) *ssa.Instruction {
	newInst := b.interpreter.Step(api, inst)
	api.WriteLastBlock()
	return newInst
}

// ---------------- region: initialize

//export initialize
func initialize(
	fileBytes *C.jbyte, fileSize C.jint,
	debugC C.jboolean,
) C.jint {
	file := toString(fileBytes, fileSize)
	debug := toBool(debugC)

	if err := bridge.init(file, debug); err != nil {
		util.Log("init failed", err)
		return 1
	}
	return 0
}

// ---------------- region: initialize

// ---------------- region: shutdown

//export shutdown
func shutdown() C.jint {
	return C.jint(0)
}

// ---------------- region: shutdown

// ---------------- region: machine

//export getMethod
func getMethod(nameBytes *C.jbyte, nameSize C.jint) C.jlong {
	name := toString(nameBytes, nameSize)
	method := bridge.interpreter.Func(name)
	return C.jlong(util.ToPointer(method))
}

// ---------------- region: machine

// ---------------- region: application graph

//export predecessors
func predecessors(pointer C.jlong, arr *C.jlong, size C.jint) C.jint {
	inst := *util.FromPointer[ssa.Instruction](uintptr(pointer))
	if inst == nil {
		return 0
	}

	out := unsafe.Slice(arr, int(size))
	block := inst.Block()
	if block == nil {
		return 0
	}

	index := 0
	for _, b := range block.Preds {
		for i := range b.Instrs {
			out[index] = C.jlong(util.ToPointer(&b.Instrs[i]))
			index++
		}
	}
	for i := range block.Instrs {
		if block.Instrs[i] == inst {
			break
		}
		out[index] = C.jlong(util.ToPointer(&block.Instrs[i]))
		index++
	}

	return C.jint(index)
}

//export successors
func successors(pointer C.jlong, arr *C.jlong, size C.jint) C.jint {
	inst := *util.FromPointer[ssa.Instruction](uintptr(pointer))
	if inst == nil {
		return 0
	}

	out := unsafe.Slice(arr, int(size))
	block := inst.Block()
	if block == nil {
		return 0
	}

	k := 0
	for j, i := range block.Instrs {
		if i == inst {
			k = j
			break
		}
	}

	index := 0
	for i := k + 1; i < len(block.Instrs); i++ {
		out[index] = C.jlong(util.ToPointer(&block.Instrs[i]))
		index++
	}
	for _, b := range block.Succs {
		for i := range b.Instrs {
			out[index] = C.jlong(util.ToPointer(&b.Instrs[i]))
			index++
		}
	}

	return C.jint(index)
}

//export callees
func callees(pointer C.jlong, arr *C.jlong) {
	inst := *util.FromPointer[ssa.Instruction](uintptr(pointer))
	if inst == nil {
		return
	}

	out := unsafe.Slice(arr, 1)
	call, ok := inst.(ssa.CallInstruction)
	if !ok {
		return
	}

	if call.Common().IsInvoke() {
		program := bridge.interpreter.Program()
		callCommon := call.Common()
		typ := callCommon.Value.Type()
		pkg := callCommon.Method.Pkg()
		name := callCommon.Method.Name()
		out[0] = C.jlong(util.ToPointer(program.LookupMethod(typ, pkg, name)))
	} else {
		out[0] = C.jlong(util.ToPointer(call.Common().StaticCallee()))
	}
}

//export callers
func callers(pointer C.jlong, arr *C.jlong, size C.jint) C.jint {
	function := util.FromPointer[ssa.Function](uintptr(pointer))
	if function == nil {
		return 0
	}

	in := bridge.callgraph.Nodes[function].In
	out := unsafe.Slice(arr, int(size))

	index := 0
	for i := range in {
		inst := in[i].Site.(ssa.Instruction)
		out[index] = C.jlong(util.ToPointer(&inst))
		index++
	}

	return C.jint(index)
}

//export entryPoints
func entryPoints(pointer C.jlong, arr *C.jlong) {
	function := util.FromPointer[ssa.Function](uintptr(pointer))
	if function == nil {
		return
	}

	out := unsafe.Slice(arr, 1)
	out[0] = C.jlong(util.ToPointer(&function.Blocks[0].Instrs[0]))
}

//export exitPoints
func exitPoints(pointer C.jlong, arr *C.jlong, size C.jint) C.jint {
	function := util.FromPointer[ssa.Function](uintptr(pointer))
	if function == nil {
		return 0
	}

	index := 0
	out := unsafe.Slice(arr, int(size))
	for _, b := range function.Blocks {
		for i := range b.Instrs {
			switch b.Instrs[i].(type) {
			case *ssa.Return, *ssa.Panic:
				out[index] = C.jlong(util.ToPointer(&b.Instrs[i]))
				index++
			}
		}
	}

	return C.jint(index)
}

//export methodOf
func methodOf(pointer C.jlong) C.jlong {
	method := *util.FromPointer[ssa.Instruction](uintptr(pointer))
	if method == nil {
		return 0
	}

	return C.jlong(util.ToPointer(method.Parent()))
}

//export statementsOf
func statementsOf(pointer C.jlong, arr *C.jlong, size C.jint) C.jint {
	function := util.FromPointer[ssa.Function](uintptr(pointer))
	if function == nil {
		return 0
	}

	index := 0
	out := unsafe.Slice(arr, int(size))
	for _, b := range function.Blocks {
		for i := range b.Instrs {
			out[index] = C.jlong(util.ToPointer(&b.Instrs[i]))
			index++
		}
	}

	return C.jint(index)
}

// ---------------- region: application graph

// ---------------- region: type system

//export getAnyType
func getAnyType() C.jlong {
	return C.jlong(util.ToPointer(&anyType))
}

//export findSubTypes
func findSubTypes(pointer C.jlong, arr *C.jlong, size C.jint) C.jint {
	t := *util.FromPointer[types.Type](uintptr(pointer))
	if t == nil {
		return 0
	}
	if !types.IsInterface(t) {
		return 0
	}

	i := t.(*types.Interface).Complete()
	index := 0
	out := unsafe.Slice(arr, int(size))
	allTypes := bridge.interpreter.Types()
	for j, v := range allTypes {
		if !types.Implements(v, i) {
			continue
		}
		out[index] = C.jlong(util.ToPointer(&allTypes[j]))
		index++
	}

	return C.jint(index)
}

//export isInstantiable
func isInstantiable(pointer C.jlong) C.jboolean {
	t := *util.FromPointer[types.Type](uintptr(pointer))
	if t == nil {
		return toJBool(false)
	}
	// TODO: maybe channels also need to be considered not instantiable
	result := !types.IsInterface(t)
	return toJBool(result)
}

//export isFinal
func isFinal(pointer C.jlong) C.jboolean {
	t := *util.FromPointer[types.Type](uintptr(pointer))
	if t == nil {
		return toJBool(false)
	}
	result := !types.IsInterface(t)
	return toJBool(result)
}

//export hasCommonSubtype
func hasCommonSubtype(pointer C.jlong, arr *C.jlong, size C.jint) C.jboolean {
	allTypes := make([]types.Type, 0, 20)
	allTypes = append(allTypes, *util.FromPointer[types.Type](uintptr(pointer)))

	in := unsafe.Slice(arr, int(size))
	for _, t := range in {
		allTypes = append(allTypes, *util.FromPointer[types.Type](uintptr(C.jlong(t))))
	}

	result := true
	for _, t := range allTypes {
		if !types.IsInterface(t) {
			result = false
			break
		}
	}
	return toJBool(result)
}

//export isSupertype
func isSupertype(supertypePointer, typePointer C.jlong) C.jboolean {
	t := *util.FromPointer[types.Type](uintptr(supertypePointer))
	v := *util.FromPointer[types.Type](uintptr(typePointer))
	result := types.Identical(v, t) || types.AssignableTo(v, t)
	return toJBool(result)
}

// ---------------- region: type system

// ---------------- region: interpreter

//export methodInfo
func methodInfo(pointer C.jlong, arr *C.jint) {
	function := util.FromPointer[ssa.Function](uintptr(pointer))
	if function == nil {
		return
	}

	parametersCount, localsCount := 0, len(function.Locals)
	for range function.Params {
		parametersCount++
	}
	for _, b := range function.Blocks {
		for _, i := range b.Instrs {
			if reflect.ValueOf(i).Elem().Field(0).Type().Name() == "register" {
				localsCount++
			}
		}
	}

	out := unsafe.Slice(arr, 2)
	out[0] = C.jint(parametersCount)
	out[1] = C.jint(localsCount)
}

// ---------------- region: interpreter

// ---------------- region: api

//export start
func start() C.int {
	return C.int(0)
}

//export step
//goland:noinspection GoVetUnsafePointer
func step(pointer C.jlong, lastBlock C.jint, arr C.jlong) C.jlong {
	inst := *util.FromPointer[ssa.Instruction](uintptr(pointer))
	if inst == nil {
		return 0
	}

	out := (*[bufSize]byte)(unsafe.Pointer(uintptr(arr)))[:]
	nextInst := bridge.step(api.NewApi(int(lastBlock), out), inst)
	if nextInst == nil {
		return 0
	}

	return C.jlong(util.ToPointer(nextInst))
}

// ---------------- region: api

// ---------------- region: tools

func toString(bytes *C.jbyte, size C.jint) string {
	return unsafe.String((*byte)(unsafe.Pointer(bytes)), int(size))
}

func toBool(b C.jboolean) bool {
	return b == C.JNI_TRUE
}

func toJBool(b bool) C.jboolean {
	if b {
		return C.JNI_TRUE
	}
	return C.JNI_FALSE
}

// ---------------- region: tools

func main() {}
