package graph

import (
	"go/types"
	"reflect"

	"golang.org/x/tools/go/ssa"

	"usvm/domain"
)

func MethodInfo(function *ssa.Function) domain.MethodInfo {
	returnType := function.Signature.Results()

	variablesCount := 0
	for _, b := range function.Blocks {
		for _, i := range b.Instrs {
			if reflect.ValueOf(i).Elem().Field(0).Type().Name() == "register" {
				variablesCount++
			}
		}
	}

	parametersCount := len(function.Params)
	parametersTypes := make([]types.Type, parametersCount)
	for i := range parametersTypes {
		parametersTypes[i] = function.Params[i].Type()
	}

	return domain.MethodInfo{
		ReturnType:      returnType,
		VariablesCount:  variablesCount,
		ParametersCount: parametersCount,
		ParametersTypes: parametersTypes,
	}
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
