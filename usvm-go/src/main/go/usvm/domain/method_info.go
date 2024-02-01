package domain

import (
	"go/types"
)

type MethodInfo struct {
	ReturnType      types.Type
	VariablesCount  int
	ParametersCount int
	ParametersTypes []types.Type
}
