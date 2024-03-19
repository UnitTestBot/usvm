package util

import (
	"go/types"
	"math"
	"unsafe"

	"golang.org/x/tools/go/ssa"

	"usvm/domain"
	"usvm/sort"
)

const bufSize = 1 << 16

type ByteBuffer struct {
	buf []byte
	i   int
}

var bufferInstance = &ByteBuffer{}

//goland:noinspection GoVetUnsafePointer
func NewByteBuffer(addr uintptr) *ByteBuffer {
	bufferInstance.i = 0
	bufferInstance.buf = (*[bufSize]byte)(unsafe.Pointer(addr))[:]
	return bufferInstance
}

func (b *ByteBuffer) Write(i byte) *ByteBuffer {
	b.buf[b.i] = i
	b.i++
	return b
}

func (b *ByteBuffer) WriteBool(i bool) *ByteBuffer {
	v := byte(0)
	if i {
		v = 1
	}
	b.buf[b.i] = v
	b.i += 1
	return b
}

func (b *ByteBuffer) WriteInt8(i int8) *ByteBuffer {
	return b.WriteUint8(uint8(i))
}

func (b *ByteBuffer) WriteUint8(i uint8) *ByteBuffer {
	return b.Write(i)
}

func (b *ByteBuffer) WriteInt16(i int16) *ByteBuffer {
	return b.WriteUint16(uint16(i))
}

func (b *ByteBuffer) WriteUint16(i uint16) *ByteBuffer {
	b.buf[b.i] = byte(i >> 8)
	b.buf[b.i+1] = byte(i)
	b.i += 2
	return b
}

func (b *ByteBuffer) WriteInt32(i int32) *ByteBuffer {
	return b.WriteUint32(uint32(i))
}

func (b *ByteBuffer) WriteUint32(i uint32) *ByteBuffer {
	b.buf[b.i] = byte(i >> 24)
	b.buf[b.i+1] = byte(i >> 16)
	b.buf[b.i+2] = byte(i >> 8)
	b.buf[b.i+3] = byte(i)
	b.i += 4
	return b
}

func (b *ByteBuffer) WriteInt64(i int64) *ByteBuffer {
	return b.WriteUint64(uint64(i))
}

func (b *ByteBuffer) WriteUintptr(i uintptr) *ByteBuffer {
	return b.WriteUint64(uint64(i))
}

func (b *ByteBuffer) WriteUint64(i uint64) *ByteBuffer {
	b.buf[b.i] = byte(i >> 56)
	b.buf[b.i+1] = byte(i >> 48)
	b.buf[b.i+2] = byte(i >> 40)
	b.buf[b.i+3] = byte(i >> 32)
	b.buf[b.i+4] = byte(i >> 24)
	b.buf[b.i+5] = byte(i >> 16)
	b.buf[b.i+6] = byte(i >> 8)
	b.buf[b.i+7] = byte(i)
	b.i += 8
	return b
}

func (b *ByteBuffer) WriteFloat32(i float32) *ByteBuffer {
	return b.WriteUint32(math.Float32bits(i))
}

func (b *ByteBuffer) WriteFloat64(i float64) *ByteBuffer {
	return b.WriteUint64(math.Float64bits(i))
}

func (b *ByteBuffer) WriteString(i string) *ByteBuffer {
	b.WriteInt32(int32(len([]rune(i))))
	for _, e := range i {
		b.WriteInt32(e)
	}
	return b
}

func (b *ByteBuffer) WriteMethodInfo(i domain.MethodInfo) *ByteBuffer {
	b.WriteType(i.ReturnType)
	b.WriteInt32(int32(i.VariablesCount))
	b.WriteInt32(int32(i.ParametersCount))
	for _, t := range i.ParametersTypes {
		b.WriteType(t)
	}
	return b
}

func (b *ByteBuffer) WriteSliceElementSort(v ssa.Value) *ByteBuffer {
	switch t := v.Type().(type) {
	case *types.Slice:
		b.Write(byte(sort.MapSort(t.Elem(), false)))
	default:
		b.Write(byte(sort.Int32))
	}

	return b
}

func (b *ByteBuffer) WriteValueType(v ssa.Value) *ByteBuffer {
	b.WriteType(v.Type())
	return b
}

func (b *ByteBuffer) WriteValueUnderlyingType(v ssa.Value) *ByteBuffer {
	t := v.Type()
	if t.String() == "iter" {
		b.WriteType(t)
		return b
	}

	u := t.Underlying()
	if p, ok := u.(*types.Pointer); ok {
		u = p.Elem()
	}
	b.WriteType(u)
	return b
}

func (b *ByteBuffer) WriteType(t types.Type) *ByteBuffer {
	b.WriteUintptr(ToPointer(&t))
	return b
}
