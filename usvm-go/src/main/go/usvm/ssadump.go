package main

import (
	"fmt"
	"runtime"

	"usvm/interpreter"
)

func main() {
	dir := "C:/Users/burai/Documents/University/MastersDiploma/expr/programs/usvm"
	switch runtime.GOOS {
	case "darwin":
		dir = "/Users/e.k.ibragimov/Documents/University/MastersDiploma/programs"
	case "linux":
		dir = "/home/buraindo/programs"
	}

	_, err := interpreter.NewInterpreter(fmt.Sprintf("%s/playground.go", dir), interpreter.Config{
		DumpSsa: true,
	})
	if err != nil {
		fmt.Println(err)
	}
}
