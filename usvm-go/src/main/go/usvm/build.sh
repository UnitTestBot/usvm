#!/bin/bash

set -euxo pipefail

if [[ "$OSTYPE" == "linux-gnu"* ]]; then
	osdir="linux"
	folder="/usr/lib/jvm/java-17-openjdk-amd64/include"
	lib="/home/buraindo/libs/java_bridge.so"
elif [[ "$OSTYPE" == "darwin"* ]]; then
  osdir="darwin"
  folder="/Users/e.k.ibragimov/.sdkman/candidates/java/current/include"
	lib="/Users/e.k.ibragimov/Documents/University/MastersDiploma/libs/java_bridge.dylib"
elif [[ "$OSTYPE" == "msys"* ]]; then
  osdir="win32"
  folder="C:/Programming/Java/jdk-17.0.3.1/include"
  lib="C:/Users/burai/Documents/University/MastersDiploma/expr/libs/java_bridge.dll"
fi

export CGO_CFLAGS="-I ${folder} -I ${folder}/${osdir} -O2"
go build -o ${lib} -buildmode=c-shared bridge.go
