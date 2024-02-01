package util

import (
	"unsafe"
)

var registry = make(map[uintptr]any)

func FromPointer[T any](in uintptr) *T {
	return registry[in].(*T)
}

func ToPointer[T any](in *T) uintptr {
	return PutPointer(unsafe.Pointer(in), in)
}

func PutPointer[T any](pointer unsafe.Pointer, in *T) uintptr {
	out := uintptr(pointer)
	registry[out] = in
	return out
}
