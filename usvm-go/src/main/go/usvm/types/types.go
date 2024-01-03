package types

import (
	"go/types"

	"golang.org/x/tools/go/ssa"
)

type Type byte

const (
	TypeUnknown Type = iota
	TypeBool
	TypeInt8
	TypeInt16
	TypeInt32
	TypeInt64
	TypeUint8
	TypeUint16
	TypeUint32
	TypeUint64
	TypeFloat32
	TypeFloat64
	TypeArray
	TypeStruct
)

var typeMapping = []Type{
	types.Bool:         TypeBool,
	types.UntypedBool:  TypeBool,
	types.Int8:         TypeInt8,
	types.Uint8:        TypeUint8,
	types.Int16:        TypeInt16,
	types.Uint16:       TypeUint16,
	types.Int:          TypeInt32,
	types.Uint:         TypeUint32,
	types.UntypedInt:   TypeInt32,
	types.Int32:        TypeInt32,
	types.Uint32:       TypeUint32,
	types.UntypedRune:  TypeInt32,
	types.Int64:        TypeInt64,
	types.Uint64:       TypeUint64,
	types.Uintptr:      TypeUint64,
	types.Float32:      TypeFloat32,
	types.Float64:      TypeFloat64,
	types.UntypedFloat: TypeFloat64,
}

func GetType(v ssa.Value) Type {
	return MapType(v.Type())
}

func MapType(t types.Type) Type {
	switch t := t.Underlying().(type) {
	case *types.Basic:
		return typeMapping[t.Kind()]
	case *types.Array, *types.Slice:
		return TypeArray
	case *types.Struct:
		return TypeStruct
	case *types.Pointer:
		return MapType(t.Elem())
	default:
		return TypeUnknown
	}
}
