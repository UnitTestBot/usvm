package util

import (
	"log"
)

var Debug = false

func Log(values ...any) {
	if Debug {
		log.Println(values...)
	}
}
