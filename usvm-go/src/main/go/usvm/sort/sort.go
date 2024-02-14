package sort

import (
	"go/types"

	"golang.org/x/tools/go/ssa"
)

type Sort byte

const (
	Unknown Sort = iota
	Void
	Bool
	Int8
	Int16
	Int32
	Int64
	Uint8
	Uint16
	Uint32
	Uint64
	Float32
	Float64
	String
	Array
	Slice
	Map
	Struct
	Interface
	Pointer
	Tuple
)

var basicSorts = []Sort{
	types.Bool:          Bool,
	types.UntypedBool:   Bool,
	types.Int8:          Int8,
	types.Uint8:         Uint8,
	types.Int16:         Int16,
	types.Uint16:        Uint16,
	types.Int:           Int32,
	types.Uint:          Uint32,
	types.UntypedInt:    Int32,
	types.Int32:         Int32,
	types.Uint32:        Uint32,
	types.UntypedRune:   Int32,
	types.Int64:         Int64,
	types.Uint64:        Uint64,
	types.Uintptr:       Uint64,
	types.Float32:       Float32,
	types.Float64:       Float64,
	types.UntypedFloat:  Float64,
	types.String:        String,
	types.UntypedString: String,
}

func GetSort(v ssa.Value, unwrap bool) Sort {
	return MapSort(v.Type(), unwrap)
}

func MapSort(t types.Type, unwrap bool) Sort {
	switch t := t.Underlying().(type) {
	case *types.Basic:
		return basicSorts[t.Kind()]
	case *types.Array:
		return Array
	case *types.Slice:
		return Slice
	case *types.Map:
		return Map
	case *types.Struct:
		return Struct
	case *types.Interface:
		return Interface
	case *types.Pointer:
		if unwrap {
			return MapSort(t.Elem(), false)
		}
		return Pointer
	case *types.Tuple:
		return Tuple
	default:
		return Unknown
	}
}

func GetMapElemSort(v ssa.Value) Sort {
	return MapSort(v.Type().Underlying().(*types.Map).Elem(), false)
}
