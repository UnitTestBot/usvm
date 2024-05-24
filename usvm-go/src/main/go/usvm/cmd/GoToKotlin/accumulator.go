package GoToKotlin

import (
	"bytes"
	"io"
	"sync"
)

const MAXLEN = 134217728 * 4

type Accumulator struct {
	buf    *bytes.Buffer
	writer io.Writer
	waiter *sync.WaitGroup

	bufferSize int
}

func NewAccumulator(writer io.Writer, bufferSize int) *Accumulator {
	return &Accumulator{
		buf:        &bytes.Buffer{},
		writer:     writer,
		waiter:     &sync.WaitGroup{},
		bufferSize: bufferSize,
	}
}

func (a *Accumulator) Write(p []byte) (int, error) {
	n, err := a.buf.Write(p)
	if err != nil {
		return n, err
	}

	if a.buf.Len() >= a.bufferSize {
		var cpy = make([]byte, a.buf.Len())
		copy(cpy, a.buf.Bytes())

		a.waiter.Wait()
		a.waiter.Add(1)

		go func() {
			a.writer.Write(cpy)
			a.waiter.Done()
		}()
		a.buf.Reset()
	}

	return n, err
}

func (a *Accumulator) WriteRest() (int, error) {
	a.waiter.Wait()

	n, err := a.writer.Write(a.buf.Bytes())
	a.buf.Reset()

	return n, err
}
