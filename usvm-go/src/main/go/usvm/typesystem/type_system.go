package typesystem

import (
	"go/types"
)

func IsSuperType(super, sub types.Type) bool {
	if types.Identical(super, sub) {
		return true
	}

	if !types.AssignableTo(sub, super) {
		return false
	}

	if !isNamed(super) && isNamed(sub) {
		return true
	}

	if isNamed(super) && !isNamed(sub) {
		return false
	}

	return true
}

func isNamed(t types.Type) bool {
	switch t.(type) {
	case *types.Basic, *types.Named, *types.TypeParam:
		return true
	}
	return false
}
