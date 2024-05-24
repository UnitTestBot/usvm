package main

import (
	"context"
	"time"

	"github.com/tarantool/go-tarantool/v2"
)

func exampleConnect(dialer tarantool.Dialer, opts tarantool.Opts) *tarantool.Connection {
	ctx, cancel := context.WithTimeout(context.Background(), 500*time.Millisecond)
	defer cancel()
	conn, err := tarantool.Connect(ctx, dialer, opts)
	if err != nil {
		panic("Connection is not established: " + err.Error())
	}
	return conn
}

type Tuple struct {
	// Instruct msgpack to pack this struct as array, so no custom packer
	// is needed.
	_msgpack struct{} `msgpack:",asArray"` //nolint: structcheck,unused
	Id       uint
	Msg      string
	Name     string
}

func main() {
	conn := exampleConnect(nil, tarantool.Opts{})
	defer conn.Close()

	// Insert a new tuple { 13, 1 }.
	conn.Do(tarantool.NewInsertRequest("spaceNo").
		Tuple([]interface{}{uint(13), "test", "one"}),
	).Get()

	// Replace a tuple with primary key 13.
	// Note, Tuple is defined within tests, and has EncdodeMsgpack and
	// DecodeMsgpack methods.
	data, err := conn.Do(tarantool.NewReplaceRequest("spaceNo").
		Tuple([]interface{}{uint(13), 1}),
	).Get()
	if err != nil {
		panic(err)
	}
	data, err = conn.Do(tarantool.NewReplaceRequest("test").
		Tuple([]interface{}{uint(13), 1}),
	).Get()
	data, err = conn.Do(tarantool.NewReplaceRequest("test").
		Tuple(&Tuple{Id: 13, Msg: "test", Name: "eleven"}),
	).Get()
	data, err = conn.Do(tarantool.NewReplaceRequest("test").
		Tuple(&Tuple{Id: 13, Msg: "test", Name: "twelve"}),
	).Get()
	panic(data)
}
