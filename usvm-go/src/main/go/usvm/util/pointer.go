package util

import (
	"go/types"
	"unsafe"

	"github.com/cespare/xxhash"
)

var registry = make(map[uintptr]any)

func FromPointer[T any](in uintptr) *T {
	return registry[in].(*T)
}

func ToPointer[T any](in *T) uintptr {
	switch v := any(in).(type) {
	case *types.Type:
		return PutPointer(uintptr(xxhash.Sum64String((*v).String())), in)
	default:
		return PutPointer(uintptr(unsafe.Pointer(in)), in)
	}
}

func PutPointer[T any](out uintptr, in *T) uintptr {
	registry[out] = in
	return out
}
