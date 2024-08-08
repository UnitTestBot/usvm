#!/bin/bash

generate() {
  if ! cd antlr; then
    exit 1
  fi

  java -jar templates-db/libs/antlr-4.13.1-complete.jar -o templates-db/src/generated/com/spbpu/bbfinfrastructure/template/parser -Dlanguage=Java -package com.spbpu.bbfinfrastructure.template.parser templates-db/*.g4
  cp ../src/generated/com/spbpu/bbfinfrastructure/template/parser/TemplateLexer.tokens ./
}

clean() {
  rm -rf templates-db/src/generated
  rm templates-db/antlr/TemplateLexer.tokens
}

case $1 in
  --generate)
    generate
    ;;
  --clean)
    clean
    ;;
  *)
    echo "FIX ERROR DESCRIPTION"
    ;;
esac
