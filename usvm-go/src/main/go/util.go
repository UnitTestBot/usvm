package main

import (
	"io"
	"log"
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
