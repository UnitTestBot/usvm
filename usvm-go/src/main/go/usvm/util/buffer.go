package util

type ByteBuffer struct {
	buf []byte
	i   int
}

var bufferInstance = &ByteBuffer{}

func NewByteBuffer(buf []byte) *ByteBuffer {
	bufferInstance.i = 0
	bufferInstance.buf = buf
	return bufferInstance
}

func (b *ByteBuffer) Write(bt byte) {
	b.buf[b.i] = bt
	b.i++
}

func (b *ByteBuffer) WriteInt(i int) {
	v := uint32(i)
	b.buf[b.i] = byte(v >> 24)
	b.buf[b.i+1] = byte(v >> 16)
	b.buf[b.i+2] = byte(v >> 8)
	b.buf[b.i+3] = byte(v)
	b.i += 4
}

func (b *ByteBuffer) WriteLong(i int64) {
	v := uint64(i)
	b.buf[b.i] = byte(v >> 56)
	b.buf[b.i+1] = byte(v >> 48)
	b.buf[b.i+2] = byte(v >> 40)
	b.buf[b.i+3] = byte(v >> 32)
	b.buf[b.i+4] = byte(v >> 24)
	b.buf[b.i+5] = byte(v >> 16)
	b.buf[b.i+6] = byte(v >> 8)
	b.buf[b.i+7] = byte(v)
	b.i += 8
}
