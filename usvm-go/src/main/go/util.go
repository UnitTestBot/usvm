package main

import (
	"io"
	"log"
	"path"
	"path/filepath"
	"runtime"
)

var (
	_, filename, _, _ = runtime.Caller(0)

	Root      = filepath.Dir(filename)
	OutputDir = path.Join(Root, filepath.Dir("../../../out/"))
)

func CheckClose(c io.Closer) {
	CheckError(c.Close())
}

func CheckError(args ...any) {
	for _, arg := range args {
		if arg == nil {
			continue
		}
		if err, ok := arg.(error); ok && err != nil {
			log.Fatalf("Fatal: %v", err)
		}
	}
}
