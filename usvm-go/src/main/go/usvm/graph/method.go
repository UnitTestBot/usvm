package graph

import (
	"reflect"

	"golang.org/x/tools/go/ssa"
)

func MethodInfo(function *ssa.Function) (int, int) {
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

	return parametersCount, localsCount
}

func Callee(program *ssa.Program, call *ssa.CallCommon) *ssa.Function {
	if call.IsInvoke() {
		typ := call.Value.Type()
		pkg := call.Method.Pkg()
		name := call.Method.Name()
		return program.LookupMethod(typ, pkg, name)
	}
	return call.StaticCallee()
}
