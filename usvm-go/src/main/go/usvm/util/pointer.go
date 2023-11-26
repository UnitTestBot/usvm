package util

import (
	"unsafe"
)

var registry = make(map[uintptr]any)

func FromPointer[T any](in uintptr) *T {
	return registry[in].(*T)
}

func ToPointer[T any](in *T) uintptr {
	out := uintptr(unsafe.Pointer(in))
	registry[out] = in
	return out
}
