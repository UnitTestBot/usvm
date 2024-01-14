package domain

import (
	"usvm/types"
)

type MethodInfo struct {
	ReturnType       types.Type
	VariablesCount   int
	AllocationsCount int
	ParametersCount  int
	ParametersTypes  []types.Type
}
